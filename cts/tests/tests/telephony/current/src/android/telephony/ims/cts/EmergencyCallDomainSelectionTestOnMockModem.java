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

package android.telephony.ims.cts;

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.GERAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.NGRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UNKNOWN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UTRAN;
import static android.telephony.BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY;
import static android.telephony.BarringInfo.BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CALL_SETUP_TIMER_ON_CURRENT_NETWORK_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CDMA_PREFERRED_NUMBERS_STRING_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_LTE_PREFERRED_AFTER_NR_FAILED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_SCAN_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_MAXIMUM_NUMBER_OF_EMERGENCY_TRIES_OVER_VOWIFI_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.REDIAL_TIMER_DISABLED;
import static android.telephony.CarrierConfigManager.ImsEmergency.SCAN_TYPE_NO_PREFERENCE;
import static android.telephony.CarrierConfigManager.ImsWfc.KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN;
import static android.telephony.mockmodem.IRadioVoiceImpl.LATCH_EMERGENCY_DIAL;
import static android.telephony.mockmodem.IRadioVoiceImpl.LATCH_GET_LAST_CALL_FAIL_CAUSE;
import static android.telephony.mockmodem.MockNetworkService.LATCH_CANCEL_EMERGENCY_SCAN;
import static android.telephony.mockmodem.MockNetworkService.LATCH_TRIGGER_EMERGENCY_SCAN;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.cts.TestUtils;
import android.telephony.AccessNetworkConstants;
import android.telephony.BarringInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.DomainSelectionService;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PreciseCallState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.mockmodem.MockEmergencyRegResult;
import android.telephony.mockmodem.MockModemManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for DomainSelection.
 */
@RunWith(AndroidJUnit4.class)
public class EmergencyCallDomainSelectionTestOnMockModem extends ImsCallingBase {

    private static final String LOG_TAG = "CtsEmergencyCallDomainSelectionTestOnMockModem";
    private static final boolean VDBG = false;

    private static final String TEST_DOMAIN_SELECTION_PROPERTY =
            "persist.radio.test_domain_selection";

    private static final String TEST_EMERGENCY_NUMBER = "998877665544332211";

    private static final int EMERGENCY_MODE_WWAN = 1;
    private static final int EMERGENCY_MODE_WLAN = 2;

    // the timeout to wait for latch countdonw in milliseconds
    private static final int WAIT_LATCH_TIMEOUT_MS = 10000;

    // the timeout to wait for result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 5000;

    // the timeout to wait for request in milliseconds
    private static final int WAIT_REQUEST_TIMEOUT_MS = 3000;

    /** Default value. */
    private static final int MODE_EMERGENCY_NONE = 0;
    /** The current domain selected for the Emergency call is cellular. */
    private static final int MODE_EMERGENCY_WWAN = 1;
    /** The current domain selected for the Emergency call is WLAN/WIFI. */
    private static final int MODE_EMERGENCY_WLAN = 2;
    /** The current mode set request is for emergency callback. */
    private static final int MODE_EMERGENCY_CALLBACK = 3;

    private static MockModemManager sMockModemManager;

    private static boolean sSupportDomainSelection = false;;

    private static boolean sVoLteEnabled = false;

    static {
        initializeLatches();
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        TelephonyManager telephonyManager = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);

        sSupportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(telephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());

        if (!sSupportDomainSelection) {
            return;
        }

