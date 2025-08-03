/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.PlatinumTest;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;

import com.android.compatibility.common.util.SystemUtil;
import com.android.server.telecom.flags.Flags;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Verifies the behavior of Telecom during various outgoing call flows.
 */
public class OutgoingCallTest extends BaseTelecomTestWithMockServices {
    private static final long STATE_CHANGE_DELAY = 1000;
    private static final String TEST_EMERGENCY_NUMBER = "9998887776655443210";

    Uri mPersonRecord = null;
    Uri mPhoneRecord = null;
    private final static String TEST_PHONE_NUMBER = "+18005552871";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (mShouldTestTelecom) {
            setupConnectionService(null,
                    FLAG_REGISTER | FLAG_ENABLE | FLAG_PHONE_ACCOUNT_HANDLES_CONTENT_SCHEME);

            try {
                // Add a test contact
                ContentResolver cr = getInstrumentation().getTargetContext().getContentResolver();
                mPersonRecord = null;
                mPhoneRecord = null;

                // insert a contact with phone number
                ContentValues values = new ContentValues();
                mPersonRecord = cr.insert(ContactsContract.RawContacts.CONTENT_URI, values);

                values.clear();
                values.put(CommonDataKinds.StructuredName.DISPLAY_NAME, "CTS test contact");
                values.put(Data.RAW_CONTACT_ID, mPersonRecord.getLastPathSegment());
                values.put(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                cr.insert(Data.CONTENT_URI, values);

                values.clear();
                values.put(Data.RAW_CONTACT_ID, mPersonRecord.getLastPathSegment());
                values.put(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_HOME);
                values.put(CommonDataKinds.Phone.NUMBER, TEST_PHONE_NUMBER);
                values.put(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                mPhoneRecord = cr.insert(Data.CONTENT_URI, values);
            } catch (Exception e) {
                // Force tearDown if setUp errors out to ensure unused listeners are cleaned up.
                tearDown();
                fail(String.format("Failed to insert test contact because of the following"
                        + " exception=[%s]", e));
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (mShouldTestTelecom) {
                ContentResolver resolver =
                        getInstrumentation().getTargetContext().getContentResolver();

                if (mPersonRecord != null) {
                    resolver.delete(mPersonRecord, null, null);
                }
                if (mPhoneRecord != null) {
                    resolver.delete(mPhoneRecord, null, null);
                }

                TestUtils.clearSystemDialerOverride(getInstrumentation());
                TestUtils.removeTestEmergencyNumber(getInstrumentation(), TEST_EMERGENCY_NUMBER);
            }
        } finally {
            super.tearDown();
        }
    }

    /* TODO: Need to send some commands to the UserManager via adb to do setup
    public void testDisallowOutgoingCallsForSecondaryUser() {
    } */

    /* TODO: Need to figure out a way to mock emergency calls without adb root
    public void testOutgoingCallBroadcast_isSentForAllCalls() {
    } */

    /**
     * Verifies that providing content URI instead of tel/sip uri does not start a call
     *
     */
    public void testDoNotStartCallWithContentUri() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, mPhoneRecord);
        placeAndVerifyNoCall(extras);
        verifyNoConnectionForOutgoingCall();
    }

