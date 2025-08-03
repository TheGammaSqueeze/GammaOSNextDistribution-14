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

import static android.telephony.ims.cts.TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION_BY_RADIO;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REASON_SIM_REFRESH;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * CTS tests for MmTelFeature API.
 */
@RunWith(AndroidJUnit4.class)
public class ImsServiceTestOnMockModem {
    private static final String LOG_TAG = "ImsServiceTestOnMockModem";
    private static final boolean VDBG = false;

    // the timeout to wait for result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 2000;

    // the timeout to wait for sim state change in milliseconds
    private static final int WAIT_SIM_STATE_TIMEOUT_MS = 3000;

    private static ImsServiceConnector sServiceConnector;
    private static MockModemManager sMockModemManager;

    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private static boolean sSupportsImsHal = false;

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "beforeAllTests");

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);

        Pair<Integer, Integer> halVersion = tm.getHalVersion(TelephonyManager.HAL_SERVICE_IMS);
        if (!(halVersion.equals(TelephonyManager.HAL_VERSION_UNKNOWN)
                || halVersion.equals(TelephonyManager.HAL_VERSION_UNSUPPORTED))) {
            sSupportsImsHal = true;
        }

        if (!sSupportsImsHal) {
            return;
        }

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService(MOCK_SIM_PROFILE_ID_TWN_CHT));

        TimeUnit.MILLISECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_MS);

        int simCardState = tm.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        // Check SIM state ready
        simCardState = tm.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        sTestSub = ImsUtils.getPreferredActiveSubId();

        int sub = SubscriptionManager.getSubscriptionId(sTestSlot);
        if (SubscriptionManager.isValidSubscriptionId(sub)) {
            if (VDBG) Log.i(LOG_TAG, "beforeAllTests sub=" + sub);
            sTestSub = sub;
        }

        if (VDBG) Log.i(LOG_TAG, "sTestSub=" + sTestSub);

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());

        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(sTestSlot);
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "afterAllTests");

        if (!sSupportsImsHal) {
            return;
        }

        // Restore all ImsService configurations that existed before the test.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectServices();
        }
        sServiceConnector = null;

        // Rebind all interfaces which is binding to MockModemService to default.
        if (sMockModemManager != null) {
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;

            TimeUnit.MILLISECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_MS);
        }
    }

    @Before
    public void beforeTest() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "beforeTest");

        assumeTrue(ImsUtils.shouldTestImsService());
        assumeTrue(sSupportsImsHal);
    }

    @After
    public void afterTest() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "afterTest");

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!sSupportsImsHal) {
            return;
        }

        // Unbind the ImsService after the test completes.
        if (sServiceConnector != null) {
            sServiceConnector.setSingleRegistrationTestModeEnabled(false);
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.disconnectDeviceImsService();
        }
    }

    @Ignore("Internal use only. Ignore this test until system API is added")
    @Test
    public void testTriggerImsDeregistration() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testNotifySrvccState");

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(0);

        sServiceConnector.getCarrierService().getImsRegistration()
                .resetDeregistrationTriggeredByRadio();

        assertTrue(sMockModemManager.triggerImsDeregistration(sTestSlot, REASON_SIM_REFRESH));
        sServiceConnector.getCarrierService().getImsRegistration().waitForLatchCountDown(
                LATCH_TRIGGER_DEREGISTRATION_BY_RADIO, WAIT_UPDATE_TIMEOUT_MS);

        assertTrue(sServiceConnector.getCarrierService()
                .getImsRegistration().isDeregistrationTriggeredByRadio());
        assertEquals(REASON_SIM_REFRESH,
                sServiceConnector.getCarrierService()
                        .getImsRegistration().getDeregistrationTriggeredByRadioReason());
    }

    private void triggerFrameworkConnectToCarrierImsService(long capabilities) throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().addCapabilities(capabilities);
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(
                new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue("Did not receive createMmTelFeature", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_MMTEL));
        assertTrue("Did not receive MmTelFeature#onReady", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_MMTEL_READY));
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());
        int serviceSlot = sServiceConnector.getCarrierService().getMmTelFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated MmTelFeature",
                sTestSlot, serviceSlot);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }
}
