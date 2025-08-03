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

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.InetAddresses;
import android.net.Uri;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsService;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipDialogState;
import android.telephony.ims.SipDialogStateCallback;
import android.telephony.ims.SipMessage;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * CTS tests for {@link SipDelegateManager} API.
 */
@RunWith(AndroidJUnit4.class)
public class SipDelegateManagerTest {
    private static final String MMTEL_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"";
    private static final String ONE_TO_ONE_CHAT_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gppservice.ims.icsi.oma.cpm.msg\"";
    private static final String GROUP_CHAT_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gppservice.ims.icsi.oma.cpm.session\"";
    private static final String FILE_TRANSFER_HTTP_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gppapplication.ims.iari.rcs.fthttp\"";
    private static final String CHATBOT_COMMUNICATION_USING_SESSION_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.chatbot\"";
    private static final String[] DEFAULT_FEATURE_TAGS = {
            ONE_TO_ONE_CHAT_TAG, GROUP_CHAT_TAG, FILE_TRANSFER_HTTP_TAG,
            CHATBOT_COMMUNICATION_USING_SESSION_TAG};

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

    /**
     * Encapsulates the interfaces created during SipDelegateManager testing.
     */
    public class TransportInterfaces {
        public final DelegateRequest request;
        public final Set<FeatureTagState> deniedTags;
        public final SipDelegateManager manager;
        public TestSipTransport transport;
        public TestImsRegistration reg;
        public TestSipDelegate delegate;
        public TestSipDelegateConnection delegateConn;
        private final int mDelegateIndex;

        public TransportInterfaces(DelegateRequest request, Set<FeatureTagState>  deniedTags,
                int delegateIndex) {
            this.request = request;
            this.deniedTags = deniedTags;
            manager = getSipDelegateManager();
            mDelegateIndex = delegateIndex;
        }

        public void connect() throws Exception {
            assertTrue(sServiceConnector.setDefaultSmsApp());
            connectTestImsServiceWithSipTransportAndConfig();

            transport = sServiceConnector.getCarrierService().getSipTransport();
            reg = sServiceConnector.getCarrierService().getImsRegistration();
            delegateConn = new TestSipDelegateConnection(request);

            delegate = createSipDelegateConnectionAndVerify(manager, delegateConn,
                    transport, deniedTags, mDelegateIndex);
            assertNotNull(delegate);
            // ensure we got a callback for initial reg state.
            verifyUpdateRegistrationCalled(reg);

            InetSocketAddress localAddr = new InetSocketAddress(
                    InetAddresses.parseNumericAddress("1.1.1.1"), 80);
            InetSocketAddress serverAddr = new InetSocketAddress(
                    InetAddresses.parseNumericAddress("2.2.2.2"), 81);
            SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                    SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
            // send first SIP config and verify
            verifyRegisteredAndSendSipConfig(delegateConn, delegate, request.getFeatureTags(),
                    deniedTags, c);
        }

        /**
         * Create a connection between fake app interface and fake ImsService impl and set up the
         * framework to accept incoming/outgoing messages. Once done, verify the transport is open.
         */
        public void connectAndVerify() throws Exception {
            connect();

            // Verify message transport is open.
            verifyOutgoingTransport(delegateConn, delegate);
            verifyIncomingTransport(delegateConn, delegate);
        }
    }

    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;
    private static int sUpdatedStateSize = 0;
    private static boolean sUpdatedState = false;

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        // First, only populate test slot/sub
        if (!ImsUtils.shouldTestTelephony()) {
            return;
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            return;
        }
        // Next, only start tests that require ImsResolver to bind to test ImsService if
        // feature FEATURE_TELEPHONY_IMS is supported on this device.
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(sTestSlot);

