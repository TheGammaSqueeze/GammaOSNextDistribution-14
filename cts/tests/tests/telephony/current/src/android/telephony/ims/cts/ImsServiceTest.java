/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_NONE;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_TRIGGER_RAT_BLOCK;
import static android.telephony.ims.RegistrationManager.SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NONE;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.PhoneAccount;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.AsyncSmsMessageListener;
import android.telephony.cts.CarrierCapability;
import android.telephony.cts.SmsReceiverHelper;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.MediaThreshold;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.PublishAttributes;
import android.telephony.ims.RcsClientConfiguration;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.RtpHeaderExtensionType;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipDetails;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.telephony.ims.stub.CapabilityExchangeEventListener;
import android.telephony.ims.stub.ImsConfigImplBase;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for ImsService API.
 */
@RunWith(AndroidJUnit4.class)
public class ImsServiceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static ImsServiceConnector sServiceConnector;

    private static final int KEY_VOLTE_PROVISIONING_STATUS =
            ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS;
    private static final int KEY_VT_PROVISIONING_STATUS =
            ProvisioningManager.KEY_VT_PROVISIONING_STATUS;
    private static final int KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE =
            ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE;
    private static final int KEY_EAB_PROVISIONING_STATUS =
            ProvisioningManager.KEY_EAB_PROVISIONING_STATUS;

    private static final int MMTEL_CAP_VOICE = MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE;
    private static final int MMTEL_CAP_VIDEO = MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO;
    private static final int MMTEL_CAP_UT = MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT;
    private static final int MMTEL_CAP_SMS = MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS;
    private static final int MMTEL_CAP_COMPOSER =
            MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER;

    private static final int RCS_CAP_NONE = RcsImsCapabilities.CAPABILITY_TYPE_NONE;
    private static final int RCS_CAP_OPTIONS = RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE;
    private static final int RCS_CAP_PRESENCE = RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE;

    private static final int IMS_REGI_TECH_NONE = ImsRegistrationImplBase.REGISTRATION_TECH_NONE;
    private static final int IMS_REGI_TECH_LTE = ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
    private static final int IMS_REGI_TECH_IWLAN = ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
    private static final int IMS_REGI_TECH_CROSS_SIM =
            ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM;
    private static final int IMS_REGI_TECH_NR = ImsRegistrationImplBase.REGISTRATION_TECH_NR;

    private static final String SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING =
            "SUPPORT_PROVISION_STATUS_FOR_CAPABILITY";

    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final String MSG_CONTENTS = "hi";
    private static final String EXPECTED_RECEIVED_MESSAGE = "foo5";
    private static final String DEST_NUMBER = "5555554567";
    private static final String SRC_NUMBER = "5555551234";
    private static final byte[] EXPECTED_PDU =
            new byte[]{1, 0, 10, -127, 85, 85, 85, 33, 67, 0, 0, 2, -24, 52};
    private static final String RECEIVED_MESSAGE = "B5EhYBMDIPgEC5FhBWKFkPEAAEGQQlGDUooE5ve7Bg==";
    private static final byte[] STATUS_REPORT_PDU =
            hexStringToByteArray("0006000681214365919061800000639190618000006300");
    private static final byte[] CLASS2SMPP_PDU =
            hexStringToByteArray("07914151551512f221110A8178563412107FF20666B2996C2603");
    private static final int EXPECTED_MESSAGEREF = 0x11;
    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static boolean sDeviceUceEnabled;
    private static boolean sSupportsImsHal = false;

    private static final int TEST_CONFIG_KEY = 1000;
    private static final int TEST_CONFIG_VALUE_INT = 0xDEADBEEF;
    private static final String TEST_CONFIG_VALUE_STRING = "DEADBEEF";

    private static final String SUPPORT_PUBLISHING_STATE_STRING = "SUPPORT_PUBLISHING_STATE";

    private static final String TEST_RCS_CONFIG_DEFAULT = "<?xml version=\"1.0\"?>\n"
            + "<wap-provisioningdoc version=\"1.1\">\n"
            + "\t<characteristic type=\"APPLICATION\">\n"
            + "\t\t<parm name=\"AppID\" value=\"urn:oma:mo:ext-3gpp-ims:1.0\"/>\n"
            + "\t\t<characteristic type=\"3GPP_IMS\">\n"
            + "\t\t\t<parm name=\"AppID\" value=\"ap2001\"/>\n"
            + "\t\t\t<parm name=\"Name\" value=\"RCS IMS Settings\"/>\n"
            + "\t\t\t<characteristic type=\"Ext\">\n"
            + "\t\t\t\t<characteristic type=\"GSMA\">\n"
            + "\t\t\t\t\t<parm name=\"AppRef\" value=\"IMS-Setting\"/>\n"
            + "\t\t\t\t\t<parm name=\"rcsVolteSingleRegistration\" value=\"1\"/>\n"
            + "\t\t\t\t</characteristic>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t</characteristic>\n"
            + "\t\t<characteristic type=\"SERVICES\">\n"
            + "\t\t\t<parm name=\"SupportedRCSProfileVersions\" value=\"UP2.3\"/>\n"
            + "\t\t\t<parm name=\"ChatAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"GroupChatAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"ftAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"standaloneMsgAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"geolocPushAuth\" value=\"1\"/>\n"
            + "\t\t\t<characteristic type=\"Ext\">\n"
            + "\t\t\t\t<characteristic type=\"DataOff\">\n"
            + "\t\t\t\t\t<parm name=\"rcsMessagingDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"fileTransferDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"mmsDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"syncDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<characteristic type=\"Ext\"/>\n"
            + "\t\t\t\t</characteristic>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t</characteristic>\n"
            + "\t</characteristic>\n"
            + "</wap-provisioningdoc>\n";

    private static final String TEST_RCS_CONFIG_SINGLE_REGISTRATION_DISABLED =
            "<?xml version=\"1.0\"?>\n"
            + "<wap-provisioningdoc version=\"1.1\">\n"
            + "\t<characteristic type=\"APPLICATION\">\n"
            + "\t\t<parm name=\"AppID\" value=\"urn:oma:mo:ext-3gpp-ims:1.0\"/>\n"
            + "\t\t<characteristic type=\"3GPP_IMS\">\n"
            + "\t\t\t<parm name=\"AppID\" value=\"ap2001\"/>\n"
            + "\t\t\t<parm name=\"Name\" value=\"RCS IMS Settings\"/>\n"
            + "\t\t\t<characteristic type=\"Ext\">\n"
            + "\t\t\t\t<characteristic type=\"GSMA\">\n"
            + "\t\t\t\t\t<parm name=\"AppRef\" value=\"IMS-Setting\"/>\n"
            + "\t\t\t\t\t<parm name=\"rcsVolteSingleRegistration\" value=\"0\"/>\n"
            + "\t\t\t\t</characteristic>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t</characteristic>\n"
            + "\t</characteristic>\n"
            + "</wap-provisioningdoc>\n";
    private static final String TEST_RCS_PRE_CONFIG = "<RCSPreProvisiniongConfig>\n"
            + "\t<VERS>\n"
            + "\t\t<version>1</version>\n"
            + "\t\t<validity>1728000</validity>\n"
            + "\t</VERS>\n"
            + "\t<TOKEN>\n"
            + "\t\t<token>X</token>\n"
            + "\t</TOKEN>\n"
            + "\t<EXT>\n"
            + "\t\t<url>https://rcs.mnc123.mcc456.pub.3gppnetwork.org</url>\n"
            + "\t</EXT>\n"
            + "</RCSPreProvisiniongConfig>";
    private static final int RCS_CONFIG_CB_UNKNOWN = Integer.MAX_VALUE;
    private static final int RCS_CONFIG_CB_CHANGED = 0;
    private static final int RCS_CONFIG_CB_ERROR   = 1;
    private static final int RCS_CONFIG_CB_RESET   = 2;
    private static final int RCS_CONFIG_CB_DELETE  = 3;
    private static final int RCS_CONFIG_CB_PREPROV = 4;

    private static final String CHAT_FEATURE_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";
    public static final String FILE_TRANSFER_FEATURE_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fthttp\"";
    private static final String CHAT_SERVICE_ID =
            "org.openmobilealliance:ChatSession";
    private static final String FILE_TRANSFER_SERVICE_ID =
            "org.openmobilealliance:File-Transfer-HTTP";

    private static final int FEATURE_STATE_READY = 0;
    private static final int TEST_PACKET_LOSS_RATE_THRESHOLD = 17;
    private static final int TEST_JITTER_THRESHOLD = 74;
    private static final long TEST_INACTIVITY_MILLIS = 4779;

    // When ImsService notifies registration or unregistration, framework needs time to handle
    // that event.
    public static final int TEST_OPERATION_TIME_MS = 1000;

    private static CarrierConfigReceiver sReceiver;
    private static SingleRegistrationCapabilityReceiver sSrcReceiver;

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

    private static class SingleRegistrationCapabilityReceiver extends BaseReceiver {
        private int mCapability;
        private int mSubId;

        SingleRegistrationCapabilityReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ProvisioningManager.ACTION_RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE
                    .equals(intent.getAction())) {
                // if sub id in intent is not expected, then intent should be ignored.
                int subId = intent.getIntExtra(ProvisioningManager.EXTRA_SUBSCRIPTION_ID,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                if (mSubId != subId) {
                    return;
                }

                mCapability = intent.getIntExtra(ProvisioningManager.EXTRA_STATUS,
                        ProvisioningManager.STATUS_DEVICE_NOT_CAPABLE
                        | ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE);
                mLatch.countDown();
            }
        }

        int getCapability() {
            return mCapability;
        }
    }

    private static class RcsProvisioningCallbackParams {
        byte[] mConfig;
        Integer mErrorCode;
        String mErrorString;
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
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
        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);

        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            return;
        }
        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(sTestSlot);
        // Configure SMS receiver based on the Android version.
        sServiceConnector.setDefaultSmsApp();

        // Save the original device uce enabled config and override it.
        sDeviceUceEnabled = sServiceConnector.getDeviceUceEnabled();
        sServiceConnector.setDeviceUceEnabled(true);

        sReceiver = new CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);

        sSrcReceiver = new SingleRegistrationCapabilityReceiver(sTestSub);
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sSrcReceiver, new IntentFilter(
                        ProvisioningManager.ACTION_RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE));
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Restore all ImsService configurations that existed before the test.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectServices();
            sServiceConnector.setDeviceUceEnabled(sDeviceUceEnabled);
        }
        sServiceConnector = null;

        // Ensure there are no CarrierConfig overrides as well as reset the ImsResolver in case the
        // ImsService override changed in CarrierConfig while we were overriding it.
        overrideCarrierConfig(null);

        if (sReceiver != null) {
            InstrumentationRegistry.getInstrumentation().getContext().unregisterReceiver(sReceiver);
            sReceiver = null;
        }

        if (sSrcReceiver != null) {
            InstrumentationRegistry.getInstrumentation()
                    .getContext().unregisterReceiver(sSrcReceiver);
            sSrcReceiver = null;
        }
    }

    @Before
    public void beforeTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TelephonyManager tm = (TelephonyManager) InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            fail("This test requires that there is a SIM in the device!");
        }
        // Correctness check: ensure that the subscription hasn't changed between tests.
        int subId = SubscriptionManager.getSubscriptionId(sTestSlot);
        if (subId != sTestSub) {
            fail("The found subId " + subId + " does not match the test sub id " + sTestSub);
        }

        TestAcsClient.getInstance().reset();
        sServiceConnector.setSingleRegistrationTestModeEnabled(true);
    }

    @After
    public void afterTest() throws Exception {
        TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE, SUPPORT_PUBLISHING_STATE_STRING);
        TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE, SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING);

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Unbind the ImsService after the test completes.
        if (sServiceConnector != null) {
            sServiceConnector.setSingleRegistrationTestModeEnabled(false);
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.disconnectDeviceImsService();
        }
    }

    @Test
    public void testCarrierImsServiceBindRcsFeature() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));
        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS);
        assertNotNull("ImsService created, but ImsService#createRcsFeature was not called!",
                sServiceConnector.getCarrierService().getRcsFeature());
        assertTrue("Not expected subId received!",
                isExpectedSubId(sServiceConnector.getCarrierService().getSubIDs()));
    }

    @Test
    public void testCarrierImsServiceBindRcsFeatureForExecutor() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        sServiceConnector.setExecutorTestType(true);
        // Connect to the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));
        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS);
        assertNotNull("ImsService created, but ImsService#createRcsFeature was not called!",
                sServiceConnector.getCarrierService().getRcsFeature());
    }

    @Test
    public void testCarrierImsServiceBindMmTelFeature() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL);
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());
        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);
        assertTrue("Not expected subId received!",
                isExpectedSubId(sServiceConnector.getCarrierService().getSubIDs()));
    }

    @Test
    public void testCarrierImsServiceBindRcsFeatureEnableDisableIms() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));
        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS));

        //Enable IMS and ensure that we receive the call to enable IMS in the ImsService.
        sServiceConnector.enableImsService(sTestSlot);
        // Wait for command in ImsService
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_ENABLE_IMS));
        assertTrue(sServiceConnector.getCarrierService().isEnabled());

        //Disable IMS and ensure that we receive the call to enable IMS in the ImsService.
        sServiceConnector.disableImsService(sTestSlot);
        // Wait for command in ImsService
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_DISABLE_IMS));
        assertFalse(sServiceConnector.getCarrierService().isEnabled());
        assertTrue("Not expected subId received!",
                isExpectedSubId(sServiceConnector.getCarrierService().getSubIDs()));
    }

    @Test
    public void testCarrierImsServiceBindRcsChangeToMmtel() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));
        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS));

        // Change the supported feature to MMTEl
        sServiceConnector.getCarrierService().getImsService().onUpdateSupportedImsFeatures(
                new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL).build());

        // createMmTelFeature should be called.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));

        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);
        assertTrue("Not expected subId received!",
                isExpectedSubId(sServiceConnector.getCarrierService().getSubIDs()));
    }

    @Test
    public void testCarrierImsServiceBindRcsChangeToMmtelCompat() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the RCS feature.
        ImsFeatureConfiguration config = new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build();
        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().resetState();
        // Set the flag for ImsService compatibility test.
        sServiceConnector.getCarrierService().setImsServiceCompat();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(config));
        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS));

        // Change the supported feature to MMTEl
        sServiceConnector.getCarrierService().getImsServiceCompat().onUpdateSupportedImsFeatures(
                new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL).build());

        // createMmTelFeature should be called.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));

        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);
    }

    @Test
    public void testCarrierImsServiceBindMmTelNoEmergency() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the MMTEL feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);
    }

    @Test
    public void testCarrierImsServiceBindMmTelEmergencyEnabled() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the MMTEL feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                .build()));
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);
        assertTrue("Not expected subId received!",
                isExpectedSubId(sServiceConnector.getCarrierService().getSubIDs()));
    }

    @Test
    public void testCarrierImsServiceBindNullRcsFeature() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the RCS feature.
        ImsFeatureConfiguration config = new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build();
        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().resetState();
        sServiceConnector.getCarrierService().setNullRcsBinding();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(config));

        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS));
        // Check to see if telephony state was reset at some point due to a crash and fail if so
        assertFalse("ImsService should not crash if there is a null ImsFeature returned",
                ImsUtils.retryUntilTrue(() ->
                        !sServiceConnector.isCarrierServiceStillConfigured(),
                5000 /*test timeout*/, 5 /*num times*/));
    }

    @Test
    public void testDeviceImsServiceBindRcsFeature() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectDeviceImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));
        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue(sServiceConnector.getExternalService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS));
        // Make sure the RcsFeature was created in the test service.
        assertTrue("Device ImsService created, but TestDeviceImsService#createRcsFeature was not"
                        + "called!", sServiceConnector.getExternalService().isRcsFeatureCreated());
    }

    @Test
    public void testBindDeviceAndCarrierDifferentFeatures() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to Device the ImsService with the MMTEL/EMERGENCY_MMTEL feature.
        assertTrue(sServiceConnector.connectDeviceImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        // Connect to Device the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue(sServiceConnector.getExternalService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        // Make sure the MmTelFeature was created in the test service.
        assertTrue("Device ImsService created, but TestDeviceImsService#createMmTelFeature was"
                + "not called!", sServiceConnector.getExternalService().isMmTelFeatureCreated());

        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS));
        assertNotNull("ImsService created, but ImsService#createRcsFeature was not called!",
                sServiceConnector.getCarrierService().getRcsFeature());
    }

    @Test
    public void testBindDeviceAndCarrierSameFeature() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to Device the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectDeviceImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));

        //First MMTEL feature is created on device ImsService.
        assertTrue(sServiceConnector.getExternalService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        assertTrue("Device ImsService created, but TestDeviceImsService#createMmTelFeature was "
                + "not called!", sServiceConnector.getExternalService().isMmTelFeatureCreated());

        // Connect to Device the ImsService with the MMTEL feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                .build()));

        // Next MMTEL feature is created on carrier ImsService (and unbound on device)
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        assertNotNull("ImsService created, but ImsService#createRcsFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());

        // Ensure that the MmTelFeature was removed on the device ImsService.
        assertTrue(sServiceConnector.getExternalService().waitForLatchCountdown(
                TestImsService.LATCH_REMOVE_MMTEL));
        assertFalse("Device ImsService was never removed when carrier ImsService took MMTEL."
                + "feature.", sServiceConnector.getExternalService().isMmTelFeatureCreated());
    }

    @Test
    public void testBindDeviceAndCarrierUpdateToSameFeature() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Connect to Device the ImsService with the MMTEL feature.
        assertTrue(sServiceConnector.connectDeviceImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));

        //First MMTEL feature is created on device ImsService.
        assertTrue(sServiceConnector.getExternalService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        assertTrue("Device ImsService created, but TestDeviceImsService#createMmTelFeature was"
                + "not called!", sServiceConnector.getExternalService().isMmTelFeatureCreated());

        // Connect to Device the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));

        // Next Rcs feature is created on carrier ImsService
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_RCS));
        assertNotNull("ImsService created, but ImsService#createRcsFeature was not called!",
                sServiceConnector.getCarrierService().getRcsFeature());

        // Change the supported feature to MMTEl
        sServiceConnector.getCarrierService().getImsService().onUpdateSupportedImsFeatures(
                new ImsFeatureConfiguration.Builder()
                        .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                        .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                        .build());

        // MMTEL feature is created on carrier ImsService
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());

        // Ensure that the MmTelFeature was removed on the device ImsService.
        assertTrue(sServiceConnector.getExternalService().waitForLatchCountdown(
                TestImsService.LATCH_REMOVE_MMTEL));
        assertFalse("Device ImsService was never removed when carrier ImsService took MMTEL."
                + "feature.", sServiceConnector.getExternalService().isMmTelFeatureCreated());

        // Ensure that the RcsFeature was removed on the carrier ImsService.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_REMOVE_RCS));
        assertNull(sServiceConnector.getCarrierService().getRcsFeature());
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendTextMessage")
    public void testMmTelSendSms() throws Exception {
        if (!ImsUtils.shouldRunSmsImsTests(sTestSub)) {
            return;
        }

        setupImsServiceForSms();
        // Send Message with sent PendingIntent requested
        SmsManager.getSmsManagerForSubscriptionId(sTestSub).sendTextMessage(SRC_NUMBER,
                DEST_NUMBER, MSG_CONTENTS, SmsReceiverHelper.getMessageSentPendingIntent(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()), null);
        assertTrue(sServiceConnector.getCarrierService().getMmTelFeature()
                .getSmsImplementation().waitForMessageSentLatch());

        // Wait for send PendingIntent
        Intent intent = AsyncSmsMessageListener.getInstance().waitForMessageSentIntent(
                ImsUtils.TEST_TIMEOUT_MS);
        assertNotNull("SMS send PendingIntent never received", intent);
        assertEquals("SMS send PendingIntent should have result RESULT_OK",
                Activity.RESULT_OK, intent.getIntExtra(SmsReceiverHelper.EXTRA_RESULT_CODE,
                        Activity.RESULT_CANCELED));

        // Ensure we receive correct PDU on the other side.
        byte[] pduWithStatusReport = EXPECTED_PDU.clone();
        pduWithStatusReport[1] = sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu[1];
        Assert.assertArrayEquals(pduWithStatusReport, sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu);
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendTextMessage")
    public void testMmTelSendSmsDeliveryReportQCompat() throws Exception {
        if (!ImsUtils.shouldRunSmsImsTests(sTestSub)) {
            return;
        }

        setupImsServiceForSms();
        // Send Message with sent PendingIntent requested
        SmsManager.getSmsManagerForSubscriptionId(sTestSub).sendTextMessage(SRC_NUMBER,
                DEST_NUMBER, MSG_CONTENTS, null, SmsReceiverHelper.getMessageDeliveredPendingIntent(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()));
        assertTrue(sServiceConnector.getCarrierService().getMmTelFeature()
                .getSmsImplementation().waitForMessageSentLatch());

        // Ensure we receive correct PDU on the other side.
        // Set TP-Status-Report-Request bit as well for this case.
        byte messageRef = sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu[1];
        byte[] pduWithStatusReport = EXPECTED_PDU.clone();
        pduWithStatusReport[0] |= 0x20;
        pduWithStatusReport[1] = messageRef;
        Assert.assertArrayEquals(pduWithStatusReport, sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu);
        byte[] pduStatusReport = STATUS_REPORT_PDU.clone();
        pduStatusReport[2] = messageRef;
        // Ensure the API works on Q as well as in R+, where it was deprecated.
        sServiceConnector.getCarrierService().getMmTelFeature().getSmsImplementation()
                .sendReportWaitForAcknowledgeSmsReportPQ(messageRef, SmsMessage.FORMAT_3GPP,
                        pduStatusReport);

        // Wait for delivered PendingIntent
        Intent intent = AsyncSmsMessageListener.getInstance().waitForMessageDeliveredIntent(
                ImsUtils.TEST_TIMEOUT_MS);
        assertNotNull("SMS delivered PendingIntent never received", intent);
        assertEquals("SMS delivered PendingIntent should have result RESULT_OK",
                Activity.RESULT_OK, intent.getIntExtra(SmsReceiverHelper.EXTRA_RESULT_CODE,
                        Activity.RESULT_CANCELED));
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendTextMessage")
    public void testMmTelSendSmsDeliveryReportR() throws Exception {
        if (!ImsUtils.shouldRunSmsImsTests(sTestSub)) {
            return;
        }

        setupImsServiceForSms();
        // Send Message with sent PendingIntent requested
        SmsManager.getSmsManagerForSubscriptionId(sTestSub).sendTextMessage(SRC_NUMBER,
                DEST_NUMBER, MSG_CONTENTS, null, SmsReceiverHelper.getMessageDeliveredPendingIntent(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()));
        assertTrue(sServiceConnector.getCarrierService().getMmTelFeature()
                .getSmsImplementation().waitForMessageSentLatch());

        // Ensure we receive correct PDU on the other side.
        // Set TP-Status-Report-Request bit as well for this case.
        byte messageRef = sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu[1];
        byte[] pduWithStatusReport = EXPECTED_PDU.clone();
        pduWithStatusReport[0] |= 0x20;
        pduWithStatusReport[1] = messageRef;
        Assert.assertArrayEquals(pduWithStatusReport, sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu);

        byte[] pduStatusReport = STATUS_REPORT_PDU.clone();
        pduStatusReport[2] = messageRef;
        sServiceConnector.getCarrierService().getMmTelFeature().getSmsImplementation()
                .sendReportWaitForAcknowledgeSmsReportR(123456789, SmsMessage.FORMAT_3GPP,
                        pduStatusReport);

        // Wait for delivered PendingIntent
        Intent intent = AsyncSmsMessageListener.getInstance().waitForMessageDeliveredIntent(
                ImsUtils.TEST_TIMEOUT_MS);
        assertNotNull("SMS delivered PendingIntent never received", intent);
        assertEquals("SMS delivered PendingIntent should have result RESULT_OK",
                Activity.RESULT_OK, intent.getIntExtra(SmsReceiverHelper.EXTRA_RESULT_CODE,
                        Activity.RESULT_CANCELED));
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendTextMessage")
    public void testMmTelSendSmsRSuccess() throws Exception {
        if (!ImsUtils.shouldRunSmsImsTests(sTestSub)) {
            return;
        }

        setupImsServiceForSms();

        // Send Message
        SmsManager.getSmsManagerForSubscriptionId(sTestSub).sendTextMessage(SRC_NUMBER,
                DEST_NUMBER, MSG_CONTENTS, SmsReceiverHelper.getMessageSentPendingIntent(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()), null);
        // Use R specific API for sending SMS result
        assertTrue(sServiceConnector.getCarrierService().getMmTelFeature()
                .getSmsImplementation().waitForMessageSentLatchSuccess());
        Intent intent = AsyncSmsMessageListener.getInstance().waitForMessageSentIntent(
                ImsUtils.TEST_TIMEOUT_MS);
        assertNotNull(intent);
        assertEquals(Activity.RESULT_OK, intent.getIntExtra(SmsReceiverHelper.EXTRA_RESULT_CODE,
                    Activity.RESULT_CANCELED));

        // Ensure we receive correct PDU on the other side.
        byte[] pduWithStatusReport = EXPECTED_PDU.clone();
        pduWithStatusReport[1] = sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu[1];
        Assert.assertArrayEquals(pduWithStatusReport, sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu);
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendTextMessage")
    public void testMmTelSendSmsNetworkError() throws Exception {
        if (!ImsUtils.shouldRunSmsImsTests(sTestSub)) {
            return;
        }

        setupImsServiceForSms();

        // Send Message
        SmsManager.getSmsManagerForSubscriptionId(sTestSub).sendTextMessage(SRC_NUMBER,
                DEST_NUMBER, MSG_CONTENTS, SmsReceiverHelper.getMessageSentPendingIntent(
                        InstrumentationRegistry.getInstrumentation().getContext()), null);
        assertTrue(sServiceConnector.getCarrierService().getMmTelFeature()
                .getSmsImplementation().waitForMessageSentLatchError(
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE, 41));
        Intent intent = AsyncSmsMessageListener.getInstance().waitForMessageSentIntent(
                ImsUtils.TEST_TIMEOUT_MS);
        assertNotNull(intent);
        // In the case of error, the PendingIntent result will not report OK
        assertNotEquals(Activity.RESULT_OK, intent.getIntExtra(SmsReceiverHelper.EXTRA_RESULT_CODE,
                Activity.RESULT_OK));
        // make sure the "errorCode" extra contains the network error code returned by the
        // ImsService.
        assertEquals(41, intent.getIntExtra("errorCode", 0));

        // Ensure we receive correct PDU on the other side.
        byte[] pduWithStatusReport = EXPECTED_PDU.clone();
        pduWithStatusReport[1] = sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu[1];
        Assert.assertArrayEquals(pduWithStatusReport, sServiceConnector.getCarrierService()
                .getMmTelFeature().getSmsImplementation().sentPdu);
    }
    @Ignore("The onMemoryAvailable and onMemoryAvailableResult Apis were moved back to @hide for"
            + " now, so do not want to completely remove this test.")
    @Test
    public void testMmTelSendMemoryAvailabilityNotification() throws Exception {
        if (!ImsUtils.shouldRunSmsImsTests(sTestSub)) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        boolean sendSmmaViaIms =  getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_smma_notification_supported_over_ims",
                        "bool",
                        "android"));
        if (!sendSmmaViaIms) {
            return;
        }
        try {
            setupImsServiceForSms();

            SmsManager.getSmsManagerForSubscriptionId(sTestSub)
                        .setStorageMonitorMemoryStatusOverride(false);

            // Clear cached data before starting test.
            AsyncSmsMessageListener.getInstance().clear();

            //Message received
            sServiceConnector.getCarrierService().getMmTelFeature().getSmsImplementation()
                    .receiveSmsWaitForAcknowledgeMemoryFull(123456789, SmsMessage.FORMAT_3GPP,
                            Base64.decode(RECEIVED_MESSAGE, Base64.DEFAULT));

            SmsManager.getSmsManagerForSubscriptionId(sTestSub)
                        .setStorageMonitorMemoryStatusOverride(true);
            assertTrue(sServiceConnector.getCarrierService().getMmTelFeature()
                    .getSmsImplementation().waitForOnMemoryAvailableLatch());
            assertTrue(sServiceConnector.getCarrierService().getMmTelFeature()
                    .getSmsImplementation().mMemoryEventReceived);
        } catch (SecurityException se) {
            fail("Caller with MODIFY_PHONE_STATE should be able to call API");
        } finally {
            SmsManager.getSmsManagerForSubscriptionId(sTestSub)
                        .clearStorageMonitorMemoryStatusOverride();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testMmTelReceiveSms() throws Exception {
        if (!ImsUtils.shouldRunSmsImsTests(sTestSub)) {
            return;
        }
        setupImsServiceForSms();

        // Clear cached data before starting test.
        AsyncSmsMessageListener.getInstance().clear();

        // Message received
        sServiceConnector.getCarrierService().getMmTelFeature().getSmsImplementation()
                .receiveSmsWaitForAcknowledge(123456789, SmsMessage.FORMAT_3GPP,
                        Base64.decode(RECEIVED_MESSAGE, Base64.DEFAULT));

        // Wait for SMS received intent and ensure it is correct.
        String receivedMessage = AsyncSmsMessageListener.getInstance()
                .waitForSmsMessage(ImsUtils.TEST_TIMEOUT_MS);
        assertEquals(EXPECTED_RECEIVED_MESSAGE, receivedMessage);
    }

    @Test
    public void testMmTelReceiveSMPPClass2Sms() throws Exception {
        if (!ImsUtils.shouldRunSmsImsTests(sTestSub)) {
            return;
        }

        boolean isViaIms = getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_smppsim_response_via_ims", "bool",
                        "android"));
        if (!isViaIms) {
            return;
        }

        setupImsServiceForSms();

        // Clear cached data before starting test.
        AsyncSmsMessageListener.getInstance().clear();

        // Message received
        sServiceConnector.getCarrierService().getMmTelFeature().getSmsImplementation()
                .receiveSmsWaitForAcknowledge(123456789, SmsMessage.FORMAT_3GPP,
                        CLASS2SMPP_PDU);

        assertEquals(EXPECTED_MESSAGEREF, sServiceConnector.getCarrierService().getMmTelFeature()
                .getSmsImplementation().getMessageRef());

    }

    @Test
    public void testGetFeatureState() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // This will set feature state to ready
        triggerFrameworkConnectToCarrierImsService();

        Integer result = getFeatureState();
        assertNotNull(result);
        assertEquals("ImsService state should be STATE_READY",
                sServiceConnector.getCarrierService().getMmTelFeature().getFeatureState(),
                ImsFeature.STATE_READY);
        assertTrue("ImsService state is ready, but STATE_READY is not reported.",
                ImsUtils.retryUntilTrue(() -> (getFeatureState() == ImsFeature.STATE_READY)));

        sServiceConnector.getCarrierService().getMmTelFeature().setFeatureState(
                ImsFeature.STATE_INITIALIZING);
        result = getFeatureState();
        assertNotNull(result);
        assertEquals("ImsService state should be STATE_INITIALIZING",
                sServiceConnector.getCarrierService().getMmTelFeature().getFeatureState(),
                ImsFeature.STATE_INITIALIZING);
        assertTrue("ImsService state is initializing, but STATE_INITIALIZING is not reported.",
                ImsUtils.retryUntilTrue(
                        () -> (getFeatureState() == ImsFeature.STATE_INITIALIZING)));

        sServiceConnector.getCarrierService().getMmTelFeature().setFeatureState(
                ImsFeature.STATE_UNAVAILABLE);
        result = getFeatureState();
        assertNotNull(result);
        assertEquals("ImsService state should be STATE_UNAVAILABLE",
                sServiceConnector.getCarrierService().getMmTelFeature().getFeatureState(),
                ImsFeature.STATE_UNAVAILABLE);
        assertTrue("ImsService state is unavailable, but STATE_UNAVAILABLE is not reported.",
                ImsUtils.retryUntilTrue(
                        () -> (getFeatureState() == ImsFeature.STATE_UNAVAILABLE)));
    }

    private Integer getFeatureState() throws Exception {
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        LinkedBlockingQueue<Integer> state = new LinkedBlockingQueue<>(1);
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(mmTelManager,
                (m) -> m.getFeatureState(Runnable::run, state::offer), ImsException.class);
        return state.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testMmTelManagerRegistrationCallbackS() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        final ArraySet<String> featureTags = new ArraySet<>();
        featureTags.add("featureTag1");
        featureTags.add("featureTag2");

        triggerFrameworkConnectToCarrierImsService();

        // Start deregistered
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));

        LinkedBlockingQueue<ImsRegistrationAttributes> mRegQueue =
                new LinkedBlockingQueue<>();
        LinkedBlockingQueue<ImsReasonInfo> mDeregQueue =
                new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
            @Override
            public void onRegistered(ImsRegistrationAttributes attributes) {
                mRegQueue.offer(attributes);
            }

            @Override
            public void onRegistering(ImsRegistrationAttributes attributes) {
                mRegQueue.offer(attributes);
            }

            @Override
            public void onUnregistered(ImsReasonInfo info) {
                mDeregQueue.offer(info);
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            // First try without the correct permissions.
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
            fail("registerImsRegistrationCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // Latch will count down here (we callback on the state during registration).
        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }
        ImsReasonInfo deregResult = waitForResult(mDeregQueue);
        assertNotNull(deregResult);
        assertEquals(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, deregResult.getCode());

        // Start registration
        verifyRegistering(IMS_REGI_TECH_LTE, featureTags, mRegQueue,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, 0 /*expected flags*/);

        // move to NR
        verifyRegistering(IMS_REGI_TECH_NR, featureTags, mRegQueue,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, 0 /*expected flags*/);

        // move to cross sim
        verifyRegistering(IMS_REGI_TECH_CROSS_SIM, featureTags,
                mRegQueue, AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET);

        // Complete registration
        verifyRegistered(IMS_REGI_TECH_LTE, featureTags, mRegQueue,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, 0 /*expected flags*/);

        // move to NR
        verifyRegistered(IMS_REGI_TECH_NR, featureTags, mRegQueue,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, 0 /*expected flags*/);

        // move to cross sim
        verifyRegistered(IMS_REGI_TECH_CROSS_SIM, featureTags,
                mRegQueue, AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                ImsRegistrationAttributes.ATTR_EPDG_OVER_CELL_INTERNET);

        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.unregisterImsRegistrationCallback(callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        try {
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.unregisterImsRegistrationCallback(callback);
            fail("unregisterImsRegistrationCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EMERGENCY_REGISTRATION_STATE)
    public void testMmTelManagerEmergencyRegistrationCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        final ArraySet<String> featureTags = new ArraySet<>();
        featureTags.add("featureTag1");
        featureTags.add("featureTag2");

        triggerFrameworkConnectToCarrierImsService();

        // Start deregistered for emergency registration
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
            new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                ImsReasonInfo.CODE_UNSPECIFIED, ""), SUGGESTED_ACTION_NONE,
            new ImsRegistrationAttributes.Builder(IMS_REGI_TECH_LTE)
                .setFlagRegistrationTypeEmergency().build());

        LinkedBlockingQueue<ImsRegistrationAttributes> mEmerRegQueue =
                new LinkedBlockingQueue<>();

        LinkedBlockingQueue<Integer> mEmerDeregQueue =
                new LinkedBlockingQueue<>();

        RegistrationManager.RegistrationCallback emerRegCallback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onRegistered(ImsRegistrationAttributes attributes) {
                        mEmerRegQueue.offer(attributes);
                    }

                    @Override
                    public void onRegistering(ImsRegistrationAttributes attributes) {
                        mEmerRegQueue.offer(attributes);
                    }

                    @Override
                    public void onUnregistered(ImsReasonInfo info, int suggestedAction,
                            int imsRadioTech) {
                        mEmerDeregQueue.offer(info.getCode());
                    }
                    @Override
                    public void onTechnologyChangeFailed(int imsTransportType, ImsReasonInfo info) {
                        mEmerDeregQueue.offer(imsTransportType);
                        mEmerDeregQueue.offer(info.getCode());
                    }
                };

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        try {
            // First try without the correct permissions.
            mmTelManager.registerImsEmergencyRegistrationCallback(getContext().getMainExecutor(),
                    emerRegCallback);
            fail("registerImsEmergencyRegistrationCallback requires READ_PRECISE_PHONE_STATE "
                    + "permission.");
        } catch (SecurityException e) {
            //expected
        }

        // Latch will count down here (we callback on the state during registration).
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mmTelManager, (mm) -> {
                        try {
                            mm.registerImsEmergencyRegistrationCallback(
                                    getContext().getMainExecutor(), emerRegCallback);
                        } catch (ImsException e) {
                            fail("registerImsEmergencyRegistrationCallback failed " + e);
                        }
                });

        assertEquals(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, waitForIntResult(mEmerDeregQueue));

        // Start registration for emergency registration
        verifyEmergencyRegistering(IMS_REGI_TECH_LTE, featureTags, mEmerRegQueue,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                ImsRegistrationAttributes.ATTR_REGISTRATION_TYPE_EMERGENCY);

        // Complete registration for emergency registration
        verifyEmergencyRegistered(IMS_REGI_TECH_LTE, featureTags, mEmerRegQueue,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                ImsRegistrationAttributes.ATTR_REGISTRATION_TYPE_EMERGENCY);

        // fail handover to IWLAN for emergency registration
        sServiceConnector.getCarrierService().getImsRegistration().onTechnologyChangeFailed(
            new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE,
                ImsReasonInfo.CODE_UNSPECIFIED, ""), new ImsRegistrationAttributes.Builder(
                IMS_REGI_TECH_IWLAN).setFlagRegistrationTypeEmergency().build());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, waitForIntResult(mEmerDeregQueue));
        assertEquals(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE, waitForIntResult(mEmerDeregQueue));

        // handover to NR successfully for emergency registration
        verifyEmergencyRegistering(IMS_REGI_TECH_NR, featureTags, mEmerRegQueue,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                ImsRegistrationAttributes.ATTR_REGISTRATION_TYPE_EMERGENCY);

        verifyEmergencyRegistered(IMS_REGI_TECH_NR, featureTags, mEmerRegQueue,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                ImsRegistrationAttributes.ATTR_REGISTRATION_TYPE_EMERGENCY);

        // Deregister registration for emergency registration with null reason info
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(null,
                SUGGESTED_ACTION_NONE, new ImsRegistrationAttributes.Builder(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE).setFlagRegistrationTypeEmergency()
                .build());
        assertEquals(ImsReasonInfo.CODE_UNSPECIFIED, waitForIntResult(mEmerDeregQueue));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mmTelManager, (mm) -> {
                    mm.unregisterImsEmergencyRegistrationCallback(emerRegCallback);
                });

        try {
            mmTelManager.unregisterImsEmergencyRegistrationCallback(emerRegCallback);
            fail("unregisterImsEmergencyRegistrationCallback requires READ_PRECISE_PHONE_STATE "
                    + "permission.");
        } catch (SecurityException e) {
            //expected
        }
    }

    @Test
    public void testMmTelManagerRegistrationCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        triggerFrameworkConnectToCarrierImsService();

        // Start deregistered
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));

        // This is a little bit gross looking, but on P devices, I can not define classes that
        // extend ImsMmTelManager.RegistrationCallback (because it doesn't exist), so this has to
        // happen as an anon class here.
        LinkedBlockingQueue<Integer> mQueue = new LinkedBlockingQueue<>();
        ImsMmTelManager.RegistrationCallback callback = new ImsMmTelManager.RegistrationCallback() {
            @Override
            public void onRegistered(int imsTransportType) {
                mQueue.offer(imsTransportType);
            }

            @Override
            public void onRegistering(int imsTransportType) {
                mQueue.offer(imsTransportType);
            }

            @Override
            public void onUnregistered(ImsReasonInfo info) {
                mQueue.offer(info.getCode());
            }

            @Override
            public void onTechnologyChangeFailed(int imsTransportType, ImsReasonInfo info) {
                mQueue.offer(imsTransportType);
                mQueue.offer(info.getCode());
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            // First try without the correct permissions.
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
            fail("registerImsRegistrationCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // Latch will count down here (we callback on the state during registration).
        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }
        assertEquals(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, waitForIntResult(mQueue));


        // Start registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistering(
                IMS_REGI_TECH_LTE);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));

        // Complete registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));

        // Fail handover to IWLAN
        sServiceConnector.getCarrierService().getImsRegistration().onTechnologyChangeFailed(
                IMS_REGI_TECH_IWLAN,
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, waitForIntResult(mQueue));
        assertEquals(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE, waitForIntResult(mQueue));

        // Ensure null ImsReasonInfo still results in non-null callback value.
        sServiceConnector.getCarrierService().getImsRegistration().onTechnologyChangeFailed(
                IMS_REGI_TECH_IWLAN, null);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, waitForIntResult(mQueue));
        assertEquals(ImsReasonInfo.CODE_UNSPECIFIED, waitForIntResult(mQueue));

        // Ensure null ImsReasonInfo still results in non-null callback.
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(null);
        assertEquals(ImsReasonInfo.CODE_UNSPECIFIED, waitForIntResult(mQueue));

        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.unregisterImsRegistrationCallback(callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        try {
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.unregisterImsRegistrationCallback(callback);
            fail("unregisterImsRegistrationCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }
    }

    @Test
    public void testRcsDeviceCapabilitiesPublish() throws Exception {
        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SUPPORT_PUBLISHING_STATE_STRING);

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to device ImsService with MmTel feature and RCS feature
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Register the callback to listen to the publish state changed
        LinkedBlockingQueue<Integer> publishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener publishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                        publishStateQueue.offer(state);
                    }
                };

        // Another publish register callback to verify the API
        // RcsUceAdapter#removeOnPublishStateChangedListener
        LinkedBlockingQueue<Integer> unregisteredPublishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener unregisteredPublishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                        unregisteredPublishStateQueue.offer(state);
                    }
                };

        // Another publish register callback to verify new added API.
        // RcsUceAdapter#removeOnPublishStateChangedListener
        LinkedBlockingQueue<PublishAttributes> addedNewPublishStateQueue =
                new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener addedNewPublishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                    }
                    public void onPublishStateChange(PublishAttributes attributes) {
                        addedNewPublishStateQueue.offer(attributes);
                    }
                };
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            // register three publish state callback
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    publishStateCallback);
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    unregisteredPublishStateCallback);
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    addedNewPublishStateCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Verify receiving the publish state callback immediately after registering the callback.
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                waitForIntResult(unregisteredPublishStateQueue));
        PublishAttributes attr = waitForResult(addedNewPublishStateQueue);
        assertNull(attr.getSipDetails());
        assertTrue(attr.getPresenceTuples().isEmpty());
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, attr.getPublishState());
        publishStateQueue.clear();
        unregisteredPublishStateQueue.clear();
        addedNewPublishStateQueue.clear();

        // Verify the value of getting from the API is NOT_PUBLISHED
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Setup the operation of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // Unregister the publish state callback
        try {
            automan.adoptShellPermissionIdentity();
            uceAdapter.removeOnPublishStateChangedListener(unregisteredPublishStateCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // IMS registers
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // Framework should not trigger the device capabilities publish when the framework doesn't
        // receive that the RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE is enabled.
        if (publishStateQueue.poll() != null) {
            fail("The publish callback should not be called because presence uce is not ready");
        }
        if (unregisteredPublishStateQueue.poll() != null) {
            fail("The de-registered publish callback should not be called");
        }

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, waitForIntResult(publishStateQueue));
        attr = waitForResult(addedNewPublishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, attr.getPublishState());
        assertNull(attr.getSipDetails());
        assertTrue(attr.getPresenceTuples().isEmpty());

        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        attr = waitForResult(addedNewPublishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, attr.getPublishState());
        assertNotNull(attr.getSipDetails());
        assertEquals(200, attr.getSipDetails().getResponseCode());
        assertTrue(attr.getSipDetails().getResponsePhrase().isEmpty());
        publishStateQueue.clear();
        addedNewPublishStateQueue.clear();
        // Verify the value of getting from the API is PUBLISH_STATE_OK
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Unregister the publish state callback
        try {
            automan.adoptShellPermissionIdentity();
            uceAdapter.removeOnPublishStateChangedListener(addedNewPublishStateCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        if (addedNewPublishStateQueue.poll() != null) {
            fail("The de-registered publish callback should not be called");
        }

        // ImsService triggers the unpublish notification
        eventListener.onUnpublish();

        // Verify the publish state callback will be called with the state "NOT_PUBLISHED"
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // The unregistered callback should not be called.
        if (unregisteredPublishStateQueue.poll() != null) {
            fail("The de-registered publish callback should not be called when unpublish");
        }

        // Verify the value of getting from the API is NOT_PUBLISHED
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }
        publishStateQueue.clear();

        // ImsService triggers to notify framework publish updated.
        eventListener.onPublishUpdated(200, "", 0, "");
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Setup the operation of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 999;
            String reason = "";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Trigger RcsFeature is unavailable
        sServiceConnector.getCarrierService().getRcsFeature()
                .setFeatureState(ImsFeature.STATE_UNAVAILABLE);

        // Verify the RcsCapabilityExchangeImplBase will be removed.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_LISTENER_SET));

        overrideCarrierConfig(null);
    }

    @Test
    public void testRcsAttributesPublish() throws Exception {
        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SUPPORT_PUBLISHING_STATE_STRING);

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to device ImsService with MmTel feature and RCS feature
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Register the callback to listen to the publish state changed
        LinkedBlockingQueue<PublishAttributes> publishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener publishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                    }
                    public void onPublishStateChange(PublishAttributes attributes) {
                        publishStateQueue.offer(attributes);
                    }
                };
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            // register three publish state callback
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    publishStateCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Verify receiving the publish state callback immediately after registering the callback.
        PublishAttributes attr = waitForResult(publishStateQueue);
        assertNull(attr.getSipDetails());
        assertTrue(attr.getPresenceTuples().isEmpty());
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, attr.getPublishState());
        publishStateQueue.clear();


        // Verify the value of getting from the API is NOT_PUBLISHED
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Setup the operation of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "OK";
            cb.onNetworkResponse(new SipDetails.Builder(SipDetails.METHOD_PUBLISH)
                    .setCSeq(1).setSipResponseCode(networkResp, reason)
                    .setCallId("TestCallId").build());
            listener.onPublish();
        });

        // IMS registers
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // Framework should not trigger the device capabilities publish when the framework doesn't
        // receive that the RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE is enabled.
        if (publishStateQueue.poll() != null) {
            fail("The publish callback should not be called because presence uce is not ready");
        }

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        attr = waitForResult(publishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, attr.getPublishState());
        assertNull(attr.getSipDetails());

        attr = waitForResult(publishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, attr.getPublishState());
        SipDetails details = attr.getSipDetails();
        assertNotNull(details);
        assertEquals(SipDetails.METHOD_PUBLISH, details.getMethod());
        assertEquals(1, details.getCSeq());
        assertEquals(200, details.getResponseCode());
        assertEquals("OK", details.getResponsePhrase());
        assertEquals(0, details.getReasonHeaderCause());
        assertTrue(details.getReasonHeaderText().isEmpty());
        assertEquals("TestCallId", details.getCallId());
        publishStateQueue.clear();

        // Verify the value of getting from the API is PUBLISH_STATE_OK
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        attr = waitForResult(publishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, attr.getPublishState());
        attr = waitForResult(publishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, attr.getPublishState());
        publishStateQueue.clear();

        // ImsService triggers the unpublish notification
        eventListener.onUnpublish();

        // Verify the publish state callback will be called with the state "NOT_PUBLISHED"
        attr = waitForResult(publishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, attr.getPublishState());
        publishStateQueue.clear();

        // Verify the value of getting from the API is NOT_PUBLISHED
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }
        publishStateQueue.clear();

        // ImsService triggers to notify framework publish updated.
        int sipCode = 200;
        String reasonPharse = "OK";
        int cSeq = 2;
        int reasonCause = 0;
        String reasonText = "headerText";
        String callId = "TestCallId1";
        eventListener.onPublishUpdated(new SipDetails.Builder(SipDetails.METHOD_PUBLISH)
                .setCSeq(cSeq).setSipResponseCode(sipCode, reasonPharse)
                .setSipResponseReasonHeader(reasonCause, reasonText).setCallId(callId).build());
        attr = waitForResult(publishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, attr.getPublishState());
        details = attr.getSipDetails();
        assertNotNull(details);
        assertEquals(SipDetails.METHOD_PUBLISH, details.getMethod());
        assertEquals(cSeq, details.getCSeq());
        assertEquals(sipCode, details.getResponseCode());
        assertEquals(reasonPharse, details.getResponsePhrase());
        assertEquals(reasonCause, details.getReasonHeaderCause());
        assertEquals(reasonText, details.getReasonHeaderText());
        assertEquals(callId, details.getCallId());
        publishStateQueue.clear();

        // Setup the operation of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            // The response code 999 means the PIDF is same as before.
            int networkResp = 999;
            String reason = "";
            cb.onNetworkResponse(new SipDetails.Builder(SipDetails.METHOD_PUBLISH)
                    .setCSeq(10).setSipResponseCode(networkResp, reason)
                    .setCallId("TestCallId3").build());
            listener.onPublish();
        });
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        attr = waitForResult(publishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, attr.getPublishState());
        attr = waitForResult(publishStateQueue);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, attr.getPublishState());
        // Verify that the sip info is set to null because the response code 999 is treated
        // as a command error.
        assertNull(attr.getSipDetails());
        publishStateQueue.clear();

        // Trigger RcsFeature is unavailable
        sServiceConnector.getCarrierService().getRcsFeature()
                .setFeatureState(ImsFeature.STATE_UNAVAILABLE);

        // Verify the RcsCapabilityExchangeImplBase will be removed.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_LISTENER_SET));

        overrideCarrierConfig(null);
    }

    @Test
    public void testPublishImsReg() throws Exception {
        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SUPPORT_PUBLISHING_STATE_STRING);
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to device ImsService with MmTel feature and RCS feature
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Register the callback to listen to the publish state changed
        LinkedBlockingQueue<Integer> publishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener publishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                        publishStateQueue.offer(state);
                    }
                };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    publishStateCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Verify receiving the publish state callback immediately after registering the callback.
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        LinkedBlockingQueue<String> pidfQueue = new LinkedBlockingQueue<>();
        // Setup the operation of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            pidfQueue.offer(pidfXml);
            int networkResp = 200;
            String reason = "";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        LinkedBlockingQueue<ImsRegistrationAttributes> mQueue = new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onRegistered(ImsRegistrationAttributes attr) {
                        mQueue.offer(attr);
                    }

                    @Override
                    public void onRegistering(ImsRegistrationAttributes attr) {}

                    @Override
                    public void onUnregistered(ImsReasonInfo info) {}

                    @Override
                    public void onTechnologyChangeFailed(int type, ImsReasonInfo info) {}
                };
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(imsRcsManager,
                (m) -> m.registerImsRegistrationCallback(getContext().getMainExecutor(), callback),
                ImsException.class);

        // IMS registers
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify that the publish is triggered and receive the publish state changed callback.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // IMS registers
        ArraySet<String> featureTags = new ArraySet<>();
        // Chat Session
        featureTags.add(CHAT_FEATURE_TAG);
        featureTags.add(FILE_TRANSFER_FEATURE_TAG);
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                IMS_REGI_TECH_LTE).setFeatureTags(featureTags).build();
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(attr);
        waitForParam(mQueue, attr);

        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Can not verify the pidf fully, but we can ensure that the service id for the feature is
        // contained in the XML. Multible PUBLISH requests may occur based on the state of the stack
        // at the time of this call, retry to get correct PIDF up to 5 times.
        boolean containsChatServiceId = false;
        boolean containsFileTransferServiceId = false;
        for (int retry = 0; retry < 5; retry++) {
            String pidf = waitForResult(pidfQueue);
            if (pidf == null) break;
            containsChatServiceId = pidf.contains(CHAT_SERVICE_ID);
            containsFileTransferServiceId  = pidf.contains(FILE_TRANSFER_SERVICE_ID);
            if (containsChatServiceId && containsFileTransferServiceId) break;
        }
        assertTrue("PIDF XML doesn't contain chat service-id", containsChatServiceId);
        assertTrue("PIDF XML doesn't contain FT service-id",
                containsFileTransferServiceId);

        publishStateQueue.clear();
        // ImsService triggers to notify framework publish updated.
        eventListener.onPublishUpdated(400, "", 0, "");
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Trigger RcsFeature is unavailable
        sServiceConnector.getCarrierService().getRcsFeature()
                .setFeatureState(ImsFeature.STATE_UNAVAILABLE);

        // Verify the RcsCapabilityExchangeImplBase will be removed.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_LISTENER_SET));

        overrideCarrierConfig(null);
    }

    @Test
    public void testPublishWithImsAssociatedUri() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);

        String mccmnc = tm.getSimOperator();
        boolean mTelUriSupported = CarrierCapability.SUPPORT_TEL_URI_PUBLISH.contains(mccmnc);

        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to device ImsService with MmTel feature and RCS feature
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Setup the operation of the publish request.
        List<String> receivedPidfXml = new ArrayList<>();
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "";
            receivedPidfXml.add(pidfXml);
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        Uri imsUri;
        if (mTelUriSupported) {
            imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, "0001112222", null);
        } else {
            imsUri = Uri.fromParts(PhoneAccount.SCHEME_SIP, "test", null);
        }

        StringBuilder expectedUriBuilder = new StringBuilder();
        expectedUriBuilder.append("<contact>").append(imsUri.toString()).append("</contact>");

        final String expectedUriString = expectedUriBuilder.toString();

        LinkedBlockingQueue<ImsRegistrationAttributes> mQueue = new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onRegistered(ImsRegistrationAttributes attr) {
                        mQueue.offer(attr);
                    }

                    @Override
                    public void onRegistering(ImsRegistrationAttributes attr) {}

                    @Override
                    public void onUnregistered(ImsReasonInfo info) {}

                    @Override
                    public void onTechnologyChangeFailed(int type, ImsReasonInfo info) {}
                };
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(imsRcsManager,
                (m) -> m.registerImsRegistrationCallback(getContext().getMainExecutor(), callback),
                ImsException.class);

        ArraySet<String> featureTags = new ArraySet<>();
        // Chat Session
        featureTags.add(CHAT_FEATURE_TAG);
        featureTags.add(FILE_TRANSFER_FEATURE_TAG);
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                IMS_REGI_TECH_LTE).setFeatureTags(featureTags).build();
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(attr);
        waitForParam(mQueue, attr);

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        // ImsService triggers to notify framework publish device's capabilities.
        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        // Verify that the ImsService has received the publish request and the received PIDF does
        // not contain the associated URI.
        assertFalse(receivedPidfXml.isEmpty());
        assertFalse(receivedPidfXml.get(0).contains(expectedUriString));

        // Reset the received pidf xml data
        receivedPidfXml.clear();

        // Notify the associated URI has changed.
        sServiceConnector.getCarrierService().getImsRegistration().onSubscriberAssociatedUriChanged(
                new Uri[] { imsUri });

        // Verify the ImsService does not receive the PUBLISH request because we just finish a
        // publish request a moment ago.
        assertFalse(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH, 2000 /* 2 seconds */));

        // Trigger a new publish request
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        // Verify that the ImsService has received the publish request and the received PIDF
        // contains the associated URI.
        assertFalse(receivedPidfXml.isEmpty());
        assertTrue(receivedPidfXml.get(0).contains(expectedUriString));

        // Reset the received pidf xml data
        receivedPidfXml.clear();

        overrideCarrierConfig(null);
    }

    @Test
    public void testRcsCapabilitiesPublishNetworkResponseWithReasonHeader() throws Exception {
        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SUPPORT_PUBLISHING_STATE_STRING);
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to device ImsService with MmTel feature and RCS feature
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Register the callback to listen to the publish state changed
        LinkedBlockingQueue<Integer> publishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener callback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                        publishStateQueue.offer(state);
                    }
                };

        // register the publish state callback
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                a -> a.addOnPublishStateChangedListener(getContext().getMainExecutor(), callback),
                ImsException.class,
                "android.permission.READ_PRIVILEGED_PHONE_STATE");

        // Verify receiving the publish state callback immediately after registering the callback.
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Setup the operation of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "OK";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // IMS registers
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify the ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Verify it is getUcePublishState for the API "getUcePublishState".
        int publishState = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(uceAdapter,
                a -> a.getUcePublishState(),
                ImsException.class,
                "android.permission.READ_PRIVILEGED_PHONE_STATE");
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);

        // Set the publish request fail (Reason header)
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "";
            int reasonHeaderCause = 400;
            String reasonHeaderText = "Bad Request";
            cb.onNetworkResponse(networkResp, reason, reasonHeaderCause, reasonHeaderText);
            listener.onPublish();
        });

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        // Verify that receive the publish failed callback
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        publishState = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(uceAdapter,
                a -> a.getUcePublishState(),
                ImsException.class,
                "android.permission.READ_PRIVILEGED_PHONE_STATE");
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR, publishState);

        overrideCarrierConfig(null);
    }

    @Test
    public void testRcsPublishThrottle() throws Exception {
        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SUPPORT_PUBLISHING_STATE_STRING);
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Trigger carrier config change
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot get the ImsManager");
        }
        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to the ImsService
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Setup the response of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "OK";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // Register the callback to listen to the publish state changed
        LinkedBlockingQueue<Integer> publishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener publishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                        publishStateQueue.offer(state);
                    }
                };

        final UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();
        try {
            automation.adoptShellPermissionIdentity();
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    publishStateCallback);
        } finally {
            automation.dropShellPermissionIdentity();
        }

        // Verify receiving the publish state callback immediately after registering the callback.
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // IMS registers
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // Verify the PUBLISH request should not be triggered and the publish state is still
        // NOT_PUBLISHED even the IMS is registered.
        if (publishStateQueue.poll() != null) {
            fail("The PUBLISH request should not be triggered.");
        }
        try {
            automation.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        } finally {
            automation.dropShellPermissionIdentity();
        }

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        // Notify framework to send the PUBLISH request to the ImsService.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify that ImsService received the first PUBLISH
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        try {
            automation.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        } finally {
            automation.dropShellPermissionIdentity();
        }

        // Now enable voice availability
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(new MmTelFeature.MmTelCapabilities(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE));

        // The published just succeeded. The next publish should not be triggered immediately even
        // the device capabilities has changed. Wait 3 seconds to verify the ImsService does not
        // receive the publish request from the framework.
        assertFalse(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH, 3000 /* 3 seconds */));

        // However, if the request is triggered from the service, a new publish request should be
        // sent immediately.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify the ImsService receive the publish request
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH, 3000 /* Wait up to 3 seconds */));

        overrideCarrierConfig(null);
    }

    @Test
    public void testRcsPublishWithSipOptions() throws Exception {
        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SUPPORT_PUBLISHING_STATE_STRING);
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Override the carrier config to support SIP OPTIONS
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot get the ImsManager");
        }
        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to the ImsService
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Setup the response of the publish request. In SIP OPTIONS mechanism, ImsService
        // should not receive any publish requests.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            fail("ImsService should not receive the PUBLISH request");
        });

        // Register the callback to listen to the publish state changed
        LinkedBlockingQueue<Integer> publishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener publishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                        publishStateQueue.offer(state);
                    }
                };

        final UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();

        // Verify receiving the publish state callback immediately after registering the callback
        // and the PUBLISH state is OK because the capability mechanism is SIP OPTIONS.
        try {
            automation.adoptShellPermissionIdentity();
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    publishStateCallback);
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        } finally {
            automation.dropShellPermissionIdentity();
            publishStateQueue.clear();
        }

        // IMS registers
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // Verify the PUBLISH request should not be triggered and the publish state is still
        // OK even the IMS is registered.
        if (publishStateQueue.poll() != null) {
            fail("The PUBLISH request should not be triggered.");
        }
        try {
            automation.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        } finally {
            automation.dropShellPermissionIdentity();
        }

        // Override the carrier config from SIP OPTIONS to PRESENCE.
        bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, false);
        overrideCarrierConfig(bundle);

        // When the capability type is changed from SIP OPTIONS to PRESENCE, the publish state
        // should be re-initialized to NOT_PUBLISHED
        try {
            // Verify receiving the callback that the publish state has changed from OK
            // to NOT_PUBLISH
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                    waitForIntResult(publishStateQueue));
            // Verify it by calling the API getPucePublishState
            automation.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        } finally {
            publishStateQueue.clear();
            automation.dropShellPermissionIdentity();
        }

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        // Setup the operation of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "OK";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // Notify framework to send the PUBLISH request to the ImsService.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify that ImsService received the first PUBLISH
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        // Verify that the publish state should be changed from NOT_PUBLISHED to OK
        try {
            assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING,
                    waitForIntResult(publishStateQueue));
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
            automation.adoptShellPermissionIdentity();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, uceAdapter.getUcePublishState());
        } finally {
            publishStateQueue.clear();
            automation.dropShellPermissionIdentity();
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testRcsPublishWithAuthorizedErrorResponse() throws Exception {
        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SUPPORT_PUBLISHING_STATE_STRING);
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Trigger carrier config change
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot get the ImsManager");
        }
        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to the ImsService
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        // Register the callback to listen to the publish state changed
        LinkedBlockingQueue<Integer> publishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener publishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                        publishStateQueue.offer(state);
                    }
                };

        final UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();
        try {
            automation.adoptShellPermissionIdentity();
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    publishStateCallback);
            // Verify receiving the publish state callback after registering the callback.
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                    waitForIntResult(publishStateQueue));
        } finally {
            publishStateQueue.clear();
            automation.dropShellPermissionIdentity();
        }

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Setup the response of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "OK";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // IMS registers
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        // Notify framework to send the PUBLISH request to the ImsService.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        try {
            // Verify the publish state callback is received.
            assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING,
                    waitForIntResult(publishStateQueue));
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
            // Verify the value of getting from the API is PUBLISH_STATE_OK
            automation.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        } finally {
            publishStateQueue.clear();
            automation.dropShellPermissionIdentity();
        }

        // Reply the SIP code 403 FORBIDDEN
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 403;
            String reason = "FORBIDDEN";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // Notify framework to send the PUBLISH request to the ImsService.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        try {
            // Verify the publish state callback is received.
            assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING,
                    waitForIntResult(publishStateQueue));
            assertEquals(RcsUceAdapter.PUBLISH_STATE_RCS_PROVISION_ERROR,
                    waitForIntResult(publishStateQueue));
            // Verify the value of getting from the API is PUBLISH_STATE_RCS_PROVISION_ERROR
            automation.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_RCS_PROVISION_ERROR, publishState);
        } finally {
            publishStateQueue.clear();
            automation.dropShellPermissionIdentity();
        }

        // Reply the SIP code 504 SERVER TIMEOUT
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 504;
            String reason = "SERVER TIMEOUT";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // Notify framework to send the PUBLISH request to the ImsService.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        try {
            // Verify the publish state callback is received.
            assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING,
                    waitForIntResult(publishStateQueue));
            assertEquals(RcsUceAdapter.PUBLISH_STATE_RCS_PROVISION_ERROR,
                    waitForIntResult(publishStateQueue));
            // Verify the value of getting from the API is PUBLISH_STATE_RCS_PROVISION_ERROR
            automation.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_RCS_PROVISION_ERROR, publishState);
        } finally {
            publishStateQueue.clear();
            automation.dropShellPermissionIdentity();
        }

        LinkedBlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
                capabilities.forEach(c -> capabilityQueue.offer(c));
            }
            @Override
            public void onComplete() {
                completeQueue.offer(true);
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(errorCode);
                errorRetryQueue.offer(retryAfterMilliseconds);
            }
        };

        capExchangeImpl.setSubscribeOperation((uris, cb) -> {
            fail("Should not received the SUBSCRIBE request");
        });

        Collection<Uri> contacts = Collections.singletonList(
                Uri.fromParts(PhoneAccount.SCHEME_SIP, "test", null));

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter,
                    adapter -> adapter.requestCapabilities(contacts, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE. "
                    + "Exception: " + e);
        } catch (ImsException e) {
            fail("requestCapabilities failed " + e);
        }

        // Verify the capability request should fail
        try {
            assertEquals(RcsUceAdapter.ERROR_NOT_AUTHORIZED, waitForIntResult(errorQueue));
            assertEquals(Long.valueOf(0L), waitForResult(errorRetryQueue));
        } catch (Exception e) {
            fail("requestCapabilities with command error failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
        }

        // Reset the UCE device state
        try {
            sServiceConnector.removeUceRequestDisallowedStatus(sTestSlot);
        } catch (Exception e) {
            fail("Cannot remove request disallowed status: " + e);
        }

        // Reply the SIP code 404 NOT FOUND
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 404;
            String reason = "NOT FOUND";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // Notify framework to send the PUBLISH request to the ImsService.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);
        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        // Verify the publish state callback is received.
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING,
                waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_RCS_PROVISION_ERROR,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();
        try {
            // Verify the value of getting from the API is PUBLISH_STATE_RCS_PROVISION_ERROR
            automation.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_RCS_PROVISION_ERROR, publishState);
        } finally {
            automation.dropShellPermissionIdentity();
        }

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter,
                    adapter -> adapter.requestCapabilities(contacts, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE. "
                    + "Exception: " + e);
        } catch (ImsException e) {
            fail("requestCapabilities failed " + e);
        }

        // Reset the UCE device state
        try {
            sServiceConnector.removeUceRequestDisallowedStatus(sTestSlot);
        } catch (Exception e) {
            fail("Cannot remove request disallowed status: " + e);
        }

        overrideCarrierConfig(null);
    }

    @Ignore("the compatibility framework does not currently support changing compatibility flags"
            + " on user builds for device side CTS tests. Ignore this test until support is added")
    @Test
    public void testRcsPublishWithDisableCompactCommand() throws Exception {
        TelephonyUtils.disableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SUPPORT_PUBLISHING_STATE_STRING);

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        RcsUceAdapter uceAdapter = imsRcsManager.getUceAdapter();

        // Connect to device ImsService with MmTel feature and RCS feature
        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        TestRcsCapabilityExchangeImpl capExchangeImpl = sServiceConnector.getCarrierService()
                .getRcsFeature().getRcsCapabilityExchangeImpl();

        // Register the callback to listen to the publish state changed
        LinkedBlockingQueue<Integer> publishStateQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.OnPublishStateChangedListener publishStateCallback =
                new RcsUceAdapter.OnPublishStateChangedListener() {
                    public void onPublishStateChange(int state) {
                        publishStateQueue.offer(state);
                    }
                };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            // register publish state callback
            uceAdapter.addOnPublishStateChangedListener(getContext().getMainExecutor(),
                    publishStateCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }
        // Verify receiving the publish state callback immediately after registering the callback.
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Verify the value of getting from the API is NOT_PUBLISHED
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Setup the operation of the publish request.
        capExchangeImpl.setPublishOperator((listener, pidfXml, cb) -> {
            int networkResp = 200;
            String reason = "";
            cb.onNetworkResponse(networkResp, reason);
            listener.onPublish();
        });

        // IMS registers
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);

        // Framework should not trigger the device capabilities publish when the framework doesn't
        // receive that the RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE is enabled.
        if (publishStateQueue.poll() != null) {
            fail("The publish callback should not be called because presence uce is not ready");
        }

        // Notify framework that the RCS capability status is changed and PRESENCE UCE is enabled.
        RcsImsCapabilities capabilities =
                new RcsImsCapabilities(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        // Sending Publish means that notifyPendingPublicRequest() has been processed.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        // Since framework compatibility is disabled, the newly added publishing state should
        // not be set and should be set to PUBLISH_STATE_OK
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Verify the value of getting from the API is PUBLISH_STATE_OK
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);
        // Verify ImsService receive the publish request from framework.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        // Since framework compatibility is disabled, the newly added publishing state should
        // not be set and should be set to PUBLISH_STATE_OK
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();
        // Verify the value of getting from the API is PUBLISH_STATE_OK
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }
        publishStateQueue.clear();

        // ImsService triggers to notify framework publish updated.
        eventListener.onPublishUpdated(400, "", 0, "");
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR,
                waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Verify the value of getting from the API is PUBLISH_STATE_OTHER_ERROR
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // ImsService triggers to notify framework publish device's capabilities.
        eventListener.onRequestPublishCapabilities(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_WLAN);

        // Verify ImsService receive the publish request from framework.
        // Sending Publish means that notifyPendingPublicRequest() has been processed.
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_UCE_REQUEST_PUBLISH));

        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED,
                waitForIntResult(publishStateQueue));
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, waitForIntResult(publishStateQueue));
        publishStateQueue.clear();

        // Verify the value of getting from the API is PUBLISH_STATE_OK
        try {
            automan.adoptShellPermissionIdentity();
            int publishState = uceAdapter.getUcePublishState();
            assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        } finally {
            automan.dropShellPermissionIdentity();
        }
        overrideCarrierConfig(null);
    }

    @Test
    public void testRcsManagerRegistrationCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        // Connect to device ImsService with RcsFeature
        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();
        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);

        // Override the carrier config
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_RCS_CAP_SET);

        // Start de-registered
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));

        LinkedBlockingQueue<Integer> mQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<SipDetails> mDeregiQueue = new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onRegistered(int imsTransportType) {
                        mQueue.offer(imsTransportType);
                    }

                    @Override
                    public void onRegistering(int imsTransportType) {
                        mQueue.offer(imsTransportType);
                    }

                    @Override
                    public void onUnregistered(ImsReasonInfo info) {
                        mQueue.offer(info.getCode());
                    }

                    @Override
                    public void onUnregistered(ImsReasonInfo info, SipDetails details) {
                        mQueue.offer(info.getCode());
                        mDeregiQueue.offer(details);
                    }

                    @Override
                    public void onTechnologyChangeFailed(int imsTransportType, ImsReasonInfo info) {
                        mQueue.offer(imsTransportType);
                        mQueue.offer(info.getCode());
                    }
                };

        // Verify the registerImsRegistrationCallback should fail without the required permission
        try {
            imsRcsManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
            fail("registerImsRegistrationCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(imsRcsManager,
                m -> m.registerImsRegistrationCallback(getContext().getMainExecutor(), callback),
                ImsException.class,
                "android.permission.READ_PRECISE_PHONE_STATE");

        // Verify it's not registered
        assertEquals(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, waitForIntResult(mQueue));
        assertTrue(mDeregiQueue.isEmpty());

        // Start registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistering(
                IMS_REGI_TECH_LTE);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));

        // Complete registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));

        // Fail handover to IWLAN
        sServiceConnector.getCarrierService().getImsRegistration().onTechnologyChangeFailed(
                IMS_REGI_TECH_IWLAN,
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, waitForIntResult(mQueue));
        assertEquals(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE, waitForIntResult(mQueue));

        int cSeq = 1;
        int responseCode = 200;
        String responsePhrase = "OK";
        String callId = "testCall-Id";
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""),
                new SipDetails.Builder(SipDetails.METHOD_REGISTER)
                                .setCSeq(cSeq).setSipResponseCode(responseCode, responsePhrase)
                                .setCallId(callId).build()
        );
        assertEquals(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, waitForIntResult(mQueue));
        SipDetails details = waitForResult(mDeregiQueue);
        assertEquals(SipDetails.METHOD_REGISTER, details.getMethod());
        assertEquals(cSeq, details.getCSeq());
        assertEquals(responseCode, details.getResponseCode());
        assertEquals(responsePhrase, details.getResponsePhrase());
        assertEquals(callId, details.getCallId());

        // Verify the unregisterImsRegistrationCallback should failure without the permission.
        try {
            imsRcsManager.unregisterImsRegistrationCallback(callback);
            fail("unregisterImsRegistrationCallback requires READ_PRECISE_PHONE_STATE permission");
        } catch (SecurityException e) {
            //expected
        }

        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(imsRcsManager,
                m -> m.unregisterImsRegistrationCallback(callback),
                ImsException.class,
                "android.permission.READ_PRECISE_PHONE_STATE");

        overrideCarrierConfig(null);
    }

    @Test
    public void testMmTelManagerRegistrationStateR() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RegistrationManager regManager = imsManager.getImsMmTelManager(sTestSub);
        LinkedBlockingQueue<Integer> mQueue = new LinkedBlockingQueue<>();

        triggerFrameworkConnectToCarrierImsService();

        // Start deregistered
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));

        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onRegistered(int imsTransportType) {
                        mQueue.offer(imsTransportType);
                    }

                    @Override
                    public void onRegistering(int imsTransportType) {
                        mQueue.offer(imsTransportType);
                    }

                    @Override
                    public void onUnregistered(ImsReasonInfo info) {
                        mQueue.offer(info.getCode());
                    }

                    @Override
                    public void onTechnologyChangeFailed(int imsTransportType, ImsReasonInfo info) {
                        mQueue.offer(imsTransportType);
                        mQueue.offer(info.getCode());
                    }
                };

        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(mmTelManager,
                (m) -> m.registerImsRegistrationCallback(getContext().getMainExecutor(), callback),
                ImsException.class);
        assertEquals(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, waitForIntResult(mQueue));

        // Ensure that the Framework reports Deregistered correctly
        verifyRegistrationState(regManager, RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED);
        verifyRegistrationTransportType(regManager, AccessNetworkConstants.TRANSPORT_TYPE_INVALID);

        // Start registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistering(
                IMS_REGI_TECH_LTE);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));
        verifyRegistrationState(regManager, RegistrationManager.REGISTRATION_STATE_REGISTERING);
        verifyRegistrationTransportType(regManager, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Complete registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));
        verifyRegistrationState(regManager, RegistrationManager.REGISTRATION_STATE_REGISTERED);
        verifyRegistrationTransportType(regManager, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);


        // Fail handover to IWLAN
        sServiceConnector.getCarrierService().getImsRegistration().onTechnologyChangeFailed(
                IMS_REGI_TECH_IWLAN,
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, waitForIntResult(mQueue));
        assertEquals(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE, waitForIntResult(mQueue));
        verifyRegistrationTransportType(regManager, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // handover to IWLAN
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_IWLAN);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, waitForIntResult(mQueue));
        verifyRegistrationTransportType(regManager, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                (m) -> m.unregisterImsRegistrationCallback(callback));
    }

    @Test
    public void testRcsManagerRegistrationState() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        // Connect to device ImsService with RcsFeature
        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();

        // Override the carrier config
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_RCS_CAP_SET);

        // Start de-registered
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));

        LinkedBlockingQueue<Integer> mQueue = new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onRegistered(int imsTransportType) {
                        mQueue.offer(imsTransportType);
                    }

                    @Override
                    public void onRegistering(int imsTransportType) {
                        mQueue.offer(imsTransportType);
                    }

                    @Override
                    public void onUnregistered(ImsReasonInfo info) {
                        mQueue.offer(info.getCode());
                    }

                    @Override
                    public void onTechnologyChangeFailed(int imsTransportType, ImsReasonInfo info) {
                        mQueue.offer(imsTransportType);
                        mQueue.offer(info.getCode());
                    }
                };

        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(imsRcsManager,
                (m) -> m.registerImsRegistrationCallback(getContext().getMainExecutor(), callback),
                ImsException.class);
        assertEquals(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, waitForIntResult(mQueue));

        // Verify the getRegistrationState should fail without the required permission
        try {
            imsRcsManager.getRegistrationState(getContext().getMainExecutor(), mQueue::offer);
            fail("getRegistrationState requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // Verify the getRegistrationTransportType should fail without the required permission
        try {
            imsRcsManager.getRegistrationTransportType(getContext().getMainExecutor(),
                    mQueue::offer);
            fail("getRegistrationTransportType requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // Ensure that the Framework reports Deregistered correctly
        verifyRegistrationState(imsRcsManager,
                RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED);
        verifyRegistrationTransportType(imsRcsManager,
                AccessNetworkConstants.TRANSPORT_TYPE_INVALID);

        // Start registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistering(
                IMS_REGI_TECH_LTE);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));
        verifyRegistrationState(imsRcsManager, RegistrationManager.REGISTRATION_STATE_REGISTERING);
        verifyRegistrationTransportType(imsRcsManager, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Complete registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));
        verifyRegistrationState(imsRcsManager, RegistrationManager.REGISTRATION_STATE_REGISTERED);
        verifyRegistrationTransportType(imsRcsManager, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Start registration over NR
        sServiceConnector.getCarrierService().getImsRegistration().onRegistering(
                IMS_REGI_TECH_NR);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));
        verifyRegistrationState(imsRcsManager, RegistrationManager.REGISTRATION_STATE_REGISTERING);
        verifyRegistrationTransportType(imsRcsManager, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Complete registration over NR
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_NR);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, waitForIntResult(mQueue));
        verifyRegistrationState(imsRcsManager, RegistrationManager.REGISTRATION_STATE_REGISTERED);
        verifyRegistrationTransportType(imsRcsManager, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Fail handover to IWLAN
        sServiceConnector.getCarrierService().getImsRegistration().onTechnologyChangeFailed(
                IMS_REGI_TECH_IWLAN,
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, waitForIntResult(mQueue));
        assertEquals(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE, waitForIntResult(mQueue));
        verifyRegistrationTransportType(imsRcsManager, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // handover to IWLAN
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_IWLAN);
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, waitForIntResult(mQueue));
        verifyRegistrationTransportType(imsRcsManager, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(imsRcsManager,
                (m) -> m.unregisterImsRegistrationCallback(callback));

        overrideCarrierConfig(null);
    }

    @Test
    public void testCapabilityStatusCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);

        triggerFrameworkConnectToCarrierImsService();

        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);
        MmTelFeature.MmTelCapabilities fwCaps = sServiceConnector.getCarrierService()
                .getMmTelFeature().getCapabilities();
        // Make sure we start off with every capability unavailable
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(new MmTelFeature.MmTelCapabilities());

        // Make sure the capabilities match the API getter for capabilities
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // Latch will count down here (we callback on the state during registration).
        try {
            automan.adoptShellPermissionIdentity();
            // Make sure we are tracking voice capability over LTE properly.
            assertEquals(fwCaps.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE),
                    mmTelManager.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                            IMS_REGI_TECH_LTE));
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // This is a little bit gross looking, but on P devices, I can not define classes that
        // extend ImsMmTelManager.CapabilityCallback (because it doesn't exist), so this has to
        // happen as an anon class here.
        LinkedBlockingQueue<MmTelFeature.MmTelCapabilities> mQueue = new LinkedBlockingQueue<>();
        ImsMmTelManager.CapabilityCallback callback = new ImsMmTelManager.CapabilityCallback() {

            @Override
            public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities capabilities) {
                mQueue.offer(capabilities);
            }
        };

        // Latch will count down here (we callback on the state during registration).
        try {
            automan.adoptShellPermissionIdentity();
            mmTelManager.registerMmTelCapabilityCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        try {
            mmTelManager.registerMmTelCapabilityCallback(getContext().getMainExecutor(), callback);
            fail("registerMmTelCapabilityCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // We should not have voice availability here, we notified the framework earlier.
        MmTelFeature.MmTelCapabilities capCb = waitForResult(mQueue);
        assertNotNull(capCb);
        assertFalse(capCb.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE));

        // Now enable voice availability
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(new MmTelFeature.MmTelCapabilities(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE));
        capCb = waitForResult(mQueue);
        assertNotNull(capCb);
        assertTrue(capCb.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE));

        try {
            automan.adoptShellPermissionIdentity();
            assertTrue(ImsUtils.retryUntilTrue(() -> mmTelManager.isAvailable(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                    IMS_REGI_TECH_LTE)));

            mmTelManager.unregisterMmTelCapabilityCallback(callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        try {
            mmTelManager.unregisterMmTelCapabilityCallback(callback);
            fail("unregisterMmTelCapabilityCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }
    }

    @Test
    public void testCallComposerCapabilityStatusCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);

        triggerFrameworkConnectToCarrierImsService();

        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);
        MmTelFeature.MmTelCapabilities fwCaps = sServiceConnector.getCarrierService()
                .getMmTelFeature().getCapabilities();
        // Make sure we start off with every capability unavailable
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(new MmTelFeature.MmTelCapabilities());

        // Make sure the capabilities match the API getter for capabilities
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // Latch will count down here (we callback on the state during registration).
        try {
            automan.adoptShellPermissionIdentity();
            // Make sure we are tracking voice capability over LTE properly.
            assertEquals(fwCaps.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE),
                    mmTelManager.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                    IMS_REGI_TECH_LTE));
        } finally {
            automan.dropShellPermissionIdentity();
        }

        LinkedBlockingQueue<MmTelFeature.MmTelCapabilities> mQueue = new LinkedBlockingQueue<>();
        ImsMmTelManager.CapabilityCallback callback = new ImsMmTelManager.CapabilityCallback() {

            @Override
            public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities capabilities) {
                mQueue.offer(capabilities);
            }
        };

        // Latch will count down here (we callback on the state during registration).
        try {
            automan.adoptShellPermissionIdentity();
            mmTelManager.registerMmTelCapabilityCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        try {
            mmTelManager.registerMmTelCapabilityCallback(getContext().getMainExecutor(), callback);
            fail("registerMmTelCapabilityCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // We should not have voice availability here, we notified the framework earlier.
        MmTelFeature.MmTelCapabilities capCb = waitForResult(mQueue);
        assertFalse(capCb.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER));
        if (com.android.server.telecom.flags.Flags.businessCallComposer()) {
            assertFalse(capCb.isCapable(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER_BUSINESS_ONLY));
        }
        // Now enable call composer availability
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(new MmTelFeature.MmTelCapabilities(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER));
        capCb = waitForResult(mQueue);
        assertNotNull(capCb);
        assertTrue(capCb.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER));
        if (com.android.server.telecom.flags.Flags.businessCallComposer()) {
            assertFalse(capCb.isCapable(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER_BUSINESS_ONLY));
        }

        try {
            automan.adoptShellPermissionIdentity();
            assertTrue(ImsUtils.retryUntilTrue(() -> mmTelManager.isAvailable(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER,
                    IMS_REGI_TECH_LTE)));
        } finally {
            automan.dropShellPermissionIdentity();
        }

        if (com.android.server.telecom.flags.Flags.businessCallComposer()) {
            // Now enable call composer availability
            sServiceConnector.getCarrierService().getMmTelFeature()
                    .notifyCapabilitiesStatusChanged(new MmTelFeature.MmTelCapabilities(
                            MmTelFeature.MmTelCapabilities
                                    .CAPABILITY_TYPE_CALL_COMPOSER_BUSINESS_ONLY));
            capCb = waitForResult(mQueue);
            assertNotNull(capCb);
            assertTrue(capCb.isCapable(MmTelFeature.MmTelCapabilities
                    .CAPABILITY_TYPE_CALL_COMPOSER_BUSINESS_ONLY));
            assertFalse(capCb.isCapable(MmTelFeature.MmTelCapabilities
                    .CAPABILITY_TYPE_CALL_COMPOSER));

            try {
                automan.adoptShellPermissionIdentity();
                assertTrue(ImsUtils.retryUntilTrue(() -> mmTelManager.isAvailable(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER_BUSINESS_ONLY,
                        IMS_REGI_TECH_LTE)));
                mmTelManager.unregisterMmTelCapabilityCallback(callback);
            } finally {
                automan.dropShellPermissionIdentity();
            }
        }

        try {
            mmTelManager.unregisterMmTelCapabilityCallback(callback);
            fail("unregisterMmTelCapabilityCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }
    }

    /**
     * We are specifically testing a race case here such that IsAvailable returns the correct
     * capability status during the callback.
     */
    @Test
    public void testCapabilityStatusWithIsAvailableDuringCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);

        triggerFrameworkConnectToCarrierImsService();

        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);


        // Make sure we start off with every capability unavailable
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);
        MmTelFeature.MmTelCapabilities stdCapabilities = new MmTelFeature.MmTelCapabilities();
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(stdCapabilities);


        // Make sure the capabilities match the API getter for capabilities
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        //This lock is to keep the shell permissions from being dropped on a different thread
        //causing a permission error.
        Object lockObj = new Object();

        synchronized (lockObj) {
            try {
                automan.adoptShellPermissionIdentity();
                boolean isAvailableBeforeStatusChange = mmTelManager.isAvailable(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                        IMS_REGI_TECH_LTE);
                assertFalse(isAvailableBeforeStatusChange);
            } finally {
                automan.dropShellPermissionIdentity();
            }
        }

        LinkedBlockingQueue<Boolean> voiceIsAvailable = new LinkedBlockingQueue<>();
        ImsMmTelManager.CapabilityCallback verifyCapabilityStatusCallaback =
                new ImsMmTelManager.CapabilityCallback() {
            @Override
            public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities capabilities) {
                synchronized (lockObj) {
                    try {
                        automan.adoptShellPermissionIdentity();
                        boolean isVoiceAvailable = mmTelManager
                                .isAvailable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                                        IMS_REGI_TECH_LTE);

                        voiceIsAvailable.offer(isVoiceAvailable);
                    } finally {
                        automan.dropShellPermissionIdentity();
                    }
                }
            }
        };

        synchronized (lockObj) {
            // Latch will count down here (we callback on the state during registration).
            try {
                automan.adoptShellPermissionIdentity();
                mmTelManager.registerMmTelCapabilityCallback(getContext().getMainExecutor(),
                        verifyCapabilityStatusCallaback);
            } finally {
                automan.dropShellPermissionIdentity();
            }
        }

        // Now enable voice availability
        Boolean isAvailableDuringRegister = waitForResult(voiceIsAvailable);
        assertNotNull(isAvailableDuringRegister);
        assertFalse(isAvailableDuringRegister);
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(new MmTelFeature.MmTelCapabilities(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE));
        Boolean isAvailableAfterStatusChange = waitForResult(voiceIsAvailable);
        assertNotNull(isAvailableAfterStatusChange);
        assertTrue(isAvailableAfterStatusChange);

        synchronized (lockObj) {
            try {
                automan.adoptShellPermissionIdentity();
                mmTelManager.unregisterMmTelCapabilityCallback(verifyCapabilityStatusCallaback);
            } finally {
                automan.dropShellPermissionIdentity();
            }
        }
    }

    @Test
    public void testRcsCapabilityStatusCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        // Connect to device ImsService with RcsFeature
        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();

        int registrationTech = IMS_REGI_TECH_LTE;
        ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(sTestSub);

        // Make sure we start off with none-capability
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);
        RcsImsCapabilities noCapabilities = new RcsImsCapabilities(RCS_CAP_NONE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(noCapabilities);

        // Make sure the capabilities match the API getter for capabilities
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // Latch will count down here (we callback on the state during registration).
        try {
            automan.adoptShellPermissionIdentity();
            // Make sure we are tracking voice capability over LTE properly.
            RcsImsCapabilities availability = sServiceConnector.getCarrierService()
                    .getRcsFeature().queryCapabilityStatus();
            assertFalse(availability.isCapable(RCS_CAP_OPTIONS));
            assertFalse(availability.isCapable(RCS_CAP_PRESENCE));
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_RCS_CAP_SET);

        // The carrier config changed should trigger RcsFeature#changeEnabledCapabilities
        try {
            automan.adoptShellPermissionIdentity();
            // Checked by isCapable api to make sure RcsFeature#changeEnabledCapabilities is called
            assertTrue(ImsUtils.retryUntilTrue(() ->
                    imsRcsManager.isCapable(RCS_CAP_OPTIONS, registrationTech)));
            assertTrue(ImsUtils.retryUntilTrue(() ->
                    imsRcsManager.isCapable(RCS_CAP_PRESENCE, registrationTech)));
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // A queue to receive capability changed
        LinkedBlockingQueue<Integer> availabilityChanged = new LinkedBlockingQueue<>();
        ImsRcsManager.OnAvailabilityChangedListener callback =
                new ImsRcsManager.OnAvailabilityChangedListener() {
            @Override
            public void onAvailabilityChanged(
                    @RcsUceAdapter.RcsImsCapabilityFlag int capabilities) {
                availabilityChanged.offer(capabilities);
            }
        };

        // Latch will count down here (we callback on the state during registration).
        try {
            automan.adoptShellPermissionIdentity();
            imsRcsManager.addOnAvailabilityChangedListener(
                    getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Verify the callback and the api isAvailable that the capabilities is NONE in the
        // beginning.
        int radioTechLTE = IMS_REGI_TECH_LTE;
        int capCb = waitForResult(availabilityChanged);
        assertEquals(capCb, RCS_CAP_NONE);
        availabilityChanged.clear();
        try {
            automan.adoptShellPermissionIdentity();
            assertFalse(imsRcsManager.isAvailable(RCS_CAP_OPTIONS, radioTechLTE));
            assertFalse(imsRcsManager.isAvailable(RCS_CAP_PRESENCE, radioTechLTE));
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Notify capabilities status change to OPTIONS
        RcsImsCapabilities optionsCap = new RcsImsCapabilities(RCS_CAP_OPTIONS);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(optionsCap);

        // Verify that the callback onAvailabilityChanged is called with OPTIONS
        capCb = waitForResult(availabilityChanged);
        assertEquals(capCb, RCS_CAP_OPTIONS);
        availabilityChanged.clear();
        try {
            automan.adoptShellPermissionIdentity();
            assertTrue(imsRcsManager.isAvailable(RCS_CAP_OPTIONS, radioTechLTE));
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Notify capabilities status change to PRESENCE
        RcsImsCapabilities presenceCap = new RcsImsCapabilities(RCS_CAP_PRESENCE);
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(presenceCap);

        // Verify that the callback onAvailabilityChanged is called with PRESENCE
        capCb = waitForResult(availabilityChanged);
        assertEquals(capCb, RCS_CAP_PRESENCE);
        availabilityChanged.clear();
        try {
            automan.adoptShellPermissionIdentity();
            assertTrue(imsRcsManager.isAvailable(RCS_CAP_PRESENCE, radioTechLTE));
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Remove availability changed listener
        try {
            automan.adoptShellPermissionIdentity();
            imsRcsManager.removeOnAvailabilityChangedListener(callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Notify capabilities status changes again.
        sServiceConnector.getCarrierService().getRcsFeature()
                .notifyCapabilitiesStatusChanged(optionsCap);

        // The callback should not be called because the listener is removed.
        assertTrue(availabilityChanged.isEmpty());

        overrideCarrierConfig(null);
    }

    @Test
    public void testProvisioningManagerSetConfig() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        triggerFrameworkConnectToCarrierImsService();

        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        // This is a little bit gross looking, but on P devices, I can not define classes that
        // extend ProvisioningManager.Callback (because it doesn't exist), so this has to
        // happen as an anon class here.
        LinkedBlockingQueue<Pair<Integer, Integer>> mIntQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Pair<Integer, String>> mStringQueue = new LinkedBlockingQueue<>();
        ProvisioningManager.Callback callback = new ProvisioningManager.Callback() {
            @Override
            public void onProvisioningIntChanged(int item, int value) {
                mIntQueue.offer(new Pair<>(item, value));
            }

            @Override
            public void onProvisioningStringChanged(int item, String value) {
                mStringQueue.offer(new Pair<>(item, value));
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerProvisioningChangedCallback(getContext().getMainExecutor(),
                    callback);

            provisioningManager.setProvisioningIntValue(TEST_CONFIG_KEY, TEST_CONFIG_VALUE_INT);
            assertTrue(waitForParam(mIntQueue, new Pair<>(TEST_CONFIG_KEY, TEST_CONFIG_VALUE_INT)));
            assertEquals(TEST_CONFIG_VALUE_INT,
                    provisioningManager.getProvisioningIntValue(TEST_CONFIG_KEY));

            provisioningManager.setProvisioningStringValue(TEST_CONFIG_KEY,
                    TEST_CONFIG_VALUE_STRING);
            assertTrue(waitForParam(mStringQueue,
                    new Pair<>(TEST_CONFIG_KEY, TEST_CONFIG_VALUE_STRING)));
            assertEquals(TEST_CONFIG_VALUE_STRING,
                    provisioningManager.getProvisioningStringValue(TEST_CONFIG_KEY));

            automan.adoptShellPermissionIdentity();
            provisioningManager.unregisterProvisioningChangedCallback(callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testProvisioningManagerWhenMmtelProvisionIsRequired() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // test in case provision for mmtel is required
        PersistableBundle bundle = new PersistableBundle();

        PersistableBundle innerBundle = new PersistableBundle();
        innerBundle.putIntArray(
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                new int[]{IMS_REGI_TECH_LTE, IMS_REGI_TECH_IWLAN}
        );
        innerBundle.putIntArray(
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                new int[]{IMS_REGI_TECH_LTE}
        );

        bundle.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE,
                innerBundle);

        overrideCarrierConfig(bundle);

        triggerFrameworkConnectToCarrierImsService();
        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        LinkedBlockingQueue<Pair<Integer, Integer>> mIntQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Pair<Integer, Pair<Integer, Boolean>>> mOnFeatureChangedQueue =
                new LinkedBlockingQueue<>();

        ProvisioningManager.Callback callback = new ProvisioningManager.Callback() {
            @Override
            public void onProvisioningIntChanged(int item, int value) {
                mIntQueue.offer(new Pair<>(item, value));
            }
        };

        ProvisioningManager.FeatureProvisioningCallback featureProvisioningCallback =
                new ProvisioningManager.FeatureProvisioningCallback() {
            @Override
            public void onFeatureProvisioningChanged(
                    @MmTelFeature.MmTelCapabilities.MmTelCapability int capability,
                    @ImsRegistrationImplBase.ImsRegistrationTech int tech,
                    boolean isProvisioned) {
                mOnFeatureChangedQueue.offer(new Pair<>(capability,
                        new Pair<>(tech, isProvisioned)));
            }

            @Override
            public void onRcsFeatureProvisioningChanged(
                    @RcsFeature.RcsImsCapabilities.RcsImsCapabilityFlag int capability,
                    @ImsRegistrationImplBase.ImsRegistrationTech int tech,
                    boolean isProvisioned){
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerProvisioningChangedCallback(getContext().getMainExecutor(),
                    callback);
            provisioningManager.registerFeatureProvisioningChangedCallback(
                    getContext().getMainExecutor(), featureProvisioningCallback);

            TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING);

            // test get/setProvisioningStatusForCapability for VoLTE
            assertTrue(provisioningManager.isProvisioningRequiredForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE));
            boolean isProvisioned = provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE);
            provisioningManager.setProvisioningStatusForCapability(MMTEL_CAP_VOICE,
                    IMS_REGI_TECH_LTE, !isProvisioned);
            assertTrue(waitForParam(mOnFeatureChangedQueue,
                    new Pair<>(MMTEL_CAP_VOICE, new Pair<>(IMS_REGI_TECH_LTE, !isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VOLTE_PROVISIONING_STATUS, !isProvisioned ? 1 : 0)));
            assertEquals(!isProvisioned, provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE));
            mIntQueue.clear();
            mOnFeatureChangedQueue.clear();
            provisioningManager.setProvisioningStatusForCapability(MMTEL_CAP_VOICE,
                    IMS_REGI_TECH_LTE, isProvisioned);
            assertTrue(waitForParam(mOnFeatureChangedQueue,
                    new Pair<>(MMTEL_CAP_VOICE, new Pair<>(IMS_REGI_TECH_LTE, isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VOLTE_PROVISIONING_STATUS, isProvisioned ? 1 : 0)));
            assertEquals(isProvisioned, provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE));

            // test get/setProvisioningStatusForCapability for VoWIFI
            assertTrue(provisioningManager.isProvisioningRequiredForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN));
            isProvisioned = provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN);
            mIntQueue.clear();
            mOnFeatureChangedQueue.clear();
            provisioningManager.setProvisioningStatusForCapability(MMTEL_CAP_VOICE,
                    IMS_REGI_TECH_IWLAN, !isProvisioned);
            assertTrue(waitForParam(mOnFeatureChangedQueue,
                    new Pair<>(MMTEL_CAP_VOICE, new Pair<>(IMS_REGI_TECH_IWLAN, !isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, !isProvisioned ? 1 : 0)));
            assertEquals(!isProvisioned, provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN));
            mIntQueue.clear();
            mOnFeatureChangedQueue.clear();
            provisioningManager.setProvisioningStatusForCapability(MMTEL_CAP_VOICE,
                    IMS_REGI_TECH_IWLAN, isProvisioned);
            assertTrue(waitForParam(mOnFeatureChangedQueue,
                    new Pair<>(MMTEL_CAP_VOICE, new Pair<>(IMS_REGI_TECH_IWLAN, isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, isProvisioned ? 1 : 0)));
            assertEquals(isProvisioned, provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN));

            // test get/setProvisioningStatusForCapability for VT
            assertTrue(provisioningManager.isProvisioningRequiredForCapability(
                    MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE));
            isProvisioned = provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE);
            mIntQueue.clear();
            mOnFeatureChangedQueue.clear();
            provisioningManager.setProvisioningStatusForCapability(MMTEL_CAP_VIDEO,
                    IMS_REGI_TECH_LTE, !isProvisioned);
            assertTrue(waitForParam(mOnFeatureChangedQueue,
                    new Pair<>(MMTEL_CAP_VIDEO, new Pair<>(IMS_REGI_TECH_LTE, !isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VT_PROVISIONING_STATUS, !isProvisioned ? 1 : 0)));
            assertEquals(!isProvisioned, provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE));
            mIntQueue.clear();
            mOnFeatureChangedQueue.clear();
            provisioningManager.setProvisioningStatusForCapability(MMTEL_CAP_VIDEO,
                    IMS_REGI_TECH_LTE, isProvisioned);
            assertTrue(waitForParam(mOnFeatureChangedQueue,
                    new Pair<>(MMTEL_CAP_VIDEO, new Pair<>(IMS_REGI_TECH_LTE, isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VT_PROVISIONING_STATUS, isProvisioned ? 1 : 0)));
            assertEquals(isProvisioned, provisioningManager
                    .getProvisioningStatusForCapability(MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE));

            TelephonyUtils.disableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING);

            // test get/setProvisioningStatusForCapability with lower bounding parameters
            // when callback is not supported

            isProvisioned = provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE);
            mIntQueue.clear();
            mOnFeatureChangedQueue.clear();
            provisioningManager.setProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE, !isProvisioned);
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VOLTE_PROVISIONING_STATUS, !isProvisioned ? 1 : 0)));
            assertEquals(!isProvisioned,
                    provisioningManager.getProvisioningStatusForCapability(
                            MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE));
            isProvisioned = provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN);
            mIntQueue.clear();
            mOnFeatureChangedQueue.clear();
            provisioningManager.setProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN, !isProvisioned);
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, !isProvisioned ? 1 : 0)));
            assertEquals(!isProvisioned,
                    provisioningManager.getProvisioningStatusForCapability(
                            MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN));

            isProvisioned = provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE);
            mIntQueue.clear();
            mOnFeatureChangedQueue.clear();
            provisioningManager.setProvisioningStatusForCapability(
                    MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE, !isProvisioned);
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_VT_PROVISIONING_STATUS, !isProvisioned ? 1 : 0)));
            assertEquals(!isProvisioned,
                    provisioningManager.getProvisioningStatusForCapability(
                            MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE));

            automan.adoptShellPermissionIdentity();
            provisioningManager.unregisterProvisioningChangedCallback(callback);
            provisioningManager.unregisterFeatureProvisioningChangedCallback(
                    featureProvisioningCallback);
        } finally {
            TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING);
            automan.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testProvisioningManagerWhenMmtelProvisionIsNotRequired() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // test in case provision for mmtel is required
        PersistableBundle bundle = new PersistableBundle();
        PersistableBundle innerBundle = new PersistableBundle();

        bundle.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE,
                innerBundle);
        bundle.putBoolean(
                CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL, false);
        overrideCarrierConfig(bundle);

        triggerFrameworkConnectToCarrierImsService();
        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        LinkedBlockingQueue<Pair<Integer, Integer>> mIntQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Pair<Integer, Pair<Integer, Boolean>>> mOnFeatureChangedQueue =
                new LinkedBlockingQueue<>();


        ProvisioningManager.Callback callback = new ProvisioningManager.Callback() {};

        ProvisioningManager.FeatureProvisioningCallback featureProvisioningCallback =
                new ProvisioningManager.FeatureProvisioningCallback() {
            @Override
            public void onFeatureProvisioningChanged(
                    @MmTelFeature.MmTelCapabilities.MmTelCapability int capability,
                    @ImsRegistrationImplBase.ImsRegistrationTech int tech,
                    boolean isProvisioned) {
            }

            @Override
            public void onRcsFeatureProvisioningChanged(
                    @RcsFeature.RcsImsCapabilities.RcsImsCapabilityFlag int capability,
                    @ImsRegistrationImplBase.ImsRegistrationTech int tech,
                    boolean isProvisioned){
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerProvisioningChangedCallback(getContext().getMainExecutor(),
                    callback);
            provisioningManager.registerFeatureProvisioningChangedCallback(
                    getContext().getMainExecutor(), featureProvisioningCallback);

            // In case provisioning is not required
            // true will be returned regardless of stored value
            // ignore set value whatever value is set by app
            // therefore set different value from current then check if the value has changed
            // test get/setProvisioningStatusForCapability for VoLTE

            // isProvisioningRequiredForCapability should return false because provision is not
            // required
            assertTrue(!provisioningManager.isProvisioningRequiredForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE));
            // However, getProvisioningStatusForCapability() should return true because it does not
            // require provision
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE));
            // put opposite value to check if the key is changed or not
            provisioningManager.setProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE, false);
            // key value should not be changed whatever value is set
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_LTE));

            // test case for VoWIFI
            assertTrue(!provisioningManager.isProvisioningRequiredForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN));
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN));
            provisioningManager.setProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN, false);
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VOICE, IMS_REGI_TECH_IWLAN));

            // test case for VT
            assertTrue(!provisioningManager.isProvisioningRequiredForCapability(
                    MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE));
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE));
            provisioningManager.setProvisioningStatusForCapability(
                    MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE, false);
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    MMTEL_CAP_VIDEO, IMS_REGI_TECH_LTE));

            automan.adoptShellPermissionIdentity();
            provisioningManager.unregisterProvisioningChangedCallback(callback);
            provisioningManager.unregisterFeatureProvisioningChangedCallback(
                    featureProvisioningCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testProvisioningManagerWhenRcsProvisionIsRequired() throws Exception {
        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            return;
        }

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                true);

        PersistableBundle innerBundle = new PersistableBundle();
        innerBundle.putIntArray(
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY,
                new int[]{IMS_REGI_TECH_LTE, IMS_REGI_TECH_IWLAN, IMS_REGI_TECH_CROSS_SIM,
                        IMS_REGI_TECH_NR}
        );
        bundle.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_RCS_REQUIRES_PROVISIONING_BUNDLE,
                innerBundle);

        overrideCarrierConfig(bundle);

        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        LinkedBlockingQueue<Pair<Integer, Integer>> mIntQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Pair<Integer, Pair<Integer, Boolean>>> mOnRcsFeatureChangedQueue =
                new LinkedBlockingQueue<>();

        ProvisioningManager.Callback callback = new ProvisioningManager.Callback() {
            @Override
            public void onProvisioningIntChanged(int item, int value) {
                mIntQueue.offer(new Pair<>(item, value));
            }

        };

        ProvisioningManager.FeatureProvisioningCallback featureProvisioningCallback =
                new ProvisioningManager.FeatureProvisioningCallback() {
            @Override
            public void onFeatureProvisioningChanged(
                    @MmTelFeature.MmTelCapabilities.MmTelCapability int capability,
                    @ImsRegistrationImplBase.ImsRegistrationTech int tech,
                    boolean isProvisioned) {
            }

            @Override
            public void onRcsFeatureProvisioningChanged(
                    @RcsFeature.RcsImsCapabilities.RcsImsCapabilityFlag int capability,
                    @ImsRegistrationImplBase.ImsRegistrationTech int tech,
                    boolean isProvisioned) {
                mOnRcsFeatureChangedQueue.offer(new Pair<>(capability,
                        new Pair<>(tech, isProvisioned)));
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerProvisioningChangedCallback(getContext().getMainExecutor(),
                    callback);
            provisioningManager.registerFeatureProvisioningChangedCallback(
                    getContext().getMainExecutor(), featureProvisioningCallback);

            TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING);

            assertTrue(provisioningManager.isRcsProvisioningRequiredForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_LTE));
            assertTrue(provisioningManager.isRcsProvisioningRequiredForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_IWLAN));
            assertTrue(provisioningManager.isRcsProvisioningRequiredForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_CROSS_SIM));
            assertTrue(provisioningManager.isRcsProvisioningRequiredForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_NR));

            // test get/setRcsProvisioningStatusForCapability for PRESENCE over LTE
            boolean isProvisioned = provisioningManager.getRcsProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_LTE);
            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    IMS_REGI_TECH_LTE, !isProvisioned);
            assertTrue(waitForParam(mOnRcsFeatureChangedQueue,
                    new Pair<>(RCS_CAP_PRESENCE, new Pair<>(IMS_REGI_TECH_LTE, !isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_EAB_PROVISIONING_STATUS, !isProvisioned ? 1 : 0)));

            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    IMS_REGI_TECH_LTE, isProvisioned);
            assertTrue(waitForParam(mOnRcsFeatureChangedQueue,
                    new Pair<>(RCS_CAP_PRESENCE, new Pair<>(IMS_REGI_TECH_LTE, isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_EAB_PROVISIONING_STATUS, isProvisioned ? 1 : 0)));

            // test get/setRcsProvisioningStatusForCapability for PRESENCE over IWLAN
            isProvisioned = provisioningManager.getRcsProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_IWLAN);
            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    IMS_REGI_TECH_IWLAN, !isProvisioned);
            assertTrue(waitForParam(mOnRcsFeatureChangedQueue,
                    new Pair<>(RCS_CAP_PRESENCE, new Pair<>(IMS_REGI_TECH_IWLAN, !isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_EAB_PROVISIONING_STATUS, !isProvisioned ? 1 : 0)));

            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    IMS_REGI_TECH_IWLAN, isProvisioned);
            assertTrue(waitForParam(mOnRcsFeatureChangedQueue,
                    new Pair<>(RCS_CAP_PRESENCE, new Pair<>(IMS_REGI_TECH_IWLAN, isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_EAB_PROVISIONING_STATUS, isProvisioned ? 1 : 0)));

            // test get/setRcsProvisioningStatusForCapability for PRESENCE over CROSS SIM
            isProvisioned = provisioningManager.getRcsProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_CROSS_SIM);
            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    IMS_REGI_TECH_CROSS_SIM, !isProvisioned);
            assertTrue(waitForParam(mOnRcsFeatureChangedQueue,
                    new Pair<>(RCS_CAP_PRESENCE,
                            new Pair<>(IMS_REGI_TECH_CROSS_SIM, !isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_EAB_PROVISIONING_STATUS, !isProvisioned ? 1 : 0)));

            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    IMS_REGI_TECH_CROSS_SIM, isProvisioned);
            assertTrue(waitForParam(mOnRcsFeatureChangedQueue,
                    new Pair<>(RCS_CAP_PRESENCE,
                            new Pair<>(IMS_REGI_TECH_CROSS_SIM, isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_EAB_PROVISIONING_STATUS, isProvisioned ? 1 : 0)));

            // test get/setRcsProvisioningStatusForCapability for PRESENCE over NR
            isProvisioned = provisioningManager.getRcsProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_NR);
            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    IMS_REGI_TECH_NR, !isProvisioned);
            assertTrue(waitForParam(mOnRcsFeatureChangedQueue,
                    new Pair<>(RCS_CAP_PRESENCE, new Pair<>(IMS_REGI_TECH_NR, !isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_EAB_PROVISIONING_STATUS, !isProvisioned ? 1 : 0)));

            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    IMS_REGI_TECH_NR, isProvisioned);
            assertTrue(waitForParam(mOnRcsFeatureChangedQueue,
                    new Pair<>(RCS_CAP_PRESENCE, new Pair<>(IMS_REGI_TECH_NR, isProvisioned))));
            assertTrue(waitForParam(mIntQueue,
                    new Pair<>(KEY_EAB_PROVISIONING_STATUS, isProvisioned ? 1 : 0)));

            // TODO : work for OPTIONS case

            automan.adoptShellPermissionIdentity();
            provisioningManager.unregisterProvisioningChangedCallback(callback);
            provisioningManager.unregisterFeatureProvisioningChangedCallback(
                    featureProvisioningCallback);
        } finally {
            TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING);

            automan.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testProvisioningManagerWhenRcsProvisionIsNotRequired() throws Exception {
        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            return;
        }

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                true);

        PersistableBundle innerBundle = new PersistableBundle();
        bundle.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_RCS_REQUIRES_PROVISIONING_BUNDLE,
                innerBundle);
        bundle.putBoolean(
                CarrierConfigManager.KEY_CARRIER_RCS_PROVISIONING_REQUIRED_BOOL, false);
        bundle.putBoolean(
                CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL, false);
        overrideCarrierConfig(bundle);

        triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature();

        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);
        ProvisioningManager.Callback callback = new ProvisioningManager.Callback() {};
        ProvisioningManager.FeatureProvisioningCallback featureProvisioningCallback =
                new ProvisioningManager.FeatureProvisioningCallback() {
            @Override
            public void onFeatureProvisioningChanged(
                    @MmTelFeature.MmTelCapabilities.MmTelCapability int capability,
                    @ImsRegistrationImplBase.ImsRegistrationTech int tech,
                    boolean isProvisioned) {
            }

            @Override
            public void onRcsFeatureProvisioningChanged(
                    @RcsFeature.RcsImsCapabilities.RcsImsCapabilityFlag int capability,
                    @ImsRegistrationImplBase.ImsRegistrationTech int tech,
                    boolean isProvisioned){
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerProvisioningChangedCallback(getContext().getMainExecutor(),
                    callback);
            provisioningManager.registerFeatureProvisioningChangedCallback(
                    getContext().getMainExecutor(), featureProvisioningCallback);

            TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING);

            assertTrue(!provisioningManager.isRcsProvisioningRequiredForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_LTE));
            assertTrue(!provisioningManager.isRcsProvisioningRequiredForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_IWLAN));
            assertTrue(!provisioningManager.isRcsProvisioningRequiredForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_CROSS_SIM));
            assertTrue(!provisioningManager.isRcsProvisioningRequiredForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_NR));

            // However, getProvisioningStatusForCapability() should return true because it does not
            // require provision
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_LTE));
            // put opposite value to check if the key is changed or not
            provisioningManager.setProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_LTE, false);
            // key value should not be changed whatever value is set
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_LTE));

            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_IWLAN));
            provisioningManager.setProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_IWLAN, false);
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_IWLAN));

            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_CROSS_SIM));
            provisioningManager.setProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_CROSS_SIM, false);
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_CROSS_SIM));

            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_NR));
            provisioningManager.setProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_NR, false);
            assertTrue(provisioningManager.getProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE, IMS_REGI_TECH_NR));

            // TODO : work for OPTIONS case

            automan.adoptShellPermissionIdentity();
            provisioningManager.unregisterProvisioningChangedCallback(callback);
            provisioningManager.unregisterFeatureProvisioningChangedCallback(
                    featureProvisioningCallback);
        } finally {
            TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    SUPPORT_PROVISION_STATUS_FOR_CAPABILITY_STRING);

            automan.dropShellPermissionIdentity();
        }
    }

    @Ignore("The ProvisioningManager constants were moved back to @hide for now, don't want to "
            + "completely remove test.")
    @Test
    public void testProvisioningManagerConstants() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        triggerFrameworkConnectToCarrierImsService();

        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        // This is a little bit gross looking, but on P devices, I can not define classes that
        // extend ProvisioningManager.Callback (because it doesn't exist), so this has to
        // happen as an anon class here.
        LinkedBlockingQueue<Pair<Integer, Integer>> mIntQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Pair<Integer, String>> mStringQueue = new LinkedBlockingQueue<>();
        ProvisioningManager.Callback callback = new ProvisioningManager.Callback() {
            @Override
            public void onProvisioningIntChanged(int item, int value) {
                mIntQueue.offer(new Pair<>(item, value));
            }

            @Override
            public void onProvisioningStringChanged(int item, String value) {
                mStringQueue.offer(new Pair<>(item, value));
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerProvisioningChangedCallback(getContext().getMainExecutor(),
                    callback);

            verifyStringKey(provisioningManager, mStringQueue,
                    ProvisioningManager.KEY_AMR_CODEC_MODE_SET_VALUES, "1,2");
            verifyStringKey(provisioningManager, mStringQueue,
                    ProvisioningManager.KEY_AMR_WB_CODEC_MODE_SET_VALUES, "1,2");
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_SESSION_TIMER_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_MINIMUM_SIP_SESSION_EXPIRATION_TIMER_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_INVITE_CANCELLATION_TIMER_MS, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_TRANSITION_TO_LTE_DELAY_MS, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_ENABLE_SILENT_REDIAL, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_T1_TIMER_VALUE_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_T2_TIMER_VALUE_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_TF_TIMER_VALUE_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_VT_PROVISIONING_STATUS, 0);
            verifyStringKey(provisioningManager, mStringQueue,
                    ProvisioningManager.KEY_REGISTRATION_DOMAIN_NAME, "test.com");
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SMS_FORMAT, ProvisioningManager.SMS_FORMAT_3GPP);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SMS_FORMAT, ProvisioningManager.SMS_FORMAT_3GPP2);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SMS_OVER_IP_ENABLED, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_PUBLISH_TIMER_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_PUBLISH_OFFLINE_AVAILABILITY_TIMER_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_CAPABILITY_DISCOVERY_ENABLED, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_AVAILABILITY_CACHE_EXPIRATION_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_CAPABILITIES_POLL_INTERVAL_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_PUBLISH_SOURCE_THROTTLE_MS, 1000);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_MAX_NUM_ENTRIES_IN_RCL, 50);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RCS_CAPABILITY_POLL_LIST_SUB_EXP_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_USE_GZIP_FOR_LIST_SUBSCRIPTION, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_EAB_PROVISIONING_STATUS, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_VOICE_OVER_WIFI_ROAMING_ENABLED_OVERRIDE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_MOBILE_DATA_ENABLED, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_VOLTE_USER_OPT_IN_STATUS, 0);
            verifyStringKey(provisioningManager, mStringQueue,
                    ProvisioningManager.KEY_LOCAL_BREAKOUT_PCSCF_ADDRESS, "local.fun.com");
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_KEEP_ALIVE_ENABLED, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_REGISTRATION_RETRY_BASE_TIME_SEC, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_REGISTRATION_RETRY_MAX_TIME_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RTP_SPEECH_START_PORT, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RTP_SPEECH_END_PORT, 600);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_INVITE_REQUEST_TRANSMIT_INTERVAL_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_INVITE_ACK_WAIT_TIME_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_INVITE_RESPONSE_RETRANSMIT_WAIT_TIME_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_NON_INVITE_TRANSACTION_TIMEOUT_TIMER_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_INVITE_RESPONSE_RETRANSMIT_INTERVAL_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_ACK_RECEIPT_WAIT_TIME_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_ACK_RETRANSMIT_WAIT_TIME_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_NON_INVITE_REQUEST_RETRANSMISSION_WAIT_TIME_MS, 500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SIP_NON_INVITE_RESPONSE_RETRANSMISSION_WAIT_TIME_MS,
                    500);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_AMR_WB_OCTET_ALIGNED_PAYLOAD_TYPE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_AMR_WB_BANDWIDTH_EFFICIENT_PAYLOAD_TYPE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_AMR_OCTET_ALIGNED_PAYLOAD_TYPE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_AMR_BANDWIDTH_EFFICIENT_PAYLOAD_TYPE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_DTMF_WB_PAYLOAD_TYPE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_DTMF_NB_PAYLOAD_TYPE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_AMR_DEFAULT_ENCODING_MODE, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_SMS_PUBLIC_SERVICE_IDENTITY, 0);
            verifyStringKey(provisioningManager, mStringQueue,
                    ProvisioningManager.KEY_SMS_PUBLIC_SERVICE_IDENTITY, "local.fun.com");
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_VIDEO_QUALITY, ProvisioningManager.VIDEO_QUALITY_HIGH);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_VIDEO_QUALITY, ProvisioningManager.VIDEO_QUALITY_LOW);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_LTE_THRESHOLD_1, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_LTE_THRESHOLD_2, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_LTE_THRESHOLD_3, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_1X_THRESHOLD, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_WIFI_THRESHOLD_A, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_WIFI_THRESHOLD_B, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_LTE_EPDG_TIMER_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_WIFI_EPDG_TIMER_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_1X_EPDG_TIMER_SEC, 5);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_MULTIENDPOINT_ENABLED, 0);
            verifyIntKey(provisioningManager, mIntQueue,
                    ProvisioningManager.KEY_RTT_ENABLED, 0);
            verifyStringKey(provisioningManager, mStringQueue,
                    ProvisioningManager.KEY_VOICE_OVER_WIFI_ENTITLEMENT_ID, "carrier_spec");

            automan.adoptShellPermissionIdentity();
            provisioningManager.unregisterProvisioningChangedCallback(callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testProvisioningManagerProvisioningCaps() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        triggerFrameworkConnectToCarrierImsService();

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, true);

        PersistableBundle innerBundle = new PersistableBundle();
        innerBundle.putIntArray(
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_UT_INT_ARRAY,
                new int[]{IMS_REGI_TECH_LTE}); // UT/LTE
        bundle.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE,
                innerBundle);

        overrideCarrierConfig(bundle);

        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            boolean provisioningStatus = provisioningManager.getProvisioningStatusForCapability(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT,
                    IMS_REGI_TECH_LTE);
            provisioningManager.setProvisioningStatusForCapability(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT,
                    IMS_REGI_TECH_LTE, !provisioningStatus);
            // Make sure the change in provisioning status is correctly returned.
            assertEquals(!provisioningStatus,
                    provisioningManager.getProvisioningStatusForCapability(
                            MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT,
                            IMS_REGI_TECH_LTE));
            // TODO: Enhance test to make sure the provisioning change is also sent to the
            // ImsService

            // set back to current status
            provisioningManager.setProvisioningStatusForCapability(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT,
                    IMS_REGI_TECH_LTE, provisioningStatus);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testProvisioningManagerRcsProvisioningCaps() throws Exception {
        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            return;
        }

        triggerFrameworkConnectToCarrierImsService();

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_RCS_BULK_CAPABILITY_EXCHANGE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_RCS_PROVISIONING_REQUIRED_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                true);
        PersistableBundle innerBundle = new PersistableBundle();
        innerBundle.putIntArray(
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY,
                new int[]{IMS_REGI_TECH_LTE, IMS_REGI_TECH_IWLAN, IMS_REGI_TECH_CROSS_SIM,
                        IMS_REGI_TECH_NR}
        );
        bundle.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_RCS_REQUIRES_PROVISIONING_BUNDLE,
                innerBundle);
        overrideCarrierConfig(bundle);


        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            boolean provisioningStatus = provisioningManager.getRcsProvisioningStatusForCapability(
                    RCS_CAP_PRESENCE);
            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    !provisioningStatus);
            // Make sure the change in provisioning status is correctly returned.
            assertEquals(!provisioningStatus,
                    provisioningManager.getRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE));
            // TODO: Enhance test to make sure the provisioning change is also sent to the
            //  ImsService

            // set back to current status
            provisioningManager.setRcsProvisioningStatusForCapability(RCS_CAP_PRESENCE,
                    provisioningStatus);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testProvisioningManagerRcsProvisioningChangedCallback() throws Exception {
        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            return;
        }

        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();

        final int errorCode = 403;
        final String errorString = "Forbidden";
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        LinkedBlockingQueue<Integer> actionQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<RcsProvisioningCallbackParams> paramsQueue =
                new LinkedBlockingQueue<>();
        ProvisioningManager.RcsProvisioningCallback cb =
                buildRcsProvisioningCallback(actionQueue, paramsQueue);
        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);
        ImsConfigImplBase config = sServiceConnector.getCarrierService().getConfig();

        //notify rcs configuration received, wait rcs gets ready and receives notification
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.notifyRcsAutoConfigurationReceived(
                    TEST_RCS_CONFIG_DEFAULT.getBytes(), false);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int res = waitForIntResult(TestAcsClient.getInstance().getActionQueue());
        assertEquals(res, TestAcsClient.ACTION_CONFIG_CHANGED);

        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerRcsProvisioningCallback(
                    getContext().getMainExecutor(), cb);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        //callback is expected immediately
        res = waitForIntResult(actionQueue);
        assertEquals(res, RCS_CONFIG_CB_CHANGED);
        RcsProvisioningCallbackParams params = waitForResult(paramsQueue);
        assertNotNull(params);
        assertTrue(Arrays.equals(params.mConfig, TEST_RCS_CONFIG_DEFAULT.getBytes()));

        //verify callback when rcs configuration removed
        config.getIImsConfig().notifyRcsAutoConfigurationRemoved();
        res = waitForIntResult(actionQueue);
        assertEquals(res, RCS_CONFIG_CB_RESET);

        //verify callback when rcs configuration received, compressed
        config.getIImsConfig().notifyRcsAutoConfigurationReceived(
                ImsUtils.compressGzip(TEST_RCS_CONFIG_DEFAULT.getBytes()), true);

        res = waitForIntResult(actionQueue);
        assertEquals(res, RCS_CONFIG_CB_CHANGED);
        params = waitForResult(paramsQueue);
        assertNotNull(params);
        assertTrue(Arrays.equals(params.mConfig, TEST_RCS_CONFIG_DEFAULT.getBytes()));

        //verify callback when auto config error received
        config.notifyAutoConfigurationErrorReceived(errorCode, errorString);
        res = waitForIntResult(actionQueue);
        assertEquals(res, RCS_CONFIG_CB_ERROR);
        params = waitForResult(paramsQueue);
        assertNotNull(params);
        assertTrue(params.mErrorCode != null && params.mErrorCode == errorCode);
        assertTrue(errorString.equals(params.mErrorString));

        //verify callback when config removed
        config.getIImsConfig().notifyRcsAutoConfigurationRemoved();
        res = waitForIntResult(actionQueue);
        assertEquals(res, RCS_CONFIG_CB_RESET);

        //verify callback when rcs pre-provisioning configuration received
        TestAcsClient.getInstance().notifyPreProvisioning(TEST_RCS_PRE_CONFIG.getBytes());

        res = waitForIntResult(actionQueue);
        assertEquals(res, RCS_CONFIG_CB_PREPROV);
        params = waitForResult(paramsQueue);
        assertNotNull(params);
        assertTrue(Arrays.equals(params.mConfig, TEST_RCS_PRE_CONFIG.getBytes()));

        //unregister callback and verify not to receive callback any more
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.unregisterRcsProvisioningCallback(cb);
        } finally {
            automan.dropShellPermissionIdentity();
        }
        res = waitForIntResult(actionQueue);
        assertEquals(res, RCS_CONFIG_CB_DELETE);

        config.notifyAutoConfigurationErrorReceived(errorCode, errorString);
        res = waitForIntResult(actionQueue, 500);
        assertEquals(res, Integer.MAX_VALUE);
    }

    @Test
    public void testProvisioningManagerNotifyRcsAutoConfigurationReceived() throws Exception {
        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            return;
        }

        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        LinkedBlockingQueue<Integer> clientQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<RcsProvisioningCallbackParams> paramsQueue =
                new LinkedBlockingQueue<>();
        ProvisioningManager.RcsProvisioningCallback cb =
                buildRcsProvisioningCallback(clientQueue, paramsQueue);
        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);
        String configStr = TEST_RCS_CONFIG_DEFAULT;

        //notify rcs configuration received, wait rcs gets ready and receives notification
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.notifyRcsAutoConfigurationReceived(
                    configStr.getBytes(), false);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int res = waitForIntResult(TestAcsClient.getInstance().getActionQueue());
        assertEquals(res, TestAcsClient.ACTION_CONFIG_CHANGED);

        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerRcsProvisioningCallback(
                    getContext().getMainExecutor(), cb);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        res = waitForIntResult(clientQueue);
        assertEquals(res, RCS_CONFIG_CB_CHANGED);
        RcsProvisioningCallbackParams params = waitForResult(paramsQueue);
        assertNotNull(params);
        assertTrue(Arrays.equals(params.mConfig, configStr.getBytes()));
        assertTrue(Arrays.equals(
                configStr.getBytes(), TestAcsClient.getInstance().getConfig()));

        configStr = TEST_RCS_CONFIG_SINGLE_REGISTRATION_DISABLED;
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.notifyRcsAutoConfigurationReceived(
                    ImsUtils.compressGzip(configStr.getBytes()), true);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        res = waitForIntResult(clientQueue);
        assertEquals(res, RCS_CONFIG_CB_CHANGED);
        params = waitForResult(paramsQueue);
        assertNotNull(params);
        assertTrue(Arrays.equals(params.mConfig, configStr.getBytes()));

        res = waitForIntResult(TestAcsClient.getInstance().getActionQueue());
        assertEquals(res, TestAcsClient.ACTION_CONFIG_CHANGED);
        assertTrue(Arrays.equals(
                configStr.getBytes(), TestAcsClient.getInstance().getConfig()));
    }

    @Test
    public void testProvisioningManagerTriggerRcsReconfiguration() throws Exception {
        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            return;
        }

        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        LinkedBlockingQueue<Integer> clientQueue = new LinkedBlockingQueue<>();
        ProvisioningManager.RcsProvisioningCallback cb =
                buildRcsProvisioningCallback(clientQueue, null);

        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        //notify rcs configuration received, wait rcs gets ready and receives notification
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.notifyRcsAutoConfigurationReceived(
                    TEST_RCS_CONFIG_DEFAULT.getBytes(), false);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int res = waitForIntResult(TestAcsClient.getInstance().getActionQueue());
        assertEquals(res, TestAcsClient.ACTION_CONFIG_CHANGED);

        //set default rcs config
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.registerRcsProvisioningCallback(
                    getContext().getMainExecutor(), cb);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        res = waitForIntResult(clientQueue);
        assertEquals(res, RCS_CONFIG_CB_CHANGED);

        //test triggerRcsReconfiguration
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.triggerRcsReconfiguration();
        } finally {
            automan.dropShellPermissionIdentity();
        }

        res = waitForIntResult(clientQueue);
        assertEquals(res, RCS_CONFIG_CB_RESET);

        res = waitForIntResult(TestAcsClient.getInstance().getActionQueue());
        assertEquals(res, TestAcsClient.ACTION_CONFIG_REMOVED);

        res = waitForIntResult(TestAcsClient.getInstance().getActionQueue());
        assertEquals(res, TestAcsClient.ACTION_TRIGGER_AUTO_CONFIG);
    }

    @Test
    public void testProvisioningManagerSetRcsClientConfiguration() throws Exception {
        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            return;
        }
        RcsClientConfiguration rcc = new RcsClientConfiguration(
                "1.0", "UP_1.0", "Android", "RCSAndrd-1.0");
        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);

        //notify rcs configuration received, wait rcs gets ready and receives notification
        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.notifyRcsAutoConfigurationReceived(
                    TEST_RCS_CONFIG_DEFAULT.getBytes(), false);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int res = waitForIntResult(TestAcsClient.getInstance().getActionQueue());
        assertEquals(res, TestAcsClient.ACTION_CONFIG_CHANGED);

        try {
            automan.adoptShellPermissionIdentity();
            provisioningManager.setRcsClientConfiguration(rcc);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        res = waitForIntResult(TestAcsClient.getInstance().getActionQueue());
        assertEquals(res, TestAcsClient.ACTION_SET_RCS_CLIENT_CONFIG);
        assertEquals(rcc, TestAcsClient.getInstance().getRcc());
    }

    @Test
    public void testProvisioningManagerRcsVolteSingleRegistrationCapable() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        boolean isSingleRegistrationEnabledOnDevice =
                sServiceConnector.getDeviceSingleRegistrationEnabled();
        boolean isSingleRegistrationEnabledByCarrier =
                sServiceConnector.getCarrierSingleRegistrationEnabled();

        ProvisioningManager provisioningManager =
                ProvisioningManager.createForSubscriptionId(sTestSub);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                !isSingleRegistrationEnabledByCarrier);
        sSrcReceiver.clearQueue();
        overrideCarrierConfig(bundle);
        sSrcReceiver.waitForChanged();
        int capability = sSrcReceiver.getCapability();

        assertEquals(!isSingleRegistrationEnabledByCarrier,
                (ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE & capability) == 0);
        try {
            automan.adoptShellPermissionIdentity();
            assertEquals(provisioningManager.isRcsVolteSingleRegistrationCapable(),
                    isSingleRegistrationEnabledOnDevice && !isSingleRegistrationEnabledByCarrier);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                isSingleRegistrationEnabledByCarrier);
        sSrcReceiver.clearQueue();
        overrideCarrierConfig(bundle);
        sSrcReceiver.waitForChanged();
        capability = sSrcReceiver.getCapability();

        assertEquals(isSingleRegistrationEnabledByCarrier,
                (ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE & capability) == 0);
        try {
            automan.adoptShellPermissionIdentity();
            assertEquals(provisioningManager.isRcsVolteSingleRegistrationCapable(),
                    isSingleRegistrationEnabledOnDevice && isSingleRegistrationEnabledByCarrier);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        sSrcReceiver.clearQueue();
        sServiceConnector.setDeviceSingleRegistrationEnabled(!isSingleRegistrationEnabledOnDevice);
        sSrcReceiver.waitForChanged();
        capability = sSrcReceiver.getCapability();

        assertEquals(!isSingleRegistrationEnabledOnDevice,
                (ProvisioningManager.STATUS_DEVICE_NOT_CAPABLE & capability) == 0);
        try {
            automan.adoptShellPermissionIdentity();
            assertEquals(provisioningManager.isRcsVolteSingleRegistrationCapable(),
                    !isSingleRegistrationEnabledOnDevice && isSingleRegistrationEnabledByCarrier);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        sSrcReceiver.clearQueue();
        sServiceConnector.setDeviceSingleRegistrationEnabled(isSingleRegistrationEnabledOnDevice);
        sSrcReceiver.waitForChanged();
        capability = sSrcReceiver.getCapability();

        assertEquals(isSingleRegistrationEnabledOnDevice,
                (ProvisioningManager.STATUS_DEVICE_NOT_CAPABLE & capability) == 0);
        try {
            automan.adoptShellPermissionIdentity();
            assertEquals(provisioningManager.isRcsVolteSingleRegistrationCapable(),
                    isSingleRegistrationEnabledOnDevice && isSingleRegistrationEnabledByCarrier);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        sServiceConnector.setDeviceSingleRegistrationEnabled(null);
        overrideCarrierConfig(null);
        sSrcReceiver.waitForChanged();
        capability = sSrcReceiver.getCapability();

        assertEquals(isSingleRegistrationEnabledOnDevice,
                (ProvisioningManager.STATUS_DEVICE_NOT_CAPABLE & capability) == 0);
        assertEquals(isSingleRegistrationEnabledByCarrier,
                (ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE & capability) == 0);
    }

    /**
     * Verifies that the RTP header extensions are set as expected when D2D communication is
     * available on the device and for the current carrier.
     * @throws Exception
     */
    @Test
    public void testSetRtpHeaderExtensions() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        sServiceConnector.setDeviceToDeviceCommunicationEnabled(true);
        try {
            PersistableBundle bundle = new PersistableBundle();
            bundle.putBoolean(
                    CarrierConfigManager.KEY_SUPPORTS_DEVICE_TO_DEVICE_COMMUNICATION_USING_RTP_BOOL,
                    true);
            bundle.putBoolean(CarrierConfigManager
                    .KEY_SUPPORTS_SDP_NEGOTIATION_OF_D2D_RTP_HEADER_EXTENSIONS_BOOL, true);
            overrideCarrierConfig(bundle);

            triggerFrameworkConnectToCarrierImsService();

            sServiceConnector.getCarrierService().getMmTelFeature()
                    .getOfferedRtpHeaderExtensionLatch().await(5000, TimeUnit.MILLISECONDS);
            Set<RtpHeaderExtensionType> extensions = sServiceConnector.getCarrierService()
                    .getMmTelFeature().getOfferedRtpHeaderExtensionTypes();

            assertTrue(extensions.size() > 0);
        } finally {
            sServiceConnector.setDeviceToDeviceCommunicationEnabled(false);
            overrideCarrierConfig(null);
        }
    }

    @Test
    public void testSetMediaThreshold() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        sServiceConnector.setDeviceToDeviceCommunicationEnabled(true);
        try {
            PersistableBundle bundle = new PersistableBundle();
            bundle.putInt(
                    CarrierConfigManager.ImsVoice.KEY_VOICE_RTP_PACKET_LOSS_RATE_THRESHOLD_INT,
                    TEST_PACKET_LOSS_RATE_THRESHOLD);
            bundle.putLong(
                    CarrierConfigManager
                            .ImsVoice
                            .KEY_VOICE_RTP_INACTIVITY_TIME_THRESHOLD_MILLIS_LONG,
                    TEST_INACTIVITY_MILLIS);
            bundle.putInt(
                    CarrierConfigManager
                            .ImsVoice.KEY_VOICE_RTP_JITTER_THRESHOLD_MILLIS_INT,
                    TEST_JITTER_THRESHOLD);
            overrideCarrierConfig(bundle);

            triggerFrameworkConnectToCarrierImsService();

            sServiceConnector.getCarrierService().getMmTelFeature()
                    .getSetMediaThresholdLatch(TEST_PACKET_LOSS_RATE_THRESHOLD,
                            TEST_JITTER_THRESHOLD, TEST_INACTIVITY_MILLIS)
                    .await(10000, TimeUnit.MILLISECONDS);
            MediaThreshold threshold =
                    sServiceConnector.getCarrierService().getMmTelFeature().getSetMediaThreshold();

            assertNotNull(threshold);
            assertArrayEquals(new int[]{TEST_PACKET_LOSS_RATE_THRESHOLD},
                    threshold.getThresholdsRtpPacketLossRate());
            assertArrayEquals(new int[]{TEST_JITTER_THRESHOLD},
                    threshold.getThresholdsRtpJitterMillis());
            assertArrayEquals(new long[]{TEST_INACTIVITY_MILLIS},
                    threshold.getThresholdsRtpInactivityTimeMillis());
        } finally {
            sServiceConnector.setDeviceToDeviceCommunicationEnabled(false);
            overrideCarrierConfig(null);
        }
    }

    @Test
    public void testImsMmTelManagerImsStateCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        LinkedBlockingQueue<Integer> stateQueue = new LinkedBlockingQueue<>();
        ImsStateCallback callback = buildImsStateCallback(stateQueue);

        ImsMmTelManager mmTelManager = null;
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsStateCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int reason = waitForIntResult(stateQueue);
        assertTrue(reason == ImsStateCallback.REASON_UNKNOWN_TEMPORARY_ERROR
                || reason == ImsStateCallback.REASON_UNKNOWN_PERMANENT_ERROR
                || reason == ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED
                || reason == ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED
                || reason == ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE
                || reason == ImsStateCallback.REASON_IMS_SERVICE_NOT_READY);

        mmTelManager.unregisterImsStateCallback(callback);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService();

        stateQueue = new LinkedBlockingQueue<>();
        callback = buildImsStateCallback(stateQueue);

        try {
            automan.adoptShellPermissionIdentity();
            mmTelManager.registerImsStateCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // expects FEATURE_MMTEL STATE_READY
        assertEquals(FEATURE_STATE_READY, waitForIntResult(stateQueue));

        if (sServiceConnector != null) {
            sServiceConnector.getCarrierService().getMmTelFeature().setFeatureState(
                    ImsFeature.STATE_INITIALIZING);
        }

        // expects NOT_READY
        assertEquals(ImsStateCallback.REASON_IMS_SERVICE_NOT_READY,
                waitForIntResult(stateQueue));

        // Unbind the GTS ImsService
        if (sServiceConnector != null) {
            sServiceConnector.disconnectCarrierImsService();
        }

        // expects DISCONNECTED
        assertEquals(ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED,
                waitForIntResult(stateQueue));

        mmTelManager.unregisterImsStateCallback(callback);
    }

    @Test
    public void testImsRcsManagerImsStateCallback() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        LinkedBlockingQueue<Integer> rcsQueue = new LinkedBlockingQueue<>();
        ImsStateCallback rcsCallback = buildImsStateCallback(rcsQueue);

        LinkedBlockingQueue<Integer> sipQueue = new LinkedBlockingQueue<>();
        ImsStateCallback sipCallback = buildImsStateCallback(sipQueue);

        ImsRcsManager imsRcsManager;
        SipDelegateManager imsSipManager;
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            imsRcsManager = imsManager.getImsRcsManager(sTestSub);
            imsRcsManager.registerImsStateCallback(getContext().getMainExecutor(), rcsCallback);
            imsSipManager = imsManager.getSipDelegateManager(sTestSub);
            imsSipManager.registerImsStateCallback(getContext().getMainExecutor(), sipCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int reason = waitForIntResult(rcsQueue);
        assertTrue(reason == ImsStateCallback.REASON_UNKNOWN_TEMPORARY_ERROR
                || reason == ImsStateCallback.REASON_UNKNOWN_PERMANENT_ERROR
                || reason == ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED
                || reason == ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED
                || reason == ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE
                || reason == ImsStateCallback.REASON_IMS_SERVICE_NOT_READY);

        reason = waitForIntResult(sipQueue);
        assertTrue(reason == ImsStateCallback.REASON_UNKNOWN_TEMPORARY_ERROR
                || reason == ImsStateCallback.REASON_UNKNOWN_PERMANENT_ERROR
                || reason == ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED
                || reason == ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED
                || reason == ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE
                || reason == ImsStateCallback.REASON_IMS_SERVICE_NOT_READY);

        imsRcsManager.unregisterImsStateCallback(rcsCallback);
        imsSipManager.unregisterImsStateCallback(sipCallback);

        // Override the carrier config
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, false);
        bundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        overrideCarrierConfig(bundle);

        // Connect to device ImsService with RcsFeature
        triggerFrameworkConnectToLocalImsServiceBindRcsFeature();

        rcsQueue = new LinkedBlockingQueue<>();
        rcsCallback = buildImsStateCallback(rcsQueue);

        sipQueue = new LinkedBlockingQueue<>();
        sipCallback = buildImsStateCallback(sipQueue);

        try {
            automan.adoptShellPermissionIdentity();
            imsRcsManager.registerImsStateCallback(getContext().getMainExecutor(), rcsCallback);
            imsSipManager.registerImsStateCallback(getContext().getMainExecutor(), sipCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // expects FEATURE_RCS, NO_IMS_SERVICE_CONFIGURED
        assertEquals(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED,
                waitForIntResult(rcsQueue));
        assertEquals(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED,
                waitForIntResult(sipQueue));

        // Override the carrier config
        bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        // Wait for the framework to set the capabilities on the ImsService
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_RCS_CAP_SET);

        // expects FEATURE_RCS, STATE_READY
        assertEquals(FEATURE_STATE_READY, waitForIntResult(rcsQueue));
        assertEquals(FEATURE_STATE_READY, waitForIntResult(sipQueue));

        bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, false);
        overrideCarrierConfig(bundle);

        // expects FEATURE_RCS, NO_IMS_SERVICE_CONFIGURED
        assertEquals(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED,
                waitForIntResult(rcsQueue));
        assertEquals(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED,
                waitForIntResult(sipQueue));

        // Override the carrier config
        bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(bundle);

        // expects FEATURE_RCS, STATE_READY
        assertEquals(FEATURE_STATE_READY, waitForIntResult(rcsQueue));
        assertEquals(FEATURE_STATE_READY, waitForIntResult(sipQueue));

        bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        overrideCarrierConfig(bundle);

        // expects FEATURE_RCS, NO_IMS_SERVICE_CONFIGURED
        assertEquals(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED,
                waitForIntResult(rcsQueue));
        assertEquals(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED,
                waitForIntResult(sipQueue));

        // Override the carrier config
        bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        bundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(bundle);

        // expects FEATURE_RCS, STATE_READY
        assertEquals(FEATURE_STATE_READY, waitForIntResult(rcsQueue));
        assertEquals(FEATURE_STATE_READY, waitForIntResult(sipQueue));

        bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        overrideCarrierConfig(bundle);

        // ensure no change in state since having one active feature
        reason = waitForIntResult(rcsQueue, 3000);
        assertEquals(Integer.MAX_VALUE, reason);
        reason = waitForIntResult(sipQueue, 1000);
        assertEquals(Integer.MAX_VALUE, reason);

        bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, false);
        overrideCarrierConfig(bundle);

        // expects FEATURE_RCS, NO_IMS_SERVICE_CONFIGURED
        assertEquals(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED,
                waitForIntResult(rcsQueue));
        assertEquals(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED,
                waitForIntResult(sipQueue));

        // Override the carrier config
        bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        // expects FEATURE_RCS, STATE_READY
        assertEquals(FEATURE_STATE_READY, waitForIntResult(rcsQueue));
        assertEquals(FEATURE_STATE_READY, waitForIntResult(sipQueue));

        if (sServiceConnector != null) {
            sServiceConnector.getCarrierService().getRcsFeature().setFeatureState(
                    ImsFeature.STATE_INITIALIZING);
        }

        // expects NOT_READY
        assertEquals(ImsStateCallback.REASON_IMS_SERVICE_NOT_READY,
                waitForIntResult(rcsQueue));
        assertEquals(ImsStateCallback.REASON_IMS_SERVICE_NOT_READY,
                waitForIntResult(sipQueue));

        // Unbind the GTS ImsService
        if (sServiceConnector != null) {
            sServiceConnector.disconnectCarrierImsService();
        }

        // expects DISCONNECTED
        assertEquals(ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED,
                waitForIntResult(rcsQueue));
        assertEquals(ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED,
                waitForIntResult(sipQueue));

        imsRcsManager.unregisterImsStateCallback(rcsCallback);
        imsSipManager.unregisterImsStateCallback(sipCallback);
    }

    @Test
    public void testUnregisteredCallbackCompatibility() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        triggerFrameworkConnectToCarrierImsService();

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            fail("Cannot find IMS service");
        }

        LinkedBlockingQueue<Integer> stateQueue = new LinkedBlockingQueue<>();
        ImsStateCallback stateCallback = buildImsStateCallback(stateQueue);

        ImsMmTelManager mmTelManager = null;
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsStateCallback(getContext().getMainExecutor(), stateCallback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // expects FEATURE_MMTEL STATE_READY
        assertEquals(FEATURE_STATE_READY, waitForIntResult(stateQueue));

        mmTelManager.unregisterImsStateCallback(stateCallback);

        // Start de-registered
        ImsReasonInfo reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NETWORK_NO_SERVICE,
                ImsReasonInfo.CODE_UNSPECIFIED, "");
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(reasonInfo);

        LinkedBlockingQueue<ImsReasonInfo> mDeregQueue =
                new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
            @Override
            public void onUnregistered(ImsReasonInfo info) {
                mDeregQueue.offer(info);
            }
        };

        try {
            automan.adoptShellPermissionIdentity();
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        ImsReasonInfo receivedInfo = waitForResult(mDeregQueue);
        assertNotNull(receivedInfo);
        assertEquals(reasonInfo, receivedInfo);

        // onDeregistered with extra
        reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                ImsReasonInfo.CODE_UNSPECIFIED, "");
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK, REGISTRATION_TECH_LTE);

        receivedInfo = waitForResult(mDeregQueue);
        assertNotNull(receivedInfo);
        assertEquals(reasonInfo, receivedInfo);

        try {
            automan.adoptShellPermissionIdentity();
            mmTelManager.unregisterImsRegistrationCallback(callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    @Ignore("RegistrationManager.RegistrationCallback#onUnregistered(ImsReasonInfo,int,int)"
            + " is hidden. Internal use only.")
    @Test
    public void testMmTelManagerRegistrationBlock() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        triggerFrameworkConnectToCarrierImsService();

        ImsReasonInfo reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                ImsReasonInfo.CODE_UNSPECIFIED, "");

        // Start de-registered
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, SUGGESTED_ACTION_NONE, REGISTRATION_TECH_NONE);

        LinkedBlockingQueue<Integer> mDeregQueue =
                new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
            @Override
            public void onUnregistered(ImsReasonInfo info, int suggestedAction,
                    int imsRadioTech) {
                mDeregQueue.offer(suggestedAction);
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int suggestedAction = waitForResult(mDeregQueue);
        assertNotEquals(SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK, suggestedAction);
        assertNotEquals(SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT, suggestedAction);

        // fatal error
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK,
                REGISTRATION_TECH_LTE);

        suggestedAction = waitForResult(mDeregQueue);
        assertEquals(SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK, suggestedAction);

        // repeated error
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT,
                REGISTRATION_TECH_LTE);

        suggestedAction = waitForResult(mDeregQueue);
        assertEquals(SUGGESTED_ACTION_TRIGGER_PLMN_BLOCK_WITH_TIMEOUT, suggestedAction);

        // without extra
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(reasonInfo);

        suggestedAction = waitForResult(mDeregQueue);
        assertEquals(SUGGESTED_ACTION_NONE, suggestedAction);
    }

    @Ignore("RegistrationManager.RegistrationCallback#onUnregistered(ImsReasonInfo,int,int)"
            + " is hidden. Internal use only.")
    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ADD_RAT_RELATED_SUGGESTED_ACTION_TO_IMS_REGISTRATION)
    public void testMmTelManagerRegistrationBlockWithRatSuggestedAction() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        triggerFrameworkConnectToCarrierImsService();

        ImsReasonInfo reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                ImsReasonInfo.CODE_UNSPECIFIED, "");

        // Start de-registered
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, SUGGESTED_ACTION_NONE, REGISTRATION_TECH_NONE);

        LinkedBlockingQueue<Integer> mDeregQueue =
                new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onUnregistered(ImsReasonInfo info, int suggestedAction,
                            int imsRadioTech) {
                        mDeregQueue.offer(suggestedAction);
                    }
                };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int suggestedAction = waitForResult(mDeregQueue);
        assertNotEquals(SUGGESTED_ACTION_TRIGGER_RAT_BLOCK, suggestedAction);
        assertNotEquals(SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS, suggestedAction);

        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, SUGGESTED_ACTION_TRIGGER_RAT_BLOCK,
                REGISTRATION_TECH_LTE);

        suggestedAction = waitForResult(mDeregQueue);
        assertEquals(SUGGESTED_ACTION_TRIGGER_RAT_BLOCK, suggestedAction);

        // rat block clear
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS,
                REGISTRATION_TECH_LTE);

        suggestedAction = waitForResult(mDeregQueue);
        assertEquals(SUGGESTED_ACTION_TRIGGER_CLEAR_RAT_BLOCKS, suggestedAction);

        // without extra
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(reasonInfo);

        suggestedAction = waitForResult(mDeregQueue);
        assertEquals(SUGGESTED_ACTION_NONE, suggestedAction);
    }

    @Ignore("RegistrationManager.RegistrationCallback#onUnregistered(ImsReasonInfo,int,int)"
            + " is hidden. Internal use only.")
    @Test
    public void testMmTelManagerRegistrationDeregisteredRadioTech() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        triggerFrameworkConnectToCarrierImsService();

        ImsReasonInfo reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                ImsReasonInfo.CODE_UNSPECIFIED, "");

        // Start de-registered
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, SUGGESTED_ACTION_NONE, REGISTRATION_TECH_LTE);

        LinkedBlockingQueue<Integer> mDeregQueue =
                new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
            @Override
            public void onUnregistered(ImsReasonInfo info, int suggestedAction,
                    int imsRadioTech) {
                mDeregQueue.offer(imsRadioTech);
            }
        };

        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        int imsRadioTech = waitForResult(mDeregQueue);
        assertEquals(REGISTRATION_TECH_LTE, imsRadioTech);

        // without extra
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(reasonInfo);

        imsRadioTech = waitForResult(mDeregQueue);
        assertEquals(REGISTRATION_TECH_NONE, imsRadioTech);
    }

    @Test
    public void testImsPhoneNumberWithImsAssociatedUri() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // bind TestImsService
        triggerFrameworkConnectToCarrierImsService();

        // deregistered to set initial state
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));

        // To check when framework completes its operation
        LinkedBlockingQueue<Integer> mSubscriberQueue = new LinkedBlockingQueue<>(1);
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onSubscriberAssociatedUriChanged(@Nullable Uri[] uris) {
                        mSubscriberQueue.offer(uris.length);
                    }
                };

        // register callback
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // trigger complete registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // trigger notify the associated URI has changed.
        Uri[] associatedUris = new Uri[] {
                Uri.parse("sip:+447539447777@ims.x.com"),
                Uri.parse("tel:+447539446666")
        };
        sServiceConnector.getCarrierService().getImsRegistration()
                .onSubscriberAssociatedUriChanged(associatedUris);

        assertNotEquals(Integer.MAX_VALUE,
                waitForIntResult(mSubscriberQueue, ImsUtils.TEST_TIMEOUT_MS));

        // Wait a second for the onSubscriberAssociatedUriChanged indication to be processed on the
        // main telephony thread. Although telephony extracts and stores phone number, accessing to
        // phone number is through the SubscriptionManager. Before calling the
        // SubscriptionManager#getPhoneNumber, a guard time is required for telephony.
        // currently no better way of knowing that telephony has processed this command.
        Thread.sleep(TEST_OPERATION_TIME_MS);

        try {
            automan.adoptShellPermissionIdentity();
            SubscriptionManager sm = (SubscriptionManager) getContext()
                    .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            String phoneNumber = sm.getPhoneNumber(sTestSub,
                    SubscriptionManager.PHONE_NUMBER_SOURCE_IMS);

            // verify Ims phone number
            assertEquals("+447539447777", phoneNumber);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testImsPhoneNumberWithImsAssociatedUriForNonGlobalNumberFormat() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Change carrier config
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.Ims.KEY_ALLOW_NON_GLOBAL_PHONE_NUMBER_FORMAT_BOOL, true);
        overrideCarrierConfig(bundle);

        // bind TestImsService
        triggerFrameworkConnectToCarrierImsService();

        // deregistered to set initial state
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));

        // To check when framework completes its operation
        LinkedBlockingQueue<Integer> mSubscriberQueue = new LinkedBlockingQueue<>(1);
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onSubscriberAssociatedUriChanged(@Nullable Uri[] uris) {
                        mSubscriberQueue.offer(uris.length);
                    }
                };

        // register callback
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // trigger complete registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // trigger notify the associated URI has changed.
        Uri[] associatedUris = new Uri[] {
                Uri.parse("sip:447539447777@ims.x.com"),
                Uri.parse("tel:447539446666")
        };
        sServiceConnector.getCarrierService().getImsRegistration()
                .onSubscriberAssociatedUriChanged(associatedUris);

        assertNotEquals(Integer.MAX_VALUE,
                waitForIntResult(mSubscriberQueue, ImsUtils.TEST_TIMEOUT_MS));

        // Wait a second for the onSubscriberAssociatedUriChanged indication to be processed on the
        // main telephony thread. Although telephony extracts and stores phone number, accessing to
        // phone number is through the SubscriptionManager. Before calling the
        // SubscriptionManager#getPhoneNumber, a guard time is required for telephony.
        // currently no better way of knowing that telephony has processed this command.
        Thread.sleep(TEST_OPERATION_TIME_MS);

        try {
            automan.adoptShellPermissionIdentity();
            SubscriptionManager sm = (SubscriptionManager) getContext()
                    .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            String phoneNumber = sm.getPhoneNumber(sTestSub,
                    SubscriptionManager.PHONE_NUMBER_SOURCE_IMS);

            // verify Ims phone number
            assertEquals("447539447777", phoneNumber);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        overrideCarrierConfig(null);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_CLEAR_CACHED_IMS_PHONE_NUMBER_WHEN_DEVICE_LOST_IMS_REGISTRATION)
    public void testClearImsPhoneNumberWithImsAssociatedUri() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Bind TestImsService
        triggerFrameworkConnectToCarrierImsService();

        ImsReasonInfo reasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                ImsReasonInfo.CODE_UNSPECIFIED, "");

        // IMS is deregistered to set initial state
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, RegistrationManager.SUGGESTED_ACTION_NONE, REGISTRATION_TECH_NONE);

        // To check when framework completes its operation
        LinkedBlockingQueue<Integer> mSubscriberQueue = new LinkedBlockingQueue<>(1);
        LinkedBlockingQueue<Integer> mUnregiQueue = new LinkedBlockingQueue<>(1);
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onUnregistered(ImsReasonInfo info, int suggestedAction,
                            int imsRadioTech) {
                        if (imsRadioTech != REGISTRATION_TECH_NONE) {
                            mUnregiQueue.offer(imsRadioTech);
                        }
                    }

                    @Override
                    public void onSubscriberAssociatedUriChanged(@Nullable Uri[] uris) {
                        mSubscriberQueue.offer(uris.length);
                    }
                };

        // Register callback
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            android.telephony.ims.ImsManager imsManager = getContext().getSystemService(
                    android.telephony.ims.ImsManager.class);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
            mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(), callback);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Trigger IMS registration
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(
                IMS_REGI_TECH_LTE);

        // Trigger the associated URI has changed.
        Uri[] associatedUris = new Uri[] {
                Uri.parse("sip:+447539447777@ims.x.com"),
                Uri.parse("tel:+447539446666")
        };
        sServiceConnector.getCarrierService().getImsRegistration()
                .onSubscriberAssociatedUriChanged(associatedUris);

        assertNotEquals(Integer.MAX_VALUE,
                waitForIntResult(mSubscriberQueue, ImsUtils.TEST_TIMEOUT_MS));

        // Wait a second for the onSubscriberAssociatedUriChanged indication to be processed on the
        // main telephony thread. Although telephony extracts and stores phone number, accessing to
        // phone number is through the SubscriptionManager. Before calling the
        // SubscriptionManager#getPhoneNumber, a guard time is required for telephony.
        // Currently no better way of knowing that telephony has processed this command.
        Thread.sleep(TEST_OPERATION_TIME_MS);

        SubscriptionManager sm = (SubscriptionManager) getContext()
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        try {
            automan.adoptShellPermissionIdentity();

            String phoneNumber = sm.getPhoneNumber(sTestSub,
                    SubscriptionManager.PHONE_NUMBER_SOURCE_IMS);

            // Verify Ims phone number
            assertEquals("+447539447777", phoneNumber);
        } finally {
            automan.dropShellPermissionIdentity();
        }

        // Trigger IMS de-registration
        sServiceConnector.getCarrierService().getImsRegistration().onDeregistered(
                reasonInfo, RegistrationManager.SUGGESTED_ACTION_NONE, REGISTRATION_TECH_LTE);

        assertNotEquals(Integer.MAX_VALUE,
                waitForIntResult(mUnregiQueue, ImsUtils.TEST_TIMEOUT_MS));

        // Wait a second for the onDeregistered indication to be processed on the
        // main telephony thread. Although telephony clear phone number, accessing to
        // phone number is through the SubscriptionManager. Before calling the
        // SubscriptionManager#getPhoneNumber, a guard time is required for telephony.
        // Currently no better way of knowing that telephony has processed this command.
        Thread.sleep(TEST_OPERATION_TIME_MS);

        try {
            automan.adoptShellPermissionIdentity();

            String phoneNumber = sm.getPhoneNumber(sTestSub,
                    SubscriptionManager.PHONE_NUMBER_SOURCE_IMS);

            // verify Ims phone number
            assertEquals("", phoneNumber);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    private void verifyIntKey(ProvisioningManager pm,
            LinkedBlockingQueue<Pair<Integer, Integer>> intQueue, int key, int value)
            throws Exception {
        pm.setProvisioningIntValue(key, value);
        assertTrue(waitForParam(intQueue, new Pair<>(key, value)));
        assertEquals(value, pm.getProvisioningIntValue(key));
    }

    private void verifyStringKey(ProvisioningManager pm,
            LinkedBlockingQueue<Pair<Integer, String>> strQueue, int key, String value)
            throws Exception {
        pm.setProvisioningStringValue(key, value);
        assertTrue(waitForParam(strQueue, new Pair<>(key, value)));
        assertEquals(value, pm.getProvisioningStringValue(key));
    }

    private void setupImsServiceForSms() throws Exception {
        MmTelFeature.MmTelCapabilities capabilities = new MmTelFeature.MmTelCapabilities(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS);
        // Set up MMTEL
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        // Wait until MMTEL is created and onFeatureReady is called
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        assertTrue(sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_READY));
        int serviceSlot = sServiceConnector.getCarrierService().getMmTelFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated MmTelFeature",
                sTestSlot, serviceSlot);
        // Wait until ImsSmsDispatcher connects and calls onReady.
        assertTrue(sServiceConnector.getCarrierService().getMmTelFeature().getSmsImplementation()
                .waitForOnReadyLatch());
        // Set Registered and SMS capable
        sServiceConnector.getCarrierService().getMmTelFeature().setCapabilities(capabilities);
        sServiceConnector.getCarrierService().getImsService()
                .getRegistrationForSubscription(sTestSlot, sTestSub)
                .onRegistered(IMS_REGI_TECH_IWLAN);
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        // Wait a second for the notifyCapabilitiesStatusChanged indication to be processed on the
        // main telephony thread - currently no better way of knowing that telephony has processed
        // this command. SmsManager#isImsSmsSupported() is @hide and must be updated to use new API.
        Thread.sleep(1000);
    }

    private void triggerFrameworkConnectToLocalImsServiceBindRcsFeature() throws Exception {
        // Connect to the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));
        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue("Did not receive createRcsFeature", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_RCS));
        assertTrue("Did not receive RcsFeature#onReady", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_RCS_READY));
        // Make sure the RcsFeature was created in the test service.
        assertNotNull("Device ImsService created, but TestDeviceImsService#createRcsFeature was not"
                + "called!", sServiceConnector.getCarrierService().getRcsFeature());
        int serviceSlot = sServiceConnector.getCarrierService().getRcsFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated RcsFeature",
                sTestSlot, serviceSlot);
    }

    private void triggerFrameworkConnectToImsServiceBindMmTelAndRcsFeature() throws Exception {
        // Connect to the ImsService with the RCS feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
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

        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue("Did not receive createRcsFeature", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_RCS));
        assertTrue("Did not receive RcsFeature#onReady", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_RCS_READY));
        // Make sure the RcsFeature was created in the test service.
        assertNotNull("Device ImsService created, but TestDeviceImsService#createRcsFeature was not"
                + "called!", sServiceConnector.getCarrierService().getRcsFeature());
        assertTrue("Did not receive RcsFeature#setCapabilityExchangeEventListener",
                sServiceConnector.getCarrierService().waitForLatchCountdown(
                        TestImsService.LATCH_UCE_LISTENER_SET));
        serviceSlot = sServiceConnector.getCarrierService().getRcsFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated RcsFeature",
                sTestSlot, serviceSlot);
    }

    private void triggerFrameworkConnectToCarrierImsService() throws Exception {
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
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

    private ImsStateCallback buildImsStateCallback(final LinkedBlockingQueue<Integer> stateQueue) {
        return new ImsStateCallback() {
            @Override
            public void onUnavailable(int reason) {
                stateQueue.offer(reason);
            }

            @Override
            public void onAvailable() {
                stateQueue.offer(FEATURE_STATE_READY);
            }

            @Override
            public void onError() {
                stateQueue.offer(-1);
            }
        };
    }

    private ProvisioningManager.RcsProvisioningCallback buildRcsProvisioningCallback(
            LinkedBlockingQueue<Integer> actionQueue,
            LinkedBlockingQueue<RcsProvisioningCallbackParams> paramQueue) {
        return new ProvisioningManager.RcsProvisioningCallback() {
            @Override
            public void onConfigurationChanged(byte[] configXml) {
                super.onConfigurationChanged(configXml);
                actionQueue.offer(RCS_CONFIG_CB_CHANGED);
                if (paramQueue != null) {
                    RcsProvisioningCallbackParams params = new RcsProvisioningCallbackParams();
                    params.mConfig = configXml;
                    paramQueue.offer(params);
                }
            }

            @Override
            public void onAutoConfigurationErrorReceived(int code, String str) {
                super.onAutoConfigurationErrorReceived(code, str);
                actionQueue.offer(RCS_CONFIG_CB_ERROR);
                if (paramQueue != null) {
                    RcsProvisioningCallbackParams params = new RcsProvisioningCallbackParams();
                    params.mErrorCode = code;
                    params.mErrorString = str;
                    paramQueue.offer(params);
                }
            }

            @Override
            public void onConfigurationReset() {
                super.onConfigurationReset();
                actionQueue.offer(RCS_CONFIG_CB_RESET);
            }

            @Override
            public void onRemoved() {
                super.onRemoved();
                actionQueue.offer(RCS_CONFIG_CB_DELETE);
            }

            @Override
            public void onPreProvisioningReceived(byte[] configXml) {
                super.onPreProvisioningReceived(configXml);
                actionQueue.offer(RCS_CONFIG_CB_PREPROV);
                if (paramQueue != null) {
                    RcsProvisioningCallbackParams params = new RcsProvisioningCallbackParams();
                    params.mConfig = configXml;
                    paramQueue.offer(params);
                }
            }
        };
    }
    // Waiting for ImsRcsManager to become public before implementing RegistrationManager,
    // Duplicate these methods for now.
    private void verifyRegistrationState(ImsRcsManager regManager, int expectedState)
            throws Exception {
        LinkedBlockingQueue<Integer> mQueue = new LinkedBlockingQueue<>();
        assertTrue(ImsUtils.retryUntilTrue(() -> {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(regManager,
                    (m) -> m.getRegistrationState(getContext().getMainExecutor(), mQueue::offer),
                    "android.permission.READ_PRECISE_PHONE_STATE");
            return waitForIntResult(mQueue) == expectedState;
        }));
    }

    // Waiting for ImsRcsManager to become public before implementing RegistrationManager,
    // Duplicate these methods for now.
    private void verifyRegistrationTransportType(ImsRcsManager regManager,
            int expectedTransportType) throws Exception {
        LinkedBlockingQueue<Integer> mQueue = new LinkedBlockingQueue<>();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(regManager,
                (m) -> m.getRegistrationTransportType(getContext().getMainExecutor(),
                        mQueue::offer),
                "android.permission.READ_PRECISE_PHONE_STATE");
        assertEquals(expectedTransportType, waitForIntResult(mQueue));
    }

    private void verifyRegistrationState(RegistrationManager regManager, int expectedState)
            throws Exception {
        LinkedBlockingQueue<Integer> mQueue = new LinkedBlockingQueue<>();
        assertTrue(ImsUtils.retryUntilTrue(() -> {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(regManager,
                    (m) -> m.getRegistrationState(getContext().getMainExecutor(), mQueue::offer));
            return waitForIntResult(mQueue) == expectedState;
        }));
    }

    private void verifyRegistrationTransportType(RegistrationManager regManager,
            int expectedTransportType) throws Exception {
        LinkedBlockingQueue<Integer> mQueue = new LinkedBlockingQueue<>();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(regManager,
                (m) -> m.getRegistrationTransportType(getContext().getMainExecutor(),
                        mQueue::offer));
        assertEquals(expectedTransportType, waitForIntResult(mQueue));
    }

    private void verifyRegistering(int tech, ArraySet<String> featureTags,
            LinkedBlockingQueue<ImsRegistrationAttributes> attrQueue, int expectedTransport,
            int expectedAttrFlags) throws Exception {
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(tech)
                .setFeatureTags(featureTags).build();
        sServiceConnector.getCarrierService().getImsRegistration().onRegistering(attr);
        ImsRegistrationAttributes attrResult = waitForResult(attrQueue);
        assertNotNull(attrResult);
        assertEquals(tech, attrResult.getRegistrationTechnology());
        assertEquals(expectedTransport, attrResult.getTransportType());
        assertEquals(expectedAttrFlags, attrResult.getAttributeFlags());
        assertEquals(featureTags, attrResult.getFeatureTags());
    }

    private void verifyRegistered(int tech, ArraySet<String> featureTags,
            LinkedBlockingQueue<ImsRegistrationAttributes> attrQueue, int expectedTransport,
            int expectedAttrFlags) throws Exception {
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(tech)
                .setFeatureTags(featureTags).build();
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(attr);
        ImsRegistrationAttributes attrResult = waitForResult(attrQueue);
        assertNotNull(attrResult);
        assertEquals(tech, attrResult.getRegistrationTechnology());
        assertEquals(expectedTransport, attrResult.getTransportType());
        assertEquals(expectedAttrFlags, attrResult.getAttributeFlags());
        assertEquals(featureTags, attrResult.getFeatureTags());
    }
    private void verifyEmergencyRegistering(int tech, ArraySet<String> featureTags,
            LinkedBlockingQueue<ImsRegistrationAttributes> attrQueue, int expectedTransport,
            int expectedAttrFlags) throws Exception {
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(tech)
                    .setFeatureTags(featureTags).setFlagRegistrationTypeEmergency().build();
        sServiceConnector.getCarrierService().getImsRegistration().onRegistering(attr);
        ImsRegistrationAttributes attrResult = waitForResult(attrQueue);
        assertNotNull(attrResult);
        assertEquals(tech, attrResult.getRegistrationTechnology());
        assertEquals(expectedTransport, attrResult.getTransportType());
        assertEquals(expectedAttrFlags, attrResult.getAttributeFlags());
        assertEquals(featureTags, attrResult.getFeatureTags());
    }

    private void verifyEmergencyRegistered(int tech, ArraySet<String> featureTags,
            LinkedBlockingQueue<ImsRegistrationAttributes> attrQueue, int expectedTransport,
            int expectedAttrFlags) throws Exception {
        ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(tech)
                    .setFeatureTags(featureTags).setFlagRegistrationTypeEmergency().build();
        sServiceConnector.getCarrierService().getImsRegistration().onRegistered(attr);
        ImsRegistrationAttributes attrResult = waitForResult(attrQueue);
        assertNotNull(attrResult);
        assertEquals(tech, attrResult.getRegistrationTechnology());
        assertEquals(expectedTransport, attrResult.getTransportType());
        assertEquals(expectedAttrFlags, attrResult.getAttributeFlags());
        assertEquals(featureTags, attrResult.getFeatureTags());
    }

    private <T> boolean waitForParam(LinkedBlockingQueue<T> queue, T waitParam) throws Exception {
        T result;
        while ((result = waitForResult(queue)) != null) {
            if (waitParam.equals(result)) {
                return true;
            }
        }
        return false;
    }

    private <T> T waitForResult(LinkedBlockingQueue<T> queue) throws Exception {
        return queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private int waitForIntResult(LinkedBlockingQueue<Integer> queue) throws Exception {
        Integer result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return result != null ? result : Integer.MAX_VALUE;
    }

    private int waitForIntResult(LinkedBlockingQueue<Integer> queue, int timeout)
            throws Exception {
        Integer result = queue.poll(timeout, TimeUnit.MILLISECONDS);
        return result != null ? result : Integer.MAX_VALUE;
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForChanged();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    // Copied from com.android.internal.util.HexDump
    private static byte[] hexStringToByteArray(String hexString) {
        int length = hexString.length();
        byte[] buffer = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            buffer[i / 2] =
                    (byte) ((toByte(hexString.charAt(i)) << 4) | toByte(hexString.charAt(i + 1)));
        }

        return buffer;
    }

    // Copied from com.android.internal.util.HexDump
    private static int toByte(char c) {
        if (c >= '0' && c <= '9') return (c - '0');
        if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
        if (c >= 'a' && c <= 'f') return (c - 'a' + 10);

        throw new RuntimeException("Invalid hex char '" + c + "'");
    }

    private boolean isExpectedSubId(HashSet<Integer> subIDs) {
        return (subIDs.size() == 1) && subIDs.contains(sTestSub);
    }
}
