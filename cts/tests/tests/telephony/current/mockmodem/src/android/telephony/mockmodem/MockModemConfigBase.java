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

import static android.telephony.mockmodem.MockSimService.EF_ICCID;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_DEFAULT;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_MAX;
import static android.telephony.mockmodem.MockVoiceService.MockCallInfo.CALL_TYPE_EMERGENCY;
import static android.telephony.mockmodem.MockVoiceService.MockCallInfo.CALL_TYPE_VOICE;

import android.content.Context;
import android.hardware.radio.config.PhoneCapability;
import android.hardware.radio.config.SimPortInfo;
import android.hardware.radio.config.SimSlotStatus;
import android.hardware.radio.config.SlotPortMapping;
import android.hardware.radio.modem.ImeiInfo;
import android.hardware.radio.sim.CardStatus;
import android.hardware.radio.voice.CdmaSignalInfoRecord;
import android.hardware.radio.voice.LastCallFailCauseInfo;
import android.hardware.radio.voice.UusInfo;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.telephony.Annotation;
import android.telephony.mockmodem.MockSimService.SimAppData;
import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

public class MockModemConfigBase implements MockModemConfigInterface {
    // ***** Instance Variables
    private static final int DEFAULT_SLOT_ID = 0;
    private static final int ESIM_SLOT_ID = 1;
    private final String mTAG = "MockModemConfigBase";
    private final Handler[] mHandler;
    private Context mContext;
    private int mSubId;
    private int mSimPhyicalId;
    private Object[] mConfigAccess;
    private final Object mSimMappingAccess = new Object();
    private int mNumOfSim = MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT;
    private int mNumOfPhone = MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM;

    // ***** Events
    static final int EVENT_SET_RADIO_POWER = 1;
    static final int EVENT_CHANGE_SIM_PROFILE = 2;
    static final int EVENT_SERVICE_STATE_CHANGE = 3;
    static final int EVENT_SET_SIM_INFO = 4;
    static final int EVENT_CALL_STATE_CHANGE = 5;
    static final int EVENT_CURRENT_CALLS_RESPONSE = 6;
    static final int EVENT_CALL_INCOMING = 7;
    static final int EVENT_RINGBACK_TONE = 8;

    // ***** Modem config values
    private String mBasebandVersion = MockModemConfigInterface.DEFAULT_BASEBAND_VERSION;
    private String[] mImei;
    private String[] mImeiSv;
    private String[] mEsn;
    private String[] mMeid;
    private int[] mImeiType;
    private int mRadioState = MockModemConfigInterface.DEFAULT_RADIO_STATE;
    private byte mNumOfLiveModem = MockModemConfigInterface.DEFAULT_NUM_OF_LIVE_MODEM;
    private PhoneCapability mPhoneCapability = new PhoneCapability();

    // ***** Sim config values
    private SimSlotStatus[] mSimSlotStatus;
    private CardStatus[] mCardStatus;
    private int[] mLogicalSimIdMap;
    private int[] mFdnStatus;
    private MockSimService[] mSimService;
    private ArrayList<SimAppData>[] mSimAppList;

    // **** Voice config values
    private MockVoiceService[] mVoiceService;
    private MockCallControlInfo mCallControlInfo;

    // ***** RegistrantLists
    // ***** IRadioConfig RegistrantLists
    private RegistrantList mNumOfLiveModemChangedRegistrants = new RegistrantList();
    private RegistrantList mPhoneCapabilityChangedRegistrants = new RegistrantList();
    private RegistrantList mSimSlotStatusChangedRegistrants = new RegistrantList();

    // ***** IRadioModem RegistrantLists
    private RegistrantList mBasebandVersionChangedRegistrants = new RegistrantList();
    private RegistrantList[] mDeviceIdentityChangedRegistrants;
    private RegistrantList[] mDeviceImeiInfoChangedRegistrants;
    private RegistrantList mRadioStateChangedRegistrants = new RegistrantList();

    // ***** IRadioSim RegistrantLists
    private RegistrantList[] mCardStatusChangedRegistrants;
    private RegistrantList[] mSimAppDataChangedRegistrants;
    private RegistrantList[] mSimInfoChangedRegistrants;

    // ***** IRadioNetwork RegistrantLists
    private RegistrantList[] mServiceStateChangedRegistrants;

    // ***** IRadioVoice RegistrantLists
    private RegistrantList[] mCallStateChangedRegistrants;
    private RegistrantList[] mCurrentCallsResponseRegistrants;
    private RegistrantList[] mCallIncomingRegistrants;
    private RegistrantList[] mRingbackToneRegistrants;

