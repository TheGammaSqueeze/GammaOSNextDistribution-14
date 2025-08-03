package android.permissionmultidevice.cts

import android.content.Context
import android.permission.PermissionManager
import android.permission.PermissionManager.PermissionState
import android.provider.Settings
import com.android.compatibility.common.util.SystemUtil

object PermissionUtils {
    fun getHostDeviceName(context: Context): String {
        return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }

    fun getAllPermissionStates(
        context: Context,
        packageName: String,
        companionDeviceId: String
    ): Map<String, PermissionState> {
        val permissionManager = context.getSystemService(PermissionManager::class.java)!!
        return SystemUtil.runWithShellPermissionIdentity<Map<String, PermissionState>> {
            permissionManager.getAllPermissionStates(packageName, companionDeviceId)
        }
    }
}
