/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.carrierapi.cts;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.NetworkScan;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyScanManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link TelephonyManager}'s network scan APIs.
 *
 * <p>Test using `atest CtsCarrierApiTestCases:NetworkScanApiTest` or `make cts -j64 && cts-tradefed
 * run cts -m CtsCarrierApiTestCases --test android.carrierapi.cts.NetworkScanApiTest`
 */
@RunWith(AndroidJUnit4.class)
public class NetworkScanApiTest extends BaseCarrierApiTest {
    private static final String TAG = "NetworkScanApiTest";

    private TelephonyManager mTelephonyManager;
    private int mNetworkScanStatus;
    private static final int EVENT_NETWORK_SCAN_START = 100;
    private static final int EVENT_NETWORK_SCAN_RENOUNCE_START = 101;
    private static final int EVENT_NETWORK_SCAN_RESULTS = 200;
    private static final int EVENT_NETWORK_SCAN_RESTRICTED_RESULTS = 201;
    private static final int EVENT_NETWORK_SCAN_ERROR = 300;
    private static final int EVENT_NETWORK_SCAN_COMPLETED = 400;
    private static final int EVENT_SCAN_DENIED = 500;
    private List<CellInfo> mScanResults = null;
    private HandlerThread mTestHandlerThread;
    private Handler mHandler;
    private NetworkScan mNetworkScan;
    private NetworkScanRequest mNetworkScanRequest;
    private NetworkScanCallbackImpl mNetworkScanCallback;
    private static final int LOCATION_SETTING_CHANGE_WAIT_MS = 1000;
    private static final int MAX_CELLINFO_WAIT_MILLIS = 5000; // 5 seconds
    private static final int SCAN_SEARCH_TIME_SECONDS = 60;
    // Wait one second longer than the max scan search time to give the test time to receive the
    // results.
    private static final int MAX_INIT_WAIT_MS = (SCAN_SEARCH_TIME_SECONDS + 1) * 1000;
    private Object mLock = new Object();
    private boolean mReady;
    private int mErrorCode;
    /* All the following constants are used to construct NetworkScanRequest*/
    private static final int SCAN_TYPE = NetworkScanRequest.SCAN_TYPE_ONE_SHOT;
    private static final boolean INCREMENTAL_RESULTS = true;
    private static final int SEARCH_PERIODICITY_SEC = 5;
    private static final int MAX_SEARCH_TIME_SEC = 300;
    private static final int INCREMENTAL_RESULTS_PERIODICITY_SEC = 3;
    private static final ArrayList<String> MCC_MNC = new ArrayList<>();
    private static final RadioAccessSpecifier[] RADIO_ACCESS_SPECIFIERS = {
        new RadioAccessSpecifier(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                null /* bands */,
                null /* channels */),
        new RadioAccessSpecifier(
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                null /* bands */,
                null /* channels */),
        new RadioAccessSpecifier(
                AccessNetworkConstants.AccessNetworkType.UTRAN,
                null /* bands */,
                null /* channels */)
    };

    // Needed because NETWORK_SCAN_PERMISSION is a systemapi
    public static final String NETWORK_SCAN_PERMISSION = "android.permission.NETWORK_SCAN";