    public MockModemConfigBase(Context context, int numOfSim, int numOfPhone) {
        mContext = context;
        mNumOfSim =
                (numOfSim > MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT)
                        ? MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT
                        : numOfSim;
        mNumOfPhone =
                (numOfPhone > MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM)
                        ? MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM
                        : numOfPhone;
        mConfigAccess = new Object[mNumOfPhone];
        mHandler = new MockModemConfigHandler[mNumOfPhone];

        // Registrants initialization
        // IRadioModem registrants
        mDeviceIdentityChangedRegistrants = new RegistrantList[mNumOfPhone];
        mDeviceImeiInfoChangedRegistrants = new RegistrantList[mNumOfPhone];
        // IRadioSim registrants
        mCardStatusChangedRegistrants = new RegistrantList[mNumOfPhone];
        mSimAppDataChangedRegistrants = new RegistrantList[mNumOfPhone];
        mSimInfoChangedRegistrants = new RegistrantList[mNumOfPhone];
        // IRadioNetwork registrants
        mServiceStateChangedRegistrants = new RegistrantList[mNumOfPhone];
        // IRadioVoice registrants
        mCallStateChangedRegistrants = new RegistrantList[mNumOfPhone];
        mCurrentCallsResponseRegistrants = new RegistrantList[mNumOfPhone];
        mCallIncomingRegistrants = new RegistrantList[mNumOfPhone];
        mRingbackToneRegistrants = new RegistrantList[mNumOfPhone];

        // IRadioModem caches
        mImei = new String[mNumOfPhone];
        mImeiSv = new String[mNumOfPhone];
        mEsn = new String[mNumOfPhone];
        mMeid = new String[mNumOfPhone];
        mImeiType = new int[mNumOfPhone];

        // IRadioSim caches
        mCardStatus = new CardStatus[mNumOfPhone];
        mSimSlotStatus = new SimSlotStatus[mNumOfSim];
        mLogicalSimIdMap = new int[mNumOfSim];
        mFdnStatus = new int[mNumOfSim];
        mSimService = new MockSimService[mNumOfSim];
        mSimAppList = (ArrayList<SimAppData>[]) new ArrayList[mNumOfSim];

        // IRadioVoice caches
        mVoiceService = new MockVoiceService[mNumOfPhone];

        // Caches initializtion
        for (int i = 0; i < mNumOfPhone; i++) {
            if (mConfigAccess != null && mConfigAccess[i] == null) {
                mConfigAccess[i] = new Object();
            }

            if (mHandler != null && mHandler[i] == null) {
                mHandler[i] = new MockModemConfigHandler(i);
            }

            if (mDeviceIdentityChangedRegistrants != null
                    && mDeviceIdentityChangedRegistrants[i] == null) {
                mDeviceIdentityChangedRegistrants[i] = new RegistrantList();
            }

            if (mDeviceImeiInfoChangedRegistrants != null
                    && mDeviceImeiInfoChangedRegistrants[i] == null) {
                mDeviceImeiInfoChangedRegistrants[i] = new RegistrantList();
            }

            if (mCardStatusChangedRegistrants != null && mCardStatusChangedRegistrants[i] == null) {
                mCardStatusChangedRegistrants[i] = new RegistrantList();
            }

            if (mSimAppDataChangedRegistrants != null && mSimAppDataChangedRegistrants[i] == null) {
                mSimAppDataChangedRegistrants[i] = new RegistrantList();
            }

            if (mSimInfoChangedRegistrants != null && mSimInfoChangedRegistrants[i] == null) {
                mSimInfoChangedRegistrants[i] = new RegistrantList();
            }

            if (mServiceStateChangedRegistrants != null
                    && mServiceStateChangedRegistrants[i] == null) {
                mServiceStateChangedRegistrants[i] = new RegistrantList();
            }

            if (mCallStateChangedRegistrants != null && mCallStateChangedRegistrants[i] == null) {
                mCallStateChangedRegistrants[i] = new RegistrantList();
            }

            if (mCurrentCallsResponseRegistrants != null
                    && mCurrentCallsResponseRegistrants[i] == null) {
                mCurrentCallsResponseRegistrants[i] = new RegistrantList();
            }

            if (mCallIncomingRegistrants != null && mCallIncomingRegistrants[i] == null) {
                mCallIncomingRegistrants[i] = new RegistrantList();
            }

            if (mRingbackToneRegistrants != null && mRingbackToneRegistrants[i] == null) {
                mRingbackToneRegistrants[i] = new RegistrantList();
            }

            if (mImei != null && mImei[i] == null) {
                String imei;
                switch (i) {
                    case 0:
                        imei = new String(MockModemConfigInterface.DEFAULT_PHONE1_IMEI);
                        break;
                    case 1:
                        imei = new String(MockModemConfigInterface.DEFAULT_PHONE2_IMEI);
                        break;
                    case 2:
                        imei = new String(MockModemConfigInterface.DEFAULT_PHONE3_IMEI);
                        break;
                    default:
                        imei = new String(MockModemConfigInterface.DEFAULT_PHONE1_IMEI);
                        break;
                }
                mImei[i] = imei;
            }

            if (mImeiSv != null && mImeiSv[i] == null) {
                String imeisv;
                switch (i) {
                    case 0:
                        imeisv = new String(MockModemConfigInterface.DEFAULT_PHONE1_IMEISV);
                        break;
                    case 1:
                        imeisv = new String(MockModemConfigInterface.DEFAULT_PHONE2_IMEISV);
                        break;
                    case 2:
                        imeisv = new String(MockModemConfigInterface.DEFAULT_PHONE3_IMEISV);
                        break;
                    default:
                        imeisv = new String(MockModemConfigInterface.DEFAULT_PHONE1_IMEISV);
                        break;
                }
                mImeiSv[i] = imeisv;
            }

            if (mEsn != null && mEsn[i] == null) {
                String esn;
                switch (i) {
                    case 0:
                        esn = new String(MockModemConfigInterface.DEFAULT_PHONE1_ESN);
                        break;
                    case 1:
                        esn = new String(MockModemConfigInterface.DEFAULT_PHONE2_ESN);
                        break;
                    case 2:
                        esn = new String(MockModemConfigInterface.DEFAULT_PHONE3_ESN);
                        break;
                    default:
                        esn = new String(MockModemConfigInterface.DEFAULT_PHONE1_ESN);
                        break;
                }
                mEsn[i] = esn;
            }

            if (mMeid != null && mMeid[i] == null) {
                String meid;
                switch (i) {
                    case 0:
                        meid = new String(MockModemConfigInterface.DEFAULT_PHONE1_MEID);
                        break;
                    case 1:
                        meid = new String(MockModemConfigInterface.DEFAULT_PHONE2_MEID);
                        break;
                    case 2:
                        meid = new String(MockModemConfigInterface.DEFAULT_PHONE3_MEID);
                        break;
                    default:
                        meid = new String(MockModemConfigInterface.DEFAULT_PHONE1_MEID);
                        break;
                }
                mMeid[i] = meid;
            }

            if (mImeiType != null) {
                int imeiType;
                if (i == 0) {
                    imeiType = ImeiInfo.ImeiType.PRIMARY;
                } else {
                    imeiType = ImeiInfo.ImeiType.SECONDARY;
                }
                mImeiType[i] = imeiType;
            }

            if (mCardStatus != null && mCardStatus[i] == null) {
                mCardStatus[i] = new CardStatus();
            }

            if (mVoiceService != null && mVoiceService[i] == null) {
                mVoiceService[i] = new MockVoiceService(mHandler[i]);
            }
        }

        for (int i = 0; i < mNumOfSim; i++) {
            if (mSimSlotStatus != null && mSimSlotStatus[i] == null) {
                mSimSlotStatus[i] = new SimSlotStatus();
            }

            if (mLogicalSimIdMap != null) {
                mLogicalSimIdMap[i] = i;
            }

            if (mFdnStatus != null) {
                mFdnStatus[i] = 0;
            }

            if (mSimService != null && mSimService[i] == null) {
                mSimService[i] = new MockSimService(mContext, i);
            }
        }

        setDefaultConfigValue();
    }