        TestUtils.setSystemDialerOverride(
                InstrumentationRegistry.getInstrumentation(), INCALL_COMPONENT);

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService(MOCK_SIM_PROFILE_ID_TWN_CHT));

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        int simCardState = telephonyManager.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        // Check SIM state ready
        simCardState = telephonyManager.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        sTestSub = ImsUtils.getPreferredActiveSubId();

        int sub = SubscriptionManager.getSubscriptionId(sTestSlot);
        if (SubscriptionManager.isValidSubscriptionId(sub)) {
            sTestSub = sub;
        }

        assertTrue(sMockModemManager.changeNetworkService(sTestSlot, 310260, true));

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        beforeAllTestsBase();

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        sVoLteEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mmTelManager,
                ImsMmTelManager::isAdvancedCallingSettingEnabled);

        sMockModemManager.notifyEmergencyNumberList(sTestSlot,
                new String[] { TEST_EMERGENCY_NUMBER });
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!sSupportDomainSelection) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                (m) -> m.setAdvancedCallingSettingEnabled(sVoLteEnabled));

        afterAllTestsBase();

        // Rebind all interfaces which is binding to MockModemService to default.
        if (sMockModemManager != null) {
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;

            TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        }

        TestUtils.clearSystemDialerOverride(InstrumentationRegistry.getInstrumentation());
    }

    @Before
    public void beforeTest() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY));
        assumeTrue(ImsUtils.shouldTestImsService());
        assumeTrue(sSupportDomainSelection);

        if (sMockModemManager != null) {
            unsolBarringInfoChanged(false);
            sMockModemManager.setLastCallFailCause(sTestSlot, DisconnectCause.POWER_OFF);
            sMockModemManager.resetNetworkAllLatchCountdown(sTestSlot);
            sMockModemManager.resetVoiceAllLatchCountdown(sTestSlot);
        }
    }

    @After
    public void afterTest() throws Exception {
        if (!mCalls.isEmpty() && (mCurrentCallId != null)) {
            Call call = mCalls.get(mCurrentCallId);
            call.disconnect();
        }

        if (sMockModemManager != null) {
            sMockModemManager.clearAllCalls(sTestSlot, DisconnectCause.POWER_OFF);
            sMockModemManager.resetEmergencyNetworkScan(sTestSlot);
            unsolBarringInfoChanged(false, true);
            waitForVoiceLatchCountdown(LATCH_GET_LAST_CALL_FAIL_CAUSE);
        }

        if (mServiceCallBack != null && mServiceCallBack.getService() != null) {
            waitForUnboundService();
        }

        //Set the untracked CountDownLatches which are reseted in ServiceCallBack
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }

        if (sServiceConnector != null && sIsBound) {
            TestImsService imsService = sServiceConnector.getCarrierService();
            sServiceConnector.disconnectCarrierImsService();
            sIsBound = false;
            imsService.waitForExecutorFinish();
        }

        TelephonyUtils.endBlockSuppression(InstrumentationRegistry.getInstrumentation());
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsRegisteredBarredSelectCs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsRegisteredSelectPs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyPsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsNotRegisteredSelectCs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsNotRegisteredBarredSelectCs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsRegisteredEmsOffBarredSelectCs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsRegisteredEmsOffSelectCs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsNotRegisteredEmsOffSelectCs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsNotRegisteredEmsOffBarredSelectCs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsRegisteredVopsOffBarredSelectCs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsRegisteredVopsOffSelectCs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsNotRegisteredVopsOffSelectCs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsNotRegisteredVopsOffBarredSelectCs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsRegisteredVopsOffEmsOffBarredSelectCs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsRegisteredVopsOffEmsOffSelectCs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsNotRegisteredVopsOffEmsOffSelectCs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCombinedImsNotRegisteredVopsOffEmsOffBarredSelectCs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCsSelectCs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(UTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsRegisteredBarredScanPsPreferred() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsRegisteredSelectPs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyPsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsNotRegisteredSelectPs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsNotRegisteredBarredSelectScanPsPreferred() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsRegisteredEmsOffBarredScanPsPreferred() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsRegisteredEmsOffScanPsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsNotRegisteredEmsOffScanPsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsNotRegisteredEmsOffBarredScanPsPreferred() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsRegisteredVopsOffBarredScanPsPreferred() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsRegisteredVopsOffScanPsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsNotRegisteredVopsOffScanPsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsNotRegisteredVopsOffBarredScanPsPreferred() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsRegisteredVopsOffEmsOffBarredScanPsPreferred() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsRegisteredVopsOffEmsOffScanPsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsImsNotRegisteredVopsOffEmsOffScanPsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultEpsNotRegisteredVopsOffEmsOffBarredScanPsPreferred() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultOutOfServiceScanPsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testVoLteOnEpsImsNotRegisteredSelectPs() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL, true);
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                (m) -> m.setAdvancedCallingSettingEnabled(true));

        verifyPsDialed();
    }

    @Ignore("For internal test only.")
    @Test
    public void testVoLteOffEpsImsNotRegisteredScanCsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL, true);
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                (m) -> m.setAdvancedCallingSettingEnabled(false));

        verifyScanCsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testRequiresRegEpsImsNotRegisteredScanCsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL, true);
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyScanCsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCsSelectCsFailedRescanPsPreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(UTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsServiceUnregistered();

        verifyCsDialed();

        sMockModemManager.clearAllCalls(sTestSlot, DisconnectCause.CONGESTION);

        waitForVoiceLatchCountdown(LATCH_GET_LAST_CALL_FAIL_CAUSE);

        verifyRescanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultWifiImsRegisteredScanTimeoutSelectWifiImsPdn() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_EMERGENCY_SCAN_TIMER_SEC_INT, 3);
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, false);
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);

        placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        assertTrue(waitForNetworkLatchCountdown(LATCH_TRIGGER_EMERGENCY_SCAN));
        assertEquals(EMERGENCY_MODE_WWAN, sMockModemManager.getEmergencyMode(sTestSlot));
        assertTrue(waitForNetworkLatchCountdown(LATCH_CANCEL_EMERGENCY_SCAN));

        TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);

        assertEquals(EMERGENCY_MODE_WLAN, sMockModemManager.getEmergencyMode(sTestSlot));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();

        assertNotNull(callSession);
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultWifiImsRegisteredCellularTimeoutSelectWifiImsPdn() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_EMERGENCY_SCAN_TIMER_SEC_INT, 0);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 3);
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, false);
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);

        placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        assertTrue(waitForNetworkLatchCountdown(LATCH_TRIGGER_EMERGENCY_SCAN));
        assertEquals(EMERGENCY_MODE_WWAN, sMockModemManager.getEmergencyMode(sTestSlot));
        assertTrue(waitForNetworkLatchCountdown(LATCH_CANCEL_EMERGENCY_SCAN));

        TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);

        assertEquals(EMERGENCY_MODE_WLAN, sMockModemManager.getEmergencyMode(sTestSlot));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();

        assertNotNull(callSession);
    }

    @Ignore("For internal test only.")
    @Test
    public void testDefaultCsThenPs() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(true);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        assertTrue(waitForVoiceLatchCountdown(LATCH_EMERGENCY_DIAL));

        sMockModemManager.clearAllCalls(sTestSlot, DisconnectCause.CONGESTION);
        unsolBarringInfoChanged(false, true);
        waitForVoiceLatchCountdown(LATCH_GET_LAST_CALL_FAIL_CAUSE);

        TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);

        assertTrue(sMockModemManager.isEmergencyNetworkScanTriggered(sTestSlot));

        unsolEmergencyNetworkScanResult(EUTRAN);
    }

    @Ignore("For internal test only.")
    @Test
    public void testNrEpsImsRegisteredEmcOffEmsOnScanLtePreferred() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        // NR has higher priority than LTE in configuration.
        bundle.putIntArray(
                KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                new int[] { NGRAN, EUTRAN });
        overrideCarrierConfig(bundle);

        // EMC=0, EMF=1, expect EPS fallback
        MockEmergencyRegResult regResult = getEmergencyRegResult(NGRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 1, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        bindImsService();

        // LTE should have higher priority in scan list to trigger EPS fallback.
        verifyScanPsPreferred();
    }

    @Ignore("For internal test only.")
    @Test
    public void testOutGoingEmergencyCall() throws Exception {
        // Setup pre-condition
        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);

        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange();

        TelephonyManager telephonyManager = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                telephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        // Place outgoing emergency call
        placeOutgoingCall(TEST_EMERGENCY_NUMBER);
        TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);

        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        assertTrue(testCb.waitForOutgoingEmergencyCall(TEST_EMERGENCY_NUMBER));
        assertTrue(testCb.waitForCallActive());

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();
        isCallActive(call, callSession);

        call.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                telephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
    }

    private class TestTelephonyCallbackForCallStateChange extends TelephonyCallback implements
            TelephonyCallback.PreciseCallStateListener,
            TelephonyCallback.OutgoingEmergencyCallListener {

        private EmergencyNumber mLastOutgoingEmergencyNumber;
        private Semaphore mOutgoingEmergencyCallSemaphore = new Semaphore(0);
        private Semaphore mActiveCallStateSemaphore = new Semaphore(0);

        @Override
        public void onPreciseCallStateChanged(@NonNull PreciseCallState callState) {
            Log.i(LOG_TAG, "onPreciseCallStateChanged: state=" + callState);
            if (callState.getForegroundCallState() == PreciseCallState.PRECISE_CALL_STATE_ACTIVE) {
                mActiveCallStateSemaphore.release();
            }
        }

        @Override
        public void onOutgoingEmergencyCall(EmergencyNumber emergencyNumber, int subscriptionId) {
            Log.i(LOG_TAG, "onOutgoingEmergencyCall: emergencyNumber=" + emergencyNumber);
            mLastOutgoingEmergencyNumber = emergencyNumber;
            mOutgoingEmergencyCallSemaphore.release();
        }

        public boolean waitForCallActive() {
            try {
                if (!mActiveCallStateSemaphore.tryAcquire(
                        WAIT_LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(LOG_TAG, "Timed out to receive active call state");
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(LOG_TAG, "waitForCallActive: ex=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitForOutgoingEmergencyCall(String expectedNumber) {
            try {
                if (!mOutgoingEmergencyCallSemaphore.tryAcquire(
                        WAIT_LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(LOG_TAG, "Timed out to receive OutgoingEmergencyCall");
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(LOG_TAG, "waitForOutgoingEmergencyCall: ex=" + ex);
                return false;
            }

            // At this point we can only be sure that we got AN update, but not necessarily the one
            // we are looking for; wait until we see the state we want before verifying further.
            waitUntilConditionIsTrueOrTimeout(
                    new Condition() {
                        @Override
                        public Object expected() {
                            return true;
                        }

                        @Override
                        public Object actual() {
                            return mLastOutgoingEmergencyNumber != null
                                    && mLastOutgoingEmergencyNumber.getNumber().equals(
                                            expectedNumber);
                        }
                    },
                    WAIT_LATCH_TIMEOUT_MS,
                    "Expected emergency number: " + expectedNumber);
            return TextUtils.equals(expectedNumber, mLastOutgoingEmergencyNumber.getNumber());
        }
    }

    private void verifyCsDialed() throws Exception {
        placeOutgoingCall(TEST_EMERGENCY_NUMBER);
        assertTrue(isCsDialing());
    }

    private boolean isCsDialing() throws Exception {
        boolean isDialing = waitForVoiceLatchCountdown(LATCH_EMERGENCY_DIAL);

        if (!isDialing && sMockModemManager.isEmergencyNetworkScanTriggered(sTestSlot)) {
            // Unexpected emergency network scan is requested.
            unsolEmergencyNetworkScanResult(UTRAN, true);
            waitForVoiceLatchCountdown(LATCH_EMERGENCY_DIAL);
        }

        return isDialing;
    }

    private void verifyPsDialed() throws Exception {
        placeOutgoingCall(TEST_EMERGENCY_NUMBER);
        TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);
        assertTrue(isPsDialing());
    }

    private boolean isPsDialing() throws Exception {
        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();

        if (callSession == null && sMockModemManager.isEmergencyNetworkScanTriggered(sTestSlot)) {
            // Unexpected emergency network scan is requested.
            unsolEmergencyNetworkScanResult(UTRAN, true);
            waitForVoiceLatchCountdown(LATCH_EMERGENCY_DIAL);
        }

        return callSession != null;
    }

    private void verifyScanPsPreferred() throws Exception {
        verifyScanPreferred(true, DomainSelectionService.SCAN_TYPE_NO_PREFERENCE, EUTRAN, UTRAN);
    }

    private void verifyScanCsPreferred() throws Exception {
        verifyScanPreferred(true, DomainSelectionService.SCAN_TYPE_NO_PREFERENCE, UTRAN, UTRAN);
    }

    private void verifyRescanPsPreferred() throws Exception {
        verifyScanPreferred(false, DomainSelectionService.SCAN_TYPE_NO_PREFERENCE, EUTRAN, UTRAN);
    }

    private void verifyScanPreferred(boolean dial, int expectedScanType,
            int expectedPreferredAccessNetwork, int scannedAccessNetwork) throws Exception {
        sMockModemManager.setLastCallFailCause(sTestSlot, DisconnectCause.POWER_OFF);

        if (dial) {
            placeOutgoingCall(TEST_EMERGENCY_NUMBER);
        }
        assertTrue(waitForNetworkLatchCountdown(LATCH_TRIGGER_EMERGENCY_SCAN));

        int scanType = sMockModemManager.getEmergencyNetworkScanType(sTestSlot);
        int[] accessNetwork = sMockModemManager.getEmergencyNetworkScanAccessNetwork(sTestSlot);

        unsolEmergencyNetworkScanResult(scannedAccessNetwork);

        assertEquals(expectedScanType, scanType);
        assertNotNull(accessNetwork);
        assertEquals(expectedPreferredAccessNetwork, accessNetwork[0]);
    }

    private void unsolEmergencyNetworkScanResult(int scannedAccessNetwork) throws Exception {
        unsolEmergencyNetworkScanResult(scannedAccessNetwork, false);
    }

    private void unsolEmergencyNetworkScanResult(int scannedAccessNetwork, boolean noAssert)
            throws Exception {
        if (scannedAccessNetwork == EUTRAN) {
            MockEmergencyRegResult regResult = getEmergencyRegResult(
                    EUTRAN, REGISTRATION_STATE_HOME,
                    NetworkRegistrationInfo.DOMAIN_PS, true, true, 0, 0, "", "");
            sMockModemManager.unsolEmergencyNetworkScanResult(sTestSlot, regResult);
            TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);
            if (!noAssert) {
                assertNotNull(sServiceConnector.getCarrierService().getMmTelFeature()
                        .getImsCallsession());
            }
        } else if (scannedAccessNetwork == NGRAN) {
            MockEmergencyRegResult regResult = getEmergencyRegResult(
                    NGRAN, REGISTRATION_STATE_HOME,
                    NetworkRegistrationInfo.DOMAIN_PS, true, false, 1, 0, "", "");
            sMockModemManager.unsolEmergencyNetworkScanResult(sTestSlot, regResult);
            TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);
            if (!noAssert) {
                assertNotNull(sServiceConnector.getCarrierService().getMmTelFeature()
                        .getImsCallsession());
            }
        } else {
            MockEmergencyRegResult regResult = getEmergencyRegResult(scannedAccessNetwork,
                    REGISTRATION_STATE_HOME, NetworkRegistrationInfo.DOMAIN_CS,
                    false, false, 0, 0, "", "");
            sMockModemManager.unsolEmergencyNetworkScanResult(sTestSlot, regResult);
            boolean isDialing = waitForVoiceLatchCountdown(LATCH_EMERGENCY_DIAL);
            if (!noAssert) assertTrue(isDialing);
        }
    }

    private void placeOutgoingCall(String address) throws Exception {
        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, address, null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
    }

    public void bindImsServiceUnregistered() throws Exception  {
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                .build()));
        sIsBound = true;
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL);
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());

        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);

        // Set Deregistered
        sServiceConnector.getCarrierService().getImsService().getRegistrationForSubscription(
                sTestSlot, sTestSub).onDeregistered(
                        new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                                ImsReasonInfo.CODE_UNSPECIFIED, ""));

        Thread.sleep(3000);
    }

    private static PersistableBundle getDefaultPersistableBundle() {
        int[] imsRats = new int[] { EUTRAN };
        int[] csRats = new int[] { UTRAN, GERAN };
        int[] imsRoamRats = new int[] { EUTRAN };
        int[] csRoamRats = new int[] { UTRAN, GERAN };
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_NON_3GPP
                };
        int[] roamDomainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_NON_3GPP
                };
        boolean imsWhenVoiceOnCs = false;
        int maxRetriesOverWiFi = 1;
        int cellularScanTimerSec = 10;
        int maxCellularTimerSec = 0;
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        boolean useEmergencyPdn = true;
        boolean requiresImsRegistration = false;
        boolean requiresVoLteEnabled = false;
        boolean ltePreferredAfterNrFailed = false;
        String[] cdmaPreferredNumbers = new String[] {};
        int crossStackTimer = REDIAL_TIMER_DISABLED;
        int quickCrossStackTimer = REDIAL_TIMER_DISABLED;
        boolean quickTimerWhenInService = true;

        return getPersistableBundle(imsRats, csRats, imsRoamRats, csRoamRats,
                domainPreference, roamDomainPreference, imsWhenVoiceOnCs, maxRetriesOverWiFi,
                useEmergencyPdn, cellularScanTimerSec, maxCellularTimerSec,
                scanType, requiresImsRegistration,
                requiresVoLteEnabled, ltePreferredAfterNrFailed, cdmaPreferredNumbers,
                crossStackTimer, quickCrossStackTimer, quickTimerWhenInService);
    }

    private static PersistableBundle getPersistableBundle(
            @Nullable int[] imsRats, @Nullable int[] csRats,
            @Nullable int[] imsRoamRats, @Nullable int[] csRoamRats,
            @Nullable int[] domainPreference, @Nullable int[] roamDomainPreference,
            boolean imsWhenVoiceOnCs, int maxRetriesOverWiFi, boolean useEmergencyPdn,
            int cellularScanTimerSec, int maxCellularTimerSec,
            int scanType, boolean requiresImsRegistration,
            boolean requiresVoLteEnabled, boolean ltePreferredAfterNrFailed,
            @Nullable String[] cdmaPreferredNumbers,
            int crossStackTimer, int quickCrossStackTimer, boolean quickTimerWhenInService) {

        PersistableBundle bundle  = new PersistableBundle();
        if (imsRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY, imsRats);
        }
        if (imsRoamRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                    imsRoamRats);
        }
        if (csRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY, csRats);
        }
        if (csRoamRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                    csRoamRats);
        }
        if (domainPreference != null) {
            bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        }
        if (roamDomainPreference != null) {
            bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY,
                    roamDomainPreference);
        }
        bundle.putBoolean(KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL, imsWhenVoiceOnCs);
        bundle.putInt(KEY_MAXIMUM_NUMBER_OF_EMERGENCY_TRIES_OVER_VOWIFI_INT, maxRetriesOverWiFi);
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, useEmergencyPdn);
        bundle.putInt(KEY_EMERGENCY_SCAN_TIMER_SEC_INT, cellularScanTimerSec);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, maxCellularTimerSec);
        bundle.putInt(KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT, scanType);
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL, requiresImsRegistration);
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL, requiresVoLteEnabled);
        bundle.putInt(KEY_EMERGENCY_CALL_SETUP_TIMER_ON_CURRENT_NETWORK_SEC_INT, 0);
        bundle.putBoolean(KEY_EMERGENCY_LTE_PREFERRED_AFTER_NR_FAILED_BOOL,
                ltePreferredAfterNrFailed);

        if (cdmaPreferredNumbers != null) {
            bundle.putStringArray(KEY_EMERGENCY_CDMA_PREFERRED_NUMBERS_STRING_ARRAY,
                    cdmaPreferredNumbers);
        }

        bundle.putInt(KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT, crossStackTimer);
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, quickCrossStackTimer);
        bundle.putBoolean(KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL,
                quickTimerWhenInService);

        return bundle;
    }

    private static MockEmergencyRegResult getEmergencyRegResult(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork,
            @NetworkRegistrationInfo.RegistrationState int regState,
            @NetworkRegistrationInfo.Domain int domain,
            boolean isVopsSupported, boolean isEmcBearerSupported, int emc, int emf,
            @NonNull String mcc, @NonNull String mnc) {
        return new MockEmergencyRegResult(accessNetwork, regState,
                domain, isVopsSupported, isEmcBearerSupported,
                emc, emf, mcc, mnc);
    }

    private static void unsolBarringInfoChanged(boolean barred) {
        unsolBarringInfoChanged(barred, false);
    }

    private static void unsolBarringInfoChanged(boolean barred, boolean noAssert) {
        SparseArray<BarringInfo.BarringServiceInfo> serviceInfos = new SparseArray<>();
        if (barred) {
            serviceInfos.put(BARRING_SERVICE_TYPE_EMERGENCY,
                    new BarringInfo.BarringServiceInfo(BARRING_TYPE_UNCONDITIONAL, false, 0, 0));
        }
        if (noAssert) {
            sMockModemManager.unsolBarringInfoChanged(sTestSlot, serviceInfos);
        } else {
            assertTrue(sMockModemManager.unsolBarringInfoChanged(sTestSlot, serviceInfos));
        }
    }

    public boolean waitForNetworkLatchCountdown(int latchIndex) {
        return waitForNetworkLatchCountdown(latchIndex, WAIT_LATCH_TIMEOUT_MS);
    }

    public boolean waitForNetworkLatchCountdown(int latchIndex, int waitMs) {
        return sMockModemManager.waitForNetworkLatchCountdown(sTestSlot, latchIndex, waitMs);
    }

    public boolean waitForVoiceLatchCountdown(int latchIndex) {
        return waitForVoiceLatchCountdown(latchIndex, WAIT_LATCH_TIMEOUT_MS);
    }

    public boolean waitForVoiceLatchCountdown(int latchIndex, int waitMs) {
        return sMockModemManager.waitForVoiceLatchCountdown(sTestSlot, latchIndex, waitMs);
    }
}
