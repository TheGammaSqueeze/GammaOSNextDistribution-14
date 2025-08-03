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

import android.content.Context;
import android.hardware.radio.sim.AppStatus;
import android.hardware.radio.sim.PinState;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

public class MockSimService {
    /* Support SIM card identify */
    public static final int MOCK_SIM_PROFILE_ID_DEFAULT = 0; // SIM Absent
    public static final int MOCK_SIM_PROFILE_ID_TWN_CHT = 1;
    public static final int MOCK_SIM_PROFILE_ID_TWN_FET = 2;
    public static final int MOCK_SIM_PROFILE_ID_US_FI = 3;
    public static final int MOCK_SIM_PROFILE_ID_MAX = 4;

    /* Type of SIM IO command */
    public static final int COMMAND_READ_BINARY = 0xb0;
    public static final int COMMAND_GET_RESPONSE = 0xc0;

    /* EF Id definition */
    public static final int EF_ICCID = 0x2FE2;
    public static final int EF_IMSI = 0x6F07;
    public static final int EF_GID1 = 0x6F3E;

    /* SIM profile XML TAG definition */
    private static final String MOCK_SIM_TAG = "MockSim";
    private static final String MOCK_SIM_PROFILE_TAG = "MockSimProfile";
    private static final String MOCK_PIN_PROFILE_TAG = "PinProfile";
    private static final String MOCK_PIN1_STATE_TAG = "Pin1State";
    private static final String MOCK_PIN2_STATE_TAG = "Pin2State";
    private static final String MOCK_FACILITY_LOCK_TAG = "FacilityLock";
    private static final String MOCK_FACILITY_LOCK_FD_TAG = "FD";
    private static final String MOCK_FACILITY_LOCK_SC_TAG = "SC";
    private static final String MOCK_MF_TAG = "MF";
    private static final String MOCK_EF_TAG = "EF";
    private static final String MOCK_EF_DIR_TAG = "EFDIR";
    private static final String MOCK_ADF_TAG = "ADF";

    /* Support SIM slot */
    private static final int MOCK_SIM_SLOT_1 = 0;
    private static final int MOCK_SIM_SLOT_2 = 1;
    private static final int MOCK_SIM_SLOT_3 = 2;
    public static final int MOCK_SIM_SLOT_MIN = 1;
    public static final int MOCK_SIM_SLOT_MAX = 3;

    /* Default value definition */
    private static final int MOCK_SIM_DEFAULT_SLOTID = MOCK_SIM_SLOT_1;
    private static final int DEFAULT_NUM_OF_SIM_APP = 0;
    private static final int DEFAULT_GSM_APP_IDX = -1;
    private static final int DEFAULT_CDMA_APP_IDX = -1;
    private static final int DEFAULT_IMS_APP_IDX = -1;
    private static final int DEFAULT_NUM_OF_SIM_PORT_INFO = 1;
    // SIM1 slot status - physical SIM
    private static final int DEFAULT_SIM1_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM1_CARD_PRESENT = false;
    private static final String DEFAULT_SIM1_ATR = "";
    private static final String DEFAULT_SIM1_EID = "";
    private static final boolean DEFAULT_SIM1_PORT_ACTIVE = true;
    private static final int DEFAULT_SIM1_PORT_ID = 0;
    private static final int DEFAULT_SIM1_LOGICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM1_UNIVERSAL_PIN_STATE = PinState.UNKNOWN;
    // SIM2 slot status - eSIM
    private static final int DEFAULT_SIM2_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM2_CARD_PRESENT = true;
    private static final String DEFAULT_SIM2_ATR =
            "3B9F97C00A3FC6828031E073FE211F65D002341512810F51";
    private static final String DEFAULT_SIM2_EID = "89033023426200000000005430099507";
    private static final boolean DEFAULT_SIM2_PORT_ACTIVE = true;
    private static final int DEFAULT_SIM2_PORT_ID = 0;
    private static final int DEFAULT_SIM2_LOGICAL_SLOT_ID = 1;
    private static final int DEFAULT_SIM2_UNIVERSAL_PIN_STATE = PinState.DISABLED;
    // SIM3 slot status - eSIM
    private static final int DEFAULT_SIM3_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM3_CARD_PRESENT = true;
    private static final String DEFAULT_SIM3_ATR =
            "3B9F97C00AB1FE453FC6838031E073FE211F65D002341569810F21";
    private static final String DEFAULT_SIM3_EID = "89033023427100000000007980324395";
    private static final boolean DEFAULT_SIM3_PORT_ACTIVE = false;
    private static final int DEFAULT_SIM3_PORT_ID = 0;
    private static final int DEFAULT_SIM3_LOGICAL_SLOT_ID = -1;
    private static final int DEFAULT_SIM3_UNIVERSAL_PIN_STATE = PinState.DISABLED;