        sReceiver = new CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);

        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            // override FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION setting for this test to enable
            // APIs.
            sServiceConnector.setDeviceSingleRegistrationEnabled(true);
        }

        setFeatureTagsCarrierAllowed(DEFAULT_FEATURE_TAGS);
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        // Only clean up ImsResolver overrides if feature FEATURE_TELEPHONY_IMS is supported.
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Restore all ImsService configurations that existed before the test.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectServices();
        }
        sServiceConnector = null;

        // Ensure there are no CarrierConfig overrides as well as reset the ImsResolver in case the
        // ImsService override changed in CarrierConfig while we were overriding it.
        overrideCarrierConfig(null);

        if (sReceiver != null) {
            InstrumentationRegistry.getInstrumentation().getContext().unregisterReceiver(sReceiver);
            sReceiver = null;
        }
    }

    @Before
    public void beforeTest() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            // If the device does not have FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION,
            // skip the all tests
            assumeTrue(ImsUtils.shouldTestImsSingleRegistration());
        } else {
            if (!ImsUtils.shouldTestTelephony()) {
                return;
            }
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
    }

    @After
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Unbind the ImsService after the test completes only if feature FEATURE_TELEPHONY_IMS
        // is enabled.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.restoreDefaultSmsApp();
        }
    }

    @Test
    // Note this test can run on devices with only feature FEATURE_TELEPHONY, so ImsResolver may not
    // be running.
    public void testIncorrectPermissions() throws Exception {
        if (!ImsUtils.shouldTestTelephony()) {
            return;
        }
        SipDelegateManager manager = getSipDelegateManager();
        try {
            manager.isSupported();
            fail("isSupported requires READ_PRIVILEGED_PHONE_STATE or "
                    + "PERFORM_IMS_SINGLE_REGISTRATION");
        } catch (SecurityException e) {
            //expected
        }
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    manager, SipDelegateManager::isSupported, ImsException.class,
                    "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
        } catch (ImsException e) {
            // Not a problem, only checking permissions here.
        } catch (SecurityException e) {
            fail("isSupported requires READ_PRIVILEGED_PHONE_STATE or "
                    + "PERFORM_IMS_SINGLE_REGISTRATION, exception:" + e);
        }
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    manager, SipDelegateManager::isSupported, ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");

        } catch (ImsException e) {
            // Not a problem, only checking permissions here.
        } catch (SecurityException e) {
            fail("isSupported requires READ_PRIVILEGED_PHONE_STATE or "
                    + "PERFORM_IMS_SINGLE_REGISTRATION, exception:" + e);
        }

        DelegateRequest d = new DelegateRequest(Collections.emptySet());
        TestSipDelegateConnection c = new TestSipDelegateConnection(d);
        try {
            manager.createSipDelegate(d, Runnable::run, c, c);
            fail("createSipDelegate requires PERFORM_IMS_SINGLE_REGISTRATION");
        } catch (SecurityException e) {
            //expected
        }


        ImsStateCallback callback = new ImsStateCallback() {
            @Override
            public void onUnavailable(int reason) { }
            @Override
            public void onAvailable() { }
            @Override
            public void onError() { }
        };

        try {
            manager.registerImsStateCallback(Runnable::run, callback);
            fail("registerImsStateCallback requires PERFORM_IMS_SINGLE_REGISTRATION or "
                    + "READ_PRIVILEGED_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        } catch (ImsException ignore) {
            fail("registerImsStateCallback requires PERFORM_IMS_SINGLE_REGISTRATION or "
                    + "READ_PRIVILEGED_PHONE_STATE permission.");
        }

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(manager,
                    m -> m.registerImsStateCallback(Runnable::run, callback),
                    ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
        } catch (SecurityException e) {
            fail("registerImsStateCallback requires PERFORM_IMS_SINGLE_REGISTRATION permission.");
        } catch (ImsException ignore) {
            // don't care, permission check passed
        }

        try {
            manager.unregisterImsStateCallback(callback);
        } catch (SecurityException e) {
            fail("uregisterImsStateCallback requires no permission.");
        }

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(manager,
                    m -> m.registerImsStateCallback(Runnable::run, callback),
                    ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("registerImsStateCallback requires READ_PRIVILEGED_PHONE_STATE permission.");
        } catch (ImsException ignore) {
            // don't care, permission check passed
        }

        try {
            manager.unregisterImsStateCallback(callback);
        } catch (SecurityException e) {
            // unreachable, already passed permission check
            fail("uregisterImsStateCallback requires no permission.");
        }
    }

    @Test
    public void testSipDialogStateChanges() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        SipDialogStateCallback callback = new SipDialogStateCallback() {
            @Override
            public void onActiveSipDialogsChanged(List<SipDialogState> dialogs) {
                int confirmedSize = dialogs.stream().filter(
                        d -> d.getState() == SipDialogState.STATE_CONFIRMED)
                        .collect(Collectors.toSet()).size();
                if (confirmedSize > 0) {
                    sUpdatedState = true;
                } else {
                    sUpdatedState = false;
                }
            }
            @Override
            public void onError() { }
        };

        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(ifaces.manager,
                    m -> m.registerSipDialogStateCallback(Runnable::run, callback),
                    ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("registerSipDialogStateCallback requires READ_PRIVILEGED_PHONE_STATE permission.");
        } catch (ImsException ignore) {
            // don't care, permission check passed
        }
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(1000);
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        assertFalse(sUpdatedState);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(1000);
        // SipDialog Confirmed
        assertTrue(sUpdatedState);
        // Send ACK
        sendAck(attr, ifaces);
        // Send BYE
        sendByeRequest(attr, ifaces);
        // SipDialog Closed
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(1000);
        assertFalse(sUpdatedState);

        // Send the cleanup, which will trigger destroy to complete.
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(1000);
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(ifaces.manager,
                    m -> m.unregisterSipDialogStateCallback(callback),
                    ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("registerSipDialogStateCallback requires READ_PRIVILEGED_PHONE_STATE permission.");
        }
        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testSipDialogStateChangesOnMultipleDelegates() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();

        SipDialogStateCallback callback = new SipDialogStateCallback() {
            @Override
            public void onActiveSipDialogsChanged(List<SipDialogState> dialogs) {
                sUpdatedStateSize = dialogs.stream()
                        .filter(d -> d.getState() == SipDialogState.STATE_CONFIRMED)
                        .collect(Collectors.toList()).size();
            }
            @Override
            public void onError() { }
        };

        SipDelegateManager manager = getSipDelegateManager();
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();
        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();

        // create delegate#1
        DelegateRequest request1 = getChatOnlyRequest();
        TestSipDelegateConnection delegateConn1 = new TestSipDelegateConnection(request1);
        TestSipDelegate delegate1 = createSipDelegateConnectionAndVerify(manager, delegateConn1,
                transportImpl, Collections.emptySet(), 0);
        Set<String> registeredTags1 = new ArraySet<>(request1.getFeatureTags());
        assertNotNull(delegate1);
        verifyRegisteredAndSendSipConfig(delegateConn1, delegate1, registeredTags1,
                Collections.emptySet(), c);
        //delegate : 1, dialog confirmed : 0
        assertEquals(0, sUpdatedStateSize);
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(manager,
                    m -> m.registerSipDialogStateCallback(Runnable::run, callback),
                    ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("registerSipDialogStateCallback requires READ_PRIVILEGED_PHONE_STATE permission.");
        } catch (ImsException ignore) {
            // don't care, permission check passed
        }
        // INVITE/200OK on Delegate#1
        SipDialogAttributes attr = new SipDialogAttributes();
        SipDialogAttributes invAttr = attr.fromExisting().copyWithNewBranch();
        invAttr.addAcceptContactTag(GROUP_CHAT_TAG);
        SipMessage invite = SipMessageUtils.generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD,
                invAttr);
        delegateConn1.sendMessageAndVerifyCompletedSuccessfully(invite);
        SipMessage resp = SipMessageUtils.generateSipResponse("200", "OK",
                attr);
        delegate1.receiveMessageAndVerifyReceivedCalled(resp);
        delegateConn1.verifyMessageReceived(resp);
        delegateConn1.setOperationCountDownLatch(1);
        delegateConn1.waitForCountDown(/*ImsUtils.TEST_TIMEOUT_MS*/1000);
        //delegate : 1, dialog confirmed : 1
        assertEquals(1, sUpdatedStateSize);

        // create delegate#2
        DelegateRequest request2 = getDefaultRequest();
        TestSipDelegateConnection delegateConn2 = new TestSipDelegateConnection(request2);
        Set<String> registeredTags2 = new ArraySet<>(request2.getFeatureTags());
        TestSipDelegate delegate2 = createSipDelegateConnectionAndVerify(manager, delegateConn2,
                transportImpl, Collections.emptySet(), 1);
        assertNotNull(delegate2);
        verifyUpdateRegistrationCalled(regImpl);
        Set<FeatureTagState> deniedSet = generateDeniedSetFromRequest(request1.getFeatureTags(),
                request2.getFeatureTags(),
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE);
        verifyRegisteredAndSendSipConfig(delegateConn2, delegate2, registeredTags2, deniedSet, c);
        // INVITE/200OK on Delegate#2
        SipDialogAttributes attr2 = new SipDialogAttributes();
        SipDialogAttributes invAttr2 = attr2.fromExisting().copyWithNewBranch();
        invAttr2.addAcceptContactTag(CHATBOT_COMMUNICATION_USING_SESSION_TAG);
        SipMessage invite2 = SipMessageUtils.generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD,
                invAttr2);
        delegateConn2.sendMessageAndVerifyCompletedSuccessfully(invite2);
        SipMessage resp2 = SipMessageUtils.generateSipResponse("200", "OK",
                attr2);
        delegate2.receiveMessageAndVerifyReceivedCalled(resp2);
        delegateConn2.verifyMessageReceived(resp2);
        delegateConn2.setOperationCountDownLatch(1);
        delegateConn2.waitForCountDown(/*ImsUtils.TEST_TIMEOUT_MS*/1000);
        //delegate : 2, dialog confirmed : 2
        assertEquals(2, sUpdatedStateSize);
        // Destroying delegate 1 will transfer all feature tags over to delegate 2
        destroySipDelegate(manager, transportImpl, delegateConn1, delegate1);
        // If one delegate is destroyed in multiple delegates,
        // it internally triggers the destruction of the remaining delegates
        // and then the recreation of another delegate with the new feature set that it supports.
        verifySipDelegateDestroyed(transportImpl, delegate2);
        delegateConn2.setOperationCountDownLatch(1);
        delegateConn2.waitForCountDown(/*ImsUtils.TEST_TIMEOUT_MS*/1000);
        //delegate : 1, dialog confirmed : 0
        assertEquals(0, sUpdatedStateSize);
        delegate2 = getSipDelegate(transportImpl, Collections.emptySet(), 0);
        verifyUpdateRegistrationCalled(regImpl);
        verifyRegisteredAndSendSipConfig(delegateConn2, delegate2, request2.getFeatureTags(),
                Collections.emptySet(), c);
        destroySipDelegateAndVerifyConnDestroyed(manager, transportImpl, delegateConn2, delegate2);
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
    }

    @Test
    // Note this test can run on devices with only feature FEATURE_TELEPHONY, so ImsResolver may not
    // be running.
    public void testFeatureImsNotSupported() throws Exception {
        if (!ImsUtils.shouldTestTelephony()) {
            return;
        }

        if (sServiceConnector != null) {
            // Override FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION for this test so that telephony
            // will report not enabled.
            sServiceConnector.setDeviceSingleRegistrationEnabled(false);
        }

        try {
            SipDelegateManager manager = getSipDelegateManager();

            try {
                // If FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION is not supported this should
                // return false.
                Boolean result = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        manager, SipDelegateManager::isSupported, ImsException.class,
                        "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
                assertNotNull(result);
                assertFalse("isSupported should return false on devices that do not "
                        + "support feature FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION", result);
            } catch (SecurityException e) {
                fail("isSupported requires PERFORM_IMS_SINGLE_REGISTRATION permission");
            }

            try {
                // If FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION is not supported, this should throw
                // an ImsException
                DelegateRequest request = new DelegateRequest(Collections.emptySet());
                TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                        manager, (m) -> m.createSipDelegate(request, Runnable::run,
                                delegateConn, delegateConn), ImsException.class,
                        "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
                fail("createSipDelegate should throw an ImsException with code "
                        + "CODE_ERROR_UNSUPPORTED_OPERATION on devices that do not support feature "
                        + "FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION");
            } catch (SecurityException e) {
                fail("isSupported requires PERFORM_IMS_SINGLE_REGISTRATION permission");
            } catch (ImsException e) {
                // expecting CODE_ERROR_UNSUPPORTED_OPERATION
                if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                    fail("createSipDelegate should throw an ImsException with code "
                            + "CODE_ERROR_UNSUPPORTED_OPERATION on devices that do not support "
                            + "feature FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION");
                }
            }
        } finally {
            if (sServiceConnector != null) {
                // restore override for the rest of the tests.
                sServiceConnector.setDeviceSingleRegistrationEnabled(true);
            }
        }
    }

    @Test
    public void testIsSupportedWithSipTransportCapable() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);
        connectTestImsServiceWithSipTransport();

        SipDelegateManager manager = getSipDelegateManager();
        Boolean result = null;
        try {
            result = callUntilImsServiceIsAvailable(() ->
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(manager,
                            SipDelegateManager::isSupported, ImsException.class,
                            "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        } catch (SecurityException e) {
            fail("isSupported requires PERFORM_IMS_SINGLE_REGISTRATION permission");
        }
        assertNotNull(result);
        assertTrue("isSupported should return true", result);
    }

    @Test
    public void testIsSupportedWithSipTransportCapableCarrierConfigNotSet() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        // Carrier Config is explicitly set to not support single registration.
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        overrideCarrierConfig(b);
        connectTestImsServiceWithSipTransport();

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        assertNotNull(result);
        assertFalse("isSupported should return false if"
                + "CarrierConfigManager.Ims.KEY_RCS_SINGLE_REGISTRATION_REQUIRED_BOOL is set to "
                + "false", result);
    }

    @Ignore("Disabling for integration b/175766573")
    @Test
    public void testIsSupportedWithSipTransportCapableOnlyRcs() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        // set SipTransport as supported with RCS only attached.
        sServiceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);
        sServiceConnector.getCarrierService().setSipTransportImplemented();

        ImsFeatureConfiguration c = getConfigForRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that the ImsService is only "
                + "attached for RCS and not MMTEL and RCS", result);
    }


    @Test
    public void testIsSupportedWithSipTransportCapableButNotImplemented() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        // SipTransport set as capable, but no SipTransport implementation is returned.
        sServiceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that SipTransport is not "
                + "implemented", result);
    }

    @Test
    public void testIsSupportedWithSipTransportImplementedButNotCapable() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        // SipTransport is set as Implemented, but not Capable
        sServiceConnector.getCarrierService().setSipTransportImplemented();
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that SipTransport is not "
                + "set as capable in ImsService#getImsServiceCapabilities", result);
    }

    @Test
    public void testIsSupportedWithSipTransportNotImplementedNotCapable() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        // Not Implemented/capable
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that SipTransport is not "
                + "set as capable in ImsService#getImsServiceCapabilities", result);
    }

    @Test
    public void testCreateDestroyDelegateNotDefaultMessagingApp() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        connectTestImsServiceWithSipTransportAndConfig();

        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration imsReg = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);

        // wait for onCreated and registration state change to be called.
        createSipDelegateConnectionNoDelegateExpected(manager, delegateConn, transportImpl);

        // TODO deal with this case better when we can filter messages.
        delegateConn.sendMessageAndVerifyFailure(SipMessageUtils.TEST_SIP_MESSAGE,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD);

        delegateConn.triggerFullNetworkRegistration(manager, 403, "FORBIDDEN");
        // wait 5 seconds, this should not return.
        TestImsRegistration.NetworkRegistrationInfo info =
                imsReg.getNextFullNetworkRegRequest(5000);
        assertNull("If there is no valid SipTransport, this should not be called", info);

        destroySipDelegateConnectionNoDelegate(manager, delegateConn);
    }

    @Test
    public void testCreateDelegateBasicUseCases() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connectAndVerify();

        // Ensure requests to perform a full network re-registration work properly.
        verifyFullRegistrationTriggered(ifaces);

        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testImsServiceDisconnected() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();

        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);

        TestSipDelegate delegate = createSipDelegateConnectionAndVerify(manager, delegateConn,
                transportImpl, Collections.emptySet(), 0);
        assertNotNull(delegate);
        verifyUpdateRegistrationCalled(regImpl);

        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        verifyRegisteredAndSendSipConfig(delegateConn, delegate, request.getFeatureTags(),
                Collections.emptySet(), c);

        verifyOutgoingTransport(delegateConn, delegate);
        verifyIncomingTransport(delegateConn, delegate);

        sServiceConnector.disconnectCarrierImsService();
        // unbind ImsService suddenly and wait for on destroyed
        delegateConn.setOperationCountDownLatch(1);
        transportImpl.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
    }

    @Test
    public void testCreateDelegateTestInvalidSipMessages() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connectAndVerify();

        // Verify restricted SIP request methods are not sent to the delegate.
        sendRestrictedRequestsAndVerifyFailed(ifaces.delegateConn);
        // Verify malformed messages are not sent to the delegate.
        sendInvalidRequestsAndVerifyFailed(ifaces.delegateConn);

        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testDelegateRegistrationChanges() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        try {
            TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.SUPPORT_REGISTERING_DELEGATE_STATE_STRING);
            TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.SUPPORT_DEREGISTERING_LOSING_PDN_STATE_STRING);

            TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                    Collections.emptySet(), 0);
            ifaces.connectAndVerify();
            Set<String> registeredTags = new ArraySet<>(ifaces.request.getFeatureTags());

            // move reg state to registering, deregistering and then deregistered
            ifaces.delegateConn.setOperationCountDownLatch(1);
            DelegateRegistrationState s = getRegisteringRegistrationState(registeredTags);
            ifaces.delegate.notifyImsRegistrationUpdate(s);
            ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
            ifaces.delegateConn.verifyRegistrationStateEquals(s);

            ifaces.delegateConn.setOperationCountDownLatch(1);
            s = getDeregisteringState(registeredTags,
                    DelegateRegistrationState.DEREGISTERING_REASON_LOSING_PDN);
            ifaces.delegate.notifyImsRegistrationUpdate(s);
            ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
            ifaces.delegateConn.verifyRegistrationStateEquals(s);

            ifaces.delegateConn.setOperationCountDownLatch(1);
            s = getRegisteredRegistrationState(registeredTags);
            ifaces.delegate.notifyImsRegistrationUpdate(s);
            ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
            ifaces.delegateConn.verifyRegistrationStateEquals(s);

            destroySipDelegateAndVerify(ifaces);
            assertEquals("There should be no more delegates", 0,
                    ifaces.transport.getDelegates().size());
        } finally {
            TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.SUPPORT_REGISTERING_DELEGATE_STATE_STRING);
            TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.SUPPORT_DEREGISTERING_LOSING_PDN_STATE_STRING);
        }
    }

    @Ignore("the compatibility framework does not currently support changing compatibility flags"
            + "on user builds for device side CTS tests. Ignore this test until support is added")
    @Test
    public void testDelegateRegistrationChangesCompatDisabled() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        try {
            TelephonyUtils.disableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.SUPPORT_REGISTERING_DELEGATE_STATE_STRING);
            TelephonyUtils.disableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.SUPPORT_DEREGISTERING_LOSING_PDN_STATE_STRING);

            TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                    Collections.emptySet(), 0);
            ifaces.connectAndVerify();
            Set<String> registeredTags = new ArraySet<>(ifaces.request.getFeatureTags());

            // The registering state should move to the deregistered state
            // with the reason DEREGISTERED_REASON_NOT_REGISTERED when the registering state is not
            // supported.
            ifaces.delegateConn.setOperationCountDownLatch(1);
            DelegateRegistrationState s = getRegisteringRegistrationState(registeredTags);
            ifaces.delegate.notifyImsRegistrationUpdate(s);
            ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
            s = getDeregisteredState(registeredTags,
                    DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED);
            ifaces.delegateConn.verifyRegistrationStateEquals(s);

            // The reason DEREGISTERING_REASON_LOSING_PDN of the deregistering state will be changed
            // to the reason DEREGISTERING_REASON_PDN_CHANGE when DEREGISTERING_LOSING_PDN_STATE is
            // not supported.
            ifaces.delegateConn.setOperationCountDownLatch(1);
            s = getDeregisteringState(registeredTags,
                    DelegateRegistrationState.DEREGISTERING_REASON_LOSING_PDN);
            ifaces.delegate.notifyImsRegistrationUpdate(s);
            ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
            s = getDeregisteringState(registeredTags,
                    DelegateRegistrationState.DEREGISTERING_REASON_PDN_CHANGE);
            ifaces.delegateConn.verifyRegistrationStateEquals(s);

            ifaces.delegateConn.setOperationCountDownLatch(1);
            s = getRegisteredRegistrationState(registeredTags);
            ifaces.delegate.notifyImsRegistrationUpdate(s);
            ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
            ifaces.delegateConn.verifyRegistrationStateEquals(s);

            destroySipDelegateAndVerify(ifaces);
            assertEquals("There should be no more delegates", 0,
                    ifaces.transport.getDelegates().size());
        } finally {
            TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.SUPPORT_REGISTERING_DELEGATE_STATE_STRING);
            TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.SUPPORT_DEREGISTERING_LOSING_PDN_STATE_STRING);
        }
    }

    @Test
    public void testCreateMultipleDelegates() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();
        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();

        DelegateRequest request1 = getChatOnlyRequest();
        TestSipDelegateConnection delegateConn1 = new TestSipDelegateConnection(request1);
        Set<String> registeredTags1 = new ArraySet<>(request1.getFeatureTags());
        TestSipDelegate delegate1 = createSipDelegateConnectionAndVerify(manager, delegateConn1,
                transportImpl, Collections.emptySet(), 0);
        assertNotNull(delegate1);

        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        verifyRegisteredAndSendSipConfig(delegateConn1, delegate1, registeredTags1,
                Collections.emptySet(), c);

        // This will only be granted File transfer FT
        DelegateRequest request2 = getDefaultRequest();
        TestSipDelegateConnection delegateConn2 = new TestSipDelegateConnection(request2);
        Set<String> registeredTags2 = new ArraySet<>();
        registeredTags2.add(FILE_TRANSFER_HTTP_TAG);
        TestSipDelegate delegate2 = createSipDelegateConnectionAndVerify(manager, delegateConn2,
                transportImpl, Collections.emptySet(), 1);
        assertNotNull(delegate2);
        verifyUpdateRegistrationCalled(regImpl);
        Set<FeatureTagState> deniedSet = generateDeniedSetFromRequest(request1.getFeatureTags(),
                request2.getFeatureTags(),
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE);
        verifyRegisteredAndSendSipConfig(delegateConn2, delegate2, registeredTags2,
                deniedSet, c);

        // Destroying delegate 1 will transfer all feature tags over to delegate 2
        destroySipDelegate(manager, transportImpl, delegateConn1, delegate1);
        // This internally triggers the destruction of the internal delegate2 and then recreation
        // of another delegate with the new feature set that it supports.
        verifySipDelegateDestroyed(transportImpl, delegate2);
        delegate2 = getSipDelegate(transportImpl, Collections.emptySet(), 0);
        verifyUpdateRegistrationCalled(regImpl);
        verifyRegisteredAndSendSipConfig(delegateConn2, delegate2, request2.getFeatureTags(),
                Collections.emptySet(), c);

        destroySipDelegateAndVerifyConnDestroyed(manager, transportImpl, delegateConn2, delegate2);
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
    }

    @Test
    public void testCreateDelegateMessagingAppChangesToApp() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // start with no features granted
        connectTestImsServiceWithSipTransportAndConfig();

        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);

        // wait for onCreated and registration state change to be called.
        createSipDelegateConnectionNoDelegateExpected(manager, delegateConn, transportImpl);

        // Make this app the DMA
        regImpl.resetLatch(TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION, 1);
        assertTrue(sServiceConnector.setDefaultSmsApp());
        assertTrue(regImpl.waitForLatchCountDown(TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION,
                ImsUtils.TEST_TIMEOUT_MS));
        TestSipDelegate delegate = getSipDelegate(transportImpl, Collections.emptySet(), 0);
        verifyUpdateRegistrationCalled(regImpl);
        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        verifyRegisteredAndSendSipConfig(delegateConn, delegate, request.getFeatureTags(),
                Collections.emptySet(), c);
        destroySipDelegateAndVerifyConnDestroyed(manager, transportImpl, delegateConn, delegate);
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
    }

    @Test
    public void testCreateDelegateMessagingAppChangesAwayFromApp() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Make this app the DMA
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connectAndVerify();

        // Move DMA to another app, we should receive a registration update.
        ifaces.reg.resetLatch(TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION, 1);
        sServiceConnector.restoreDefaultSmsApp();
        assertTrue(ifaces.reg.waitForLatchCountDown(
                TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION, ImsUtils.TEST_TIMEOUT_MS));
        // we should get another reg update with all tags denied.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        verifySipDelegateDestroyed(ifaces.transport, ifaces.delegate);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyRegistrationStateEmpty();
        // All requested features should have been denied due to the app not being the default sms
        // app.
        ifaces.delegateConn.verifyAllDenied(SipDelegateManager.DENIED_REASON_NOT_ALLOWED);
        // There should not be any delegates left, as the only delegate should have been cleaned up.
        assertEquals("SipDelegate should not have any delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
        destroySipDelegateConnectionNoDelegate(ifaces.manager, ifaces.delegateConn);
    }

    @Test
    public void testParcelUnparcelDelegateRequest() {
        ArraySet<String> testTags = new ArraySet<>();
        testTags.add(MMTEL_TAG);
        testTags.add(ONE_TO_ONE_CHAT_TAG);
        testTags.add(GROUP_CHAT_TAG);
        testTags.add(FILE_TRANSFER_HTTP_TAG);
        DelegateRequest r = new DelegateRequest(testTags);
        Parcel p = Parcel.obtain();
        r.writeToParcel(p, 0);
        p.setDataPosition(0);
        DelegateRequest unparcelled = DelegateRequest.CREATOR.createFromParcel(p);
        assertEquals(r, unparcelled);
        assertEquals(r.getFeatureTags(), unparcelled.getFeatureTags());
    }

    @Test
    public void testParcelUnparcelFeatureTagState() {
        FeatureTagState f = new FeatureTagState(MMTEL_TAG,
                DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED);
        Parcel p = Parcel.obtain();
        f.writeToParcel(p, 0);
        p.setDataPosition(0);
        FeatureTagState unparcelled = FeatureTagState.CREATOR.createFromParcel(p);
        assertEquals(f, unparcelled);
        assertEquals(f.getFeatureTag(), unparcelled.getFeatureTag());
        assertEquals(f.getState(), unparcelled.getState());
    }

    @Test
    public void testParcelUnparcelRegistrationState() {
        ArraySet<String> regTags = new ArraySet<>();
        regTags.add(MMTEL_TAG);
        ArraySet<String> registeringTags = new ArraySet<>();
        registeringTags.add(CHATBOT_COMMUNICATION_USING_SESSION_TAG);
        DelegateRegistrationState s = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTags(regTags)
                .addRegisteredFeatureTag(ONE_TO_ONE_CHAT_TAG)
                .addDeregisteringFeatureTag(GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_PDN_CHANGE)
                .addDeregisteredFeatureTag(FILE_TRANSFER_HTTP_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED)
                .addRegisteringFeatureTags(registeringTags)
                .build();
        Parcel p = Parcel.obtain();
        s.writeToParcel(p, 0);
        p.setDataPosition(0);
        DelegateRegistrationState unparcel = DelegateRegistrationState.CREATOR.createFromParcel(p);
        assertEquals(s, unparcel);
        assertEquals(s.getRegisteredFeatureTags(), unparcel.getRegisteredFeatureTags());
        assertEquals(s.getDeregisteringFeatureTags(), unparcel.getDeregisteringFeatureTags());
        assertEquals(s.getDeregisteredFeatureTags(), unparcel.getDeregisteredFeatureTags());
        assertEquals(s.getRegisteringFeatureTags(), unparcel.getRegisteringFeatureTags());
    }

    @Test
    public void testParcelUnparcelConfiguration() {
        // Set everything to a non-default value
        SipDelegateConfiguration c = generateNewTestConfig();
        assertEquals(1, c.getVersion());
        assertEquals(SipDelegateConfiguration.SIP_TRANSPORT_TCP, c.getTransportType());
        assertEquals("1.1.1.1", c.getLocalAddress().getAddress().getHostAddress());
        assertEquals(80, c.getLocalAddress().getPort());
        assertEquals("2.2.2.2", c.getSipServerAddress().getAddress().getHostAddress());
        assertEquals(81, c.getSipServerAddress().getPort());
        assertTrue(c.isSipCompactFormEnabled());
        assertTrue(c.isSipKeepaliveEnabled());
        assertEquals(508, c.getMaxUdpPayloadSizeBytes());
        assertEquals("test1", c.getPublicUserIdentifier());
        assertEquals("test2", c.getPrivateUserIdentifier());
        assertEquals("test.domain", c.getHomeDomain());
        assertEquals("testImei", c.getImei());
        assertEquals("sipauth", c.getSipAuthenticationHeader());
        assertEquals("sipnonce", c.getSipAuthenticationNonce());
        assertEquals("srvroute", c.getSipServiceRouteHeader());
        assertEquals("path", c.getSipPathHeader());
        assertEquals("ua", c.getSipUserAgentHeader());
        assertEquals("user", c.getSipContactUserParameter());
        assertEquals("pani", c.getSipPaniHeader());
        assertEquals("plani", c.getSipPlaniHeader());
        assertEquals("cni", c.getSipCniHeader());
        assertEquals("uri", c.getSipAssociatedUriHeader());
        Uri gruuUri = Uri.parse("sip:blah@gruu.net");
        assertEquals(gruuUri, c.getPublicGruuUri());

        Parcel p = Parcel.obtain();
        c.writeToParcel(p, 0);
        p.setDataPosition(0);
        SipDelegateConfiguration unparcelConfig =
                SipDelegateConfiguration.CREATOR.createFromParcel(p);
        assertEquals(c, unparcelConfig);
    }

    // this test is required to avoid pre-submit check (API coverage check FAIL)
    @Test
    public void testSipDialogStateDescribeContents() {
        SipDialogState state = new SipDialogState.Builder(SipDialogState.STATE_CONFIRMED).build();
        assertNotNull(state);
        int describe = state.describeContents();
        assertTrue(describe == 0);
    }

    @Test
    public void testParcelUnparcelSipDialogState() {
        SipDialogState state = new SipDialogState.Builder(SipDialogState.STATE_CONFIRMED).build();
        assertEquals(SipDialogState.STATE_CONFIRMED, state.getState());
        Parcel p = Parcel.obtain();
        state.writeToParcel(p, 0);
        p.setDataPosition(0);
        SipDialogState unparcelled = SipDialogState.CREATOR.createFromParcel(p);
        assertEquals(state, unparcelled);
        assertEquals(state.getState() , unparcelled.getState());
    }

    @Test
    public void testCopyConfiguration() {
        // Set everything to a non-default value
        SipDelegateConfiguration c = generateNewTestConfig();
        // The config should be exactly the same, but with an incremented version.
        SipDelegateConfiguration configInc = new SipDelegateConfiguration.Builder(c).build();
        assertEquals(2, configInc.getVersion());
        assertEquals(SipDelegateConfiguration.SIP_TRANSPORT_TCP, configInc.getTransportType());
        assertEquals("1.1.1.1", configInc.getLocalAddress().getAddress().getHostAddress());
        assertEquals(80, configInc.getLocalAddress().getPort());
        assertEquals("2.2.2.2",
                configInc.getSipServerAddress().getAddress().getHostAddress());
        assertEquals(81, configInc.getSipServerAddress().getPort());
        assertTrue(configInc.isSipCompactFormEnabled());
        assertTrue(configInc.isSipKeepaliveEnabled());
        assertEquals(508, configInc.getMaxUdpPayloadSizeBytes());
        assertEquals("test1", configInc.getPublicUserIdentifier());
        assertEquals("test2", configInc.getPrivateUserIdentifier());
        assertEquals("test.domain", configInc.getHomeDomain());
        assertEquals("testImei", configInc.getImei());
        assertEquals("sipauth", configInc.getSipAuthenticationHeader());
        assertEquals("sipnonce", configInc.getSipAuthenticationNonce());
        assertEquals("srvroute", configInc.getSipServiceRouteHeader());
        assertEquals("path", configInc.getSipPathHeader());
        assertEquals("ua", configInc.getSipUserAgentHeader());
        assertEquals("user", configInc.getSipContactUserParameter());
        assertEquals("pani", configInc.getSipPaniHeader());
        assertEquals("plani", configInc.getSipPlaniHeader());
        assertEquals("cni", configInc.getSipCniHeader());
        assertEquals("uri", configInc.getSipAssociatedUriHeader());
        Uri gruuUri = Uri.parse("sip:blah@gruu.net");
        assertEquals(gruuUri, configInc.getPublicGruuUri());
        SipDelegateConfiguration.IpSecConfiguration ipSecConfig = configInc.getIpSecConfiguration();
        assertEquals(123, ipSecConfig.getLocalTxPort());
        assertEquals(124, ipSecConfig.getLocalRxPort());
        assertEquals(125, ipSecConfig.getLastLocalTxPort());
        assertEquals(126, ipSecConfig.getRemoteTxPort());
        assertEquals(127, ipSecConfig.getRemoteRxPort());
        assertEquals(128, ipSecConfig.getLastRemoteTxPort());
        assertEquals("secverify", ipSecConfig.getSipSecurityVerifyHeader());
        InetSocketAddress natAddr = configInc.getNatSocketAddress();
        assertEquals("3.3.3.3", natAddr.getAddress().getHostAddress());
        assertEquals(129, natAddr.getPort());
    }

    @Test
    public void testParcelUnparcelSipMessage() {
        String startLine =
                "INVITE sip:12345678@[2607:fc20:3806:2a44:0:6:42ae:5b01]:49155 SIP/2.0\r\n";
        String header = "Via: SIP/2.0/TCP [FD00:976A:C202:1808::1]:65529;"
                + "branch=z9hG4bKg3Zqkv7iivdfzmfqu68sro3cuht97q846\r\n"
                + "To: <sip:12345678;phone-context=xxx.com@xxx.com;user=phone>\r\n"
                + "From: <sip:12345679@xxx.com>;tag=ABC\r\n"
                + "Call-ID: 000050B04074-79e-fc9b8700-29df64-5f3e5811-26fa8\r\n";
        String branch = "z9hG4bKg3Zqkv7iivdfzmfqu68sro3cuht97q846";
        String callId = "000050B04074-79e-fc9b8700-29df64-5f3e5811-26fa8";
        byte[] bytes = new byte[1];
        bytes[0] = 'a';
        SipMessage m = new SipMessage(startLine, header, bytes);
        Parcel p = Parcel.obtain();
        m.writeToParcel(p, 0);
        p.setDataPosition(0);
        SipMessage unparcel = SipMessage.CREATOR.createFromParcel(p);
        assertEquals(m, unparcel);
        assertEquals(m.getStartLine(), unparcel.getStartLine());
        assertEquals(m.getHeaderSection(), unparcel.getHeaderSection());
        assertTrue(Arrays.equals(m.getContent(), unparcel.getContent()));
        assertEquals(branch, m.getViaBranchParameter());
        assertEquals(callId, m.getCallIdParameter());
        assertEquals(m.getViaBranchParameter(), unparcel.getViaBranchParameter());
        assertEquals(m.getCallIdParameter(), unparcel.getCallIdParameter());
    }

    @Test
    public void testEncodeSipMessage() {
        String startLine =
                "INVITE sip:12345678@[2607:fc20:3806:2a44:0:6:42ae:5b01]:49155 SIP/2.0\r\n";
        String header = "Via: SIP/2.0/TCP [FD00:976A:C202:1808::1]:65529;"
                + "branch=z9hG4bKg3Zqkv7iivdfzmfqu68sro3cuht97q846\r\n"
                + "To: <sip:12345678;phone-context=xxx.com@xxx.com;"
                + "user=phone>\r\n"
                + "From: <sip:12345679@xxx.com>;"
                + "tag=h7g4Esbg_mavodi-e-10b-123-6-ffffffff-_000050B04074-79e-fc9b8700-29df65"
                + "-5f3e5811-27196\r\n"
                + "Call-ID: 000050B04074-79e-fc9b8700-29df64-5f3e5811-26fa8\r\n";
        byte[] content1 = ("v=0\r\n"
                + "o=- 10 1000 IN IP6 FD00:976A:C202:1808::1\r\n"
                + "s=VOIP\r\n"
                + "c=IN IP6 fd00:976a:c002:1940::4\r\n").getBytes(UTF_8);
        byte[] content2 = new byte[0];

        SipMessage m = new SipMessage(startLine, header, content1);
        byte[] encodedMsg = m.toEncodedMessage();
        String decodedStr = new String(encodedMsg, UTF_8);
        SipMessage decodedMsg = generateSipMessage(decodedStr);
        assertEquals(decodedMsg.getStartLine(), m.getStartLine());
        assertEquals(decodedMsg.getHeaderSection(), m.getHeaderSection());
        assertTrue(Arrays.equals(decodedMsg.getContent(), m.getContent()));

        // Test empty content
        m = new SipMessage(startLine, header, content2);
        encodedMsg = m.toEncodedMessage();
        decodedStr = new String(encodedMsg, UTF_8);
        decodedMsg = generateSipMessage(decodedStr);
        assertEquals(decodedMsg.getStartLine(), m.getStartLine());
        assertEquals(decodedMsg.getHeaderSection(), m.getHeaderSection());
        assertTrue(Arrays.equals(decodedMsg.getContent(), m.getContent()));
    }

    @Test
    public void testFeatureTagDeniedByCarrierConfig() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        setFeatureTagsCarrierAllowed(new String[]{FILE_TRANSFER_HTTP_TAG});
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();
        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);
        Set<String> deniedTags = new ArraySet<>(request.getFeatureTags());
        deniedTags.remove(FILE_TRANSFER_HTTP_TAG);

        TestSipDelegate delegate = createSipDelegateConnectionAndVerify(manager, delegateConn,
                transportImpl, getDeniedTagsForReason(deniedTags,
                        SipDelegateManager.DENIED_REASON_NOT_ALLOWED), 0);
        assertNotNull(delegate);

        Set<String> registeredTags = new ArraySet<>(
                Arrays.asList(new String[]{FILE_TRANSFER_HTTP_TAG}));
        delegateConn.setOperationCountDownLatch(1);
        DelegateRegistrationState s = getRegisteredRegistrationState(registeredTags);
        delegate.notifyImsRegistrationUpdate(s);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyRegistrationStateEquals(s);
        destroySipDelegateAndVerifyConnDestroyed(manager, transportImpl, delegateConn, delegate);
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
        setFeatureTagsCarrierAllowed(getDefaultRequest().getFeatureTags().toArray(new String[0]));
    }

    @Test
    public void testCloseActiveDialog() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // send close from app
        ifaces.delegateConn.disconnect(ifaces.manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING));
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        // Send BYE
        sendByeRequest(attr, ifaces);
        // destroy should not be called until cleanupSession is sent.
        assertFalse(ifaces.transport.isLatchCountDownFinished(
                TestSipTransport.LATCH_DESTROY_DELEGATE));

        ifaces.delegateConn.setOperationCountDownLatch(1);
        // Send the cleanup, which will trigger destroy to complete.
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);

        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testReceivedActiveDialogClose() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // receive invite
        SipDialogAttributes attr = new SipDialogAttributes();
        receiveChatInvite(attr, ifaces);
        // send close from app
        ifaces.delegateConn.disconnect(ifaces.manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING));
        // Send 200 OK
        send200OkResponse(attr, ifaces);
        // receive ACK
        receiveAck(attr, ifaces);
        // Send BYE
        receiveByeRequest(attr, ifaces);
        ifaces.delegateConn.setOperationCountDownLatch(1);
        // Send the cleanup, which will trigger destroy to complete.
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);

        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testActiveDialogPendingNewInvite() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // send close from app
        ifaces.delegateConn.disconnect(ifaces.manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING));
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        // Send invite
        SipDialogAttributes attr2 = new SipDialogAttributes();
        attr2.addAcceptContactTag(ONE_TO_ONE_CHAT_TAG);
        // Should be denied because the transport is now restricted
        sendDeniedChatInvite(attr2, ifaces,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        // Send BYE on original invite
        sendByeRequest(attr, ifaces);
        // destroy should not be called until cleanupSession is sent.
        assertFalse(ifaces.transport.isLatchCountDownFinished(
                TestSipTransport.LATCH_DESTROY_DELEGATE));
        ifaces.delegateConn.setOperationCountDownLatch(1);
        // Send the cleanup, which will trigger destroy to complete.
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);

        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testCloseSessionByePendingCleanup() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // send close from app
        ifaces.delegateConn.disconnect(ifaces.manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        // Send BYE
        sendByeRequest(attr, ifaces);
        assertFalse(ifaces.transport.isLatchCountDownFinished(
                TestSipTransport.LATCH_DESTROY_DELEGATE));
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING));
        // waiting for delegate connection onDestroy to be called.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        // no cleanupSession called, so cleanup session should be called from internal and then
        // the delegate should be closed.
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testCloseSessionPendingBye() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // send close from app
        ifaces.delegateConn.disconnect(ifaces.manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        // Don't send BYE or cleanupSession
        assertFalse(ifaces.transport.isLatchCountDownFinished(
                TestSipTransport.LATCH_DESTROY_DELEGATE));
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING));
        // waiting for delegate connection onDestroy to be called.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        // no cleanupSession called, so cleanup session should be called from internal and then
        // the delegate should be closed.
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testCloseMultipleSessionsPendingBye() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite 1
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // Send invite 2
        SipDialogAttributes attr2 = new SipDialogAttributes();
        sendChatInvite(attr2, ifaces);
        // send close from app
        ifaces.delegateConn.disconnect(ifaces.manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        receive200OkResponse(attr2, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        sendAck(attr2, ifaces);
        // Don't send BYE or cleanupSession
        assertFalse(ifaces.transport.isLatchCountDownFinished(
                TestSipTransport.LATCH_DESTROY_DELEGATE));
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING));
        // waiting for delegate connection onDestroy to be called.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        // no cleanupSession called, so cleanup session should be called from internal and then
        // the delegate should be closed.
        ifaces.delegate.verifyCleanupSession(attr.callId, attr2.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testCloseSessionBye() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        // send close from app
        ifaces.delegateConn.disconnect(ifaces.manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        // Send BYE
        sendByeRequest(attr, ifaces);
        assertFalse(ifaces.transport.isLatchCountDownFinished(
                TestSipTransport.LATCH_DESTROY_DELEGATE));
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING));
        // waiting for delegate connection onDestroy to be called.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        // Send the cleanup, which will trigger destroy to complete.
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testSwitchAppPendingBye() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Restore the default SMS app.
        sServiceConnector.restoreDefaultSmsApp();
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING));
        // Don't send BYE or cleanup, session should still be cleaned up.
        assertFalse(ifaces.transport.isLatchCountDownFinished(
                TestSipTransport.LATCH_DESTROY_DELEGATE));
        // wait for delegate connection feature tag state to be updated with denied features.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        // verify framework internally calls cleanup on the session before destroy.
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyRegistrationStateEmpty();
        ifaces.delegateConn.verifyAllDenied(SipDelegateManager.DENIED_REASON_NOT_ALLOWED);
    }

    @Test
    public void testSwitchAppActiveSession() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Restore the default SMS app.
        sServiceConnector.restoreDefaultSmsApp();
        // Registration state will change to deregistering during this time.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        assertTrue(ifaces.delegateConn.verifyDeregisteringStateContains(ONE_TO_ONE_CHAT_TAG,
                DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING));
        // BYE should still be able to be sent
        sendByeRequest(attr, ifaces);
        assertFalse(ifaces.transport.isLatchCountDownFinished(
                TestSipTransport.LATCH_DESTROY_DELEGATE));
        // wait for delegate connection feature tag state to be updated with denied features.
        ifaces.delegateConn.setOperationCountDownLatch(1);
        // Send the cleanup, which will trigger delegate destroy to complete.
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);
        ifaces.transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        ifaces.delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyAllDenied(SipDelegateManager.DENIED_REASON_NOT_ALLOWED);
    }

    @Test
    public void testActiveSessionDeregistering() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // move chat to deregistering
        Set<String> regFeatures = new ArraySet<>(Arrays.asList(DEFAULT_FEATURE_TAGS));
        regFeatures.remove(ONE_TO_ONE_CHAT_TAG);
        DelegateRegistrationState state = getDeregisteringState(regFeatures,
                Collections.singleton(ONE_TO_ONE_CHAT_TAG),
                DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE);
        verifyRegistrationState(ifaces, state);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        // Send BYE and clean up
        sendByeRequest(attr, ifaces);
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);

        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testActiveSessionDeregisteringNoResponse() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // move chat to deregistering
        Set<String> regFeatures = new ArraySet<>(Arrays.asList(DEFAULT_FEATURE_TAGS));
        regFeatures.remove(ONE_TO_ONE_CHAT_TAG);
        DelegateRegistrationState state = getDeregisteringState(regFeatures,
                Collections.singleton(ONE_TO_ONE_CHAT_TAG),
                DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE);
        verifyRegistrationState(ifaces, state);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        // Don't send BYE or cleanup and ensure that we still get call to clean up after timeout.
        ifaces.delegate.verifyCleanupSession(attr.callId);

        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testMultipleActiveSessionDeregisteringNoResponse() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite 1
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // Send invite 2
        SipDialogAttributes attr2 = new SipDialogAttributes();
        sendChatInvite(attr2, ifaces);
        // move chat to deregistering
        Set<String> regFeatures = new ArraySet<>(Arrays.asList(DEFAULT_FEATURE_TAGS));
        regFeatures.remove(ONE_TO_ONE_CHAT_TAG);
        DelegateRegistrationState state = getDeregisteringState(regFeatures,
                Collections.singleton(ONE_TO_ONE_CHAT_TAG),
                DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE);
        verifyRegistrationState(ifaces, state);
        // receive 200 OK for invite 1
        receive200OkResponse(attr, ifaces);
        // Don't send BYE or cleanup and ensure that we still get call to clean up after timeout.
        ifaces.delegate.verifyCleanupSession(attr.callId, attr2.callId);
        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testActiveSessionDeregisteringNewInviteDenied() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // Send invite
        SipDialogAttributes attr = new SipDialogAttributes();
        sendChatInvite(attr, ifaces);
        // move chat to deregistering
        Set<String> regFeatures = new ArraySet<>(Arrays.asList(DEFAULT_FEATURE_TAGS));
        regFeatures.remove(ONE_TO_ONE_CHAT_TAG);
        DelegateRegistrationState state = getDeregisteringState(regFeatures,
                Collections.singleton(ONE_TO_ONE_CHAT_TAG),
                DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE);
        verifyRegistrationState(ifaces, state);
        // receive 200 OK
        receive200OkResponse(attr, ifaces);
        // Send ACK
        sendAck(attr, ifaces);
        // send a new invite over the same feature tag, which should be denied because the tag
        // is deregistering
        SipDialogAttributes attr2 = new SipDialogAttributes();
        attr2.addAcceptContactTag(ONE_TO_ONE_CHAT_TAG);
        sendDeniedChatInvite(attr2, ifaces,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG);
        // Send BYE and clean up
        sendByeRequest(attr, ifaces);
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);

        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testInviteDeniedTag() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Deny ONE_TO_ONE_CHAT access to this delegate
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.singleton(new FeatureTagState(ONE_TO_ONE_CHAT_TAG,
                        SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE)), 0);
        ifaces.connect();
        // send a new invite over the chat feature tag, which should be denied because the tag was
        // denied
        SipDialogAttributes attr = new SipDialogAttributes();
        attr.addAcceptContactTag(ONE_TO_ONE_CHAT_TAG);
        sendDeniedChatInvite(attr, ifaces,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG);

        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testInviteAcceptContactNotAssociated() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // send a new invite over the MMTEL feature tag, which is not in the set of feature tags
        // associated with this delegate.
        SipDialogAttributes attr = new SipDialogAttributes();
        attr.addAcceptContactTag(MMTEL_TAG);
        sendDeniedChatInvite(attr, ifaces,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_FEATURE_TAG);

        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    @Test
    public void testIncomingInviteDeregistering() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TransportInterfaces ifaces = new TransportInterfaces(getDefaultRequest(),
                Collections.emptySet(), 0);
        ifaces.connect();
        // move chat to deregistering
        Set<String> regFeatures = new ArraySet<>(Arrays.asList(DEFAULT_FEATURE_TAGS));
        regFeatures.remove(ONE_TO_ONE_CHAT_TAG);
        DelegateRegistrationState state = getDeregisteringState(regFeatures,
                Collections.singleton(ONE_TO_ONE_CHAT_TAG),
                DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE);
        verifyRegistrationState(ifaces, state);
        // receive invite, which can not be blocked
        SipDialogAttributes attr = new SipDialogAttributes();
        receiveChatInvite(attr, ifaces);
        // ensure delegate connection can still respond to the request, even if in restricted state.
        send200OkResponse(attr, ifaces);
        receiveAck(attr, ifaces);
        // receive BYE and clean up
        receiveByeRequest(attr, ifaces);
        ifaces.delegateConn.sendCleanupSession(attr.callId);
        ifaces.delegate.verifyCleanupSession(attr.callId);

        destroySipDelegateAndVerify(ifaces);
        assertEquals("There should be no more delegates", 0,
                ifaces.transport.getDelegates().size());
        verifyUpdateRegistrationCalled(ifaces.reg);
    }

    private SipMessage generateSipMessage(String str) {
        String crlf = "\r\n";
        String[] components = str.split(crlf);
        String startLine = "";
        String header = "";
        String content = "";
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        if (components.length > 0) {
            startLine = components[0] + crlf;
        }
        // generate sip header
        idx = composeSipSection(idx, components, sb);
        header = sb.toString();

        idx++;
        sb.setLength(0);
        // generate sip body
        idx = composeSipSection(idx, components, sb);
        content = sb.toString();

        return new SipMessage(startLine, header, content.getBytes(UTF_8));
    }

    private int composeSipSection(int index, String[] components, StringBuilder sb) {
        String crlf = "\r\n";
        while (index < components.length) {
            if (components[index].length() > 0) {
                sb.append(components[index]).append(crlf);
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    private void sendChatInvite(SipDialogAttributes attr,
            TransportInterfaces ifaces) throws Exception {
        SipDialogAttributes invAttr = attr.fromExisting().copyWithNewBranch();
        invAttr.addAcceptContactTag(ONE_TO_ONE_CHAT_TAG);
        SipMessage invite = SipMessageUtils.generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD,
                invAttr);
        sendMessageAndVerifyAck(invite, ifaces);
    }

    private void sendDeniedChatInvite(SipDialogAttributes attr,
            TransportInterfaces ifaces, int denyReason) throws Exception {
        SipDialogAttributes invAttr = attr.fromExisting().copyWithNewBranch();
        SipMessage invite = SipMessageUtils.generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD,
                invAttr);
        ifaces.delegateConn.sendMessageAndVerifyFailure(invite, denyReason);
    }

    private void receiveChatInvite(SipDialogAttributes attr,
            TransportInterfaces ifaces) throws Exception {
        SipDialogAttributes invAttr = attr.fromExisting().copyWithNewBranch();
        invAttr.addAcceptContactTag(ONE_TO_ONE_CHAT_TAG);
        SipMessage invite = SipMessageUtils.generateSipRequest(SipMessageUtils.INVITE_SIP_METHOD,
                invAttr);
        receiveMessageAndVerifyAck(invite, ifaces);
    }

    private void send200OkResponse(SipDialogAttributes attr,
            TransportInterfaces ifaces) throws Exception {
        attr.setToTag();
        // do not update branch here, as it is a response to a request.
        SipMessage resp = SipMessageUtils.generateSipResponse("200", "OK",
                attr);
        sendMessageAndVerifyAck(resp, ifaces);
    }

    private void receive200OkResponse(SipDialogAttributes attr,
            TransportInterfaces ifaces) throws Exception {
        attr.setToTag();
        // do not update branch here, as it is a response to a request.
        SipMessage resp = SipMessageUtils.generateSipResponse("200", "OK",
                attr);
        receiveMessageAndVerifyAck(resp, ifaces);
    }

    private void sendAck(SipDialogAttributes attr,
            TransportInterfaces ifaces) throws Exception {
        attr = attr.copyWithNewBranch();
        SipMessage invite = SipMessageUtils.generateSipRequest(SipMessageUtils.ACK_SIP_METHOD,
                attr);
        sendMessageAndVerifyAck(invite, ifaces);
    }

    private void receiveAck(SipDialogAttributes attr,
            TransportInterfaces ifaces) throws Exception {
        attr = attr.copyWithNewBranch();
        SipMessage invite = SipMessageUtils.generateSipRequest(SipMessageUtils.ACK_SIP_METHOD,
                attr);
        receiveMessageAndVerifyAck(invite, ifaces);
    }

    private void sendByeRequest(SipDialogAttributes attr,
            TransportInterfaces ifaces) throws Exception {
        attr = attr.copyWithNewBranch();
        SipMessage invite = SipMessageUtils.generateSipRequest(SipMessageUtils.BYE_SIP_METHOD,
                attr);
        sendMessageAndVerifyAck(invite, ifaces);
    }

    private void receiveByeRequest(SipDialogAttributes attr,
            TransportInterfaces ifaces) throws Exception {
        attr = attr.copyWithNewBranch();
        SipMessage invite = SipMessageUtils.generateSipRequest(SipMessageUtils.BYE_SIP_METHOD,
                attr);
        receiveMessageAndVerifyAck(invite, ifaces);
    }

    private void createSipDelegateConnectionNoDelegateExpected(SipDelegateManager manager,
            TestSipDelegateConnection conn, TestSipTransport transport) throws Exception {
        // wait for onCreated and reg state changed
        conn.setOperationCountDownLatch(2);
        conn.connect(manager);
        conn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        conn.verifyDelegateCreated();
        conn.verifyRegistrationStateEmpty();
        // All requested features should have been denied due to the app not being the default sms
        // app.
        conn.verifyAllDenied(SipDelegateManager.DENIED_REASON_NOT_ALLOWED);
        // There should not have been a call to create a SipDelegate on the service side, since all
        // features were denied due to permissions issues.
        assertEquals("SipDelegate should not have been created", 0,
                transport.getDelegates().size());
    }

    private void destroySipDelegateConnectionNoDelegate(SipDelegateManager manager,
            TestSipDelegateConnection delegateConn) throws Exception {
        delegateConn.setOperationCountDownLatch(1);
        delegateConn.disconnect(manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    private void destroySipDelegate(SipDelegateManager manager,
            TestSipTransport transportImpl, TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate) throws Exception {
        delegateConn.disconnect(manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        transportImpl.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);

    }

    private void destroySipDelegateAndVerifyConnDestroyed(SipDelegateManager manager,
            TestSipTransport transportImpl, TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate) throws Exception {
        delegateConn.setOperationCountDownLatch(1);
        destroySipDelegate(manager, transportImpl, delegateConn, delegate);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    private void destroySipDelegateAndVerify(TransportInterfaces ifaces) throws Exception {
        // wait for on destroyed
        destroySipDelegateAndVerifyConnDestroyed(ifaces.manager, ifaces.transport,
                ifaces.delegateConn, ifaces.delegate);
    }

    private void verifySipDelegateDestroyed(TestSipTransport transportImpl,
            TestSipDelegate delegate) {
        transportImpl.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    private TestSipDelegate createSipDelegateConnectionAndVerify(SipDelegateManager manager,
            TestSipDelegateConnection conn, TestSipTransport transport,
            Set<FeatureTagState>  deniedTags, int delegateIndex) throws Exception {
        conn.setOperationCountDownLatch(1);
        conn.connect(manager);
        TestSipDelegate d = getSipDelegate(transport, deniedTags, delegateIndex);
        conn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        conn.verifyDelegateCreated();
        return d;
    }

    private TestSipDelegate getSipDelegate(TestSipTransport transport,
            Set<FeatureTagState> deniedTags, int delegateIndex) {
        transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_CREATE_DELEGATE);
        // There must have been a call to create a SipDelegate on the service side.
        assertEquals("SipDelegate should have been created", delegateIndex + 1,
                transport.getDelegates().size());
        TestSipDelegate d = transport.getDelegates().get(delegateIndex);
        d.notifyOnCreated(deniedTags);
        return d;
    }

    private void verifyRegisteredAndSendSipConfig(TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate, Set<String> registeredTags,
            Set<FeatureTagState> deniedTags, SipDelegateConfiguration sipConfig) {
        // wait for reg change to be called
        delegateConn.setOperationCountDownLatch(1);
        DelegateRegistrationState s = getRegisteredRegistrationState(registeredTags);
        delegate.notifyImsRegistrationUpdate(s);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyRegistrationStateRegistered(registeredTags);
        delegateConn.verifyDenied(deniedTags);

        // send config change as well.
        sendConfigChange(sipConfig, delegateConn, delegate);
    }

    private Set<FeatureTagState> generateDeniedSetFromRequest(Set<String> grantedTags,
            Set<String> newTags, int reason) {
        // Deny features from newTags that are already granted in grantedTags.
        return grantedTags.stream().filter(newTags::contains)
                .map(s -> new FeatureTagState(s, reason))
                .collect(Collectors.toSet());
    }

    private void verifyUpdateRegistrationCalled(TestImsRegistration regImpl) {
        regImpl.resetLatch(TestImsRegistration.LATCH_UPDATE_REGISTRATION, 1);
        // it is okay to reset and wait here (without race conditions) because there is a
        // second delay between triggering update registration and the latch being triggered.
        assertTrue(regImpl.waitForLatchCountDown(TestImsRegistration.LATCH_UPDATE_REGISTRATION,
                ImsUtils.TEST_TIMEOUT_MS));
    }

    private void sendRestrictedRequestsAndVerifyFailed(
            TestSipDelegateConnection delegateConn) throws Exception {
        delegateConn.sendMessageAndVerifyFailure(SipMessageUtils.TEST_INVALID_SIP_REGISTER,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE);
        delegateConn.sendMessageAndVerifyFailure(SipMessageUtils.TEST_INVALID_SIP_PUBLISH,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE);
        delegateConn.sendMessageAndVerifyFailure(SipMessageUtils.TEST_INVALID_SIP_OPTIONS,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE);
        delegateConn.sendMessageAndVerifyFailure(
                SipMessageUtils.TEST_INVALID_SIP_SUBSCRIBE_PRESENCE,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_HEADER_FIELDS);
    }

    private void verifyFullRegistrationTriggered(TransportInterfaces ifaces) throws Exception {
        ifaces.delegateConn.verifyDelegateCreated();
        ifaces.delegateConn.triggerFullNetworkRegistration(ifaces.manager, 403, "FORBIDDEN");
        TestImsRegistration.NetworkRegistrationInfo info =
                ifaces.reg.getNextFullNetworkRegRequest(ImsUtils.TEST_TIMEOUT_MS);
        assertNotNull("full registration requested, but ImsRegistrationImplBase "
                + "implementation did not receive a request.", info);
        assertEquals(403, info.sipCode);
        assertEquals("FORBIDDEN", info.sipReason);
    }

    private void sendInvalidRequestsAndVerifyFailed(
            TestSipDelegateConnection delegateConn) throws Exception {
        delegateConn.sendMessageAndVerifyFailure(SipMessageUtils.TEST_SIP_MESSAGE_INVALID_REQUEST,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE);
        delegateConn.sendMessageAndVerifyFailure(SipMessageUtils.TEST_SIP_MESSAGE_INVALID_RESPONSE,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_START_LINE);
    }

    private void verifyOutgoingTransport(TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate) throws Exception {
        // Send a message and ensure it gets received on the other end as well as acked
        delegateConn.sendMessageAndVerifyCompletedSuccessfully(SipMessageUtils.TEST_SIP_MESSAGE);
        delegate.verifyMessageSend(SipMessageUtils.TEST_SIP_MESSAGE);
        delegateConn.sendCleanupSession(SipMessageUtils.TEST_SIP_MESSAGE.getCallIdParameter());
        delegate.verifyCleanupSession(SipMessageUtils.TEST_SIP_MESSAGE.getCallIdParameter());
        // send a message and notify connection that it failed
        delegate.setSendMessageDenyReason(
                SipDelegateManager.MESSAGE_FAILURE_REASON_NETWORK_NOT_AVAILABLE);
        delegateConn.sendMessageAndVerifyFailure(SipMessageUtils.TEST_SIP_MESSAGE,
                SipDelegateManager.MESSAGE_FAILURE_REASON_NETWORK_NOT_AVAILABLE);
        delegate.verifyMessageSend(SipMessageUtils.TEST_SIP_MESSAGE);
    }

    private void sendMessageAndVerifyAck(SipMessage message,
            TransportInterfaces ifaces) throws Exception {
        // Send a message and ensure it gets received on the other end as well as acked
        ifaces.delegateConn.sendMessageAndVerifyCompletedSuccessfully(message);
    }

    private void verifyIncomingTransport(TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate) throws Exception {
        // Receive a message and ensure it gets received on the other end as well as acked
        delegate.receiveMessageAndVerifyReceivedCalled(SipMessageUtils.TEST_SIP_MESSAGE);
        delegateConn.verifyMessageReceived(SipMessageUtils.TEST_SIP_MESSAGE);
        // Receive a message and have connection notify that it didn't complete
        delegateConn.setReceivedMessageErrorResponseReason(
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_BODY_CONTENT);
        delegate.receiveMessageAndVerifyReceiveErrorCalled(SipMessageUtils.TEST_SIP_MESSAGE,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_BODY_CONTENT);
    }

    private void receiveMessageAndVerifyAck(SipMessage message,
            TransportInterfaces ifaces) throws Exception {
        // Receive a message and ensure it gets received on the other end as well as acked
        ifaces.delegate.receiveMessageAndVerifyReceivedCalled(message);
        ifaces.delegateConn.verifyMessageReceived(message);
    }

    private void verifyRegistrationState(TransportInterfaces ifaces,
            DelegateRegistrationState state) {
        ifaces.delegateConn.setOperationCountDownLatch(1);
        ifaces.delegate.notifyImsRegistrationUpdate(state);
        ifaces.delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        ifaces.delegateConn.verifyRegistrationStateEquals(state);
    }

    private DelegateRegistrationState getDeregisteringState(Set<String> registered,
            Set<String> deregistering, int deregisteringReason) {
        DelegateRegistrationState.Builder b = new DelegateRegistrationState.Builder();
        b.addRegisteredFeatureTags(registered);
        for (String dereg : deregistering) {
            b.addDeregisteringFeatureTag(dereg, deregisteringReason);
        }
        return b.build();
    }

    private void sendConfigChange(SipDelegateConfiguration c,
            TestSipDelegateConnection delegateConn, TestSipDelegate delegate) {
        delegateConn.setOperationCountDownLatch(1);
        delegate.notifyConfigurationUpdate(c);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyConfigEquals(c);
    }

    /**
     * @return A new test SipDelegateConfiguration that has all fields populated.1
     */
    private SipDelegateConfiguration generateNewTestConfig() {
        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration.Builder b = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr);
        b.setSipCompactFormEnabled(true);
        b.setSipKeepaliveEnabled(true);
        b.setMaxUdpPayloadSizeBytes(508);
        b.setPublicUserIdentifier("test1");
        b.setPrivateUserIdentifier("test2");
        b.setHomeDomain("test.domain");
        b.setImei("testImei");
        b.setSipAuthenticationHeader("sipauth");
        b.setSipAuthenticationNonce("sipnonce");
        b.setSipServiceRouteHeader("srvroute");
        b.setSipPathHeader("path");
        b.setSipUserAgentHeader("ua");
        b.setSipContactUserParameter("user");
        b.setSipPaniHeader("pani");
        b.setSipPlaniHeader("plani");
        b.setSipCniHeader("cni");
        b.setSipAssociatedUriHeader("uri");
        Uri gruuUri = Uri.parse("sip:blah@gruu.net");
        b.setPublicGruuUri(gruuUri);
        SipDelegateConfiguration.IpSecConfiguration ipSecConfig =
                new SipDelegateConfiguration.IpSecConfiguration(123, 124,
                        125, 126, 127, 128, "secverify");
        assertEquals(123, ipSecConfig.getLocalTxPort());
        assertEquals(124, ipSecConfig.getLocalRxPort());
        assertEquals(125, ipSecConfig.getLastLocalTxPort());
        assertEquals(126, ipSecConfig.getRemoteTxPort());
        assertEquals(127, ipSecConfig.getRemoteRxPort());
        assertEquals(128, ipSecConfig.getLastRemoteTxPort());
        assertEquals("secverify", ipSecConfig.getSipSecurityVerifyHeader());
        b.setIpSecConfiguration(ipSecConfig);
        InetSocketAddress natAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("3.3.3.3"), 129);
        b.setNatSocketAddress(natAddr);
        assertEquals("3.3.3.3", natAddr.getAddress().getHostAddress());
        assertEquals(129, natAddr.getPort());
        return b.build();
    }

    private DelegateRegistrationState getRegisteredRegistrationState(Set<String> registered) {
        return new DelegateRegistrationState.Builder().addRegisteredFeatureTags(registered).build();
    }

    private DelegateRegistrationState getRegisteringRegistrationState(Set<String> registering) {
        return new DelegateRegistrationState.Builder().addRegisteringFeatureTags(registering)
                .build();
    }

    private DelegateRegistrationState getDeregisteringState(Set<String> deregisterTags,
            int reason) {
        DelegateRegistrationState.Builder b = new DelegateRegistrationState.Builder();
        for (String t : deregisterTags) {
            b.addDeregisteringFeatureTag(t, reason);
        }
        return b.build();
    }

    private DelegateRegistrationState getDeregisteredState(Set<String> deregisterTags,
            int reason) {
        DelegateRegistrationState.Builder b = new DelegateRegistrationState.Builder();
        for (String t : deregisterTags) {
            b.addDeregisteredFeatureTag(t, reason);
        }
        return b.build();
    }


    private void connectTestImsServiceWithSipTransportAndConfig() throws Exception {
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);
        sServiceConnector.getCarrierService().setSipTransportImplemented();
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);
    }


    private void connectTestImsServiceWithSipTransport() throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);
        sServiceConnector.getCarrierService().setSipTransportImplemented();
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);
    }

    private void verifyImsServiceState(ImsFeatureConfiguration config) {
        for (ImsFeatureConfiguration.FeatureSlotPair p : config.getServiceFeatures()) {
            switch (p.featureType) {
                case ImsFeature.FEATURE_MMTEL: {
                    sServiceConnector.getCarrierService().waitForLatchCountdown(
                            TestImsService.LATCH_CREATE_MMTEL);
                    assertNotNull("ImsService created, but ImsService#createMmTelFeature was not "
                            + "called!", sServiceConnector.getCarrierService().getMmTelFeature());
                    break;
                }
                case ImsFeature.FEATURE_RCS: {
                    sServiceConnector.getCarrierService().waitForLatchCountdown(
                            TestImsService.LATCH_CREATE_RCS);
                    assertNotNull("ImsService created, but ImsService#createRcsFeature was not "
                            + "called!", sServiceConnector.getCarrierService().getRcsFeature());
                    break;
                }
            }
        }
    }

    /**
     * Wait up to five seconds (retrying a command 1 time per second) until ImsExceptions due to the
     * ImsService not being available go away. If the ImsService never becomes available, this
     * method will return null.
     */
    private <T> T callUntilImsServiceIsAvailable(Callable<T> command) throws Exception {
        int retry = 0;
        while (retry < 5) {
            try {
                return command.call();
            } catch (ImsException e) {
                // we want to absorb only the unavailable error, as telephony may still be
                // internally setting up. Any other type of ImsException is unexpected.
                if (e.getCode() != ImsException.CODE_ERROR_SERVICE_UNAVAILABLE) {
                    throw e;
                }
            }
            Thread.sleep(1000);
            retry++;
        }
        return null;
    }

    private DelegateRequest getDefaultRequest() {
        ArraySet<String> features = new ArraySet<>(Arrays.asList(DEFAULT_FEATURE_TAGS));
        return new DelegateRequest(features);
    }

    private DelegateRequest getChatOnlyRequest() {
        ArraySet<String> features = new ArraySet<>(3);
        features.add(ONE_TO_ONE_CHAT_TAG);
        features.add(GROUP_CHAT_TAG);
        return new DelegateRequest(features);
    }

    private ImsFeatureConfiguration getConfigForMmTelAndRcs() {
        return new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build();
    }

    private ImsFeatureConfiguration getConfigForRcs() {
        return new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build();
    }

    private Set<FeatureTagState> getDeniedTagsForReason(Set<String> deniedTags, int reason) {
        return deniedTags.stream().map(t -> new FeatureTagState(t, reason))
                .collect(Collectors.toSet());
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForCarrierConfigChanged();
    }

    private static void setFeatureTagsCarrierAllowed(String[] tags) throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putStringArray(CarrierConfigManager.Ims.KEY_RCS_FEATURE_TAG_ALLOWED_STRING_ARRAY,
                tags);
        overrideCarrierConfig(bundle);
    }

    private SipDelegateManager getSipDelegateManager() {
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        assertNotNull(imsManager);
        return imsManager.getSipDelegateManager(sTestSub);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }
}
