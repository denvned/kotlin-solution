import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.util.SystemProperties
import org.jetbrains.kotlin.idea.framework.JavaRuntimeLibraryDescription
import org.jetbrains.kotlin.utils.KotlinPathsFromHomeDir
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

internal class KotlinProjectDescriptor : DefaultLightProjectDescriptor() {
    private lateinit var library: Library

    override fun getSdk(): Sdk = JavaSdk.getInstance()
            .createJdk("Full JDK", SystemProperties.getJavaHome(), true)

    override fun createMainModule(project: Project): Module {
        addLibrary(project)
        return super.createMainModule(project)
    }

    private fun addLibrary(project: Project) {
        val table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        library = table.createLibrary(JavaRuntimeLibraryDescription.LIBRARY_NAME)
        addJars()
    }

    private fun addJars() {
        val model = library.modifiableModel
        val paths = getKotlinPaths()
        arrayOf(paths.reflectPath, paths.runtimePath).forEach {
            model.addRoot(VfsUtil.getUrlForLibraryRoot(it), OrderRootType.CLASSES)
        }
        model.commit()
    }

    override fun configureModule(
            module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        model.addLibraryEntry(library)
    }
}

private fun getKotlinPaths(): KotlinPathsFromHomeDir {
    val pluginHome = PathUtil.getPathUtilJar().parentFile.parentFile
    val home = File(pluginHome, PathUtil.HOME_FOLDER_NAME)
    return KotlinPathsFromHomeDir(home)
}
