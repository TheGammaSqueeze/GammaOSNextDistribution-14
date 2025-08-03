/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.telecom.cts;

import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.telecom.cts.TestUtils.COMPONENT;
import static android.telecom.cts.TestUtils.PACKAGE;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;
import static android.telephony.TelephonyManager.CALL_STATE_RINGING;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.PlatinumTest;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.TelephonyCallback;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.telecom.flags.Flags;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests valid/invalid incoming calls that are received from the ConnectionService
 * and registered through TelecomManager
 */
public class IncomingCallTest extends BaseTelecomTestWithMockServices {
    private static final long STATE_CHANGE_DELAY = 1000;
    private static final PhoneAccountHandle TEST_INVALID_HANDLE = new PhoneAccountHandle(
            new ComponentName(PACKAGE, COMPONENT), "WRONG_ID");
    private static final String TEST_NUMBER = "5625698388";
    private ContentResolver mContentResolver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContentResolver = mContext.getContentResolver();
    }

    public void testVerstatPassed() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        addAndVerifyNewIncomingCall(MockConnectionService.VERSTAT_PASSED_NUMBER, null);
        verifyConnectionForIncomingCall();

        Call call = mInCallCallbacks.getService().getLastCall();
        assertEquals(Connection.VERIFICATION_STATUS_PASSED,
                call.getDetails().getCallerNumberVerificationStatus());
    }

    public void testVerstatFailed() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        addAndVerifyNewIncomingCall(MockConnectionService.VERSTAT_FAILED_NUMBER, null);
        verifyConnectionForIncomingCall();

        Call call = mInCallCallbacks.getService().getLastCall();
        assertEquals(Connection.VERIFICATION_STATUS_FAILED,
                call.getDetails().getCallerNumberVerificationStatus());
    }

    public void testVerstatNotVerified() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        addAndVerifyNewIncomingCall(MockConnectionService.VERSTAT_NOT_VERIFIED_NUMBER, null);
        verifyConnectionForIncomingCall();

        Call call = mInCallCallbacks.getService().getLastCall();
        assertEquals(Connection.VERIFICATION_STATUS_NOT_VERIFIED,
                call.getDetails().getCallerNumberVerificationStatus());
    }

    /**
     * Nominal incoming call test; verifies that an incoming call can be routed through Telecom and
     * will be reported to the Dialer app.
     * @throws Exception
     */
    @PlatinumTest(focusArea = "telecom")
    public void testAddNewIncomingCall_CorrectPhoneAccountHandle() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        Uri testNumber = createTestNumber();
        addAndVerifyNewIncomingCall(testNumber, null);
        // Confirm that we got ConnectionService#onCreateConnectionComplete
        if (Flags.telecomResolveHiddenDependencies()) {
            assertTrue(connectionService.waitForEvent(
                    MockConnectionService.EVENT_CONNECTION_SERVICE_CREATE_CONNECTION_COMPLETE));
        }
        final Connection connection3 = verifyConnectionForIncomingCall();
        Collection<Connection> connections = CtsConnectionService.getAllConnectionsFromTelecom();
        assertEquals(1, connections.size());
        assertTrue(connections.contains(connection3));
        connection3.onDisconnect();
        verifyCallLogging(testNumber, CallLog.Calls.INCOMING_TYPE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
    }

    public void testPhoneStateListenerInvokedOnIncomingCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        Uri testNumber = createTestNumber();
        addAndVerifyNewIncomingCall(testNumber, null);
        verifyConnectionForIncomingCall();
        verifyPhoneStateListenerCallbacksForCall(CALL_STATE_RINGING,
                testNumber.getSchemeSpecificPart());
        verifyCallStateListener(CALL_STATE_RINGING);
    }

    /**
     * This test verifies that when a default dialer is incapable of playing a ringtone that the
     * platform still plays a ringtone.
     * <p>
     * Given that the default {@link MockInCallService} defined in the CTS tests does not declare
     * {@link TelecomManager#METADATA_IN_CALL_SERVICE_RINGING}, we expect the Telecom framework to
     * play a ringtone for an incoming call.
     * @throws Exception
     */
    public void testRingOnIncomingCall() throws Exception {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        AudioManager.AudioPlaybackCallback callback = new AudioManager.AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                super.onPlaybackConfigChanged(configs);
                boolean isPlayingRingtone = configs.stream()
                        .anyMatch(c -> c.getAudioAttributes().getUsage()
                                == USAGE_NOTIFICATION_RINGTONE);
                if (isPlayingRingtone && queue.isEmpty()) {
                    queue.add(isPlayingRingtone);
                }
            }
        };
        audioManager.registerAudioPlaybackCallback(callback, new Handler(Looper.getMainLooper()));
        Uri testNumber = createTestNumber();
        addAndVerifyNewIncomingCall(testNumber, null);
        verifyConnectionForIncomingCall();
        verifyPhoneStateListenerCallbacksForCall(CALL_STATE_RINGING,
                testNumber.getSchemeSpecificPart());
        Boolean ringing = queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull("Telecom should have played a ringtone, timed out waiting for state change",
                ringing);
        assertTrue("Telecom should have played a ringtone.", ringing);
        audioManager.unregisterAudioPlaybackCallback(callback);
    }

    /**
     * This test verifies that the local ringtone is not played when the call has an in_band
     * ringtone associated with it.
     */
    public void testExtraCallHasInBandRingtone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        AudioManager.AudioPlaybackCallback callback = new AudioManager.AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                super.onPlaybackConfigChanged(configs);
                boolean isPlayingRingtone = configs.stream()
                        .anyMatch(c -> c.getAudioAttributes().getUsage()
                                == USAGE_NOTIFICATION_RINGTONE);
                if (isPlayingRingtone && queue.isEmpty()) {
                    queue.add(isPlayingRingtone);
                }
            }
        };
        audioManager.registerAudioPlaybackCallback(callback, new Handler(Looper.getMainLooper()));
        Bundle extras = new Bundle();
        extras.putBoolean(TelecomManager.EXTRA_CALL_HAS_IN_BAND_RINGTONE, true);
        Uri testNumber = createTestNumber();
        addAndVerifyNewIncomingCall(testNumber, extras);
        verifyConnectionForIncomingCall();
        verifyPhoneStateListenerCallbacksForCall(CALL_STATE_RINGING,
                testNumber.getSchemeSpecificPart());
        Boolean ringing = queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        // ringing should be null as the state should not change (no ringing)
        assertNull("Telecom should not have played a ringtone.", ringing);
        audioManager.unregisterAudioPlaybackCallback(callback);
    }

    /**
     * Tests to be sure that new incoming calls can only be added using a valid PhoneAccountHandle
     * (b/26864502). If a PhoneAccount has not been registered for the PhoneAccountHandle, then
     * a SecurityException will be thrown.
     */
    public void testAddNewIncomingCall_IncorrectPhoneAccountHandle() {
        if (!mShouldTestTelecom) {
            return;
        }

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, createTestNumber());
        try {
            mTelecomManager.addNewIncomingCall(TEST_INVALID_HANDLE, extras);
            fail();
        } catch (SecurityException e) {
            // This should create a security exception!
        }

        assertFalse(CtsConnectionService.isServiceRegisteredToTelecom());
    }

    /**
     * Tests to be sure that new incoming calls can only be added if a PhoneAccount is enabled
     * (b/26864502). If a PhoneAccount is not enabled for the PhoneAccountHandle, then
     * a SecurityException will be thrown.
     */
    public void testAddNewIncomingCall_PhoneAccountNotEnabled() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // Do not enable PhoneAccount
        setupConnectionService(null, FLAG_REGISTER);
        assertFalse(mTelecomManager.getPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT_HANDLE)
                .isEnabled());
        try {
            addAndVerifyNewIncomingCall(createTestNumber(), null);
            fail();
        } catch (SecurityException e) {
            // This should create a security exception!
        }

        assertFalse(CtsConnectionService.isServiceRegisteredToTelecom());
    }

    /**
     * Ensure {@link Call.Details#PROPERTY_VOIP_AUDIO_MODE} is set for a ringing call which uses
     * voip audio mode.
     * @throws Exception
     */
    public void testAddNewIncomingCallVoipState() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupConnectionService(new MockConnectionService() {
            @Override
            public Connection onCreateIncomingConnection(
                    PhoneAccountHandle connectionManagerPhoneAccount,
                    ConnectionRequest request) {
                Connection connection = super.onCreateIncomingConnection(
                        connectionManagerPhoneAccount,
                        request);
                connection.setAudioModeIsVoip(true);
                lock.release();
                return connection;
            }
        }, FLAG_REGISTER | FLAG_ENABLE);
        addAndVerifyNewIncomingCall(createTestNumber(), null);
        verifyConnectionForIncomingCall();

        assertTrue((mInCallCallbacks.getService().getLastCall().getDetails().getCallProperties()
                & Call.Details.PROPERTY_VOIP_AUDIO_MODE) != 0);
    }

    /**
     * Ensure the phone state is changed in an expected way.
     * @throws Exception
     */
    public void testPhoneStateChangeAsExpected() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        CountDownLatch count = new CountDownLatch(1);
        Executor executor = (Runnable command) -> count.countDown();
        TelephonyCallback callback = new TelephonyCallback();
        try {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
            Uri testNumber = createTestNumber();
            addAndVerifyNewIncomingCall(testNumber, null);

            mTelephonyManager.registerTelephonyCallback(executor, callback);
            count.await(TestUtils.WAIT_FOR_PHONE_STATE_LISTENER_REGISTERED_TIMEOUT_S,
                    TimeUnit.SECONDS);
            Thread.sleep(STATE_CHANGE_DELAY);
            assertEquals(CALL_STATE_RINGING, mTelephonyManager.getCallState());
        } finally {
            mTelephonyManager.unregisterTelephonyCallback(callback);
        }
    }

    /**
     * Verifies that a call to {@link android.telecom.Call#answer(int)} with a passed video state of
     * {@link android.telecom.VideoProfile#STATE_AUDIO_ONLY} will result in a call to
     * {@link Connection#onAnswer()}.
     * @throws Exception
     */
    public void testConnectionOnAnswerForAudioCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // Get a new incoming call.
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        addAndVerifyNewIncomingCall(createTestNumber(), null);
        Call call = mInCallCallbacks.getService().getLastCall();
        final MockConnection connection = verifyConnectionForIncomingCall();
        TestUtils.InvokeCounter audioInvoke = connection.getInvokeCounter(
                MockConnection.ON_ANSWER_CALLED);

        // Answer as audio-only.
        call.answer(VideoProfile.STATE_AUDIO_ONLY);

        // Make sure we get a call to {@link Connection#onAnswer()}.
        audioInvoke.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
    }

    /**
     * Verify that when an incoming verified business call is received, the corresponding CallLog
     * data (CallLog.Call.IS_BUSINESS_CALL && CallLog.Calls.ASSERTED_DISPLAY_NAME) is populated.
     */
    public void testBusinessCallProperties() throws Exception {
        if (!mShouldTestTelecom || !Flags.businessCallComposer()) {
            return;
        }
        // The following will be the selection of columns from the call log database that are
        // returned.  The IS_BUSINESS_CALL and ASSERTED_DISPLAY_NAME are the business values.
        final String[] projection =
                new String[]{ CallLog.Calls.NUMBER,
                              CallLog.Calls.TYPE,
                              CallLog.Calls.IS_BUSINESS_CALL,
                              CallLog.Calls.ASSERTED_DISPLAY_NAME};
        boolean isBusinessCall = true;
        String businessName = "Google";
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        try {
            automation.adoptShellPermissionIdentity(
                    "android.permission.READ_CALL_LOG",
                    "android.permission.WRITE_CALL_LOG");

            // create an incoming business call
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
            addAndVerifyNewIncomingCall(Uri.parse(TEST_NUMBER), null);

            // inject the business values. This is typically done by the IMS layer & network
            Call call = setAndVerifyBusinessExtras(isBusinessCall, businessName);

            // register an observer on the call logs to ensure this call changes the call logs
            CountDownLatch changeLatch = new CountDownLatch(1);
            mContentResolver.registerContentObserver(
                    CallLog.Calls.CONTENT_URI, true,
                    new ContentObserver(null /* handler */) {
                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            mContentResolver.unregisterContentObserver(this);
                            changeLatch.countDown();
                            super.onChange(selfChange);
                        }
                    });

            // disconnect the call so the call logs can be evaluated
            call.disconnect();
            assertCallState(call, Call.STATE_DISCONNECTED);

            try {
                assertTrue(changeLatch.await(5000 /* MS timeout*/, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail("Expected the call logs to add a new entry but timed out waiting for entry");
            }

            // verify the business call values are logged and correct
            verifyBusinessCallValues(TEST_NUMBER, projection, isBusinessCall, businessName);
        } finally {
            deleteCallLogsWithNumber(TEST_NUMBER);
            automation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verifies that a call to {@link android.telecom.Call#answer(int)} with a passed video state of
     * {@link android.telecom.VideoProfile#STATE_AUDIO_ONLY} will result in a call to
     * {@link Connection#onAnswer()} where overridden.
     * @throws Exception
     */
    public void testConnectionOnAnswerForVideoCallAnsweredAsAudio() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // Get a new incoming call.
        Bundle extras = new Bundle();
        extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_BIDIRECTIONAL);
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        addAndVerifyNewIncomingCall(createTestNumber(), extras);
        Call call = mInCallCallbacks.getService().getLastCall();
        final MockConnection connection = verifyConnectionForIncomingCall();
        TestUtils.InvokeCounter audioInvoke = connection.getInvokeCounter(
                MockConnection.ON_ANSWER_CALLED);

        // Answer as audio-only.
        call.answer(VideoProfile.STATE_AUDIO_ONLY);

        // Make sure we get a call to {@link Connection#onAnswer()}.
        audioInvoke.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
    }

    /**
     * Verifies that a call to {@link android.telecom.Call#answer(int)} with a passed video state of
     * {@link android.telecom.VideoProfile#STATE_BIDIRECTIONAL} will result in a call to
     * {@link Connection#onAnswer(int)}.
     * @throws Exception
     */
    public void testConnectionOnAnswerIntForVideoCallAnsweredAsVideo() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // Get a new incoming call.
        Bundle extras = new Bundle();
        extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_BIDIRECTIONAL);
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        addAndVerifyNewIncomingCall(createTestNumber(), extras);
        Call call = mInCallCallbacks.getService().getLastCall();
        final MockConnection connection = verifyConnectionForIncomingCall();
        TestUtils.InvokeCounter audioInvoke = connection.getInvokeCounter(
                MockConnection.ON_ANSWER_VIDEO_CALLED);

        // Answer as audio-only.
        call.answer(VideoProfile.STATE_BIDIRECTIONAL);

        // Make sure we get a call to {@link Connection#onAnswer(int)}.
        audioInvoke.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
    }

    // Note: The WRITE_CALL_LOG permission is needed in order to call this helper.
    private void deleteCallLogsWithNumber(String number) {
        mContentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                CallLog.Calls.NUMBER + " = " + number,
                null);
    }

    private Call setAndVerifyBusinessExtras(boolean isBusinessCall, String businessName) {
        Bundle businessCallExtras = new Bundle();
        businessCallExtras.putBoolean(Call.EXTRA_IS_BUSINESS_CALL, isBusinessCall);
        businessCallExtras.putString(Call.EXTRA_ASSERTED_DISPLAY_NAME, businessName);

        // inject the Business Composer values and verify they are accessible
        final MockConnection connection = verifyConnectionForIncomingCall();
        connection.setExtras(businessCallExtras);

        // verify the Extras can be fetched from the Call object
        Call call = mInCallCallbacks.getService().getLastCall();
        assertCallExtrasKey(call, Call.EXTRA_IS_BUSINESS_CALL);
        assertCallExtrasKey(call, Call.EXTRA_ASSERTED_DISPLAY_NAME);
        return call;
    }

    private void verifyBusinessCallValues(
            String testNumber,
            String[] projection,
            boolean isBusinessCall,
            String assertedDisplayName) {

        // fetch the call logs where the testNumber matches the call made in this test
        Cursor cursor = mContentResolver.query(CallLog.Calls.CONTENT_URI,
                projection,
                CallLog.Calls.NUMBER + " = " + testNumber,
                null,
                CallLog.Calls.DEFAULT_SORT_ORDER);

        assertNotNull(cursor);

        // extract the data from the cursor and put the objects in a map
        cursor.moveToFirst();

        assertEquals((isBusinessCall ? 1 : 0), cursor.getInt(
                cursor.getColumnIndex(CallLog.Calls.IS_BUSINESS_CALL)));

        assertEquals(assertedDisplayName, cursor.getString(
                cursor.getColumnIndex(CallLog.Calls.ASSERTED_DISPLAY_NAME)));
    }

    /**
     * Asserts that a call's extras contain a specified key.
     *
     * @param call The call.
     * @param expectedKey The expected extras key.
     */
    public void assertCallExtrasKey(final Call call, final String expectedKey) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expectedKey;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getExtras().containsKey(expectedKey) ? expectedKey
                                : "";
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have extras key " + expectedKey
        );
    }
}
