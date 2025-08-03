import org.gradle.api.tasks.AbstractExecTask

class CommandLineUtils {
    companion object {
        const val DEBUG = false

        fun debugPrintCommandLineArgs(task: AbstractExecTask<*>) {
            if (!DEBUG) return
            println("---- begin command-line ----")
            println("cd ${task.workingDir}")
            task.commandLine.forEachIndexed { i, s ->
                if (i != 0) print("    ")
                print("$s")
                if (i != task.commandLine.size) println(" \\") else println()
            }
            println("---- end command-line ----")
        }
    }
}
