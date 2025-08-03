package android.platform.helpers

import android.platform.uiautomator_helpers.DeviceHelpers.shell
import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import android.util.Log
import java.time.Duration

/** Allows to execute operations such as restart on a process identififed by [packageName]. */
class ProcessUtil(private val packageName: String) {

    /** Restart [packageName] running `am crash <package-name>`. */
    fun restart() {
        val initialPids = getPids("initial")
        initialPids
            .map { pid -> "kill $pid" }
            .forEach { killCmd ->
                val result = shell(killCmd)
                Log.d(TAG, "Result of \"$killCmd\": \"$result\"")
            }
        ensureThat("All sysui process stopped", Duration.ofSeconds(30L)) {
           allProcessesStopped(initialPids)
        }
        ensureThat("All sysui process restarted", Duration.ofSeconds(30L)) {
           hasProcessRestarted(initialPids)
       }
    }

    private fun getPids(logTag: String): List<String> {
        val pidofResult = shell("pidof $packageName").trim()
        Log.d(TAG, "pidofResult($logTag) = \"$pidofResult\"")
        return if (pidofResult.isEmpty()) {
            emptyList()
        } else pidofResult.split("\\s".toRegex())
    }

    private fun allProcessesStopped(initialPidsList: List<String>): Boolean =
        (getPids("stopped") intersect initialPidsList).isEmpty()

    /**
     * We can only test if one process has restarted. If we match against the number of killed
     * processes, one may have spawned another process later, and this check would fail.
     *
     * @param initialPidsList list of pidof $packageName
     * @return true if there is a new process with name $packageName
     */
    private fun hasProcessRestarted(initialPidsList: List<String>): Boolean =
        (getPids("restarted") subtract initialPidsList).isNotEmpty()

    private companion object {
        const val TAG = "ProcessUtils"
    }
}
