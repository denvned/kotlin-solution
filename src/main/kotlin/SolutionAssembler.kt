import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.caches.resolve.*
import org.jetbrains.kotlin.idea.refactoring.fqName.isImported
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.canBeResolvedViaImport
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.descriptorUtil.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.DefaultImportProvider

private val KtElement.canBeExcluded get() = when (this) {
    is KtFile -> true
    is KtObjectDeclaration -> !isCompanion() && !isObjectLiteral() && !isLocal
    is KtClass -> !isLocal
    is KtNamedFunction -> !isLocal
    // https://youtrack.jetbrains.com/issue/KT-14040
    is KtSecondaryConstructor -> !containingClass()!!.isEnum()
    is KtProperty -> !isLocal && !hasDelegate() && (initializer?.hasNoSideEffects ?: true)
    is KtTypeAlias -> true
    else -> false
}

class SolutionAssembler {
    private val included = hashSetOf<KtElement>()
    private lateinit var moduleDescriptor: ModuleDescriptor

    fun assembleSolution(entryPoint: KtElement): String {
        moduleDescriptor = entryPoint.findModuleDescriptor()

        IncludingVisitor().include(entryPoint)

        val entryFile = entryPoint.containingKtFile
        val files = listOf(entryFile) +
                included.filterIsInstance<KtFile>()
                        .filterNot { it == entryFile }
                        .sortedWith(compareBy<KtFile> { it.packageFqName.asString() }
                                .thenBy { it.name })

        val importCollector = ImportCollectingVisitor()
        files.forEach { it.accept(importCollector) }

        return TextBuilder().apply {
            val imports = importCollector.imports
                    .filterNot { it.fqName.parent().isRoot && !it.hasAlias() }
            val aliasedImports = imports.filter { it.hasAlias() }.map { it.fqName }

            val defaultImportProvider = entryFile.getResolutionFacade()
                    .frontendService<DefaultImportProvider>()
            val defaultImports = defaultImportProvider.defaultImports
            val excludedImports = defaultImportProvider.excludedImports + aliasedImports

            fun ImportPath.isImportedByDefault() =
                    isImported(defaultImports, excludedImports)

            imports.filterNot { it.isImportedByDefault() }.map { it.render() }.sorted().forEach {
                append("import ")
                append(it)
                appendWhitespace("\n")
            }

            val visitor = TextBuildingVisitor(this)
            files.forEach {
                appendWhitespace("\n\n")
                it.accept(visitor)
            }
        }.toString()
    }

