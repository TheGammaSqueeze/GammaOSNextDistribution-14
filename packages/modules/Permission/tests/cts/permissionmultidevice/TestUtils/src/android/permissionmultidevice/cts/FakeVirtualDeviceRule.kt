package android.permissionmultidevice.cts

import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplayConfig
import android.media.ImageReader
import android.virtualdevice.cts.common.FakeAssociationRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth
import org.junit.Assume

/** A test rule that creates a virtual device for the duration of the test. */
class FakeVirtualDeviceRule : FakeAssociationRule() {

    private val imageReader =
        ImageReader.newInstance(
            /* width= */ DISPLAY_WIDTH,
            /* height= */ DISPLAY_HEIGHT,
            PixelFormat.RGBA_8888,
            /* maxImages= */ 1
        )

    private lateinit var virtualDeviceManager: VirtualDeviceManager
    lateinit var virtualDevice: VirtualDeviceManager.VirtualDevice
    lateinit var deviceDisplayName: String
    var virtualDisplayId: Int = -1

    override fun before() {
        // Call FakeAssociationRule#before() to create a CDM association to be used by VDM
        super.before()

        SystemUtil.callWithShellPermissionIdentity {
            val virtualDeviceManager =
                getApplicationContext<Context>().getSystemService(VirtualDeviceManager::class.java)
            Assume.assumeNotNull(virtualDeviceManager)
            this.virtualDeviceManager = virtualDeviceManager!!
            virtualDevice =
                virtualDeviceManager.createVirtualDevice(
                    associationInfo.id,
                    VirtualDeviceParams.Builder().build()
                )
            val display =
                virtualDevice.createVirtualDisplay(
                    VirtualDisplayConfig.Builder("testDisplay", DISPLAY_WIDTH, DISPLAY_HEIGHT, 240)
                        .setSurface(imageReader.surface)
                        .setFlags(
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED or
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        )
                        .build(),
                    Runnable::run,
                    null
                )
            Truth.assertThat(display).isNotNull()
            virtualDisplayId = display!!.display.displayId
            deviceDisplayName =
                virtualDeviceManager
                    .getDisplayNameForPersistentDeviceId(virtualDevice.persistentDeviceId!!)
                    .toString()
        }
    }

    override fun after() {
        // Call FakeAssociationRule#after() to remote CDM association
        super.after()

        SystemUtil.callWithShellPermissionIdentity { virtualDevice.close() }
        imageReader.close()
    }

    companion object {
        private const val DISPLAY_HEIGHT = 1920
        private const val DISPLAY_WIDTH = 1080
    }
}
