/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telephony.satellite.cts;

import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;

import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.radio.RadioError;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.TelephonyManagerTest.ServiceStateRadioStateListener;
import android.telephony.satellite.AntennaDirection;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.NTRadioTechnology;
import android.telephony.satellite.stub.SatelliteResult;
import android.util.Log;
import android.util.Pair;
import android.uwb.UwbManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.LocationUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.satellite.DatagramController;
import com.android.internal.telephony.satellite.SatelliteServiceUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SatelliteManagerTestOnMockService extends SatelliteManagerTestBase {
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final long TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS = 100;
    private static final long TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS = 60 * 10 * 1000;
    private static final long TEST_SATELLITE_DEVICE_ALIGN_TIMEOUT_MILLIS = 100;
    private static final long TEST_SATELLITE_DEVICE_ALIGN_FOREVER_TIMEOUT_MILLIS = 100000;

    private static MockSatelliteServiceManager sMockSatelliteServiceManager;

    /* SatelliteCapabilities constant indicating that the radio technology is proprietary. */
    private static final Set<Integer> SUPPORTED_RADIO_TECHNOLOGIES;
    static {
        SUPPORTED_RADIO_TECHNOLOGIES = new HashSet<>();
        SUPPORTED_RADIO_TECHNOLOGIES.add(SatelliteManager.NT_RADIO_TECHNOLOGY_PROPRIETARY);
    }

    /* SatelliteCapabilities constant indicating that pointing to satellite is required. */
    private static final boolean POINTING_TO_SATELLITE_REQUIRED = true;
    /* SatelliteCapabilities constant indicating the maximum number of characters per datagram. */
    private static final int MAX_BYTES_PER_DATAGRAM = 339;
    /* SatelliteCapabilites constant antenna position map received from satellite modem. */
    private static final Map<Integer, AntennaPosition> ANTENNA_POSITION_MAP;
    static {
        ANTENNA_POSITION_MAP = new HashMap<>();
        ANTENNA_POSITION_MAP.put(SatelliteManager.DISPLAY_MODE_OPENED,
                new AntennaPosition(new AntennaDirection(1,1,1),
                        SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT));
        ANTENNA_POSITION_MAP.put(SatelliteManager.DISPLAY_MODE_CLOSED,
                new AntennaPosition(new AntennaDirection(2,2,2),
                        SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT));
    }
    private static CarrierConfigReceiver sCarrierConfigReceiver;

    private static final int SUB_ID = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
    private static final String OVERRIDING_COUNTRY_CODES = "US";
    private static final String SATELLITE_COUNTRY_CODES = "US,UK,CA";
    private static final String SATELLITE_S2_FILE = "google_us_san_sat_s2.dat";
    private static final String TEST_PROVIDER = LocationManager.GPS_PROVIDER;
    private static final float LOCATION_ACCURACY = 95;
    private static LocationManager sLocationManager;

    BTWifiNFCStateReceiver mBTWifiNFCSateReceiver = null;
    UwbAdapterStateCallback mUwbAdapterStateCallback = null;
    private String mTestSatelliteModeRadios = null;
    boolean mBTInitState = false;
    boolean mWifiInitState = false;
    boolean mNfcInitState = false;
    boolean mUwbInitState = false;


    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd("beforeAllTests");

        if (!shouldTestSatelliteWithMockService()) return;

        beforeAllTestsBase();
        enforceMockModemDeveloperSetting();

        grantSatellitePermission();

        sMockSatelliteServiceManager = new MockSatelliteServiceManager(
                InstrumentationRegistry.getInstrumentation());
        setUpSatelliteAccessAllowed();
        setupMockSatelliteService();

        sCarrierConfigReceiver = new CarrierConfigReceiver(SUB_ID);
        sLocationManager = getContext().getSystemService(LocationManager.class);

        revokeSatellitePermission();
    }

    private static void setupMockSatelliteService() {
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        int count = 0;
        while (sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback)
                != SatelliteManager.SATELLITE_RESULT_SUCCESS
                && count < 10) {
            count++;
            waitFor(500);
        }
        assertTrue(callback.waitUntilResult(1));
        if (callback.modemState == SatelliteManager.SATELLITE_MODEM_STATE_OFF) {
            waitFor(2000);
        } else {
            assertTrue(callback.waitUntilModemOff(EXTERNAL_DEPENDENT_TIMEOUT));
        }
        sSatelliteManager.unregisterForModemStateChanged(callback);

        assertTrue(isSatelliteSupported());
        if (!isSatelliteProvisioned()) {
            logd("Provision satellite");

            SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                    new SatelliteProvisionStateCallbackTest();
            long registerError = sSatelliteManager.registerForProvisionStateChanged(
                    getContext().getMainExecutor(), satelliteProvisionStateCallback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

            assertTrue(provisionSatellite());

            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertTrue(satelliteProvisionStateCallback.isProvisioned);
            sSatelliteManager.unregisterForProvisionStateChanged(
                    satelliteProvisionStateCallback);
        }
    }

    @AfterClass
    public static void afterAllTests() {
        logd("afterAllTests");
        if (!shouldTestSatelliteWithMockService()) return;

        grantSatellitePermission();

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
        waitFor(2000);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        resetSatelliteAccessControlOverlayConfigs();
        afterAllTestsBase();
        sMockSatelliteServiceManager = null;
        sCarrierConfigReceiver = null;
        revokeSatellitePermission();
    }

    @Before
    public void setUp() throws Exception {
        logd("setUp");
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(sMockSatelliteServiceManager != null);

        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.mIsPointingUiOverridden = false;

        // Initialize radio state
        mBTInitState = false;
        mWifiInitState = false;
        mNfcInitState = false;
        mUwbInitState = false;
        mTestSatelliteModeRadios = "";

        SatelliteModeRadiosUpdater satelliteRadiosModeUpdater =
                new SatelliteModeRadiosUpdater(getContext());
        assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(""));

        grantSatellitePermission();
        if (!isSatelliteEnabled()) {
            logd("Enable satellite");

            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            int i = 0;
            while (requestSatelliteEnabledWithResult(true, EXTERNAL_DEPENDENT_TIMEOUT)
                    != SatelliteManager.SATELLITE_RESULT_SUCCESS && i < 3) {
                waitFor(500);
                i++;
            }

            assertTrue(callback.waitUntilResult(1));
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForModemStateChanged(callback);
        }
        revokeSatellitePermission();
    }

    @After
    public void tearDown() {
        logd("tearDown");
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(sMockSatelliteServiceManager != null);
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);

        grantSatellitePermission();
        if (isSatelliteEnabled()) {
            logd("Disable satellite");
            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            // Disable satellite modem to clean up all pending resources and reset telephony states.
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());

            sSatelliteManager.unregisterForModemStateChanged(callback);
        }
        sMockSatelliteServiceManager.restoreSatellitePointingUiClassName();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearListeningEnabledList();
        revokeSatellitePermission();
        sMockSatelliteServiceManager.mIsPointingUiOverridden = false;
    }

    @Test
    public void testProvisionSatelliteService() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testProvisionSatelliteService: start");
        grantSatellitePermission();

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = sSatelliteManager.registerForProvisionStateChanged(
                getContext().getMainExecutor(), satelliteProvisionStateCallback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        if (isSatelliteProvisioned()) {
            logd("testProvisionSatelliteService: dreprovision");
            assertTrue(deprovisionSatellite());
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertFalse(satelliteProvisionStateCallback.isProvisioned);
        }

        logd("testProvisionSatelliteService: provision and cancel");
        satelliteProvisionStateCallback.clearProvisionedStates();
        CancellationSignal cancellationSignal = new CancellationSignal();
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();
        sSatelliteManager.provisionService(TOKEN, testProvisionData, cancellationSignal,
                getContext().getMainExecutor(), error::offer);

        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            cancellationSignal.cancel();
        } catch (InterruptedException ex) {
            fail("testProvisionSatelliteService: Got InterruptedException ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);

        // Provision succeeded and then got canceled - deprovisioned
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(2));
        assertEquals(2, satelliteProvisionStateCallback.getTotalCountOfProvisionedStates());
        assertTrue(satelliteProvisionStateCallback.getProvisionedState(0));
        assertFalse(satelliteProvisionStateCallback.getProvisionedState(1));
        assertFalse(satelliteProvisionStateCallback.isProvisioned);
        assertFalse(isSatelliteProvisioned());

        logd("testProvisionSatelliteService: restore provision state");
        assertTrue(provisionSatellite());
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
        assertTrue(satelliteProvisionStateCallback.isProvisioned);
        sSatelliteManager.unregisterForProvisionStateChanged(
                satelliteProvisionStateCallback);

        revokeSatellitePermission();
    }

    @Test
    public void testProvisioningApiNotSupportedByVendorService() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testProvisioningApiNotSupportedByVendorService: start");
        grantSatellitePermission();

        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = sSatelliteManager.registerForProvisionStateChanged(
                getContext().getMainExecutor(), satelliteProvisionStateCallback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        if (isSatelliteProvisioned()) {
            logd("testProvisioningApiNotSupportedByVendorService: dreprovision");
            assertTrue(deprovisionSatellite());
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertFalse(satelliteProvisionStateCallback.isProvisioned);
        }

        sMockSatelliteServiceManager.setProvisioningApiSupported(false);

        logd("testProvisioningApiNotSupportedByVendorService: provision satellite service");
        assertTrue(provisionSatellite());
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
        assertTrue(satelliteProvisionStateCallback.isProvisioned);
        assertTrue(isSatelliteProvisioned());

        logd("testProvisioningApiNotSupportedByVendorService: dreprovision satellite service");
        assertTrue(deprovisionSatellite());
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
        assertFalse(satelliteProvisionStateCallback.isProvisioned);
        assertFalse(isSatelliteProvisioned());

        logd("testProvisioningApiNotSupportedByVendorService: restore provision state");
        sMockSatelliteServiceManager.setProvisioningApiSupported(true);
        assertTrue(provisionSatellite());
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
        assertTrue(satelliteProvisionStateCallback.isProvisioned);
        assertTrue(isSatelliteProvisioned());
        sSatelliteManager.unregisterForProvisionStateChanged(
                satelliteProvisionStateCallback);

        revokeSatellitePermission();
    }

    @Test
    public void testPointingUICrashHandling() {
        if (!shouldTestSatelliteWithMockService()) return;

        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.overrideExternalSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();

        // Start Pointing UI app
        sendSatelliteDatagramSuccess(false, false);

        // Forcefully stop the Pointing UI app
        sMockSatelliteServiceManager.clearStopPointingUiActivity();
        assertTrue(sMockSatelliteServiceManager.stopExternalMockPointingUi());
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStopped(1));
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        // Check if the Pointing UI app restarted
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));

        // Kill the Pointing UI app multiple times and check if it is restarted everytime
        for (int i = 0; i < 10; i++) {
            sMockSatelliteServiceManager.clearStopPointingUiActivity();
            // Forcefully stop the Pointing UI app again
            assertTrue(sMockSatelliteServiceManager.stopExternalMockPointingUi());
            assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStopped(1));
            sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
            // Check if the Pointing UI app has restarted
            assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));
        }
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
    }

    @Test
    public void testSatelliteModemStateChanged() {
        if (!shouldTestSatelliteWithMockService()) return;

        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        assertTrue(sMockSatelliteServiceManager.connectSatelliteGatewayService());
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        requestSatelliteEnabled(true);

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

        SatelliteModemStateCallbackTest
                callback1 = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager
                .registerForModemStateChanged(getContext().getMainExecutor(), callback1);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
        sSatelliteManager.unregisterForModemStateChanged(callback);

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

        // Verify state transitions: IDLE -> TRANSFERRING -> LISTENING -> IDLE
        sendSatelliteDatagramWithSuccessfulResult(callback1, true);

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

        // Move to LISTENING state
        sendSatelliteDatagramWithSuccessfulResult(callback1, false);

        // Verify state transitions: LISTENING -> TRANSFERRING -> LISTENING
        receiveSatelliteDatagramWithSuccessfulResult(callback1);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        sendSatelliteDatagramWithFailedResult(callback1);

        // Move to LISTENING state
        sendSatelliteDatagramWithSuccessfulResult(callback1, false);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        receiveSatelliteDatagramWithFailedResult(callback1);

        callback1.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(callback1.waitUntilModemOff());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback1.modemState);
        assertFalse(isSatelliteEnabled());
        assertTrue(
                sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceDisconnected(1));

        sSatelliteManager.unregisterForModemStateChanged(callback1);
        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemStateChangedForNbIot() {
        if (!shouldTestSatelliteWithMockService() || !Flags.oemEnabledSatelliteFlag()) return;

        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);

        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        assertTrue(sMockSatelliteServiceManager.connectSatelliteGatewayService());
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        assertTrue(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

        // Verify state transitions: OFF -> NOT_CONNECTED -> IDLE
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertTrue(isSatelliteEnabled());
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        callback.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

        // Verify state transitions when sending: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
        // -> CONNECTED -> IDLE
        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sendDatagramWithoutResponse();
        verifyNbIotStateTransitionsWithSendingOnConnected(callback, true);

        // Verify state transitions when receiving: IDLE -> NOT_CONNECTED -> CONNECTED
        // -> TRANSFERRING -> CONNECTED -> IDLE
        verifyNbIotStateTransitionsWithReceivingOnIdle(callback, true);

        // Verify not state transition on IDLE state
        verifyNbIotStateTransitionsWithTransferringFailureOnIdle(callback);

        // Verify state transition: IDLE -> NOT_CONNECTED -> POWER_OFF
        verifyNbIotStateTransitionsWithSendingAborted(callback);

        // Verify state transitions: POWER_OFF -> NOT_CONNECTED
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

        // Verify state transitions when sending: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
        // -> CONNECTED
        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sendDatagramWithoutResponse();
        verifyNbIotStateTransitionsWithSendingOnConnected(callback, false);

        // Verify state transitions: CONNECTED -> POWER_OFF
        callback.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());
        assertTrue(
                sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceDisconnected(1));

        // Verify state transitions: POWER_OFF -> NOT_CONNECTED
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        assertTrue(isSatelliteEnabled());

        // Move to CONNECTED state
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.modemState);

        // Verify state transitions: CONNECTED -> TRANSFERRING -> CONNECTED
        verifyNbIotStateTransitionsWithReceivingOnConnected(callback);

        sSatelliteManager.unregisterForModemStateChanged(callback);
        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);

        revokeSatellitePermission();
    }

    private void sendDatagramWithoutResponse() {
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("sendDatagramWithoutResponse: Got InterruptedException in waiting"
                    + " for the sendDatagram result code, ex=" + ex);
            return;
        }
        assertNull(errorCode);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithSendingOnConnected(
            @NonNull SatelliteModemStateCallbackTest callback, boolean moveToIdleState) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        callback.clearModemStates();

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Move satellite to CONNECTED state
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);

        int expectedNumberOfEvents = moveToIdleState ? 4 : 3;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(2));
        if (moveToIdleState) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(3));
        }

        // Expected datagram transfer state transitions: WAITING_FOR_CONNECTED -> SENDING
        // -> SEND_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithTransferringFailureOnIdle(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Test sending failure
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        callback.clearModemStates();
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_WAIT_FOR_CONNECTED_STATE, 1000);
        // Return failure for the request to disable cellular scanning when exiting IDLE state.
        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteManager.SATELLITE_RESULT_SERVICE_ERROR);
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        assertFalse(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED -> FAILED
        // -> IDLE.
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        1, SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Datagram wait for connected state timer should have timed out and the send request should
        // have been aborted.
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("verifyNbIotStateTransitionsWithTransferringFailureOnIdle: Got "
                    + "InterruptedException in waiting for the sendDatagram result code"
                    + ", ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE, (long) errorCode);

        // Test receiving failure
        resultListener.clear();
        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteResult.SATELLITE_RESULT_ERROR);
        callback.clearModemStates();
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                resultListener::offer);

        assertFalse(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(3);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Datagram wait for connected state timer should have timed out and the poll request should
        // have been aborted.
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("verifyNbIotStateTransitionsWithTransferringFailureOnIdle: Got "
                    + "InterruptedException in waiting for the pollPendingDatagrams result"
                    + " code, ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE, (long) errorCode);

        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_WAIT_FOR_CONNECTED_STATE, 0);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithSendingAborted(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        callback.clearModemStates();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("verifyNbIotStateTransitionsWithSendingAborted: Got InterruptedException"
                    + " in waiting for the sendDatagram result code, ex=" + ex);
            return;
        }
        assertNull(errorCode);

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);

        // Turn off satellite modem. The send request should be aborted.
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("verifyNbIotStateTransitionsWithSendingAborted: Got InterruptedException"
                    + " in waiting for the sendDatagram result code, ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED, (long) errorCode);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED -> FAILED
        // -> IDLE.
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        1, SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithReceivingOnIdle(
            @NonNull SatelliteModemStateCallbackTest callback, boolean moveToIdleState) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Verify state transitions: IDLE -> NOT_CONNECTED
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearPollPendingDatagramPermits();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertFalse(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Verify state transitions: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(1));
        // Telephony should send the request pollPendingDatagrams to modem
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Expected datagram transfer state transitions: WAITING_FOR_CONNECTED -> RECEIVING
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        verifyNbIotStateTransitionsWithDatagramReceivedOnTransferring(
                callback, moveToIdleState, transmissionUpdateCallback);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithDatagramReceivedOnTransferring(
            @NonNull SatelliteModemStateCallbackTest callback, boolean moveToIdleState,
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback) {
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING, callback.modemState);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        int expectedNumberOfEvents = moveToIdleState ? 2 : 1;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(0));
        if (moveToIdleState) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(1));
        }

        // Expected datagram transfer state transitions: RECEIVING -> RECEIVE_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(2);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
    }

    private void verifyNbIotStateTransitionsWithReceivingOnConnected(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        // Verify state transitions: CONNECTED -> TRANSFERRING -> CONNECTED
        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        int expectedNumberOfEvents = 2;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(1));

        // Expected datagram transfer state transitions: IDLE -> RECEIVE_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(2);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    @Test
    public void testSatelliteEnableErrorHandling() {
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(sTelephonyManager != null);

        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        requestSatelliteEnabled(true, true, SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());

        requestSatelliteEnabled(true, true, SatelliteManager.SATELLITE_RESULT_SUCCESS);
        requestSatelliteEnabled(true, false, SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS);

        turnRadioOff();
        grantSatellitePermission();
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());

        requestSatelliteEnabled(true, true, SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE);
        requestSatelliteEnabled(false);

        turnRadioOn();
        grantSatellitePermission();
        assertFalse(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());

        requestSatelliteEnabled(true, true, SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());

        callback.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());

        sSatelliteManager.unregisterForModemStateChanged(callback);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteDatagramReceivedAck() {
        if (!shouldTestSatelliteWithMockService()) return;

        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        // Compute next received datagramId using current ID and verify it is correct.
        long nextDatagramId = ((satelliteDatagramCallback.mDatagramId + 1)
                % DatagramController.MAX_DATAGRAM_ID);
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertThat(satelliteDatagramCallback.mDatagramId).isEqualTo(nextDatagramId);

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
    }

    @Test
    public void  testRequestSatelliteCapabilities() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testRequestSatelliteCapabilities");
        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        final AtomicReference<SatelliteCapabilities> capabilities = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        logd("testRequestSatelliteCapabilities: onResult");
                        capabilities.set(result);

                        assertNotNull(result);
                        assertNotNull(result.getSupportedRadioTechnologies());
                        assertThat(SUPPORTED_RADIO_TECHNOLOGIES)
                                .isEqualTo(result.getSupportedRadioTechnologies());
                        assertThat(POINTING_TO_SATELLITE_REQUIRED)
                                .isEqualTo(result.isPointingRequired());
                        assertThat(MAX_BYTES_PER_DATAGRAM)
                                .isEqualTo(result.getMaxBytesPerOutgoingDatagram());
                        assertNotNull(result.getAntennaPositionMap());
                        assertThat(ANTENNA_POSITION_MAP).isEqualTo(result.getAntennaPositionMap());
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("testRequestSatelliteCapabilities: onError");
                        errorCode.set(exception.getErrorCode());
                    }
                };

        sMockSatelliteServiceManager.setSupportedRadioTechnologies(
                new int[]{NTRadioTechnology.PROPRIETARY});
        sSatelliteManager.requestCapabilities(getContext().getMainExecutor(), receiver);

        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_success() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendSatelliteDatagram_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        for (int i = 0; i < 5; i++) {
            logd("testSendSatelliteDatagram_success: moveToSendingState");
            assertTrue(isSatelliteEnabled());
            moveToSendingState();

            logd("testSendSatelliteDatagram_success: Disable satellite");
            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                    startTransmissionUpdates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();

            // Datagram transfer state should change from SENDING to FAILED and then IDLE.
            assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(2));
            assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(2);
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                            1, SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED));
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            stopTransmissionUpdates(transmissionUpdateCallback);

            logd("testSendSatelliteDatagram_success: Enable satellite");
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(1));
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForModemStateChanged(callback);

            logd("testSendSatelliteDatagram_success: sendSatelliteDatagramSuccess");
            sendSatelliteDatagramSuccess(true, true);
        }
        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_failure() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendSatelliteDatagram_failure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_failure: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_failure: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_ERROR);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) SENDING to SENDING_FAILED
         * 2) SENDING_FAILED to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_ERROR));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendMultipleSatelliteDatagrams_success() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendMultipleSatelliteDatagrams_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list.
        sMockSatelliteServiceManager.setWaitToSend(true);

        // Send three datagrams to observe how pendingCount is updated
        // after processing one datagram at a time.
        LinkedBlockingQueue<Integer> resultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener1::offer);
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        LinkedBlockingQueue<Integer> resultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener2::offer);
        LinkedBlockingQueue<Integer> resultListener3 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener3::offer);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send first datagram: SENDING to SENDING_SUCCESS
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        int expectedNumOfEvents = 1;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));

        // Pending count is 2 as there are 2 datagrams to be sent.
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send second datagram: SENDING to SENDING_SUCCESS
        // callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        expectedNumOfEvents = 2;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send third datagram: SENDING - SENDING_SUCCESS - IDLE
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener3.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getSendDatagramStateChange(3)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(4)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(5)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendMultipleSatelliteDatagrams_failure() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendMultipleSatelliteDatagrams_failure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_failure: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list.
        sMockSatelliteServiceManager.setWaitToSend(true);

        // Send three datagrams to observe how pendingCount is updated
        // after processing one datagram at a time.
        LinkedBlockingQueue<Integer> resultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener1::offer);
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        LinkedBlockingQueue<Integer> resultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener2::offer);
        LinkedBlockingQueue<Integer> resultListener3 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener3::offer);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Set error and send first datagram: SENDING to SENDING_FAILED
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_ERROR);

        try {
            errorCode = resultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED);

        try {
            errorCode = resultListener3.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED);

        assertTrue(callback.waitUntilOnSendDatagramStateChanged(2));
        // Pending count is 2 as there are 2 datagrams to be sent.
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        2, SatelliteManager.SATELLITE_RESULT_ERROR));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }


    @Test
    public void testReceiveSatelliteDatagram() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testReceiveSatelliteDatagram");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        for (int i = 0; i < 5; i++) {
            logd("testReceiveSatelliteDatagram: moveToReceivingState");
            assertTrue(isSatelliteEnabled());
            moveToReceivingState();

            logd("testReceiveSatelliteDatagram: Disable satellite");
            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                    startTransmissionUpdates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();

            // Datagram transfer state should change from RECEIVING to IDLE.
            assertTrue(transmissionUpdateCallback
                    .waitUntilOnReceiveDatagramStateChanged(2));
            assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                    .isEqualTo(2);
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                            0, SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            stopTransmissionUpdates(transmissionUpdateCallback);

            logd("testReceiveSatelliteDatagram: Enable satellite");
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(1));
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForModemStateChanged(callback);

            logd("testReceiveSatelliteDatagram: receiveSatelliteDatagramSuccess");
            receiveSatelliteDatagramSuccess();
        }
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveMultipleSatelliteDatagrams() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testReceiveMultipleSatelliteDatagrams");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveMultipleSatelliteDatagrams: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Receive first datagram: Datagram state changes from RECEIVING to RECEIVE_SUCCESS
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 2);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        int expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));


        // Receive second datagram: Datagram state changes from RECEIVING to RECEIVE_SUCCESS
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 1);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(3)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Receive third datagram: Datagram state changes from RECEIVING - RECEIVE_SUCCESS - IDLE
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(4)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(5)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(6)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveSatellitePositionUpdate() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testReceiveSatellitePositionUpdate");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatellitePositionUpdate: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        transmissionUpdateCallback.clearPointingInfo();
        android.telephony.satellite.stub.PointingInfo pointingInfo =
                new android.telephony.satellite.stub.PointingInfo();
        pointingInfo.satelliteAzimuth = 10.5f;
        pointingInfo.satelliteElevation = 30.23f;
        PointingInfo expectedPointingInfo = new PointingInfo(10.5f, 30.23f);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        transmissionUpdateCallback.clearPointingInfo();
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveMultipleSatellitePositionUpdates() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testReceiveMultipleSatellitePositionUpdates");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveMultipleSatellitePositionUpdates: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Receive first position update
        transmissionUpdateCallback.clearPointingInfo();
        android.telephony.satellite.stub.PointingInfo pointingInfo =
                new android.telephony.satellite.stub.PointingInfo();
        pointingInfo.satelliteAzimuth = 10.5f;
        pointingInfo.satelliteElevation = 30.23f;
        PointingInfo expectedPointingInfo = new PointingInfo(10.5f, 30.23f);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        // Receive second position update
        transmissionUpdateCallback.clearPointingInfo();
        pointingInfo.satelliteAzimuth = 100;
        pointingInfo.satelliteElevation = 120;
        expectedPointingInfo = new PointingInfo(100, 120);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        transmissionUpdateCallback.clearPointingInfo();
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendAndReceiveSatelliteDatagram_DemoMode_success() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendSatelliteDatagram_DemoMode_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        String mText = "This is a test datagram message from user";
        for (int i = 0; i < 5; i++) {
            logd("testSendSatelliteDatagram_DemoMode_success: moveToSendingState");
            assertTrue(isSatelliteEnabled());
            moveToSendingState();

            logd("testSendSatelliteDatagram_DemoMode_success: Disable satellite");
            SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
            sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), stateCallback);
            assertTrue(stateCallback.waitUntilResult(1));

            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                    startTransmissionUpdates();
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            stateCallback.clearModemStates();

            // Datagram transfer state should change from SENDING to FAILED and then IDLE.
            assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(2));
            assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(2);
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                            1, SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED));
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            stopTransmissionUpdates(transmissionUpdateCallback);

            logd("testSendSatelliteDatagram_DemoMode_success: Enable satellite");
            stateCallback.clearModemStates();
            requestSatelliteEnabledForDemoMode(true);
            assertTrue(stateCallback.waitUntilResult(1));
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);

            logd("testSendSatelliteDatagram_DemoMode_success: sendSatelliteDatagramSuccess");
            sendSatelliteDatagramDemoModeSuccess(mText);

            // test pollPendingSatelliteDatagram for demo mode
            sSatelliteManager.setDeviceAlignedWithSatellite(true);
            transmissionUpdateCallback = startTransmissionUpdates();
            SatelliteDatagramCallbackTest datagramCallback = new SatelliteDatagramCallbackTest();
            assertTrue(SatelliteManager.SATELLITE_RESULT_SUCCESS
                    == sSatelliteManager.registerForIncomingDatagram(
                            getContext().getMainExecutor(), datagramCallback));

            LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
            sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                    resultListener::offer);

            assertTrue(datagramCallback.waitUntilResult(1));

            // Because pending count is 0, datagram transfer state changes from
            // IDLE -> RECEIVING -> RECEIVE_SUCCESS -> IDLE.
            int expectedNumOfEvents = 3;
            assertTrue(transmissionUpdateCallback
                    .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
            assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                    .isEqualTo(expectedNumOfEvents);
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            transmissionUpdateCallback.clearReceiveDatagramStateChanges();
            stopTransmissionUpdates(transmissionUpdateCallback);

            // Because demo mode is on, the received datagram should be the same as the
            // last sent datagram
            assertTrue(Arrays.equals(
                    datagramCallback.mDatagram.getSatelliteDatagram(), mText.getBytes()));
            sSatelliteManager.unregisterForIncomingDatagram(datagramCallback);
        }

        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_DemoMode_failure() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendSatelliteDatagram_DemoMode_failure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        // Enable satellite with demo mode on
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            stateCallback.clearModemStates();
        }
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(1));
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(true));

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_failure: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_failure: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_ERROR);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_FAILED
         * 3) SENDING_FAILED to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_ERROR));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        sSatelliteManager.unregisterForModemStateChanged(stateCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModeRadios() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSatelliteModeRadios: start");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        Manifest.permission.UWB_PRIVILEGED);
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        boolean originalEnabledState = isSatelliteEnabled();
        if (originalEnabledState) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertSatelliteEnabledInSettings(false);
            stateCallback.clearModemStates();
        }

        // Get satellite mode radios
        String originalSatelliteModeRadios =  Settings.Global.getString(
                getContext().getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
        logd("originalSatelliteModeRadios: " + originalSatelliteModeRadios);
        SatelliteModeRadiosUpdater satelliteRadiosModeUpdater =
                new SatelliteModeRadiosUpdater(getContext());

        try {
            identifyRadiosSensitiveToSatelliteMode();
            mTestSatelliteModeRadios = "";
            logd("test satelliteModeRadios: " + mTestSatelliteModeRadios);
            assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(mTestSatelliteModeRadios));

            // Enable Satellite and check whether all radios are disabled
            requestSatelliteEnabled(true, EXTERNAL_DEPENDENT_TIMEOUT);
            assertTrue(stateCallback.waitUntilResult(1));
            assertSatelliteEnabledInSettings(true);
            assertTrue(areAllRadiosDisabled());

            // Disable satellite and check whether all radios are set to their initial state
            setRadioExpectedState();
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilResult(1));
            assertSatelliteEnabledInSettings(false);
            assertTrue(areAllRadiosResetToInitialState());
        } finally {
            // Restore original satellite mode radios
            logd("restore original satellite mode radios");
            assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(
                    originalSatelliteModeRadios));
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);
            unregisterSatelliteModeRadios();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSatelliteModeRadios_noRadiosSensitiveToSatelliteMode() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSatelliteModeRadios_noRadiosSensitiveToSatelliteMode: start");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        Manifest.permission.UWB_PRIVILEGED);
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        boolean originalEnabledState = isSatelliteEnabled();
        if (originalEnabledState) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertSatelliteEnabledInSettings(false);
            stateCallback.clearModemStates();
        }

        // Get satellite mode radios
        String originalSatelliteModeRadios =  Settings.Global.getString(
                getContext().getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
        logd("originalSatelliteModeRadios: " + originalSatelliteModeRadios);
        SatelliteModeRadiosUpdater satelliteRadiosModeUpdater =
                new SatelliteModeRadiosUpdater(getContext());

        try {
            mTestSatelliteModeRadios = "";
            logd("test satelliteModeRadios: " + mTestSatelliteModeRadios);
            assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(mTestSatelliteModeRadios));

            // Enable Satellite and check whether all radios are disabled
            setRadioExpectedState();
            requestSatelliteEnabled(true, EXTERNAL_DEPENDENT_TIMEOUT);
            assertTrue(stateCallback.waitUntilResult(1));
            assertSatelliteEnabledInSettings(true);
            assertTrue(areAllRadiosDisabled());
            assertTrue(areAllRadiosResetToInitialState());

            // Disable satellite and check whether all radios are set to their initial state
            setRadioExpectedState();
            stateCallback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertSatelliteEnabledInSettings(false);
            assertTrue(areAllRadiosResetToInitialState());
            stateCallback.clearModemStates();
        } finally {
            // Restore original satellite mode radios
            logd("restore original satellite mode radios");
            assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(
                    originalSatelliteModeRadios));
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSatelliteModeRadiosWithAirplaneMode() throws Exception {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSatelliteModeRadiosWithAirplaneMode: start");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        Manifest.permission.NETWORK_SETTINGS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                        Manifest.permission.UWB_PRIVILEGED);
        assertTrue(isSatelliteProvisioned());

        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                sTelephonyManager.getServiceState(), sTelephonyManager.getRadioPowerState());
        sTelephonyManager.registerTelephonyCallback(Runnable::run, callback);
        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        boolean originalEnabledState = isSatelliteEnabled();
        if (originalEnabledState) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            stateCallback.clearModemStates();
        }

        ConnectivityManager connectivityManager =
                getContext().getSystemService(ConnectivityManager.class);

        // Get original satellite mode radios and original airplane mode
        String originalSatelliteModeRadios =  Settings.Global.getString(
                getContext().getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
        logd("originalSatelliteModeRadios: " + originalSatelliteModeRadios);
        boolean originalAirplaneMode = Settings.Global.getInt(
                getContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON) != 0;
        SatelliteModeRadiosUpdater satelliteModeRadiosUpdater =
                new SatelliteModeRadiosUpdater(getContext());

        try {
            identifyRadiosSensitiveToSatelliteMode();
            mTestSatelliteModeRadios = "";
            logd("test satelliteModeRadios: " + mTestSatelliteModeRadios);
            assertTrue(satelliteModeRadiosUpdater.setSatelliteModeRadios(mTestSatelliteModeRadios));

            // Enable Satellite and check whether all radios are disabled
            requestSatelliteEnabled(true, EXTERNAL_DEPENDENT_TIMEOUT);
            assertTrue(stateCallback.waitUntilResult(1));
            assertTrue(isSatelliteEnabled());
            assertSatelliteEnabledInSettings(true);
            assertTrue(areAllRadiosDisabled());

            // Enable airplane mode, check whether all radios are disabled and
            // also satellite mode is disabled
            connectivityManager.setAirplaneMode(true);
            // Wait for telephony radio power off
            callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
            // Wait for satellite mode state changed
            assertTrue(stateCallback.waitUntilResult(1));
            assertFalse(isSatelliteEnabled());
            assertSatelliteEnabledInSettings(false);
            assertTrue(areAllRadiosDisabled());

            // Disable airplane mode, check whether all radios are set to their initial state
            setRadioExpectedState();
            connectivityManager.setAirplaneMode(false);
            callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
            assertTrue(areAllRadiosResetToInitialState());
        } finally {
            // Restore original satellite mode radios
            logd("restore original satellite mode radios and original airplane mode");
            connectivityManager.setAirplaneMode(originalAirplaneMode);
            callback.waitForRadioStateIntent(originalAirplaneMode
                    ? TelephonyManager.RADIO_POWER_OFF : TelephonyManager.RADIO_POWER_ON);
            assertTrue(satelliteModeRadiosUpdater.setSatelliteModeRadios(
                    originalSatelliteModeRadios));
            sTelephonyManager.unregisterTelephonyCallback(callback);
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);
            unregisterSatelliteModeRadios();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSendSatelliteDatagram_DemoMode_not_Aligned() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendSatelliteDatagram_DemoMode_not_Aligned");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(true));
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        // Enable satellite with demo mode on
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            stateCallback.clearModemStates();
        }
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(1));

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in "
                    + "waiting for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram and satellite is not aligned.
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_ALIGN, TEST_SATELLITE_DEVICE_ALIGN_TIMEOUT_MILLIS));
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_FAILED
         * 3) SENDING_FAILED to IDLE
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Move to sending state and wait for satellite alignment forever
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_ALIGN,
                TEST_SATELLITE_DEVICE_ALIGN_FOREVER_TIMEOUT_MILLIS));
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        // No response for the request sendDatagram received
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNull(errorCode);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.setDeviceAlignedWithSatellite(true);

        // Satellite is aligned now. We should get the response of the request
        // sendSatelliteDatagrams.
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) SENDING to SEND_SUCCESS
         * 2) SEND_SUCCESS to IDLE
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(2));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Move to sending state and wait for satellite alignment forever again
        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        // No response for the request sendDatagram received
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNull(errorCode);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        stateCallback.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(stateCallback.waitUntilModemOff());
        stateCallback.clearModemStates();

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) SENDING to SENDING_FAILED
         * 2) SENDING_FAILED to IDLE
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(2));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(2);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        1, SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);

        // Restore satellite device align time out to default value.
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_ALIGN, 0));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
        sSatelliteManager.unregisterForModemStateChanged(stateCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveSatelliteDatagram_DemoMode_not_Aligned() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testReceiveSatelliteDatagram_DemoMode_not_Aligned");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());

        // Request enable satellite with demo mode on
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            stateCallback.clearModemStates();
        }
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(1));

        sSatelliteManager.setDeviceAlignedWithSatellite(true);
        // Send satellite datagram to compare with the received datagram in demo mode
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in "
                    + "waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        Log.d(TAG, "testReceiveSatelliteDatagram_DemoMode_not_Aligned: sendDatagram "
                + "errorCode=" + errorCode);


        // Test poll pending satellite datagram for demo mode while it is not aligned
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_ALIGN, TEST_SATELLITE_DEVICE_ALIGN_TIMEOUT_MILLIS));

        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                resultListener::offer);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in "
                    + "waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE);

        // Datagram transfer state should change from RECEIVING to IDLE.
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(3);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Move satellite to receiving state and wait for satellite aligned forever
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_ALIGN,
                TEST_SATELLITE_DEVICE_ALIGN_FOREVER_TIMEOUT_MILLIS));

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                resultListener::offer);

        // No response for the request pollPendingDatagrams received
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in "
                    + "waiting for the sendDatagram result code");
            return;
        }
        assertNull(errorCode);

        // Datagram transfer state should change from IDLE to RECEIVING.
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        stateCallback.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(stateCallback.waitUntilModemOff());
        stateCallback.clearModemStates();

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in "
                    + "waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED);

        // Datagram transfer state should change from RECEIVING to IDLE.
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(2);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        stopTransmissionUpdates(transmissionUpdateCallback);
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_ALIGN, 0));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
        sSatelliteManager.unregisterForModemStateChanged(stateCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemBusy_modemSendingDatagram_pollingFailure() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSatelliteModemBusy_modemSendingDatagram_pollingFailure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list
        // and modem is busy sending datagrams
        sMockSatelliteServiceManager.setWaitToSend(true);

        LinkedBlockingQueue<Integer> sendResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                sendResultListener::offer);

        LinkedBlockingQueue<Integer> pollResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                pollResultListener::offer);

        Integer errorCode;
        try {
            errorCode = pollResultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemSendingDatagram_pollingFailure: Got "
                    + "InterruptedException in waiting for the pollPendingDatagrams "
                    + "result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_MODEM_BUSY);

        // Send datagram successfully to bring sending state back to IDLE.
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemBusy_modemPollingDatagrams_pollingFailure() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSatelliteModemBusy_modemPollingDatagrams_pollingFailure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> pollResultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                pollResultListener1::offer);

        // As we already got one polling request, this second polling request would fail
        LinkedBlockingQueue<Integer> pollResultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                pollResultListener2::offer);

        Integer errorCode;
        try {
            errorCode = pollResultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemSendingDatagram_pollingFailure: Got "
                    + "InterruptedException in waiting for the pollPendingDatagrams "
                    + "result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_MODEM_BUSY);

        // Receive one datagram successfully to bring receiving state back to IDLE.
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemBusy_modemPollingDatagram_sendingDelayed() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSatelliteModemBusy_modemPollingDatagram_sendingDelayed");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemPollingDatagram_sendingDelayed: "
                    + "Got InterruptedException in waiting for the "
                    + "startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearSendDatagramStateChanges();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();

        LinkedBlockingQueue<Integer> pollResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                pollResultListener::offer);
        // Datagram transfer state changes from IDLE -> RECEIVING.
        assertSingleReceiveDatagramStateChanged(transmissionUpdateCallback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                0, SatelliteManager.SATELLITE_RESULT_SUCCESS);

        LinkedBlockingQueue<Integer> sendResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                sendResultListener::offer);
        // Sending datagram will be delayed as modem is in RECEIVING state
        assertFalse(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        // As pending count is 0, datagram transfer state changes from
        // RECEIVING -> RECEIVE_SUCCESS -> IDLE.
        int expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // As polling is completed, now modem will start sending datagrams
        expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback.
                waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        transmissionUpdateCallback.clearSendDatagramStateChanges();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testRebindToSatelliteService() {
        if (!shouldTestSatelliteWithMockService()) return;

        grantSatellitePermission();
        assertTrue(isSatelliteSupported());

        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // Forcefully stop the external satellite service.
        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteServiceDisconnected(1));

        // Reconnect CTS to the external satellite service.
        assertTrue(sMockSatelliteServiceManager.setupExternalSatelliteService());
        // Telephony should rebind to the external satellite service after the binding died.
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // Restore original binding states
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        callback.clearModemStates();
        sMockSatelliteServiceManager.resetSatelliteService();
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());
        assertTrue(callback.waitUntilModemOff(EXTERNAL_DEPENDENT_TIMEOUT));
        callback.clearModemStates();

        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteServiceDisconnected(1));

        revokeSatellitePermission();
    }

    @Test
    public void testRebindToSatelliteGatewayService() {
        if (!shouldTestSatelliteWithMockService()) return;

        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
        }

        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteGatewayService());
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));

        // Forcefully stop the external satellite gateway service.
        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteGatewayService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteGatewayServiceDisconnected(1));

        // Reconnect CTS to the external satellite gateway service.
        assertTrue(sMockSatelliteServiceManager.setupExternalSatelliteGatewayService());
        // Telephony should rebind to the external satellite gateway service after the binding died.
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));

        sSatelliteManager.unregisterForModemStateChanged(callback);
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteAttachEnabledForCarrier() {
        if (!shouldTestSatelliteWithMockService() || !Flags.carrierEnabledSatelliteFlag()) return;

        logd("testSatelliteAttachEnabledForCarrier");
        grantSatellitePermission();
        beforeSatelliteForCarrierTest();
        @SatelliteManager.SatelliteResult int expectedSuccess =
                SatelliteManager.SATELLITE_RESULT_SUCCESS;
        @SatelliteManager.SatelliteResult int expectedError;

        /* Test when satellite is not supported in the carrier config */
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, false);

        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
        requestSatelliteAttachEnabledForCarrier(true, SatelliteManager.SATELLITE_RESULT_SUCCESS);

        Pair<Boolean, Integer> pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);


        /* Test when satellite is supported in the carrier config */
        setSatelliteError(expectedSuccess);
        bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        PersistableBundle plmnBundle = new PersistableBundle();
        int[] intArray1 = {3, 5};
        int[] intArray2 = {3};
        plmnBundle.putIntArray("123411", intArray1);
        plmnBundle.putIntArray("123412", intArray2);
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                plmnBundle);
        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);

        ArrayList<String> expectedCarrierPlmnList = new ArrayList<>();
        expectedCarrierPlmnList.add("123411");
        expectedCarrierPlmnList.add("123412");
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSetSatellitePlmn(1));
        List<String> carrierPlmnList = sMockSatelliteServiceManager.getCarrierPlmnList();
        assertNotNull(carrierPlmnList);
        assertEquals(expectedCarrierPlmnList, carrierPlmnList);
        List<String> satellitePlmnListFromOverlayConfig =
                sMockSatelliteServiceManager.getPlmnListFromOverlayConfig();
        List<String> expectedAllSatellitePlmnList = SatelliteServiceUtils.mergeStrLists(
                carrierPlmnList, satellitePlmnListFromOverlayConfig);
        List<String> allSatellitePlmnList = sMockSatelliteServiceManager.getAllSatellitePlmnList();
        assertNotNull(allSatellitePlmnList);
        assertEquals(expectedAllSatellitePlmnList, allSatellitePlmnList);
        requestSatelliteAttachEnabledForCarrier(true, expectedSuccess);

        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported, and requested satellite disabled */
        requestSatelliteAttachEnabledForCarrier(false, expectedSuccess);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());
        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(false, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported, but modem returns INVALID_MODEM_STATE */
        expectedError = SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE;
        setSatelliteError(expectedError);
        requestSatelliteAttachEnabledForCarrier(true, expectedError);

        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported, and requested satellite disabled */
        expectedError = SatelliteManager.SATELLITE_RESULT_SUCCESS;
        requestSatelliteAttachEnabledForCarrier(false, expectedError);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());
        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(false, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported, but modem returns RADIO_NOT_AVAILABLE */
        expectedError = SatelliteManager.SATELLITE_RESULT_RADIO_NOT_AVAILABLE;
        setSatelliteError(expectedError);
        requestSatelliteAttachEnabledForCarrier(true, expectedError);

        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);

        afterSatelliteForCarrierTest();
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteAttachRestrictionForCarrier() {
        if (!shouldTestSatelliteWithMockService() || !Flags.carrierEnabledSatelliteFlag()) return;

        logd("testSatelliteAttachRestrictionForCarrier");
        grantSatellitePermission();
        beforeSatelliteForCarrierTest();
        clearSatelliteEnabledForCarrier();
        @SatelliteManager.SatelliteResult int expectedSuccess =
                SatelliteManager.SATELLITE_RESULT_SUCCESS;

        /* Test when satellite is supported but there is a restriction reason */
        setSatelliteError(expectedSuccess);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
        int restrictionReason = SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;
        requestAddSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, true);
        requestSatelliteAttachEnabledForCarrier(true, expectedSuccess);
        Pair<Boolean, Integer> pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'GEOLOCATION' is removed and the restriction reason is
           empty, re-evaluate and trigger enable/disable again */
        requestRemoveSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, false);
        assertEquals(true, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'GEOLOCATION' is added and the restriction reason becomes
           'GEOLOCATION', re-evaluate and trigger enable/disable again */
        requestAddSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, true);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'ENTITLEMENT' is added and the restriction reasons become
           ‘GEOLOCATION’ and ‘ENTITLEMENT.’ re-evaluate and trigger enable/disable again */
        restrictionReason = SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT;
        requestAddSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, true);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'ENTITLEMENT' is removed and the restriction reason becomes
           ‘GEOLOCATION’, re-evaluate and trigger enable/disable again */
        requestRemoveSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        restrictionReason = SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, true);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'GEOLOCATION' is removed and the restriction reason becomes
            empty, re-evaluate and trigger enable/disable again */
        requestRemoveSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, false);
        assertEquals(true, getIsSatelliteEnabledForCarrierFromMockService());

        afterSatelliteForCarrierTest();
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testNtnSignalStrength() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testNtnSignalStrength: start");
        grantSatellitePermission();

        NtnSignalStrengthCallbackTest ntnSignalStrengthCallbackTest =
                new NtnSignalStrengthCallbackTest();

        /* register callback for non-terrestrial network signal strength changed event */
        sSatelliteManager.registerForNtnSignalStrengthChanged(getContext().getMainExecutor(),
                ntnSignalStrengthCallbackTest);

        @NtnSignalStrength.NtnSignalStrengthLevel int expectedLevel =
                NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE;
        @SatelliteManager.SatelliteResult int expectedError;
        setSatelliteError(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        setNtnSignalStrength(expectedLevel);
        Pair<NtnSignalStrength, Integer> pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);

        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD;
        expectedError = SatelliteManager.SATELLITE_RESULT_MODEM_ERROR;
        setSatelliteError(expectedError);
        setNtnSignalStrength(expectedLevel);
        pairResult = requestNtnSignalStrength();
        assertNull(pairResult.first);
        assertEquals(expectedError, pairResult.second.intValue());

        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD;
        expectedError = SatelliteManager.SATELLITE_RESULT_SUCCESS;
        setSatelliteError(expectedError);
        setNtnSignalStrength(expectedLevel);
        pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);

        /* As non-terrestrial network signal strength is cached in framework, simple set won't
        affect cached value */
        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_GREAT;
        setNtnSignalStrength(expectedLevel);
        pairResult = requestNtnSignalStrength();
        assertNotEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);

        /* Cache will be updated when non-terrestrial network signal strength changed event comes */
        sendOnNtnSignalStrengthChanged(expectedLevel);
        assertTrue(ntnSignalStrengthCallbackTest.waitUntilResult(1));
        pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);
        assertEquals(expectedLevel, ntnSignalStrengthCallbackTest.mNtnSignalStrength.getLevel());

        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_MODERATE;
        sendOnNtnSignalStrengthChanged(expectedLevel);
        assertTrue(ntnSignalStrengthCallbackTest.waitUntilResult(1));
        pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);
        assertEquals(expectedLevel, ntnSignalStrengthCallbackTest.mNtnSignalStrength.getLevel());

        /* Initialize the non-terrestrial signal strength cache in the framework */
        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE;
        sendOnNtnSignalStrengthChanged(expectedLevel);
        assertTrue(ntnSignalStrengthCallbackTest.waitUntilResult(1));
        pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);
        assertEquals(expectedLevel, ntnSignalStrengthCallbackTest.mNtnSignalStrength.getLevel());

        /* unregister non-terrestrial network signal strength changed event callback */
        sSatelliteManager.unregisterForNtnSignalStrengthChanged(ntnSignalStrengthCallbackTest);

        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testRegisterForCapabilitiesChanged() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testRegisterForCapabilitiesChanged: start");
        grantSatellitePermission();

        android.telephony.satellite.stub.SatelliteCapabilities capabilities =
                new android.telephony.satellite.stub.SatelliteCapabilities();
        int[] supportedRadioTechnologies =
                new int[]{android.telephony.satellite.stub.NTRadioTechnology.NB_IOT_NTN};
        capabilities.supportedRadioTechnologies = supportedRadioTechnologies;
        int[] antennaPositionKeys = new int[]{
                SatelliteManager.DISPLAY_MODE_OPENED, SatelliteManager.DISPLAY_MODE_CLOSED};
        AntennaPosition[] antennaPositionValues = new AntennaPosition[] {
                new AntennaPosition(new AntennaDirection(1, 1, 1),
                        SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT),
                new AntennaPosition(new AntennaDirection(2, 2, 2),
                        SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT)
        };

        capabilities.isPointingRequired = POINTING_TO_SATELLITE_REQUIRED;
        capabilities.maxBytesPerOutgoingDatagram = MAX_BYTES_PER_DATAGRAM;
        capabilities.antennaPositionKeys = antennaPositionKeys;
        capabilities.antennaPositionValues = antennaPositionValues;
        SatelliteCapabilities frameworkCapabilities =
                SatelliteServiceUtils.fromSatelliteCapabilities(capabilities);
        SatelliteCapabilitiesCallbackTest satelliteCapabilitiesCallbackTest =
                new SatelliteCapabilitiesCallbackTest();

        /* register callback for satellite capabilities changed event */
        @SatelliteManager.SatelliteResult int registerError =
                sSatelliteManager.registerForCapabilitiesChanged(
                        getContext().getMainExecutor(), satelliteCapabilitiesCallbackTest);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        /* Verify whether capability changed event has received */
        sendOnSatelliteCapabilitiesChanged(capabilities);
        assertTrue(satelliteCapabilitiesCallbackTest.waitUntilResult(1));
        assertTrue(frameworkCapabilities
                .equals(satelliteCapabilitiesCallbackTest.mSatelliteCapabilities));

        /* Verify whether notified and requested capabilities are equal */
        Pair<SatelliteCapabilities, Integer> pairResult = requestSatelliteCapabilities();
        assertTrue(frameworkCapabilities.equals(pairResult.first));
        assertNull(pairResult.second);

        /* datagram size has changed */
        capabilities.maxBytesPerOutgoingDatagram = MAX_BYTES_PER_DATAGRAM + 1;
        frameworkCapabilities = SatelliteServiceUtils.fromSatelliteCapabilities(capabilities);

        /* Verify changed capabilities are reflected */
        sendOnSatelliteCapabilitiesChanged(capabilities);
        assertTrue(satelliteCapabilitiesCallbackTest.waitUntilResult(1));
        assertTrue(frameworkCapabilities
                .equals(satelliteCapabilitiesCallbackTest.mSatelliteCapabilities));

        pairResult = requestSatelliteCapabilities();
        assertTrue(frameworkCapabilities.equals(pairResult.first));
        assertNull(pairResult.second);

        /* Initialize Radio technology */
        supportedRadioTechnologies =
                new int[]{android.telephony.satellite.stub.NTRadioTechnology.PROPRIETARY};
        capabilities.supportedRadioTechnologies = supportedRadioTechnologies;
        sendOnSatelliteCapabilitiesChanged(capabilities);
        /* unregister non-terrestrial network signal strength changed event callback */
        sSatelliteManager.unregisterForCapabilitiesChanged(
                satelliteCapabilitiesCallbackTest);

        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testSendSatelliteDatagram_DemoMode_WithDeviceConfig() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendSatelliteDatagram_DemoMode_WithDeviceConfig");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        // Enable satellite with demo mode on
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            stateCallback.clearModemStates();
        }
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(1));
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(false));

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_WithDeviceConfig: Got InterruptedException "
                    + "in waiting for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setErrorCode(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);
        sSatelliteManager.setDeviceAlignedWithSatellite(true);
        // Satellite datagram does not send to satellite modem.
        sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(0);
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_WithDeviceConfig: Got InterruptedException "
                    + "in waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SEND_SUCCESS
         * 3) SEND_SUCCESS to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        sSatelliteManager.unregisterForModemStateChanged(stateCallback);
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testSatelliteAccessControl() {
        if (!shouldTestSatelliteWithMockService()) return;

        // Satellite is not allowed by modem
        sMockSatelliteServiceManager.setSatelliteCommunicationAllowed(false);
        verifyIsSatelliteAllowed(false);

        // Satellite is allowed by modem
        sMockSatelliteServiceManager.setSatelliteCommunicationAllowed(true);

        // Test access controller using cached country codes
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                false, true, null, 0, SATELLITE_COUNTRY_CODES));

        // Allowed case
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, null, null, "US",
                SystemClock.elapsedRealtimeNanos()));
        verifyIsSatelliteAllowed(true);

        // Disallowed case
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, null, null, "IN",
                SystemClock.elapsedRealtimeNanos()));
        verifyIsSatelliteAllowed(false);

        // Test access controller using on-device data
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, null, null, null, 0));
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                false, true, SATELLITE_S2_FILE, TimeUnit.MINUTES.toNanos(10), "US"));
        registerTestLocationProvider();

        // Set current location to Google San Diego office
        setTestProviderLocation(32.909808231041644, -117.18185788819781);
        verifyIsSatelliteAllowed(true);

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (!isSatelliteEnabled()) {
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
            assertTrue(isSatelliteEnabled());
        }

        // Set current location to Google Bangalore office
        setTestProviderLocation(12.994021769576554, 12.994021769576554);
        verifyIsSatelliteAllowed(false);

        // Even though satellite is not allowed at the current location, disabling satellite should
        // succeed
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());

        // Restore satellite access allowed
        setUpSatelliteAccessAllowed();
        revokeSatellitePermission();
        unregisterTestLocationProvider();
    }

    @Test
    @FlaggedApi(Flags.FLAG_CARRIER_ENABLED_SATELLITE_FLAG)
    public void testGetSatellitePlmnsForCarrier() {
        if (!shouldTestSatelliteWithMockService() || !Flags.carrierEnabledSatelliteFlag()) return;

        logd("testGetAggregateSatellitePlmnListForCarrier");
        grantSatellitePermission();
        beforeSatelliteForCarrierTest();
        @SatelliteManager.SatelliteResult int expectedSuccess =
                SatelliteManager.SATELLITE_RESULT_SUCCESS;

        /* Test when satellite is supported in the carrier config */
        setSatelliteError(expectedSuccess);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        PersistableBundle plmnBundle = new PersistableBundle();
        int[] intArray1 = {3, 5};
        int[] intArray2 = {3};
        plmnBundle.putIntArray("123411", intArray1);
        plmnBundle.putIntArray("123412", intArray2);
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                plmnBundle);
        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);

        ArrayList<String> expectedCarrierPlmnList = new ArrayList<>();
        expectedCarrierPlmnList.add("123411");
        expectedCarrierPlmnList.add("123412");
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSetSatellitePlmn(1));
        List<String> carrierPlmnList = sMockSatelliteServiceManager.getCarrierPlmnList();
        assertNotNull(carrierPlmnList);
        assertEquals(expectedCarrierPlmnList, carrierPlmnList);

        /* Aggregated satellite plmn should be same with allSatellitePlmnList */
        List<String> aggregatedPlmnList = sSatelliteManager.getSatellitePlmnsForCarrier(
                sTestSubIDForCarrierSatellite);
        List<String> satellitePlmnListFromOverlayConfig =
                sMockSatelliteServiceManager.getPlmnListFromOverlayConfig();
        List<String> expectedAllSatellitePlmnList = SatelliteServiceUtils.mergeStrLists(
                carrierPlmnList, satellitePlmnListFromOverlayConfig);
        List<String> allSatellitePlmnList = sMockSatelliteServiceManager.getAllSatellitePlmnList();
        assertNotNull(allSatellitePlmnList);
        assertEquals(expectedAllSatellitePlmnList, allSatellitePlmnList);
        assertEquals(expectedAllSatellitePlmnList, aggregatedPlmnList);

        afterSatelliteForCarrierTest();
        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagrams_timeout() {
        if (!shouldTestSatelliteWithMockService()) return;

        logd("testSendSatelliteDatagrams_timeout");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagrams_timeout: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that the send request will time out.
        sMockSatelliteServiceManager.setWaitToSend(true);
        // Override the sending timeout duration to 1 second
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_WAIT_FOR_DATAGRAM_SENDING_RESPONSE, 1000);

        LinkedBlockingQueue<Integer> resultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener1::offer);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        assertTrue(callback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        1, SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagrams_timeout: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT);

        // Respond to the first send request
        callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        // Telephony should ignore the response
        assertFalse(callback.waitUntilOnSendDatagramStateChanged(1));

        // Restore the timeout duration
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_WAIT_FOR_DATAGRAM_SENDING_RESPONSE, 0);
    }
    @Test
    public void testRequestSatelliteEnabled_timeout() {
        if (!shouldTestSatelliteWithMockService()) return;

        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_timeout: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_timeout: disabling satellite...");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondTelephony(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 500));

        // Time out to enable satellite
        logd("testRequestSatelliteEnabled_timeout: enabling satellite...");
        int result = requestSatelliteEnabledWithResult(true, TIMEOUT);
        assertEquals(SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT, result);
        assertFalse(callback.waitUntilResult(1));
        assertFalse(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Respond to the above enable request. Telephony should ignore the event.
        logd("testRequestSatelliteEnabled_timeout: Responding the enabling request...");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true));
        assertFalse(callback.waitUntilResult(1));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Successfully enable satellite
        logd("testRequestSatelliteEnabled_timeout: enabling satellite...");
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());

        sMockSatelliteServiceManager.setShouldRespondTelephony(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 500));

        // Time out to disable satellite. Telephony should respond SATELLITE_RESULT_MODEM_TIMEOUT to
        // clients and move to SATELLITE_MODEM_STATE_OFF
        logd("testRequestSatelliteEnabled_timeout: disabling satellite...");
        callback.clearModemStates();
        result = requestSatelliteEnabledWithResult(false, TIMEOUT);
        assertEquals(SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT, result);
        assertTrue(callback.waitUntilModemOff());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Respond to the above disable request. Telephony should ignore the event.
        logd("testRequestSatelliteEnabled_timeout: Responding the disabling request...");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false));
        assertFalse(callback.waitUntilResult(1));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
    }

    /*
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithSuccessfulResult(
            SatelliteModemStateCallbackTest callback, boolean verifyListenToIdleTransition) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithSuccessfulResult: wrong modem state="
                    + callback.modemState);
            return;
        }

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemStateChanged: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);

        /*
         * Modem state should have the following transitions:
         * 1) IDLE to TRANSFERRING.
         * 2) TRANSFERRING to LISTENING.
         * 3) LISTENING to IDLE.
         *
         * When verifyListenToIdleTransition is true, we expect the above 3 state transitions.
         * Otherwise, we expect only the first 2 transitions since satellite is still in LISTENING
         * state (timeout duration is long when verifyListenToIdleTransition is false).
         */
        int expectedNumberOfEvents = verifyListenToIdleTransition ? 3 : 2;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(1));

        /*
         * On entering LISTENING state, we expect one event of EventOnSatelliteListeningEnabled with
         * value true. On exiting LISTENING state, we expect one event of
         * EventOnSatelliteListeningEnabled with value false.
         *
         * When verifyListenToIdleTransition is true, we expect satellite entering and then exiting
         * LISTENING state. Otherwise, we expect satellite entering and staying at LISTENING state.
         */
        expectedNumberOfEvents = verifyListenToIdleTransition ? 2 : 1;
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(
                expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents,
                sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertTrue(sMockSatelliteServiceManager.getListeningEnabled(0));

        if (verifyListenToIdleTransition) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(2));
            assertFalse(sMockSatelliteServiceManager.getListeningEnabled(1));
        }
        sMockSatelliteServiceManager.clearListeningEnabledList();
    }

    /*
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithFailedResult(SatelliteModemStateCallbackTest callback) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithFailedResult: wrong modem state=" + callback.modemState);
            return;
        }
        boolean isFirstStateListening =
                (callback.modemState == SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemStateChanged: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_ERROR, (long) errorCode);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        if (isFirstStateListening) {
            assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
            assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
            assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));
        }
        sMockSatelliteServiceManager.clearListeningEnabledList();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
    }

    /*
     * Before calling this function, caller need to make sure the modem is in LISTENING state.
     */
    private void receiveSatelliteDatagramWithSuccessfulResult(
            SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING, callback.modemState);

        // TODO (b/275086547): remove the below registerForIncomingDatagram command when the bug
        // is resolved.
        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertTrue(sMockSatelliteServiceManager.getListeningEnabled(0));
        sMockSatelliteServiceManager.clearListeningEnabledList();

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
    }

    /*
     * Before calling this function, caller need to make sure the modem is in LISTENING state.
     */
    private void receiveSatelliteDatagramWithFailedResult(
            SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING, callback.modemState);

        // TODO (b/275086547): remove the below registerForIncomingDatagram command when the bug
        // is resolved.
        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        /*
         * On entering LISTENING state, we expect one event of EventOnSatelliteListeningEnabled with
         * value true. On exiting LISTENING state, we expect one event of
         * EventOnSatelliteListeningEnabled with value false.
         *
         * At the beginning of this function, satellite is in LISTENING state. It then transitions
         * to TRANSFERRING state. Thus, we expect one event of EventOnSatelliteListeningEnabled with
         * value false.
         */
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));

        sMockSatelliteServiceManager.clearListeningEnabledList();
        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
    }

    private void moveToSendingState() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("moveToSendingState: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setShouldRespondTelephony(false);
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send datagram transfer state should move from IDLE to SENDING.
        assertSingleSendDatagramStateChanged(callback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                1, SatelliteManager.SATELLITE_RESULT_SUCCESS);

        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
    }

    private void sendSatelliteDatagramSuccess(
            boolean shouldOverridePointingUiClassName, boolean needFullScreenForPointingUi) {
        SatelliteTransmissionUpdateCallbackTest callback = startTransmissionUpdates();

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        callback.clearSendDatagramStateChanges();
        if (shouldOverridePointingUiClassName) {
            assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        }
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, needFullScreenForPointingUi, getContext().getMainExecutor(),
                resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_SUCCESS
         * 3) SENDING_SUCCESS to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));
        if (shouldOverridePointingUiClassName) {
            assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
        }

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
    }

    private void sendSatelliteDatagramDemoModeSuccess(String sampleText) {
        SatelliteTransmissionUpdateCallbackTest callback = startTransmissionUpdates();

        // Send satellite datagram
        SatelliteDatagram datagram = new SatelliteDatagram(sampleText.getBytes());
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.setDeviceAlignedWithSatellite(true);
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(true));
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_SUCCESS
         * 3) SENDING_SUCCESS to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
    }

    private void moveToReceivingState() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatelliteDatagram: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Datagram transfer state changes from IDLE to RECEIVING.
        assertSingleReceiveDatagramStateChanged(transmissionUpdateCallback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                0, SatelliteManager.SATELLITE_RESULT_SUCCESS);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void receiveSatelliteDatagramSuccess() {
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Receive one datagram
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        // As pending count is 0, datagram transfer state changes from
        // IDLE -> RECEIVING -> RECEIVE_SUCCESS -> IDLE.
        int expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(expectedNumOfEvents);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        stopTransmissionUpdates(transmissionUpdateCallback);
        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
    }

    private SatelliteTransmissionUpdateCallbackTest startTransmissionUpdates() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("SatelliteTransmissionUpdateCallbackTest: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return null;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        return transmissionUpdateCallback;
    }

    private void stopTransmissionUpdates(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
    }

    private void assertSingleSendDatagramStateChanged(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback,
            int expectedTransferState, int expectedPendingCount, int expectedErrorCode) {
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        expectedTransferState, expectedPendingCount, expectedErrorCode));
    }

    private void assertSingleReceiveDatagramStateChanged(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback,
            int expectedTransferState, int expectedPendingCount, int expectedErrorCode) {
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        expectedTransferState, expectedPendingCount, expectedErrorCode));
    }

    private static void enforceMockModemDeveloperSetting() {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        if (!isAllowed && !DEBUG) {
            throw new IllegalStateException(
                    "!! Enable Mock Modem before running this test !! "
                            + "Developer options => Allow Mock Modem");
        }
    }

    private void identifyRadiosSensitiveToSatelliteMode() {
        PackageManager packageManager = getContext().getPackageManager();
        List<String> satelliteModeRadiosList = new ArrayList<>();
        mBTWifiNFCSateReceiver = new BTWifiNFCStateReceiver();
        mUwbAdapterStateCallback = new UwbAdapterStateCallback();
        IntentFilter radioStateIntentFilter = new IntentFilter();

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_WIFI);
            mWifiManager = getContext().getSystemService(WifiManager.class);
            mWifiInitState = mWifiManager.isWifiEnabled();
            radioStateIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_BLUETOOTH);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBTInitState = mBluetoothAdapter.isEnabled();
            radioStateIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_NFC);
            mNfcAdapter = NfcAdapter.getDefaultAdapter(getContext().getApplicationContext());
            mNfcInitState = mNfcAdapter.isEnabled();
            radioStateIntentFilter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        }
        getContext().registerReceiver(mBTWifiNFCSateReceiver, radioStateIntentFilter);

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_UWB);
            mUwbManager = getContext().getSystemService(UwbManager.class);
            mUwbInitState = mUwbManager.isUwbEnabled();
            mUwbManager.registerAdapterStateCallback(getContext().getMainExecutor(),
                    mUwbAdapterStateCallback);
        }

        mTestSatelliteModeRadios = String.join(",", satelliteModeRadiosList);
    }

    private boolean isRadioSatelliteModeSensitive(String radio) {
        return mTestSatelliteModeRadios.contains(radio);
    }

    private boolean areAllRadiosDisabled() {
        logd("areAllRadiosDisabled");
        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_WIFI)) {
            assertFalse(mWifiManager.isWifiEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_UWB)) {
            assertFalse(mUwbManager.isUwbEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_NFC)) {
            assertFalse(mNfcAdapter.isEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_BLUETOOTH)) {
            assertFalse(mBluetoothAdapter.isEnabled());
        }

        return true;
    }

    private boolean areAllRadiosResetToInitialState() {
        logd("areAllRadiosResetToInitialState");

        if (mBTWifiNFCSateReceiver != null
                && isRadioSatelliteModeSensitive(Settings.Global.RADIO_WIFI)) {
            assertTrue(mBTWifiNFCSateReceiver.waitUntilOnWifiStateChanged());
        }

        if (mBTWifiNFCSateReceiver != null
                && isRadioSatelliteModeSensitive(Settings.Global.RADIO_NFC)) {
            assertTrue(mBTWifiNFCSateReceiver.waitUntilOnNfcStateChanged());
        }

        if (mUwbAdapterStateCallback != null
                && isRadioSatelliteModeSensitive(Settings.Global.RADIO_UWB)) {
            assertTrue(mUwbAdapterStateCallback.waitUntilOnUwbStateChanged());
        }

        if (mBTWifiNFCSateReceiver != null
                && isRadioSatelliteModeSensitive(Settings.Global.RADIO_BLUETOOTH)) {
            assertTrue(mBTWifiNFCSateReceiver.waitUntilOnBTStateChanged());
        }

        return true;
    }

    private void setRadioExpectedState() {
        // Set expected state of all radios to their initial states
        if (mBTWifiNFCSateReceiver != null) {
            mBTWifiNFCSateReceiver.setBTExpectedState(mBTInitState);
            mBTWifiNFCSateReceiver.setWifiExpectedState(mWifiInitState);
            mBTWifiNFCSateReceiver.setNfcExpectedState(mNfcInitState);
        }

        if (mUwbAdapterStateCallback != null) {
            mUwbAdapterStateCallback.setUwbExpectedState(mUwbInitState);
        }
    }

    private void unregisterSatelliteModeRadios() {
        getContext().unregisterReceiver(mBTWifiNFCSateReceiver);

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_UWB)) {
            mUwbManager.unregisterAdapterStateCallback(mUwbAdapterStateCallback);
        }
    }

    private void requestSatelliteAttachEnabledForCarrier(boolean isEnable,
            int expectedResult) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestAttachEnabledForCarrier(sTestSubIDForCarrierSatellite,
                isEnable, getContext().getMainExecutor(), resultListener::offer);
        Integer result;
        try {
            result = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestAttachEnabledForCarrier failed with ex=" + ex);
            return;
        }
        assertNotNull(result);
        assertEquals(expectedResult, (int) result);
    }

    private void requestAddSatelliteAttachRestrictionForCarrier(int reason, int expectedResult) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.addAttachRestrictionForCarrier(sTestSubIDForCarrierSatellite,
                reason, getContext().getMainExecutor(), resultListener::offer);
        Integer result;
        try {
            result = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestAddSatelliteAttachRestrictionForCarrier failed with ex=" + ex);
            return;
        }
        assertNotNull(result);
        assertEquals(expectedResult, (int) result);
    }

    private void verifySatelliteAttachRestrictionForCarrier(int reason, boolean isReasonExpected) {
        Set<Integer> restrictionReasons = sSatelliteManager
                .getAttachRestrictionReasonsForCarrier(sTestSubIDForCarrierSatellite);
        assertNotNull(restrictionReasons);
        if (isReasonExpected) {
            assertTrue(restrictionReasons.contains(reason));
        } else {
            assertFalse(restrictionReasons.contains(reason));
        }
    }

    private void requestRemoveSatelliteAttachRestrictionForCarrier(int reason, int expectedResult) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.removeAttachRestrictionForCarrier(sTestSubIDForCarrierSatellite,
                reason, getContext().getMainExecutor(), resultListener::offer);
        Integer result;
        try {
            result = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestRemoveSatelliteAttachRestrictionForCarrier failed with ex=" + ex);
            return;
        }
        assertNotNull(result);
        assertEquals(expectedResult, (int) result);
    }

    private Pair<Boolean, Integer> requestIsSatelliteAttachEnabledForCarrier() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        logd("onResult: result=" + result);
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("onError: onError=" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsAttachEnabledForCarrier(sTestSubIDForCarrierSatellite,
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(enabled.get(), callback.get());
    }

    private Pair<NtnSignalStrength, Integer> requestNtnSignalStrength() {
        final AtomicReference<NtnSignalStrength> ntnSignalStrength = new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<NtnSignalStrength, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(NtnSignalStrength result) {
                        logd("onResult: result=" + result);
                        ntnSignalStrength.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("onError: onError=" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestNtnSignalStrength(getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(ntnSignalStrength.get(), callback.get());
    }

    private Pair<SatelliteCapabilities, Integer> requestSatelliteCapabilities() {
        final AtomicReference<SatelliteCapabilities> SatelliteCapabilities =
                new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        logd("onResult: result=" + result);
                        SatelliteCapabilities.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("onError: onError=" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestCapabilities(getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(SatelliteCapabilities.get(), callback.get());
    }

    private abstract static class BaseReceiver extends BroadcastReceiver {
        protected CountDownLatch mLatch = new CountDownLatch(1);

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    private static class CarrierConfigReceiver extends BaseReceiver {
        private final int mSubId;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }
    }

    private static void overrideCarrierConfig(int subId, PersistableBundle bundle) {
        try {
            CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(CarrierConfigManager.class);
            sCarrierConfigReceiver.clearQueue();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                    (m) -> m.overrideConfig(subId, bundle));
            sCarrierConfigReceiver.waitForChanged();
        } catch (Exception ex) {
            loge("overrideCarrierConfig(), ex=" + ex);
        } finally {
            grantSatellitePermission();
        }
    }

    private void setSatelliteError(@SatelliteManager.SatelliteResult int error) {
        @RadioError int satelliteError;

        switch(error) {
            case SatelliteManager.SATELLITE_RESULT_SUCCESS:
                satelliteError = SatelliteResult.SATELLITE_RESULT_SUCCESS;
                break;
            case SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE:
                satelliteError = SatelliteResult.SATELLITE_RESULT_INVALID_MODEM_STATE;
                break;
            case SatelliteManager.SATELLITE_RESULT_MODEM_ERROR:
                satelliteError = SatelliteResult.SATELLITE_RESULT_MODEM_ERROR;
                break;
            case SatelliteManager.SATELLITE_RESULT_RADIO_NOT_AVAILABLE:
                satelliteError = SatelliteResult.SATELLITE_RESULT_RADIO_NOT_AVAILABLE;
                break;
            case SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED:
            default:
                satelliteError = SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
                break;
        }

        sMockSatelliteServiceManager.setErrorCode(satelliteError);
    }

    private void setNtnSignalStrength(
            @NtnSignalStrength.NtnSignalStrengthLevel int ntnSignalStrengthLevel) {
        sMockSatelliteServiceManager.setNtnSignalStrength(toHAL(ntnSignalStrengthLevel));
    }

    private void sendOnNtnSignalStrengthChanged(
            @NtnSignalStrength.NtnSignalStrengthLevel int ntnSignalStrengthLevel) {
        sMockSatelliteServiceManager.sendOnNtnSignalStrengthChanged(toHAL(ntnSignalStrengthLevel));
    }

    private void sendOnSatelliteCapabilitiesChanged(
            android.telephony.satellite.stub.SatelliteCapabilities satelliteCapabilities) {
        sMockSatelliteServiceManager.sendOnSatelliteCapabilitiesChanged(satelliteCapabilities);
    }

    @Nullable
    private android.telephony.satellite.stub.NtnSignalStrength toHAL(
            @NtnSignalStrength.NtnSignalStrengthLevel int signalStrengthLevelFromFramework) {
        android.telephony.satellite.stub.NtnSignalStrength ntnSignalStrength =
                new android.telephony.satellite.stub.NtnSignalStrength();

        switch(signalStrengthLevelFromFramework) {
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_NONE;
                break;
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_POOR:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_POOR;
                break;
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_MODERATE:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_MODERATE;
                break;
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_GOOD;
                break;
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_GREAT:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_GREAT;
                break;
            default:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_NONE;
                break;
        }
        return ntnSignalStrength;
    }

    private boolean getIsSatelliteEnabledForCarrierFromMockService() {
        Boolean receivedResult = sMockSatelliteServiceManager.getIsSatelliteEnabledForCarrier();
        return receivedResult != null ? receivedResult : false;
    }

    private void clearSatelliteEnabledForCarrier() {
        sMockSatelliteServiceManager.clearSatelliteEnabledForCarrier();
    }


    static int sTestSubIDForCarrierSatellite;
    static SubscriptionManager sSubscriptionManager;
    static boolean sPreviousSatelliteAttachEnabled;

    private void beforeSatelliteForCarrierTest() {
        sTestSubIDForCarrierSatellite = getActiveSubIDForCarrierSatelliteTest();
        sSubscriptionManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(SubscriptionManager.class);
        // Get the default subscription values for COLUMN_SATELLITE_ATTACH_ENABLED_FOR_CARRIER.
        sPreviousSatelliteAttachEnabled =
                sSubscriptionManager.getBooleanSubscriptionProperty(sTestSubIDForCarrierSatellite,
                        SubscriptionManager.SATELLITE_ATTACH_ENABLED_FOR_CARRIER,
                        false,
                        getContext());
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Set user Setting as false
            sSubscriptionManager.setSubscriptionProperty(sTestSubIDForCarrierSatellite,
                    SubscriptionManager.SATELLITE_ATTACH_ENABLED_FOR_CARRIER, String.valueOf(0));
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    private void afterSatelliteForCarrierTest() {
        // Set user Setting value to previous one.
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            sSubscriptionManager.setSubscriptionProperty(sTestSubIDForCarrierSatellite,
                    SubscriptionManager.SATELLITE_ATTACH_ENABLED_FOR_CARRIER,
                    sPreviousSatelliteAttachEnabled ? "1" : "0");
        } finally {
            ui.dropShellPermissionIdentity();
            sTestSubIDForCarrierSatellite = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

    private void updateSupportedRadioTechnologies(
            @NonNull int[] supportedRadioTechnologies, boolean needSetUp) {
        logd("updateSupportedRadioTechnologies: supportedRadioTechnologies="
                + supportedRadioTechnologies[0]);
        grantSatellitePermission();

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
        waitFor(2000);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setSupportedRadioTechnologies(supportedRadioTechnologies);
        try {
            setupMockSatelliteService();
            if (needSetUp) {
                setUp();
            }
        } catch (Exception e) {
            loge("Fail to set up mock satellite service after updating supported radio "
                    + "technologies, e=" + e);
        }

        revokeSatellitePermission();
    }

    private Pair<Boolean, Integer> requestIsCommunicationAllowedForCurrentLocation() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        logd("isSatelliteAllowed.onResult: result=" + result);
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("isSatelliteAllowed.onError: onError=" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsCommunicationAllowedForCurrentLocation(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("isSatelliteAllowed: ex=" + e);
        }
        return new Pair<>(enabled.get(), callback.get());
    }

    private void verifyIsSatelliteAllowed(boolean allowed) {
        grantSatellitePermission();
        Pair<Boolean, Integer> result =
                requestIsCommunicationAllowedForCurrentLocation();
        assertNotNull(result.first);
        assertEquals(allowed, result.first);
    }

    private static void registerTestLocationProvider() {
        requestMockLocationPermission(true);
        sLocationManager.addTestProvider(TEST_PROVIDER,
                new ProviderProperties.Builder().build());
        sLocationManager.setTestProviderEnabled(TEST_PROVIDER, true);
    }

    private static void unregisterTestLocationProvider() {
        requestMockLocationPermission(true);
        sLocationManager.removeTestProvider(TEST_PROVIDER);
        requestMockLocationPermission(false);
    }

    private void setTestProviderLocation(double latitude, double longitude) {
        requestMockLocationPermission(true);
        Location loc = LocationUtils.createLocation(
                TEST_PROVIDER, latitude, longitude, LOCATION_ACCURACY);
        sLocationManager.setTestProviderLocation(TEST_PROVIDER, loc);
    }

    private static void requestMockLocationPermission(boolean allowed) {
        AppOpsManager aom = getContext().getSystemService(AppOpsManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(aom, (appOpsMan) -> appOpsMan
                .setUidMode(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(),
                        allowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED));
    }

    private static void setUpSatelliteAccessAllowed() {
        logd("setUpSatelliteAccessAllowed...");
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false,
                OVERRIDING_COUNTRY_CODES, null, null, 0));
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                false, true, null, 0, SATELLITE_COUNTRY_CODES));
    }

    private static void resetSatelliteAccessControlOverlayConfigs() {
        logd("resetSatelliteAccessControlOverlayConfigs");
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(true, null, null, null, 0));
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                true, true, null, 0, null));
    }
}
