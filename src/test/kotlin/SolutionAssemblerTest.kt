import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.refactoring.toPsiDirectory
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class SolutionAssemblerTest : LightCodeInsightFixtureTestCase() {
    private lateinit var mainFunction: KtNamedFunction
    private lateinit var expected: VirtualFile

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinProjectDescriptor()

    override fun setUp() {
        super.setUp()
        val directory = myFixture.copyDirectoryToProject("src", "").toPsiDirectory(project)!!
        this.mainFunction = directory.findMainFunction()!!
        expected = LocalFileSystem.getInstance().findFileByPath("$testDataPath/expected.kt")!!
    }

    override fun getTestDataPath() = "testData/${getTestName(true)}"

    fun testTrivial() = doTest()
    fun testChain() = doTest()
    fun testMultiFile() = doTest()
    fun testUnusedAbstractMethod() = doTest()
    fun testImportAs() = doTest()
    fun testOperator() = doTest()
    fun testThisSuperReference() = doTest()
    fun testEnumSecondaryConstructor() = doTest()
    // TODO: enable when it is fixed: https://youtrack.jetbrains.com/issue/KT-18706
    //fun testImportAsBackticked() = doTest()

    private fun doTest() {
        val text = SolutionAssembler().assembleSolution(mainFunction)
        val actual = LightVirtualFile("actual.kt", text)
        PlatformTestUtil.assertFilesEqual(expected, actual)
    }
}

private fun PsiDirectory.findMainFunction(): KtNamedFunction? {
    var mainFunction: KtNamedFunction? = null
    accept(object : PsiRecursiveElementVisitor() {
        override fun visitFile(file: PsiFile) {
            (file as? KtFile)?.findMainFunction()?.let {
                check(mainFunction == null) { "Multiple entry points" }
                mainFunction = it
            }
        }
    })
    return mainFunction
}
