package android.permissionmultidevice.cts

import com.android.compatibility.common.util.SystemUtil
import com.android.modules.utils.build.SdkLevel
import org.junit.Assert

object PackageManagementUtils {
    fun installPackage(
        apkPath: String,
        reinstall: Boolean = false,
        grantRuntimePermissions: Boolean = false,
        expectSuccess: Boolean = true,
        installSource: String? = null
    ) {
        val output =
            SystemUtil.runShellCommandOrThrow(
                    "pm install${if (SdkLevel.isAtLeastU()) " --bypass-low-target-sdk-block" else ""} " +
                        "${if (reinstall) " -r" else ""}${
                        if (grantRuntimePermissions) " -g"
                        else ""
                    }${if (installSource != null) " -i $installSource" else ""} $apkPath"
                )
                .trim()

        if (expectSuccess) {
            Assert.assertEquals("Success", output)
        } else {
            Assert.assertNotEquals("Success", output)
        }
    }

    fun uninstallPackage(packageName: String, requireSuccess: Boolean = true) {
        val output = SystemUtil.runShellCommand("pm uninstall $packageName").trim()
        if (requireSuccess) {
            Assert.assertEquals("Success", output)
        }
    }
}
