import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.hasNoSideEffect
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.*

internal fun KtFile.findMainFunction() = children.filterIsInstance<KtNamedFunction>().singleOrNull {
    it.toDescriptor()?.let { MainFunctionDetector.isMain(it) } ?: false
}

// TODO: object literals without side effects, other complex expressions, this, super, arrays
internal val KtExpression.hasNoSideEffects: Boolean
    get() = hasNoSideEffect() || this is KtLambdaExpression || this is KtFunctionLiteral

internal val DeclarationDescriptor.element get() = findPsi() as? KtElement

internal val KtCallableDeclaration.allOverridden: Set<CallableMemberDescriptor>
    get() = (toDescriptor() as? CallableMemberDescriptor)?.let {
        DescriptorUtils.getAllOverriddenDeclarations(it)
    } ?: emptySet()

internal val KtDotQualifiedExpression.receiverSimpleName: KtSimpleNameExpression? get() {
    val receiverExpression = receiverExpression
    return ((receiverExpression as? KtDotQualifiedExpression)?.selectorExpression
            ?: receiverExpression) as? KtSimpleNameExpression
}

internal val KtUserType.qualifierSimpleName get() = qualifier?.referenceExpression

internal val KtSimpleNameExpression.isPackageName get() = target is PackageViewDescriptor

internal val KtSimpleNameExpression.target
    get() = resolveMainReferenceToDescriptors().singleOrNull()

// Taken from KotlinImportOptimizer.CollectUsedDescriptorsVisitor.isAccessibleAsMember
internal fun DeclarationDescriptor.isAccessibleAsMember(resolutionScope: LexicalScope): Boolean {
    if (containingDeclaration !is ClassDescriptor) return false

    fun isInScope(scope: HierarchicalScope): Boolean {
        return when (this) {
            is FunctionDescriptor ->
                scope.findFunction(name, NoLookupLocation.FROM_IDE) { it == this } != null

            is PropertyDescriptor ->
                scope.findVariable(name, NoLookupLocation.FROM_IDE) { it == this } != null

            is ClassDescriptor ->
                scope.findClassifier(name, NoLookupLocation.FROM_IDE) == this

            else -> false
        }
    }

    val noImportsScope = resolutionScope.replaceImportingScopes(null)

    return when {
        isInScope(noImportsScope) -> true
        // classes not accessible through receivers, only their constructors
        this !is ClassDescriptor -> resolutionScope.getImplicitReceiversHierarchy().any {
            isInScope(it.type.memberScope.memberScopeAsImportingScope())
        }
        else -> false
    }
}

fun ImportPath.render(): String {
    return pathStr + (alias?.render()?.let { " as $it" } ?: "")
}
