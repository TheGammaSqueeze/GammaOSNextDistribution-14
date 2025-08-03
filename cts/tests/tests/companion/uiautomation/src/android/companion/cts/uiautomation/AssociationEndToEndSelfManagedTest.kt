package android.companion.cts.uiautomation

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
import android.companion.AssociationRequest.DEVICE_PROFILE_GLASSES
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.CompanionDeviceManager
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.RecordingCallback.OnAssociationCreated
import android.content.Intent
import android.platform.test.annotations.AppModeFull
import com.android.compatibility.common.util.FeatureUtil
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests the Association Flow end-to-end.
 *
 * Build/Install/Run:
 * atest CtsCompanionDeviceManagerUiAutomationTestCases:AssociationEndToEndSelfManagedTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(Parameterized::class)
class AssociationEndToEndSelfManagedTest(
    profile: String?,
    profilePermission: String?,
    profileName: String // Used only by the Parameterized test runner for tagging.
) : UiAutomationTestBase(profile, profilePermission) {

    override fun setUp() {
        super.setUp()

        assumeFalse(FeatureUtil.isWatch())
        // confirmation UI (the "self-managed" flow variant).
        // Watch and glasses profiles are not supported for self-managed association flow.
        assumeFalse(profile == null)
        assumeFalse(profile == DEVICE_PROFILE_WATCH)
        assumeFalse(profile == DEVICE_PROFILE_GLASSES)
        // Do not need to test the automotive_projection profile since it does not have
        // the UI.
        assumeFalse(profile == DEVICE_PROFILE_AUTOMOTIVE_PROJECTION)
    }

    @Test
    fun test_userRejected() = super.test_userRejected(
            singleDevice = false, selfManaged = true, displayName = DEVICE_DISPLAY_NAME)

    @Test
    fun test_userDismissed() = super.test_userDismissed(
            singleDevice = false, selfManaged = true, displayName = DEVICE_DISPLAY_NAME)

    @Test
    fun test_userConfirmed() {
        sendRequestAndLaunchConfirmation(selfManaged = true, displayName = DEVICE_DISPLAY_NAME)

        if (profile != null) {
            confirmationUi.scrollToBottom()
        }

        callback.assertInvokedByActions {
            // User "approves" the request.
            confirmationUi.clickPositiveButton()
        }
        // Check callback invocations: there should have been exactly 1 invocation of the
        // OnAssociationCreated() method.
        assertEquals(1, callback.invocations.size)
        val associationInvocation = callback.invocations.first()
        assertIs<OnAssociationCreated>(associationInvocation)
        val associationFromCallback = associationInvocation.associationInfo
        assertEquals(actual = associationFromCallback.displayName, expected = DEVICE_DISPLAY_NAME)

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code and the data delivered via onActivityResult()
        val (resultCode: Int, data: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = Activity.RESULT_OK)
        assertNotNull(data)
        val associationFromActivityResult: AssociationInfo? =
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION)
        assertNotNull(associationFromActivityResult)
        // Check that the association reported back via the callback same as the association
        // delivered via onActivityResult().
        assertEquals(associationFromCallback, associationFromActivityResult)

        // Make sure getMyAssociations() returns the same association we received via the callback
        // as well as in onActivityResult()
        assertContentEquals(actual = cdm.myAssociations, expected = listOf(associationFromCallback))

        // Make sure that the role (for the current CDM device profile) was granted.
        assertIsProfileRoleHolder()
    }

    companion object {
        /**
         * List of (profile, permission, name) tuples that represent all supported profiles and
         * null.
         * Each test will be suffixed with "[profile=<NAME>]", e.g.: "[profile=WATCH]".
         */
        @Parameterized.Parameters(name = "profile={2}")
        @JvmStatic
        fun parameters() = supportedProfilesAndNull()

        private const val DEVICE_DISPLAY_NAME = "My Device"
    }
}
