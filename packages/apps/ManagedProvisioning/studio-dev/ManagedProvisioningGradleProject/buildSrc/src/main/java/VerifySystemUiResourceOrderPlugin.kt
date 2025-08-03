import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.tasks.MergeResources
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.configurationcache.extensions.capitalized

/**
 * Plugin that verifies that correct order is used for Google and AOSP SystemUI resources. When
 * there are multiple modules with the same resource, AGP will use the order of the dependencies
 * declaration.
 *
 * See https://developer.android.com/build/dependencies#dependency-order for more details
 */
abstract class VerifySystemUiResourceOrderPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.configure(AndroidComponentsExtension::class.java) {
            onVariants { variant ->
                project.afterEvaluate {
                    val capitalizedVariantName = variant.name.capitalized()
                    val mergeTask =
                        project.tasks.named(
                            "merge${capitalizedVariantName}Resources",
                            MergeResources::class.java
                        )

                    mergeTask.configure { doLast { verifyOrder(variant) } }
                }
            }
        }
    }

    private fun MergeResources.verifyOrder(variant: ComponentIdentity) {
        val projectPaths = librariesProjectPaths

        // The lower the index, the higher is the priority
        val googleResourcesPriority = projectPaths.indexOf(GOOGLE_RESOURCES_PROJECT)
        val aospResourcesPriority = projectPaths.indexOf(AOSP_RESOURCES_PROJECT)

        if (variant.isGoogleSpecific()) {
            if (googleResourcesPriority == INDEX_NOT_FOUND) {
                throw IllegalArgumentException(
                    "Project ${projectPath.get()} doesn't have $GOOGLE_RESOURCES_PROJECT dependency"
                )
            }
            if (aospResourcesPriority == INDEX_NOT_FOUND) {
                throw IllegalArgumentException(
                    "Project ${projectPath.get()} doesn't have $AOSP_RESOURCES_PROJECT dependency"
                )
            }

            if (googleResourcesPriority > aospResourcesPriority) {
                val prioritiesDescription =
                    "'$GOOGLE_RESOURCES_PROJECT' index: $googleResourcesPriority, " +
                        "'$AOSP_RESOURCES_PROJECT' index: $aospResourcesPriority"

                throw IllegalArgumentException(
                    "Invalid resource dependencies order, expected Google resources " +
                        "($GOOGLE_RESOURCES_PROJECT) to have higher priority " +
                        "(earlier in the list) than AOSP resources " +
                        "($AOSP_RESOURCES_PROJECT) for task ${this.name}.\n\n" +
                        prioritiesDescription
                )
            }
        }
    }

    private val MergeResources.librariesProjectPaths: List<String>
        get() =
            resourcesComputer.libraries
                .get()
                .map { it.id.componentIdentifier }
                .filterIsInstance<ProjectComponentIdentifier>()
                .map { it.projectPath }

    private fun ComponentIdentity.isGoogleSpecific(): Boolean =
        name.contains("google", ignoreCase = true) || name.contains("titan", ignoreCase = true)

    private companion object {
        private const val GOOGLE_RESOURCES_PROJECT = ":sysuig-resources"
        private const val AOSP_RESOURCES_PROJECT = ":SystemUI-res"
        private const val INDEX_NOT_FOUND = -1
    }
}
