/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telecom.cts;

import static android.media.AudioManager.MODE_IN_COMMUNICATION;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.app.UiModeManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.UserHandle;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.carmodetestapp.CtsCarModeInCallServiceControl;
import android.telecom.cts.carmodetestapp.ICtsCarModeInCallServiceControl;
import android.telecom.cts.carmodetestappselfmanaged.CtsCarModeInCallServiceControlSelfManaged;
import android.telecom.cts.carmodetestapptwo.CtsCarModeInCallServiceControlTwo;
import android.telecom.cts.selfmanagedcstestapp.ICtsSelfManagedConnectionServiceControl;
import android.telecom.cts.selfmanagedcstestappone.CtsSelfManagedConnectionServiceControlOne;
import android.telecom.cts.thirdptydialer.CtsThirdPtyDialerInCallServiceControl;
import android.telecom.cts.thirdptydialertwo.CtsThirdPtyDialerInCallServiceControlTwo;
import android.telecom.cts.thirdptyincallservice.CtsThirdPartyInCallServiceControl;
import android.telecom.cts.thirdptyincallservice.ICtsThirdPartyInCallServiceControl;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class SelfManagedConnectionTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = "SelfManagedConnectionTest";
    private static final long TIMEOUT = 3000L;
    private static final String THIRD_PTY_CONTROL =
            CtsThirdPartyInCallServiceControl.CONTROL_INTERFACE_ACTION;
    private static final String CAR_MODE_CONTROL =
            "android.telecom.cts.carmodetestapp.ACTION_CAR_MODE_CONTROL";
    private static final ComponentName NON_UI_INCALLSERVICE = ComponentName.createRelative(
            CtsThirdPartyInCallServiceControl.class.getPackage().getName(),
            CtsThirdPartyInCallServiceControl.class.getName());
    // Default dialer that not support self-managed calls
    private static final String DEFAULT_DIALER_PKG_1 = CtsThirdPtyDialerInCallServiceControl.class
            .getPackage().getName();
    private static final ComponentName DEFAULT_DIALER_INCALLSERVICE_1 = ComponentName
            .createRelative(DEFAULT_DIALER_PKG_1,
                    CtsThirdPtyDialerInCallServiceControl.class.getName());
    // Default dialerhat support self-managed calls
    private static final String DEFAULT_DIALER_PKG_2 = CtsThirdPtyDialerInCallServiceControlTwo
            .class.getPackage().getName();
    private static final ComponentName DEFAULT_DIALER_INCALLSERVICE_2 = ComponentName
            .createRelative(DEFAULT_DIALER_PKG_2,
                    CtsThirdPtyDialerInCallServiceControlTwo.class.getName());
    private static final String CAR_DIALER_PKG_1 = CtsCarModeInCallServiceControl.class
            .getPackage().getName();
    private static final ComponentName CAR_DIALER_1 = ComponentName.createRelative(
            CAR_DIALER_PKG_1, CtsCarModeInCallServiceControl.class.getName());
    private static final String CAR_DIALER_PKG_2 = CtsCarModeInCallServiceControlTwo.class
            .getPackage().getName();
    private static final String CAR_SELF_MANAGED_PKG =
            CtsCarModeInCallServiceControlSelfManaged.class
                    .getPackage().getName();
    private static final ComponentName CAR_DIALER_2 = ComponentName.createRelative(
            CAR_DIALER_PKG_2, CtsCarModeInCallServiceControlTwo.class.getName());
    private static final ComponentName CAR_SELF_MANAGED_COMPONENT = ComponentName.createRelative(
            CAR_SELF_MANAGED_PKG, CtsCarModeInCallServiceControlSelfManaged.class.getName());
    private static final String SELF_MANAGED_CS_CONTROL =
            "android.telecom.cts.selfmanagedcstestapp.ACTION_SELF_MANAGED_CS_CONTROL";

    private static final String SELF_MANAGED_CS_PKG_1 =
            CtsSelfManagedConnectionServiceControlOne.class.getPackage().getName();
    private static final ComponentName SELF_MANAGED_CS_1 = ComponentName.createRelative(
            SELF_MANAGED_CS_PKG_1, CtsSelfManagedConnectionServiceControlOne.class.getName());

    private Uri TEST_ADDRESS = Uri.fromParts("tel", "6505551213", null);

    private static final PhoneAccountHandle TEST_CAR_SELF_MANAGED_HANDLE =
            new PhoneAccountHandle(
                    new ComponentName(CAR_SELF_MANAGED_PKG, TestUtils.SELF_MANAGED_COMPONENT),
                    TestUtils.SELF_MANAGED_ACCOUNT_ID_1);

    private static final PhoneAccount TEST_SELF_MANAGED_PHONE_ACCOUNT = PhoneAccount.builder(
                    TEST_CAR_SELF_MANAGED_HANDLE, TestUtils.SELF_MANAGED_ACCOUNT_LABEL)
            .setAddress(Uri.parse("sip:test@test.com"))
            .setSubscriptionAddress(Uri.parse("sip:test@test.com"))
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED
                    | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                    | PhoneAccount.CAPABILITY_VIDEO_CALLING)
            .setHighlightColor(Color.BLUE)
            .setShortDescription(TestUtils.SELF_MANAGED_ACCOUNT_LABEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
            .setExtras(TestUtils.SELF_MANAGED_ACCOUNT_1_EXTRAS)
            .build();

    private RoleManager mRoleManager;
    private String mDefaultDialer;
    private UiAutomation mUiAutomation;
    private ICtsCarModeInCallServiceControl mCarModeIncallServiceControlOne;
    private ICtsCarModeInCallServiceControl mCarModeIncallServiceControlTwo;
    private ICtsCarModeInCallServiceControl mCarModeIncallServiceControlSelfManaged;

    private class TestServiceConnection implements ServiceConnection {
        private IBinder mService;
        private CountDownLatch mLatch = new CountDownLatch(1);
        private boolean mIsConnected;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service Connected: " + componentName);
            mService = service;
            mIsConnected = true;
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }

        public IBinder getService() {
            return mService;
        }

        public boolean waitBind() {
            try {
                mLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
                return mIsConnected;
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        if (!mShouldTestTelecom) {
            mShouldTestTelecom = false;
            return;
        }
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        mUiAutomation = getInstrumentation().getUiAutomation();
        if (mShouldTestTelecom) {
            mRoleManager = mContext.getSystemService(RoleManager.class);
            setupConnectionService(null, FLAG_ENABLE | FLAG_REGISTER);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_4);
            if(mRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                mDefaultDialer = getDefaultDialer();
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (mShouldTestTelecom) {
                disableAndVerifyCarMode(mCarModeIncallServiceControlOne,
                        Configuration.UI_MODE_TYPE_NORMAL);
                disableAndVerifyCarMode(mCarModeIncallServiceControlTwo,
                        Configuration.UI_MODE_TYPE_NORMAL);

                disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);
                disconnectAllCallsAndVerify(mCarModeIncallServiceControlTwo);

                CtsSelfManagedConnectionService connectionService =
                        CtsSelfManagedConnectionService.getConnectionService();
                if (connectionService != null) {
                    connectionService.tearDown();
                    mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_4);
                    if (mRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                        assertTrue(setDefaultDialer(mDefaultDialer));
                    }
                }
            }
        } finally {
            // Force tearDown if setUp errors out to ensure unused listeners are cleaned up.
            super.tearDown();
        }
    }

    /**
     * Test bind to non-UI in call services that support self-managed connections
     */
    public void testBindToSupportNonUiInCallService() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(THIRD_PTY_CONTROL,
                NON_UI_INCALLSERVICE);
        ICtsThirdPartyInCallServiceControl control = ICtsThirdPartyInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        control.resetLatchForServiceBound(true /* bind */);

        mUiAutomation.adoptShellPermissionIdentity("android.permission.CONTROL_INCALL_EXPERIENCE");
        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        control.checkBindStatus(true /* bindStatus */);
        assertTrue(control.checkBindStatus(true /* bindStatus */));
        connection.waitOnInCallServiceTrackingChanged();
        assertTrue(connection.isTracked());
        mUiAutomation.dropShellPermissionIdentity();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    /**
     * Test bind to default dialer that support self-managed connections when device is not in car
     * mode
     */
    public void testBindToSupportDefaultDialerNoCarMode() throws Exception {
        if (!mShouldTestTelecom || !mRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(THIRD_PTY_CONTROL,
                DEFAULT_DIALER_INCALLSERVICE_2);
        ICtsThirdPartyInCallServiceControl control = ICtsThirdPartyInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        TestUtils.setDefaultDialer(getInstrumentation(), DEFAULT_DIALER_PKG_2);
        control.resetLatchForServiceBound(true /* bind */);

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        assertTrue(control.checkBindStatus(true /* bindStatus */));

        connection.waitOnInCallServiceTrackingChanged();
        assertTrue(connection.isAlternativeUiShowing());
        mUiAutomation.dropShellPermissionIdentity();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    /**
     * Test not bind to default dialer that not support self-managed connections when device is not
     * in car mode
     */
    public void testNoBindToUnsupportDefaultDialerNoCarMode() throws Exception {
        if (!mShouldTestTelecom || !mRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(THIRD_PTY_CONTROL,
                DEFAULT_DIALER_INCALLSERVICE_1);
        ICtsThirdPartyInCallServiceControl control = ICtsThirdPartyInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        assertTrue(setDefaultDialer(DEFAULT_DIALER_PKG_1));
        control.resetLatchForServiceBound(true /* bind */);

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        assertFalse(control.checkBindStatus(true /* bindStatus */));

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    public void testEnterCarMode() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(CAR_MODE_CONTROL,
                CAR_DIALER_1);
        mCarModeIncallServiceControlOne = ICtsCarModeInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        mCarModeIncallServiceControlOne.reset();

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.CONTROL_INCALL_EXPERIENCE");
        mCarModeIncallServiceControlOne.enableCarMode(1000);
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(true /* bindStatus */));
        mCarModeIncallServiceControlOne.disableCarMode();
        mUiAutomation.dropShellPermissionIdentity();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    /**
     * Test {@link TelecomManager#getOwnSelfManagedPhoneAccounts} works on packages with only the
     * {@link android.Manifest.permission#MANAGE_OWN_CALLS} permission.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,7.4.1.2/C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#getOwnSelfManagedPhoneAccounts"})
    public void testTelecomManagerGetSelfManagedPhoneAccountsForPackage() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // bind to CarModeTestAppSelfManaged which only has the
        // {@link android.Manifest.permission#MANAGE_OWN_CALLS} permission
        TestServiceConnection control = setUpControl(CAR_MODE_CONTROL, CAR_SELF_MANAGED_COMPONENT);

        mCarModeIncallServiceControlSelfManaged =
                ICtsCarModeInCallServiceControl.Stub
                        .asInterface(control.getService());

        mCarModeIncallServiceControlSelfManaged.reset();

        // register a self-managed phone account
        mCarModeIncallServiceControlSelfManaged.registerPhoneAccount(
                TEST_SELF_MANAGED_PHONE_ACCOUNT);

        List<PhoneAccountHandle> pah =
                mCarModeIncallServiceControlSelfManaged.getOwnSelfManagedPhoneAccounts();

        // assert that we can get all the self-managed phone accounts registered to
        // CarModeTestAppSelfManaged
        assertEquals(1, pah.size());
        assertTrue(pah.contains(TEST_CAR_SELF_MANAGED_HANDLE));

        mCarModeIncallServiceControlSelfManaged.unregisterPhoneAccount(
                TEST_CAR_SELF_MANAGED_HANDLE);

        // unbind to CarModeTestAppSelfManaged
        mContext.unbindService(control);
    }

    /**
     * helper method that creates and returns a self-managed connection with a given handle
     * and address.  Additionally, some checks are made to ensure the self-managed connection was
     * successful.
     */
    private void placeIncomingVideoCallOnTestApp(
            ICtsSelfManagedConnectionServiceControl serviceControl,
            PhoneAccountHandle handle, Uri address) throws Exception {
        // place a self-managed call
        assertTrue(serviceControl.placeIncomingCall(handle, address.toString(),
                VideoProfile.STATE_BIDIRECTIONAL));

        // Wait for Telecom to finish creating the new connection.
        try {
            TestUtils.waitOnAllHandlers(getInstrumentation());
        } catch (Exception e) {
            fail("Failed to wait on handlers");
        }

        // Ensure Telecom bound to the self managed CS
        if (!serviceControl.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }
    }

    /**
     * Test ensures that Incoming video call received with Audio only when
     * car mode with phone account only audio is supported.
     */
    @CddTest(requirements = "7.4.1.2/C-12-1,7.4.1.2/C-12-2")
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    public void testIncomingVideoCallWithNoVideoSupportInCarMode() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            UiModeManager uiModeManager = mContext.getSystemService(UiModeManager.class);
            if (uiModeManager.isUiModeLocked()) {
                Log.e(TAG, "testIncomingVideoCallWithNoVideoSupportInCarMode: UI mode Locked");
                return;
            }
        }


        //bind to test app selfmanagedcstestappone
        TestServiceConnection csControl =
                setUpControl(SELF_MANAGED_CS_CONTROL, SELF_MANAGED_CS_1);
        ICtsSelfManagedConnectionServiceControl selfManagedCSControl =
                ICtsSelfManagedConnectionServiceControl.Stub.asInterface(csControl.getService());
        selfManagedCSControl.init();

        // register a phone account with no video support from self-managed CS test app
        selfManagedCSControl.registerPhoneAccount(
                TestUtils.TEST_SELF_MANAGED_CS_1_PHONE_ACCOUNT_2);

        // bind to CarModeTestApp
        TestServiceConnection inCallControl = setUpControl(CAR_MODE_CONTROL,
                CAR_DIALER_1);
        mCarModeIncallServiceControlOne = ICtsCarModeInCallServiceControl.Stub
                .asInterface(inCallControl.getService());
        mCarModeIncallServiceControlOne.reset();

        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.CONTROL_INCALL_EXPERIENCE");
        mCarModeIncallServiceControlOne.enableCarMode(1000);

        // place a self-managed call
        placeIncomingVideoCallOnTestApp(selfManagedCSControl,
                TestUtils.TEST_SELF_MANAGED_CS_1_HANDLE_1, TEST_ADDRESS);

        // self managed car mode inCallService should be binded
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(true /* bindStatus */));

        // Ensure Telecom bound to the self managed CS
        if (!selfManagedCSControl.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        if (!selfManagedCSControl.isConnectionAvailable()) {
            fail("Connection not available for Self-Managed ConnectionService");
        }

        // check call object received at inCallService
        assertTrue(mCarModeIncallServiceControlOne.checkCallAddedStatus());

        // incoming call video state should be audio only
        assertEquals(VideoProfile.STATE_AUDIO_ONLY,
                mCarModeIncallServiceControlOne.getCallVideoState());

        // answer with video state bi-directional, still audio only allowed
        mCarModeIncallServiceControlOne.answerCall(VideoProfile.STATE_BIDIRECTIONAL);

        assertTrue(selfManagedCSControl.waitOnAnswer());

        assertEquals(Connection.STATE_ACTIVE, selfManagedCSControl.getConnectionState());

        // incoming call connection video state should be audio only
        assertEquals(VideoProfile.STATE_AUDIO_ONLY,
                mCarModeIncallServiceControlOne.getCallVideoState());

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(selfManagedCSControl.getAudioModeIsVoip());

        // Ensure AudioManager has correct voip mode.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        assertIsInCall(true);
        assertIsInManagedCall(false);

        selfManagedCSControl.disconnectConnection();

        selfManagedCSControl.unregisterPhoneAccount(
                TestUtils.TEST_SELF_MANAGED_CS_1_HANDLE_1);

        mCarModeIncallServiceControlOne.disableCarMode();
        mUiAutomation.dropShellPermissionIdentity();

        mContext.unbindService(inCallControl);
        mContext.unbindService(csControl);

        assertIsInCall(false);
        assertIsInManagedCall(false);
    }

    public void testSwapInCallServicesForSelfManagedCSCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            UiModeManager uiModeManager = mContext.getSystemService(UiModeManager.class);
            if (uiModeManager.isUiModeLocked()) {
                Log.e(TAG, "testSwapInCallServicesForSelfManagedCSCall: UI mode Locked");
                return;
            }
        }

        //Place self-managed CS second call
        SelfManagedConnection connection = placeAndVerifySelfManagedCall();

        // By default MockInCallService is binded
        final MockInCallService inCallService = mInCallCallbacks.getService();
        assertTrue(inCallService != null);

        final Call call = inCallService.getLastCall();
        assertTrue(call != null);

        call.answer(VideoProfile.STATE_AUDIO_ONLY);

        // Ensure AudioManager has correct voip mode.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);
        assertTrue(connection.getAudioModeIsVoip());

        //hold the call
        call.hold();

        //connection should be on hold
        assertTrue(connection.waitOnHold());
        assertConnectionState(connection, Connection.STATE_HOLDING);
        //call should be on hold
        assertCallState(call, Call.STATE_HOLDING);

        //should be still self-managed call
        assertIsInCall(true);
        assertIsInManagedCall(false);

        // Start Car mode
        TestServiceConnection inCallControl = setUpControl(CAR_MODE_CONTROL, CAR_DIALER_1);
        mCarModeIncallServiceControlOne = ICtsCarModeInCallServiceControl.Stub
                .asInterface(inCallControl.getService());
        mCarModeIncallServiceControlOne.reset();

        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.CONTROL_INCALL_EXPERIENCE");
        mCarModeIncallServiceControlOne.enableCarMode(999);

        // car mode inCallService should be binded
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(true /* bindStatus */));

        // MockInCallService should be unbinded
        assertMockInCallServiceUnbound();

        // check call object received at car mode inCallService
        assertTrue(mCarModeIncallServiceControlOne.checkCallAddedStatus());

        // call state should be on hold
        assertEquals(Call.STATE_HOLDING, mCarModeIncallServiceControlOne.getCallState());
        //connection should be on hold
        assertConnectionState(connection, Connection.STATE_HOLDING);

        //should be still self-managed call
        assertIsInCall(true);
        assertIsInManagedCall(false);

        //unhold the call
        mCarModeIncallServiceControlOne.unhold();

        // connection state should be ACTIVE
        assertTrue(connection.waitOnUnHold());
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        // Ensure AudioManager has correct voip mode.
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);
        assertTrue(connection.getAudioModeIsVoip());

        // disconnect call
        mCarModeIncallServiceControlOne.disconnect();

        assertConnectionState(connection, Connection.STATE_DISCONNECTED);

        mCarModeIncallServiceControlOne.disableCarMode();

        mUiAutomation.dropShellPermissionIdentity();
        mContext.unbindService(inCallControl);

        assertIsInCall(false);
        assertIsInManagedCall(false);
    }

    /**
     * Third party app .
     */

    public void testChangeCarModeApp() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        TestServiceConnection controlConn1 = setUpControl(CAR_MODE_CONTROL, CAR_DIALER_1);
        TestServiceConnection controlConn2 = setUpControl(CAR_MODE_CONTROL, CAR_DIALER_2);
        mCarModeIncallServiceControlOne = ICtsCarModeInCallServiceControl.Stub
                .asInterface(controlConn1.getService());
        mCarModeIncallServiceControlTwo = ICtsCarModeInCallServiceControl.Stub
                .asInterface(controlConn2.getService());
        mCarModeIncallServiceControlOne.reset();
        mCarModeIncallServiceControlTwo.reset();

        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.CONTROL_INCALL_EXPERIENCE");
        mCarModeIncallServiceControlOne.enableCarMode(999);

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(true /* bindStatus */));
        mCarModeIncallServiceControlTwo.enableCarMode(1000);
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(false /* bindStatus */));
        assertTrue(mCarModeIncallServiceControlTwo.checkBindStatus(true /* bindStatus */));

        mCarModeIncallServiceControlOne.disableCarMode();
        mCarModeIncallServiceControlTwo.disableCarMode();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mUiAutomation.dropShellPermissionIdentity();
        mContext.unbindService(controlConn1);
        mContext.unbindService(controlConn2);
    }

    public void testExitCarMode() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(CAR_MODE_CONTROL, CAR_DIALER_1);
        mCarModeIncallServiceControlOne = ICtsCarModeInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        mCarModeIncallServiceControlOne.reset();

        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.CONTROL_INCALL_EXPERIENCE");
        mCarModeIncallServiceControlOne.enableCarMode(1000);

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(true /* bindStatus */));
        mCarModeIncallServiceControlOne.disableCarMode();
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(false /* bindStatus */));
        mUiAutomation.dropShellPermissionIdentity();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    private TestServiceConnection setUpControl(String action, ComponentName componentName) {
        Intent bindIntent = new Intent(action);
        bindIntent.setComponent(componentName);

        TestServiceConnection serviceConnection = new TestServiceConnection();
        mContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!serviceConnection.waitBind()) {
            fail("fail bind to service");
        }
        return serviceConnection;
    }

    private boolean setDefaultDialer(String packageName) {
        mUiAutomation.adoptShellPermissionIdentity();
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Consumer<Boolean> callback = successful -> {
                future.complete(successful);
            };

            mRoleManager.addRoleHolderAsUser(RoleManager.ROLE_DIALER, packageName, 0,
                    UserHandle.of(ActivityManager.getCurrentUser()), AsyncTask.THREAD_POOL_EXECUTOR,
                    callback);
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            return false;
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    private String getDefaultDialer() {
        mUiAutomation.adoptShellPermissionIdentity();
        String result = mRoleManager.getRoleHolders(RoleManager.ROLE_DIALER).get(0);
        mUiAutomation.dropShellPermissionIdentity();
        return result;
    }

    private SelfManagedConnection placeAndVerifySelfManagedCall() {
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_4, TEST_ADDRESS);
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }
        return TestUtils.waitForAndGetConnection(TEST_ADDRESS);
    }
}
