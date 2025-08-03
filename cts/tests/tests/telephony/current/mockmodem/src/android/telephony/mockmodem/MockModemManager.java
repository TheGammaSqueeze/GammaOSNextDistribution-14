/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.telephony.mockmodem;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_DEFAULT;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import android.content.Context;
import android.hardware.radio.sim.Carrier;
import android.hardware.radio.sim.CarrierRestrictions;
import android.hardware.radio.voice.CdmaSignalInfoRecord;
import android.hardware.radio.voice.UusInfo;
import android.os.Build;
import android.os.Looper;
import android.os.SystemProperties;
import android.telephony.Annotation;
import android.telephony.BarringInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.feature.ConnectionFailureInfo;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.TestThread;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class MockModemManager {
    private static final String TAG = "MockModemManager";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);

    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final String BOOT_ALLOW_MOCK_MODEM_PROPERTY = "ro.boot.radio.allow_mock_modem";

    private static Context sContext;
    private static MockModemServiceConnector sServiceConnector;
    private static final long TIMEOUT_IN_MSEC_FOR_SIM_STATUS_CHANGED = 10000;
    private static final int WAIT_UPDATE_TIMEOUT_MS = 200;  // 0.2sec delay
    private MockModemService mMockModemService;

    public static void enforceMockModemDeveloperSetting() throws Exception {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        boolean isAllowedForBoot =
                SystemProperties.getBoolean(BOOT_ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        if (!(isAllowed || isAllowedForBoot) && !DEBUG) {
            throw new IllegalStateException(
                "!! Enable Mock Modem before running this test !! "
                    + "Developer options => Allow Mock Modem");
        }
    }

    public MockModemManager() {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    private void waitForTelephonyFrameworkDone(int delayInSec) throws Exception {
        TimeUnit.SECONDS.sleep(delayInSec);
    }

    private void waitForSubscriptionCondition(BooleanSupplier condition, long maxWaitMillis)
            throws Exception {
        final Object lock = new Object();
        SubscriptionManager sm =
                (SubscriptionManager)
                        sContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        TestThread t =
                new TestThread(
                        () -> {
                            Looper.prepare();

                            SubscriptionManager.OnSubscriptionsChangedListener listener =
                                    new SubscriptionManager.OnSubscriptionsChangedListener() {
                                        @Override
                                        public void onSubscriptionsChanged() {
                                            synchronized (lock) {
                                                if (condition.getAsBoolean()) {
                                                    lock.notifyAll();
                                                    Looper.myLooper().quitSafely();
                                                }
                                            }
                                        }
                                    };

                            sm.addOnSubscriptionsChangedListener(listener);
                            try {
                                synchronized (lock) {
                                    if (condition.getAsBoolean()) lock.notifyAll();
                                }
                                Log.d(TAG, "before loop()....");
                                if (!condition.getAsBoolean()) Looper.loop();
                                Log.d(TAG, "after loop()....");
                            } finally {
                                sm.removeOnSubscriptionsChangedListener(listener);
                            }
                        });

        synchronized (lock) {
            if (condition.getAsBoolean()) return;
            t.start();
            lock.wait(maxWaitMillis);
        }
    }

    /* Public APIs */

    /**
     * Bring up Mock Modem Service and connect to it.
     *
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean connectMockModemService() throws Exception {
        return connectMockModemService(MOCK_SIM_PROFILE_ID_DEFAULT);
    }

    /**
     * Bring up Mock Modem Service and connect to it.
     *
     * @pararm simprofile for initial Sim profile
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean connectMockModemService(int simprofile) throws Exception {
        int[] simprofiles = new int[1];
        simprofiles[0] = Integer.valueOf(simprofile);

        return connectMockModemService(simprofiles);
    }

    /**
     * Bring up Mock Modem Service and connect to it.
     *
     * @param simprofiles for initial Sim profile of multiple Sim slots
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean connectMockModemService(int[] simprofiles) throws Exception {
        boolean result = true;

        if (simprofiles == null) {
            Log.e(TAG, "The parameter is invalid.");
            result = false;
        }

        if (result && sServiceConnector == null) {
            sServiceConnector =
                    new MockModemServiceConnector(InstrumentationRegistry.getInstrumentation());
        }

        if (result && sServiceConnector != null) {
            result = sServiceConnector.connectMockModemService(simprofiles);

            if (result) {
                mMockModemService = sServiceConnector.getMockModemService();

                if (mMockModemService != null) {
                    /*
                     It needs to have a delay to wait for Telephony Framework to bind with
                     MockModemService and set radio power as a desired state for initial condition
                     even get SIM card state. Currently, 1 sec is enough for now.
                    */
                    waitForTelephonyFrameworkDone(1);
                } else {
                    Log.e(TAG, "MockModemService get failed!");
                    result = false;
                }
            }
        } else {
            Log.e(TAG, "Create MockModemServiceConnector failed!");
        }

        return result;
    }

    /**
     * Disconnect from Mock Modem Service.
     *
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean disconnectMockModemService() throws Exception {
        boolean result = false;

        if (sServiceConnector != null) {
            result = sServiceConnector.disconnectMockModemService();

            if (result) {
                mMockModemService = null;
            } else {
                Log.e(TAG, "MockModemService disconnected failed!");
            }
        } else {
            Log.e(TAG, "No MockModemServiceConnector exist!");
        }

        return result;
    }

    /**
     * Query whether an active SIM card is present on this slot or not.
     *
     * @param slotId which slot would be checked.
     * @return boolean true if any sim card inserted, otherwise false.
     */
    public boolean isSimCardPresent(int slotId) throws Exception {
        Log.d(TAG, "isSimCardPresent[" + slotId + "]");

        MockModemConfigInterface configInterface = mMockModemService.getMockModemConfigInterface();
        return (configInterface != null) ? configInterface.isSimCardPresent(slotId, TAG) : false;
    }

    /**
     * Insert a SIM card.
     *
     * @param slotId which slot would insert.
     * @param simProfileId which carrier sim card is inserted.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean insertSimCard(int slotId, int simProfileId) throws Exception {
        Log.d(TAG, "insertSimCard[" + slotId + "] with profile Id(" + simProfileId + ")");
        boolean result = true;

        if (!isSimCardPresent(slotId)) {
            MockModemConfigInterface configInterface =
                    mMockModemService.getMockModemConfigInterface();
            if (configInterface != null) {
                TelephonyManager tm = sContext.getSystemService(TelephonyManager.class);

                result = configInterface.changeSimProfile(slotId, simProfileId, TAG);
                if (result) {
                    try {
                        waitForSubscriptionCondition(
                                () -> (TelephonyManager.SIM_STATE_PRESENT == tm.getSimCardState()),
                                TIMEOUT_IN_MSEC_FOR_SIM_STATUS_CHANGED);
                    } finally {
                        Log.d(TAG, "Insert Sim - subscription changed.");
                    }
                }
            }
        } else {
            Log.d(TAG, "There is a SIM inserted. Need to remove first.");
            result = false;
        }
        return result;
    }

    /**
     * Remove a SIM card.
     *
     * @param slotId which slot would remove the SIM.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean removeSimCard(int slotId) throws Exception {
        Log.d(TAG, "removeSimCard[" + slotId + "]");
        boolean result = true;

        if (isSimCardPresent(slotId)) {
            MockModemConfigInterface configInterface =
                    mMockModemService.getMockModemConfigInterface();
            if (configInterface != null) {
                TelephonyManager tm = sContext.getSystemService(TelephonyManager.class);

                result = configInterface.changeSimProfile(slotId, MOCK_SIM_PROFILE_ID_DEFAULT, TAG);
                if (result) {
                    try {
                        waitForSubscriptionCondition(
                                () -> (TelephonyManager.SIM_STATE_ABSENT == tm.getSimCardState()),
                                TIMEOUT_IN_MSEC_FOR_SIM_STATUS_CHANGED);
                    } finally {
                        Log.d(TAG, "Remove Sim - subscription changed.");
                    }
                }
            }
        } else {
            Log.d(TAG, "There is no SIM inserted.");
            result = false;
        }
        return result;
    }

    /**
     * Modify SIM info of the SIM such as MCC/MNC, IMSI, etc.
     *
     * @param slotId for modifying.
     * @param type the type of SIM info to modify.
     * @param data to modify for the type of SIM info.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean setSimInfo(int slotId, int type, String[] data) throws Exception {
        Log.d(TAG, "setSimInfo[" + slotId + "]");
        boolean result = true;

        if (isSimCardPresent(slotId)) {
            MockModemConfigInterface configInterface =
                    mMockModemService.getMockModemConfigInterface();
            if (configInterface != null) {
                configInterface.setSimInfo(slotId, type, data, TAG);

                // Wait for telephony framework refresh data and carrier config
                waitForTelephonyFrameworkDone(3);
            } else {
                Log.e(TAG, "MockModemConfigInterface == null!");
                result = false;
            }
        } else {
            Log.d(TAG, "There is no SIM inserted.");
            result = false;
        }
        return result;
    }

    /**
     * Get SIM info of the SIM slot, e.g. MCC/MNC, IMSI.
     *
     * @param slotId for the query.
     * @param type the type of SIM info.
     * @return String the SIM info of the queried type.
     */
    public String getSimInfo(int slotId, int type) throws Exception {
        Log.d(TAG, "getSimInfo[" + slotId + "]");
        String result = "";

        if (isSimCardPresent(slotId)) {
            MockModemConfigInterface configInterface =
                    mMockModemService.getMockModemConfigInterface();
            if (configInterface != null) {
                result = configInterface.getSimInfo(slotId, type, TAG);
            }
        } else {
            Log.d(TAG, "There is no SIM inserted.");
        }
        return result;
    }

    /**
     * Force the response error return for a specific RIL request
     *
     * @param slotId which slot needs to be set.
     * @param requestId the request/response message ID
     * @param error RIL_Errno and -1 means to disable the modified mechanism, back to original mock
     *     modem behavior
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean forceErrorResponse(int slotId, int requestId, int error) throws Exception {
        Log.d(
                TAG,
                "forceErrorResponse[" + slotId + "] for request:" + requestId + " ,error:" + error);
        boolean result = true;

        switch (requestId) {
            case RIL_REQUEST_RADIO_POWER:
                mMockModemService
                        .getIRadioModem((byte) slotId)
                        .forceErrorResponse(requestId, error);
                break;
            default:
                Log.e(TAG, "request:" + requestId + " not support to change the response error");
                result = false;
                break;
        }
        return result;
    }

    /**
     * Make the modem is in service or not.
     *
     * @param slotId which SIM slot is under the carrierId network.
     * @param carrierId which carrier network is used.
     * @param registration boolean true if the modem is in service, otherwise false.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean changeNetworkService(int slotId, int carrierId, boolean registration)
            throws Exception {
        Log.d(
                TAG,
                "changeNetworkService["
                        + slotId
                        + "] in carrier ("
                        + carrierId
                        + ") "
                        + registration);

        boolean result;
        result =
                mMockModemService
                        .getIRadioNetwork((byte) slotId)
                        .changeNetworkService(carrierId, registration);

        waitForTelephonyFrameworkDone(1);
        return result;
    }

    /**
     * Make the modem is in service or not for CS or PS registration
     *
     * @param slotId which SIM slot is under the carrierId network.
     * @param carrierId which carrier network is used.
     * @param registration boolean true if the modem is in service, otherwise false.
     * @param domainBitmask int specify domains (CS only, PS only, or both).
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean changeNetworkService(
            int slotId, int carrierId,
            boolean registration, int domainBitmask) throws Exception {
        return changeNetworkService(
                slotId, carrierId, registration, domainBitmask, 0 /* regFailCause */);
    }

    /**
     * Make the modem is in service or not for CS or PS registration
     *
     * @param slotId which SIM slot is under the carrierId network.
     * @param carrierId which carrier network is used.
     * @param registration boolean true if the modem is in service, otherwise false.
     * @param domainBitmask int specify domains (CS only, PS only, or both).
     * @param regFailCause int reason code for registration failure.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean changeNetworkService(
            int slotId, int carrierId,
            boolean registration, int domainBitmask,
            int regFailCause) throws Exception {
        Log.d(
                TAG,
                "changeNetworkService["
                        + slotId
                        + "] in carrier ("
                        + carrierId
                        + ") "
                        + registration
                        + " with domainBitmask = "
                        + domainBitmask
                        + " with failCause = "
                        + regFailCause);

        boolean result;
        result =
                mMockModemService
                        .getIRadioNetwork((byte) slotId)
                        .changeNetworkService(
                                carrierId, registration, domainBitmask, regFailCause);

        waitForTelephonyFrameworkDone(1);
        return result;
    }

    /**
     * get GSM CellBroadcastConfig outputs from IRadioMessagingImpl
     *
     * @param slotId which slot would insert
     * @return Set of broadcast configs
     */
    public Set<Integer> getGsmBroadcastConfig(int slotId) {
        return mMockModemService.getIRadioMessaging((byte) slotId).getGsmBroadcastConfigSet();
    }

    /**
     * get CDMA CellBroadcastConfig outputs from IRadioMessagingImpl
     *
     * @param slotId which slot would insert
     * @return Set of broadcast configs
     */
    public Set<Integer> getCdmaBroadcastConfig(int slotId) {
        return mMockModemService.getIRadioMessaging((byte) slotId).getCdmaBroadcastConfigSet();
    }

    /**
     * receive new broadcast sms message
     *
     * @param slotId which slot would insert
     * @param data data of broadcast messages to be received
     */
    public void newBroadcastSms(int slotId, byte[] data) {
        mMockModemService.getIRadioMessaging((byte) slotId).newBroadcastSms(data);
    }

    /**
     * register callback for monitoring broadcast activation
     *
     * @param slotId which slot would insert
     * @param callback callback to register
     */
    public void registerBroadcastCallback(int slotId,
            IRadioMessagingImpl.CallBackWithExecutor callback) {
        mMockModemService.getIRadioMessaging((byte) slotId).registerBroadcastCallback(callback);
    }

    /**
     * unregister callback for monitoring broadcast activation
     *
     * @param slotId which slot would insert
     * @param callback callback to unregister
     */
    public void unregisterBroadcastCallback(int slotId,
            IRadioMessagingImpl.CallBackWithExecutor callback) {
        mMockModemService.getIRadioMessaging((byte) slotId).unregisterBroadcastCallback(callback);
    }

    /**
     * Indicates when Single Radio Voice Call Continuity (SRVCC) progress state has changed.
     *
     * @param slotId which slot would insert.
     * @param state New SRVCC State
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean srvccStateNotify(int slotId, int state) {
        Log.d(TAG, "notifySrvccState[" + slotId + "] with state(" + state + ")");

        boolean result = false;
        try {
            IRadioVoiceImpl radioVoice = mMockModemService.getIRadioVoice((byte) slotId);
            if (radioVoice != null) {
                radioVoice.srvccStateNotify(state);

                waitForTelephonyFrameworkDone(1);
                result = true;
            }
        } catch (Exception e) {
        }
        return result;
    }

    /**
     * Returns the list of MockSrvccCall
     *
     * @param slotId for which slot to get the list
     * @return the list of {@link MockSrvccCall}
     */
    public List<MockSrvccCall> getSrvccCalls(int slotId) {
        Log.d(TAG, "getSrvccCalls[" + slotId + "]");

        IRadioImsImpl radioIms = mMockModemService.getIRadioIms((byte) slotId);
        if (radioIms == null) return null;
        return radioIms.getSrvccCalls();
    }

    /**
     * Triggers IMS deregistration.
     *
     * @param slotId which slot would insert.
     * @param reason the reason why the deregistration is triggered.
     * @return {@code true} if the operation is successful, otherwise {@code false}.
     */
    public boolean triggerImsDeregistration(int slotId,
            @ImsRegistrationImplBase.ImsDeregistrationReason int reason) {
        Log.d(TAG, "triggerImsDeregistration[" + slotId + "] reason=" + reason);

        boolean result = false;
        try {
            mMockModemService.getIRadioIms().triggerImsDeregistration(reason);

            waitForTelephonyFrameworkDone(1);
            result = true;
        } catch (Exception e) {
        }
        return result;
    }

    /**
     * Clears the Anbr values.
     *
     * @param slotId for which slot to get the reason.
     */
    public void resetAnbrValues(int slotId) {
        Log.d(TAG, "resetAnbrValues[" + slotId + "]");

        try {
            IRadioImsImpl radioIms = mMockModemService.getIRadioIms((byte) slotId);
            if (radioIms == null) return;
            radioIms.resetAnbrValues();
        } catch (Exception e) {
            Log.e(TAG, "resetAnbrValues - failed");
        }
    }

    /**
     * Returns the Anbr values.
     *
     * @param slotId for which slot to get the reason.
     * @return the Anbr values triggered by Anbr Query.
     */
    public int[] getAnbrValues(int slotId) {
        Log.d(TAG, "getAnbrValues[" + slotId + "]");

        IRadioImsImpl radioIms = mMockModemService.getIRadioIms((byte) slotId);
        if (radioIms == null) return null;
        return radioIms.getAnbrValues();
    }

    /**
     * Triggers NotifyAnbr.
     *
     * @param slotId which slot would insert.
     * @param qosSessionId is used to identify media stream such as audio or video.
     * @param direction Direction of this packet stream (e.g. uplink or downlink).
     * @param bitsPerSecond is the bitrate received from the NW through the Recommended
     *        bitrate MAC Control Element message and ImsStack converts this value from MAC bitrate
     *        to audio/video codec bitrate (defined in TS26.114).
     * @return {@code true} if the operation is successful, otherwise {@code false}.
     */
    public boolean notifyAnbr(int slotId, int qosSessionId, int direction, int bitsPerSecond) {
        Log.d(TAG, "mockmodem - notifyAnbr[" + slotId + "] qosSessionId=" + qosSessionId
                + ", direction=" + direction + ", bitsPerSecond" + bitsPerSecond);

        boolean result = false;
        try {

            IRadioImsImpl radioIms = mMockModemService.getIRadioIms((byte) slotId);
            if (radioIms == null) return false;
            radioIms.notifyAnbr(qosSessionId, direction, bitsPerSecond);

            waitForTelephonyFrameworkDone(1);
            result = true;
        } catch (Exception e) {
            Log.e(TAG, "Create notifyAnbr - failed");
        }
        return result;
    }

    /**
     * Returns the reason that caused EPS fallback.
     *
     * @param slotId for which slot to get the reason
     * @return the reason that caused EPS fallback.
     */
    public @MmTelFeature.EpsFallbackReason int getEpsFallbackReason(int slotId) {
        Log.d(TAG, "getEpsFallbackReason[" + slotId + "]");

        IRadioImsImpl radioIms = mMockModemService.getIRadioIms((byte) slotId);
        if (radioIms == null) return -1;
        return radioIms.getEpsFallbackReason();
    }

    /**
     * Clears the EPS fallback reason.
     *
     * @param slotId for which slot to get the reason
     */
    public void resetEpsFallbackReason(int slotId) {
        Log.d(TAG, "resetEpsFallbackReason[" + slotId + "]");

        IRadioImsImpl radioIms = mMockModemService.getIRadioIms((byte) slotId);
        if (radioIms == null) return;
        radioIms.resetEpsFallbackReason();
    }

    /**
     * Updates the emergency registration state.
     *
     * @param slotId the Id of logical sim slot.
     * @param regResult the emergency registration state.
     */
    public void setEmergencyRegResult(int slotId, MockEmergencyRegResult regResult) {
        Log.d(TAG, "setEmergencyRegResult[" + slotId + "]");
        mMockModemService.getIRadioNetwork((byte) slotId).setEmergencyRegResult(regResult);
    }

    /**
     * Notifies the barring information change.
     *
     * @param slotId the Id of logical sim slot.
     * @param barringServiceInfos the barring information.
     */
    public boolean unsolBarringInfoChanged(int slotId,
            SparseArray<BarringInfo.BarringServiceInfo> barringServiceInfos) {
        Log.d(TAG, "unsolBarringInfoChanged[" + slotId + "]");
        return mMockModemService.getIRadioNetwork((byte) slotId)
                .unsolBarringInfoChanged(barringServiceInfos);
    }

    /**
     * Triggers RIL_UNSOL_EMERGENCY_NETWORK_SCAN_RESULT unsol message.
     *
     * @param slotId the Id of logical sim slot.
     * @param regResult the registration result.
     */
    public boolean unsolEmergencyNetworkScanResult(int slotId, MockEmergencyRegResult regResult) {
        Log.d(TAG, "unsolEmergencyNetworkScanResult[" + slotId + "]");
        return mMockModemService.getIRadioNetwork((byte) slotId)
                .unsolEmergencyNetworkScanResult(regResult);
    }

    /**
     * Resets the current emergency mode.
     *
     * @param slotId the Id of logical sim slot.
     */
    public void resetEmergencyMode(int slotId) {
        Log.d(TAG, "resetEmergencyMode[" + slotId + "]");
        mMockModemService.getIRadioNetwork((byte) slotId).resetEmergencyMode();
    }

    /**
     * @return the current emergency mode.
     *
     * @param slotId the Id of logical sim slot.
     */
    public int getEmergencyMode(int slotId) {
        Log.d(TAG, "getEmergencyMode[" + slotId + "]");
        return mMockModemService.getIRadioNetwork((byte) slotId).getEmergencyMode();
    }

    /**
     * Returns whether emergency network scan is triggered.
     *
     * @param slotId the Id of logical sim slot.
     * @return {@code true} if emergency network scan is triggered.
     */
    public boolean isEmergencyNetworkScanTriggered(int slotId) {
        Log.d(TAG, "isEmergencyNetworkScanTriggered[" + slotId + "]");
        return mMockModemService.getIRadioNetwork((byte) slotId).isEmergencyNetworkScanTriggered();
    }

    /**
     * Returns whether emergency network scan is canceled.
     *
     * @param slotId the Id of logical sim slot.
     * @return {@code true} if emergency network scan is canceled.
     */
    public boolean isEmergencyNetworkScanCanceled(int slotId) {
        Log.d(TAG, "isEmergencyNetworkScanCanceled[" + slotId + "]");
        return mMockModemService.getIRadioNetwork((byte) slotId).isEmergencyNetworkScanCanceled();
    }

    /**
     * Returns the list of preferred network type.
     *
     * @param slotId the Id of logical sim slot.
     * @return the list of preferred network type.
     */
    public int[] getEmergencyNetworkScanAccessNetwork(int slotId) {
        Log.d(TAG, "getEmergencyNetworkScanAccessNetwork[" + slotId + "]");
        return mMockModemService.getIRadioNetwork((byte) slotId)
                .getEmergencyNetworkScanAccessNetwork();
    }

    /**
     * Returns the preferred scan type.
     *
     * @param slotId the Id of logical sim slot.
     * @return the preferred scan type.
     */
    public int getEmergencyNetworkScanType(int slotId) {
        Log.d(TAG, "getEmergencyNetworkScanType[" + slotId + "]");
        return mMockModemService.getIRadioNetwork((byte) slotId).getEmergencyNetworkScanType();
    }

    /**
     * Resets the emergency network scan attributes.
     *
     * @param slotId the Id of logical sim slot.
     */
    public void resetEmergencyNetworkScan(int slotId) {
        Log.d(TAG, "resetEmergencyNetworkScan[" + slotId + "]");
        mMockModemService.getIRadioNetwork((byte) slotId).resetEmergencyNetworkScan();
    }

    /**
     * Waits for the event of network service.
     *
     * @param slotId the Id of logical sim slot.
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     */
    public boolean waitForNetworkLatchCountdown(int slotId, int latchIndex, int waitMs) {
        Log.d(TAG, "waitForNetworkLatchCountdown[" + slotId + "]");
        return mMockModemService.getIRadioNetwork((byte) slotId)
                .waitForLatchCountdown(latchIndex, waitMs);
    }

    /**
     * Resets the CountDownLatches of network service.
     *
     * @param slotId the Id of logical sim slot.
     */
    public void resetNetworkAllLatchCountdown(int slotId) {
        Log.d(TAG, "resetNetworkAllLatchCountdown[" + slotId + "]");
        mMockModemService.getIRadioNetwork((byte) slotId).resetAllLatchCountdown();
    }

    /**
     * Waits for the event of voice service.
     *
     * @param slotId the Id of logical sim slot.
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     */
    public boolean waitForVoiceLatchCountdown(int slotId, int latchIndex, int waitMs) {
        Log.d(TAG, "waitForVoiceLatchCountdown[" + slotId + "]");
        return mMockModemService.getIRadioVoice((byte) slotId)
                .waitForLatchCountdown(latchIndex, waitMs);
    }

    /**
     * Resets the CountDownLatches of voice service.
     *
     * @param slotId the Id of logical sim slot.
     */
    public void resetVoiceAllLatchCountdown(int slotId) {
        Log.d(TAG, "resetVoiceAllLatchCountdown[" + slotId + "]");
        mMockModemService.getIRadioVoice((byte) slotId).resetAllLatchCountdown();
    }

    /**
     * Stops sending default response to startImsTraffic.
     *
     * @param slotId which slot would insert.
     * @param blocked indicates whether sending response is allowed or not.
     */
    public void blockStartImsTrafficResponse(int slotId, boolean blocked) {
        Log.d(TAG, "blockStartImsTrafficResponse[" + slotId + "] blocked(" + blocked + ")");

        mMockModemService.getIRadioIms((byte) slotId).blockStartImsTrafficResponse(blocked);
    }

    /**
     * Returns whether the given IMS traffic type is started or not.
     *
     * @param slotId which slot would insert.
     * @param trafficType the IMS traffic type
     * @return boolean true if the given IMS traffic type is started
     */
    public boolean isImsTrafficStarted(int slotId,
            @MmTelFeature.ImsTrafficType int trafficType) {
        Log.d(TAG, "isImsTrafficStarted[" + slotId + "] trafficType(" + trafficType + ")");

        return mMockModemService.getIRadioIms((byte) slotId).isImsTrafficStarted(trafficType);
    }

    /**
     * Sends the response with the given information.
     *
     * @param slotId which slot would insert.
     * @param trafficType the IMS traffic type
     * @param reason The reason of failure.
     * @param causeCode Failure cause code from network or modem specific to the failure.
     * @param waitTimeMillis Retry wait time provided by network in milliseconds.
     * @return boolean true if there is no error
     */
    public boolean sendStartImsTrafficResponse(int slotId,
            @MmTelFeature.ImsTrafficType int trafficType,
            @ConnectionFailureInfo.FailureReason int reason,
            int causeCode, int waitTimeMillis) {
        Log.d(TAG, "sendStartImsTrafficResponse[" + slotId
                + "] trafficType(" + trafficType + ")"
                + " reason(" + reason + ")"
                + " cause(" + causeCode + ")"
                + " wait(" + waitTimeMillis + ")");

        boolean result = false;
        try {
            mMockModemService.getIRadioIms((byte) slotId).sendStartImsTrafficResponse(
                    trafficType, reason, causeCode, waitTimeMillis);

            waitForTelephonyFrameworkDone(1);
            result = true;
        } catch (Exception e) {
            Log.e(TAG, "sendStartImsTrafficResponse e=" + e);
        }
        return result;
    }

    /**
     * Notifies the connection failure info
     *
     * @param slotId which slot would insert.
     * @param trafficType the IMS traffic type
     * @param reason The reason of failure.
     * @param causeCode Failure cause code from network or modem specific to the failure.
     * @param waitTimeMillis Retry wait time provided by network in milliseconds.
     * @return boolean true if there is no error
     */
    public boolean sendConnectionFailureInfo(int slotId,
            @MmTelFeature.ImsTrafficType int trafficType,
            @ConnectionFailureInfo.FailureReason int reason,
            int causeCode, int waitTimeMillis) {
        Log.d(TAG, "sendConnectionFailureInfo[" + slotId
                + "] trafficType(" + trafficType + ")"
                + " reason(" + reason + ")"
                + " cause(" + causeCode + ")"
                + " wait(" + waitTimeMillis + ")");

        boolean result = false;
        try {
            mMockModemService.getIRadioIms((byte) slotId).sendConnectionFailureInfo(
                    trafficType, reason, causeCode, waitTimeMillis);

            waitForTelephonyFrameworkDone(1);
            result = true;
        } catch (Exception e) {
            Log.e(TAG, "sendConnectionFailureInfo e=" + e);
        }
        return result;
    }

    /**
     * Clears the IMS traffic state.
     */
    public void clearImsTrafficState() {
        mMockModemService.getIRadioIms().clearImsTrafficState();
    }

    /**
     * Waits for the event of mock IMS state.
     *
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     */
    public boolean waitForImsLatchCountdown(int latchIndex, int waitMs) {
        return mMockModemService.getIRadioIms().waitForLatchCountdown(latchIndex, waitMs);
    }

    /** Resets the CountDownLatches of IMS state. */
    public void resetImsAllLatchCountdown() {
        mMockModemService.getIRadioIms().resetAllLatchCountdown();
    }

    /**
     * Set/override default call control configuration.
     *
     * @param slotId the Id of logical sim slot.
     * @param callControlInfo the configuration of call control would like to override.
     * @return boolean true if the operation succeeds, otherwise false.
     */
    public boolean setCallControlInfo(int slotId, MockCallControlInfo callControlInfo) {
        Log.d(TAG, "setCallControlInfo[" + slotId + "]");
        return mMockModemService
                .getMockModemConfigInterface()
                .setCallControlInfo(slotId, callControlInfo, TAG);
    }

    /**
     * Get call control configuration.
     *
     * @param slotId the Id of logical sim slot.
     * @return MockCallControlInfo which was set/overridden before.
     */
    public MockCallControlInfo getCallControlInfo(int slotId) {
        Log.d(TAG, "getCallControlInfo[" + slotId + "]");
        return mMockModemService.getMockModemConfigInterface().getCallControlInfo(slotId, TAG);
    }

    /**
     * Trigger an incoming voice call.
     *
     * @param slotId the Id of logical sim slot.
     * @param address phone number of the incoming call
     * @param uusInfo user to user signaling information.
     * @param cdmaSignalInfoRecord CDMA Signal Information Record.
     * @return boolean true if the operation succeeds, otherwise false.
     */
    public boolean triggerIncomingVoiceCall(
            int slotId,
            String address,
            UusInfo[] uusInfo,
            CdmaSignalInfoRecord cdmaSignalInfoRecord)
            throws Exception {
        return triggerIncomingVoiceCall(slotId, address, uusInfo, cdmaSignalInfoRecord, null);
    }

    /**
     * Trigger an incoming voice call with a call control configuration.
     *
     * @param slotId the Id of logical sim slot.
     * @param address phone number of the incoming call
     * @param uusInfo user to user signaling information.
     * @param cdmaSignalInfoRecord CDMA Signal Information Record.
     * @param callControlInfo the configuration of call control would like to override.
     * @return boolean true if the operation succeeds, otherwise false.
     */
    public boolean triggerIncomingVoiceCall(
            int slotId,
            String address,
            UusInfo[] uusInfo,
            CdmaSignalInfoRecord cdmaSignalInfoRecord,
            MockCallControlInfo callControlInfo)
            throws Exception {
        Log.d(TAG, "triggerIncomingVoiceCall[" + slotId + "] address: " + address);
        boolean result;

        result =
                mMockModemService
                        .getMockModemConfigInterface()
                        .triggerIncomingVoiceCall(
                                slotId,
                                address,
                                uusInfo,
                                cdmaSignalInfoRecord,
                                callControlInfo,
                                TAG);

        waitForTelephonyFrameworkDone(1);
        return result;
    }

    /**
     * Get number of on going CS calls.
     *
     * @param slotId the Id of logical sim slot.
     * @return int the number of CS calls.
     */
    public int getNumberOfOngoingCSCalls(int slotId) {
        Log.d(TAG, "getNumberOfOngoingCSCalls[" + slotId + "]");
        return mMockModemService.getMockModemConfigInterface().getNumberOfCalls(slotId, TAG);
    }

    /**
     * Sets the last call fail cause.
     *
     * @param slotId the Id of logical sim slot.
     * @param cause The disconnect cause.
     */
    public void setLastCallFailCause(int slotId, int cause) {
        Log.d(TAG, "setLastCallFailCause[" + slotId + "] cause = " + cause);
        mMockModemService.getMockModemConfigInterface().setLastCallFailCause(slotId, cause, TAG);
    }

    /**
     * Clears all calls with the given cause.
     *
     * @param slotId the Id of logical sim slot.
     * @param cause The disconnect cause.
     */
    public void clearAllCalls(int slotId, @Annotation.DisconnectCauses int cause) {
        Log.d(TAG, "clearAllCalls[" + slotId + "] cause = " + cause);
        mMockModemService.getMockModemConfigInterface().clearAllCalls(slotId, cause, TAG);
    }

    /**
     * Reports the list of emergency numbers.
     *
     * @param numbers The list of emergency numbers.
     */
    public void notifyEmergencyNumberList(int slotId, String[] numbers) {
        Log.d(TAG, "notifyEmergencyNumberList[" + slotId + "]");
        mMockModemService.getIRadioVoice((byte) slotId).notifyEmergencyNumberList(numbers);
    }

    /**
     * Sets the new carrierId and CarrierRestriction status values in IRadioSimImpl.java
     * @param carrierList
     * @param carrierRestrictionStatus
     */
    public void updateCarrierRestrictionInfo(Carrier[] carrierList, int carrierRestrictionStatus) {
        mMockModemService.getIRadioSim().updateCarrierRestrictionStatusInfo(carrierList,
                carrierRestrictionStatus);
    }

    /**
     * Helper method that can be called from the test suite to change the primary IMEi mapping
     * with respect to sim slot.
     */
    public boolean changeImeiMapping() {
        int modemCount = mMockModemService.getActiveMockModemCount();
        if (modemCount == 1) {
            return false;
        }
        for (int slotId = 0; slotId < modemCount; slotId++) {
            mMockModemService.getIRadioModem((byte) slotId).resetAllLatchCountdown();
            mMockModemService.getIRadioModem((byte) slotId).changeImeiMapping();
            if (waitForModemLatchCountdown(slotId, WAIT_UPDATE_TIMEOUT_MS)) {
                Log.e(TAG, "Error in waitForModemLatchCountdown");
            }
        }
        return true;
    }

    /**
     * Wait latch for 0.2 sec or latch to countdown.
     * @param slotId : slotId of the device.
     * @param waitMs : Wait in milliseconds for the latch-wait timeout.
     * @return true if latch is success else fail.
     */
    public boolean waitForModemLatchCountdown(int slotId, int waitMs) {
        Log.d(TAG, "waitForVoiceLatchCountdown[" + slotId + "]");
        return mMockModemService.getIRadioModem((byte) slotId)
                .waitForLatchCountdown(slotId, waitMs);
    }

    public void setCarrierRestrictionRules(CarrierRestrictions carrierRestrictionRules,
            int multiSimPolicy) {
        mMockModemService.getIRadioSim().updateCarrierRestrictionRules(
                carrierRestrictionRules, multiSimPolicy);
    }

    /**
     * Triggers an incoming SMS.
     *
     * @param slotId the Id of logical sim slot.
     * @return {@code true} if the operation succeeds otherwise false.
     */
    public boolean triggerIncomingSms(int slotId) {
        Log.d(TAG, "triggerIncomingSms[" + slotId + "]");

        boolean result = false;
        try {
            String pdu = "07916164260220F0040B914151245584F600006060605130308A04D4F29C0E";
            mMockModemService.getIRadioMessaging((byte) slotId)
                    .newSms(TelephonyUtils.hexStringToByteArray(pdu));

            waitForTelephonyFrameworkDone(1);
            result = true;
        } catch (Exception e) {
            Log.e(TAG, "triggerIncomingSms e=" + e);
        }
        return result;
    }
}
