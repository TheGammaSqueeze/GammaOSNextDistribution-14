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
import android.hardware.radio.network.BarringInfo;
import android.hardware.radio.network.CellConnectionStatus;
import android.hardware.radio.network.CellInfo;
import android.hardware.radio.network.CellInfoRatSpecificInfo;
import android.hardware.radio.network.Domain;
import android.hardware.radio.network.EmergencyRegResult;
import android.hardware.radio.network.RegState;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.util.Log;

import com.android.internal.telephony.RILConstants;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockNetworkService {
    private static final String TAG = "MockNetworkService";

    // Grouping of RAFs
    // 2G
    public static final int GSM =
            RadioAccessFamily.RAF_GSM | RadioAccessFamily.RAF_GPRS | RadioAccessFamily.RAF_EDGE;
    public static final int CDMA =
            RadioAccessFamily.RAF_IS95A | RadioAccessFamily.RAF_IS95B | RadioAccessFamily.RAF_1xRTT;
    // 3G
    public static final int EVDO =
            RadioAccessFamily.RAF_EVDO_0
                    | RadioAccessFamily.RAF_EVDO_A
                    | RadioAccessFamily.RAF_EVDO_B
                    | RadioAccessFamily.RAF_EHRPD;
    public static final int HS =
            RadioAccessFamily.RAF_HSUPA
                    | RadioAccessFamily.RAF_HSDPA
                    | RadioAccessFamily.RAF_HSPA
                    | RadioAccessFamily.RAF_HSPAP;
    public static final int WCDMA = HS | RadioAccessFamily.RAF_UMTS;
    // 4G
    public static final int LTE = RadioAccessFamily.RAF_LTE | RadioAccessFamily.RAF_LTE_CA;
    // 5G
    public static final int NR = RadioAccessFamily.RAF_NR;

    static final int MOCK_CARRIER_NO_SERVICE = 0;

    // Network status update reason
    static final int NETWORK_UPDATE_PREFERRED_MODE_CHANGE = 1;

    public static final int LATCH_TRIGGER_EMERGENCY_SCAN = 0;
    public static final int LATCH_CANCEL_EMERGENCY_SCAN = 1;
    private static final int LATCH_MAX = 2;

    private int mCsRegState = RegState.NOT_REG_MT_NOT_SEARCHING_OP;
    private int mPsRegState = RegState.NOT_REG_MT_NOT_SEARCHING_OP;

    private int mRegFailCause = 0;

    private Context mContext;

    private String mSimPlmn;
    private boolean mIsHomeCamping;
    private boolean mIsRoamingCamping;
    private int mHomeCarrierId;
    private int mRoamingCarrierId;
    private int mInServiceCarrierId;
    private int mHighRat;

    private ArrayList<MockModemCell> mCellList = new ArrayList<MockModemCell>();

    private BarringInfo[] mBarringInfos = new BarringInfo[0];
    private EmergencyRegResult mEmergencyRegResult = new EmergencyRegResult();
    private boolean mEmergencyNetworkScanTriggered = false;
    private boolean mEmergencyNetworkScanCanceled = false;
    private int[] mEmergencyNetworkScanAccessNetwork = null;
    private int mEmergencyNetworkScanType = -1;
    private int mEmergencyMode = 0;

    private final CountDownLatch[] mLatches = new CountDownLatch[LATCH_MAX];

    private class MockModemCell {
        private int mCarrierId;

        // Non-AOSP
        public String[] mEHPlmnList;
        public String[] mAllowRoamingList;

        // AOSP
        private CellInfo[] mCells;

        MockModemCell(Context context, String file) {
            MockNetworkConfig config;

            config = new MockNetworkConfig(context);
            config.getConfigFromAssets(file);
            mCarrierId = config.getCarrierId();
            updateHomeRoamingList(config);
            updateCellList(config);
        }

        public int getCarrierId() {
            return mCarrierId;
        }

        public CellInfo[] getCells() {
            return mCells;
        }

        private void updateHomeRoamingList(MockNetworkConfig config) {
            mEHPlmnList = config.getEHPlmnList();
            mAllowRoamingList = config.getAllowRoamingList();
        }

        private void updateCellList(MockNetworkConfig config) {
            int cellNum;

            cellNum = config.getCellNum();
            mCells = new CellInfo[cellNum];
            for (int i = 0; i < cellNum; i++) {
                mCells[i] = config.getCellInfo(i);
            }
        }

        public android.hardware.radio.network.OperatorInfo getPrimaryCellOperatorInfo() {
            android.hardware.radio.network.OperatorInfo operatorInfo =
                    new android.hardware.radio.network.OperatorInfo();
            for (CellInfo cellInfo : getCells()) {
                if (cellInfo.connectionStatus == CellConnectionStatus.PRIMARY_SERVING) {
                    switch (cellInfo.ratSpecificInfo.getTag()) {
                        case CellInfoRatSpecificInfo.wcdma:
                            operatorInfo =
                                    cellInfo.ratSpecificInfo.getWcdma()
                                            .cellIdentityWcdma
                                            .operatorNames;
                            break;
                        case CellInfoRatSpecificInfo.lte:
                            operatorInfo =
                                    cellInfo.ratSpecificInfo.getLte().cellIdentityLte.operatorNames;
                            break;
                        default:
                            break;
                    }
                }
            }

            return operatorInfo;
        }

        public android.hardware.radio.network.SignalStrength getPrimaryCellSignalStrength() {
            android.hardware.radio.network.SignalStrength signalStrength =
                    new android.hardware.radio.network.SignalStrength();

            signalStrength.gsm = new android.hardware.radio.network.GsmSignalStrength();
            signalStrength.cdma = new android.hardware.radio.network.CdmaSignalStrength();
            signalStrength.evdo = new android.hardware.radio.network.EvdoSignalStrength();
            signalStrength.lte = new android.hardware.radio.network.LteSignalStrength();
            signalStrength.tdscdma = new android.hardware.radio.network.TdscdmaSignalStrength();
            signalStrength.wcdma = new android.hardware.radio.network.WcdmaSignalStrength();
            signalStrength.nr = new android.hardware.radio.network.NrSignalStrength();
            signalStrength.nr.csiCqiReport = new byte[0];

            for (CellInfo cellInfo : getCells()) {
                if (cellInfo.connectionStatus == CellConnectionStatus.PRIMARY_SERVING) {
                    switch (cellInfo.ratSpecificInfo.getTag()) {
                        case CellInfoRatSpecificInfo.wcdma:
                            signalStrength.wcdma =
                                    cellInfo.ratSpecificInfo.getWcdma().signalStrengthWcdma;
                            break;
                        case CellInfoRatSpecificInfo.lte:
                            signalStrength.lte =
                                    cellInfo.ratSpecificInfo.getLte().signalStrengthLte;
                            break;
                        default:
                            break;
                    }
                }
            }

            return signalStrength;
        }

        public int getPrimaryCellRat() {
            int rat = android.hardware.radio.RadioTechnology.UNKNOWN;

            for (CellInfo cellInfo : getCells()) {
                if (cellInfo.connectionStatus == CellConnectionStatus.PRIMARY_SERVING) {
                    switch (cellInfo.ratSpecificInfo.getTag()) {
                        case CellInfoRatSpecificInfo.wcdma:
                            // TODO: Need find an element to assign the rat WCDMA, HSUPA, HSDPA, or
                            // HSPA
                            rat = android.hardware.radio.RadioTechnology.HSPA;
                            break;
                        case CellInfoRatSpecificInfo.lte:
                            rat = android.hardware.radio.RadioTechnology.LTE;
                            break;
                        default:
                            break;
                    }
                }
            }

            return rat;
        }

        public android.hardware.radio.network.CellIdentity getPrimaryCellIdentity() {
            android.hardware.radio.network.CellIdentity cellIdentity =
                    android.hardware.radio.network.CellIdentity.noinit(true);

            for (CellInfo cellInfo : getCells()) {
                if (cellInfo.connectionStatus == CellConnectionStatus.PRIMARY_SERVING) {
                    switch (cellInfo.ratSpecificInfo.getTag()) {
                        case CellInfoRatSpecificInfo.wcdma:
                            cellIdentity.setWcdma(
                                    cellInfo.ratSpecificInfo.getWcdma().cellIdentityWcdma);
                            break;
                        case CellInfoRatSpecificInfo.lte:
                            cellIdentity.setLte(cellInfo.ratSpecificInfo.getLte().cellIdentityLte);
                            break;
                        default:
                            break;
                    }
                }
            }

            return cellIdentity;
        }
    }

    public MockNetworkService(Context context) {
        mContext = context;
        loadMockModemCell("mock_network_tw_cht.xml");
        loadMockModemCell("mock_network_tw_fet.xml");
        loadMockModemCell("mock_network_us_fi.xml");
        for (int i = 0; i < LATCH_MAX; i++) {
            mLatches[i] = new CountDownLatch(1);
        }
    }

    public void loadMockModemCell(String config) {
        MockModemCell tmp = new MockModemCell(mContext, config);
        int cid = tmp.getCarrierId();
        if (!mCellList.isEmpty()) {
            for (MockModemCell mmc : mCellList) {
                if (mmc.getCarrierId() == cid) {
                    Log.d(TAG, "Carrier ID " + cid + " had been loaded.");
                    return;
                }
            }
        }

        Log.d(TAG, "Load carrier(" + cid + ") " + config);
        mCellList.add(tmp);
    }

    private int getHighestRatFromNetworkType(int raf) {
        int rat;
        int networkMode = RadioAccessFamily.getNetworkTypeFromRaf(raf);

        switch (networkMode) {
            case RILConstants.NETWORK_MODE_WCDMA_PREF:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_GSM_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_GSM;
                break;
            case RILConstants.NETWORK_MODE_WCDMA_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_GSM_UMTS:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_CDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_IS95A;
                break;
            case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_CDMA_NO_EVDO:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_IS95A;
                break;
            case RILConstants.NETWORK_MODE_EVDO_NO_CDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0;
                break;
            case RILConstants.NETWORK_MODE_GLOBAL:
                // GSM | WCDMA | CDMA | EVDO;
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_TD_SCDMA;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_GSM:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_TD_SCDMA;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_GSM:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_NR_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_CDMA_EVDO:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_GSM:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            default:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
                break;
        }
        return rat;
    }

    public android.hardware.radio.network.OperatorInfo getPrimaryCellOperatorInfo() {
        android.hardware.radio.network.OperatorInfo operatorInfo =
                new android.hardware.radio.network.OperatorInfo();

        if (mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME) {
            operatorInfo = getCarrierStatus(mHomeCarrierId).getPrimaryCellOperatorInfo();
        } else if (mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING) {
            operatorInfo = getCarrierStatus(mRoamingCarrierId).getPrimaryCellOperatorInfo();
        }

        return operatorInfo;
    }

    public android.hardware.radio.network.CellIdentity getPrimaryCellIdentity() {
        android.hardware.radio.network.CellIdentity cellIdentity =
                android.hardware.radio.network.CellIdentity.noinit(true);

        if (mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME) {
            cellIdentity = getCarrierStatus(mHomeCarrierId).getPrimaryCellIdentity();
        } else if (mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING) {
            cellIdentity = getCarrierStatus(mRoamingCarrierId).getPrimaryCellIdentity();
        }

        return cellIdentity;
    }

    public android.hardware.radio.network.CellInfo[] getCells() {
        ArrayList<android.hardware.radio.network.CellInfo> cellInfos = new ArrayList<>();

        for (MockModemCell mmc : mCellList) {
            CellInfo[] cells = mmc.getCells();
            if (cells != null) {
                for (CellInfo cellInfo : cells) {
                    cellInfos.add(cellInfo);
                }
            }
        }

        return cellInfos.stream().toArray(android.hardware.radio.network.CellInfo[]::new);
    }

    public boolean updateHighestRegisteredRat(int raf) {

        int rat = mHighRat;
        mHighRat = getHighestRatFromNetworkType(raf);

        return (rat == mHighRat);
    }

    public void updateNetworkStatus(int reason) {
        if (reason == NETWORK_UPDATE_PREFERRED_MODE_CHANGE) {
            Log.d(TAG, "updateNetworkStatus: NETWORK_UPDATE_PREFERRED_MODE_CHANGE");
            // TODO
        }
    }

    public int getRegistrationRat() {
        int rat = android.hardware.radio.RadioTechnology.UNKNOWN;

        if (mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME) {
            rat = getCarrierStatus(mHomeCarrierId).getPrimaryCellRat();
        } else if (mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING) {
            rat = getCarrierStatus(mRoamingCarrierId).getPrimaryCellRat();
        }

        return rat;
    }

    public android.hardware.radio.network.SignalStrength getSignalStrength() {
        android.hardware.radio.network.SignalStrength signalStrength =
                new android.hardware.radio.network.SignalStrength();

        if (mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME) {
            signalStrength = getCarrierStatus(mHomeCarrierId).getPrimaryCellSignalStrength();
        } else if (mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING) {
            signalStrength = getCarrierStatus(mRoamingCarrierId).getPrimaryCellSignalStrength();
        } else {
            // TODO
        }

        return signalStrength;
    }

    public int getRegistration(int domain) {
        if (domain == android.hardware.radio.network.Domain.CS) {
            return mCsRegState;
        } else {
            return mPsRegState;
        }
    }

    public boolean isInService() {
        return ((mCsRegState == RegState.REG_HOME)
                || (mPsRegState == RegState.REG_HOME)
                || (mCsRegState == RegState.REG_ROAMING)
                || (mPsRegState == RegState.REG_ROAMING));
    }

    public boolean isPsInService() {
        return ((mPsRegState == RegState.REG_HOME) || (mPsRegState == RegState.REG_ROAMING));
    }

    public void updateSimPlmn(String simPlmn) {
        mSimPlmn = simPlmn;

        // Reset mHomeCarrierId and mRoamingCarrierId
        mHomeCarrierId = MOCK_CARRIER_NO_SERVICE;
        mRoamingCarrierId = MOCK_CARRIER_NO_SERVICE;

        if (mSimPlmn == null || mSimPlmn.isEmpty()) return;

        if (mCellList.isEmpty()) return;

        for (MockModemCell mmc : mCellList) {

            if (isHomeCellExisted() && isRoamingCellExisted()) break;

            // Find out which cell is Home cell
            for (String plmn : mmc.mEHPlmnList) {
                if (!isHomeCellExisted() && mSimPlmn.equals(plmn)) {
                    mHomeCarrierId = mmc.getCarrierId();
                    Log.d(TAG, "Cell ID: Home Cell " + mHomeCarrierId);
                }
            }

            // Find out which cell is Home cell
            for (String plmn : mmc.mAllowRoamingList) {
                if (!isRoamingCellExisted() && mSimPlmn.equals(plmn)) {
                    mRoamingCarrierId = mmc.getCarrierId();
                    Log.d(TAG, "Cell ID: Roaming Cell " + mRoamingCarrierId);
                }
            }
        }
    }

    /**
     * Set the device enters IN SERVICE
     *
     * @param isRoaming boolean true if the camping network is Roaming service, otherwise Home
     *     service
     * @param inService boolean true if the deviec enters carrier coverge, otherwise the device
     *     leaves the carrier coverage.
     */
    public void setServiceStatus(boolean isRoaming, boolean inService) {
        if (isRoaming) {
            mIsRoamingCamping = inService;
        } else {
            mIsHomeCamping = inService;
        }
    }

    public boolean getIsHomeCamping() {
        return mIsHomeCamping;
    }

    public boolean getIsRoamingCamping() {
        return mIsRoamingCamping;
    }

    public boolean isHomeCellExisted() {
        return (mHomeCarrierId != MOCK_CARRIER_NO_SERVICE);
    }

    public boolean isRoamingCellExisted() {
        return (mRoamingCarrierId != MOCK_CARRIER_NO_SERVICE);
    }

    public void setRegFailCause(int regFailCause) {
        mRegFailCause = regFailCause;
    }

    public int getRegFailCause() {
        return mRegFailCause;
    }

    /** updateServiceState */
    public void updateServiceState(int reg) {
        updateServiceState(reg, Domain.CS | Domain.PS);
    }

    public void updateServiceState(int reg, int domainBitmask) {
        Log.d(TAG, "Cell ID: updateServiceState " + reg + " with domainBitmask = " + domainBitmask);

        /* a little range check here */
        switch (reg) {
            case RegState.NOT_REG_MT_SEARCHING_OP:
                /* fallthrough */
            case RegState.REG_HOME:
                /* fallthrough */
            case RegState.REG_ROAMING:
                /* fallthrough */
            case RegState.NOT_REG_MT_NOT_SEARCHING_OP:
                /* fallthrough */
            case RegState.REG_DENIED:
                break;
            default:
                reg = RegState.NOT_REG_MT_NOT_SEARCHING_OP;
                break;
        }

        if ((domainBitmask & Domain.CS) != 0) {
            mCsRegState = reg;
        }
        if ((domainBitmask & Domain.PS) != 0) {
            mPsRegState = reg;
        }

        updateCellRegistration();
    }

    void updateCellRegistration() {
        for (MockModemCell mmc : mCellList) {
            boolean registered;
            if ((mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME)
                    && mHomeCarrierId == mmc.getCarrierId()) {
                registered = true;
            } else if ((mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING)
                    && mRoamingCarrierId == mmc.getCarrierId()) {
                registered = true;
            } else {
                registered = false;
            }

            CellInfo[] cells = mmc.getCells();
            if (cells != null) {
                for (CellInfo cellInfo : cells) {
                    cellInfo.registered = registered;
                }
            }
        }
    }

    public MockModemCell getCarrierStatus(int carrierId) {
        for (MockModemCell mmc : mCellList) {
            if (mmc.getCarrierId() == carrierId) return mmc;
        }

        return null;
    }

    /**
     * @return The barring status.
     */
    public BarringInfo[] getBarringInfo() {
        return mBarringInfos;
    }

    /**
     * Updates the barring status.
     * @param barringInfos the barring status.
     */
    public void updateBarringInfos(BarringInfo[] barringInfos) {
        mBarringInfos = barringInfos;
    }

    /**
     * Updates the emergency registration state.
     * @param regResult the emergency registration state.
     */
    public void setEmergencyRegResult(EmergencyRegResult regResult) {
        mEmergencyRegResult = regResult;
    }

    /**
     * @return the emergency registration state.
     */
    public EmergencyRegResult getEmergencyRegResult() {
        return mEmergencyRegResult;
    }

    /**
     * Updates the current emergency mode.
     * @param mode the emergency mode
     */
    public void setEmergencyMode(int mode) {
        mEmergencyMode = mode;
    }

    /**
     * @return the current emergency mode.
     */
    public int getEmergencyMode() {
        return mEmergencyMode;
    }

    /**
     * Updates whether triggerEmergencyNetworkScan is requested and the attributes.
     *
     * @param state {@code true} if the scan is trigerred.
     * @param accessNetwork the list of preferred network type.
     * @param scanType indicates the preferred scan type.
     */
    public void setEmergencyNetworkScanTriggered(boolean state,
            int[] accessNetwork, int scanType) {
        mEmergencyNetworkScanTriggered = state;
        if (state) {
            mEmergencyNetworkScanAccessNetwork = accessNetwork;
            mEmergencyNetworkScanType = scanType;
            countDownLatch(LATCH_TRIGGER_EMERGENCY_SCAN);
        }
    }

    /**
     * Updates whether cancelEmergencyNetworkScan is requested.
     */
    public void setEmergencyNetworkScanCanceled(boolean state) {
        mEmergencyNetworkScanCanceled = state;
        if (state) {
            mEmergencyNetworkScanAccessNetwork = null;
            mEmergencyNetworkScanType = -1;
            countDownLatch(LATCH_CANCEL_EMERGENCY_SCAN);
        }
    }

    /**
     * @return whether emergency network scan is triggered.
     */
    public boolean isEmergencyNetworkScanTriggered() {
        return mEmergencyNetworkScanTriggered;
    }

    /**
     * @return whether emergency network scan is canceled.
     */
    public boolean isEmergencyNetworkScanCanceled() {
        return mEmergencyNetworkScanCanceled;
    }

    /**
     * @return the list of preferred network type.
     */
    public int[] getEmergencyNetworkScanAccessNetwork() {
        return mEmergencyNetworkScanAccessNetwork;
    }

    /**
     * @return the preferred scan type.
     */
    public int getEmergencyNetworkScanType() {
        return mEmergencyNetworkScanType;
    }

    /**
     * Resets the emergency network scan attributes.
     */
    public void resetEmergencyNetworkScan() {
        mEmergencyRegResult = new EmergencyRegResult();
        mEmergencyNetworkScanTriggered = false;
        mEmergencyNetworkScanCanceled = false;
        mEmergencyNetworkScanAccessNetwork = null;
        mEmergencyNetworkScanType = -1;
        mEmergencyMode = 0;
    }

    private void countDownLatch(int latchIndex) {
        synchronized (mLatches) {
            mLatches[latchIndex].countDown();
        }
    }

    /**
     * Waits for the event of network service.
     *
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     * @return {@code true} if the event happens.
     */
    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLatches) {
                latch = mLatches[latchIndex];
            }
            long startTime = System.currentTimeMillis();
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
            Log.i(TAG, "Latch " + latchIndex + " took "
                    + (System.currentTimeMillis() - startTime) + " ms to count down.");
        } catch (InterruptedException e) {
            Log.e(TAG, "Waiting latch " + latchIndex + " interrupted, e=" + e);
        }
        synchronized (mLatches) {
            mLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    /**
     * Resets the CountDownLatches
     */
    public void resetAllLatchCountdown() {
        synchronized (mLatches) {
            for (int i = 0; i < LATCH_MAX; i++) {
                mLatches[i] = new CountDownLatch(1);
            }
        }
    }

    @Override
    public String toString() {
        return "isInService():" + isInService() + " Rat:" + getRegistrationRat() + "";
    }
}