    private String mTag = "MockSimService";
    private Context mContext;

    // SIM Slot status
    private int mPhysicalSlotId;
    private int mLogicalSlotId;
    private int mSlotPortId;
    private boolean mIsSlotPortActive;
    private boolean mIsCardPresent;

    /* SIM profile info */
    private SimProfileInfo[] mSimProfileInfoList;

    // SIM card data
    private int mSimProfileId;
    private String mEID;
    private String mATR;
    private int mUniversalPinState;

    private AppStatus[] mSimApp;
    private ArrayList<SimAppData> mSimAppList;

    public class SimAppData {
        private static final int EF_INFO_DATA = 0;
        private static final int EF_BINARY_DATA = 1;

        private int mSimAppId;
        private String mAid;
        private boolean mIsCurrentActive;
        private String mPath;
        private int mFdnStatus;
        private int mPin1State;
        private String mImsi;
        private String mMcc;
        private String mMnc;
        private String mMsin;
        private String[] mIccid;
        private String[] mGid1;

        private void initSimAppData(int simappid, String aid, String path, boolean status) {
            mSimAppId = simappid;
            mAid = aid;
            mIsCurrentActive = status;
            mPath = path;
            mIccid = new String[2];
            mGid1 = new String[2];
        }

        public SimAppData(int simappid, String aid, String path) {
            initSimAppData(simappid, aid, path, false);
        }

        public SimAppData(int simappid, String aid, String path, boolean status) {
            initSimAppData(simappid, aid, path, status);
        }

        public int getSimAppId() {
            return mSimAppId;
        }

        public String getAid() {
            return mAid;
        }

        public boolean isCurrentActive() {
            return mIsCurrentActive;
        }

        public String getPath() {
            return mPath;
        }

        public int getFdnStatus() {
            return mFdnStatus;
        }

        public void setFdnStatus(int status) {
            mFdnStatus = status;
        }

        public int getPin1State() {
            return mPin1State;
        }

        public void setPin1State(int state) {
            mPin1State = state;
        }

        public String getImsi() {
            return mMcc + mMnc + mMsin;
        }

        public void setImsi(String mcc, String mnc, String msin) {
            setMcc(mcc);
            setMnc(mnc);
            setMsin(msin);
        }

        public String getMcc() {
            return mMcc;
        }

        public void setMcc(String mcc) {
            mMcc = mcc;
        }

        public String getMnc() {
            return mMnc;
        }

        public void setMnc(String mnc) {
            mMnc = mnc;
        }

        public String getMsin() {
            return mMsin;
        }

        public void setMsin(String msin) {
            mMsin = msin;
        }

        public String getIccidInfo() {
            return mIccid[EF_INFO_DATA];
        }

        public void setIccidInfo(String info) {
            mIccid[EF_INFO_DATA] = info;
        }

        public String getIccid() {
            return mIccid[EF_BINARY_DATA];
        }

        public void setIccid(String iccid) {
            mIccid[EF_BINARY_DATA] = iccid;
        }

        public String getGid1Info() {
            return mGid1[EF_INFO_DATA];
        }

        public void setGid1Info(String info) {
            mGid1[EF_INFO_DATA] = info;
        }

        public String getGid1() {
            return mGid1[EF_BINARY_DATA];
        }

        public void setGid1(String gid1) {
            mGid1[EF_BINARY_DATA] = gid1;
        }
    }

    public class SimProfileInfo {
        private int mSimProfileId;
        private int mNumOfSimApp;
        private int mGsmAppIndex;
        private int mCdmaAppIndex;
        private int mImsAppIndex;
        private String mXmlFile;

        public SimProfileInfo(int profileid) {
            mSimProfileId = profileid;
            mNumOfSimApp = DEFAULT_NUM_OF_SIM_APP;
            mGsmAppIndex = DEFAULT_GSM_APP_IDX;
            mCdmaAppIndex = DEFAULT_CDMA_APP_IDX;
            mImsAppIndex = DEFAULT_IMS_APP_IDX;
            mXmlFile = "";
        }

        public int getNumOfSimApp() {
            return mNumOfSimApp;
        }

        public int getGsmAppIndex() {
            return mGsmAppIndex;
        }

        public int getCdmaAppIndex() {
            return mCdmaAppIndex;
        }

        public int getImsAppIndex() {
            return mImsAppIndex;
        }

        public String getXmlFile() {
            return mXmlFile;
        }

        public void setNumOfSimApp(int number) {
            mNumOfSimApp = number;
        }

        public void setGsmAppIndex(int index) {
            mGsmAppIndex = index;
        }

