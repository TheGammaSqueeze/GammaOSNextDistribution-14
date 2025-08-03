package android.app.cts

import android.app.StatusBarManager
import android.media.NearbyDevice
import android.media.NearbyMediaDevicesProvider
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.function.Consumer
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for {@link StatusBarManager.registerNearbyMediaDevicesProvider} and
 * {@link StatusBarManager.unregisterNearbyMediaDevicesProvider}.
 */
@RunWith(AndroidJUnit4::class)
class NearbyMediaDevicesProviderTest {
    private lateinit var statusBarManager: StatusBarManager

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        statusBarManager = instrumentation
            .getTargetContext()
            .getSystemService(StatusBarManager::class.java)!!
    }

    @Test(expected = SecurityException::class)
    fun registerNearbyMediaDevicesProvider_noPermission_throwsSecurityException() {
        statusBarManager.registerNearbyMediaDevicesProvider(TestProvider())
    }

    @Test(expected = SecurityException::class)
    fun unregisterNearbyMediaDevicesProvider_noPermission_throwsSecurityException() {
        val provider = TestProvider()
        // First, register a provider so we have something to unregister.
        runWithShellPermissionIdentity(
            ThrowingSupplier {
                statusBarManager.registerNearbyMediaDevicesProvider(provider)
            },
            MEDIA_PERMISSION
        )

        // Then, try to unregister without the permission.
        statusBarManager.unregisterNearbyMediaDevicesProvider(provider)
    }

    @Test
    fun registerNearbyMediaDevicesProvider_hasPermission_noCrash() {
        // No assert, just no crash needed
        runWithShellPermissionIdentity(
            ThrowingSupplier {
                statusBarManager.registerNearbyMediaDevicesProvider(TestProvider())
            },
            MEDIA_PERMISSION
        )
    }

    @Test
    fun unregisterNearbyMediaDevicesProvider_hasPermission_noCrash() {
        // No assert, just no crash needed
        runWithShellPermissionIdentity(
            ThrowingSupplier {
                statusBarManager.unregisterNearbyMediaDevicesProvider(TestProvider())
            },
            MEDIA_PERMISSION
        )
    }

    // No other CTS tests necessary: The API guarantees that applications can set a provider, but
    // guarantees nothing about how that provider might be used.

    private class TestProvider : NearbyMediaDevicesProvider {
        override fun registerNearbyDevicesCallback(callback: Consumer<List<NearbyDevice>>) {}
        override fun unregisterNearbyDevicesCallback(callback: Consumer<List<NearbyDevice>>) {}
    }
}

private val MEDIA_PERMISSION: String = android.Manifest.permission.MEDIA_CONTENT_CONTROL
