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

import android.hardware.radio.modem.ImeiInfo;
import android.hardware.radio.voice.CdmaSignalInfoRecord;
import android.hardware.radio.voice.LastCallFailCauseInfo;
import android.hardware.radio.voice.UusInfo;
import android.os.Handler;
import android.telephony.Annotation;

public interface MockModemConfigInterface {

    // ***** Constants
    int MAX_NUM_OF_SIM_SLOT = 3; // Change this needs to add more SIM SLOT NVs.
    int MAX_NUM_OF_LOGICAL_MODEM = 3; // Change this needs to add more MODEM NVs.
    int RADIO_STATE_UNAVAILABLE = 0;
    int RADIO_STATE_OFF = 1;
    int RADIO_STATE_ON = 2;

    // Default config value
    String DEFAULT_BASEBAND_VERSION = "mock-modem-service-1.0";
    // PHONE1
    String DEFAULT_PHONE1_IMEI = "123456789012345";
    String DEFAULT_PHONE1_IMEISV = "01";
    String DEFAULT_PHONE1_ESN = "123456789";
    String DEFAULT_PHONE1_MEID = "123456789012345";
    int DEFAULT_PHONE1_IMEITYPE = ImeiInfo.ImeiType.PRIMARY;
    // PHONE2
    String DEFAULT_PHONE2_IMEI = "987654321543210";
    String DEFAULT_PHONE2_IMEISV = "02";
    String DEFAULT_PHONE2_ESN = "987654321";
    String DEFAULT_PHONE2_MEID = "987654321543210";
    int DEFAULT_PHONE2_IMEITYPE = ImeiInfo.ImeiType.SECONDARY;
    // PHONE3
    String DEFAULT_PHONE3_IMEI = "987654321012345";
    String DEFAULT_PHONE3_IMEISV = "03";
    String DEFAULT_PHONE3_ESN = "192837465";
    String DEFAULT_PHONE3_MEID = "987654321012345";
    int DEFAULT_PHONE3_IMEITYPE = ImeiInfo.ImeiType.SECONDARY;

    int DEFAULT_RADIO_STATE = RADIO_STATE_UNAVAILABLE;
    int DEFAULT_NUM_OF_LIVE_MODEM = 1; // Should <= MAX_NUM_OF_MODEM
    int DEFAULT_MAX_ACTIVE_DATA = 2;
    int DEFAULT_MAX_ACTIVE_INTERNAL_DATA = 1;
    boolean DEFAULT_IS_INTERNAL_LINGERING_SUPPORTED = false;
    int DEFAULT_LOGICAL_MODEM1_ID = 0;
    int DEFAULT_LOGICAL_MODEM2_ID = 1;
    int DEFAULT_LOGICAL_MODEM3_ID = 2;

    // ***** Methods
    void destroy();

    Handler getMockModemConfigHandler(int logicalSlotId);

    /** Broadcast all notifications */
    void notifyAllRegistrantNotifications();