        public void setCdmaAppIndex(int index) {
            mCdmaAppIndex = index;
        }

        public void setImsAppIndex(int index) {
            mImsAppIndex = index;
        }

        public void setXmlFile(String file) {
            mXmlFile = file;
        }
    }

    public MockSimService(Context context, int slotId) {
        mContext = context;
        int simprofile = DEFAULT_SIM1_PROFILE_ID;

        if (slotId >= MOCK_SIM_SLOT_MAX) {
            Log.e(
                    mTag,
                    "Invalid slot id("
                            + slotId
                            + "). Using default slot id("
                            + MOCK_SIM_DEFAULT_SLOTID
                            + ").");
            slotId = MOCK_SIM_DEFAULT_SLOTID;
        }

        // Init default SIM profile id
        mTag = mTag + "-" + slotId;
        switch (slotId) {
            case MOCK_SIM_SLOT_1:
                simprofile = DEFAULT_SIM1_PROFILE_ID;
                break;
            case MOCK_SIM_SLOT_2:
                simprofile = DEFAULT_SIM2_PROFILE_ID;
                break;
            case MOCK_SIM_SLOT_3:
                simprofile = DEFAULT_SIM3_PROFILE_ID;
                break;
        }

        // Initial support SIM profile list
        mSimProfileInfoList = new SimProfileInfo[MOCK_SIM_PROFILE_ID_MAX];
        for (int idx = 0; idx < MOCK_SIM_PROFILE_ID_MAX; idx++) {
            Log.d(mTag, "Create sim profile id = " + idx);
            mSimProfileInfoList[idx] = new SimProfileInfo(idx);
            switch (idx) {
                case MOCK_SIM_PROFILE_ID_TWN_CHT:
                    mSimProfileInfoList[idx].setXmlFile("mock_sim_tw_cht.xml");
                    break;
                case MOCK_SIM_PROFILE_ID_TWN_FET:
                    mSimProfileInfoList[idx].setXmlFile("mock_sim_tw_fet.xml");
                    break;
                case MOCK_SIM_PROFILE_ID_US_FI:
                    mSimProfileInfoList[idx].setXmlFile("mock_sim_us_fi.xml");
                    break;
                default:
                    break;
            }
        }

        // Initiate SIM card with default profile
        initMockSimCard(slotId, simprofile);
    }

    private void initMockSimCard(int slotId, int simProfileId) {
        if (slotId > MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT) {
            Log.e(
                    mTag,
                    "Physical slot id("
                            + slotId
                            + ") is invalid. Using default slot id("
                            + MOCK_SIM_DEFAULT_SLOTID
                            + ").");
            mPhysicalSlotId = MOCK_SIM_DEFAULT_SLOTID;
        } else {
            mPhysicalSlotId = slotId;
        }
        if (simProfileId >= 0 && simProfileId < MOCK_SIM_PROFILE_ID_MAX) {
            mSimProfileId = simProfileId;
            Log.i(
                    mTag,
                    "Load SIM profile ID: "
                            + mSimProfileId
                            + " into physical slot["
                            + mPhysicalSlotId
                            + "]");
        } else {
            mSimProfileId = MOCK_SIM_PROFILE_ID_DEFAULT;
            Log.e(
                    mTag,
                    "SIM Absent on physical slot["
                            + mPhysicalSlotId
                            + "]. Not support SIM card ID: "
                            + mSimProfileId);
        }

        // Initiate slot status
        initMockSimSlot();

        // Load SIM profile data
        loadMockSimCard();
    }

    private void initMockSimSlot() {
        switch (mPhysicalSlotId) {
            case MOCK_SIM_SLOT_1:
                mLogicalSlotId = DEFAULT_SIM1_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM1_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM1_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM1_CARD_PRESENT;
                break;
            case MOCK_SIM_SLOT_2:
                mLogicalSlotId = DEFAULT_SIM2_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM2_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM2_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM2_CARD_PRESENT;
                break;
            case MOCK_SIM_SLOT_3:
                mLogicalSlotId = DEFAULT_SIM3_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM3_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM3_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM3_CARD_PRESENT;
                break;
        }
    }

    private int convertMockSimPinState(String pinstate) {
        int mocksim_pinstate = PinState.UNKNOWN;
        switch (pinstate) {
            case "PINSTATE_UNKNOWN":
                mocksim_pinstate = PinState.UNKNOWN;
                break;
            case "PINSTATE_ENABLED_NOT_VERIFIED":
                mocksim_pinstate = PinState.ENABLED_NOT_VERIFIED;
                break;
            case "PINSTATE_ENABLED_VERIFIED":
                mocksim_pinstate = PinState.ENABLED_VERIFIED;
                break;
            case "PINSTATE_DISABLED":
                mocksim_pinstate = PinState.DISABLED;
                break;
            case "PINSTATE_ENABLED_BLOCKED":
                mocksim_pinstate = PinState.ENABLED_BLOCKED;
                break;
            case "PINSTATE_ENABLED_PERM_BLOCKED":
                mocksim_pinstate = PinState.ENABLED_PERM_BLOCKED;
                break;
        }

        return mocksim_pinstate;
    }