    /**
     * Verifies that providing the EXTRA_START_CALL_WITH_SPEAKERPHONE extra starts the call with
     * speakerphone automatically enabled.
     *
     * @see {@link TelecomManager#EXTRA_START_CALL_WITH_SPEAKERPHONE}
     */
    public void testStartCallWithSpeakerphoneTrue_SpeakerphoneOnInCall() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }

        final Bundle extras = new Bundle();
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
        placeAndVerifyCall(extras);
        verifyConnectionForOutgoingCall();
        assertAudioRoute(mInCallCallbacks.getService(), CallAudioState.ROUTE_SPEAKER);
    }

    public void testStartCallWithSpeakerphoneFalse_SpeakerphoneOffInCall() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }

        final Bundle extras = new Bundle();
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false);
        placeAndVerifyCall(extras);
        verifyConnectionForOutgoingCall();
        if (mInCallCallbacks.getService().getCallAudioState().getSupportedRouteMask() ==
                CallAudioState.ROUTE_SPEAKER) {
            return; // Do not verify if the only available route is speaker.
        }
        assertNotAudioRoute(mInCallCallbacks.getService(), CallAudioState.ROUTE_SPEAKER);
    }

    /**
     * Nominal outgoing test case; verifies an outgoing call can be placed on the device.
     */
    @PlatinumTest(focusArea = "telecom")
    public void testStartCallWithSpeakerphoneNotProvided_SpeakerphoneOffByDefault() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }

        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        placeAndVerifyCall();
        // Confirm that we got ConnectionService#onCreateConnectionComplete
        if (Flags.telecomResolveHiddenDependencies()) {
            assertTrue(connectionService.waitForEvent(
                    MockConnectionService.EVENT_CONNECTION_SERVICE_CREATE_CONNECTION_COMPLETE));
        }
        verifyConnectionForOutgoingCall();
        if (mInCallCallbacks.getService().getCallAudioState().getSupportedRouteMask() ==
                CallAudioState.ROUTE_SPEAKER) {
            return; // Do not verify if the only available route is speaker.
        }
        assertNotAudioRoute(mInCallCallbacks.getService(), CallAudioState.ROUTE_SPEAKER);
    }

    public void testPhoneStateListenerInvokedOnOutgoingEmergencyCall() throws Throwable {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        TestUtils.setSystemDialerOverride(getInstrumentation());
        TestUtils.setTestEmergencyPhoneAccountPackageFilter(getInstrumentation(), mContext);
        mTelephonyCallback.clearEmergencyNumberQueue();
        TestUtils.addTestEmergencyNumber(getInstrumentation(), TEST_EMERGENCY_NUMBER);
        Map<Integer, List<EmergencyNumber>> emergencyNumbers = null;

        for (int i = 0; i < 5; i++) {
            emergencyNumbers = mTelephonyCallback.waitForEmergencyNumberListUpdate(
                    TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
            assertNotNull("Never got an update that the test emergency number was registered",
                    emergencyNumbers);
            if (doesEmergencyNumberListContainTestNumber(emergencyNumbers)) {
                break;
            }
        }
        assertTrue("Emergency number list from telephony still doesn't have the test number",
                doesEmergencyNumberListContainTestNumber(emergencyNumbers));

        try {
            Bundle extras = new Bundle();
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                    TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
            mTelecomManager.placeCall(Uri.fromParts("tel", TEST_EMERGENCY_NUMBER, null), extras);

            verifyPhoneStateListenerCallbacksForEmergencyCall(TEST_EMERGENCY_NUMBER);
        } finally {
            TestUtils.removeTestEmergencyNumber(getInstrumentation(), TEST_EMERGENCY_NUMBER);
            TestUtils.clearTestEmergencyPhoneAccountPackageFilter(getInstrumentation());
        }
    }

    private boolean doesEmergencyNumberListContainTestNumber(
            Map<Integer, List<EmergencyNumber>> emergencyNumbers) {
        return emergencyNumbers.values().stream().flatMap(List::stream)
                .anyMatch(numberObj ->
                        Objects.equals(numberObj.getNumber(), TEST_EMERGENCY_NUMBER));
    }

    public void testPhoneStateListenerInvokedOnOutgoingCall() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }

        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();
        String expectedNumber = connectionService.outgoingConnections.get(0)
                .getAddress().getSchemeSpecificPart();
        verifyPhoneStateListenerCallbacksForCall(TelephonyManager.CALL_STATE_OFFHOOK,
                expectedNumber);
        verifyCallStateListener(TelephonyManager.CALL_STATE_OFFHOOK);
    }

    /**
     * Ensure the phone state is changed in an expected way.
     * @throws Exception
     */
    public void testPhoneStateChangeAsExpected() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }

        CountDownLatch count = new CountDownLatch(1);
        Executor executor = (Runnable command) -> count.countDown();
        TelephonyCallback callback = new TelephonyCallback();
        try {
            final Bundle extras = new Bundle();
            extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);

            mTelephonyManager.registerTelephonyCallback(executor, callback);
            placeAndVerifyCall(extras);
            verifyConnectionForOutgoingCall();
            count.await(TestUtils.WAIT_FOR_PHONE_STATE_LISTENER_REGISTERED_TIMEOUT_S,
                    TimeUnit.SECONDS);
            Thread.sleep(STATE_CHANGE_DELAY);
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mTelephonyManager.getCallState());
        } finally {
            mTelephonyManager.unregisterTelephonyCallback(callback);
        }
    }

    /**
     * Ensure that {@link TelecomManager#EXTRA_PHONE_ACCOUNT_HANDLE} is taken account when placing
     * an outgoing call.
     * @throws Exception
     */
    public void testExtraPhoneAccountHandleAvailable() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }

        final Bundle extras1 = new Bundle();
        final Bundle extras2 = new Bundle();
        final Uri testNumber1 = createTestNumber();
        final Uri testNumber2 = createTestNumber();
        extras1.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        extras1.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, testNumber1);
        extras2.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE_2);
        extras2.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, testNumber2);

        mTelecomManager.registerPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT);
        TestUtils.enablePhoneAccount(
                getInstrumentation(), TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        placeAndVerifyCall(extras1);
        Connection conn = verifyConnectionForOutgoingCall();
        assertEquals(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, conn.getPhoneAccountHandle());

        cleanupCalls();
        assertCtsConnectionServiceUnbound();
        CtsConnectionService.tearDown();
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        verifyCallLogging(testNumber1, CallLog.Calls.OUTGOING_TYPE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);

        mTelecomManager.registerPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT_2);
        TestUtils.enablePhoneAccount(
                getInstrumentation(), TestUtils.TEST_PHONE_ACCOUNT_HANDLE_2);
        placeAndVerifyCall(extras2);
        conn = verifyConnectionForOutgoingCall();
        assertEquals(TestUtils.TEST_PHONE_ACCOUNT_HANDLE_2, conn.getPhoneAccountHandle());
        conn.onDisconnect();
        verifyCallLogging(testNumber2, CallLog.Calls.OUTGOING_TYPE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE_2);
    }

    public void testAccountSelectionAvailable() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        PhoneAccountHandle cachedHandle = mTelecomManager.getUserSelectedOutgoingPhoneAccount();

        mTelecomManager.registerPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT);
        TestUtils.enablePhoneAccount(getInstrumentation(), TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        mTelecomManager.registerPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT_2);
        TestUtils.enablePhoneAccount(getInstrumentation(), TestUtils.TEST_PHONE_ACCOUNT_HANDLE_2);

        CountDownLatch latch = new CountDownLatch(1);
        mInCallCallbacks = new MockInCallService.InCallServiceCallbacks() {
            @Override
            public void onCallAdded(Call call, int numCalls) {
                if (call.getState() == STATE_SELECT_PHONE_ACCOUNT) {
                    call.phoneAccountSelected(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, false);
                }
            }

            public void onCallStateChanged(Call call, int state) {
                if (TestUtils.TEST_PHONE_ACCOUNT_HANDLE.equals(
                        call.getDetails().getAccountHandle())) {
                    latch.countDown();
                }
            }
        };
        MockInCallService.setCallbacks(mInCallCallbacks);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mTelecomManager.setUserSelectedOutgoingPhoneAccount(null);
        });

        try {
            Uri testNumber = createTestNumber();
            mTelecomManager.placeCall(testNumber, null);

            assertTrue(latch.await(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S, TimeUnit.SECONDS));
        } finally {
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT_HANDLE_2);
            SystemUtil.runWithShellPermissionIdentity(() -> {
                mTelecomManager.setUserSelectedOutgoingPhoneAccount(cachedHandle);
            });
        }
    }
}
