package android.devicepolicy.cts

import android.app.admin.SystemUpdateInfo.SECURITY_PATCH_STATE_FALSE
import android.app.admin.SystemUpdateInfo.SECURITY_PATCH_STATE_TRUE
import android.app.admin.SystemUpdateInfo.SECURITY_PATCH_STATE_UNKNOWN
import android.app.admin.flags.Flags
import android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN
import android.content.pm.PackageManager.FEATURE_MANAGED_USERS
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.policies.ReceiveSystemUpdateCallback
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith


@RunWith(BedsteadJUnit4::class)
@RequireFeature(FEATURE_DEVICE_ADMIN)
@RequireFeature(FEATURE_MANAGED_USERS)
// Always filter by the 'receivedTime' since there can be false positives where the real system
// update service triggers the callback on the device.
class PendingSystemUpdateTest {

    // TODO(324373863): Replace with infra support for flagged policy changes
    @Before
    fun skipDpmRoleHolderTestIfFlagIsNotEnabled() {
        try {
            if (deviceState.dpc() == deviceState.dpmRoleHolder()) {
                val flagIsEnabled = Flags.permissionMigrationForZeroTrustImplEnabled()
                assumeTrue("This test only runs with flag "
                        + Flags.FLAG_PERMISSION_MIGRATION_FOR_ZERO_TRUST_IMPL_ENABLED
                        + " is enabled", flagIsEnabled)
            }
        } catch (e: IllegalStateException) {
            // Fine - we don't have one
        }
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#notifyPendingSystemUpdate",
            "android.app.admin.DeviceAdminReceiver#onSystemUpdatePending"]
    )
    @CanSetPolicyTest(policy = [ReceiveSystemUpdateCallback::class])
    fun notifyPendingSystemUpdate_unknownIfSecurityPatch_dpcReceivesCallback() {
        val receivedTime = System.currentTimeMillis()

        TestApis.devicePolicy().notifyPendingSystemUpdate(receivedTime)

        assertThat(
            deviceState.dpc().events().systemUpdatePending()
                .whereReceivedTime().isEqualTo(receivedTime)
        ).eventOccurred()
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#notifyPendingSystemUpdate",
            "android.app.admin.DeviceAdminReceiver#onSystemUpdatePending"]
    )
    @CanSetPolicyTest(policy = [ReceiveSystemUpdateCallback::class])
    fun notifyPendingSystemUpdate_forSecurityPatch_dpcReceivesCallback() {
        val receivedTime = System.currentTimeMillis()

        TestApis.devicePolicy().notifyPendingSystemUpdate(receivedTime,  /* isSecurityPatch= */true)

        assertThat(
            deviceState.dpc().events().systemUpdatePending()
                .whereReceivedTime().isEqualTo(receivedTime)
        ).eventOccurred()
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#notifyPendingSystemUpdate",
            "android.app.admin.DeviceAdminReceiver#onSystemUpdatePending"]
    )
    @CanSetPolicyTest(policy = [ReceiveSystemUpdateCallback::class])
    fun notifyPendingSystemUpdate_forNonSecurityPatch_dpcReceivesCallback() {
        val receivedTime = System.currentTimeMillis()

        TestApis.devicePolicy()
            .notifyPendingSystemUpdate(receivedTime,  /* isSecurityPatch= */false)

        assertThat(
            deviceState.dpc().events().systemUpdatePending()
                .whereReceivedTime().isEqualTo(receivedTime)
        ).eventOccurred()
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#notifyPendingSystemUpdate",
            "android.app.admin.DevicePolicyManager#getPendingSystemUpdate"]
    )
    @CanSetPolicyTest(policy = [ReceiveSystemUpdateCallback::class])
    fun getPendingSystemUpdate_unknownIfSecurityPatch() {
        TestApis.devicePolicy().notifyPendingSystemUpdate(System.currentTimeMillis())
        val securityPatchState = deviceState.dpc().devicePolicyManager()
            .getPendingSystemUpdate(deviceState.dpc().componentName())
            .securityPatchState
        assertThat(securityPatchState).isEqualTo(SECURITY_PATCH_STATE_UNKNOWN)
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#notifyPendingSystemUpdate",
            "android.app.admin.DevicePolicyManager#getPendingSystemUpdate"]
    )
    @CanSetPolicyTest(policy = [ReceiveSystemUpdateCallback::class])
    fun getPendingSystemUpdate_forSecurityPatch() {
        TestApis.devicePolicy().notifyPendingSystemUpdate(
            System.currentTimeMillis(),  /* isSecurityPatch= */true
        )

        val securityPatchState = deviceState.dpc().devicePolicyManager()
            .getPendingSystemUpdate(deviceState.dpc().componentName())
            .securityPatchState

        assertThat(securityPatchState).isEqualTo(SECURITY_PATCH_STATE_TRUE)
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#notifyPendingSystemUpdate",
            "android.app.admin.DevicePolicyManager#getPendingSystemUpdate"]
    )
    @CanSetPolicyTest(policy = [ReceiveSystemUpdateCallback::class])
    fun getPendingSystemUpdate_forNonSecurityPatch() {
        TestApis.devicePolicy().notifyPendingSystemUpdate(
            System.currentTimeMillis(),  /* isSecurityPatch= */false
        )

        val securityPatchState = deviceState.dpc().devicePolicyManager()
            .getPendingSystemUpdate(deviceState.dpc().componentName())
            .securityPatchState

        assertThat(securityPatchState).isEqualTo(SECURITY_PATCH_STATE_FALSE)
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#notifyPendingSystemUpdate",
            "android.app.admin.DevicePolicyManager#getPendingSystemUpdate"]
    )
    @CanSetPolicyTest(policy = [ReceiveSystemUpdateCallback::class])
    fun getPendingSystemUpdate_withReceivedTime() {
        val receivedTime = System.currentTimeMillis()

        TestApis.devicePolicy().notifyPendingSystemUpdate(receivedTime)

        val actualReceivedTime: Long =
            deviceState.dpc().devicePolicyManager().getPendingSystemUpdate(
                deviceState.dpc().componentName()
            ).receivedTime
        assertThat(actualReceivedTime).isEqualTo(receivedTime)
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#notifyPendingSystemUpdate",
            "android.app.admin.DevicePolicyManager#getPendingSystemUpdate"]
    )
    @CanSetPolicyTest(policy = [ReceiveSystemUpdateCallback::class])
    fun getPendingSystemUpdate_doesNotExistYet() {
        TestApis.devicePolicy().notifyPendingSystemUpdate(-1)

        assertThat(
            deviceState.dpc().devicePolicyManager().getPendingSystemUpdate(
                deviceState.dpc().componentName()
            )
        ).isNull()
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getPendingSystemUpdate"])
    @CannotSetPolicyTest(
        policy = [ReceiveSystemUpdateCallback::class],
        includeNonDeviceAdminStates = false
    )
    fun getPendingSystemUpdate_notAllowed_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().getPendingSystemUpdate(
                deviceState.dpc().componentName()
            )
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }

}