    private int convertMockSimAppType(String apptype) {
        int mocksim_apptype = AppStatus.APP_TYPE_UNKNOWN;
        switch (apptype) {
            case "APPTYPE_UNKNOWN":
                mocksim_apptype = AppStatus.APP_TYPE_UNKNOWN;
                break;
            case "APPTYPE_SIM":
                mocksim_apptype = AppStatus.APP_TYPE_SIM;
                break;
            case "APPTYPE_USIM":
                mocksim_apptype = AppStatus.APP_TYPE_USIM;
                break;
            case "APPTYPE_RUIM":
                mocksim_apptype = AppStatus.APP_TYPE_RUIM;
                break;
            case "APPTYPE_CSIM":
                mocksim_apptype = AppStatus.APP_TYPE_CSIM;
                break;
            case "APPTYPE_ISIM":
                mocksim_apptype = AppStatus.APP_TYPE_ISIM;
                break;
        }

        return mocksim_apptype;
    }

    private int convertMockSimAppState(String appstate) {
        int mocksim_appstate = AppStatus.APP_STATE_UNKNOWN;
        switch (appstate) {
            case "APPSTATE_UNKNOWN":
                mocksim_appstate = AppStatus.APP_STATE_UNKNOWN;
                break;
            case "APPSTATE_DETECTED":
                mocksim_appstate = AppStatus.APP_STATE_DETECTED;
                break;
            case "APPSTATE_PIN":
                mocksim_appstate = AppStatus.APP_STATE_PIN;
                break;
            case "APPSTATE_PUK":
                mocksim_appstate = AppStatus.APP_STATE_PUK;
                break;
            case "APPSTATE_SUBSCRIPTION_PERSO":
                mocksim_appstate = AppStatus.APP_STATE_SUBSCRIPTION_PERSO;
                break;
            case "APPSTATE_READY":
                mocksim_appstate = AppStatus.APP_STATE_READY;
                break;
        }
        return mocksim_appstate;
    }

    private int convertMockSimFacilityLock(String lock) {
        int facilitylock = 0;
        switch (lock) {
            case "LOCK_ENABLED":
                facilitylock = 1;
                break;
            case "LOCK_DISABLED":
                facilitylock = 0;
                break;
        }
        return facilitylock;
    }

    private int getSimAppDataIndexByAid(String aid) {
        int idx;
        for (idx = 0; idx < mSimAppList.size(); idx++) {
            if (aid.equals(mSimAppList.get(idx).getAid())) {
                break;
            }
        }
        return idx;
    }

    private String[] extractImsi(String imsi, int mncDigit) {
        String[] result = null;

        Log.d(mTag, "IMSI = " + imsi + ", mnc-digit = " + mncDigit);

        if (imsi.length() > 15 && imsi.length() < 5) {
            Log.d(mTag, "Invalid IMSI length.");
            return result;
        }

        if (mncDigit != 2 && mncDigit != 3) {
            Log.d(mTag, "Invalid mnc length.");
            return result;
        }

        result = new String[3];
        result[0] = imsi.substring(0, 3); // MCC
        result[1] = imsi.substring(3, 3 + mncDigit); // MNC
        result[2] = imsi.substring(3 + mncDigit, imsi.length()); // MSIN

        Log.d(mTag, "MCC = " + result[0] + " MNC = " + result[1] + " MSIN = " + result[2]);

        return result;
    }

