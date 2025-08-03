/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities.DUPLEX_MODE_RECEIVE_ONLY;
import static android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities.DUPLEX_MODE_SEND_ONLY;
import static android.telephony.ims.RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN;
import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_FOUND;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.PersistableBundle;
import android.telecom.PhoneAccount;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class EabControllerTest {

    private static final String TAG = "EabControllerTest";
    private static final String COMMAND_BASE = "cmd phone ";
    private static final String COMMAND_GET_EAB_CONTACT = "uce get-eab-capability ";

    private static int sTestSlot = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;
    private static String sTestPhoneNumber;
    private static Uri sTestNumberUri;
    private static boolean sDeviceUceEnabled;

    private static final String TEST_SERVICE_DESCRIPTION = "description_test1";
    private static final int EXPIRATION_TIME_IN_SEC = 1;
    private static final int WAITING_IN_MILLI_SEC = 1000;
    private static final int POLLING_RETRY_TIMES = 3;

    BlockingQueue<Long> mErrorQueue = new LinkedBlockingQueue<>();
    BlockingQueue<Boolean> mCompleteQueue = new LinkedBlockingQueue<>();
    BlockingQueue<RcsContactUceCapability> mCapabilityQueue = new LinkedBlockingQueue<>();
    RcsUceAdapter.CapabilitiesCallback mCallback = new RcsUceAdapter.CapabilitiesCallback() {
        @Override
        public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
            capabilities.forEach(c -> mCapabilityQueue.offer(c));
        }

        @Override
        public void onComplete() {
            mCompleteQueue.offer(true);
        }

        @Override
        public void onError(int errorCode, long retryAfterMilliseconds) {
            mErrorQueue.offer(new Long(errorCode));
            mErrorQueue.offer(retryAfterMilliseconds);
        }
    };

    private static class CarrierConfigReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
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

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForCarrierConfigChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        sTestPhoneNumber = generateRandomPhoneNumber();
        sTestNumberUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, sTestPhoneNumber, null);

        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        sServiceConnector.clearAllActiveImsServices(sTestSlot);
        sDeviceUceEnabled = sServiceConnector.getDeviceUceEnabled();
        sServiceConnector.setDeviceUceEnabled(true);

        sReceiver = new CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);

        overrideCarrierConfig();

        connectTestImsService();
    }

    private static String generateRandomPhoneNumber() {
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Restore all ImsService configurations that existed before the test.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.disconnectDeviceImsService();
            sServiceConnector.disconnectServices();
            sServiceConnector.setDeviceUceEnabled(sDeviceUceEnabled);
        }
        sServiceConnector = null;

        overrideCarrierConfig(null);

        if (sReceiver != null) {
            InstrumentationRegistry.getInstrumentation().getContext().unregisterReceiver(sReceiver);
            sReceiver = null;
        }
    }

    @Before
    public void beforeTest() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        if (!SubscriptionManager.isValidSubscriptionId(sTestSub)) {
            fail("This test requires that there is a SIM in the device!");
        }
    }

    @After
    public void afterTest() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Remove all the test contacts from EAB database
        removeTestContactFromEab();

        mErrorQueue.clear();
        mCompleteQueue.clear();
        mCapabilityQueue.clear();
    }

    @Test
    public void testRequestCapabilities() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ArrayList<Uri> contacts = new ArrayList<>(1);
        contacts.add(sTestNumberUri);
        fakeNetworkResult(getPidfXmlData(
                sTestNumberUri,
                TEST_SERVICE_DESCRIPTION,
                TUPLE_BASIC_STATUS_OPEN,
                false,
                true,
                DUPLEX_MODE_RECEIVE_ONLY,
                DUPLEX_MODE_SEND_ONLY));
        // Request capabilities for saving capability to EAB provider
        requestCapabilities(contacts);
        mErrorQueue.clear();
        mCompleteQueue.clear();
        mCapabilityQueue.clear();

        // Request capabilities again to get the capabilities in EAB provider
        RcsContactUceCapability capability = requestCapabilities(contacts);

        // Verify that the capability in EAB is the same as expected
        verifyCapabilityResult(capability,
                sTestNumberUri,
                TEST_SERVICE_DESCRIPTION,
                TUPLE_BASIC_STATUS_OPEN,
                REQUEST_RESULT_FOUND,
                RcsContactUceCapability.SOURCE_TYPE_CACHED,
                false,
                true,
                DUPLEX_MODE_RECEIVE_ONLY,
                DUPLEX_MODE_SEND_ONLY);

        // Verify the onCompleted is called
        waitForResult(mCompleteQueue);
    }

    @Test
    public void testRequestAvailabilities() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        fakeNetworkResult(getPidfXmlData(
                sTestNumberUri,
                TEST_SERVICE_DESCRIPTION,
                TUPLE_BASIC_STATUS_OPEN,
                false,
                true,
                DUPLEX_MODE_RECEIVE_ONLY,
                DUPLEX_MODE_SEND_ONLY));
        // Request capabilities for saving capability to EAB provider
        requestAvailability(sTestNumberUri);
        mErrorQueue.clear();
        mCompleteQueue.clear();
        mCapabilityQueue.clear();

        // Request capabilities again to get the capabilities in EAB provider
        RcsContactUceCapability capability = requestAvailability(sTestNumberUri);

        // Verify that the capability in EAB is the same as expected
        verifyCapabilityResult(capability,
                sTestNumberUri,
                TEST_SERVICE_DESCRIPTION,
                TUPLE_BASIC_STATUS_OPEN,
                REQUEST_RESULT_FOUND,
                RcsContactUceCapability.SOURCE_TYPE_CACHED,
                false,
                true,
                DUPLEX_MODE_RECEIVE_ONLY,
                DUPLEX_MODE_SEND_ONLY);

        // Verify the onCompleted is called
        waitForResult(mCompleteQueue);
    }

    @Test
    public void testRequestExpiredCapabilities() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Set capabilities expiration time
        setProvisioningIntValue(ProvisioningManager.KEY_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC,
                EXPIRATION_TIME_IN_SEC);

        ArrayList<Uri> contacts = new ArrayList<>(1);
        contacts.add(sTestNumberUri);
        fakeNetworkResult(getPidfXmlData(
                sTestNumberUri,
                TEST_SERVICE_DESCRIPTION,
                TUPLE_BASIC_STATUS_OPEN,
                false,
                true,
                DUPLEX_MODE_RECEIVE_ONLY,
                DUPLEX_MODE_SEND_ONLY));
        // Request capabilities for saving expired capability to EAB provider
        requestCapabilities(contacts);
        mErrorQueue.clear();
        mCompleteQueue.clear();
        mCapabilityQueue.clear();

        waitingEabCapabilityExpire();

        // Request capabilities again
        RcsContactUceCapability capability = requestCapabilities(contacts);

        // Verify that return the availabilities from network instead of the EAB provider
        verifyCapabilityResult(capability,
                sTestNumberUri,
                TEST_SERVICE_DESCRIPTION,
                TUPLE_BASIC_STATUS_OPEN,
                REQUEST_RESULT_FOUND,
                RcsContactUceCapability.SOURCE_TYPE_NETWORK,
                false,
                true,
                DUPLEX_MODE_RECEIVE_ONLY,
                DUPLEX_MODE_SEND_ONLY);

        // Verify the onCompleted is called
        waitForResult(mCompleteQueue);
    }

    @Test
    public void testRequestExpiredAvailabilities() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Set availability expiration time
        setProvisioningIntValue(ProvisioningManager.KEY_RCS_AVAILABILITY_CACHE_EXPIRATION_SEC,
                EXPIRATION_TIME_IN_SEC);

        fakeNetworkResult(getPidfXmlData(
                sTestNumberUri,
                TEST_SERVICE_DESCRIPTION,
                TUPLE_BASIC_STATUS_OPEN,
                true,
                false,
                DUPLEX_MODE_RECEIVE_ONLY,
                DUPLEX_MODE_SEND_ONLY));
        // Request availabilities for saving availabilities to EAB provider
        requestAvailability(sTestNumberUri);
        mErrorQueue.clear();
        mCompleteQueue.clear();
        mCapabilityQueue.clear();

        // Waiting availabilities expire
        waitingEabCapabilityExpire();

        // Request availabilities again
        RcsContactUceCapability capability = requestAvailability(sTestNumberUri);

        // Verify that return the availabilities from network instead of the EAB provider
        verifyCapabilityResult(capability,
                sTestNumberUri,
                TEST_SERVICE_DESCRIPTION,
                TUPLE_BASIC_STATUS_OPEN,
                REQUEST_RESULT_FOUND,
                RcsContactUceCapability.SOURCE_TYPE_NETWORK,
                true,
                false,
                DUPLEX_MODE_RECEIVE_ONLY,
                DUPLEX_MODE_SEND_ONLY);

        // Verify the onCompleted is called
        waitForResult(mCompleteQueue);
    }

    private RcsContactUceCapability requestAvailability(Uri contact) throws Exception {
        // Request capabilities by calling the API requestCapabilities.
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter,
                    adapter -> adapter.requestAvailability(contact, Runnable::run,
                            mCallback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE."
                 + e);
        } catch (ImsException e) {
            fail("requestCapabilities failed " + e);
        }

        // Verify that all the contact's capabilities are received
        return waitForResult(mCapabilityQueue);
    }

    private RcsContactUceCapability requestCapabilities(List<Uri> contact) throws Exception {
        // Request capabilities by calling the API requestCapabilities.
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter,
                    adapter -> adapter.requestCapabilities(contact, Runnable::run, mCallback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE."
                 + e);
        } catch (ImsException e) {
            fail("requestCapabilities failed " + e);
        }

        // Verify that all the contact's capabilities are received
        return waitForResult(mCapabilityQueue);
    }

    private static void overrideCarrierConfig() throws Exception {
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);


        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                true);
        overrideCarrierConfig(bundle);
    }

    private void fakeNetworkResult(String pidfXml) {
        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        ArrayList<String> pidfXmlList = new ArrayList<>(1);
        pidfXmlList.add(pidfXml);

        // Setup the network response is 200 OK and notify capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(pidfXmlList);
            cb.onTerminated("", 0L);
        });
    }

    private String getPidfXmlData(Uri contact,
            String serviceDescription,
            String serviceStatus,
            boolean audioSupported,
            boolean videoSupported,
            String supportedDuplexMode,
            String unSupportedDuplexMode) {
        GregorianCalendar date = new GregorianCalendar();
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
                .format(date.getTime());

        String pidfBuilder = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"
                + "<presence entity=\"" + contact + "\""
                + " xmlns=\"urn:ietf:params:xml:ns:pidf\""
                + " xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\""
                + " xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">"
                + "<tuple id=\"tid0\">"
                // status
                + "<status><basic>" + serviceStatus + "</basic></status>"
                // timestamp
                + "<timestamp>" + timeStamp + "</timestamp>"

                // service description
                + "<op:service-description>"
                + "<op:service-id>service_id_01</op:service-id>"
                + "<op:version>1.0</op:version>"
                + "<op:description>" + serviceDescription + "</op:description>"
                + "</op:service-description>"

                // service capabilities
                + "<caps:servcaps>"
                // audio capabilities
                + "<caps:audio>" + audioSupported + "</caps:audio>"
                // video capabilities
                + "<caps:video>" + videoSupported + "</caps:video>"

                // duplex mode
                + "<caps:duplex>"
                // support duplex mode
                + "<caps:supported>"
                + "<caps:" + supportedDuplexMode + "/>"
                + "</caps:supported>"

                // unsupported duplex mode
                + "<caps:notsupported>"
                + "<caps:" + unSupportedDuplexMode + "/>"
                + "</caps:notsupported>"
                + "</caps:duplex>"
                + "</caps:servcaps>"
                + "<contact>" + contact + "</contact>"
                + "</tuple>"
                + "</presence>";
        return pidfBuilder;
    }

    private void verifyCapabilityResult(RcsContactUceCapability resultCapability,
            Uri expectedUri,
            String serviceDescription,
            String serviceStatus,
            int expectedResult,
            int expectedSourceType,
            boolean expectedAudioSupported,
            boolean expectedVideoSupported,
            String expectedSupportedDuplexMode,
            String expectedUnSupportedDuplexMode) {
        // Verify the contact URI
        assertEquals(expectedUri, resultCapability.getContactUri());

        // Verify the source type is the network type.
        assertEquals(expectedSourceType, resultCapability.getSourceType());

        // Verify the request result is expected.
        final int requestResult = resultCapability.getRequestResult();
        assertEquals(requestResult, expectedResult);

        // Verify the mechanism is presence
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE,
                resultCapability.getCapabilityMechanism());

        RcsContactPresenceTuple presenceTuple =
                resultCapability.getCapabilityTuple("service_id_01");
        assertNotNull("Contact Presence tuple should not be null!", presenceTuple);

        RcsContactPresenceTuple.ServiceCapabilities capabilities =
                presenceTuple.getServiceCapabilities();
        assertNotNull("Service capabilities should not be null!", capabilities);

        // Verify timestamp
        assertNotNull("Timestamp should not be null!", presenceTuple.getTime());

        // Verify service id
        assertEquals("service_id_01", presenceTuple.getServiceId());

        // Verify service status
        assertEquals(serviceStatus, presenceTuple.getStatus());

        // Verify service description
        assertEquals(serviceDescription, presenceTuple.getServiceDescription());

        // Verify audio
        assertEquals(expectedAudioSupported, capabilities.isAudioCapable());

        // Verify video
        assertEquals(expectedVideoSupported, capabilities.isVideoCapable());

        // Verify Supported Duplex Mode
        assertEquals(expectedSupportedDuplexMode, capabilities.getSupportedDuplexModes().get(0));

        // Verify UnSupported Duplex Mode
        assertEquals(expectedUnSupportedDuplexMode,
                capabilities.getUnsupportedDuplexModes().get(0));
    }

    private <T> T waitForResult(BlockingQueue<T> queue) throws Exception {
        return queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static void connectTestImsService() throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
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
        assertTrue("Did not receive RcsFeature#setCapabilityExchangeEventListener",
                sServiceConnector.getCarrierService().waitForLatchCountdown(
                        TestImsService.LATCH_UCE_LISTENER_SET));
        int serviceSlot = sServiceConnector.getCarrierService().getRcsFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated RcsFeature",
                sTestSlot, serviceSlot);
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForCarrierConfigChanged();
    }

    private static void removeTestContactFromEab() {
        try {
            sServiceConnector.removeEabContacts(sTestSlot, sTestPhoneNumber);
        } catch (Exception e) {
            Log.w("RcsUceAdapterTest", "Cannot remove test contacts from eab database: " + e);
        }
    }

    private static String formatNumber(Context context, String number) {
        TelephonyManager manager = context.getSystemService(TelephonyManager.class);
        String simCountryIso = manager.getSimCountryIso();
        if (simCountryIso != null) {
            simCountryIso = simCountryIso.toUpperCase();
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            try {
                Phonenumber.PhoneNumber phoneNumber = util.parse(number, simCountryIso);
                return util.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
            } catch (NumberParseException e) {
                Log.w(TAG, "formatNumber: could not format " + number + ", error: " + e);
            }
        }
        return number;
    }

    private String getEabCapabilities(String phoneNum) throws Exception {
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(COMMAND_BASE).append(COMMAND_GET_EAB_CONTACT)
                .append(" ").append(phoneNum);
        return TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                cmdBuilder.toString());
    }

    private void setProvisioningIntValue(int key, int value) {
        final UiAutomation automan = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automan.adoptShellPermissionIdentity();
            ProvisioningManager provisioningManager =
                    ProvisioningManager.createForSubscriptionId(sTestSub);
            provisioningManager.setProvisioningIntValue(key, value);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    private void waitingEabCapabilityExpire() throws Exception {
        int retryTimes = POLLING_RETRY_TIMES;
        long expirationTime;
        do {
            String capabilities = getEabCapabilities(formatNumber(getContext(), sTestPhoneNumber));
            String[] capabilityInfo = capabilities.split(",");
            assertTrue(capabilityInfo.length > 4);
            Thread.sleep(WAITING_IN_MILLI_SEC);
            expirationTime = Long.parseLong(capabilityInfo[2]);
            retryTimes--;
        } while (retryTimes > 0 && Instant.now().getEpochSecond() < expirationTime);
    }
}
