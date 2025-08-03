import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.maybeCreate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import task.SymlinkSourcesTask
import java.io.File

fun Project.symlinkedSources(
        dir: String,
        name: String = File(dir).name,
        excludeSubdirectories: Boolean = true,
        test: Boolean = false,
        filter: Action<PatternFilterable> = Action { },
): FileCollection {
    val sourceDir = File(dir)
    val type = if (test) "test" else "main"
    val outputDir = rootDir.resolve(".symlinkSrc/${project.name}/${type}/${name}")
    val task = tasks.register<SymlinkSourcesTask>("symlink${type.capitalized()}Sources${name.capitalized()}") {
        description = "Filter sources from ${sourceDir.absolutePath} and link them to ${outputDir.absolutePath}"
        this.sourcesRoot.set(dir)
        this.files {
            exclude { !it.isDirectory && it.file.extension !in arrayOf("kt", "java") }
            if (excludeSubdirectories) {
                exclude { it.isDirectory }
            }
            filter.execute(this)
        }
        this.destinationDir.set(outputDir)
    }
    tasks.withType<KotlinCompile> { dependsOn(task) }
    tasks.withType<JavaCompile> { dependsOn(task) }
    tasks.maybeCreate<Delete>("cleanSymlinkedSources").apply {
        group = "symlink"
        delete(outputDir)
    }
    val symlinkSources = tasks.maybeCreate("symlinkSources").apply {
        group = "symlink"
        dependsOn(task)
    }
    rootProject.tasks.named("updateSdkSources") {
        dependsOn(symlinkSources)
    }
    return files(task.flatMap(SymlinkSourcesTask::destinationDir)) {
        builtBy(task)
    }
}