    private boolean storeEfData(
            String aid, String name, String id, String command, String[] value) {
        boolean result = true;

        if (value == null) {
            Log.e(mTag, "Invalid value of EF field - " + name + "(" + id + ")");
            return false;
        }

        switch (name) {
            case "EF_IMSI":
                if (value.length == 3
                        && value[0] != null
                        && value[0].length() == 3
                        && value[1] != null
                        && (value[1].length() == 2 || value[1].length() == 3)
                        && value[2] != null
                        && value[2].length() > 0
                        && (value[0].length() + value[1].length() + value[2].length() <= 15)) {
                    mSimAppList
                            .get(getSimAppDataIndexByAid(aid))
                            .setImsi(value[0], value[1], value[2]);
                } else {
                    result = false;
                    Log.e(mTag, "Invalid value for EF field - " + name + "(" + id + ")");
                }
                break;
            case "EF_ICCID":
                if (command.length() > 2
                        && Integer.parseInt(command.substring(2), 16) == COMMAND_READ_BINARY) {
                    mSimAppList.get(getSimAppDataIndexByAid(aid)).setIccid(value[0]);
                } else if (command.length() > 2
                        && Integer.parseInt(command.substring(2), 16) == COMMAND_GET_RESPONSE) {
                    mSimAppList.get(getSimAppDataIndexByAid(aid)).setIccidInfo(value[0]);
                } else {
                    Log.e(mTag, "No valid Iccid data found");
                    result = false;
                }
                break;
            case "EF_GID1":
                if (command.length() > 2
                        && Integer.parseInt(command.substring(2), 16) == COMMAND_READ_BINARY) {
                    mSimAppList.get(getSimAppDataIndexByAid(aid)).setGid1(value[0]);
                } else if (command.length() > 2
                        && Integer.parseInt(command.substring(2), 16) == COMMAND_GET_RESPONSE) {
                    mSimAppList.get(getSimAppDataIndexByAid(aid)).setGid1Info(value[0]);
                } else {
                    Log.e(mTag, "No valid GID1 found");
                    result = false;
                }
                break;
            default:
                result = false;
                Log.w(mTag, "Not support EF field - " + name + "(" + id + ")");
                break;
        }
        return result;
    }