    // ***** IRadioConfig
    /** Register/unregister notification handler for number of modem changed */
    void registerForNumOfLiveModemChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForNumOfLiveModemChanged(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for sim slot status changed */
    void registerForPhoneCapabilityChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForPhoneCapabilityChanged(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for sim slot status changed */
    void registerForSimSlotStatusChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForSimSlotStatusChanged(int logicalSlotId, Handler h);

    // ***** IRadioModem
    /** Register/unregister notification handler for baseband version changed */
    void registerForBasebandVersionChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForBasebandVersionChanged(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for device identity changed */
    void registerForDeviceIdentityChanged(int logicalSlotId, Handler h, int what, Object obj);

    /** Register/unregister notification handler for device ImeiInfo changed */
    void registerForDeviceImeiInfoChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForDeviceIdentityChanged(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for radio state changed */
    void registerForRadioStateChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForRadioStateChanged(int logicalSlotId, Handler h);

    /**
     * Sets the latest radio power state of modem
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param state 0 means "unavailable", 1 means "off", 2 means "on".
     * @param client for tracking calling client
     */
    void setRadioState(int logicalSlotId, int state, String client);

    // ***** IRadioSim
    /** Register/unregister notification handler for card status changed */
    void registerForCardStatusChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForCardStatusChanged(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for sim app data changed */
    void registerForSimAppDataChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForSimAppDataChanged(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for sim info changed */
    void registerForSimInfoChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForSimInfoChanged(int logicalSlotId, Handler h);

    // ***** IRadioNetwork
    /** Register/unregister notification handler for service status changed */
    void registerForServiceStateChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForServiceStateChanged(int logicalSlotId, Handler h);

    // ***** IRadioVoice
    /** Register/unregister notification handler for call state changed */
    void registerForCallStateChanged(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForCallStateChanged(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for current calls response */
    void registerForCurrentCallsResponse(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForCurrentCallsResponse(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for incoming call */
    void registerForCallIncoming(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterForCallIncoming(int logicalSlotId, Handler h);

    /** Register/unregister notification handler for ringback tone */
    void registerRingbackTone(int logicalSlotId, Handler h, int what, Object obj);

    void unregisterRingbackTone(int logicalSlotId, Handler h);

    /**
     * Request to get current calls.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean getCurrentCalls(int logicalSlotId, String client);

    /**
     * Request to dial a voice call.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param address the phone number to dial.
     * @param clir CLIR mode.
     * @param uusInfo user to user signaling information.
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean dialVoiceCall(
            int logicalSlotId, String address, int clir, UusInfo[] uusInfo, String client);

    /**
     * Request to dial a voice call with a call control info.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param address the phone number to dial.
     * @param clir CLIR mode.
     * @param uusInfo user to user signaling information.
     * @param callControlInfo call control configuration
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean dialVoiceCall(
            int logicalSlotId,
            String address,
            int clir,
            UusInfo[] uusInfo,
            MockCallControlInfo callControlInfo,
            String client);

    /**
     * Request to dial an emergency voice call.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param address the phone number to dial.
     * @param categories the Emergency Service Category(s) of the call.
     * @param urns the emergency Uniform Resource Names (URN).
     * @param routing EmergencyCallRouting the emergency call routing information.
     * @param client for tracking calling client.
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean dialEccVoiceCall(int logicalSlotId, String address,
            int categories, String[] urns, int routing, String client);

    /**
     * Request to dial an emergency voice call with call control info.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param address the phone number to dial.
     * @param categories the Emergency Service Category(s) of the call.
     * @param urns the emergency Uniform Resource Names (URN).
     * @param routing EmergencyCallRouting the emergency call routing information.
     * @param callControlInfo call control configuration.
     * @param client for tracking calling client.
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean dialEccVoiceCall(int logicalSlotId, String address,
            int categories, String[] urns, int routing,
            MockCallControlInfo callControlInfo, String client);

    /**
     * Request to hangup a voice call.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param index call identify to hangup.
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean hangupVoiceCall(int logicalSlotId, int index, String client);

    /**
     * Request to reject an incoming voice call.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean rejectVoiceCall(int logicalSlotId, String client);

    /**
     * Request to accept an incoming voice call.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean acceptVoiceCall(int logicalSlotId, String client);

    /**
     * Get last call fail cause.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client
     * @return LastCallFailCauseInfo last cause code and vendor cause info.
     */
    LastCallFailCauseInfo getLastCallFailCause(int logicalSlotId, String client);

    /**
     * Sets the last call fail cause.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client.
     * @param cause the disconnect cause code.
     */
    void setLastCallFailCause(int logicalSlotId,
            @Annotation.DisconnectCauses int cause, String client);

    /**
     * Clears all calls.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client.
     * @param cause the disconnect cause code.
     */
    void clearAllCalls(int logicalSlotId,
            @Annotation.DisconnectCauses int cause, String client);

    /**
     * Get voice mute mode.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client
     * @return boolean true if voice is mute, otherwise false.
     */
    boolean getVoiceMuteMode(int logicalSlotId, String client);

    /**
     * Set voice mute mode.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param muteMode mute mode for voice call.
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean setVoiceMuteMode(int logicalSlotId, boolean muteMode, String client);

    // ***** Utility methods
    /**
     * Query whether any SIM cards are present or not.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client
     * @return boolean true if any sim card inserted, otherwise false.
     */
    boolean isSimCardPresent(int logicalSlotId, String client);

    /**
     * Change SIM profile
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param simProfileId The target profile to be switched.
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds.
     */
    boolean changeSimProfile(int logicalSlotId, int simProfileId, String client);

    /**
     * Modify SIM info of the SIM such as MCC/MNC, IMSI, etc.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param type the type of SIM info to modify.
     * @param data to modify for the type of SIM info.
     * @param client for tracking calling client
     */
    void setSimInfo(int logicalSlotId, int type, String[] data, String client);

    /**
     * Get SIM info of the SIM slot, e.g. MCC/MNC, IMSI.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param type the type of SIM info.
     * @param client for tracking calling client
     * @return String the SIM info of the queried type.
     */
    String getSimInfo(int logicalSlotId, int type, String client);

    /**
     * Request to set call control configuration.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param callControlInfo the configuration of call control.
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean setCallControlInfo(
            int logicalSlotId, MockCallControlInfo callControlInfo, String client);

    /**
     * Request to get call control configuration.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client
     * @return MockCallControlInfo which was set before.
     */
    MockCallControlInfo getCallControlInfo(int logicalSlotId, String client);

    /**
     * Request to trigger an incoming voice call with a call control info.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param address the phone number to dial.
     * @param uusInfo user to user signaling information.
     * @param cdmaSignalInfoRecord CDMA Signal Information Record as defined in C.S0005 section
     *     3.7.5.5, null for GSM case.
     * @param callControlInfo call control configuration
     * @param client for tracking calling client
     * @return boolean true if the operation succeeds, otherwise false.
     */
    boolean triggerIncomingVoiceCall(
            int logicalSlotId,
            String address,
            UusInfo[] uusInfo,
            CdmaSignalInfoRecord cdmaSignalInfoRecord,
            MockCallControlInfo callControlInfo,
            String client);

    /**
     * Get number of voice calls.
     *
     * @param logicalSlotId the Id of logical sim slot.
     * @param client for tracking calling client
     * @return int number of ongoing calls
     */
    int getNumberOfCalls(int logicalSlotId, String client);
}