    @Before
    public void setUp() throws Exception {
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class);
        String selfPackageName = getContext().getPackageName();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .grantRuntimePermission(selfPackageName, ACCESS_FINE_LOCATION);
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .grantRuntimePermission(selfPackageName, ACCESS_BACKGROUND_LOCATION);
        mTestHandlerThread = new HandlerThread(TAG);
        mTestHandlerThread.start();
        /* create a custom handler for the Handler Thread */
        mHandler =
                new Handler(mTestHandlerThread.getLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case EVENT_NETWORK_SCAN_START: {
                                Log.d(TAG, "request network scan");
                                boolean useShellIdentity = (Boolean) msg.obj;
                                if (useShellIdentity) {
                                    InstrumentationRegistry.getInstrumentation()
                                            .getUiAutomation()
                                            .adoptShellPermissionIdentity();
                                }
                                try {
                                    mNetworkScan =
                                            mTelephonyManager.requestNetworkScan(
                                                    mNetworkScanRequest,
                                                    AsyncTask.SERIAL_EXECUTOR,
                                                    mNetworkScanCallback);
                                    if (mNetworkScan == null) {
                                        mNetworkScanStatus = EVENT_SCAN_DENIED;
                                        setReady(true);
                                    }
                                } catch (SecurityException e) {
                                    mNetworkScanStatus = EVENT_SCAN_DENIED;
                                    setReady(true);
                                } finally {
                                    if (useShellIdentity) {
                                        InstrumentationRegistry.getInstrumentation()
                                                .getUiAutomation()
                                                .dropShellPermissionIdentity();
                                    }
                                }
                                break;
                            }
                            case EVENT_NETWORK_SCAN_RENOUNCE_START: {
                                Log.d(TAG, "request network scan with renounce");
                                boolean useShellIdentity = (Boolean) msg.obj;
                                if (useShellIdentity) {
                                    InstrumentationRegistry.getInstrumentation()
                                            .getUiAutomation()
                                            .adoptShellPermissionIdentity();
                                }
                                try {
                                    mNetworkScan = mTelephonyManager.requestNetworkScan(
                                            TelephonyManager.INCLUDE_LOCATION_DATA_NONE,
                                            mNetworkScanRequest,
                                            AsyncTask.SERIAL_EXECUTOR,
                                            mNetworkScanCallback);
                                    if (mNetworkScan == null) {
                                        mNetworkScanStatus = EVENT_SCAN_DENIED;
                                        setReady(true);
                                    }
                                } catch (SecurityException e) {
                                    mNetworkScanStatus = EVENT_SCAN_DENIED;
                                    setReady(true);
                                } finally {
                                    if (useShellIdentity) {
                                        InstrumentationRegistry.getInstrumentation()
                                                .getUiAutomation()
                                                .dropShellPermissionIdentity();
                                    }
                                }
                                break;
                            }
                            default:
                                Log.d(TAG, "Unknown Event " + msg.what);
                        }
                    }
                };
    }

    @After
    public void tearDown() throws Exception {
        if (!werePreconditionsSatisfied()) return;

        // Revoking runtime permissions makes ActivityManager kill our process, so we don't do it,
        // as the test harness will eventually uninstall this APK after testing completes anyway, so
        // we aren't really leaking anything long-term.
        mTestHandlerThread.quit();
    }

    private void waitUntilReady() {
        synchronized (mLock) {
            try {
                mLock.wait(MAX_INIT_WAIT_MS);
            } catch (InterruptedException ie) {
            }

            assertWithMessage("NetworkScanApiTest failed to initialize").that(mReady).isTrue();
        }
    }

    private void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
            mLock.notifyAll();
        }
    }

    private class NetworkScanCallbackImpl extends TelephonyScanManager.NetworkScanCallback {
        @Override
        public void onResults(List<CellInfo> results) {
            Log.d(TAG, "onResults: " + results.toString());
            mNetworkScanStatus = EVENT_NETWORK_SCAN_RESULTS;
            mScanResults = results;
        }

        @Override
        public void onComplete() {
            Log.d(TAG, "onComplete");
            mNetworkScanStatus = EVENT_NETWORK_SCAN_COMPLETED;
            setReady(true);
        }

        @Override
        public void onError(int error) {
            Log.d(TAG, "onError: " + String.valueOf(error));
            mNetworkScanStatus = EVENT_NETWORK_SCAN_ERROR;
            mErrorCode = error;
            setReady(true);
        }
    }

    private class CellInfoResultsCallback extends TelephonyManager.CellInfoCallback {
        public List<CellInfo> cellInfo;

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

    private List<RadioAccessSpecifier> getRadioAccessSpecifier(List<CellInfo> allCellInfo) {
        List<RadioAccessSpecifier> radioAccessSpecifier = new ArrayList<>();
        List<Integer> lteChannels = new ArrayList<>();
        List<Integer> wcdmaChannels = new ArrayList<>();
        List<Integer> gsmChannels = new ArrayList<>();
        for (int i = 0; i < allCellInfo.size(); i++) {
            CellInfo cellInfo = allCellInfo.get(i);
            if (cellInfo instanceof CellInfoLte) {
                lteChannels.add(((CellInfoLte) cellInfo).getCellIdentity().getEarfcn());
            } else if (cellInfo instanceof CellInfoWcdma) {
                wcdmaChannels.add(((CellInfoWcdma) cellInfo).getCellIdentity().getUarfcn());
            } else if (cellInfo instanceof CellInfoGsm) {
                gsmChannels.add(((CellInfoGsm) cellInfo).getCellIdentity().getArfcn());
            }
        }
        if (!lteChannels.isEmpty()) {
            Log.d(TAG, "lte channels" + lteChannels.toString());
            int ranLte = AccessNetworkConstants.AccessNetworkType.EUTRAN;
            radioAccessSpecifier.add(
                    new RadioAccessSpecifier(
                            ranLte,
                            null /* bands */,
                            lteChannels.stream().mapToInt(i -> i).toArray()));
        }
        if (!wcdmaChannels.isEmpty()) {
            Log.d(TAG, "wcdma channels" + wcdmaChannels.toString());
            int ranWcdma = AccessNetworkConstants.AccessNetworkType.UTRAN;
            radioAccessSpecifier.add(
                    new RadioAccessSpecifier(
                            ranWcdma,
                            null /* bands */,
                            wcdmaChannels.stream().mapToInt(i -> i).toArray()));
        }
        if (!gsmChannels.isEmpty()) {
            Log.d(TAG, "gsm channels" + gsmChannels.toString());
            int ranGsm = AccessNetworkConstants.AccessNetworkType.GERAN;
            radioAccessSpecifier.add(
                    new RadioAccessSpecifier(
                            ranGsm,
                            null /* bands */,
                            gsmChannels.stream().mapToInt(i -> i).toArray()));
        }
        return radioAccessSpecifier;
    }

    /** Tests that the device properly requests a network scan. */
    @Test
    public void testRequestNetworkScan() {
        boolean isLocationSwitchOn = getAndSetLocationSwitch(true);
        try {
            mNetworkScanRequest = buildNetworkScanRequest(true);
            mNetworkScanCallback = new NetworkScanCallbackImpl();
            Message startNetworkScan = mHandler.obtainMessage(EVENT_NETWORK_SCAN_START, false);
            setReady(false);
            startNetworkScan.sendToTarget();
            waitUntilReady();

            Log.d(TAG, "mNetworkScanStatus: " + mNetworkScanStatus);
            assertWithMessage(
                            "The final scan status is "
                                    + mNetworkScanStatus
                                    + " with error code "
                                    + mErrorCode
                                    + ", not ScanCompleted"
                                    + " or ScanError with an error code ERROR_MODEM_UNAVAILABLE or"
                                    + " ERROR_UNSUPPORTED")
                    .that(isScanStatusValid())
                    .isTrue();
        } finally {
            getAndSetLocationSwitch(isLocationSwitchOn);
        }
    }

    /** Tests that the device properly requests a network scan. */
    @Test
    public void testRequestNetworkScanWithRenounce() {
        boolean isLocationSwitchOn = getAndSetLocationSwitch(true);
        try {
            mNetworkScanRequest = buildNetworkScanRequest(true);
            mNetworkScanCallback = new NetworkScanCallbackImpl();
            Message startNetworkScan = mHandler.obtainMessage(EVENT_NETWORK_SCAN_RENOUNCE_START,
                    false);
            setReady(false);
            startNetworkScan.sendToTarget();
            waitUntilReady();

            Log.d(TAG, "mNetworkScanStatus: " + mNetworkScanStatus);
            assertWithMessage(
                    "The final scan status is "
                            + mNetworkScanStatus
                            + " with error code "
                            + mErrorCode
                            + ", not ScanCompleted"
                            + " or ScanError with an error code ERROR_MODEM_UNAVAILABLE or"
                            + " ERROR_UNSUPPORTED")
                    .that(mNetworkScanStatus)
                    .isEqualTo(EVENT_SCAN_DENIED);
        } finally {
            getAndSetLocationSwitch(isLocationSwitchOn);
        }
    }

    @Test
    public void testRequestNetworkScanWithRenounceWithoutChannels() {
        boolean isLocationSwitchOn = getAndSetLocationSwitch(true);
        try {
            mNetworkScanRequest = buildNetworkScanRequest(/*includeBandsAndChannels=*/false);
            mNetworkScanCallback = new NetworkScanCallbackImpl();
            Message startNetworkScan = mHandler.obtainMessage(EVENT_NETWORK_SCAN_RENOUNCE_START,
                    false);
            setReady(false);
            startNetworkScan.sendToTarget();
            waitUntilReady();

            Log.d(TAG, "mNetworkScanStatus: " + mNetworkScanStatus);
            assertWithMessage(
                    "The final scan status is "
                            + mNetworkScanStatus
                            + " with error code "
                            + mErrorCode
                            + ", not ScanCompleted"
                            + " or ScanError with an error code ERROR_MODEM_UNAVAILABLE or"
                            + " ERROR_UNSUPPORTED")
                    .that(isScanStatusValid())
                    .isTrue();
        } finally {
            getAndSetLocationSwitch(isLocationSwitchOn);
        }
    }

    @Test
    public void testRequestNetworkScanLocationOffPass() {
        requestNetworkScanLocationOffHelper(false, true);
    }

    @Test
    public void testRequestNetworkScanLocationOffFail() {
        requestNetworkScanLocationOffHelper(true, true);
    }

    public void requestNetworkScanLocationOffHelper(
            boolean includeBandsAndChannels, boolean useSpecialScanPermission) {
        mNetworkScanRequest = buildNetworkScanRequest(includeBandsAndChannels);

        boolean isLocationSwitchOn = getAndSetLocationSwitch(false);
        try {
            mNetworkScanCallback = new NetworkScanCallbackImpl();
            Message startNetworkScan =
                    mHandler.obtainMessage(EVENT_NETWORK_SCAN_START, useSpecialScanPermission);
            setReady(false);
            startNetworkScan.sendToTarget();
            waitUntilReady();
            if (includeBandsAndChannels) {
                // If we included the bands when location is off, expect a security error and
                // nothing else.
                assertThat(mNetworkScanStatus).isEqualTo(EVENT_SCAN_DENIED);
                return;
            }

            Log.d(TAG, "mNetworkScanStatus: " + mNetworkScanStatus);
            assertWithMessage(
                            "The final scan status is "
                                    + mNetworkScanStatus
                                    + " with error code "
                                    + mErrorCode
                                    + ", not ScanCompleted"
                                    + " or ScanError with an error code ERROR_MODEM_UNAVAILABLE or"
                                    + " ERROR_UNSUPPORTED")
                    .that(isScanStatusValid())
                    .isTrue();
        } finally {
            getAndSetLocationSwitch(isLocationSwitchOn);
        }
    }

    private NetworkScanRequest buildNetworkScanRequest(boolean includeBandsAndChannels) {
        // Make sure that there should be at least one entry.
        List<CellInfo> allCellInfo = getCellInfo();
        List<RadioAccessSpecifier> radioAccessSpecifier = new ArrayList<>();

        if (allCellInfo != null && allCellInfo.size() != 0) {
            // Construct a NetworkScanRequest
            radioAccessSpecifier = getRadioAccessSpecifier(allCellInfo);
            if (!includeBandsAndChannels) {
                radioAccessSpecifier =
                        radioAccessSpecifier.stream()
                                .map(
                                        spec ->
                                                new RadioAccessSpecifier(
                                                        spec.getRadioAccessNetwork(), null, null))
                                .collect(Collectors.toList());
            }
        }

        Log.d(TAG, "number of radioAccessSpecifier: " + radioAccessSpecifier.size());
        if (radioAccessSpecifier.isEmpty()) {
            // Put in some arbitrary bands and channels so that we trip the location check if needed
            int[] fakeBands =
                    includeBandsAndChannels
                            ? new int[] {AccessNetworkConstants.EutranBand.BAND_5}
                            : null;
            int[] fakeChannels = includeBandsAndChannels ? new int[] {2400} : null;

            RadioAccessSpecifier gsm =
                    new RadioAccessSpecifier(
                            AccessNetworkConstants.AccessNetworkType.GERAN,
                            null /* bands */,
                            null /* channels */);
            RadioAccessSpecifier lte =
                    new RadioAccessSpecifier(
                            AccessNetworkConstants.AccessNetworkType.EUTRAN,
                            fakeBands /* bands */,
                            fakeChannels /* channels */);
            RadioAccessSpecifier wcdma =
                    new RadioAccessSpecifier(
                            AccessNetworkConstants.AccessNetworkType.UTRAN,
                            null /* bands */,
                            null /* channels */);
            radioAccessSpecifier.add(gsm);
            radioAccessSpecifier.add(lte);
            radioAccessSpecifier.add(wcdma);
        }
        RadioAccessSpecifier[] radioAccessSpecifierArray =
                new RadioAccessSpecifier[radioAccessSpecifier.size()];
        return new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT /* scan type */,
                radioAccessSpecifier.toArray(radioAccessSpecifierArray),
                5 /* search periodicity */,
                SCAN_SEARCH_TIME_SECONDS /* max search time */,
                true /*enable incremental results*/,
                5 /* incremental results periodicity */,
                null /* List of PLMN ids (MCC-MNC) */);
    }

    private List<CellInfo> getCellInfo() {
        CellInfoResultsCallback resultsCallback = new CellInfoResultsCallback();
        mTelephonyManager.requestCellInfoUpdate(r -> r.run(), resultsCallback);
        try {
            resultsCallback.wait(MAX_CELLINFO_WAIT_MILLIS);
        } catch (InterruptedException ex) {
            fail("CellInfoCallback was interrupted: " + ex);
        }
        return resultsCallback.cellInfo;
    }

    @Test
    public void testNetworkScanPermission() {
        PackageManager pm = getContext().getPackageManager();

        List<Integer> specialUids =
                Arrays.asList(Process.SYSTEM_UID, Process.PHONE_UID, Process.SHELL_UID);

        List<PackageInfo> holding =
                pm.getPackagesHoldingPermissions(
                        new String[] {NETWORK_SCAN_PERMISSION},
                        PackageManager.MATCH_DISABLED_COMPONENTS);

        List<Integer> nonSpecialPackages =
                holding.stream()
                        .map(
                                pi -> {
                                    try {
                                        return pm.getPackageUid(pi.packageName, 0);
                                    } catch (PackageManager.NameNotFoundException e) {
                                        return Process.INVALID_UID;
                                    }
                                })
                        .filter(uid -> !specialUids.contains(UserHandle.getAppId(uid)))
                        .collect(Collectors.toList());

        assertWithMessage(
                        "Only one app on the device is allowed to hold the NETWORK_SCAN"
                                + " permission.")
                .that(nonSpecialPackages.size())
                .isAtMost(1);
    }

    private boolean getAndSetLocationSwitch(boolean enabled) {
        CountDownLatch locationChangeLatch = new CountDownLatch(1);
        BroadcastReceiver locationModeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (LocationManager.MODE_CHANGED_ACTION.equals(intent.getAction())
                        && intent.getBooleanExtra(LocationManager.EXTRA_LOCATION_ENABLED, !enabled)
                        == enabled) {
                    locationChangeLatch.countDown();
                }
            }
        };

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            Context context = InstrumentationRegistry.getContext();
            LocationManager lm = context.getSystemService(
                    LocationManager.class);
            boolean oldLocationOn = lm.isLocationEnabledForUser(
                    UserHandle.of(UserHandle.myUserId()));

            if (enabled != oldLocationOn) {
                context.registerReceiver(locationModeChangeReceiver,
                        new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
                lm.setLocationEnabledForUser(enabled, UserHandle.of(UserHandle.myUserId()));
                try {
                    assertThat(locationChangeLatch.await(LOCATION_SETTING_CHANGE_WAIT_MS,
                            TimeUnit.MILLISECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Log.w(NetworkScanApiTest.class.getSimpleName(),
                            "Interrupted while waiting for location settings change. Test results"
                                    + " may not be accurate.");
                } finally {
                    context.unregisterReceiver(locationModeChangeReceiver);
                }
            }
            return oldLocationOn;
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private boolean isScanStatusValid() {
        // TODO(b/72162885): test the size of ScanResults is not zero after the blocking bug fixed.
        if ((mNetworkScanStatus == EVENT_NETWORK_SCAN_COMPLETED) && (mScanResults != null)) {
            // Scan complete.
            return true;
        }
        if ((mNetworkScanStatus == EVENT_NETWORK_SCAN_ERROR)
                && ((mErrorCode == NetworkScan.ERROR_MODEM_UNAVAILABLE)
                        || (mErrorCode == NetworkScan.ERROR_UNSUPPORTED))) {
            // Scan error but the error type is allowed.
            return true;
        }
        return false;
    }

    private ArrayList<String> getPlmns() {
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310260");
        mccMncs.add("310120");
        return mccMncs;
    }

    /** To test its constructor and getters. */
    @Test
    public void testNetworkScanRequest_constructorAndGetters() {
        NetworkScanRequest networkScanRequest =
                new NetworkScanRequest(
                        SCAN_TYPE,
                        RADIO_ACCESS_SPECIFIERS,
                        SEARCH_PERIODICITY_SEC,
                        MAX_SEARCH_TIME_SEC,
                        INCREMENTAL_RESULTS,
                        INCREMENTAL_RESULTS_PERIODICITY_SEC,
                        getPlmns());

        assertWithMessage("getScanType() returns wrong value")
                .that(networkScanRequest.getScanType())
                .isEqualTo(SCAN_TYPE);
        assertWithMessage("getSpecifiers() returns wrong value")
                .that(networkScanRequest.getSpecifiers())
                .isEqualTo(RADIO_ACCESS_SPECIFIERS);
        assertWithMessage("getSearchPeriodicity() returns wrong value")
                .that(networkScanRequest.getSearchPeriodicity())
                .isEqualTo(SEARCH_PERIODICITY_SEC);
        assertWithMessage("getMaxSearchTime() returns wrong value")
                .that(networkScanRequest.getMaxSearchTime())
                .isEqualTo(MAX_SEARCH_TIME_SEC);
        assertWithMessage("getIncrementalResults() returns wrong value")
                .that(networkScanRequest.getIncrementalResults())
                .isEqualTo(INCREMENTAL_RESULTS);
        assertWithMessage("getIncrementalResultsPeriodicity() returns wrong value")
                .that(networkScanRequest.getIncrementalResultsPeriodicity())
                .isEqualTo(INCREMENTAL_RESULTS_PERIODICITY_SEC);
        assertWithMessage("getPlmns() returns wrong value")
                .that(networkScanRequest.getPlmns())
                .isEqualTo(getPlmns());
        assertWithMessage("describeContents() returns wrong value")
                .that(networkScanRequest.describeContents())
                .isEqualTo(0);
    }

    /** To test its hashCode method. */
    @Test
    public void testNetworkScanRequestParcel_hashCode() {
        NetworkScanRequest networkScanRequest1 =
                new NetworkScanRequest(
                        SCAN_TYPE,
                        RADIO_ACCESS_SPECIFIERS,
                        SEARCH_PERIODICITY_SEC,
                        MAX_SEARCH_TIME_SEC,
                        INCREMENTAL_RESULTS,
                        INCREMENTAL_RESULTS_PERIODICITY_SEC,
                        getPlmns());

        NetworkScanRequest networkScanRequest2 =
                new NetworkScanRequest(
                        SCAN_TYPE,
                        RADIO_ACCESS_SPECIFIERS,
                        SEARCH_PERIODICITY_SEC,
                        MAX_SEARCH_TIME_SEC,
                        INCREMENTAL_RESULTS,
                        INCREMENTAL_RESULTS_PERIODICITY_SEC,
                        getPlmns());

        NetworkScanRequest networkScanRequest3 =
                new NetworkScanRequest(
                        SCAN_TYPE,
                        null,
                        SEARCH_PERIODICITY_SEC,
                        MAX_SEARCH_TIME_SEC,
                        false,
                        0,
                        getPlmns());

        assertWithMessage("hashCode() returns different hash code for same objects")
                .that(networkScanRequest1.hashCode())
                .isEqualTo(networkScanRequest2.hashCode());
        assertWithMessage("hashCode() returns same hash code for different objects")
                .that(networkScanRequest1.hashCode())
                .isNotEqualTo(networkScanRequest3.hashCode());
    }

    /** To test its comparison method. */
    @Test
    public void testNetworkScanRequestParcel_equals() {
        NetworkScanRequest networkScanRequest1 =
                new NetworkScanRequest(
                        SCAN_TYPE,
                        RADIO_ACCESS_SPECIFIERS,
                        SEARCH_PERIODICITY_SEC,
                        MAX_SEARCH_TIME_SEC,
                        INCREMENTAL_RESULTS,
                        INCREMENTAL_RESULTS_PERIODICITY_SEC,
                        getPlmns());

        NetworkScanRequest networkScanRequest2 =
                new NetworkScanRequest(
                        SCAN_TYPE,
                        RADIO_ACCESS_SPECIFIERS,
                        SEARCH_PERIODICITY_SEC,
                        MAX_SEARCH_TIME_SEC,
                        INCREMENTAL_RESULTS,
                        INCREMENTAL_RESULTS_PERIODICITY_SEC,
                        getPlmns());

        assertThat(networkScanRequest1).isEqualTo(networkScanRequest2);

        networkScanRequest2 =
                new NetworkScanRequest(
                        SCAN_TYPE,
                        RADIO_ACCESS_SPECIFIERS,
                        SEARCH_PERIODICITY_SEC,
                        MAX_SEARCH_TIME_SEC,
                        INCREMENTAL_RESULTS,
                        INCREMENTAL_RESULTS_PERIODICITY_SEC,
                        null /* List of PLMN ids (MCC-MNC) */);
        assertThat(networkScanRequest1).isNotEqualTo(networkScanRequest2);
    }

    /** To test its writeToParcel and createFromParcel methods. */
    @Test
    public void testNetworkScanRequestParcel_parcel() {
        NetworkScanRequest networkScanRequest =
                new NetworkScanRequest(
                        SCAN_TYPE,
                        null /* Radio Access Specifier */,
                        SEARCH_PERIODICITY_SEC,
                        MAX_SEARCH_TIME_SEC,
                        INCREMENTAL_RESULTS,
                        INCREMENTAL_RESULTS_PERIODICITY_SEC,
                        getPlmns());

        Parcel p = Parcel.obtain();
        networkScanRequest.writeToParcel(p, 0);
        p.setDataPosition(0);
        NetworkScanRequest newnsr = NetworkScanRequest.CREATOR.createFromParcel(p);
        assertThat(networkScanRequest).isEqualTo(newnsr);
    }
}