    private boolean loadSimProfileFromXml() {
        boolean result = true;

        if (mSimProfileInfoList == null) {
            Log.e(mTag, "No support SIM profile list.");
            return false;
        }

        try {
            String file = mSimProfileInfoList[mSimProfileId].getXmlFile();
            int event;
            XmlPullParser parser = Xml.newPullParser();
            InputStream input;
            boolean mocksim_validation = false;
            boolean mocksim_pf_validatiion = false;
            boolean mocksim_mf_validation = false;
            int appidx = 0;
            int fd_lock = 0;
            int sc_lock = 0;
            String adf_aid = "";

            input = mContext.getAssets().open(file);
            parser.setInput(input, null);
            while (result && (event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (MOCK_SIM_TAG.equals(parser.getName())) {
                            int numofapp = Integer.parseInt(parser.getAttributeValue(0));
                            mATR = parser.getAttributeValue(1);
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_SIM_TAG
                                            + ": numofapp = "
                                            + numofapp
                                            + " atr = "
                                            + mATR);
                            mSimApp = new AppStatus[numofapp];
                            if (mSimApp == null) {
                                Log.e(mTag, "Create SIM app failed!");
                                result = false;
                                break;
                            }
                            mocksim_validation = true;
                        } else if (mocksim_validation
                                && MOCK_SIM_PROFILE_TAG.equals(parser.getName())
                                && appidx < mSimApp.length) {
                            int id = Integer.parseInt(parser.getAttributeValue(0));
                            int type = convertMockSimAppType(parser.getAttributeValue(1));
                            mSimApp[appidx] = new AppStatus();
                            mSimApp[appidx].appType = type;
                            switch (type) {
                                case AppStatus.APP_TYPE_SIM:
                                case AppStatus.APP_TYPE_USIM:
                                    mSimProfileInfoList[mSimProfileId].setGsmAppIndex(id);
                                    break;
                                case AppStatus.APP_TYPE_CSIM:
                                case AppStatus.APP_TYPE_RUIM:
                                    mSimProfileInfoList[mSimProfileId].setCdmaAppIndex(id);
                                    break;
                                case AppStatus.APP_TYPE_ISIM:
                                    mSimProfileInfoList[mSimProfileId].setImsAppIndex(id);
                                    break;
                            }
                            Log.d(
                                    mTag,
                                    "Found ["
                                            + MOCK_SIM_PROFILE_TAG
                                            + "]: id = "
                                            + id
                                            + " type = "
                                            + parser.getAttributeValue(1)
                                            + " ("
                                            + type
                                            + ")========");
                            mocksim_pf_validatiion = true;
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_PIN_PROFILE_TAG.equals(parser.getName())) {
                            int appstate = convertMockSimAppState(parser.getAttributeValue(0));
                            mSimApp[appidx].appState = appstate;
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_PIN_PROFILE_TAG
                                            + ": appstate = "
                                            + parser.getAttributeValue(0)
                                            + " ("
                                            + appstate
                                            + ")");
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_PIN1_STATE_TAG.equals(parser.getName())) {
                            String state = parser.nextText();
                            int pin1state = convertMockSimPinState(state);
                            mSimApp[appidx].pin1 = pin1state;
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_PIN1_STATE_TAG
                                            + " = "
                                            + state
                                            + " ("
                                            + pin1state
                                            + ")");
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_PIN2_STATE_TAG.equals(parser.getName())) {
                            String state = parser.nextText();
                            int pin2state = convertMockSimPinState(state);
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_PIN2_STATE_TAG
                                            + " = "
                                            + state
                                            + " ("
                                            + pin2state
                                            + ")");
                            mSimApp[appidx].pin2 = pin2state;
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_FACILITY_LOCK_FD_TAG.equals(parser.getName())) {
                            fd_lock = convertMockSimFacilityLock(parser.nextText());
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_FACILITY_LOCK_FD_TAG
                                            + ": fd lock = "
                                            + fd_lock);
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_FACILITY_LOCK_SC_TAG.equals(parser.getName())) {
                            sc_lock = convertMockSimFacilityLock(parser.nextText());
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_FACILITY_LOCK_SC_TAG
                                            + ": sc lock = "
                                            + sc_lock);
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_MF_TAG.equals(parser.getName())) {
                            SimAppData simAppData;
                            String name = parser.getAttributeValue(0);
                            String path = parser.getAttributeValue(1);
                            simAppData = new SimAppData(appidx, name, path);
                            if (simAppData == null) {
                                Log.e(mTag, "Create SIM app data failed!");
                                result = false;
                                break;
                            }
                            mSimAppList.add(simAppData);
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_MF_TAG
                                            + ": name = "
                                            + name
                                            + " path = "
                                            + path);
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && !mocksim_mf_validation
                                && MOCK_EF_DIR_TAG.equals(parser.getName())) {
                            SimAppData simAppData;
                            String name = parser.getAttributeValue(0);
                            boolean curr_active = Boolean.parseBoolean(parser.getAttributeValue(1));
                            String aid = parser.nextText();
                            simAppData = new SimAppData(appidx, aid, name, curr_active);
                            if (simAppData == null) {
                                Log.e(mTag, "Create SIM app data failed!");
                                result = false;
                                break;
                            }
                            simAppData.setFdnStatus(fd_lock);
                            simAppData.setPin1State(sc_lock);
                            mSimAppList.add(simAppData);
                            if (curr_active) {
                                mSimApp[appidx].aidPtr = aid;
                            }
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_EF_DIR_TAG
                                            + ": name = "
                                            + name
                                            + ": curr_active = "
                                            + curr_active
                                            + " aid = "
                                            + aid);
                            mocksim_mf_validation = true;
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && mocksim_mf_validation
                                && MOCK_ADF_TAG.equals(parser.getName())) {
                            String aid = parser.getAttributeValue(0);
                            Log.d(mTag, "Found " + MOCK_ADF_TAG + ": aid = " + aid);
                            adf_aid = aid;
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && mocksim_mf_validation
                                && (adf_aid.length() > 0)
                                && MOCK_EF_TAG.equals(parser.getName())) {
                            String name = parser.getAttributeValue(0);
                            String id = parser.getAttributeValue(1);
                            String command = parser.getAttributeValue(2);
                            String[] value;
                            switch (id) {
                                case "6F07": // EF_IMSI
                                    int mncDigit = Integer.parseInt(parser.getAttributeValue(3));
                                    String imsi = parser.nextText();
                                    value = extractImsi(imsi, mncDigit);
                                    if (value != null
                                            && storeEfData(adf_aid, name, id, command, value)) {
                                        Log.d(
                                                mTag,
                                                "Found "
                                                        + MOCK_EF_TAG
                                                        + ": name = "
                                                        + name
                                                        + " id = "
                                                        + id
                                                        + " command = "
                                                        + command
                                                        + " value = "
                                                        + imsi
                                                        + " with mnc-digit = "
                                                        + mncDigit);
                                    }
                                    break;
                                default:
                                    value = new String[1];
                                    if (value != null) {
                                        value[0] = parser.nextText();
                                        if (storeEfData(adf_aid, name, id, command, value)) {
                                            Log.d(
                                                    mTag,
                                                    "Found "
                                                            + MOCK_EF_TAG
                                                            + ": name = "
                                                            + name
                                                            + " id = "
                                                            + id
                                                            + " command = "
                                                            + command
                                                            + " value = "
                                                            + value[0]);
                                        }
                                    }
                                    break;
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (mocksim_validation && MOCK_SIM_PROFILE_TAG.equals(parser.getName())) {
                            appidx++;
                            mocksim_pf_validatiion = false;
                            mocksim_mf_validation = false;
                        } else if (mocksim_validation && MOCK_ADF_TAG.equals(parser.getName())) {
                            adf_aid = "";
                        }
                        break;
                }
            }
            Log.d(mTag, "Totally create " + Math.min(mSimApp.length, appidx) + " SIM profiles");
            mSimProfileInfoList[mSimProfileId].setNumOfSimApp(appidx);
            input.close();
        } catch (Exception e) {
            Log.e(mTag, "Exception error: " + e);
            result = false;
        }

        return result;
    }

    private boolean loadSimApp() {
        boolean result = true;

        if (mSimAppList == null) {
            mSimAppList = new ArrayList<SimAppData>();
        } else {
            mSimAppList.clear();
        }

        if (mSimProfileId == MOCK_SIM_PROFILE_ID_DEFAULT
                || mSimProfileInfoList[mSimProfileId].getXmlFile().length() == 0) {
            Log.d(mTag, "SIM absent case");
            mSimApp = new AppStatus[0];
            if (mSimApp == null) {
                Log.e(mTag, "Create SIM app failed!");
                result = false;
            }
        } else {
            result = loadSimProfileFromXml();
        }

        return result;
    }

    private boolean loadMockSimCard() {
        if (mSimProfileId != MOCK_SIM_PROFILE_ID_DEFAULT) {
            switch (mPhysicalSlotId) {
                case MOCK_SIM_SLOT_1: // pSIM
                    mEID = DEFAULT_SIM1_EID;
                    break;
                case MOCK_SIM_SLOT_2: // eSIM
                    mATR = DEFAULT_SIM2_ATR;
                    mEID = DEFAULT_SIM2_EID;
                    break;
                case MOCK_SIM_SLOT_3: // eSIM
                    mATR = DEFAULT_SIM3_ATR;
                    mEID = DEFAULT_SIM3_EID;
                    break;
            }
            mUniversalPinState = PinState.DISABLED;
            mIsCardPresent = true;
        } else {
            switch (mPhysicalSlotId) {
                case MOCK_SIM_SLOT_1:
                    mATR = DEFAULT_SIM1_ATR;
                    mEID = DEFAULT_SIM1_EID;
                    mUniversalPinState = DEFAULT_SIM1_UNIVERSAL_PIN_STATE;
                    mIsCardPresent = DEFAULT_SIM1_CARD_PRESENT;
                    break;
                case MOCK_SIM_SLOT_2:
                    mATR = DEFAULT_SIM2_ATR;
                    mEID = DEFAULT_SIM2_EID;
                    mUniversalPinState = DEFAULT_SIM2_UNIVERSAL_PIN_STATE;
                    mIsCardPresent = DEFAULT_SIM2_CARD_PRESENT;
                    break;
                case MOCK_SIM_SLOT_3:
                    mATR = DEFAULT_SIM3_ATR;
                    mEID = DEFAULT_SIM3_EID;
                    mUniversalPinState = DEFAULT_SIM3_UNIVERSAL_PIN_STATE;
                    mIsCardPresent = DEFAULT_SIM3_CARD_PRESENT;
                    break;
            }
        }
        return loadSimApp();
    }

    public boolean loadSimCard(int simprofileid) {
        boolean result = true;
        if (mSimProfileId >= MOCK_SIM_PROFILE_ID_DEFAULT
                && mSimProfileId < MOCK_SIM_PROFILE_ID_MAX) {
            mSimProfileId = simprofileid;
            result = loadMockSimCard();
        } else {
            result = false;
        }
        return result;
    }

    public boolean isSlotPortActive() {
        return mIsSlotPortActive;
    }

    public boolean isCardPresent() {
        return mIsCardPresent;
    }

    public int getNumOfSimPortInfo() {
        // TODO: support multiple sim port
        return DEFAULT_NUM_OF_SIM_PORT_INFO;
    }

    public int getPhysicalSlotId() {
        return mPhysicalSlotId;
    }

    public int getLogicalSlotId() {
        return mLogicalSlotId;
    }

    public int getSlotPortId() {
        return mSlotPortId;
    }

    public String getEID() {
        return mEID;
    }

    public boolean setATR(String atr) {
        // TODO: add any ATR format check
        mATR = atr;
        return true;
    }

    public String getATR() {
        return mATR;
    }

    public boolean setICCID(String iccid) {
        boolean result = false;
        SimAppData activeSimAppData = getActiveSimAppData();

        // TODO: add iccid format check
        if (activeSimAppData != null) {
            String iccidInfo = activeSimAppData.getIccidInfo();
            int dataFileSize = iccid.length() / 2;
            String dataFileSizeStr = Integer.toString(dataFileSize, 16);

            Log.d(mTag, "Data file size = " + dataFileSizeStr);
            if (dataFileSizeStr.length() <= 4) {
                dataFileSizeStr = String.format("%04x", dataFileSize).toUpperCase(Locale.ROOT);
                // Data file size index is 2 and 3 in byte array of iccid info data.
                iccidInfo = iccidInfo.substring(0, 4) + dataFileSizeStr + iccidInfo.substring(8);
                Log.d(mTag, "Update iccid info = " + iccidInfo);
                activeSimAppData.setIccidInfo(iccidInfo);
                activeSimAppData.setIccid(iccid);
                result = true;
            } else {
                Log.e(mTag, "Data file size(" + iccidInfo.length() + ") is too large.");
            }
        } else {
            Log.e(mTag, "activeSimAppData = null");
        }

        return result;
    }

    public String getICCID() {
        String iccid = "";
        SimAppData activeSimAppData = getActiveSimAppData();

        if (activeSimAppData != null) {
            iccid = activeSimAppData.getIccid();
        }

        return iccid;
    }

    public int getUniversalPinState() {
        return mUniversalPinState;
    }

    public int getGsmAppIndex() {
        return mSimProfileInfoList[mSimProfileId].getGsmAppIndex();
    }

    public int getCdmaAppIndex() {
        return mSimProfileInfoList[mSimProfileId].getCdmaAppIndex();
    }

    public int getImsAppIndex() {
        return mSimProfileInfoList[mSimProfileId].getImsAppIndex();
    }

    public int getNumOfSimApp() {
        return mSimProfileInfoList[mSimProfileId].getNumOfSimApp();
    }

    public AppStatus[] getSimApp() {
        return mSimApp;
    }

    public ArrayList<SimAppData> getSimAppList() {
        return mSimAppList;
    }

    public SimAppData getActiveSimAppData() {
        SimAppData activeSimAppData = null;

        for (int simAppIdx = 0; simAppIdx < mSimAppList.size(); simAppIdx++) {
            if (mSimAppList.get(simAppIdx).isCurrentActive()) {
                activeSimAppData = mSimAppList.get(simAppIdx);
                break;
            }
        }

        return activeSimAppData;
    }

    public String getActiveSimAppId() {
        String aid = "";
        SimAppData activeSimAppData = getActiveSimAppData();

        if (activeSimAppData != null) {
            aid = activeSimAppData.getAid();
        }

        return aid;
    }

    private boolean setMcc(String mcc) {
        boolean result = false;

        if (mcc.length() == 3) {
            SimAppData activeSimAppData = getActiveSimAppData();
            if (activeSimAppData != null) {
                activeSimAppData.setMcc(mcc);
                result = true;
            }
        }

        return result;
    }

    private boolean setMnc(String mnc) {
        boolean result = false;

        if (mnc.length() == 2 || mnc.length() == 3) {
            SimAppData activeSimAppData = getActiveSimAppData();
            if (activeSimAppData != null) {
                activeSimAppData.setMnc(mnc);
                result = true;
            }
        }

        return result;
    }

    public String getMccMnc() {
        String mcc;
        String mnc;
        String result = "";
        SimAppData activeSimAppData = getActiveSimAppData();

        if (activeSimAppData != null) {
            mcc = activeSimAppData.getMcc();
            mnc = activeSimAppData.getMnc();
            if (mcc != null
                    && mcc.length() == 3
                    && mnc != null
                    && (mnc.length() == 2 || mnc.length() == 3)) {
                result = mcc + mnc;
            } else {
                Log.e(mTag, "Invalid Mcc or Mnc.");
            }
        }
        return result;
    }

    public String getMsin() {
        String result = "";
        SimAppData activeSimAppData = getActiveSimAppData();

        if (activeSimAppData != null) {
            result = activeSimAppData.getMsin();
            if (result.length() <= 0 || result.length() > 10) {
                Log.e(mTag, "Invalid Msin.");
            }
        }

        return result;
    }

    public boolean setImsi(String mcc, String mnc, String msin) {
        boolean result = false;

        if (msin.length() > 0 && (mcc.length() + mnc.length() + msin.length()) <= 15) {
            SimAppData activeSimAppData = getActiveSimAppData();
            if (activeSimAppData != null) {
                setMcc(mcc);
                setMnc(mnc);
                activeSimAppData.setMsin(msin);
                result = true;
            } else {
                Log.e(mTag, "activeSimAppData = null");
            }
        } else {
            Log.e(mTag, "Invalid IMSI");
        }

        return result;
    }

    public String getImsi() {
        String imsi = "";
        String mccmnc;
        String msin;
        SimAppData activeSimAppData = getActiveSimAppData();

        if (activeSimAppData != null) {
            mccmnc = getMccMnc();
            msin = activeSimAppData.getMsin();
            if (mccmnc.length() > 0
                    && msin != null
                    && msin.length() > 0
                    && (mccmnc.length() + msin.length()) <= 15) {
                imsi = mccmnc + msin;
            } else {
                Log.e(mTag, "Invalid Imsi.");
            }
        }
        return imsi;
    }
}
