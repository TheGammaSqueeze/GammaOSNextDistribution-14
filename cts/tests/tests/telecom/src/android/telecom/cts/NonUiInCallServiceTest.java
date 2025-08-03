package android.telecom.cts;

import static android.telecom.cts.TestUtils.TEST_PHONE_ACCOUNT_HANDLE;
import static android.telecom.cts.TestUtils.waitOnAllHandlers;

import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.ComponentInfo;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.telecom.cts.api29incallservice.CtsApi29InCallService;
import android.telecom.cts.api29incallservice.ICtsApi29InCallServiceControl;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import java.util.Arrays;

public class NonUiInCallServiceTest extends BaseTelecomTestWithMockServices {
    private static final String LOG_TAG = NonUiInCallServiceTest.class.getSimpleName();

    private ServiceConnection mServiceConnection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_2);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            mTelecomManager.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_2);
        }
        super.tearDown();
        waitOnAllHandlers(getInstrumentation());
    }

    public void testMidCallComponentEnablement() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        "android.permission.CONTROL_INCALL_EXPERIENCE",
                        "android.permission.CHANGE_COMPONENT_ENABLED_STATE");
        try {
            mContext.getPackageManager().setComponentEnabledSetting(
                    ComponentName.createRelative(CtsApi29InCallService.PACKAGE_NAME,
                            "." + CtsApi29InCallService.class.getSimpleName()),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
            ICtsApi29InCallServiceControl controlInterface = setUpControl();

            addAndVerifyNewIncomingCall(createTestNumber(), new Bundle());
            waitOnAllHandlers(getInstrumentation());
            assertFalse("Non-UI incall incorrectly bound to despite being disabled",
                    controlInterface.hasReceivedBindRequest());

            mContext.getPackageManager().setComponentEnabledSetting(
                    ComponentName.createRelative(CtsApi29InCallService.PACKAGE_NAME,
                            "." + CtsApi29InCallService.class.getSimpleName()),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);

            boolean hasBound = controlInterface.waitForBindRequest();
            assertTrue("InCall was not bound to", hasBound);
            waitOnAllHandlers(getInstrumentation());

            assertEquals("Call was not sent to incall", 1, controlInterface.getLocalCallCount());

            try {
                controlInterface.kill();
            } catch (DeadObjectException e) {
                //expected
            }
            tearDownControl();
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /**
     * Similar to {@link #testMidCallComponentEnablement()}, except this CTS test is explicitly
     * ensuring that a non-UI InCallService will be bound to if there were no non-UI InCallServices
     * available at the time the call was originally added.  This mimics a scenario where
     * BluetoothInCallService is not enabled because BT is turned off.  If the user turns ON
     * bluetooth after the start of the call, Telecom was unable to do the mid-call component
     * enablement.
     * @throws Exception
     */
    public void testMidCallComponentEnablementWithNoneAvailableAtStart() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        final Uri testAddress = Uri.fromParts("tel", "6505551213", null);
        SelfManagedConnection connection = null;

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        "android.permission.CONTROL_INCALL_EXPERIENCE",
                        "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
                        "android.permission.BLUETOOTH_CONNECT",
                        "android.permission.BLUETOOTH_PRIVILEGED");
        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        try {
            // Note: Since the CtsApi29Ics app is a separate app its fine to kill the app when
            // disabling it since it shouldn't be running anyways.
            mContext.getPackageManager().setComponentEnabledSetting(
                    ComponentName.createRelative(CtsApi29InCallService.PACKAGE_NAME,
                            "." + CtsApi29InCallService.class.getSimpleName()),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0 /* doKillApp */);

            // Disable Bluetooth to make sure the Bluetooth ICS is not running.
            bluetoothManager.getAdapter().disable(false);

            // Ensure that the CTS test's ICS is disabled; we want to get to a state where there are
            // no non UI ICS that can get bound.
            mContext.getPackageManager().setComponentEnabledSetting(
                    ComponentName.createRelative(MockInCallService.class.getPackage().getName(),
                            "." + MockInCallService.class.getSimpleName()),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

            ICtsApi29InCallServiceControl controlInterface = setUpControl();

            TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                    TestUtils.TEST_SELF_MANAGED_HANDLE_2, testAddress);
            connection = TestUtils.waitForAndGetConnection(testAddress);

            if (!CtsSelfManagedConnectionService.waitForBinding()) {
                fail("Could not bind to Self-Managed ConnectionService");
            }
            assertFalse("Non-UI incall incorrectly bound to despite being disabled",
                    controlInterface.hasReceivedBindRequest());

            mContext.getPackageManager().setComponentEnabledSetting(
                    ComponentName.createRelative(CtsApi29InCallService.PACKAGE_NAME,
                            "." + CtsApi29InCallService.class.getSimpleName()),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            boolean hasBound = controlInterface.waitForBindRequest();
            assertTrue("InCall was not bound to", hasBound);
            waitOnAllHandlers(getInstrumentation());

            assertEquals("Call was not sent to incall", 1, controlInterface.getLocalCallCount());
            try {
                controlInterface.kill();
            } catch (DeadObjectException e) {
                //expected
            }
            tearDownControl();
            if (connection != null) {
                connection.disconnectAndDestroy();
            }
        } finally {
            if (connection != null) {
                connection.disconnectAndDestroy();
            }
            // Re-enable Bluetooth to make sure the ICS it has is not running.
            bluetoothManager.getAdapter().enable();

            // Always ensure the CTS ICS is re-enabled.
            mContext.getPackageManager().setComponentEnabledSetting(
                    ComponentName.createRelative(MockInCallService.class.getPackage().getName(),
                            "." + MockInCallService.class.getSimpleName()),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    public void testNullBinding() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        "android.permission.CONTROL_INCALL_EXPERIENCE",
                        "android.permission.CHANGE_COMPONENT_ENABLED_STATE");
        try {
            mContext.getPackageManager().setComponentEnabledSetting(
                    ComponentName.createRelative(CtsApi29InCallService.PACKAGE_NAME,
                            "." + CtsApi29InCallService.class.getSimpleName()),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            ICtsApi29InCallServiceControl controlInterface = setUpControl();
            controlInterface.setShouldReturnNullBinding(true);

            int currentCallCount = addNewIncomingCall(createTestNumber(), new Bundle());
            // The test InCallService can be bound and unbound before this test gets a chance to
            // validate. Ensure that the test verifies it is bound before checking if onCallAdded
            // was called.
            assertTrue("Non-UI incall incorrectly not bound to despite being enabled",
                    controlInterface.waitForBindRequest());
            verifyNewIncomingCall(currentCallCount);

            assertEquals("Call was sent to incall despite null binding",
                    0, controlInterface.getLocalCallCount());

            try {
                controlInterface.kill();
            } catch (DeadObjectException e) {
                //expected
            }
            tearDownControl();
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }
    private ICtsApi29InCallServiceControl setUpControl() throws Exception {
        Pair<ServiceConnection, ICtsApi29InCallServiceControl> setupResult =
                Api29InCallUtils.setupControl(mContext);
        mServiceConnection = setupResult.first;
        return setupResult.second;
    }

    private void tearDownControl() throws Exception {
        Api29InCallUtils.tearDownControl(mContext,
                mServiceConnection);
    }
}