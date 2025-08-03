/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_FOUND;
import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_NOT_FOUND;
import static android.telephony.ims.RcsContactUceCapability.SOURCE_TYPE_CACHED;
import static android.telephony.ims.RcsContactUceCapability.SOURCE_TYPE_NETWORK;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_FETCH_ERROR;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_GENERIC_FAILURE;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_INSUFFICIENT_MEMORY;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_INVALID_PARAM;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_LOST_NETWORK_CONNECTION;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_FOUND;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_SUPPORTED;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_NO_CHANGE;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_REQUEST_TIMEOUT;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNAVAILABLE;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNKNOWN;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.Telephony;
import android.telecom.PhoneAccount;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.SipDetails;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.CapabilityExchangeEventListener;
import android.telephony.ims.stub.CapabilityExchangeEventListener.OptionsRequestCallback;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.BlockedNumberUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.os.SomeArgs;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class RcsUceAdapterTest {

    private static final String FEATURE_TAG_CHAT =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";
    private static final String FEATURE_TAG_FILE_TRANSFER =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fthttp\"";
    private static final String FEATURE_TAG_MMTEL_AUDIO_CALL =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"";
    private static final String FEATURE_TAG_MMTEL_VIDEO_CALL =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\";video";

    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static final Uri LISTENER_URI = Uri.withAppendedPath(Telephony.SimInfo.CONTENT_URI,
            Telephony.SimInfo.COLUMN_IMS_RCS_UCE_ENABLED);
    private static HandlerThread sHandlerThread;
    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;
    private static boolean sDeviceUceEnabled;

    private static String sTestPhoneNumber;
    private static String sTestContact2;
    private static String sTestContact3;
    private static String sTestContact4;
    private static Uri sTestNumberUri;
    private static Uri sTestContact2Uri;
    private static Uri sTestContact3Uri;
    private static Uri sTestContact4Uri;

    private ContentObserver mUceObserver;

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

        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);

        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }
        sHandlerThread = new HandlerThread("CtsTelephonyTestCases");
        sHandlerThread.start();

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        sServiceConnector.clearAllActiveImsServices(sTestSlot);

        // Save the original config of device uce enabled and override it.
        sDeviceUceEnabled = sServiceConnector.getDeviceUceEnabled();
        sServiceConnector.setDeviceUceEnabled(true);

        sReceiver = new RcsUceAdapterTest.CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);

        // Initialize the test phone numbers
        initPhoneNumbers();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // // Restore all ImsService configurations that existed before the test.
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

        if (sHandlerThread != null) {
            sHandlerThread.quit();
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
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Unbind the ImsService after the test completes.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.disconnectDeviceImsService();
        }
        overrideCarrierConfig(null);
        // Remove all the test contacts from EAB database
        removeTestContactFromEab();

        removeUceRequestDisallowedStatus();
    }

    @Test
    public void testCapabilityDiscoveryIntentReceiverExists() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        PackageManager packageManager = getContext().getPackageManager();
        ResolveInfo info = packageManager.resolveActivity(
                new Intent(ImsRcsManager.ACTION_SHOW_CAPABILITY_DISCOVERY_OPT_IN),
                PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull(ImsRcsManager.ACTION_SHOW_CAPABILITY_DISCOVERY_OPT_IN
                + " Intent action must be handled by an appropriate settings application.", info);
        assertNotEquals(ImsRcsManager.ACTION_SHOW_CAPABILITY_DISCOVERY_OPT_IN
                + " activity intent filter must have a > 0 priority.", 0, info.priority);
    }

    @Test
    public void testGetAndSetUceSetting() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter adapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("RcsUceAdapter can not be null!", adapter);

        Boolean isEnabled = null;
        try {
            isEnabled = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    adapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                    "android.permission.READ_PHONE_STATE");
            assertNotNull(isEnabled);

            // Ensure the ContentObserver gets the correct callback based on the change.
            LinkedBlockingQueue<Uri> queue = new LinkedBlockingQueue<>(1);
            registerUceObserver(queue::offer);
            boolean userSetIsEnabled = isEnabled;
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    adapter, a -> a.setUceSettingEnabled(!userSetIsEnabled), ImsException.class,
                    "android.permission.MODIFY_PHONE_STATE");
            Uri result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull(result);
            assertTrue("Unexpected URI, should only receive URIs with prefix " + LISTENER_URI,
                    result.isPathPrefixMatch(LISTENER_URI));
            // Verify the subId associated with the Observer is correct.
            List<String> pathSegments = result.getPathSegments();
            String subId = pathSegments.get(pathSegments.size() - 1);
            assertEquals("Subscription ID contained in ContentObserver URI doesn't match the "
                            + "subscription that has changed.",
                    String.valueOf(sTestSub), subId);

            Boolean setResult = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    adapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                    "android.permission.READ_PHONE_STATE");
            assertNotNull(setResult);
            assertEquals("Incorrect setting!", !userSetIsEnabled, setResult);
        } catch (ImsException e) {
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("failed getting UCE setting with code: " + e.getCode());
            }
        } finally {
            if (isEnabled != null) {
                boolean userSetIsEnabled = isEnabled;
                // set back to user preference
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                        adapter, a -> a.setUceSettingEnabled(userSetIsEnabled), ImsException.class,
                        "android.permission.MODIFY_PHONE_STATE");
            }
            unregisterUceObserver();
        }
    }

    @Test
    public void testMethodPermissions() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);
        Collection<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        // isUceSettingEnabled - read
        Boolean isUceSettingEnabledResult = null;
        try {
            isUceSettingEnabledResult =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    uceAdapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                    "android.permission.READ_PHONE_STATE");
            assertNotNull("result from isUceSettingEnabled should not be null",
                    isUceSettingEnabledResult);
        } catch (SecurityException e) {
            fail("isUceSettingEnabled should succeed with READ_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("isUceSettingEnabled failed with code " + e.getCode());
            }
        }

        // isUceSettingEnabled - read_privileged
        try {
            isUceSettingEnabledResult =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                            uceAdapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                            "android.permission.READ_PRIVILEGED_PHONE_STATE");
            assertNotNull("result from isUceSettingEnabled should not be null",
                    isUceSettingEnabledResult);
        } catch (SecurityException e) {
            fail("isUceSettingEnabled should succeed with READ_PRIVILEGED_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("isUceSettingEnabled failed with code " + e.getCode());
            }
        }

        // setUceSettingEnabled
        boolean isUceSettingEnabled =
                (isUceSettingEnabledResult == null ? false : isUceSettingEnabledResult);
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.setUceSettingEnabled(isUceSettingEnabled), ImsException.class,
                    "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            fail("setUceSettingEnabled should succeed with MODIFY_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("setUceSettingEnabled failed with code " + e.getCode());
            }
        }

        // getUcePublishState without permission
        try {
            uceAdapter.getUcePublishState();
            fail("getUcePublishState should require READ_PRIVILEGED_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // getUcePublishState with permission
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    RcsUceAdapter::getUcePublishState, ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("getUcePublishState should succeed with READ_PRIVILEGED_PHONE_STATE.");
        } catch (ImsException e) {
            // ImsExceptions are still valid because it means the permission check passed.
        }

        final RcsUceAdapter.OnPublishStateChangedListener publishStateListener = (state) -> { };

        // addOnPublishStateChangedListener without permission
        try {
            uceAdapter.addOnPublishStateChangedListener(Runnable::run, publishStateListener);
            fail("addOnPublishStateChangedListener should require "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }

        // addOnPublishStateChangedListener with permission.
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.addOnPublishStateChangedListener(Runnable::run, publishStateListener),
                    ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("addOnPublishStateChangedListener should succeed with "
                    + "READ_PRIVILEGED_PHONE_STATE.");
        } catch (ImsException e) {
            // ImsExceptions are still valid because it means the permission check passed.
        }

        // removeOnPublishStateChangedListener without permission
        try {
            uceAdapter.removeOnPublishStateChangedListener(publishStateListener);
            fail("removeOnPublishStateChangedListener should require "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }

        // Prepare the callback of the capability request
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
            }
            @Override
            public void onComplete() {
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
            }
        };

        // requestCapabilities without permission
        try {
            uceAdapter.requestCapabilities(numbers, Runnable::run , callback);
            fail("requestCapabilities should require ACCESS_RCS_USER_CAPABILITY_EXCHANGE.");
        } catch (SecurityException e) {
            //expected
        }

        // requestAvailability without permission
        try {
            uceAdapter.requestAvailability(sTestNumberUri, Runnable::run, callback);
            fail("requestAvailability should require ACCESS_RCS_USER_CAPABILITY_EXCHANGE.");
        } catch (SecurityException e) {
            //expected
        }

        // requestCapabilities in the foreground
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.requestCapabilities(numbers, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE.");
        } catch (ImsException e) {
            // ImsExceptions are still valid because it means the permission check passed.
        }

        // requestAvailability in the foreground
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.requestAvailability(sTestNumberUri, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestAvailability should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE.");
        } catch (ImsException e) {
            // ImsExceptions are still valid because it means the permission check passed.
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testCapabilitiesRequestAllowed() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Start cap exchange disabled and enable later.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                false);
        overrideCarrierConfig(bundle);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Prepare the test contact and the callback
        Collection<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
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
            }
        };

        // The API requestCapabilities should fail when it doesn't grant the permission.
        try {
            uceAdapter.requestCapabilities(numbers, Runnable::run, callback);
            fail("requestCapabilities requires ACCESS_RCS_USER_CAPABILITY_EXCHANGE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // The API requestAvailability should fail when it doesn't grant the permission.
        try {
            uceAdapter.requestAvailability(sTestNumberUri, Runnable::run, callback);
            fail("requestAvailability requires ACCESS_RCS_USER_CAPABILITY_EXCHANGE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // Trigger carrier config changed
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        // Connect to the TestImsService
        connectTestImsService();

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the callback "onError" is called with the error code NOT_ENABLED because
        // the carrier config KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL is still false.
        try {
            assertEquals(RcsUceAdapter.ERROR_NOT_ENABLED, waitForIntResult(errorQueue));
        } catch (Exception e) {
            fail("requestCapabilities with command error failed: " + e);
        } finally {
            capabilityQueue.clear();
            completeQueue.clear();
            errorQueue.clear();
        }

        requestAvailability(uceAdapter, sTestNumberUri, callback);

        // Verify that the callback "onError" is called with the error code NOT_ENABLED because
        // the carrier config KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL is still false.
        try {
            assertEquals(RcsUceAdapter.ERROR_NOT_ENABLED, waitForIntResult(errorQueue));
        } catch (Exception e) {
            fail("requestAvailability with command error failed: " + e);
        } finally {
            capabilityQueue.clear();
            completeQueue.clear();
            errorQueue.clear();
        }

        // Override another carrier config KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                true);
        overrideCarrierConfig(bundle);

        // Prepare the network response is 200 OK and the capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(sTestNumberUri, new Pair<>(true, true));

        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(new ArrayList(uris),
                    contactExpectedMedia));
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the contact capability is received and the onCompleted is called.
        verifyCapabilities(numbers, getCapabilities(capabilityQueue, numbers.size()),
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);
        waitForResult(completeQueue);

        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        requestAvailability(uceAdapter, sTestNumberUri, callback);

        // Verify that the contact capability is received and the onCompleted is called.
        verifyCapabilityReceived(sTestNumberUri, capabilityQueue, SOURCE_TYPE_NETWORK,
                REQUEST_RESULT_FOUND, contactExpectedMedia.get(sTestNumberUri).first,
                contactExpectedMedia.get(sTestNumberUri).second);

        waitForResult(completeQueue);

        overrideCarrierConfig(null);
    }

    @Test
    public void testCapabilitiesRequestWithCmdError() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Connect to the TestImsService
        setupTestImsService(uceAdapter, true, true, false);

        Collection<Uri> contacts = Collections.singletonList(sTestNumberUri);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        // Prepare queues to receive the callback
        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> retryAfterQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
            }
            @Override
            public void onComplete() {
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(errorCode);
                retryAfterQueue.offer(retryAfterMilliseconds);
            }
        };

        // Prepare a map and define each command error code and its associated result.
        Map<Integer, Integer> cmdErrorMap = new HashMap<>();
        cmdErrorMap.put(COMMAND_CODE_SERVICE_UNKNOWN, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_GENERIC_FAILURE, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_INVALID_PARAM, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_FETCH_ERROR, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_REQUEST_TIMEOUT, RcsUceAdapter.ERROR_REQUEST_TIMEOUT);
        cmdErrorMap.put(COMMAND_CODE_INSUFFICIENT_MEMORY, RcsUceAdapter.ERROR_INSUFFICIENT_MEMORY);
        cmdErrorMap.put(COMMAND_CODE_LOST_NETWORK_CONNECTION, RcsUceAdapter.ERROR_LOST_NETWORK);
        cmdErrorMap.put(COMMAND_CODE_NOT_SUPPORTED, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_NOT_FOUND, RcsUceAdapter.ERROR_NOT_FOUND);
        cmdErrorMap.put(COMMAND_CODE_SERVICE_UNAVAILABLE, RcsUceAdapter.ERROR_SERVER_UNAVAILABLE);
        cmdErrorMap.put(COMMAND_CODE_NO_CHANGE, RcsUceAdapter.ERROR_GENERIC_FAILURE);

        // Verify each command error code and the expected callback result
        cmdErrorMap.forEach((cmdError, expectedCallbackResult) -> {
            // Setup the capabilities request that will be failed with the given command error code
            capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
                cb.onCommandError(cmdError);
            });

            requestCapabilities(uceAdapter, contacts, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestCapabilities with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
            }

            requestAvailability(uceAdapter, sTestNumberUri, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestAvailability with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
            }
        });

        overrideCarrierConfig(null);
    }

    @Test
    public void testCapabilitiesRequestWithResponseError() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* options */);

        Collection<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> retryAfterQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
            }
            @Override
            public void onComplete() {
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(errorCode);
                retryAfterQueue.offer(retryAfterMilliseconds);
            }
        };

        Map<Entry<Integer, String>, Integer> networkRespError = new HashMap<>();
        // 408 Request Timeout
        networkRespError.put(new Entry<Integer, String>() {
            @Override
            public Integer getKey() {
                return 408;
            }
            @Override
            public String getValue() {
                return "Request Timeout";
            }
            @Override
            public String setValue(String value) {
                return value;
            }
        }, RcsUceAdapter.ERROR_REQUEST_TIMEOUT);

        // 423 Interval Too Short
        networkRespError.put(new Entry<Integer, String>() {
            @Override
            public Integer getKey() {
                return 423;
            }
            @Override
            public String getValue() {
                return "Interval Too Short";
            }
            @Override
            public String setValue(String value) {
                return value;
            }
        }, RcsUceAdapter.ERROR_GENERIC_FAILURE);

        // 500 Server Internal Error
        networkRespError.put(new Entry<Integer, String>() {
            @Override
            public Integer getKey() {
                return 500;
            }
            @Override
            public String getValue() {
                return "Service Unavailable";
            }
            @Override
            public String setValue(String value) {
                return value;
            }
        }, RcsUceAdapter.ERROR_SERVER_UNAVAILABLE);

        // 503 Service Unavailable
        networkRespError.put(new Entry<Integer, String>() {
            @Override
            public Integer getKey() {
                return 503;
            }
            @Override
            public String getValue() {
                return "Service Unavailable";
            }
            @Override
            public String setValue(String value) {
                return value;
            }
        }, RcsUceAdapter.ERROR_SERVER_UNAVAILABLE);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        networkRespError.forEach((networkResp, expectedCallbackResult) -> {
            // Set the capabilities request failed with the given SIP code (without Reason header)
            capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
                cb.onNetworkResponse(networkResp.getKey(), networkResp.getValue());
            });

            requestCapabilities(uceAdapter, numbers, callback);
            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestCapabilities with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
                removeTestContactFromEab();
                removeUceRequestDisallowedStatus();
            }

            requestAvailability(uceAdapter, sTestNumberUri, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestAvailability with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
                removeTestContactFromEab();
                removeUceRequestDisallowedStatus();
            }

            /*
             * Set the capabilities request failed with the given SIP code (with Reason header)
             */
            capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
                int networkRespCode = 200;
                String networkReason = "OK";
                cb.onNetworkResponse(networkRespCode, networkReason,
                        networkResp.getKey(), networkResp.getValue());
            });

            requestCapabilities(uceAdapter, numbers, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestCapabilities with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
                removeTestContactFromEab();
                removeUceRequestDisallowedStatus();
            }

            requestAvailability(uceAdapter, sTestNumberUri, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestAvailability with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
                removeTestContactFromEab();
                removeUceRequestDisallowedStatus();
            }
        });

        // Set the capabilities request will be failed with the 403 sip code
        int networkResp = 403;
        String networkRespReason = "";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkResp, networkRespReason);
        });

        requestAvailability(uceAdapter, sTestNumberUri, callback);

        // Verify that the callback "onError" is called with the error code FORBIDDEN
        try {
            assertEquals(RcsUceAdapter.ERROR_FORBIDDEN, waitForIntResult(errorQueue));
            assertEquals(0L, waitForLongResult(retryAfterQueue));
        } catch (Exception e) {
            fail("requestAvailability with command error failed: " + e);
        } finally {
            errorQueue.clear();
            retryAfterQueue.clear();
            removeTestContactFromEab();
            removeUceRequestDisallowedStatus();
        }

        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the capabilities request is sill failed because the ImsService has returned
        // the 403 error before.
        try {
            assertEquals(RcsUceAdapter.ERROR_FORBIDDEN, waitForIntResult(errorQueue));
            assertEquals(0L, waitForLongResult(retryAfterQueue));
        } catch (Exception e) {
            fail("requestCapabilities with command error failed: " + e);
        } finally {
            errorQueue.clear();
            retryAfterQueue.clear();
            removeTestContactFromEab();
            removeUceRequestDisallowedStatus();
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testRequestCapabilitiesWithPresenceMechanism() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Override the carrier config to support group subscribe.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_GROUP_SUBSCRIBE_BOOL, true);
        overrideCarrierConfig(bundle);

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // Prepare three contacts
        final Uri contact1 = sTestNumberUri;
        final Uri contact2 = sTestContact2Uri;
        final Uri contact3 = sTestContact3Uri;
        final Uri contact4 = sTestContact4Uri;

        Collection<Uri> contacts = new ArrayList<>(4);
        contacts.add(contact1);
        contacts.add(contact2);
        contacts.add(contact3);
        contacts.add(contact4);

        // Setup the network response is 200 OK and notify capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(contact1, new Pair<>(true, true));
        contactExpectedMedia.put(contact2, new Pair<>(true, false));
        contactExpectedMedia.put(contact3, new Pair<>(false, false));
        contactExpectedMedia.put(contact4, new Pair<>(false, false));

        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(new ArrayList(uris),
                    contactExpectedMedia));
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify that all the four contact's capabilities are received
        List<RcsContactUceCapability> resultCapList =
                getCapabilities(capabilityQueue, contacts.size());

        Collection<Uri> contactsToVerify = new ArrayList<>(3);
        contactsToVerify.add(contact1);
        contactsToVerify.add(contact2);
        contactsToVerify.add(contact3);

        // Verify the contact with normal capabilities from the received capabilities list
        verifyCapabilities(contactsToVerify, resultCapList, SOURCE_TYPE_NETWORK,
                REQUEST_RESULT_FOUND, contactExpectedMedia);

        // Verify the contact with malformed capabilities from the received capabilities list
        RcsContactUceCapability resultCapability = getContactCapability(resultCapList, contact4);
        assertNotNull("Cannot find the contact", resultCapability);
        verifyMalformedCapabilityResult(resultCapability, contact4, SOURCE_TYPE_NETWORK,
                REQUEST_RESULT_FOUND, contactExpectedMedia.get(contact4).first,
                contactExpectedMedia.get(contact4).second);
        // Verify the onCompleted is called
        waitForResult(completeQueue);

        errorQueue.clear();
        errorRetryQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        resultCapList.clear();
        removeTestContactFromEab();

        // Setup the callback that some of the contacts are terminated.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(404, "NOT FOUND");
        });

        requestCapabilities(uceAdapter, contacts, callback);

        contactExpectedMedia.clear();
        contactExpectedMedia.put(contact1, new Pair<>(false, false));
        contactExpectedMedia.put(contact2, new Pair<>(false, false));
        contactExpectedMedia.put(contact3, new Pair<>(false, false));
        contactExpectedMedia.put(contact4, new Pair<>(false, false));

        // Verify the contact capabilities from the received capabilities list
        verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        errorQueue.clear();
        errorRetryQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        // Setup the callback that some of the contacts are terminated.
        contactExpectedMedia.replace(contact1, new Pair<>(true, true));

        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            List<Uri> uriList = new ArrayList(uris);
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            // Notify capabilities updated for the first contact
            assertEquals(contact1, uriList.get(0));
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(uriList.subList(0, 1),
                    contactExpectedMedia));
            List<Pair<Uri, String>> terminatedResources = new ArrayList<>();
            for (int i = 1; i < uriList.size(); i++) {
                Pair<Uri, String> pair = Pair.create(uriList.get(i), "noresource");
                terminatedResources.add(pair);
            }
            cb.onResourceTerminated(terminatedResources);
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        resultCapList = getCapabilities(capabilityQueue, contacts.size());

        // Verify the first contact capabilities from the received capabilities list
        verifyCapabilities(Collections.singletonList(contact1), resultCapList,
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);

        contactsToVerify.clear();
        contactsToVerify.add(contact2);
        contactsToVerify.add(contact3);
        contactsToVerify.add(contact4);

        // Verify the other contact capabilities from the received capabilities list
        verifyCapabilities(contactsToVerify, resultCapList, SOURCE_TYPE_NETWORK,
                REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        overrideCarrierConfig(null);
    }

    /**
     * Tests the case when contact1 has had a successful network query, but a query for contact2
     * has resulted in the carrier network not responding with a NOTIFY. If contact1 caps are
     * queried again, the query to the cache for contact1 should not be blocked in a queue behind
     * the pending network query. Eventually, request for contact2 will timeout with onTimeout
     * response from vendor, which will result in ERROR_REQUEST_TIMEOUT result back to app.
     */
    @Test
    public void testCacheQuerySuccessWhenNetworkBlocked() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
                capabilities.forEach(capabilityQueue::offer);
            }
            @Override
            public void onComplete() {
                completeQueue.offer(true);
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(errorCode);
            }
        };

        // Prepare two contacts
        final Uri contact1 = sTestNumberUri;
        final Uri contact2 = sTestContact2Uri;

        Collection<Uri> contacts = new ArrayList<>(1);
        contacts.add(contact1);

        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(contact1, new Pair<>(true, true));

        // Setup the network response is 200 OK and notify capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(new ArrayList(uris),
                    contactExpectedMedia));
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify contact1's capabilities from the received capabilities list
        verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        completeQueue.clear();
        capabilityQueue.clear();

        // Now hold the second contact and do not return a response until after contact1 is queried
        //again
        CountDownLatch latch = new CountDownLatch(1);
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            try {
                cb.onNetworkResponse(networkRespCode, networkRespReason);
                assertTrue("Timed out waiting for latch", latch.await(10, TimeUnit.SECONDS));
                // We didn't receive any NOTIFY
                cb.onTerminated("timeout", 0L);
            } catch (InterruptedException e) {
                fail("Waiting for cap response resulted in unexpected exception: " + e);
            }
        });

        contacts.clear();
        contacts.add(contact2);

        requestCapabilities(uceAdapter, contacts, callback);

        // Send another request for contact1's caps. Although the request queue is blocked due to
        // pending network request, contact1 has valid caps, so system should return those.
        contacts.clear();
        contacts.add(contact1);

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify contact1's capabilities from the received capabilities list
        verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                SOURCE_TYPE_CACHED, REQUEST_RESULT_FOUND, contactExpectedMedia);

        // Now contact2's query finishes and it timed out without a NOTIFY
        latch.countDown();

        Integer error = waitForResult(errorQueue);
        assertEquals("Timeout without NOTIFY should result in ERROR_REQUEST_TIMEOUT",
                RcsUceAdapter.ERROR_REQUEST_TIMEOUT, error.intValue());

        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        overrideCarrierConfig(null);
    }

    @Test
    public void testRequestCapabilitiesFromCacheWithPresenceMechanism() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // Prepare three contacts
        final Uri contact1 = sTestNumberUri;
        final Uri contact2 = sTestContact2Uri;
        final Uri contact3 = sTestContact3Uri;

        Collection<Uri> contacts = new ArrayList<>(3);
        contacts.add(contact1);
        contacts.add(contact2);
        contacts.add(contact3);

        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(contact1, new Pair<>(true, true));
        contactExpectedMedia.put(contact2, new Pair<>(true, false));
        contactExpectedMedia.put(contact3, new Pair<>(false, false));

        // Setup the network response is 200 OK and notify capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(new ArrayList(uris),
                    contactExpectedMedia));
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify that all the three contact's capabilities are received.
        verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        errorQueue.clear();
        errorRetryQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();

        // The request should not be called because the capabilities should be retrieved from cache.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            fail("The request should not be called.");
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify that all the three contact's capabilities are received
        verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                SOURCE_TYPE_CACHED, REQUEST_RESULT_FOUND, contactExpectedMedia);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        errorQueue.clear();
        errorRetryQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        overrideCarrierConfig(null);
    }

    @Test
    public void testIndividualRequestCapabilities() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Override the carrier config to not support group subscribe.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_GROUP_SUBSCRIBE_BOOL, false);
        overrideCarrierConfig(bundle);

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Long> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        List<RcsContactUceCapability> capabilityQueue = new ArrayList<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
                capabilities.forEach(c -> capabilityQueue.add(c));
            }
            @Override
            public void onComplete() {
                completeQueue.offer(true);
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(new Long(errorCode));
                errorQueue.offer(retryAfterMilliseconds);
            }
        };

        // Prepare three contacts
        final Uri contact1 = sTestNumberUri;
        final Uri contact2 = sTestContact2Uri;
        final Uri contact3 = sTestContact3Uri;

        Collection<Uri> contacts = new ArrayList<>(3);
        contacts.add(contact1);
        contacts.add(contact2);
        contacts.add(contact3);

        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(contact1, new Pair<>(true, true));
        contactExpectedMedia.put(contact2, new Pair<>(true, false));
        contactExpectedMedia.put(contact3, new Pair<>(false, false));

        List<String> pidfXml1 = Collections.singletonList(getPidfXmlData(contact1, true, true));
        List<String> pidfXml2 = Collections.singletonList(getPidfXmlData(contact2, true, false));
        List<String> pidfXml3 = Collections.singletonList(getPidfXmlData(contact3, false, false));

        // Setup the network response is 200 OK and notify capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        AtomicInteger receiveRequestCount = new AtomicInteger(0);
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            receiveRequestCount.incrementAndGet();
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            assertEquals(1, uris.size());
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(new ArrayList(uris),
                    contactExpectedMedia));
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        // Verify the capability request has been split to individual requests.
        assertEquals(contacts.size(), receiveRequestCount.get());

        // verify the capabilities result
        assertEquals(contacts.size(), capabilityQueue.size());
        for (RcsContactUceCapability capability : capabilityQueue) {
            Uri contact = capability.getContactUri();
            if (contact1.equals(contact)) {
                verifyCapabilityResult(capability, contact1, SOURCE_TYPE_NETWORK,
                        REQUEST_RESULT_FOUND, true, true);
            } else if (contact2.equals(contact)) {
                verifyCapabilityResult(capability, contact2, SOURCE_TYPE_NETWORK,
                        REQUEST_RESULT_FOUND, true, false);
            } else if (contact3.equals(contact)) {
                verifyCapabilityResult(capability, contact3, SOURCE_TYPE_NETWORK,
                        REQUEST_RESULT_FOUND, false, false);
            } else {
                fail("The contact of the capabilities result is invalid.");
            }
        }

        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();
        receiveRequestCount.set(0);

        // Setup the callback that some of the contacts are terminated.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            receiveRequestCount.incrementAndGet();
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            assertEquals(1, uris.size());
            String uriPart = uris.iterator().next().getSchemeSpecificPart();
            if (contact1.getSchemeSpecificPart().equalsIgnoreCase(uriPart)) {
                cb.onNotifyCapabilitiesUpdate(pidfXml1);
            } else {
                // Notify resources terminated for the reset contacts
                List<Uri> uriList = new ArrayList(uris);
                List<Pair<Uri, String>> terminatedResources = new ArrayList<>();
                for (int i = 0; i < uriList.size(); i++) {
                    Pair<Uri, String> pair = Pair.create(uriList.get(i), "noresource");
                    terminatedResources.add(pair);
                }
                cb.onResourceTerminated(terminatedResources);
            }
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        // Verify the capability request has been split to individual requests.
        assertEquals(contacts.size(), receiveRequestCount.get());

        // verify the capabilities result
        assertEquals(contacts.size(), capabilityQueue.size());
        for (RcsContactUceCapability capability : capabilityQueue) {
            Uri contact = capability.getContactUri();
            if (contact1.equals(contact)) {
                verifyCapabilityResult(capability, contact1, SOURCE_TYPE_NETWORK,
                        REQUEST_RESULT_FOUND, true, true);
            } else if (contact2.equals(contact)) {
                verifyCapabilityResult(capability, contact2, SOURCE_TYPE_NETWORK,
                        REQUEST_RESULT_NOT_FOUND, true, false);
            } else if (contact3.equals(contact)) {
                verifyCapabilityResult(capability, contact3, SOURCE_TYPE_NETWORK,
                        REQUEST_RESULT_NOT_FOUND, false,
                        false);
            } else {
                fail("The contact of the capabilities result is invalid.");
            }
        }
        overrideCarrierConfig(null);
    }

    @Test
    public void testRequestCapabilitiesWithOptionsMechanism() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, false, true /* OPTIONS enabled */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        // The test contact
        Collection<Uri> contacts = new ArrayList<>(3);
        contacts.add(sTestNumberUri);

        // The result callback
        BlockingQueue<Long> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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
                errorQueue.offer(new Long(errorCode));
                errorQueue.offer(retryAfterMilliseconds);
            }
        };

        // Set the result of the network response is 200 OK.
        final List<String> featureTags = new ArrayList<>();
        featureTags.add(FEATURE_TAG_CHAT);
        featureTags.add(FEATURE_TAG_FILE_TRANSFER);
        featureTags.add(FEATURE_TAG_MMTEL_AUDIO_CALL);
        featureTags.add(FEATURE_TAG_MMTEL_VIDEO_CALL);
        capabilityExchangeImpl.setOptionsOperation((contact, myCapabilities, optionsCallback) -> {
            int sipCode = 200;
            String reason = "OK";
            optionsCallback.onNetworkResponse(sipCode, reason, featureTags);
        });

        // Request capabilities by calling the API requestCapabilities.
        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the callback "onCapabilitiesReceived" is called.
        RcsContactUceCapability capability = waitForResult(capabilityQueue);
        assertNotNull("RcsContactUceCapability should not be null", capability);
        // Verify the callback "onComplete" is called.
        assertNotNull(waitForResult(completeQueue));
        assertEquals(RcsContactUceCapability.SOURCE_TYPE_NETWORK, capability.getSourceType());
        assertEquals(sTestNumberUri, capability.getContactUri());
        assertEquals(RcsContactUceCapability.REQUEST_RESULT_FOUND, capability.getRequestResult());
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                capability.getCapabilityMechanism());
        Set<String> resultFeatureTags = capability.getFeatureTags();
        assertEquals(featureTags.size(), resultFeatureTags.size());
        for (String featureTag : featureTags) {
            if (!resultFeatureTags.contains(featureTag)) {
                fail("Cannot find feature tag in the result");
            }
        }
        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Request capabilities by calling the API requestAvailability.
        requestAvailability(uceAdapter, sTestContact2Uri, callback);

        // Verify the callback "onCapabilitiesReceived" is called.
        capability = waitForResult(capabilityQueue);
        // Verify the callback "onComplete" is called.
        waitForResult(completeQueue);
        assertNotNull("RcsContactUceCapability should not be null", capability);
        assertEquals(RcsContactUceCapability.SOURCE_TYPE_NETWORK, capability.getSourceType());
        assertEquals(sTestContact2Uri, capability.getContactUri());
        assertEquals(RcsContactUceCapability.REQUEST_RESULT_FOUND, capability.getRequestResult());
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                capability.getCapabilityMechanism());
        resultFeatureTags = capability.getFeatureTags();
        assertEquals(featureTags.size(), resultFeatureTags.size());
        for (String featureTag : featureTags) {
            if (!resultFeatureTags.contains(featureTag)) {
                fail("Cannot find feature tag in the result");
            }
        }
        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Set the OPTIONS result is failed.
        capabilityExchangeImpl.setOptionsOperation((contact, myCapabilities, optionsCallback) -> {
            int sipCode = 400;
            String reason = "Bad Request";
            optionsCallback.onNetworkResponse(sipCode, reason, Collections.EMPTY_LIST);
        });

        // Request capabilities by calling the API requestCapabilities.
        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the callback "onError" is called.
        assertEquals(RcsUceAdapter.ERROR_GENERIC_FAILURE, waitForLongResult(errorQueue));

        // The callback "onCapabilitiesReceived" should be called with NOT FOUND
        capability = waitForResult(capabilityQueue);
        assertEquals(RcsContactUceCapability.REQUEST_RESULT_NOT_FOUND,
                capability.getRequestResult());

        overrideCarrierConfig(null);
    }

    @Test
    public void testRequestCapabilitiesFromCacheWithOptionsMechanism() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, false, true /* OPTIONS enabled */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        // The result callback
        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> retryAfterQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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
                retryAfterQueue.offer(retryAfterMilliseconds);
            }
        };

        // Set the result of the network response is 200 OK.
        final List<String> featureTags = new ArrayList<>();
        featureTags.add(FEATURE_TAG_CHAT);
        featureTags.add(FEATURE_TAG_FILE_TRANSFER);
        featureTags.add(FEATURE_TAG_MMTEL_AUDIO_CALL);
        featureTags.add(FEATURE_TAG_MMTEL_VIDEO_CALL);
        capabilityExchangeImpl.setOptionsOperation((contact, myCapabilities, optionsCallback) -> {
            int sipCode = 200;
            String reason = "OK";
            optionsCallback.onNetworkResponse(sipCode, reason, featureTags);
        });

        // Request capabilities with the for the first time.
        requestCapabilities(uceAdapter, Collections.singletonList(sTestNumberUri), callback);

        // Verify the callback "onCapabilitiesReceived" is called.
        RcsContactUceCapability capability = waitForResult(capabilityQueue);
        assertNotNull("RcsContactUceCapability should not be null", capability);
        // Verify the callback "onComplete" is called.
        assertNotNull(waitForResult(completeQueue));
        assertEquals(RcsContactUceCapability.SOURCE_TYPE_NETWORK, capability.getSourceType());
        assertEquals(sTestNumberUri, capability.getContactUri());
        assertEquals(RcsContactUceCapability.REQUEST_RESULT_FOUND, capability.getRequestResult());
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                capability.getCapabilityMechanism());
        Set<String> resultFeatureTags = capability.getFeatureTags();
        assertEquals(featureTags.size(), resultFeatureTags.size());
        for (String featureTag : featureTags) {
            if (!resultFeatureTags.contains(featureTag)) {
                fail("Cannot find feature tag in the result");
            }
        }
        errorQueue.clear();
        retryAfterQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();

        // Request capabilities for the second time.
        requestAvailability(uceAdapter, sTestContact2Uri, callback);

        // Verify the callback "onCapabilitiesReceived" is called.
        capability = waitForResult(capabilityQueue);
        // Verify the callback "onComplete" is called.
        waitForResult(completeQueue);
        assertNotNull("RcsContactUceCapability should not be null", capability);
        assertEquals(RcsContactUceCapability.SOURCE_TYPE_NETWORK, capability.getSourceType());
        assertEquals(sTestContact2Uri, capability.getContactUri());
        assertEquals(RcsContactUceCapability.REQUEST_RESULT_FOUND, capability.getRequestResult());
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                capability.getCapabilityMechanism());
        resultFeatureTags = capability.getFeatureTags();
        assertEquals(featureTags.size(), resultFeatureTags.size());
        for (String featureTag : featureTags) {
            if (!resultFeatureTags.contains(featureTag)) {
                fail("Cannot find feature tag in the result");
            }
        }
        errorQueue.clear();
        retryAfterQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();

        // Set the OPTIONS result is failed because the capabilities should be retrieved from cache.
        capabilityExchangeImpl.setOptionsOperation((contact, myCapabilities, optionsCallback) -> {
            fail("The Options request should not be called.");
        });

        // The contact to requeste the capabilities is the same as the first time.
        requestCapabilities(uceAdapter, Collections.singletonList(sTestNumberUri), callback);

        // Verify the callback "onCapabilitiesReceived" is called.
        capability = waitForResult(capabilityQueue);
        assertNotNull("RcsContactUceCapability should not be null", capability);
        // Verify the callback "onComplete" is called.
        assertNotNull(waitForResult(completeQueue));
        // Verify the capabilities are retrieved from the cache.
        assertEquals(RcsContactUceCapability.SOURCE_TYPE_CACHED, capability.getSourceType());
        assertEquals(sTestNumberUri, capability.getContactUri());
        assertEquals(RcsContactUceCapability.REQUEST_RESULT_FOUND, capability.getRequestResult());
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                capability.getCapabilityMechanism());
        resultFeatureTags = capability.getFeatureTags();
        assertEquals(featureTags.size(), resultFeatureTags.size());
        for (String featureTag : featureTags) {
            if (!resultFeatureTags.contains(featureTag)) {
                fail("Cannot find feature tag in the result");
            }
        }
        errorQueue.clear();
        retryAfterQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        overrideCarrierConfig(null);
    }

    @Test
    public void testIndividualRequestCapabilitiesWithOptionsMechanism() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, false, true /* OPTIONS enabled */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        // Prepare three test contacts
        Collection<Uri> contacts = new ArrayList<>(3);
        contacts.add(sTestNumberUri);
        contacts.add(sTestContact2Uri);
        contacts.add(sTestContact3Uri);

        // The result callback
        BlockingQueue<Long> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        List<RcsContactUceCapability> capabilityQueue = new ArrayList<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
                capabilities.forEach(c -> capabilityQueue.add(c));
            }
            @Override
            public void onComplete() {
                completeQueue.offer(true);
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(new Long(errorCode));
                errorQueue.offer(retryAfterMilliseconds);
            }
        };

        // Set the result of the network response is 200 OK.
        final List<String> featureTags = new ArrayList<>();
        featureTags.add(FEATURE_TAG_CHAT);
        featureTags.add(FEATURE_TAG_FILE_TRANSFER);
        featureTags.add(FEATURE_TAG_MMTEL_AUDIO_CALL);
        featureTags.add(FEATURE_TAG_MMTEL_VIDEO_CALL);

        AtomicInteger receiveRequestCount = new AtomicInteger(0);

        capabilityExchangeImpl.setOptionsOperation((contact, myCapabilities, optionsCallback) -> {
            receiveRequestCount.incrementAndGet();
            int sipCode = 200;
            String reason = "OK";
            optionsCallback.onNetworkResponse(sipCode, reason, featureTags);
        });

        // Request capabilities by calling the API requestCapabilities.
        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the callback "onComplete" is called.
        assertNotNull(waitForResult(completeQueue));

        // Verify the capability request has been split to individual requests.
        assertEquals(contacts.size(), receiveRequestCount.get());

        // Verify the result
        verifyOptionsCapabilityResult(capabilityQueue, contacts,
                RcsContactUceCapability.SOURCE_TYPE_NETWORK,
                RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                RcsContactUceCapability.REQUEST_RESULT_FOUND, featureTags);

        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        receiveRequestCount.set(0);
        removeTestContactFromEab();

        // Set the OPTIONS result is failed.
        capabilityExchangeImpl.setOptionsOperation((contact, myCapabilities, optionsCallback) -> {
            receiveRequestCount.incrementAndGet();
            int sipCode = 400;
            String reason = "Bad Request";
            optionsCallback.onNetworkResponse(sipCode, reason, Collections.EMPTY_LIST);
        });

        // Request capabilities by calling the API requestCapabilities.
        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the callback "onError" is called.
        assertEquals(RcsUceAdapter.ERROR_GENERIC_FAILURE, waitForLongResult(errorQueue));

        // Verify the result
        verifyOptionsCapabilityResult(capabilityQueue, contacts,
                RcsContactUceCapability.SOURCE_TYPE_NETWORK,
                RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                RcsContactUceCapability.REQUEST_RESULT_NOT_FOUND, Collections.EMPTY_LIST);

        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        receiveRequestCount.set(0);
        removeTestContactFromEab();

        overrideCarrierConfig(null);
    }

    @Test
    public void testOptionsRequestFromNetwork() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, false, true /* OPTIONS enabled */);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        final Uri contact = sTestContact2Uri;
        Set<String> remoteCapabilities = new ArraySet<>();
        remoteCapabilities.add(FEATURE_TAG_CHAT);
        remoteCapabilities.add(FEATURE_TAG_FILE_TRANSFER);
        remoteCapabilities.add(FEATURE_TAG_MMTEL_AUDIO_CALL);
        remoteCapabilities.add(FEATURE_TAG_MMTEL_VIDEO_CALL);
        BlockingQueue<Pair<RcsContactUceCapability, Boolean>> respToCapRequestQueue =
                new LinkedBlockingQueue<>();
        OptionsRequestCallback callback = new OptionsRequestCallback() {
            @Override
            public void onRespondToCapabilityRequest(RcsContactUceCapability capabilities,
                    boolean isBlocked) {
                respToCapRequestQueue.offer(new Pair<>(capabilities, isBlocked));
            }
            @Override
            public void onRespondToCapabilityRequestWithError(int sipCode, String reason) {
            }
        };

        // Notify the remote capability request
        eventListener.onRemoteCapabilityRequest(contact, remoteCapabilities, callback);

        // Verify receive the result
        Pair<RcsContactUceCapability, Boolean> capability = waitForResult(respToCapRequestQueue);
        assertNotNull("RcsContactUceCapability should not be null", capability);
        assertEquals(RcsContactUceCapability.SOURCE_TYPE_CACHED, capability.first.getSourceType());
        assertEquals(RcsContactUceCapability.REQUEST_RESULT_FOUND,
                capability.first.getRequestResult());
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                capability.first.getCapabilityMechanism());
        // Should not report blocked
        assertFalse("This number is not blocked, so the API should not report blocked",
                capability.second);

        overrideCarrierConfig(null);
    }

    @Test
    public void testOptionsRequestFromNetworkBlocked() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, false, true /* OPTIONS enabled */);

        CapabilityExchangeEventListener eventListener =
                sServiceConnector.getCarrierService().getRcsFeature().getEventListener();

        final Uri contact = sTestNumberUri;
        Set<String> remoteCapabilities = new ArraySet<>();
        remoteCapabilities.add(FEATURE_TAG_CHAT);
        remoteCapabilities.add(FEATURE_TAG_FILE_TRANSFER);
        remoteCapabilities.add(FEATURE_TAG_MMTEL_AUDIO_CALL);
        remoteCapabilities.add(FEATURE_TAG_MMTEL_VIDEO_CALL);

        BlockingQueue<Pair<RcsContactUceCapability, Boolean>> respToCapRequestQueue =
                new LinkedBlockingQueue<>();
        OptionsRequestCallback callback = new OptionsRequestCallback() {
            @Override
            public void onRespondToCapabilityRequest(RcsContactUceCapability capabilities,
                    boolean isBlocked) {
                respToCapRequestQueue.offer(new Pair<>(capabilities, isBlocked));
            }
            @Override
            public void onRespondToCapabilityRequestWithError(int sipCode, String reason) {
            }
        };

        // Must be default SMS app to block numbers
        sServiceConnector.setDefaultSmsApp();
        Uri blockedUri = BlockedNumberUtil.insertBlockedNumber(getContext(), sTestPhoneNumber);
        assertNotNull("could not block number", blockedUri);
        try {
            // Notify the remote capability request
            eventListener.onRemoteCapabilityRequest(contact, remoteCapabilities, callback);

            // Verify receive the result
            Pair<RcsContactUceCapability, Boolean> capability =
                    waitForResult(respToCapRequestQueue);
            assertNotNull("RcsContactUceCapability should not be null", capability);
            assertEquals(RcsContactUceCapability.SOURCE_TYPE_CACHED,
                    capability.first.getSourceType());
            assertEquals(RcsContactUceCapability.REQUEST_RESULT_FOUND,
                    capability.first.getRequestResult());
            assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS,
                    capability.first.getCapabilityMechanism());
            // Should report blocked
            assertTrue("this number is blocked, so API should report blocked",
                    capability.second);
        } finally {
            BlockedNumberUtil.deleteBlockedNumber(getContext(), blockedUri);
            sServiceConnector.restoreDefaultSmsApp();
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testForbidCapabilitiesRequest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Override the carrier config of SIP 489 request forbidden.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_RCS_REQUEST_FORBIDDEN_BY_SIP_489_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_GROUP_SUBSCRIBE_BOOL, true);
        overrideCarrierConfig(bundle);

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> retryAfterQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        List<RcsContactUceCapability> capabilityQueue = new ArrayList<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
                capabilities.forEach(c -> capabilityQueue.add(c));
            }

            @Override
            public void onComplete() {
                completeQueue.offer(true);
            }

            @Override
            public void onError(int errorCode, long retryAfterMillis) {
                errorQueue.offer(errorCode);
                retryAfterQueue.offer(retryAfterMillis);
            }
        };

        // Prepare two contacts
        final Uri contact1 = sTestNumberUri;
        final Uri contact2 = sTestContact2Uri;
        Collection<Uri> contacts = new ArrayList<>(2);
        contacts.add(contact1);
        contacts.add(contact2);

        // Prepare the network response.
        final int sipCodeBadEvent = 489;
        final int sipCodeForbidden = 403;
        AtomicInteger subscribeRequestCount = new AtomicInteger(0);

        // Prepare a map to define the sip code and its associated result.
        Map<Integer, Integer> networkSipCodeMap = new HashMap<>();
        networkSipCodeMap.put(sipCodeBadEvent, RcsUceAdapter.ERROR_FORBIDDEN);
        networkSipCodeMap.put(sipCodeForbidden, RcsUceAdapter.ERROR_FORBIDDEN);

        // Verify each command error code and the expected callback result
        networkSipCodeMap.forEach((sipCode, expectedCallbackResult) -> {
            // Setup the capabilities request response with the given sip code.
            capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
                subscribeRequestCount.incrementAndGet();
                cb.onNetworkResponse(sipCode, "");
            });

            try {
                // Request contact uce capabilities
                requestCapabilities(uceAdapter, contacts, callback);

                // Verify that the callback "onError" is called with the error code FORBIDDEN
                assertEquals(RcsUceAdapter.ERROR_FORBIDDEN, waitForIntResult(errorQueue));
                // Verify the retryAfter value
                long retryAfterMillis = waitForLongResult(retryAfterQueue);
                if (sipCode == sipCodeForbidden) {
                    assertEquals(0L, retryAfterMillis);
                } else if (sipCode == sipCodeBadEvent) {
                    assertTrue(retryAfterMillis > 0L);
                }
            } catch (Exception e) {
                fail("testForbiddenResponseToCapabilitiesRequest with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
                capabilityQueue.clear();
                completeQueue.clear();
                subscribeRequestCount.set(0);
                removeTestContactFromEab();
                removeUceRequestDisallowedStatus();
            }
        });

        overrideCarrierConfig(null);
    }

    @Test
    public void testTerminatedCallbackWithCapabilitiesRequest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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
            public void onError(int errorCode, long retryAfterMillis) {
                errorQueue.offer(errorCode);
                errorRetryQueue.offer(retryAfterMillis);
            }
        };

        // Prepare the test contact and the callback
        Collection<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(sTestNumberUri, new Pair<>(true, true));

        // Prepare the network response is 200 OK and the capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";

        Map<SomeArgs, SomeArgs> terminatedMap = new HashMap<>();
        SomeArgs deactivatedArgs = SomeArgs.obtain();
        deactivatedArgs.arg1 = "deactivated";
        deactivatedArgs.arg2 = Long.valueOf(3000L);
        SomeArgs deactivatedExpectedArgs = SomeArgs.obtain();
        deactivatedExpectedArgs.argi1 = RcsUceAdapter.ERROR_GENERIC_FAILURE;
        deactivatedExpectedArgs.arg1 = Long.valueOf(3000L);
        terminatedMap.put(deactivatedArgs, deactivatedExpectedArgs);

        SomeArgs probationArgs = SomeArgs.obtain();
        probationArgs.arg1 = "probation";
        probationArgs.arg2 = Long.valueOf(4000L);
        SomeArgs probationExpectedArgs = SomeArgs.obtain();
        probationExpectedArgs.argi1 = RcsUceAdapter.ERROR_GENERIC_FAILURE;
        probationExpectedArgs.arg1 = Long.valueOf(4000L);
        terminatedMap.put(probationArgs, probationExpectedArgs);

        SomeArgs rejectedArgs = SomeArgs.obtain();
        rejectedArgs.arg1 = "rejected";
        rejectedArgs.arg2 = Long.valueOf(5000L);
        SomeArgs rejectedExpectedArgs = SomeArgs.obtain();
        rejectedExpectedArgs.argi1 = RcsUceAdapter.ERROR_NOT_AUTHORIZED;
        rejectedExpectedArgs.arg1 = Long.valueOf(0L);
        terminatedMap.put(rejectedArgs, rejectedExpectedArgs);

        SomeArgs timeoutArgs = SomeArgs.obtain();
        timeoutArgs.arg1 = "timeout";
        timeoutArgs.arg2 = Long.valueOf(6000L);
        SomeArgs timeoutExpectedArgs = SomeArgs.obtain();
        timeoutExpectedArgs.argi1 = RcsUceAdapter.ERROR_REQUEST_TIMEOUT;
        timeoutExpectedArgs.arg1 = Long.valueOf(6000L);
        terminatedMap.put(timeoutArgs, timeoutExpectedArgs);

        SomeArgs giveupArgs = SomeArgs.obtain();
        giveupArgs.arg1 = "giveup";
        giveupArgs.arg2 = Long.valueOf(7000L);
        SomeArgs giveupExpectedArgs = SomeArgs.obtain();
        giveupExpectedArgs.argi1 = RcsUceAdapter.ERROR_NOT_AUTHORIZED;
        giveupExpectedArgs.arg1 = Long.valueOf(7000L);
        terminatedMap.put(giveupArgs, giveupExpectedArgs);

        SomeArgs noresourceArgs = SomeArgs.obtain();
        noresourceArgs.arg1 = "noresource";
        noresourceArgs.arg2 = Long.valueOf(8000L);
        SomeArgs noresourceExpectedArgs = SomeArgs.obtain();
        noresourceExpectedArgs.argi1 = RcsUceAdapter.ERROR_NOT_FOUND;
        noresourceExpectedArgs.arg1 = Long.valueOf(0L);
        terminatedMap.put(giveupArgs, giveupExpectedArgs);

        SomeArgs emptyReasonArgs = SomeArgs.obtain();
        emptyReasonArgs.arg1 = "";
        emptyReasonArgs.arg2 = Long.valueOf(9000L);
        SomeArgs emptyReasonExpectedArgs = SomeArgs.obtain();
        emptyReasonExpectedArgs.argi1 = RcsUceAdapter.ERROR_GENERIC_FAILURE;
        emptyReasonExpectedArgs.arg1 = Long.valueOf(9000L);
        terminatedMap.put(emptyReasonArgs, emptyReasonExpectedArgs);

        // Verify each subscription terminated and the expected result
        terminatedMap.forEach((reason, expectedResult) -> {
            String terminatedReason = (String) reason.arg1;
            Long terminatedRetryAfterMillis = (Long) reason.arg2;
            capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
                cb.onNetworkResponse(networkRespCode, networkRespReason);
                cb.onNotifyCapabilitiesUpdate(getPidfForUris(new ArrayList(uris),
                        contactExpectedMedia));
                cb.onTerminated(terminatedReason, terminatedRetryAfterMillis);
            });

            requestCapabilities(uceAdapter, numbers, callback);

            try {
                // Verify that the contact capability is received and the onCompleted is called.
                verifyCapabilities(numbers, getCapabilities(capabilityQueue, numbers.size()),
                        SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);

                int expectedErrorCode = expectedResult.argi1;
                Long expectedRetryAfter = (Long) expectedResult.arg1;
                assertEquals(expectedErrorCode, waitForIntResult(errorQueue));
                assertEquals(expectedRetryAfter.longValue(), (waitForLongResult(errorRetryQueue)));
            } catch (Exception e) {
                fail("Unexpected exception " + e);
            }

            reason.recycle();
            expectedResult.recycle();
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            removeTestContactFromEab();
        });

        /*
         * Verify the subscribe request is successful when: A) The terminated is timeout and
         * B) The retryAfter is 0L and C) All the capabilities have been received.
         */
        String terminatedReason = "timeout";
        long terminatedRetryAfterMillis = 0L;
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(new ArrayList(uris),
                    contactExpectedMedia));
            cb.onTerminated(terminatedReason, terminatedRetryAfterMillis);
        });

        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the contact capability is received and the onCompleted is called.
        verifyCapabilities(numbers, getCapabilities(capabilityQueue, numbers.size()),
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);

        assertTrue(waitForResult(completeQueue));

        errorQueue.clear();
        errorRetryQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        /*
         * Set the subscribe request is failed because NOT all of the capabilities have been
         * received.
         */
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onTerminated(terminatedReason, terminatedRetryAfterMillis);
        });

        requestCapabilities(uceAdapter, numbers, callback);

        /*
         * Verify the request is failed because NOT all of the capabilities have been received.
         */
        assertEquals(RcsUceAdapter.ERROR_REQUEST_TIMEOUT, waitForIntResult(errorQueue));
        assertEquals(0L, (waitForLongResult(errorRetryQueue)));

        errorQueue.clear();
        errorRetryQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();
        overrideCarrierConfig(null);
    }

    @Test
    public void testTimeoutToRequestCapabilitiesWithPresenceMechanism() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // Prepare three contacts
        final Uri contact1 = sTestNumberUri;
        final Uri contact2 = sTestContact2Uri;
        final Uri contact3 = sTestContact3Uri;

        Collection<Uri> contacts = new ArrayList<>(3);
        contacts.add(contact1);
        contacts.add(contact2);
        contacts.add(contact3);

        // Setup the ImsService doesn't trigger any callbacks.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            // It won't trigger any callbacks.
        });

        try {
            setCapabilitiesRequestTimeout(3000L);

            requestCapabilities(uceAdapter, contacts, callback);

            // Verify that the clients receive the TIMEOUT error code.
            assertEquals(RcsUceAdapter.ERROR_REQUEST_TIMEOUT, waitForIntResult(errorQueue));
            assertEquals(0L, (waitForLongResult(errorRetryQueue)));
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            removeTestContactFromEab();
            removeUceRequestDisallowedStatus();
            setCapabilitiesRequestTimeout(-1L);
        }

        // Setup the ImsService only trigger the network response callback. However it doesn't
        // trigger the onTerminated callback
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
        });

        try {
            setCapabilitiesRequestTimeout(3000L);

            requestCapabilities(uceAdapter, contacts, callback);

            // Verify that the clients receive the TIMEOUT error code.
            assertEquals(RcsUceAdapter.ERROR_REQUEST_TIMEOUT, waitForIntResult(errorQueue));
            assertEquals(0L, (waitForLongResult(errorRetryQueue)));
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            removeTestContactFromEab();
            removeUceRequestDisallowedStatus();
            setCapabilitiesRequestTimeout(-1L);
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testRequestTimeoutWithPresenceMechanism() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // Prepare the test contact
        Collection<Uri> contacts = new ArrayList<>();
        contacts.add(sTestNumberUri);
        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(sTestNumberUri, new Pair<>(false, false));

        // Setup the ImsService doesn't trigger any callbacks.
        AtomicInteger subscribeRequestCount = new AtomicInteger(0);
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            subscribeRequestCount.incrementAndGet();
            // It won't trigger any callbacks to the framework.
        });

        // Set the timeout for 3 seconds
        setCapabilitiesRequestTimeout(3000L);

        // Request capabilities
        requestCapabilities(uceAdapter, contacts, callback);

        try {
            // Verify the error code REQUEST_TIMEOUT is received
            assertEquals(RcsUceAdapter.ERROR_REQUEST_TIMEOUT, waitForIntResult(errorQueue));
            assertEquals(0L, waitForLongResult(errorRetryQueue));

            // Verify the capabilities can still be received.
            verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            subscribeRequestCount.set(0);
        }

        // Request the capabilities with the same contact again.
        requestCapabilities(uceAdapter, contacts, callback);

        try {
            // Verify that the caller can received the capabilities callback.
            // Verify the capabilities can still be received.
            verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);
            // Verify the complete callback will be called.
            waitForResult(completeQueue);

            // Verify that the ImsService didn't received the request because the capabilities
            // should be retrieved from the cache.
            assertEquals(0, subscribeRequestCount.get());
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            removeTestContactFromEab();
            removeUceRequestDisallowedStatus();
            subscribeRequestCount.set(0);
            setCapabilitiesRequestTimeout(-1L);
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testTimeoutToRequestCapabilitiesWithOptionsMechanism() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, false, true /* OPTIONS enabled */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        // The test contact
        Collection<Uri> contacts = new ArrayList<>(3);
        contacts.add(sTestNumberUri);

        // The result callback
        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // Setup the ImsService doesn't trigger any callbacks.
        capabilityExchangeImpl.setOptionsOperation((contact, myCapabilities, optionsCallback) -> {
            // It won't trigger any callbacks.
        });

        try {
            setCapabilitiesRequestTimeout(3000L);

            requestCapabilities(uceAdapter, contacts, callback);

            // Verify that the clients receive the TIMEOUT error code.
            assertEquals(RcsUceAdapter.ERROR_REQUEST_TIMEOUT, waitForIntResult(errorQueue));
            assertEquals(0L, (waitForLongResult(errorRetryQueue)));
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            removeTestContactFromEab();
            setCapabilitiesRequestTimeout(-1L);
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testRequestCapabilitiesWithUriFormatChanged() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Override the carrier config to support group subscribe.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_GROUP_SUBSCRIBE_BOOL, true);
        overrideCarrierConfig(bundle);

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // Prepare three contacts
        final Uri contact1TelScheme = sTestNumberUri;
        final Uri contact1SipScheme = Uri.fromParts(PhoneAccount.SCHEME_SIP,
                sTestPhoneNumber + "@test.cts;user=phone", null);
        final Uri contact2 = sTestContact2Uri;
        final Uri contact3 = sTestContact3Uri;

        Collection<Uri> contacts = new ArrayList<>(3);
        // The first contact is using the tel scheme
        contacts.add(contact1TelScheme);
        contacts.add(contact2);
        contacts.add(contact3);

        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>(contacts.size());
        contactExpectedMedia.put(contact1SipScheme, new Pair<>(true, true));
        contactExpectedMedia.put(contact2, new Pair<>(true, false));
        contactExpectedMedia.put(contact3, new Pair<>(false, false));


        // Setup the network response is 200 OK and notify capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            List<Uri> uriList = new ArrayList<>();
            for (Uri uri : uris) {
                if (contact1TelScheme.equals(uri)) {
                    // ImsService replies the pidf xml data with the SIP scheme
                    uriList.add(contact1SipScheme);
                } else {
                    uriList.add(uri);
                }
            }
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(uriList, contactExpectedMedia));
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify that all the three contact's capabilities are received
        Collection<Uri> contactsToVerify = new ArrayList<>(3);
        contactsToVerify.add(contact1SipScheme);
        contactsToVerify.add(contact2);
        contactsToVerify.add(contact3);

        // Verify that all the three contact's capabilities are received.
        verifyCapabilities(contactsToVerify, getCapabilities(capabilityQueue, contacts.size()),
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        errorQueue.clear();
        errorRetryQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        // Setup the callback that some of the contacts are terminated.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(404, "NOT FOUND");
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the contacts are not found.
        contactExpectedMedia.clear();
        contactExpectedMedia.put(contact1TelScheme, new Pair<>(false, false));
        contactExpectedMedia.put(contact2, new Pair<>(false, false));
        contactExpectedMedia.put(contact3, new Pair<>(false, false));

        verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        errorQueue.clear();
        errorRetryQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        // Setup the callback that some of the contacts are terminated.
        contactExpectedMedia.clear();
        contactExpectedMedia.put(contact1SipScheme, new Pair<>(true, true));
        contactExpectedMedia.put(contact2, new Pair<>(true, false));
        contactExpectedMedia.put(contact3, new Pair<>(false, false));

        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            List<Uri> uriList = new ArrayList<>(uris);
            // Notify capabilities updated for the first contact
            assertEquals(uriList.get(0), contact1TelScheme);

            List<Uri> changedUriList = new ArrayList<>(1);
            for (Uri uri : uris) {
                if (contact1TelScheme.equals(uri)) {
                    changedUriList.add(contact1SipScheme);
                }
            }
            // ImsService replies the pidf xml data with the SIP scheme
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(changedUriList, contactExpectedMedia));

            List<Pair<Uri, String>> terminatedResources = new ArrayList<>();
            for (int i = 1; i < uriList.size(); i++) {
                Pair<Uri, String> pair = Pair.create(uriList.get(i), "noresource");
                terminatedResources.add(pair);
            }
            cb.onResourceTerminated(terminatedResources);
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the first contact is found.
        List<RcsContactUceCapability> resultCapList =
                getCapabilities(capabilityQueue, contacts.size());

        // Verify the first contact capabilities from the received capabilities list
        verifyCapabilities(Collections.singletonList(contact1SipScheme), resultCapList,
                SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);

        // Verify the other contact capabilities from the received capabilities list
        contactsToVerify.clear();
        contactsToVerify.add(contact2);
        contactsToVerify.add(contact3);

        verifyCapabilities(contactsToVerify, resultCapList, SOURCE_TYPE_NETWORK,
                REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        overrideCarrierConfig(null);
    }

    @Test
    public void testReceivingEmptyPidfXml() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* OPTIONS */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // Prepare the test contact
        Collection<Uri> contacts = new ArrayList<>(1);
        contacts.add(sTestNumberUri);
        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(sTestNumberUri, new Pair<>(false, false));
        // Setup the network response is 200 OK, empty PIDF data and the reason of onTerminated
        // is "TIMEOUT"
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            // Prepare the empty PIDF xml
            cb.onNotifyCapabilitiesUpdate(Collections.singletonList(""));
            cb.onTerminated("TIMEOUT", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);
        try {
            // Verify the contact capabilities is received and the result is NOT FOUND.
            // Verify the capabilities can still be received.
            verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);
            // Verify the callback "onCompleted" is called
            waitForResult(completeQueue);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            removeTestContactFromEab();
        }

        // Setup the network response is 404 NOT FOUND
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(404, "NOT FOUND");
        });

        requestCapabilities(uceAdapter, contacts, callback);
        try {
            // Verify the contact capabilities is received and the result is NOT FOUND.
            verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);
            // Verify that the callback "onComplete" is called
            waitForResult(completeQueue);
        } catch (Exception e) {
            fail("requestCapabilities is failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            removeTestContactFromEab();
        }

        // Setup the network response is 405 Method Not Allowed
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(405, "Method Not Allowed");
        });

        requestCapabilities(uceAdapter, contacts, callback);
        try {
            // Verify the contact capabilities is received and the result is NOT FOUND.
            verifyCapabilities(contacts, getCapabilities(capabilityQueue, contacts.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);
            // Verify that the callback "onComplete" is called
            waitForResult(completeQueue);
        } catch (Exception e) {
            fail("requestCapabilities is failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            completeQueue.clear();
            capabilityQueue.clear();
            removeTestContactFromEab();
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testContactInThrottlingState() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();
        // Reset the UCE device state.
        removeUceRequestDisallowedStatus();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* options */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // Prepare the test contact
        Collection<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);
        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(sTestNumberUri, new Pair<>(false, false));
        // Setup the network response is 408 Request Timeout.
        int networkRespCode = 408;
        String networkRespReason = "Request Timeout";
        AtomicInteger subscribeRequestCount = new AtomicInteger(0);
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            subscribeRequestCount.incrementAndGet();
            cb.onNetworkResponse(networkRespCode, networkRespReason);
        });

        // Request contact capabilities
        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the callback "onError" is called with the expected error code.
        try {
            assertEquals(RcsUceAdapter.ERROR_REQUEST_TIMEOUT, waitForIntResult(errorQueue));
            assertEquals(0L, waitForLongResult(errorRetryQueue));
            // Verify the caller can received the capabilities callback.
            verifyCapabilities(numbers, getCapabilities(capabilityQueue, numbers.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);
            // Verity the ImsService received the request.
            assertTrue(subscribeRequestCount.get() > 0);
        } catch (Exception e) {
            fail("testContactsInThrottlingState with command error failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            capabilityQueue.clear();
            completeQueue.clear();
            subscribeRequestCount.set(0);
        }

        // Request the capabilities again with the same contact.
        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the result.
        try {
            // Verify that the caller can received the capabilities callback.
            verifyCapabilities(numbers, getCapabilities(capabilityQueue, numbers.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);
            // Verify the complete callback will be called.
            waitForResult(completeQueue);
            // Verify that the ImsService didn't received the request because the capabilities
            // should be created from throttling list.
            assertEquals(0, subscribeRequestCount.get());
        } catch (Exception e) {
            fail("testContactsInThrottlingState with command error failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            subscribeRequestCount.set(0);
            // reset the cache and throttling list
            removeTestContactFromEab();
            removeUceRequestDisallowedStatus();
        }

        // Request availability.
        requestAvailability(uceAdapter, sTestNumberUri, callback);

        // Verify that the callback "onError" is called with the expected error code.
        try {
            assertEquals(RcsUceAdapter.ERROR_REQUEST_TIMEOUT, waitForIntResult(errorQueue));
            assertEquals(0L, waitForLongResult(errorRetryQueue));
            // Verify the caller can received the capabilities callback.
            verifyCapabilityReceived(sTestNumberUri, capabilityQueue, SOURCE_TYPE_NETWORK,
                    REQUEST_RESULT_NOT_FOUND, contactExpectedMedia.get(sTestNumberUri).first,
                    contactExpectedMedia.get(sTestNumberUri).second);
            // Verity the ImsService received the request.
            assertTrue(subscribeRequestCount.get() > 0);
        } catch (Exception e) {
            fail("requestAvailability with command error failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            capabilityQueue.clear();
            completeQueue.clear();
            subscribeRequestCount.set(0);
        }

        // Request availability again with the same contact.
        requestAvailability(uceAdapter, sTestNumberUri, callback);

        // Verify that the callback "onError" is called with the expected error code.
        try {
            // Verify that the caller can received the capabilities callback.
            verifyCapabilityReceived(sTestNumberUri, capabilityQueue, SOURCE_TYPE_NETWORK,
                    REQUEST_RESULT_NOT_FOUND, contactExpectedMedia.get(sTestNumberUri).first,
                    contactExpectedMedia.get(sTestNumberUri).second);
            // Verify the complete callback will be called.
            waitForResult(completeQueue);
            // Verify that the ImsService didn't received the request because the capabilities
            // should be retrieved from the cache.
            assertEquals(0, subscribeRequestCount.get());
        } catch (Exception e) {
            fail("testContactsInThrottlingState with command error failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            subscribeRequestCount.set(0);
            removeTestContactFromEab();
            removeUceRequestDisallowedStatus();
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testRequestResultInconclusive() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();
        // Reset the UCE device state.
        removeUceRequestDisallowedStatus();

        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* options */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> errorRetryQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
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

        // In the first round, prepare the test account
        Collection<Uri> numbers = new ArrayList<>();
        numbers.add(sTestNumberUri);
        HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia = new HashMap<>();
        contactExpectedMedia.put(sTestNumberUri, new Pair<>(true, true));
        // Setup the network response is 200 OK for the first request
        final int networkRespCode200 = 200;
        final String networkRespReasonOK = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode200, networkRespReasonOK);
            cb.onNotifyCapabilitiesUpdate(getPidfForUris(new ArrayList(uris),
                    contactExpectedMedia));
            cb.onTerminated("", 0L);
        });

        // Request contact capabilities
        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the contact capability is received and the onCompleted is called.
        try {
            verifyCapabilities(numbers, getCapabilities(capabilityQueue, numbers.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);
            waitForResult(completeQueue);
        } catch (Exception e) {
            fail("testRequestResultInconclusive with command error failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            capabilityQueue.clear();
            completeQueue.clear();
            numbers.clear();
        }

        // Request the second contacts and this time, the network respons is 408 Request Timeout
        numbers.add(sTestContact2Uri);
        contactExpectedMedia.clear();
        contactExpectedMedia.put(sTestContact2Uri, new Pair<>(false, false));

        final int networkRespCode408 = 408;
        final String networkRespReasonTimeout = "Request Timeout";
        AtomicInteger subscribeRequestCount = new AtomicInteger(0);
        contactExpectedMedia.put(sTestContact2Uri, new Pair<>(true, true));
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            subscribeRequestCount.incrementAndGet();
            cb.onNetworkResponse(networkRespCode408, networkRespReasonTimeout);
        });

        // Request contact capabilities again with different contact
        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the callback "onError" is called with the expected error code.
        try {
            assertEquals(RcsUceAdapter.ERROR_REQUEST_TIMEOUT, waitForIntResult(errorQueue));
            assertEquals(0L, waitForLongResult(errorRetryQueue));
            verifyCapabilities(numbers, getCapabilities(capabilityQueue, numbers.size()),
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);
            assertTrue(subscribeRequestCount.get() > 0);
        } catch (Exception e) {
            fail("testRequestResultInconclusive with command error failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            capabilityQueue.clear();
            completeQueue.clear();
            numbers.clear();
            subscribeRequestCount.set(0);
        }

        // Request three contacts at a time in the third round.
        numbers.add(sTestNumberUri);
        numbers.add(sTestContact2Uri);
        numbers.add(sTestContact3Uri);

        contactExpectedMedia.clear();
        contactExpectedMedia.put(sTestNumberUri, new Pair<>(true, true));
        contactExpectedMedia.put(sTestContact2Uri, new Pair<>(false, false));
        contactExpectedMedia.put(sTestContact3Uri, new Pair<>(true, true));

        // The first two contact capabilities can be retrieved from the cache. However, the third
        // contact capabilities will be provided by the ImsService
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            subscribeRequestCount.incrementAndGet();
            assertNotNull("The uris of capabilities request cannot be null", uris);
            List<Uri> uriList = new ArrayList(uris);
            // Verify that only uri need to be queried from the network
            assertEquals(1, uriList.size());
            assertEquals(sTestContact3Uri, uriList.get(0));
            cb.onNetworkResponse(networkRespCode200, networkRespReasonOK);

            cb.onNotifyCapabilitiesUpdate(getPidfForUris(uriList.subList(0, 1),
                    contactExpectedMedia));
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, numbers, callback);

        List<RcsContactUceCapability> resultCapList;

        // Verify that the contact capability is received and the onCompleted is called.
        try {
            resultCapList = getCapabilities(capabilityQueue, numbers.size());
            verifyCapabilities(Collections.singletonList(sTestNumberUri), resultCapList,
                    SOURCE_TYPE_CACHED, REQUEST_RESULT_FOUND, contactExpectedMedia);

            // The capability information is created as a NON RCS with a SOURCE_TYPE_NETWORK
            // from the throttling list
            verifyCapabilities(Collections.singletonList(sTestContact2Uri), resultCapList,
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND, contactExpectedMedia);

            verifyCapabilities(Collections.singletonList(sTestContact3Uri), resultCapList,
                    SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND, contactExpectedMedia);
            // Verify the onCompleted is called
            waitForResult(completeQueue);

        } catch (Exception e) {
            fail("testRequestResultInconclusive with command error failed: " + e);
        } finally {
            errorQueue.clear();
            errorRetryQueue.clear();
            capabilityQueue.clear();
            completeQueue.clear();
            numbers.clear();
            removeTestContactFromEab();
            removeUceRequestDisallowedStatus();
        }

        overrideCarrierConfig(null);
    }

    @Test
    public void testCapabilitiesRequestWithSipDetails() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Prepare the test contact and the callback
        Collection<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        BlockingQueue<Optional<SipDetails>> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Optional<SipDetails>> errorQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            // ignore this calling
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {}
            // ignore this calling
            @Override
            public void onComplete() {}
            // ignore this calling
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {}
            @Override
            public void onComplete(SipDetails details) {
                completeQueue.offer(Optional.ofNullable(details));
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds, SipDetails details) {
                errorQueue.offer(Optional.ofNullable(details));
            }
        };
        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* options */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        // Start cap exchange disabled and enable later.
        PersistableBundle bundle = new PersistableBundle();
        // Trigger carrier config changed
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        // Override another carrier config KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                true);
        overrideCarrierConfig(bundle);

        // Verify the sip information with 200 OK response.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(new SipDetails.Builder(SipDetails.METHOD_SUBSCRIBE)
                    .setCSeq(1).setSipResponseCode(200, "OK").setCallId("TestCallId").build());
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, numbers, callback);

        Optional<SipDetails> receivedDetails = waitForResult(completeQueue);
        SipDetails receivedInfo = receivedDetails.orElse(null);

        assertNotNull(receivedInfo);
        assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
        assertEquals(1, receivedInfo.getCSeq());
        assertEquals(200, receivedInfo.getResponseCode());
        assertEquals("OK", receivedInfo.getResponsePhrase());
        assertEquals("TestCallId", receivedInfo.getCallId());

        completeQueue.clear();
        removeTestContactFromEab();

        requestAvailability(uceAdapter, sTestNumberUri, callback);

        receivedDetails = waitForResult(completeQueue);
        receivedInfo = receivedDetails.orElse(null);
        assertNotNull(receivedInfo);
        assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
        assertEquals(1, receivedInfo.getCSeq());
        assertEquals(200, receivedInfo.getResponseCode());
        assertEquals("OK", receivedInfo.getResponsePhrase());
        assertEquals("TestCallId", receivedInfo.getCallId());

        completeQueue.clear();
        removeTestContactFromEab();

        // Verify the sip information with 404 NOT FOUND response.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(new SipDetails.Builder(SipDetails.METHOD_SUBSCRIBE)
                    .setCSeq(2).setSipResponseCode(404, "NOT FOUND")
                    .setCallId("TestCallId1").build());
        });

        requestCapabilities(uceAdapter, numbers, callback);

        receivedDetails = waitForResult(completeQueue);
        receivedInfo = receivedDetails.orElse(null);

        assertNotNull(receivedInfo);
        assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
        assertEquals(2, receivedInfo.getCSeq());
        assertEquals(404, receivedInfo.getResponseCode());
        assertEquals("NOT FOUND", receivedInfo.getResponsePhrase());
        assertEquals("TestCallId1", receivedInfo.getCallId());

        completeQueue.clear();
        removeTestContactFromEab();

        requestAvailability(uceAdapter, sTestNumberUri, callback);

        receivedDetails = waitForResult(completeQueue);
        receivedInfo = receivedDetails.orElse(null);

        assertNotNull(receivedInfo);
        assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
        assertEquals(2, receivedInfo.getCSeq());
        assertEquals(404, receivedInfo.getResponseCode());
        assertEquals("NOT FOUND", receivedInfo.getResponsePhrase());
        assertEquals("TestCallId1", receivedInfo.getCallId());

        completeQueue.clear();
        removeTestContactFromEab();

        // Verify the sip information with failure response except 404 NOT FOUND.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(new SipDetails.Builder(SipDetails.METHOD_SUBSCRIBE)
                    .setCSeq(3).setSipResponseCode(500, "Internal Server Error")
                    .setCallId("TestCallId2").build());
        });

        requestCapabilities(uceAdapter, numbers, callback);

        receivedDetails = waitForResult(errorQueue);
        receivedInfo = receivedDetails.orElse(null);

        assertNotNull(receivedInfo);
        assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
        assertEquals(3, receivedInfo.getCSeq());
        assertEquals(500, receivedInfo.getResponseCode());
        assertEquals("Internal Server Error", receivedInfo.getResponsePhrase());
        assertEquals("TestCallId2", receivedInfo.getCallId());

        removeUceRequestDisallowedStatus();
        errorQueue.clear();

        // Verify the sip information when error for a request
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onCommandError(COMMAND_CODE_SERVICE_UNKNOWN);
        });

        requestCapabilities(uceAdapter, numbers, callback);

        receivedDetails = waitForResult(errorQueue);
        receivedInfo = receivedDetails.orElse(null);

        assertNull(receivedInfo);
        errorQueue.clear();
        removeUceRequestDisallowedStatus();
        overrideCarrierConfig(null);
    }

    @Test
    public void testCapabilitiesRequestWithoutSipDetails() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Prepare the test contact and the callback
        Collection<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        BlockingQueue<SipDetails> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<SipDetails> errorQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {}
            @Override
            public void onComplete() {}
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {}
            @Override
            public void onComplete(SipDetails details) {
                completeQueue.offer(details);
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds, SipDetails details) {
                errorQueue.offer(details);
            }
        };
        // Connect to the ImsService
        setupTestImsService(uceAdapter, true, true /* presence cap */, false /* options */);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        // Start cap exchange disabled and enable later.
        PersistableBundle bundle = new PersistableBundle();
        // Trigger carrier config changed
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        // Override another carrier config KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                true);
        overrideCarrierConfig(bundle);

        // Verify the sip information with 200 OK response.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(200, "OK");
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, numbers, callback);

        SipDetails receivedInfo = waitForResult(completeQueue);
        assertNotNull(receivedInfo);
        assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
        assertEquals(200, receivedInfo.getResponseCode());
        assertEquals("OK", receivedInfo.getResponsePhrase());

        completeQueue.clear();
        removeTestContactFromEab();

        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(404, "NOT FOUND");
        });
        requestAvailability(uceAdapter, sTestNumberUri, callback);

        receivedInfo = waitForResult(completeQueue);
        assertNotNull(receivedInfo);
        assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
        assertEquals(404, receivedInfo.getResponseCode());
        assertEquals("NOT FOUND", receivedInfo.getResponsePhrase());

        completeQueue.clear();
        removeTestContactFromEab();

        // Verify the sip information with failure response except 404 NOT FOUND.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(500, "Internal Server Error");
        });

        requestCapabilities(uceAdapter, numbers, callback);

        receivedInfo = waitForResult(errorQueue);
        assertNotNull(receivedInfo);
        assertEquals(SipDetails.METHOD_SUBSCRIBE, receivedInfo.getMethod());
        assertEquals(500, receivedInfo.getResponseCode());
        assertEquals("Internal Server Error", receivedInfo.getResponsePhrase());

        errorQueue.clear();
        removeUceRequestDisallowedStatus();

        overrideCarrierConfig(null);
    }

    private void setupTestImsService(RcsUceAdapter uceAdapter, boolean presencePublishEnabled,
            boolean presenceCapExchangeEnabled, boolean sipOptionsEnabled) throws Exception {
        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL,
                presencePublishEnabled);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                presenceCapExchangeEnabled);
        bundle.putBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, sipOptionsEnabled);
        overrideCarrierConfig(bundle);

        // Connect to the TestImsService
        connectTestImsService();
    }

    private String getPidfXmlData(Uri contact, boolean audioSupported, boolean videoSupported) {
        StringBuilder pidfBuilder = new StringBuilder();
        pidfBuilder.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"").append(contact).append("\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_01</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test1</op:description>")
                .append("</op:service-description>")
                .append("<caps:servcaps>")
                .append("<caps:audio>").append(audioSupported).append("</caps:audio>")
                .append("<caps:video>").append(videoSupported).append("</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>").append(contact).append("</contact>")
                .append("</tuple></presence>");
        return pidfBuilder.toString();
    }

    private String getMalformedPidfXmlData(Uri contact, boolean audioSupported,
            boolean videoSupported) {
        StringBuilder pidfBuilder = new StringBuilder();
        pidfBuilder.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"").append(contact).append("\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_01</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test1</op:description>")
                .append("</op:service-description>")
                .append("<caps:servcaps>")
                .append("<caps:audio>").append(audioSupported).append("</caps:audio>")
                .append("<caps:video>").append(videoSupported).append("</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>").append(contact).append("</contact>")
                .append("</tuple>")
                .append("<tuple id=\"tid1\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_02</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:ddescription>description_test2</op:description>")
                .append("</op:service-description>")
                .append("<contact>").append(contact).append("</contact>")
                .append("</tuple></presence>");
        return pidfBuilder.toString();
    }

    private RcsContactUceCapability getContactCapability(
            List<RcsContactUceCapability> resultCapList, Uri targetUri) {
        if (resultCapList == null) {
            return null;
        }
        return resultCapList.stream()
            .filter(capability -> targetUri.equals(capability.getContactUri()))
            .findFirst()
            .orElse(null);
    }

    private void verifyCapabilityResult(RcsContactUceCapability resultCapability, Uri expectedUri,
            int expectedSourceType, int expectedResult, boolean expectedAudioSupported,
            boolean expectedVideoSupported) {
        // Verify the contact URI
        assertEquals(expectedUri, resultCapability.getContactUri());

        // Verify the source type is the network type.
        assertEquals(expectedSourceType,
                resultCapability.getSourceType());

        // Verify the request result is expected.
        final int requestResult = resultCapability.getRequestResult();
        assertEquals(expectedResult, requestResult);

        // Return directly if the result is not found.
        if (requestResult == REQUEST_RESULT_NOT_FOUND) {
            return;
        }

        // Verify the mechanism is presence
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE,
                resultCapability.getCapabilityMechanism());

        RcsContactPresenceTuple presenceTuple =
                resultCapability.getCapabilityTuple("service_id_01");
        assertNotNull("Contact Presence tuple should not be null!", presenceTuple);

        ServiceCapabilities capabilities = presenceTuple.getServiceCapabilities();
        assertNotNull("Service capabilities should not be null!", capabilities);

        // Verify if the audio is supported
        assertEquals(expectedAudioSupported, capabilities.isAudioCapable());

        // Verify if the video is supported
        assertEquals(expectedVideoSupported, capabilities.isVideoCapable());
    }

    private void verifyMalformedCapabilityResult(RcsContactUceCapability resultCapability,
            Uri expectedUri, int expectedSourceType, int expectedResult,
            boolean expectedAudioSupported, boolean expectedVideoSupported) {
        // Verify the contact URI
        assertEquals(expectedUri, resultCapability.getContactUri());

        // Verify the source type is the network type.
        assertEquals(expectedSourceType, resultCapability.getSourceType());

        // Verify the request result is expected.
        final int requestResult = resultCapability.getRequestResult();
        assertEquals(expectedResult, requestResult);

        // Return directly if the result is not found.
        if (requestResult == REQUEST_RESULT_NOT_FOUND) {
            return;
        }

        // Verify the mechanism is presence
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE,
                resultCapability.getCapabilityMechanism());

        // First tuple is malformed. Verify that no malformed tuple is stored.
        RcsContactPresenceTuple presenceTuple =
                resultCapability.getCapabilityTuple("service_id_02");
        assertNull("Contact Presence tuple should be null!", presenceTuple);

        presenceTuple = resultCapability.getCapabilityTuple("service_id_01");
        assertNotNull("Contact Presence tuple should not be null!", presenceTuple);

        RcsContactPresenceTuple.ServiceCapabilities capabilities =
                presenceTuple.getServiceCapabilities();
        assertNotNull("Service capabilities should not be null!", capabilities);

        // Verify if the audio is supported
        assertEquals(expectedAudioSupported, capabilities.isAudioCapable());

        // Verify if the video is supported
        assertEquals(expectedVideoSupported, capabilities.isVideoCapable());
    }

    private void verifyOptionsCapabilityResult(List<RcsContactUceCapability> resultCapList,
            Collection<Uri> expectedUriList, int expectedSourceType, int expectedMechanism,
            int expectedResult, List<String> expectedFeatureTags) {
        assertEquals(resultCapList.size(), expectedUriList.size());

        assertTrue(resultCapList.stream().map(capability -> capability.getContactUri())
                .anyMatch(expectedUriList::contains));

        resultCapList.stream().map(capability -> capability.getSourceType())
                .forEach(sourceType -> assertEquals((int) sourceType, (int) expectedSourceType));

        resultCapList.stream().map(capability -> capability.getCapabilityMechanism())
                .forEach(mechanism -> assertEquals((int) mechanism, (int) expectedMechanism));

        resultCapList.stream().map(capability -> capability.getRequestResult())
                .forEach(result -> assertEquals((int) result, (int) expectedResult));

        resultCapList.stream().map(capability -> capability.getFeatureTags())
                .forEach(featureTags -> {
                    assertEquals((int) featureTags.size(), (int) expectedFeatureTags.size());
                    assertTrue(featureTags.containsAll(expectedFeatureTags));
                });
    }

    private void registerUceObserver(Consumer<Uri> resultConsumer) {
        mUceObserver = new ContentObserver(new Handler(sHandlerThread.getLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                resultConsumer.accept(uri);
            }
        };
        getContext().getContentResolver().registerContentObserver(LISTENER_URI,
                true /*notifyForDecendents*/, mUceObserver);
    }

    private void unregisterUceObserver() {
        if (mUceObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mUceObserver);
        }
    }

    private int waitForIntResult(BlockingQueue<Integer> queue) throws Exception {
        Integer result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return result != null ? result : Integer.MAX_VALUE;
    }

    private long waitForLongResult(BlockingQueue<Long> queue) throws Exception {
        Long result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return result != null ? result : Long.MAX_VALUE;
    }

    private <T> T waitForResult(BlockingQueue<T> queue) throws Exception {
        return queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private void connectTestImsService() throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
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
        assertTrue("Did not receive RcsFeature#setCapabilityExchangeEventListener",
                sServiceConnector.getCarrierService().waitForLatchCountdown(
                        TestImsService.LATCH_UCE_LISTENER_SET));
        int serviceSlot = sServiceConnector.getCarrierService().getRcsFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated RcsFeature",
                sTestSlot, serviceSlot);
    }

    private static void initPhoneNumbers() {
        // Generate a random phone number
        sTestPhoneNumber = generateRandomPhoneNumber();
        sTestNumberUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, sTestPhoneNumber, null);

        sTestContact2 = generateRandomContact(5);
        sTestContact2Uri = Uri.fromParts(PhoneAccount.SCHEME_SIP, sTestContact2, null);

        sTestContact3 = generateRandomContact(6);
        sTestContact3Uri = Uri.fromParts(PhoneAccount.SCHEME_SIP, sTestContact3, null);

        sTestContact4 = generateRandomContact(7);
        sTestContact4Uri = Uri.fromParts(PhoneAccount.SCHEME_SIP, sTestContact4, null);
    }

    private static String generateRandomPhoneNumber() {
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private static String generateRandomContact(int length) {
        Random random = new Random();
        StringBuilder builder = new StringBuilder("TestContact");
        for (int i = 0; i < length; i++) {
            int asciiNum = random.nextInt(26) + 65;  // ascii 65
            builder.append((char) asciiNum);
        }
        return builder.toString();
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
            StringBuilder builder = new StringBuilder();
            builder.append(sTestPhoneNumber)
                    .append(",").append(sTestContact2)
                    .append(",").append(sTestContact3)
                    .append(",").append(sTestContact4);
            sServiceConnector.removeEabContacts(sTestSlot, builder.toString());
        } catch (Exception e) {
            Log.w("RcsUceAdapterTest", "Cannot remove test contacts from eab database: " + e);
        }
    }

    private static void removeUceRequestDisallowedStatus() {
        try {
            sServiceConnector.removeUceRequestDisallowedStatus(sTestSlot);
        } catch (Exception e) {
            Log.w("RcsUceAdapterTest", "Cannot remove request disallowed status: " + e);
        }
    }

    private static void setCapabilitiesRequestTimeout(long timeoutAfterMillis) {
        try {
            sServiceConnector.setCapabilitiesRequestTimeout(sTestSlot, timeoutAfterMillis);
        } catch (Exception e) {
            Log.w("RcsUceAdapterTest", "Cannot set capabilities request timeout: " + e);
        }
    }

    private void requestCapabilities(RcsUceAdapter uceAdapter, Collection<Uri> numbers,
            RcsUceAdapter.CapabilitiesCallback callback) {
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter,
                    adapter -> adapter.requestCapabilities(numbers, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE. "
                    + "Exception: " + e);
        } catch (ImsException e) {
            fail("requestCapabilities failed " + e);
        }
    }

    private void requestAvailability(RcsUceAdapter uceAdapter, Uri number,
            RcsUceAdapter.CapabilitiesCallback callback) {
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter,
                    adapter -> adapter.requestAvailability(number, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestAvailability should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE. "
                    + "Exception: " + e);
        } catch (ImsException e) {
            fail("requestAvailability failed " + e);
        }
    }

    private void verifyCapabilities(Collection<Uri> contacts,
            List<RcsContactUceCapability> resultCapList,
            int expectedSourceType, int expectedResult,
            HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia) {

        for (Uri uri : contacts) {
            RcsContactUceCapability resultCapability = getContactCapability(resultCapList, uri);
            assertNotNull("Cannot find the contact: " + uri, resultCapability);
            Pair<Boolean, Boolean> pair = contactExpectedMedia.get(uri);
            assertNotNull("Expected media type is not matched with uri", pair);
            verifyCapabilityResult(resultCapability, uri, expectedSourceType,
                    expectedResult, pair.first, pair.second);
        }
    }

    private void verifyCapabilityReceived(Uri contact,
            BlockingQueue<RcsContactUceCapability> capabilityQueue,
            int expectedSourceType, int expectedResult,
            boolean audioSupported, boolean videoSupported) throws Exception {

        RcsContactUceCapability capability = waitForResult(capabilityQueue);
        assertNotNull("Can not receive capabilities result", capability);

        assertEquals(contact, capability.getContactUri());
        verifyCapabilityResult(capability, contact, expectedSourceType, expectedResult,
                audioSupported, videoSupported);
    }

    private List<RcsContactUceCapability> getCapabilities(
            BlockingQueue<RcsContactUceCapability> capabilityQueue, int size) throws Exception {
        List<RcsContactUceCapability> resultCapList = new ArrayList<>();
        if (size == 0) {
            return resultCapList;
        }
        for (int index = 0; index < size; index++) {
            RcsContactUceCapability capability = waitForResult(capabilityQueue);
            assertNotNull("Can not receive capabilities result.", capability);
            resultCapList.add(capability);
        }
        return resultCapList;
    }

    private List<String> getPidfForUris(List<Uri> uris,
            HashMap<Uri, Pair<Boolean, Boolean>> contactExpectedMedia) {
        ArrayList<String> pidfXmlList = new ArrayList<>(uris.size());
        for (Uri uri : uris) {
            Pair<Boolean, Boolean> expectedMedia = contactExpectedMedia.get(uri);
            assertNotNull("unexpected URI", expectedMedia);
            String pidf = getPidfXmlData(uri, expectedMedia.first, expectedMedia.second);
            assertNotNull("no pidf found for URI", pidf);
            pidfXmlList.add(pidf);
        }
        return pidfXmlList;
    }
}