    private inner class IncludingVisitor : Visitor() {
        tailrec fun include(element: KtElement) {
            if (element !in included) {
                if (element.canBeExcluded) {
                    included.add(element)
                    element.accept(this)

                    element.overrides.forEach(this::include)
                }
                val parent = element.parent
                if (parent is KtElement) {
                    include(parent)
                }
            }
        }

        override fun visitKtElement(element: KtElement) {
            val bindingContext = element.analyze(BodyResolveMode.PARTIAL)
            element.references.filterIsInstance<KtReference>().forEach {
                it.resolveToDescriptors(bindingContext)
                        .filterNot { it.isExternal }
                        .mapNotNull { it.element }
                        .forEach(this::include)
            }
            super.visitKtElement(element)
        }

        override fun visitElement(element: PsiElement) {
            element.acceptChildren(childVisitor)
        }

        private val childVisitor = object : KtVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                if (declaration is KtCallableDeclaration
                        && declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)
                        && !declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                        && declaration.hasBody()
                        && !declaration.isIncluded) {
                    val overridden = declaration.allOverridden
                    if (overridden.hasNonExcluded()) {
                        include(declaration)
                    } else {
                        overridden.forEach { it.element?.addOverride(declaration) }
                    }
                }
                super.visitNamedDeclaration(declaration)
            }

            override fun visitKtElement(element: KtElement) {
                element.takeUnless { it.canBeExcluded }?.accept(this@IncludingVisitor)
            }
        }

        private val overridesMap =
                hashMapOf<KtElement, MutableList<KtCallableDeclaration>>()

        private fun KtElement.addOverride(override: KtCallableDeclaration) {
            overridesMap.getOrPut(this) { mutableListOf() } += override
        }

        private val KtElement.overrides: Collection<KtCallableDeclaration>
            get() = overridesMap[this] ?: emptyList()
    }

    private inner class ImportCollectingVisitor : IncludedElementsTreeVisitor() {
        val imports = hashSetOf<ImportPath>()

        override fun visitThisExpression(expression: KtThisExpression) {}

        override fun visitSuperExpression(expression: KtSuperExpression) {}

        override fun visitKtElement(element: KtElement) {
            if (element.isIncluded) {
                val bindingContext = element.analyze()
                val resolutionScope =
                        element.getResolutionScope(bindingContext, element.getResolutionFacade())

                element.references.filterIsInstance<KtReference>().forEach { reference ->
                    val companionContainingClass = bindingContext[
                            BindingContext.SHORT_REFERENCE_TO_COMPANION_OBJECT,
                            element as? KtReferenceExpression]

                    val targets = companionContainingClass?.let { listOf(it) }
                            ?: reference.resolveToDescriptors(bindingContext)

                    targets.map { it.getImportableDescriptor() }
                            .filterNot { it is PackageViewDescriptor }
                            .filter { reference.canBeResolvedViaImport(it) }
                            .filterNot { it.isAccessibleAsMember(resolutionScope) }
                            .mapTo(imports) {
                                it.toImportPath(alias = (element as? KtNameReferenceExpression)
                                        ?.getReferencedNameAsName())
                            }
                }
            }
            super.visitKtElement(element)
        }
    }

    private inner class TextBuildingVisitor(
            private val builder: TextBuilder
    ) : IncludedElementsTreeVisitor() {

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            val selector = expression.getQualifiedElementSelector()
            if (selector is KtNameReferenceExpression && !selector.isExternal
                    && expression.receiverSimpleName?.isPackageName == true) {
                expression.selectorExpression?.accept(this)
            } else {
                super.visitDotQualifiedExpression(expression)
            }
        }

        override fun visitUserType(type: KtUserType) {
            type.referenceExpression?.takeIf {
                !it.isExternal && type.qualifierSimpleName?.isPackageName == true
            }?.accept(this) ?: super.visitUserType(type)
        }

        override fun visitWhiteSpace(space: PsiWhiteSpace) {
            builder.appendWhitespace(space.text)
        }

        override fun visitComment(comment: PsiComment) {
            builder.append(comment.text)
        }

        override fun visitLeafElement(element: LeafPsiElement) {
            fun isOverride() = element.parent is KtModifierList
                    && element.elementType == KtTokens.OVERRIDE_KEYWORD

            fun isOverrideObsolete(): Boolean {
                val declaration = element.parent.parent
                return declaration is KtCallableDeclaration
                        && !declaration.allOverridden.hasNonExcluded()
            }

            if (!isOverride() || !isOverrideObsolete()) {
                builder.append(element.text)
            }
        }
    }

    private abstract inner class IncludedElementsTreeVisitor : Visitor() {
        override fun visitKtElement(element: KtElement) {
            if (element.isIncluded) {
                super.visitKtElement(element)
            }
        }

        override fun visitElement(element: PsiElement) {
            when (element) {
                is LeafPsiElement -> visitLeafElement(element)
                else -> element.acceptChildren(this)
            }
        }

        open fun visitLeafElement(element: LeafPsiElement) {}
    }

    private abstract class Visitor : KtVisitorVoid() {
        override fun visitPackageDirective(directive: KtPackageDirective) {}

        override fun visitImportList(importList: KtImportList) {}

        override fun visitAnnotation(annotation: KtAnnotation) {
            if (annotation.useSiteTarget?.getAnnotationUseSiteTarget()
                    != AnnotationUseSiteTarget.FILE) {
                super.visitAnnotation(annotation)
            }
        }
    }

    private val KtSimpleNameExpression.isExternal get() = target?.isExternal ?: true

    private val DeclarationDescriptor.isExternal
        get() = module != moduleDescriptor || element == null

    private val KtElement.isIncluded get() = this in included || !canBeExcluded

    private fun Set<CallableMemberDescriptor>.hasNonExcluded(): Boolean = any {
        it.isExternal || it.element?.isIncluded ?: true
    }

    private fun DeclarationDescriptor.toImportPath(alias: Name?) = ImportPath(
            fqName = if (isExternal) fqNameSafe else fqNameWithoutPackage,
            isAllUnder = false,
            alias = alias?.takeUnless { it == name })

    private val DeclarationDescriptor?.fqNameWithoutPackage: FqName get() = when (this) {
        null, is ModuleDescriptor, is PackageViewDescriptor, is PackageFragmentDescriptor ->
            FqName.ROOT
        else -> containingDeclaration.fqNameWithoutPackage.child(name)
    }
}
