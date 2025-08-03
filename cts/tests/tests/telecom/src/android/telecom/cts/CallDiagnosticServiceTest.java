/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;
import static android.telecom.cts.TestUtils.shouldTestTelecom;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.content.Context;
import android.os.Bundle;
import android.telecom.BluetoothCallQualityReport;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallDiagnostics;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.telephony.CallQuality;
import android.telephony.TelephonyManager;

import java.util.concurrent.TimeUnit;

public class CallDiagnosticServiceTest extends BaseTelecomTestWithMockServices {
    private static final String POOR_CALL_MESSAGE = "Can you hear me?";
    private static final String OVERRIDE_MESSAGE = "Whoopsie doodles; call dropped.  Oh well.";
    private static final int POOR_MESSAGE_ID = 90210;
    private TelecomManager mTelecomManager;
    private MockConnection mConnection;
    private android.telecom.Call mCall;
    private CtsCallDiagnosticService mService;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        runWithShellPermissionIdentity(() -> {
                    // Make sure there is a sim account registered.
                    mTelecomManager.registerPhoneAccount(TestUtils.TEST_SIM_PHONE_ACCOUNT);
                });
        TestUtils.enablePhoneAccount(
                getInstrumentation(), TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE);
        TestUtils.setCallDiagnosticService(getInstrumentation(), TestUtils.PACKAGE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mConnection != null ) {
            mConnection.onDisconnect();
            mConnection.destroy();
        }
        TestUtils.setCallDiagnosticService(getInstrumentation(), "default");
        super.tearDown();
    }

    /**
     * Test adding a call binds to the call diagnostic service.
     * @throws InterruptedException
     */
    public void testAddCallAndPassValues() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        assertEquals(1, mService.getCalls().size());
        final CtsCallDiagnosticService.CtsCallDiagnostics diagnosticCall =
                mService.getCalls().get(0);

        // Add an extra to the connection and verify CDS gets it.
        Bundle connectionExtras = new Bundle();
        connectionExtras.putInt(Connection.EXTRA_AUDIO_CODEC, Connection.AUDIO_CODEC_AMR_WB);
        mConnection.putExtras(connectionExtras);
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return true;
            }

            @Override
            public Object actual() {
                return diagnosticCall.getCallDetails().getExtras().containsKey(
                        Connection.EXTRA_AUDIO_CODEC)
                        && diagnosticCall.getCallDetails().getExtras().getInt(
                        Connection.EXTRA_AUDIO_CODEC) == Connection.AUDIO_CODEC_AMR_WB;
            }
        }, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "Extras propagation");
    }

    /**
     * Test adding multiple calls to the Call Diagnostic Service.
     * @throws InterruptedException
     */
    public void testAddMultipleCalls() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        final Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE);

        // Add a second call.
        placeAndVerifyCall(extras);
        MockConnection connection = verifyConnectionForOutgoingCall(1);
        MockInCallService inCallService = mInCallCallbacks.getService();
        Call call = inCallService.getLastCall();
        assertEquals(TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE, connection.getPhoneAccountHandle());

        mService.getCallChangeLatch().await(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        assertEquals(2, mService.getCalls().size());

        // Disconnect the first call.
        mConnection.onDisconnect();
        mConnection.destroy();
        mConnection = null;

        mService.getCallChangeLatch().await(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        assertEquals(1, mService.getCalls().size());

        connection.onDisconnect();
        connection.destroy();
    }


    /**
     * Test passing of BT call quality report to CDS.
     * @throws InterruptedException
     */
    public void testBluetoothCallQualityReport() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        // Add an extra to the connection and verify CDS gets it.
        BluetoothCallQualityReport report = new BluetoothCallQualityReport.Builder()
                .setChoppyVoice(true)
                .setNegativeAcknowledgementCount(10)
                .setRetransmittedPacketsCount(10)
                .build();
        Bundle eventExtras = new Bundle();
        eventExtras.putParcelable(BluetoothCallQualityReport.EXTRA_BLUETOOTH_CALL_QUALITY_REPORT,
                report);
        mCall.sendCallEvent(BluetoothCallQualityReport.EVENT_BLUETOOTH_CALL_QUALITY_REPORT,
                eventExtras);

        mService.getBluetoothCallQualityReportLatch().await(
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(report, mService.getBluetoothCallQualityReport());
    }

    /**
     * Test passing of call audio route to CDS.
     * @throws InterruptedException
     */
    public void testCallAudioRoute() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        mInCallCallbacks.getService().setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        assertAudioRoute(mInCallCallbacks.getService(), CallAudioState.ROUTE_SPEAKER);

        mService.getCallAudioStateLatch().await(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        assertEquals(CallAudioState.ROUTE_SPEAKER, mService.getCallAudioState().getRoute());
    }

    /**
     * Test incoming D2D message
     * @throws InterruptedException
     */
    public void testReceiveD2DMessage() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        Bundle message = new Bundle();
        message.putInt(Connection.EXTRA_DEVICE_TO_DEVICE_MESSAGE_TYPE,
                CallDiagnostics.MESSAGE_CALL_NETWORK_TYPE);
        message.putInt(Connection.EXTRA_DEVICE_TO_DEVICE_MESSAGE_VALUE,
                TelephonyManager.NETWORK_TYPE_LTE);
        mConnection.sendConnectionEvent(Connection.EVENT_DEVICE_TO_DEVICE_MESSAGE, message);

        CtsCallDiagnosticService.CtsCallDiagnostics diagnosticCall = mService.getCalls().get(0);
        diagnosticCall.getReceivedMessageLatch().await(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        assertEquals(CallDiagnostics.MESSAGE_CALL_NETWORK_TYPE,
                diagnosticCall.getMessageType());
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE,
                diagnosticCall.getMessageValue());
    }

    /**
     * Test sending D2D message
     * @throws InterruptedException
     */
    public void testSendD2DMessage() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        CtsCallDiagnosticService.CtsCallDiagnostics diagnosticCall = mService.getCalls().get(0);
        diagnosticCall.sendDeviceToDeviceMessage(CallDiagnostics.MESSAGE_DEVICE_BATTERY_STATE,
                CallDiagnostics.BATTERY_STATE_LOW);

        final TestUtils.InvokeCounter counter = mConnection.getInvokeCounter(
                MockConnection.ON_CALL_EVENT);
        counter.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        String event = (String) (counter.getArgs(0)[0]);
        Bundle extras = (Bundle) (counter.getArgs(0)[1]);
        assertEquals(Connection.EVENT_DEVICE_TO_DEVICE_MESSAGE, event);
        assertNotNull(extras);
        int messageType = extras.getInt(Connection.EXTRA_DEVICE_TO_DEVICE_MESSAGE_TYPE);
        int messageValue = extras.getInt(Connection.EXTRA_DEVICE_TO_DEVICE_MESSAGE_VALUE);
        assertEquals(CallDiagnostics.MESSAGE_DEVICE_BATTERY_STATE, messageType);
        assertEquals(CallDiagnostics.BATTERY_STATE_LOW, messageValue);
    }

    /**
     * Test routing of a diagnostic message from the CallDiagnosticService to the dialer.
     * @throws InterruptedException
     */
    public void testDisplayDiagnosticMessage() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        CtsCallDiagnosticService.CtsCallDiagnostics diagnosticCall = mService.getCalls().get(0);
        diagnosticCall.displayDiagnosticMessage(POOR_MESSAGE_ID, POOR_CALL_MESSAGE);

        mOnConnectionEventCounter.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        String event = (String) (mOnConnectionEventCounter.getArgs(0)[1]);
        Bundle extras = (Bundle) (mOnConnectionEventCounter.getArgs(0)[2]);
        assertEquals(Call.EVENT_DISPLAY_DIAGNOSTIC_MESSAGE, event);
        assertNotNull(extras);
        CharSequence message = extras.getCharSequence(Call.EXTRA_DIAGNOSTIC_MESSAGE);
        int messageId = extras.getInt(Call.EXTRA_DIAGNOSTIC_MESSAGE_ID);
        assertEquals(POOR_MESSAGE_ID, messageId);
        assertEquals(POOR_CALL_MESSAGE, message);
    }

    /**
     * Test routing of a clear diagnostic message from the CallDiagnosticService to the dialer.
     * @throws InterruptedException
     */
    public void testClearDisplayDiagnosticMessage() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        CtsCallDiagnosticService.CtsCallDiagnostics diagnosticCall = mService.getCalls().get(0);
        diagnosticCall.clearDiagnosticMessage(POOR_MESSAGE_ID);

        mOnConnectionEventCounter.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        String event = (String) (mOnConnectionEventCounter.getArgs(0)[1]);
        Bundle extras = (Bundle) (mOnConnectionEventCounter.getArgs(0)[2]);
        assertEquals(Call.EVENT_CLEAR_DIAGNOSTIC_MESSAGE, event);
        assertNotNull(extras);
        int messageId = extras.getInt(Call.EXTRA_DIAGNOSTIC_MESSAGE_ID);
        assertEquals(POOR_MESSAGE_ID, messageId);
    }

    /**
     * Test not overriding the disconnect message.
     * @throws InterruptedException
     */
    public void testSetNullDisconnectMessage() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();
        mService.setDisconnectMessage(null);
        mConnection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
        mConnection.destroy();
        CtsCallDiagnosticService.CtsCallDiagnostics diagnosticCall = mService.getCalls().get(0);
        diagnosticCall.getDisconnectLatch().await(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);

        assertCallState(mCall, Call.STATE_DISCONNECTED);
        assertNull(mCall.getDetails().getDisconnectCause().getLabel());
        assertNull(mCall.getDetails().getDisconnectCause().getDescription());
    }

    /**
     * Test override the disconnect message.
     * @throws InterruptedException
     */
    public void testOverrideDisconnectMessage() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();
        mService.setDisconnectMessage(OVERRIDE_MESSAGE);
        mConnection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
        mConnection.destroy();
        CtsCallDiagnosticService.CtsCallDiagnostics diagnosticCall = mService.getCalls().get(0);
        diagnosticCall.getDisconnectLatch().await(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);

        assertCallState(mCall, Call.STATE_DISCONNECTED);
        assertEquals(OVERRIDE_MESSAGE, mCall.getDetails().getDisconnectCause().getLabel());
        assertEquals(OVERRIDE_MESSAGE, mCall.getDetails().getDisconnectCause().getDescription());
    }

    /**
     * Test call quality report received.
     * @throws InterruptedException
     */
    public void testReceiveCallQualityReport() throws InterruptedException {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        setupCall();

        // Fake out a call quality report.
        android.telephony.CallQuality callQuality = new CallQuality(
                android.telephony.CallQuality.CALL_QUALITY_EXCELLENT,
                android.telephony.CallQuality.CALL_QUALITY_EXCELLENT,
                60000, // duration
                90210, // transmitted
                90210, // received
                0, // lost
                0, // lost
                0, // jitter
                0, // jitter
                10, // round trip
                0); // codec
        Bundle message = new Bundle();
        message.putParcelable("android.telecom.extra.CALL_QUALITY_REPORT", callQuality);
        mConnection.sendConnectionEvent("android.telecom.event.CALL_QUALITY_REPORT", message);

        CtsCallDiagnosticService.CtsCallDiagnostics diagnosticCall = mService.getCalls().get(0);
        diagnosticCall.getCallQualityReceivedLatch().await(
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(diagnosticCall.getCallQuality());
    }

    /**
     * Starts a fake SIM call and verifies binding to the CDS.
     * @throws InterruptedException
     */
    private void setupCall() throws InterruptedException {
        final Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE);

        // Add a call.
        placeAndVerifyCall(extras);
        mConnection = verifyConnectionForOutgoingCall();
        MockInCallService inCallService = mInCallCallbacks.getService();
        mCall = inCallService.getLastCall();

        assertEquals(TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE, mConnection.getPhoneAccountHandle());

        // Make sure we bound to the CTS call diagnostic service and told it there is a call.
        CtsCallDiagnosticService.getBindLatch().await(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        mService = CtsCallDiagnosticService.getInstance();
        assertNotNull(mService);
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return 1;
            }

            @Override
            public Object actual() {
                return mService.getCalls().size();
            }
        }, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "Call added");

        // Make the call active.
        mConnection.setActive();

        // Make sure UI knows.
        assertCallState(mCall, Call.STATE_ACTIVE);
    }
}