    public static class SimInfoChangedResult {
        public static final int SIM_INFO_TYPE_MCC_MNC = 1;
        public static final int SIM_INFO_TYPE_IMSI = 2;
        public static final int SIM_INFO_TYPE_ATR = 3;

        public int mSimInfoType;
        public int mEfId;
        public String mAid;

        public SimInfoChangedResult(int type, int efid, String aid) {
            mSimInfoType = type;
            mEfId = efid;
            mAid = aid;
        }

        @Override
        public String toString() {
            return "SimInfoChangedResult:"
                    + " simInfoType="
                    + mSimInfoType
                    + " efId="
                    + mEfId
                    + " aId="
                    + mAid;
        }
    }

    public class MockModemConfigHandler extends Handler {
        private int mLogicalSlotId;

        MockModemConfigHandler(int slotId) {
            mLogicalSlotId = slotId;
        }

        // ***** Handler implementation
        @Override
        public void handleMessage(Message msg) {
            int physicalSimSlot = getSimPhysicalSlotId(mLogicalSlotId);

            synchronized (mConfigAccess[physicalSimSlot]) {
                switch (msg.what) {
                    case EVENT_SET_RADIO_POWER:
                        int state = msg.arg1;
                        if (state >= RADIO_STATE_UNAVAILABLE && state <= RADIO_STATE_ON) {
                            Log.d(
                                    mTAG,
                                    "EVENT_SET_RADIO_POWER: old("
                                            + mRadioState
                                            + "), new("
                                            + state
                                            + ")");
                            if (mRadioState != state) {
                                mRadioState = state;
                                mRadioStateChangedRegistrants.notifyRegistrants(
                                        new AsyncResult(null, mRadioState, null));
                            }
                        } else {
                            Log.e(mTAG, "EVENT_SET_RADIO_POWER: invalid state(" + state + ")");
                            mRadioStateChangedRegistrants.notifyRegistrants(null);
                        }
                        break;
                    case EVENT_CHANGE_SIM_PROFILE:
                        int simprofileid =
                                msg.getData()
                                        .getInt(
                                                "changeSimProfile",
                                                MockSimService.MOCK_SIM_PROFILE_ID_DEFAULT);
                        Log.d(mTAG, "EVENT_CHANGE_SIM_PROFILE: sim profile(" + simprofileid + ")");
                        if (loadSIMCard(physicalSimSlot, simprofileid)) {
                            if (mLogicalSlotId == DEFAULT_SLOT_ID) {
                                mSimSlotStatusChangedRegistrants.notifyRegistrants(
                                        new AsyncResult(null, mSimSlotStatus, null));
                            }
                            mCardStatusChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                    new AsyncResult(null, mCardStatus[physicalSimSlot], null));
                            mSimAppDataChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                    new AsyncResult(null, mSimAppList[physicalSimSlot], null));
                        } else {
                            Log.e(mTAG, "Load Sim card failed.");
                        }
                        break;
                    case EVENT_SERVICE_STATE_CHANGE:
                        Log.d(mTAG, "EVENT_SERVICE_STATE_CHANGE");
                        // Notify object MockNetworkService
                        mServiceStateChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;
                    case EVENT_SET_SIM_INFO:
                        int simInfoType = msg.getData().getInt("setSimInfo:type", -1);
                        String[] simInfoData = msg.getData().getStringArray("setSimInfo:data");
                        Log.d(
                                mTAG,
                                "EVENT_SET_SIM_INFO: type = "
                                        + simInfoType
                                        + " data length = "
                                        + simInfoData.length);
                        for (int i = 0; i < simInfoData.length; i++) {
                            Log.d(mTAG, "simInfoData[" + i + "] = " + simInfoData[i]);
                        }
                        SimInfoChangedResult simInfoChangeResult =
                                setSimInfo(physicalSimSlot, simInfoType, simInfoData);
                        if (simInfoChangeResult != null) {
                            switch (simInfoChangeResult.mSimInfoType) {
                                case SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC:
                                case SimInfoChangedResult.SIM_INFO_TYPE_IMSI:
                                    mSimInfoChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(null, simInfoChangeResult, null));
                                    mSimAppDataChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(
                                                    null, mSimAppList[physicalSimSlot], null));
                                    // Card status changed still needed for updating carrier config
                                    // in Telephony Framework
                                    if (mLogicalSlotId == DEFAULT_SLOT_ID) {
                                        mSimSlotStatusChangedRegistrants.notifyRegistrants(
                                                new AsyncResult(null, mSimSlotStatus, null));
                                    }
                                    mCardStatusChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(
                                                    null, mCardStatus[physicalSimSlot], null));
                                    break;
                                case SimInfoChangedResult.SIM_INFO_TYPE_ATR:
                                    if (mLogicalSlotId == DEFAULT_SLOT_ID) {
                                        mSimSlotStatusChangedRegistrants.notifyRegistrants(
                                                new AsyncResult(null, mSimSlotStatus, null));
                                    }
                                    mCardStatusChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(
                                                    null, mCardStatus[physicalSimSlot], null));
                                    break;
                            }
                        }
                        break;
                    case EVENT_CALL_STATE_CHANGE:
                        Log.d(mTAG, "EVENT_CALL_STATE_CHANGE");
                        mCallStateChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;

                    case EVENT_CURRENT_CALLS_RESPONSE:
                        Log.d(mTAG, "EVENT_CURRENT_CALLS_RESPONSE");
                        mCurrentCallsResponseRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;

                    case EVENT_CALL_INCOMING:
                        Log.d(mTAG, "EVENT_CALL_INCOMING");
                        mCallIncomingRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;
                    case EVENT_RINGBACK_TONE:
                        Log.d(mTAG, "EVENT_RINGBACK_TONE");
                        mRingbackToneRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;
                }
            }
        }
    }

    private int getSimLogicalSlotId(int physicalSlotId) {
        int logicalSimId = DEFAULT_SLOT_ID;

        synchronized (mSimMappingAccess) {
            logicalSimId = mLogicalSimIdMap[physicalSlotId];
        }

        return logicalSimId;
    }

    private int getSimPhysicalSlotId(int logicalSlotId) {
        int physicalSlotId = DEFAULT_SLOT_ID;

        synchronized (mSimMappingAccess) {
            for (int i = 0; i < mNumOfSim; i++) {
                if (mLogicalSimIdMap[i] == logicalSlotId) {
                    physicalSlotId = i;
                }
            }
        }

        return physicalSlotId;
    }

    private void setDefaultConfigValue() {
        for (int i = 0; i < mNumOfPhone; i++) {
            synchronized (mConfigAccess[i]) {
                switch (i) {
                    case 0:
                        mImei[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEI;
                        mImeiSv[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEISV;
                        mEsn[i] = MockModemConfigInterface.DEFAULT_PHONE1_ESN;
                        mMeid[i] = MockModemConfigInterface.DEFAULT_PHONE1_MEID;
                        mImeiType[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEITYPE;
                        break;
                    case 1:
                        mImei[i] = MockModemConfigInterface.DEFAULT_PHONE2_IMEI;
                        mImeiSv[i] = MockModemConfigInterface.DEFAULT_PHONE2_IMEISV;
                        mEsn[i] = MockModemConfigInterface.DEFAULT_PHONE2_ESN;
                        mMeid[i] = MockModemConfigInterface.DEFAULT_PHONE2_MEID;
                        mImeiType[i] = MockModemConfigInterface.DEFAULT_PHONE2_IMEITYPE;
                        break;
                    case 2:
                        mImei[i] = MockModemConfigInterface.DEFAULT_PHONE3_IMEI;
                        mImeiSv[i] = MockModemConfigInterface.DEFAULT_PHONE3_IMEISV;
                        mEsn[i] = MockModemConfigInterface.DEFAULT_PHONE3_ESN;
                        mMeid[i] = MockModemConfigInterface.DEFAULT_PHONE3_MEID;
                        mImeiType[i] = MockModemConfigInterface.DEFAULT_PHONE3_IMEITYPE;
                        break;
                    default:
                        mImei[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEI;
                        mImeiSv[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEISV;
                        mEsn[i] = MockModemConfigInterface.DEFAULT_PHONE1_ESN;
                        mMeid[i] = MockModemConfigInterface.DEFAULT_PHONE1_MEID;
                        mImeiType[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEITYPE;
                        break;
                }

                if (i == DEFAULT_SLOT_ID) {
                    mBasebandVersion = MockModemConfigInterface.DEFAULT_BASEBAND_VERSION;
                    mRadioState = MockModemConfigInterface.DEFAULT_RADIO_STATE;
                    mNumOfLiveModem = MockModemConfigInterface.DEFAULT_NUM_OF_LIVE_MODEM;
                    setDefaultPhoneCapability(mPhoneCapability);
                    updateSimSlotStatus();
                }
                updateCardStatus(i);
            }
        }
    }

    private void setDefaultPhoneCapability(PhoneCapability phoneCapability) {
        phoneCapability.logicalModemIds =
                new byte[MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM];
        phoneCapability.maxActiveData = MockModemConfigInterface.DEFAULT_MAX_ACTIVE_DATA;
        phoneCapability.maxActiveInternetData =
                MockModemConfigInterface.DEFAULT_MAX_ACTIVE_INTERNAL_DATA;
        phoneCapability.isInternetLingeringSupported =
                MockModemConfigInterface.DEFAULT_IS_INTERNAL_LINGERING_SUPPORTED;
        phoneCapability.logicalModemIds[0] = MockModemConfigInterface.DEFAULT_LOGICAL_MODEM1_ID;
        phoneCapability.logicalModemIds[1] = MockModemConfigInterface.DEFAULT_LOGICAL_MODEM2_ID;
        phoneCapability.logicalModemIds[2] = MockModemConfigInterface.DEFAULT_LOGICAL_MODEM3_ID;
    }

    private void updateSimSlotStatus() {
        if (mSimService == null) {
            Log.e(mTAG, "SIM service didn't be created yet.");
        }

        for (int i = 0; i < mNumOfSim; i++) {
            if (mSimService[i] == null) {
                Log.e(mTAG, "SIM service[" + i + "] didn't be created yet.");
                continue;
            }
            int portInfoListLen = mSimService[i].getNumOfSimPortInfo();
            mSimSlotStatus[i] = new SimSlotStatus();
            mSimSlotStatus[i].cardState =
                    mSimService[i].isCardPresent()
                            ? CardStatus.STATE_PRESENT
                            : CardStatus.STATE_ABSENT;
            mSimSlotStatus[i].atr = mSimService[i].getATR();
            mSimSlotStatus[i].eid = mSimService[i].getEID();
            // TODO: support multiple sim port
            SimPortInfo[] portInfoList0 = new SimPortInfo[portInfoListLen];
            portInfoList0[0] = new SimPortInfo();
            portInfoList0[0].portActive = mSimService[i].isSlotPortActive();
            portInfoList0[0].logicalSlotId = mSimService[i].getLogicalSlotId();
            portInfoList0[0].iccId = mSimService[i].getICCID();
            mSimSlotStatus[i].portInfo = portInfoList0;
        }
    }

    private void updateCardStatus(int slotId) {
        if (slotId >= 0
                && slotId < mSimService.length
                && mSimService != null
                && mSimService[slotId] != null) {
            mCardStatus[slotId] = new CardStatus();
            mCardStatus[slotId].cardState =
                    mSimService[slotId].isCardPresent()
                            ? CardStatus.STATE_PRESENT
                            : CardStatus.STATE_ABSENT;
            mCardStatus[slotId].universalPinState = mSimService[slotId].getUniversalPinState();
            mCardStatus[slotId].gsmUmtsSubscriptionAppIndex = mSimService[slotId].getGsmAppIndex();
            mCardStatus[slotId].cdmaSubscriptionAppIndex = mSimService[slotId].getCdmaAppIndex();
            mCardStatus[slotId].imsSubscriptionAppIndex = mSimService[slotId].getImsAppIndex();
            mCardStatus[slotId].applications = mSimService[slotId].getSimApp();
            mCardStatus[slotId].atr = mSimService[slotId].getATR();
            mCardStatus[slotId].iccid = mSimService[slotId].getICCID();
            mCardStatus[slotId].eid = mSimService[slotId].getEID();
            mCardStatus[slotId].slotMap = new SlotPortMapping();
            mCardStatus[slotId].slotMap.physicalSlotId = mSimService[slotId].getPhysicalSlotId();
            mCardStatus[slotId].slotMap.portId = mSimService[slotId].getSlotPortId();
            mSimAppList[slotId] = mSimService[slotId].getSimAppList();
        } else {
            Log.e(mTAG, "Invalid Sim physical id(" + slotId + ") or SIM card didn't be created.");
        }
    }

    private boolean loadSIMCard(int slotId, int simProfileId) {
        boolean result = false;
        if (slotId >= 0
                && slotId < mSimService.length
                && mSimService != null
                && mSimService[slotId] != null) {
            result = mSimService[slotId].loadSimCard(simProfileId);
            if (slotId == DEFAULT_SLOT_ID) {
                updateSimSlotStatus();
            }
            updateCardStatus(slotId);
        }
        return result;
    }

    private String generateRandomIccid(String baseIccid) {
        String newIccid;
        Random rnd = new Random();
        StringBuilder randomNum = new StringBuilder();

        // Generate random 12-digit account id
        for (int i = 0; i < 12; i++) {
            randomNum.append(rnd.nextInt(10));
        }

        Log.d(mTAG, "Random Num = " + randomNum.toString());

        // TODO: regenerate checksum
        // Simply modify account id from base Iccid
        newIccid =
                baseIccid.substring(0, 7)
                        + randomNum.toString()
                        + baseIccid.substring(baseIccid.length() - 1);

        Log.d(mTAG, "Generate new Iccid = " + newIccid);

        return newIccid;
    }

    private SimInfoChangedResult setSimInfo(int slotId, int simInfoType, String[] simInfoData) {
        SimInfoChangedResult result = null;

        if (simInfoData == null) {
            Log.e(mTAG, "simInfoData == null");
            return result;
        }

        switch (simInfoType) {
            case SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC:
                if (simInfoData.length == 2 && simInfoData[0] != null && simInfoData[1] != null) {
                    String msin = mSimService[slotId].getMsin();

                    // Adjust msin length to make sure IMSI length is valid.
                    if (simInfoData[1].length() == 3 && msin.length() == 10) {
                        msin = msin.substring(0, msin.length() - 1);
                        Log.d(mTAG, "Modify msin = " + msin);
                    }
                    mSimService[slotId].setImsi(simInfoData[0], simInfoData[1], msin);

                    // Auto-generate a new Iccid to change carrier config id in Android Framework
                    mSimService[slotId].setICCID(
                            generateRandomIccid(mSimService[slotId].getICCID()));
                    updateSimSlotStatus();
                    updateCardStatus(slotId);

                    result =
                            new SimInfoChangedResult(
                                    simInfoType, EF_ICCID, mSimService[slotId].getActiveSimAppId());
                }
                break;
            case SimInfoChangedResult.SIM_INFO_TYPE_IMSI:
                if (simInfoData.length == 3
                        && simInfoData[0] != null
                        && simInfoData[1] != null
                        && simInfoData[2] != null) {
                    mSimService[slotId].setImsi(simInfoData[0], simInfoData[1], simInfoData[2]);

                    // Auto-generate a new Iccid to change carrier config id in Android Framework
                    mSimService[slotId].setICCID(
                            generateRandomIccid(mSimService[slotId].getICCID()));
                    updateSimSlotStatus();
                    updateCardStatus(slotId);

                    result =
                            new SimInfoChangedResult(
                                    simInfoType, EF_ICCID, mSimService[slotId].getActiveSimAppId());
                }
                break;
            case SimInfoChangedResult.SIM_INFO_TYPE_ATR:
                if (simInfoData[0] != null) {
                    mSimService[slotId].setATR(simInfoData[0]);
                    updateSimSlotStatus();
                    updateCardStatus(slotId);
                    result = new SimInfoChangedResult(simInfoType, 0, "");
                }
                break;
            default:
                Log.e(mTAG, "Not support Sim info type(" + simInfoType + ") to modify");
                break;
        }

        return result;
    }

    private void notifyDeviceIdentityChangedRegistrants(int logicalSlotId) {
        String[] deviceIdentity = new String[4];
        int physicalSlotId = getSimLogicalSlotId(logicalSlotId);

        synchronized (mConfigAccess[physicalSlotId]) {
            deviceIdentity[0] = mImei[physicalSlotId];
            deviceIdentity[1] = mImeiSv[physicalSlotId];
            deviceIdentity[2] = mEsn[physicalSlotId];
            deviceIdentity[3] = mMeid[physicalSlotId];
        }
        AsyncResult ar = new AsyncResult(null, deviceIdentity, null);
        mDeviceIdentityChangedRegistrants[logicalSlotId].notifyRegistrants(ar);
    }

    private void notifyDeviceImeiTypeChangedRegistrants(int logicalSlotId) {
        int physicalSlotId = getSimLogicalSlotId(logicalSlotId);
        android.hardware.radio.modem.ImeiInfo imeiInfo =
                new android.hardware.radio.modem.ImeiInfo();
        synchronized (mConfigAccess[physicalSlotId]) {
            imeiInfo.type = mImeiType[physicalSlotId];
            imeiInfo.imei = mImei[physicalSlotId];
            imeiInfo.svn = mImeiSv[physicalSlotId];
        }
        AsyncResult ar = new AsyncResult(null, imeiInfo, null);
        mDeviceImeiInfoChangedRegistrants[logicalSlotId].notifyRegistrants(ar);
    }

    // ***** MockModemConfigInterface implementation
    @Override
    public void destroy() {
        // Mock Services destroy
        for (int i = 0; i < mNumOfPhone; i++) {
            // IRadioVoice
            if (mVoiceService != null && mVoiceService[i] != null) {
                mVoiceService[i].destroy();
            }
        }
    }

    @Override
    public Handler getMockModemConfigHandler(int logicalSlotId) {
        return mHandler[logicalSlotId];
    }

    @Override
    public void notifyAllRegistrantNotifications() {
        Log.d(mTAG, "notifyAllRegistrantNotifications");

        // IRadioConfig
        mNumOfLiveModemChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mNumOfLiveModem, null));
        mPhoneCapabilityChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mPhoneCapability, null));
        mSimSlotStatusChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mSimSlotStatus, null));

        // IRadioModem
        mBasebandVersionChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mBasebandVersion, null));
        mRadioStateChangedRegistrants.notifyRegistrants(new AsyncResult(null, mRadioState, null));

        for (int i = 0; i < mNumOfPhone; i++) {
            int physicalSlotId = getSimPhysicalSlotId(i);

            synchronized (mConfigAccess[physicalSlotId]) {
                // IRadioModem
                notifyDeviceIdentityChangedRegistrants(i);
                notifyDeviceImeiTypeChangedRegistrants(i);

                // IRadioSim
                mCardStatusChangedRegistrants[i].notifyRegistrants(
                        new AsyncResult(null, mCardStatus[physicalSlotId], null));
                mSimAppDataChangedRegistrants[i].notifyRegistrants(
                        new AsyncResult(null, mSimAppList[physicalSlotId], null));
            }
        }
    }

    // ***** IRadioConfig notification implementation
    @Override
    public void registerForNumOfLiveModemChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mNumOfLiveModemChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForNumOfLiveModemChanged(int logicalSlotId, Handler h) {
        mNumOfLiveModemChangedRegistrants.remove(h);
    }

    @Override
    public void registerForPhoneCapabilityChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mPhoneCapabilityChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForPhoneCapabilityChanged(int logicalSlotId, Handler h) {
        mPhoneCapabilityChangedRegistrants.remove(h);
    }

    @Override
    public void registerForSimSlotStatusChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mSimSlotStatusChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimSlotStatusChanged(int logicalSlotId, Handler h) {
        mSimSlotStatusChangedRegistrants.remove(h);
    }

    // ***** IRadioModem notification implementation
    @Override
    public void registerForBasebandVersionChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mBasebandVersionChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForBasebandVersionChanged(int logicalSlotId, Handler h) {
        mBasebandVersionChangedRegistrants.remove(h);
    }

    @Override
    public void registerForDeviceIdentityChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mDeviceIdentityChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void registerForDeviceImeiInfoChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mDeviceImeiInfoChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForDeviceIdentityChanged(int logicalSlotId, Handler h) {
        mDeviceIdentityChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForRadioStateChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mRadioStateChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForRadioStateChanged(int logicalSlotId, Handler h) {
        mRadioStateChangedRegistrants.remove(h);
    }

    // ***** IRadioSim notification implementation
    @Override
    public void registerForCardStatusChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mCardStatusChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCardStatusChanged(int logicalSlotId, Handler h) {
        mCardStatusChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForSimAppDataChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mSimAppDataChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimAppDataChanged(int logicalSlotId, Handler h) {
        mSimAppDataChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForSimInfoChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mSimInfoChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimInfoChanged(int logicalSlotId, Handler h) {
        mSimInfoChangedRegistrants[logicalSlotId].remove(h);
    }

    // ***** IRadioNetwork notification implementation
    @Override
    public void registerForServiceStateChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mServiceStateChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForServiceStateChanged(int logicalSlotId, Handler h) {
        mServiceStateChangedRegistrants[logicalSlotId].remove(h);
    }

    // ***** IRadioVoice notification implementation
    @Override
    public void registerForCallStateChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mCallStateChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCallStateChanged(int logicalSlotId, Handler h) {
        mCallStateChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForCurrentCallsResponse(
            int logicalSlotId, Handler h, int what, Object obj) {
        mCurrentCallsResponseRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCurrentCallsResponse(int logicalSlotId, Handler h) {
        mCurrentCallsResponseRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForCallIncoming(int logicalSlotId, Handler h, int what, Object obj) {
        mCallIncomingRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCallIncoming(int logicalSlotId, Handler h) {
        mCallIncomingRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerRingbackTone(int logicalSlotId, Handler h, int what, Object obj) {
        mRingbackToneRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterRingbackTone(int logicalSlotId, Handler h) {
        mRingbackToneRegistrants[logicalSlotId].remove(h);
    }

    // ***** IRadioConfig set APIs implementation

    // ***** IRadioModem set APIs implementation
    @Override
    public void setRadioState(int logicalSlotId, int state, String client) {
        Log.d(mTAG, "setRadioState[" + logicalSlotId + "] (" + state + ") from " + client);

        Message msg = mHandler[logicalSlotId].obtainMessage(EVENT_SET_RADIO_POWER);
        msg.arg1 = state;
        mHandler[logicalSlotId].sendMessage(msg);
    }

    // ***** IRadioSim set APIs implementation

    // ***** IRadioNetwork set APIs implementation

    // ***** IRadioVoice set APIs implementation
    @Override
    public boolean getCurrentCalls(int logicalSlotId, String client) {
        Log.d(mTAG, "getCurrentCalls[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].getCurrentCalls();
    }

    @Override
    public boolean dialVoiceCall(
            int logicalSlotId, String address, int clir, UusInfo[] uusInfo, String client) {
        return dialVoiceCall(logicalSlotId, address, clir, uusInfo, mCallControlInfo, client);
    }

    @Override
    public boolean dialVoiceCall(
            int logicalSlotId,
            String address,
            int clir,
            UusInfo[] uusInfo,
            MockCallControlInfo callControlInfo,
            String client) {
        Log.d(
                mTAG,
                "dialVoiceCall["
                        + logicalSlotId
                        + "]: address = "
                        + address
                        + " clir = "
                        + clir
                        + " from: "
                        + client);
        if (uusInfo == null) {
            Log.e(mTAG, "ussInfo == null!");
            return false;
        }

        int callType = CALL_TYPE_VOICE;
        return mVoiceService[logicalSlotId].dialVoiceCall(
                address, clir, uusInfo, callType, callControlInfo);
    }

    @Override
    public boolean dialEccVoiceCall(
            int logicalSlotId,
            String address,
            int categories,
            String[] urns,
            int routing,
            String client) {
        return dialEccVoiceCall(
                logicalSlotId, address, categories, urns, routing, mCallControlInfo, client);
    }

    @Override
    public boolean dialEccVoiceCall(
            int logicalSlotId,
            String address,
            int categories,
            String[] urns,
            int routing,
            MockCallControlInfo callControlInfo,
            String client) {
        Log.d(
                mTAG,
                "dialEccVoiceCall["
                        + logicalSlotId
                        + "]: address = "
                        + address
                        + " categories = "
                        + categories
                        + " from: "
                        + client);

        return mVoiceService[logicalSlotId].dialEccVoiceCall(
                address, categories, urns, routing, CALL_TYPE_EMERGENCY, callControlInfo);
    }

    @Override
    public boolean hangupVoiceCall(int logicalSlotId, int index, String client) {
        Log.d(
                mTAG,
                "hangupVoiceCall[" + logicalSlotId + "]: index = " + index + " from: " + client);
        return mVoiceService[logicalSlotId].hangupVoiceCall(index);
    }

    @Override
    public boolean rejectVoiceCall(int logicalSlotId, String client) {
        Log.d(mTAG, "rejectVoiceCall[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].rejectVoiceCall();
    }

    @Override
    public boolean acceptVoiceCall(int logicalSlotId, String client) {
        Log.d(mTAG, "acceptVoiceCall[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].acceptVoiceCall();
    }

    @Override
    public LastCallFailCauseInfo getLastCallFailCause(int logicalSlotId, String client) {
        Log.d(mTAG, "getLastCallFailCause[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].getLastCallEndInfo();
    }

    @Override
    public void setLastCallFailCause(
            int logicalSlotId, @Annotation.DisconnectCauses int cause, String client) {
        Log.d(
                mTAG,
                "setLastCallFailCause["
                        + logicalSlotId
                        + "]: cause = "
                        + cause
                        + "  from: "
                        + client);
        mVoiceService[logicalSlotId].setLastCallFailCause(cause);
    }

    @Override
    public void clearAllCalls(
            int logicalSlotId, @Annotation.DisconnectCauses int cause, String client) {
        Log.d(mTAG, "clearAllCalls[" + logicalSlotId + "]: cause = " + cause + "  from: " + client);
        mVoiceService[logicalSlotId].clearAllCalls(cause);
    }

    @Override
    public boolean getVoiceMuteMode(int logicalSlotId, String client) {
        Log.d(mTAG, "getVoiceMuteMode[" + logicalSlotId + "] from " + client);
        return mVoiceService[logicalSlotId].getMuteMode();
    }

    @Override
    public boolean setVoiceMuteMode(int logicalSlotId, boolean muteMode, String client) {
        Log.d(
                mTAG,
                "setVoiceMuteMode["
                        + logicalSlotId
                        + "]: muteMode = "
                        + muteMode
                        + " from: "
                        + client);
        mVoiceService[logicalSlotId].setMuteMode(muteMode);
        return true;
    }

    // ***** IRadioData set APIs implementation

    // ***** IRadioMessaging set APIs implementation

    // ***** Utility methods implementation
    @Override
    public boolean isSimCardPresent(int logicalSlotId, String client) {
        Log.d(mTAG, "isSimCardPresent[" + logicalSlotId + "] from: " + client);

        int physicalSlotId = getSimPhysicalSlotId(logicalSlotId);
        boolean isPresent = false;
        if (physicalSlotId == DEFAULT_SLOT_ID) {
            synchronized (mConfigAccess[physicalSlotId]) {
                isPresent =
                        (mCardStatus[physicalSlotId].cardState == CardStatus.STATE_PRESENT)
                                ? true
                                : false;
            }
        } else if (physicalSlotId == ESIM_SLOT_ID) {
            synchronized (mConfigAccess[physicalSlotId]) {
                isPresent = (mCardStatus[physicalSlotId].iccid.trim().length() > 0) ? true : false;
            }
        }
        return isPresent;
    }

    @Override
    public boolean changeSimProfile(int logicalSlotId, int simprofileid, String client) {
        boolean result = true;
        Log.d(
                mTAG,
                "changeSimProfile["
                        + logicalSlotId
                        + "]: profile id("
                        + simprofileid
                        + ") from: "
                        + client);

        if (simprofileid >= MOCK_SIM_PROFILE_ID_DEFAULT && simprofileid < MOCK_SIM_PROFILE_ID_MAX) {
            Message msg = mHandler[logicalSlotId].obtainMessage(EVENT_CHANGE_SIM_PROFILE);
            msg.getData().putInt("changeSimProfile", simprofileid);
            mHandler[logicalSlotId].sendMessage(msg);
        } else {
            result = false;
        }

        return result;
    }

    @Override
    public void setSimInfo(int logicalSlotId, int type, String[] data, String client) {
        Log.d(mTAG, "setSimInfo[" + logicalSlotId + "]: type(" + type + ") from: " + client);

        Message msg = mHandler[logicalSlotId].obtainMessage(EVENT_SET_SIM_INFO);
        Bundle bundle = msg.getData();
        bundle.putInt("setSimInfo:type", type);
        bundle.putStringArray("setSimInfo:data", data);
        mHandler[logicalSlotId].sendMessage(msg);
    }

    @Override
    public String getSimInfo(int logicalSlotId, int type, String client) {
        Log.d(mTAG, "getSimInfo[" + logicalSlotId + "]: type(" + type + ") from: " + client);

        String result = "";
        int physicalSlotId = getSimPhysicalSlotId(logicalSlotId);

        synchronized (mConfigAccess[physicalSlotId]) {
            switch (type) {
                case SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC:
                    result = mSimService[physicalSlotId].getMccMnc();
                    break;
                case SimInfoChangedResult.SIM_INFO_TYPE_IMSI:
                    result = mSimService[physicalSlotId].getImsi();
                    break;
                case SimInfoChangedResult.SIM_INFO_TYPE_ATR:
                    result = mCardStatus[physicalSlotId].atr;
                    break;
                default:
                    Log.e(mTAG, "Not support this type of SIM info.");
                    break;
            }
        }

        return result;
    }

    @Override
    public boolean setCallControlInfo(
            int logicalSlotId, MockCallControlInfo callControlInfo, String client) {
        Log.d(mTAG, "setCallControlInfo[" + logicalSlotId + " from: " + client);
        mCallControlInfo = callControlInfo;

        return true;
    }

    @Override
    public MockCallControlInfo getCallControlInfo(int logicalSlotId, String client) {
        Log.d(mTAG, "getCallControlInfo[" + logicalSlotId + " from: " + client);
        return mCallControlInfo;
    }

    @Override
    public boolean triggerIncomingVoiceCall(
            int logicalSlotId,
            String address,
            UusInfo[] uusInfo,
            CdmaSignalInfoRecord cdmaSignalInfoRecord,
            MockCallControlInfo callControlInfo,
            String client) {
        Log.d(
                mTAG,
                "triggerIncomingVoiceCall["
                        + logicalSlotId
                        + "]: address = "
                        + address
                        + " from: "
                        + client);
        if (uusInfo == null) {
            Log.e(mTAG, "ussInfo == null!");
            return false;
        }

        int callType = CALL_TYPE_VOICE;
        return mVoiceService[logicalSlotId].triggerIncomingVoiceCall(
                address, uusInfo, callType, cdmaSignalInfoRecord, callControlInfo);
    }

    @Override
    public int getNumberOfCalls(int logicalSlotId, String client) {
        Log.d(mTAG, "getNumberOfCalls[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].getNumberOfCalls();
    }
}
