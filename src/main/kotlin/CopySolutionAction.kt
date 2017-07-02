import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private fun AnActionEvent.findMainFunction() =
        (getData(LangDataKeys.PSI_FILE) as? KtFile)?.findMainFunction()

class CopySolutionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val mainFunction = e.findMainFunction() ?: return

        val text = SolutionAssembler().assembleSolution(mainFunction)

        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

        Notifications.Bus.notify(Notification(
                "Kotlin Solution",
                "Copy Kotlin Solution",
                "The solution has been copied to the clipboard",
                NotificationType.INFORMATION))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.findMainFunction() != null
    }
}
