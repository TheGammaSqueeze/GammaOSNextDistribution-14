/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.telephony.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.SystemClock;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.ClosedSubscriberGroupInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Test TelephonyManager.getAllCellInfo()
 * <p>
 *
 * Test that the Cellular Location APIs return proper and complete information.
 * <ul>
 *     <li>At least one cell must be reported as the registered cell.
 *     <li>Registered cells must report the technology-specific fields consisting of a globally
 *         unique cell identifier.
 *     <li>All cells must report a technology-specific physical cell identifier, such as a tuple
 *         of the frequency and a phyisical cell ID that allows them to be uniquely identified
 *         given a known global cell.
 *     <li>All cells must report at least one valid power measurement.
 * </ul>
 *
 */
public class CellInfoTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "android.telephony.cts.CellInfoTest";

    // Maximum and minimum possible RSSI values(in dbm).
    private static final int MAX_RSSI = -10;
    private static final int MIN_RSSI = -150;
    // Maximum and minimum possible RSRP values(in dbm).
    private static final int MAX_RSRP = -44;
    private static final int MIN_RSRP = -140;
    // Maximum and minimum possible RSRQ values.
    private static final int MAX_RSRQ = -3;
    private static final int MIN_RSRQ = -35;
    // Maximum and minimum possible RSSNR values.
    private static final int MAX_RSSNR = 30;
    private static final int MIN_RSSNR = -20;
    // Maximum and minimum possible CQI values.
    private static final int MAX_CQI = 15;
    private static final int MIN_CQI = 0;

    /**
     * Maximum and minimum valid LTE RSSI values in dBm
     *
     * The valid RSSI ASU range from current HAL is [0,31].
     * Convert RSSI ASU to dBm: dBm = -113 + 2 * ASU, which is [-113, -51]
     *
     * Reference: TS 27.007 8.5 - Signal quality +CSQ
     */
    private static final int MAX_LTE_RSSI = -51;
    private static final int MIN_LTE_RSSI = -113;

    // The followings are parameters for testing CellIdentityCdma
    // Network Id ranges from 0 to 65535.
    private static final int NETWORK_ID  = 65535;
    // CDMA System Id ranges from 0 to 32767
    private static final int SYSTEM_ID = 32767;
    // Base Station Id ranges from 0 to 65535
    private static final int BASESTATION_ID = 65535;
    // Longitude ranges from -2592000 to 2592000.
    private static final int LONGITUDE = 2592000;
    // Latitude ranges from -1296000 to 1296000.
    private static final int LATITUDE = 1296000;
    // Cell identity ranges from 0 to 268435456.

    // The followings are parameters for testing CellIdentityLte
    private static final int CI = 268435456;
    // Physical cell id ranges from 0 to 503.
    private static final int PCI = 503;
    // Tracking area code ranges from 0 to 65535.
    private static final int TAC = 65535;
    // Absolute RF Channel Number ranges from 0 to 262143.
    private static final int EARFCN_MAX = 262143;
    private static final int BANDWIDTH_LOW = 1400;  // kHz
    private static final int BANDWIDTH_HIGH = 20000;  // kHz
    // 3GPP TS 36.101
    private static final int BAND_MIN_LTE = 1;
    private static final int BAND_MAX_LTE = 88;
    //3GPP TS 136.213 section 7.2.3
    private static final int CQI_TABLE_INDEX_MIN_LTE = 1;
    private static final int CQI_TABLE_INDEX_MAX_LTE = 6;

    // The followings are parameters for testing CellIdentityWcdma
    // Location Area Code ranges from 0 to 65535.
    private static final int LAC = 65535;
    // UMTS Cell Identity ranges from 0 to 268435455.
    private static final int CID_UMTS = 268435455;
    // Primary Scrambling Code ranges from 0 to 511.
    private static final int PSC = 511;
    // Cell Parameters Index rangest from 0-127.
    private static final int CPID = 127;

    // The followings are parameters for testing CellIdentityGsm
    // GSM Cell Identity ranges from 0 to 65535.
    private static final int CID_GSM = 65535;
    // GSM Absolute RF Channel Number ranges from 0 to 65535.
    private static final int ARFCN = 1024;

    // The followings are parameters for testing CellIdentityNr
    // 3GPP TS 38.101-1 and 38.101-2
    private static final int BAND_FR1_MIN_NR = 1;
    private static final int BAND_FR1_MAX_NR = 95;
    private static final int BAND_FR2_MIN_NR = 257;
    private static final int BAND_FR2_MAX_NR = 261;
    //3GPP TS 138.214 section 5.2.2.1
    private static final int CQI_TABLE_INDEX_MIN_NR = 1;
    private static final int CQI_TABLE_INDEX_MAX_NR = 3;

    // 3gpp 36.101 Sec 5.7.2
    private static final int CHANNEL_RASTER_EUTRAN = 100; //kHz

    private static final int MAX_CELLINFO_WAIT_MILLIS = 5000;
    private static final int MAX_LISTENER_WAIT_MILLIS = 1000; // usually much less
    // The maximum interval between CellInfo updates from the modem. In the AOSP code it varies
    // between 2 and 10 seconds, and there is an allowable modem delay of 3 seconds, so if we
    // cannot get a seconds CellInfo update within 15 seconds, then something is broken.
    // See DeviceStateMonitor#CELL_INFO_INTERVAL_*
    private static final int MAX_CELLINFO_INTERVAL_MILLIS = 15000; // in AOSP the max is 10s
    private static final int RADIO_HAL_VERSION_1_2 = makeRadioVersion(1, 2);
    private static final int RADIO_HAL_VERSION_1_5 = makeRadioVersion(1, 5);

    private PackageManager mPm;
    private TelephonyManager mTm;

    private int mNetworkHalVersion;

    private static final int makeRadioVersion(int major, int minor) {
        if (major < 0 || minor < 0) return 0;
        return major * 100 + minor;
    }

    private Executor mSimpleExecutor = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private static class CellInfoResultsCallback extends TelephonyManager.CellInfoCallback {
        List<CellInfo> cellInfo;

        @Override
        public synchronized void onCellInfo(List<CellInfo> cellInfo) {
            this.cellInfo = cellInfo;
            notifyAll();
        }

        public synchronized void wait(int millis) throws InterruptedException {
            if (cellInfo == null) {
                super.wait(millis);
            }
        }
    }

    private static class CellInfoListener extends PhoneStateListener {
        List<CellInfo> cellInfo;

        public CellInfoListener(Executor e) {
            super(e);
        }

        @Override
        public synchronized void onCellInfoChanged(List<CellInfo> cellInfo) {
            this.cellInfo = cellInfo;
            notifyAll();
        }

        public synchronized void wait(int millis) throws InterruptedException {
            if (cellInfo == null) {
                super.wait(millis);
            }
        }
    }

    private boolean isCamped() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PHONE_STATE");

        ServiceState ss = mTm.getServiceState();
        if (ss == null) return false;
        if (ss.getState() == ServiceState.STATE_EMERGENCY_ONLY) return true;
        List<NetworkRegistrationInfo> nris = ss.getNetworkRegistrationInfoList();
        for (NetworkRegistrationInfo nri : nris) {
            if (nri.getTransportType() != AccessNetworkConstants.TRANSPORT_TYPE_WWAN) continue;
            if (nri.isRegistered()) return true;
        }
        return false;
    }

    @Before
    public void setUp() throws Exception {
        mPm = getContext().getPackageManager();
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        } else {
            assumeTrue(mPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        }

        mTm = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        Pair<Integer, Integer> verPair =
                mTm.getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK);
        mNetworkHalVersion = makeRadioVersion(verPair.first, verPair.second);
        TelephonyManagerTest.grantLocationPermissions();
    }

    /**
     * Test to ensure that the PhoneStateListener receives callbacks every time that new CellInfo
     * is received and not otherwise.
     */
    @Test
    public void testPhoneStateListenerCallback() throws Throwable {
        CellInfoResultsCallback resultsCallback = new CellInfoResultsCallback();
        // Prime the system by requesting a CellInfoUpdate
        mTm.requestCellInfoUpdate(mSimpleExecutor, resultsCallback);
        resultsCallback.wait(MAX_CELLINFO_WAIT_MILLIS);
        // Register a new PhoneStateListener for CellInfo
        CellInfoListener listener = new CellInfoListener(mSimpleExecutor);
        mTm.listen(listener, PhoneStateListener.LISTEN_CELL_INFO);
        // Expect a callback immediately upon registration
        listener.wait(MAX_LISTENER_WAIT_MILLIS);
        assertNotNull("CellInfo Listener Never Fired on Registration", listener.cellInfo);
        // Save the initial listener result as a baseline
        List<CellInfo> referenceList = listener.cellInfo;
        assertFalse("CellInfo does not contain valid results", referenceList.isEmpty());
        assertTrue("Listener Didn't Receive the Right Data",
                referenceList.containsAll(resultsCallback.cellInfo));
        listener.cellInfo = null;
        resultsCallback.cellInfo = null;
        long timeoutTime = SystemClock.elapsedRealtime() + MAX_CELLINFO_INTERVAL_MILLIS;
        while (timeoutTime > SystemClock.elapsedRealtime()) {
            // Request a CellInfo update to try and coax an update from the listener
            mTm.requestCellInfoUpdate(mSimpleExecutor, resultsCallback);
            resultsCallback.wait(MAX_CELLINFO_WAIT_MILLIS);
            assertNotNull("CellInfoCallback should return valid data", resultsCallback.cellInfo);
            if (referenceList.containsAll(resultsCallback.cellInfo)) {
                // Check the a call to getAllCellInfo doesn't trigger the listener.
                mTm.getAllCellInfo();
                // Wait for the listener to fire; it shouldn't.
                listener.wait(MAX_LISTENER_WAIT_MILLIS);
                // Check to ensure the listener didn't fire for stale data.
                assertNull("PhoneStateListener Fired For Old CellInfo Data", listener.cellInfo);
            } else {
                // If there is new CellInfo data, then the listener should fire
                listener.wait(MAX_LISTENER_WAIT_MILLIS);
                assertNotNull("Listener did not receive updated CellInfo Data",
                        listener.cellInfo);
                assertFalse("CellInfo data should be different from the old listener data."
                        + referenceList + " : " + listener.cellInfo,
                        referenceList.containsAll(listener.cellInfo));
                return; // pass the test
            }
            // Reset the resultsCallback for the next iteration
            resultsCallback.cellInfo = null;
        }
    }

    @Test
    public void testCellInfo() throws Throwable {
        if (!isCamped()) fail("Device is not camped to a cell");

        // Make a blocking call to requestCellInfoUpdate for results (for simplicity of test).
        CellInfoResultsCallback resultsCallback = new CellInfoResultsCallback();
        mTm.requestCellInfoUpdate(mSimpleExecutor, resultsCallback);
        resultsCallback.wait(MAX_CELLINFO_WAIT_MILLIS);
        List<CellInfo> allCellInfo = resultsCallback.cellInfo;

        assertNotNull("TelephonyManager.getAllCellInfo() returned NULL!", allCellInfo);
        assertTrue("TelephonyManager.getAllCellInfo() returned zero-length list!",
            allCellInfo.size() > 0);

        int numRegisteredCells = 0;
        for (CellInfo cellInfo : allCellInfo) {
            if (cellInfo.isRegistered()) {
                ++numRegisteredCells;
            }
            verifyBaseCellInfo(cellInfo);
            verifyBaseCellIdentity(cellInfo.getCellIdentity(), cellInfo.isRegistered());
            if (cellInfo instanceof CellInfoLte) {
                verifyLteInfo((CellInfoLte) cellInfo);
            } else if (cellInfo instanceof CellInfoWcdma) {
                verifyWcdmaInfo((CellInfoWcdma) cellInfo);
            } else if (cellInfo instanceof CellInfoGsm) {
                verifyGsmInfo((CellInfoGsm) cellInfo);
            } else if (cellInfo instanceof CellInfoCdma) {
                verifyCdmaInfo((CellInfoCdma) cellInfo);
            } else if (cellInfo instanceof CellInfoTdscdma) {
                verifyTdscdmaInfo((CellInfoTdscdma) cellInfo);
            } else if (cellInfo instanceof CellInfoNr) {
                verifyNrInfo((CellInfoNr) cellInfo);
            } else {
                fail("Unknown CellInfo Type reported.");
            }
        }

        //FIXME: The maximum needs to be calculated based on the number of
        //       radios and the technologies used (ex SRLTE); however, we have
        //       not hit any of these cases yet.
        assertTrue("None or too many registered cells : " + numRegisteredCells,
                numRegisteredCells > 0 && numRegisteredCells <= 2);
    }

    private void verifyBaseCellInfo(CellInfo info) {
        assertTrue("Invalid timestamp in CellInfo: " + info.getTimeStamp(),
                info.getTimeStamp() > 0 && info.getTimeStamp() < Long.MAX_VALUE);

        long curTime = SystemClock.elapsedRealtime();
        assertTrue("Invalid timestamp in CellInfo: " + info.getTimestampMillis(),
                info.getTimestampMillis() > 0 && info.getTimestampMillis() <= curTime);

        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_2) {
            // In HAL 1.2 or greater, the connection status must be reported
            assertTrue(info.getCellConnectionStatus() != CellInfo.CONNECTION_UNKNOWN);
        }
    }

    private void verifyBaseCellIdentity(CellIdentity id, boolean isRegistered) {
        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_2) {
            if (isRegistered) {
                String alphaLong = (String) id.getOperatorAlphaLong();
                assertNotNull("getOperatorAlphaLong() returns NULL!", alphaLong);

                String alphaShort = (String) id.getOperatorAlphaShort();
                assertNotNull("getOperatorAlphaShort() returns NULL!", alphaShort);
            }
        }
    }

    private void verifyCdmaInfo(CellInfoCdma cdma) {
        verifyCellConnectionStatus(cdma.getCellConnectionStatus());
        verifyCellInfoCdmaParcelandHashcode(cdma);
        verifyCellIdentityCdma(cdma.getCellIdentity(), cdma.isRegistered());
        verifyCellIdentityCdmaParcel(cdma.getCellIdentity());
        verifyCellSignalStrengthCdma(cdma.getCellSignalStrength());
        verifyCellSignalStrengthCdmaParcel(cdma.getCellSignalStrength());
    }

    private void verifyCellInfoCdmaParcelandHashcode(CellInfoCdma cdma) {
        Parcel p = Parcel.obtain();
        cdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellInfoCdma newCi = CellInfoCdma.CREATOR.createFromParcel(p);
        assertTrue(cdma.equals(newCi));
        assertEquals("hashCode() did not get right hashCode", cdma.hashCode(), newCi.hashCode());
    }

    private void verifyCellIdentityCdma(CellIdentityCdma cdma, boolean isRegistered) {
        int networkId = cdma.getNetworkId();
        assertTrue("getNetworkId() out of range [0,65535], networkId=" + networkId,
                networkId == CellInfo.UNAVAILABLE || (networkId >= 0 && networkId <= NETWORK_ID));

        int systemId = cdma.getSystemId();
        assertTrue("getSystemId() out of range [0,32767], systemId=" + systemId,
                systemId == CellInfo.UNAVAILABLE || (systemId >= 0 && systemId <= SYSTEM_ID));

        int basestationId = cdma.getBasestationId();
        assertTrue("getBasestationId() out of range [0,65535], basestationId=" + basestationId,
                basestationId == CellInfo.UNAVAILABLE
                        || (basestationId >= 0 && basestationId <= BASESTATION_ID));

        int longitude = cdma.getLongitude();
        assertTrue("getLongitude() out of range [-2592000,2592000], longitude=" + longitude,
                longitude == CellInfo.UNAVAILABLE
                        || (longitude >= -LONGITUDE && longitude <= LONGITUDE));

        int latitude = cdma.getLatitude();
        assertTrue("getLatitude() out of range [-1296000,1296000], latitude=" + latitude,
                latitude == CellInfo.UNAVAILABLE
                        || (latitude >= -LATITUDE && latitude <= LATITUDE));

        if (isRegistered) {
            assertTrue("SID is required for registered cells", systemId != CellInfo.UNAVAILABLE);
            assertTrue("NID is required for registered cells", networkId != CellInfo.UNAVAILABLE);
            assertTrue("BSID is required for registered cells",
                    basestationId != CellInfo.UNAVAILABLE);
        }

        verifyCellIdentityCdmaLocationSanitation(cdma);
    }

    private void verifyCellIdentityCdmaLocationSanitation(CellIdentityCdma cdma) {
        CellIdentityCdma sanitized = cdma.sanitizeLocationInfo();
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getNetworkId());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getSystemId());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getBasestationId());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getLongitude());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getLatitude());
    }

    private void verifyCellIdentityCdmaParcel(CellIdentityCdma cdma) {
        Parcel p = Parcel.obtain();
        cdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityCdma newCi = CellIdentityCdma.CREATOR.createFromParcel(p);
        assertTrue(cdma.equals(newCi));
    }

    private void verifyCellSignalStrengthCdma(CellSignalStrengthCdma cdma) {
        int level = cdma.getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level,
                level >= 0 && level <= 4);

        int asuLevel = cdma.getAsuLevel();
        assertTrue("getAsuLevel() out of range [0,97] (or 99 is unknown), asuLevel=" + asuLevel,
                asuLevel == 99 || (asuLevel >= 0 && asuLevel <= 97));

        int cdmaLevel = cdma.getCdmaLevel();
        assertTrue("getCdmaLevel() out of range [0,4], cdmaLevel=" + cdmaLevel,
                cdmaLevel >= 0 && cdmaLevel <= 4);

        int evdoLevel = cdma.getEvdoLevel();
        assertTrue("getEvdoLevel() out of range [0,4], evdoLevel=" + evdoLevel,
                evdoLevel >= 0 && evdoLevel <= 4);

        // The following four fields do not have specific limits. So just calling to verify that
        // they don't crash the phone.
        int cdmaDbm = cdma.getCdmaDbm();
        int evdoDbm = cdma.getEvdoDbm();
        cdma.getCdmaEcio();
        cdma.getEvdoEcio();

        int dbm = (cdmaDbm < evdoDbm) ? cdmaDbm : evdoDbm;
        assertEquals("getDbm() did not get correct value", dbm, cdma.getDbm());

        int evdoSnr = cdma.getEvdoSnr();
        assertTrue("getEvdoSnr() out of range [0,8], evdoSnr=" + evdoSnr,
                (evdoSnr == CellInfo.UNAVAILABLE) || (evdoSnr >= 0 && evdoSnr <= 8));
    }

    private void verifyCellSignalStrengthCdmaParcel(CellSignalStrengthCdma cdma) {
        Parcel p = Parcel.obtain();
        cdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellSignalStrengthCdma newCss = CellSignalStrengthCdma.CREATOR.createFromParcel(p);
        assertEquals(cdma, newCss);
    }

    private static void verifyPlmnInfo(String mccStr, String mncStr, int mcc, int mnc) {
        // If either int value is invalid, all values must be invalid
        if (mcc == CellInfo.UNAVAILABLE) {
            assertTrue("MNC and MNC must always be reported together.",
                    mnc == CellInfo.UNAVAILABLE && mccStr == null && mncStr == null);
            return;
        }

        assertTrue("getMcc() out of range [0, 999], mcc=" + mcc, (mcc >= 0 && mcc <= 999));
        assertTrue("getMnc() out of range [0, 999], mnc=" + mnc, (mnc >= 0 && mnc <= 999));
        assertTrue("MCC and MNC Strings must always be reported together.",
                (mccStr == null) == (mncStr == null));

        // For legacy compatibility, it's possible to have int values without valid string values
        // but not the other way around.
        // mccStr is set as NULL if empty, unknown or invalid.
        assertTrue("getMccString() out of range [0, 999], mcc=" + mccStr,
                mccStr == null || mccStr.matches("^[0-9]{3}$"));
        // mccStr must either be null or match mcc integer.
        assertTrue("MccString must match Mcc Integer, str=" + mccStr + " int=" + mcc,
                mccStr == null || mcc == Integer.parseInt(mccStr));

        // mncStr is set as NULL if empty, unknown or invalid.
        assertTrue("getMncString() out of range [0, 999], mnc=" + mncStr,
                mncStr == null || mncStr.matches("^[0-9]{2,3}$"));
        // mncStr must either be null or match mnc integer.
        assertTrue("MncString must match Mnc Integer, str=" + mncStr + " int=" + mnc,
                mncStr == null || mnc == Integer.parseInt(mncStr));
    }

    // Verify lte cell information is within correct range.
    private void verifyLteInfo(CellInfoLte lte) {
        verifyCellConnectionStatus(lte.getCellConnectionStatus());
        verifyCellInfoLteParcelandHashcode(lte);
        verifyCellIdentityLte(lte.getCellIdentity(), lte.isRegistered());
        verifyCellIdentityLteParcel(lte.getCellIdentity());
        verifyCellSignalStrengthLte(lte.getCellSignalStrength());
        verifyCellSignalStrengthLteParcel(lte.getCellSignalStrength());
    }

    // Verify NR 5G cell information is within correct range.
    private void verifyNrInfo(CellInfoNr nr) {
        verifyCellConnectionStatus(nr.getCellConnectionStatus());
        verifyCellIdentityNr((CellIdentityNr) nr.getCellIdentity(), nr.isRegistered());
        verifyCellIdentityNrParcel((CellIdentityNr) nr.getCellIdentity());
        verifyCellSignalStrengthNr((CellSignalStrengthNr) nr.getCellSignalStrength());
        verifyCellSignalStrengthNrParcel((CellSignalStrengthNr) nr.getCellSignalStrength());
    }

    private void verifyCellSignalStrengthNrParcel(CellSignalStrengthNr nr) {
        Parcel p = Parcel.obtain();
        nr.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellSignalStrengthNr newCss = CellSignalStrengthNr.CREATOR.createFromParcel(p);
        assertEquals(nr, newCss);
    }

    private void verifyCellIdentityNrParcel(CellIdentityNr nr) {
        Parcel p = Parcel.obtain();
        nr.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityNr newCi = CellIdentityNr.CREATOR.createFromParcel(p);
        assertEquals(nr, newCi);
    }

    private void verifyCellIdentityNr(CellIdentityNr nr, boolean isRegistered) {
        // This class was added after numeric mcc/mncs were no longer provided, so it lacks the
        // basic getMcc() and getMnc() - empty out those checks.
        String mccStr = nr.getMccString();
        String mncStr = nr.getMncString();
        verifyPlmnInfo(mccStr, mncStr,
                mccStr != null ? Integer.parseInt(mccStr) : CellInfo.UNAVAILABLE,
                mncStr != null ? Integer.parseInt(mncStr) : CellInfo.UNAVAILABLE);

        int pci = nr.getPci();
        assertTrue("getPci() out of range [0, 1007], pci = " + pci, 0 <= pci && pci <= 1007);

        int tac = nr.getTac();
        assertTrue("getTac() out of range [0, 16777215], tac = " + tac,
            (tac == Integer.MAX_VALUE) || (0 <= tac && tac <= 16777215));

        int nrArfcn = nr.getNrarfcn();
        assertTrue("getNrarfcn() out of range [0, 3279165], nrarfcn = " + nrArfcn,
                0 <= nrArfcn && nrArfcn <= 3279165);

        for (String plmnId : nr.getAdditionalPlmns()) {
            verifyPlmnId(plmnId);
        }

        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_5) {
            int[] bands = nr.getBands();

            for (int band: bands) {
                assertTrue("getBand out of range [1, 95] or [257, 261], band = " + band,
                        (band >= BAND_FR1_MIN_NR && band <= BAND_FR1_MAX_NR)
                        || (band >= BAND_FR2_MIN_NR && band <= BAND_FR2_MAX_NR));

                verifyCellIdentityNrBands(bands);
            }
        }

        // If the cell is reported as registered, then all the logical cell info must be reported
        if (isRegistered) {
            assertTrue("TAC is required for registered cells", tac != CellInfo.UNAVAILABLE);
            assertTrue("MCC is required for registered cells", nr.getMccString() != null);
            assertTrue("MNC is required for registered cells", nr.getMncString() != null);
        }

        verifyCellIdentityNrLocationSanitation(nr);
    }

    private void verifyCellIdentityNrLocationSanitation(CellIdentityNr nr) {
        CellIdentityNr sanitized = nr.sanitizeLocationInfo();
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getPci());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getTac());
        assertEquals(CellInfo.UNAVAILABLE_LONG, sanitized.getNci());
    }

    private void verifyCellSignalStrengthNr(CellSignalStrengthNr nr) {
        int csiRsrp = nr.getCsiRsrp();
        int csiRsrq = nr.getCsiRsrq();
        int csiSinr = nr.getSsSinr();
        int csiCqiTableIndex = nr.getCsiCqiTableIndex();
        List<Integer> csiCqiReport = nr.getCsiCqiReport();
        int ssRsrp = nr.getSsRsrp();
        int ssRsrq = nr.getSsRsrq();
        int ssSinr = nr.getSsSinr();
        int timingAdvance = nr.getTimingAdvanceMicros();

        assertTrue("getCsiRsrp() out of range [-140, -44] | Integer.MAX_INTEGER, csiRsrp = "
                        + csiRsrp, -140 <= csiRsrp && csiRsrp <= -44
                || csiRsrp == CellInfo.UNAVAILABLE);
        assertTrue("getCsiRsrq() out of range [-20, -3] | Integer.MAX_INTEGER, csiRsrq = "
                + csiRsrq, -20 <= csiRsrq && csiRsrq <= -3 || csiRsrq == CellInfo.UNAVAILABLE);
        assertTrue("getCsiSinr() out of range [-23, 40] | Integer.MAX_INTEGER, csiSinr = "
                + csiSinr, -23 <= csiSinr && csiSinr <= 40 || csiSinr == CellInfo.UNAVAILABLE);
        assertTrue("getCsiCqiTableIndex() out of range | CellInfo.UNAVAILABLE, csiCqiTableIndex="
                + csiCqiTableIndex, csiCqiTableIndex == CellInfo.UNAVAILABLE
                        || (csiCqiTableIndex >= CQI_TABLE_INDEX_MIN_NR
                                && csiCqiTableIndex <= CQI_TABLE_INDEX_MAX_NR));
        assertTrue("cqi in getCsiCqiReport() out of range | CellInfo.UNAVAILABLE, csiCqiReport="
                + csiCqiReport, csiCqiReport.stream().allMatch(
                        cqi -> cqi == CellInfo.UNAVAILABLE || (cqi >= MIN_CQI && cqi <= MAX_CQI)));
        assertTrue("getSsRsrp() out of range [-140, -44] | Integer.MAX_INTEGER, ssRsrp = "
                        + ssRsrp, -140 <= ssRsrp && ssRsrp <= -44
                || ssRsrp == CellInfo.UNAVAILABLE);
        assertTrue("getSsRsrq() out of range [-20, -3] | Integer.MAX_INTEGER, ssRsrq = "
                + ssRsrq, -20 <= ssRsrq && ssRsrq <= -3 || ssRsrq == CellInfo.UNAVAILABLE);
        assertTrue("getSsSinr() out of range [-23, 40] | Integer.MAX_INTEGER, ssSinr = "
                + ssSinr, -23 <= ssSinr && ssSinr <= 40 || ssSinr == CellInfo.UNAVAILABLE);
        assertTrue("getTimingAdvanceMicros() out of range [0, 1282] | Integer.MAX_INTEGER, "
                + "timingAdvance = " + timingAdvance, 0 <= timingAdvance && timingAdvance <= 1282
                || timingAdvance == CellInfo.UNAVAILABLE);
    }

    private void verifyCellIdentityNrBands(int[] nrBands) {
        //Verify the registered cell reports non-null band.
        assertTrue(nrBands != null);

        //Verify the registered cell reports at least one band.
        assertTrue(Arrays.stream(nrBands).anyMatch(band -> band > 0));
    }

    private void verifyCellInfoLteParcelandHashcode(CellInfoLte lte) {
        Parcel p = Parcel.obtain();
        lte.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellInfoLte newCi = CellInfoLte.CREATOR.createFromParcel(p);
        assertTrue(lte.equals(newCi));
        assertEquals("hashCode() did not get right hashCode", lte.hashCode(), newCi.hashCode());
    }

    private void verifyCellIdentityLte(CellIdentityLte lte, boolean isRegistered) {
        verifyPlmnInfo(lte.getMccString(), lte.getMncString(), lte.getMcc(), lte.getMnc());

        // Cell identity ranges from 0 to 268435456.
        int ci = lte.getCi();
        assertTrue("getCi() out of range [0,268435456], ci=" + ci,
                (ci == CellInfo.UNAVAILABLE) || (ci >= 0 && ci <= CI));

        // Verify LTE physical cell id information.
        // Only physical cell id is available for LTE neighbor.
        int pci = lte.getPci();
        // Physical cell id should be within [0, 503].
        assertTrue("getPci() out of range [0, 503], pci=" + pci, (pci >= 0 && pci <= PCI));

        // Tracking area code ranges from 0 to 65535.
        int tac = lte.getTac();
        assertTrue("getTac() out of range [0,65535], tac=" + tac,
                (tac == CellInfo.UNAVAILABLE) || (tac >= 0 && tac <= TAC));

        // Bandwidth ranges from 1400 to 20000
        int bw = lte.getBandwidth();
        assertTrue("getBandwidth out of range [1400, 20000] | Integer.Max_Value, bw=" + bw,
                bw == CellInfo.UNAVAILABLE || bw >= BANDWIDTH_LOW && bw <= BANDWIDTH_HIGH);

        int earfcn = lte.getEarfcn();
        // Reference 3GPP 36.101 Table 5.7.3-1
        // As per NOTE 1 in the table, although 0-6 are valid channel numbers for
        // LTE, the reported EARFCN is the center frequency, rendering these channels
        // out of the range of the narrowest 1.4Mhz deployment.
        int minEarfcn = 7;
        int maxEarfcn = EARFCN_MAX - 7;
        if (bw != CellInfo.UNAVAILABLE) {
            // The number of channels used by a cell is equal to the cell bandwidth divided
            // by the channel raster (bandwidth of a channel). The center channel is the channel
            // the n/2-th channel where n is the number of channels, and since it is the center
            // channel that is reported as the channel number for a cell, we can exclude any channel
            // numbers within a band that would place the bottom of a cell's bandwidth below the
            // edge of the band. For channel numbers in Band 1, the EARFCN numbering starts from
            // channel 0, which means that we can exclude from the valid range channels starting
            // from 0 and numbered less than half the total number of channels occupied by a cell.
            minEarfcn = bw / CHANNEL_RASTER_EUTRAN / 2;
            maxEarfcn = EARFCN_MAX - (bw / CHANNEL_RASTER_EUTRAN / 2);
        }
        assertTrue(
                "getEarfcn() out of range [" + minEarfcn + "," + maxEarfcn + "], earfcn=" + earfcn,
                (earfcn >= minEarfcn && earfcn <= maxEarfcn));

        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_5) {
            int[] bands = lte.getBands();

            for (int band: bands) {
                assertTrue("getBand out of range [1, 88], band = " + band,
                        band >= BAND_MIN_LTE && band <= BAND_MAX_LTE);

                verifyCellIdentityLteBands(bands);
            }
        }

        verifyPlmnId(lte.getMobileNetworkOperator());

        for (String plmnId : lte.getAdditionalPlmns()) {
            verifyPlmnId(plmnId);
        }

        verifyCsgInfo(lte.getClosedSubscriberGroupInfo());

        verifyCellIdentityLteLocationSanitation(lte);

        // If the cell is reported as registered, then all the logical cell info must be reported
        if (isRegistered) {
            assertTrue("TAC is required for registered cells", tac != CellInfo.UNAVAILABLE);
            assertTrue("CID is required for registered cells", ci != CellInfo.UNAVAILABLE);
            assertTrue("MCC is required for registered cells",
                    lte.getMccString() != null || lte.getMcc() != CellInfo.UNAVAILABLE);
            assertTrue("MNC is required for registered cells",
                    lte.getMncString() != null || lte.getMnc() != CellInfo.UNAVAILABLE);
            assertFalse("PLMN-ID is required for registered cells",
                    TextUtils.isEmpty(lte.getMobileNetworkOperator()));
        }
    }

    private void verifyCellIdentityLteLocationSanitation(CellIdentityLte lte) {
        CellIdentityLte sanitized = lte.sanitizeLocationInfo();
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getCi());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getEarfcn());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getPci());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getTac());
    }

    private void verifyCellIdentityLteParcel(CellIdentityLte lte) {
        Parcel p = Parcel.obtain();
        lte.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityLte newci = CellIdentityLte.CREATOR.createFromParcel(p);
        assertEquals(lte, newci);
    }

    private void verifyCellSignalStrengthLte(CellSignalStrengthLte cellSignalStrengthLte) {
        verifyRssiDbm(cellSignalStrengthLte.getDbm());

        //ICellInfo.UNAVAILABLE indicates an unavailable field
        int rsrp = cellSignalStrengthLte.getRsrp();
        // RSRP is being treated as RSSI in LTE (they are similar but not quite right)
        // so reusing the constants here.
        assertTrue("getRsrp() out of range, rsrp=" + rsrp, rsrp >= MIN_RSRP && rsrp <= MAX_RSRP);

        int rsrq = cellSignalStrengthLte.getRsrq();
        assertTrue("getRsrq() out of range | CellInfo.UNAVAILABLE, rsrq=" + rsrq,
                rsrq == CellInfo.UNAVAILABLE || (rsrq >= MIN_RSRQ && rsrq <= MAX_RSRQ));

        int rssi = cellSignalStrengthLte.getRssi();
        assertTrue("getRssi() out of range [-113, -51] or CellInfo.UNAVAILABLE if unknown, rssi="
                + rssi, rssi == CellInfo.UNAVAILABLE
                || (rssi >= MIN_LTE_RSSI && rssi <= MAX_LTE_RSSI));

        int rssnr = cellSignalStrengthLte.getRssnr();
        assertTrue("getRssnr() out of range | CellInfo.UNAVAILABLE, rssnr=" + rssnr,
                rssnr == CellInfo.UNAVAILABLE || (rssnr >= MIN_RSSNR && rssnr <= MAX_RSSNR));

        int cqiTableIndex = cellSignalStrengthLte.getCqiTableIndex();
        assertTrue("getCqiTableIndex() out of range | CellInfo.UNAVAILABLE, cqi=" + cqiTableIndex,
                cqiTableIndex == CellInfo.UNAVAILABLE || (cqiTableIndex >= CQI_TABLE_INDEX_MIN_LTE
                        && cqiTableIndex <= CQI_TABLE_INDEX_MAX_LTE));

        int cqi = cellSignalStrengthLte.getCqi();
        assertTrue("getCqi() out of range | CellInfo.UNAVAILABLE, cqi=" + cqi,
                cqi == CellInfo.UNAVAILABLE || (cqi >= MIN_CQI && cqi <= MAX_CQI));

        int ta = cellSignalStrengthLte.getTimingAdvance();
        assertTrue("getTimingAdvance() invalid [0-1282] | CellInfo.UNAVAILABLE, ta=" + ta,
                ta == CellInfo.UNAVAILABLE || (ta >= 0 && ta <= 1282));

        int level = cellSignalStrengthLte.getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level, level >= 0 && level <= 4);

        int asuLevel = cellSignalStrengthLte.getAsuLevel();
        assertTrue("getAsuLevel() out of range [0,97] (or 99 is unknown), asuLevel=" + asuLevel,
                (asuLevel == 99) || (asuLevel >= 0 && asuLevel <= 97));

        int timingAdvance = cellSignalStrengthLte.getTimingAdvance();
        assertTrue("getTimingAdvance() out of range [0,1282], timingAdvance=" + timingAdvance,
                timingAdvance == CellInfo.UNAVAILABLE
                        || (timingAdvance >= 0 && timingAdvance <= 1282));

        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_2) {
            assertTrue("RSRP Must be valid for LTE",
                    cellSignalStrengthLte.getRsrp() != CellInfo.UNAVAILABLE);
        }
    }

    private void verifyCellSignalStrengthLteParcel(CellSignalStrengthLte cellSignalStrengthLte) {
        Parcel p = Parcel.obtain();
        cellSignalStrengthLte.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellSignalStrengthLte newCss = CellSignalStrengthLte.CREATOR.createFromParcel(p);
        assertEquals(cellSignalStrengthLte, newCss);
    }

    private void verifyCellIdentityLteBands(int[] lteBands) {
        //Verify the registered cell reports non-null band.
        assertTrue(lteBands != null);

        //Verify the registered cell reports at least one band.
        assertTrue(Arrays.stream(lteBands).anyMatch(band -> band > 0));
    }

    // Verify wcdma cell information is within correct range.
    private void verifyWcdmaInfo(CellInfoWcdma wcdma) {
        verifyCellConnectionStatus(wcdma.getCellConnectionStatus());
        verifyCellInfoWcdmaParcelandHashcode(wcdma);
        verifyCellIdentityWcdma(wcdma.getCellIdentity(), wcdma.isRegistered());
        verifyCellIdentityWcdmaParcel(wcdma.getCellIdentity());
        verifyCellSignalStrengthWcdma(wcdma.getCellSignalStrength());
        verifyCellSignalStrengthWcdmaParcel(wcdma.getCellSignalStrength());
    }

    private void verifyCellInfoWcdmaParcelandHashcode(CellInfoWcdma wcdma) {
        Parcel p = Parcel.obtain();
        wcdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellInfoWcdma newCi = CellInfoWcdma.CREATOR.createFromParcel(p);
        assertTrue(wcdma.equals(newCi));
        assertEquals("hashCode() did not get right hashCode", wcdma.hashCode(), newCi.hashCode());
    }

    private void verifyCellIdentityWcdma(CellIdentityWcdma wcdma, boolean isRegistered) {
        verifyPlmnInfo(wcdma.getMccString(), wcdma.getMncString(), wcdma.getMcc(), wcdma.getMnc());

        int lac = wcdma.getLac();
        assertTrue("getLac() out of range [0, 65535], lac=" + lac,
                (lac >= 0 && lac <= LAC) || lac == CellInfo.UNAVAILABLE);

        int cid = wcdma.getCid();
        assertTrue("getCid() out of range [0, 268435455], cid=" + cid,
                (cid >= 0 && cid <= CID_UMTS) || cid == CellInfo.UNAVAILABLE);

        // Verify wcdma primary scrambling code information.
        // Primary scrambling code should be within [0, 511].
        int psc = wcdma.getPsc();
        assertTrue("getPsc() out of range [0, 511], psc=" + psc, psc >= 0 && psc <= PSC);

        verifyPlmnId(wcdma.getMobileNetworkOperator());

        int uarfcn = wcdma.getUarfcn();
        // Reference 3GPP 25.101 Table 5.2
        // From Appendix E.1, even though UARFCN is numbered from 400, the minumum
        // usable channel is 412 due to the fixed bandwidth of 5Mhz
        assertTrue("getUarfcn() out of range [412,11000], uarfcn=" + uarfcn,
                uarfcn >= 412 && uarfcn <= 11000);

        for (String plmnId : wcdma.getAdditionalPlmns()) {
            verifyPlmnId(plmnId);
        }

        verifyCsgInfo(wcdma.getClosedSubscriberGroupInfo());

        // If the cell is reported as registered, then all the logical cell info must be reported
        if (isRegistered) {
            assertTrue("LAC is required for registered cells", lac != CellInfo.UNAVAILABLE);
            assertTrue("CID is required for registered cells", cid != CellInfo.UNAVAILABLE);
            assertTrue("MCC is required for registered cells",
                    wcdma.getMccString() != null || wcdma.getMcc() != CellInfo.UNAVAILABLE);
            assertTrue("MNC is required for registered cells",
                    wcdma.getMncString() != null || wcdma.getMnc() != CellInfo.UNAVAILABLE);
            assertFalse("PLMN-ID is required for registered cells",
                    TextUtils.isEmpty(wcdma.getMobileNetworkOperator()));
        }

        verifyCellIdentityWcdmaLocationSanitation(wcdma);
    }

    private void verifyCellIdentityWcdmaLocationSanitation(CellIdentityWcdma wcdma) {
        CellIdentityWcdma sanitized = wcdma.sanitizeLocationInfo();
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getLac());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getCid());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getPsc());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getUarfcn());
    }

    private void verifyCellIdentityWcdmaParcel(CellIdentityWcdma wcdma) {
        Parcel p = Parcel.obtain();
        wcdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityWcdma newci = CellIdentityWcdma.CREATOR.createFromParcel(p);
        assertEquals(wcdma, newci);
    }

    private void verifyCellSignalStrengthWcdma(CellSignalStrengthWcdma wcdma) {
        verifyRssiDbm(wcdma.getDbm());

        // Dbm here does not have specific limits. So just calling to verify that it does not crash
        // the phone
        wcdma.getDbm();

        int asuLevel = wcdma.getAsuLevel();
        if (wcdma.getRscp() != CellInfo.UNAVAILABLE) {
            assertTrue("getAsuLevel() out of range 0..96, 255), asuLevel=" + asuLevel,
                    asuLevel == 255 || (asuLevel >= 0 && asuLevel <= 96));
        } else if (wcdma.getRssi() != CellInfo.UNAVAILABLE) {
            assertTrue("getAsuLevel() out of range 0..31, 99), asuLevel=" + asuLevel,
                    asuLevel == 99 || (asuLevel >= 0 && asuLevel <= 31));
        } else {
            assertTrue("getAsuLevel() out of range 0..96, 255), asuLevel=" + asuLevel,
                    asuLevel == 255);
        }

        int level = wcdma.getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level, level >= 0 && level <= 4);

        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_2) {
            assertTrue("RSCP Must be valid for WCDMA", wcdma.getRscp() != CellInfo.UNAVAILABLE);
        }

        int ecNo = wcdma.getEcNo();
        assertTrue("getEcNo() out of range [-24,1], EcNo=" + ecNo,
                (ecNo >= -24 && ecNo <= 1) || ecNo == CellInfo.UNAVAILABLE);
    }

    private void verifyCellSignalStrengthWcdmaParcel(CellSignalStrengthWcdma wcdma) {
        Parcel p = Parcel.obtain();
        wcdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellSignalStrengthWcdma newCss = CellSignalStrengthWcdma.CREATOR.createFromParcel(p);
        assertEquals(wcdma, newCss);
    }

    // Verify gsm cell information is within correct range.
    private void verifyGsmInfo(CellInfoGsm gsm) {
        verifyCellConnectionStatus(gsm.getCellConnectionStatus());
        verifyCellInfoWcdmaParcelandHashcode(gsm);
        verifyCellIdentityGsm(gsm.getCellIdentity(), gsm.isRegistered());
        verifyCellIdentityGsmParcel(gsm.getCellIdentity());
        verifyCellSignalStrengthGsm(gsm.getCellSignalStrength());
        verifyCellSignalStrengthGsmParcel(gsm.getCellSignalStrength());
    }

    private void verifyCellInfoWcdmaParcelandHashcode(CellInfoGsm gsm) {
        Parcel p = Parcel.obtain();
        gsm.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellInfoGsm newCi = CellInfoGsm.CREATOR.createFromParcel(p);
        assertTrue(gsm.equals(newCi));
        assertEquals("hashCode() did not get right hashCode", gsm.hashCode(), newCi.hashCode());
    }

    private void verifyCellIdentityGsm(CellIdentityGsm gsm, boolean isRegistered) {
        verifyPlmnInfo(gsm.getMccString(), gsm.getMncString(), gsm.getMcc(), gsm.getMnc());

        // Local area code and cellid should be with [0, 65535].
        int lac = gsm.getLac();
        assertTrue("getLac() out of range [0, 65535], lac=" + lac,
                lac == CellInfo.UNAVAILABLE || (lac >= 0 && lac <= LAC));
        int cid = gsm.getCid();
        assertTrue("getCid() out range [0, 65535], cid=" + cid,
                cid == CellInfo.UNAVAILABLE || (cid >= 0 && cid <= CID_GSM));

        int arfcn = gsm.getArfcn();
        // Reference 3GPP 45.005 Table 2-2
        assertTrue("getArfcn() out of range [0,1024], arfcn=" + arfcn,
                arfcn == CellInfo.UNAVAILABLE || (arfcn >= 0 && arfcn <= ARFCN));

        int bsic = gsm.getBsic();
        assertTrue("getBsic() out of range [0,63]", bsic >= 0 && bsic <= 63);

        for (String plmnId : gsm.getAdditionalPlmns()) {
            verifyPlmnId(plmnId);
        }
        verifyPlmnId(gsm.getMobileNetworkOperator());

        // If the cell is reported as registered, then all the logical cell info must be reported
        if (isRegistered) {
            assertTrue("LAC is required for registered cells", lac != CellInfo.UNAVAILABLE);
            assertTrue("CID is required for registered cells", cid != CellInfo.UNAVAILABLE);
            assertTrue("MCC is required for registered cells",
                    gsm.getMccString() != null || gsm.getMcc() != CellInfo.UNAVAILABLE);
            assertTrue("MNC is required for registered cells",
                    gsm.getMncString() != null || gsm.getMnc() != CellInfo.UNAVAILABLE);
            assertFalse("PLMN-ID is required for registered cells",
                    TextUtils.isEmpty(gsm.getMobileNetworkOperator()));
        }

        verifyCellIdentityGsmLocationSanitation(gsm);
    }

    private void verifyCellIdentityGsmLocationSanitation(CellIdentityGsm gms) {
        CellIdentityGsm sanitized = gms.sanitizeLocationInfo();
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getLac());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getCid());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getArfcn());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getBsic());
    }

    private void verifyCellIdentityGsmParcel(CellIdentityGsm gsm) {
        Parcel p = Parcel.obtain();
        gsm.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityGsm newci = CellIdentityGsm.CREATOR.createFromParcel(p);
        assertEquals(gsm, newci);
    }

    private void verifyCellSignalStrengthGsm(CellSignalStrengthGsm gsm) {
        verifyRssiDbm(gsm.getDbm());

        int level = gsm.getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level, level >= 0 && level <= 4);

        int ta = gsm.getTimingAdvance();
        assertTrue("getTimingAdvance() out of range [0,219] | CellInfo.UNAVAILABLE, ta=" + ta,
                ta == CellInfo.UNAVAILABLE || (ta >= 0 && ta <= 219));

        assertEquals(gsm.getDbm(), gsm.getRssi());

        int asuLevel = gsm.getAsuLevel();
        assertTrue("getLevel() out of range [0,31] (or 99 is unknown), level=" + asuLevel,
                asuLevel == 99 || (asuLevel >=0 && asuLevel <= 31));

        int ber = gsm.getBitErrorRate();
        assertTrue("getBitErrorRate out of range [0,7], 99, or CellInfo.UNAVAILABLE, ber=" + ber,
                ber == 99 || ber == CellInfo.UNAVAILABLE || (ber >= 0 && ber <= 7));

        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_2) {
            assertTrue("RSSI Must be valid for GSM", gsm.getDbm() != CellInfo.UNAVAILABLE);
        }
    }

    private void verifyCellSignalStrengthGsmParcel(CellSignalStrengthGsm gsm) {
        Parcel p = Parcel.obtain();
        gsm.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellSignalStrengthGsm newCss = CellSignalStrengthGsm.CREATOR.createFromParcel(p);
        assertEquals(gsm, newCss);
    }

    // Verify tdscdma cell information is within correct range.
    private void verifyTdscdmaInfo(CellInfoTdscdma tdscdma) {
        verifyCellConnectionStatus(tdscdma.getCellConnectionStatus());
        verifyCellInfoTdscdmaParcelandHashcode(tdscdma);
        verifyCellIdentityTdscdma(tdscdma.getCellIdentity(), tdscdma.isRegistered());
        verifyCellIdentityTdscdmaParcel(tdscdma.getCellIdentity());
        verifyCellSignalStrengthTdscdma(tdscdma.getCellSignalStrength());
        verifyCellSignalStrengthTdscdmaParcel(tdscdma.getCellSignalStrength());
    }

    private void verifyCellInfoTdscdmaParcelandHashcode(CellInfoTdscdma tdscdma) {
        Parcel p = Parcel.obtain();
        tdscdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellInfoTdscdma newCi = CellInfoTdscdma.CREATOR.createFromParcel(p);
        assertTrue(tdscdma.equals(newCi));
        assertEquals("hashCode() did not get right hashCode", tdscdma.hashCode(), newCi.hashCode());
    }

    private void verifyCellIdentityTdscdma(CellIdentityTdscdma tdscdma, boolean isRegistered) {
        String mccStr = tdscdma.getMccString();
        String mncStr = tdscdma.getMncString();

        // This class was added after numeric mcc/mncs were no longer provided, so it lacks the
        // basic getMcc() and getMnc() - empty out those checks.
        verifyPlmnInfo(tdscdma.getMccString(), tdscdma.getMncString(),
                mccStr != null ? Integer.parseInt(mccStr) : CellInfo.UNAVAILABLE,
                mncStr != null ? Integer.parseInt(mncStr) : CellInfo.UNAVAILABLE);

        int lac = tdscdma.getLac();
        assertTrue("getLac() out of range [0, 65535], lac=" + lac,
                (lac >= 0 && lac <= LAC) || lac == CellInfo.UNAVAILABLE);

        int cid = tdscdma.getCid();
        assertTrue("getCid() out of range [0, 268435455], cid=" + cid,
                (cid >= 0 && cid <= CID_UMTS) || cid == CellInfo.UNAVAILABLE);

        // Verify tdscdma primary scrambling code information.
        // Primary scrambling code should be within [0, 511].
        int cpid = tdscdma.getCpid();
        assertTrue("getCpid() out of range [0, 127], cpid=" + cpid, (cpid >= 0 && cpid <= CPID));

        verifyPlmnId(tdscdma.getMobileNetworkOperator());

        int uarfcn = tdscdma.getUarfcn();
        // Reference 3GPP 25.101 Table 5.2
        // From Appendix E.1, even though UARFCN is numbered from 400, the minumum
        // usable channel is 412 due to the fixed bandwidth of 5Mhz
        assertTrue("getUarfcn() out of range [412,11000], uarfcn=" + uarfcn,
                uarfcn >= 412 && uarfcn <= 11000);

        for (String plmnId : tdscdma.getAdditionalPlmns()) {
            verifyPlmnId(plmnId);
        }

        verifyCsgInfo(tdscdma.getClosedSubscriberGroupInfo());

        // If the cell is reported as registered, then all the logical cell info must be reported
        if (isRegistered) {
            assertTrue("LAC is required for registered cells", lac != CellInfo.UNAVAILABLE);
            assertTrue("CID is required for registered cells", cid != CellInfo.UNAVAILABLE);
            assertTrue("MCC is required for registered cells", tdscdma.getMccString() != null);
            assertTrue("MNC is required for registered cells", tdscdma.getMncString() != null);
            assertFalse("PLMN-ID is required for registered cells",
                    TextUtils.isEmpty(tdscdma.getMobileNetworkOperator()));
        }

        verifyCellIdentityTdscdmaLocationSanitation(tdscdma);
    }

    private void verifyCellIdentityTdscdmaLocationSanitation(CellIdentityTdscdma tdscdma) {
        CellIdentityTdscdma sanitized = tdscdma.sanitizeLocationInfo();
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getLac());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getCid());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getCpid());
        assertEquals(CellInfo.UNAVAILABLE, sanitized.getUarfcn());
    }

    private void verifyCellIdentityTdscdmaParcel(CellIdentityTdscdma tdscdma) {
        Parcel p = Parcel.obtain();
        tdscdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityTdscdma newci = CellIdentityTdscdma.CREATOR.createFromParcel(p);
        assertEquals(tdscdma, newci);
    }

    private void verifyCellSignalStrengthTdscdma(CellSignalStrengthTdscdma tdscdma) {
        verifyRssiDbm(tdscdma.getDbm());

        // Dbm here does not have specific limits. So just calling to verify that it does not crash
        // the phone
        tdscdma.getDbm();

        int asuLevel = tdscdma.getAsuLevel();
        assertTrue("getLevel() out of range [0,31] (or 99 is unknown), level=" + asuLevel,
                asuLevel == 99 || (asuLevel >= 0 && asuLevel <= 31));

        int level = tdscdma.getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level, level >= 0 && level <= 4);

        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_2) {
            assertTrue("RSCP Must be valid for TDSCDMA", tdscdma.getRscp() != CellInfo.UNAVAILABLE);
        }
    }

    private void verifyCellSignalStrengthTdscdmaParcel(CellSignalStrengthTdscdma tdscdma) {
        Parcel p = Parcel.obtain();
        tdscdma.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellSignalStrengthTdscdma newCss = CellSignalStrengthTdscdma.CREATOR.createFromParcel(p);
        assertEquals(tdscdma, newCss);
    }

    // Rssi(in dbm) should be within [MIN_RSSI, MAX_RSSI].
    private static void verifyRssiDbm(int dbm) {
        assertTrue("getCellSignalStrength().getDbm() out of range, dbm=" + dbm,
                dbm >= MIN_RSSI && dbm <= MAX_RSSI);
    }

    private static void verifyCellConnectionStatus(int status) {
        assertTrue("getCellConnectionStatus() invalid [0,2] | Integer.MAX_VALUE, status=",
            status == CellInfo.CONNECTION_NONE
                || status == CellInfo.CONNECTION_PRIMARY_SERVING
                || status == CellInfo.CONNECTION_SECONDARY_SERVING
                || status == CellInfo.CONNECTION_UNKNOWN);
    }

    private static void verifyPlmnId(String plmnId) {
        if (TextUtils.isEmpty(plmnId)) return;

        assertTrue("PlmnId() out of range [00000 - 999999], PLMN ID=" + plmnId,
                plmnId.matches("^[0-9]{5,6}$"));
    }

    private static void verifyCsgInfo(@Nullable ClosedSubscriberGroupInfo csgInfo) {
        if (csgInfo == null) return;

        // This is boolean, so as long as it doesn't crash, we're good.
        csgInfo.getCsgIndicator();
        // This is nullable, and it's free-form so all we can do is ensure it doesn't crash.
        csgInfo.getHomeNodebName();

        // It might be technically possible to have a CSG ID of zero, but if that's the case
        // then let someone complain about it. It's far more likely that if it's '0', then there
        // is a bug.
        assertTrue("CSG Identity out of range", csgInfo.getCsgIdentity() > 0
                && csgInfo.getCsgIdentity() <= 0x7FFFFF);
    }
}
