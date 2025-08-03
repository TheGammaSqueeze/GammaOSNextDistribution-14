package android.permissionmultidevice.cts.accessremotedevicecamera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle

class RequestPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId =
            intent.getIntExtra(
                PackageManager.EXTRA_REQUEST_PERMISSIONS_DEVICE_ID,
                Context.DEVICE_ID_DEFAULT
            )

        requestPermissions(arrayOf(Manifest.permission.CAMERA), 1001, deviceId)
    }
}
