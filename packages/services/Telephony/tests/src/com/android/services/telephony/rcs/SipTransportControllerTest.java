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

package com.android.services.telephony.rcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.role.RoleManager;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.ImsException;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateConnectionStateCallback;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.aidl.ISipTransport;
import android.util.ArraySet;
import android.util.Pair;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.TestExecutorService;
import com.android.ims.RcsFeatureManager;
import com.android.phone.RcsProvisioningMonitor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class SipTransportControllerTest extends TelephonyTestBase {
    private static final int TEST_SUB_ID = 1;
    private static final int TEST_UID = 1001;
    private static final String TEST_PACKAGE_NAME = "com.test_pkg";
    private static final String TEST_PACKAGE_NAME_2 = "com.test_pkg2";
    private static final int TIMEOUT_MS = 200;
    private static final int THROTTLE_MS = 50;

    private class SipDelegateControllerContainer {
        public final int subId;
        public final String packageName;
        public final DelegateRequest delegateRequest;
        public final SipDelegateController delegateController;
        private final ISipDelegate mMockDelegate;
        public final IBinder mockDelegateBinder;
        public final ISipDelegateConnectionStateCallback mockDelegateConnectionCallback;
        public final ISipDelegateMessageCallback mockMessageCallback;
        public final IBinder mockMessageCallbackBinder;

        SipDelegateControllerContainer(int id, String name, DelegateRequest request) {
            delegateController = mock(SipDelegateController.class);
            mMockDelegate = mock(ISipDelegate.class);
            mockDelegateBinder = mock(IBinder.class);
            doReturn(mockDelegateBinder).when(mMockDelegate).asBinder();
            mockDelegateConnectionCallback = mock(ISipDelegateConnectionStateCallback.class);
            mockMessageCallback = mock(ISipDelegateMessageCallback.class);
            mockMessageCallbackBinder = mock(IBinder.class);
            doReturn(mockMessageCallbackBinder).when(mockMessageCallback).asBinder();
            doReturn(name).when(delegateController).getPackageName();
            doReturn(request).when(delegateController).getInitialRequest();
            doReturn(mMockDelegate).when(delegateController).getSipDelegateInterface();
            doReturn(mockMessageCallback).when(delegateController).getAppMessageCallback();
            subId = id;
            packageName = name;
            delegateRequest = request;
        }
    }

    @Mock private RcsFeatureManager mRcsManager;
    @Mock private ISipTransport mSipTransport;
    @Mock private IImsRegistration mImsRegistration;
    @Mock private SipTransportController.SipDelegateControllerFactory
            mMockDelegateControllerFactory;
    @Mock private SipTransportController.RoleManagerAdapter mMockRoleManager;

    private ScheduledExecutorService mExecutorService = null;
    private final ArrayList<SipDelegateControllerContainer> mMockControllers = new ArrayList<>();
    private final ArrayList<String> mSmsPackageName = new ArrayList<>(1);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mSmsPackageName).when(mMockRoleManager).getRoleHolders(RoleManager.ROLE_SMS);
        doReturn(mImsRegistration).when(mRcsManager).getImsRegistration();
        mSmsPackageName.add(TEST_PACKAGE_NAME);
        doAnswer(invocation -> {
            Integer subId = invocation.getArgument(0);
            String packageName = invocation.getArgument(3);
            DelegateRequest request = invocation.getArgument(2);
            SipDelegateController c = getMockDelegateController(subId, packageName, request);
            assertNotNull("create called with no corresponding controller set up", c);
            return c;
        }).when(mMockDelegateControllerFactory).create(anyInt(), anyInt(), any(), anyString(),
                any(), any(), any(), any(), any());
        setFeatureAllowedConfig(TEST_SUB_ID, new String[]{ImsSignallingUtils.MMTEL_TAG,
                ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG, ImsSignallingUtils.GROUP_CHAT_TAG,
                ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG});
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        boolean isShutdown = mExecutorService == null || mExecutorService.isShutdown();
        if (!isShutdown) {
            mExecutorService.shutdownNow();
        }
        RcsProvisioningMonitor.getInstance().overrideImsFeatureValidation(TEST_SUB_ID, null);
    }

    @SmallTest
    @Test
    public void isSupportedRcsNotConnected() {
        SipTransportController controller = createController(new TestExecutorService());
        try {
            controller.isSupported(TEST_SUB_ID);
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE, e.getCode());
        }
    }

    @SmallTest
    @Test
    public void isSupportedInvalidSubId() {
        SipTransportController controller = createController(new TestExecutorService());
        try {
            controller.isSupported(TEST_SUB_ID + 1);
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION, e.getCode());
        }
    }

    @SmallTest
    @Test
    public void isSupportedSubIdChanged() {
        SipTransportController controller = createController(new TestExecutorService());
        controller.onAssociatedSubscriptionUpdated(TEST_SUB_ID + 1);
        try {
            controller.isSupported(TEST_SUB_ID);
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION, e.getCode());
        }
    }

    @SmallTest
    @Test
    public void isSupportedSipTransportAvailableRcsConnected() throws Exception {
        SipTransportController controller = createController(new TestExecutorService());
        doReturn(mSipTransport).when(mRcsManager).getSipTransport();
        controller.onRcsConnected(mRcsManager);
        try {
            assertTrue(controller.isSupported(TEST_SUB_ID));
        } catch (ImsException e) {
            fail();
        }
    }

    @SmallTest
    @Test
    public void isSupportedSipTransportNotAvailableRcsDisconnected() throws Exception {
        SipTransportController controller = createController(new TestExecutorService());
        doReturn(mSipTransport).when(mRcsManager).getSipTransport();
        controller.onRcsConnected(mRcsManager);
        controller.onRcsDisconnected();
        try {
            controller.isSupported(TEST_SUB_ID);
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE, e.getCode());
        }
    }

    @SmallTest
    @Test
    public void isSupportedSipTransportNotAvailableRcsConnected() throws Exception {
        SipTransportController controller = createController(new TestExecutorService());
        doReturn(null).when(mRcsManager).getSipTransport();
        controller.onRcsConnected(mRcsManager);
        try {
            assertFalse(controller.isSupported(TEST_SUB_ID));
        } catch (ImsException e) {
            fail();
        }
    }

    @SmallTest
    @Test
    public void isSupportedImsServiceNotAvailableRcsConnected() throws Exception {
        SipTransportController controller = createController(new TestExecutorService());
        doThrow(new ImsException("", ImsException.CODE_ERROR_SERVICE_UNAVAILABLE))
                .when(mRcsManager).getSipTransport();
        controller.onRcsConnected(mRcsManager);
        try {
            controller.isSupported(TEST_SUB_ID);
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE, e.getCode());
        }
    }

    @SmallTest
    @Test
    public void createImsServiceAvailableSubIdIncorrect() throws Exception {
        SipTransportController controller = createController(new TestExecutorService());
        doReturn(mSipTransport).when(mRcsManager).getSipTransport();
        controller.onRcsConnected(mRcsManager);
        try {
            controller.createSipDelegate(TEST_SUB_ID + 1, TEST_UID,
                    new DelegateRequest(Collections.emptySet()), TEST_PACKAGE_NAME,
                    mock(ISipDelegateConnectionStateCallback.class),
                    mock(ISipDelegateMessageCallback.class));
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION, e.getCode());
        }
    }

    @SmallTest
    @Test
    public void createImsServiceDoesntSupportTransport() throws Exception {
        SipTransportController controller = createController(new TestExecutorService());
        doReturn(null).when(mRcsManager).getSipTransport();
        controller.onRcsConnected(mRcsManager);
        try {
            controller.createSipDelegate(TEST_SUB_ID, TEST_UID,
                    new DelegateRequest(Collections.emptySet()), TEST_PACKAGE_NAME,
                    mock(ISipDelegateConnectionStateCallback.class),
                    mock(ISipDelegateMessageCallback.class));
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION, e.getCode());
        }
    }

    @SmallTest
    @Test
    public void createImsServiceNotAvailable() throws Exception {
        SipTransportController controller = createController(new TestExecutorService());
        doThrow(new ImsException("", ImsException.CODE_ERROR_SERVICE_UNAVAILABLE))
                .when(mRcsManager).getSipTransport();
        // No RCS connected message
        try {
            controller.createSipDelegate(TEST_SUB_ID, TEST_UID,
                    new DelegateRequest(Collections.emptySet()), TEST_PACKAGE_NAME,
                    mock(ISipDelegateConnectionStateCallback.class),
                    mock(ISipDelegateMessageCallback.class));
            fail();
        } catch (ImsException e) {
            assertEquals(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE, e.getCode());
        }
    }

    @SmallTest
    @Test
    public void basicCreate() throws Exception {
        SipTransportController controller = setupLiveTransportController();

        DelegateRequest r = getBaseDelegateRequest();

        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME, r);
        createDelegateAndVerify(controller, c, r.getFeatureTags(), Collections.emptySet());
        verifyDelegateRegistrationChangedEvent(1 /*times*/, 0 /*waitMs*/);
        triggerFullNetworkRegistrationAndVerify(controller, c);
    }

    @SmallTest
    @Test
    public void basicCreateDestroy() throws Exception {
        SipTransportController controller = setupLiveTransportController();

        DelegateRequest r = getBaseDelegateRequest();
        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME, r);
        createDelegateAndVerify(controller, c, r.getFeatureTags(), Collections.emptySet());
        verify(c.mockMessageCallbackBinder).linkToDeath(any(), anyInt());
        verifyDelegateRegistrationChangedEvent(1, 0 /*throttle*/);

        destroyDelegateAndVerify(controller, c, false,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(c.mockMessageCallbackBinder).unlinkToDeath(any(), anyInt());
        verifyDelegateRegistrationChangedEvent(2 /*times*/, 0 /*waitMs*/);
        triggerFullNetworkRegistrationAndVerifyNever(controller, c);
    }

    @SmallTest
    @Test
    public void createDestroyAppDied() throws Exception {
        SipTransportController controller = setupLiveTransportController();
        DelegateRequest r = getBaseDelegateRequest();
        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME, r);
        createDelegateAndVerify(controller, c, r.getFeatureTags(), Collections.emptySet());
        ArgumentCaptor<IBinder.DeathRecipient> captor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(c.mockMessageCallbackBinder).linkToDeath(captor.capture(), anyInt());
        IBinder.DeathRecipient deathRecipient = captor.getValue();
        assertNotNull(deathRecipient);
        verifyDelegateRegistrationChangedEvent(1, 0 /*throttle*/);

        CompletableFuture<Integer> pendingDestroy = setDestroyFuture(c.delegateController, true,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        // Simulate app dying
        deathRecipient.binderDied();
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verifyDestroyDelegate(c.delegateController, pendingDestroy, true,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        // If binderDied is called, then unregister does not lead to unlinkToDeath
        triggerFullNetworkRegistrationAndVerifyNever(controller, c);
    }

    @SmallTest
    @Test
    public void testCreateButNotInRole() throws Exception {
        SipTransportController controller = setupLiveTransportController();

        DelegateRequest r = getBaseDelegateRequest();
        Set<FeatureTagState> getDeniedTags = getDeniedTagsForReason(r.getFeatureTags(),
                SipDelegateManager.DENIED_REASON_NOT_ALLOWED);

        // Try to create a SipDelegate for a package that is not the default sms role.
        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME_2, r);
        createDelegateAndVerify(controller, c, Collections.emptySet(), getDeniedTags);
    }

    @SmallTest
    @Test
    public void createTwoAndDenyOverlappingTags() throws Exception {
        SipTransportController controller = setupLiveTransportController(0 /*reeval*/,
                THROTTLE_MS);

        // First delegate requests RCS message + File transfer
        ArraySet<String> firstDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        firstDelegate.remove(ImsSignallingUtils.GROUP_CHAT_TAG);
        DelegateRequest firstDelegateRequest = new DelegateRequest(firstDelegate);
        SipDelegateControllerContainer c1 = injectMockDelegateController(TEST_PACKAGE_NAME,
                firstDelegateRequest);
        createDelegateAndVerify(controller, c1, firstDelegate, Collections.emptySet());
        // there is a delay in the indication to update reg, so it should not happen yet.
        verifyNoDelegateRegistrationChangedEvent();

        // First delegate requests RCS message + Group RCS message. For this delegate, single RCS
        // message should be denied.
        ArraySet<String> secondDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        secondDelegate.remove(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG);
        DelegateRequest secondDelegateRequest = new DelegateRequest(secondDelegate);
        Pair<Set<String>, Set<FeatureTagState>> grantedAndDenied = getAllowedAndDeniedTagsForConfig(
                secondDelegateRequest, SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE,
                firstDelegate);
        SipDelegateControllerContainer c2 = injectMockDelegateController(TEST_PACKAGE_NAME,
                secondDelegateRequest);
        createDelegateAndVerify(controller, c2, grantedAndDenied.first,
                grantedAndDenied.second, 1);
        // a reg changed event should happen after wait.
        verifyDelegateRegistrationChangedEvent(1, 2 * THROTTLE_MS);
    }

    @SmallTest
    @Test
    public void createTwoAndTriggerRoleChange() throws Exception {
        SipTransportController controller = setupLiveTransportController(0 /*reeval*/, THROTTLE_MS);

        DelegateRequest firstDelegateRequest = getBaseDelegateRequest();
        Set<FeatureTagState> firstDeniedTags = getDeniedTagsForReason(
                firstDelegateRequest.getFeatureTags(),
                SipDelegateManager.DENIED_REASON_NOT_ALLOWED);
        SipDelegateControllerContainer c1 = injectMockDelegateController(TEST_PACKAGE_NAME,
                firstDelegateRequest);
        createDelegateAndVerify(controller, c1, firstDelegateRequest.getFeatureTags(),
                Collections.emptySet());
        verifyDelegateRegistrationChangedEvent(1 /*times*/, THROTTLE_MS);

        DelegateRequest secondDelegateRequest = getBaseDelegateRequest();
        Set<FeatureTagState> secondDeniedTags = getDeniedTagsForReason(
                secondDelegateRequest.getFeatureTags(),
                SipDelegateManager.DENIED_REASON_NOT_ALLOWED);
        // Try to create a SipDelegate for a package that is not the default sms role.
        SipDelegateControllerContainer c2 = injectMockDelegateController(TEST_PACKAGE_NAME_2,
                secondDelegateRequest);
        createDelegateAndVerify(controller, c2, Collections.emptySet(), secondDeniedTags, 1);

        // now swap the SMS role.
        CompletableFuture<Boolean> pendingC1Change = setChangeSupportedFeatureTagsFuture(
                c1.delegateController, Collections.emptySet(), firstDeniedTags);
        CompletableFuture<Boolean> pendingC2Change = setChangeSupportedFeatureTagsFuture(
                c2.delegateController, secondDelegateRequest.getFeatureTags(),
                Collections.emptySet());
        setSmsRoleAndEvaluate(controller, TEST_PACKAGE_NAME_2);
        // swapping roles should trigger a deregistration event on the ImsService side.
        verifyDelegateDeregistrationEvent();
        // there should also not be any new registration changed events
        verifyDelegateRegistrationChangedEvent(1 /*times*/, THROTTLE_MS);
        // trigger completion stage to run
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verify(c1.delegateController).changeSupportedFeatureTags(Collections.emptySet(),
                firstDeniedTags);
        // we should not get a change for c2 until pendingC1Change completes.
        verify(c2.delegateController, never()).changeSupportedFeatureTags(
                secondDelegateRequest.getFeatureTags(), Collections.emptySet());
        // ensure we are not blocking executor here
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        completePendingChange(pendingC1Change, true);
        // trigger completion stage to run
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verify(c2.delegateController).changeSupportedFeatureTags(
                secondDelegateRequest.getFeatureTags(), Collections.emptySet());
        // ensure we are not blocking executor here
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        completePendingChange(pendingC2Change, true);
        // verify we now get a second registration changed event
        verifyDelegateRegistrationChangedEvent(2 /*times*/, THROTTLE_MS);
    }

    @SmallTest
    @Test
    public void createTwoAndDestroyOlder() throws Exception {
        SipTransportController controller = setupLiveTransportController(0 /*reeval*/, THROTTLE_MS);

        // First delegate requests RCS message + File transfer
        ArraySet<String> firstDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        firstDelegate.remove(ImsSignallingUtils.GROUP_CHAT_TAG);
        DelegateRequest firstDelegateRequest = new DelegateRequest(firstDelegate);
        SipDelegateControllerContainer c1 = injectMockDelegateController(TEST_PACKAGE_NAME,
                firstDelegateRequest);
        createDelegateAndVerify(controller, c1, firstDelegate, Collections.emptySet());
        verifyNoDelegateRegistrationChangedEvent();

        // First delegate requests RCS message + Group RCS message. For this delegate, single RCS
        // message should be denied.
        ArraySet<String> secondDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        secondDelegate.remove(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG);
        DelegateRequest secondDelegateRequest = new DelegateRequest(secondDelegate);
        Pair<Set<String>, Set<FeatureTagState>> grantedAndDenied = getAllowedAndDeniedTagsForConfig(
                secondDelegateRequest, SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE,
                firstDelegate);
        SipDelegateControllerContainer c2 = injectMockDelegateController(TEST_PACKAGE_NAME,
                secondDelegateRequest);
        createDelegateAndVerify(controller, c2, grantedAndDenied.first, grantedAndDenied.second, 1);
        verifyNoDelegateRegistrationChangedEvent();

        // Destroy the firstDelegate, which should now cause all previously denied tags to be
        // granted to the new delegate.
        CompletableFuture<Boolean> pendingC2Change = setChangeSupportedFeatureTagsFuture(
                c2.delegateController, secondDelegate, Collections.emptySet());
        destroyDelegateAndVerify(controller, c1, false /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        // wait for create to be processed.
        assertTrue(waitForExecutorAction(mExecutorService, TIMEOUT_MS));
        verify(c2.delegateController).changeSupportedFeatureTags(secondDelegate,
                Collections.emptySet());
        completePendingChange(pendingC2Change, true);

        verifyDelegateRegistrationChangedEvent(1 /*times*/, THROTTLE_MS);
    }

    @SmallTest
    @Test
    public void testThrottling() throws Exception {
        SipTransportController controller = setupLiveTransportController(THROTTLE_MS, THROTTLE_MS);

        // First delegate requests RCS message + File transfer
        ArraySet<String> firstDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        firstDelegate.remove(ImsSignallingUtils.GROUP_CHAT_TAG);
        DelegateRequest firstDelegateRequest = new DelegateRequest(firstDelegate);
        SipDelegateControllerContainer c1 = injectMockDelegateController(TEST_PACKAGE_NAME,
                firstDelegateRequest);
        CompletableFuture<Boolean> pendingC1Change = createDelegate(controller, c1,
                firstDelegate, Collections.emptySet());

        // Request RCS message + group RCS Message. For this delegate, single RCS message should be
        // denied.
        ArraySet<String> secondDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        secondDelegate.remove(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG);
        DelegateRequest secondDelegateRequest = new DelegateRequest(secondDelegate);
        Pair<Set<String>, Set<FeatureTagState>> grantedAndDeniedC2 =
                getAllowedAndDeniedTagsForConfig(secondDelegateRequest,
                        SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE, firstDelegate);
        SipDelegateControllerContainer c2 = injectMockDelegateController(TEST_PACKAGE_NAME,
                secondDelegateRequest);
        CompletableFuture<Boolean> pendingC2Change = createDelegate(controller, c2,
                grantedAndDeniedC2.first, grantedAndDeniedC2.second);

        // Request group RCS message + file transfer. All should be denied at first
        ArraySet<String> thirdDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        thirdDelegate.remove(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);
        DelegateRequest thirdDelegateRequest = new DelegateRequest(thirdDelegate);
        Pair<Set<String>, Set<FeatureTagState>> grantedAndDeniedC3 =
                getAllowedAndDeniedTagsForConfig(thirdDelegateRequest,
                        SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE, firstDelegate,
                        grantedAndDeniedC2.first);
        SipDelegateControllerContainer c3 = injectMockDelegateController(TEST_PACKAGE_NAME,
                thirdDelegateRequest);
        CompletableFuture<Boolean> pendingC3Change = createDelegate(controller, c3,
                grantedAndDeniedC3.first, grantedAndDeniedC3.second);

        verifyNoDelegateRegistrationChangedEvent();
        assertTrue(scheduleDelayedWait(2 * THROTTLE_MS));
        verifyDelegateChanged(c1.delegateController, pendingC1Change, firstDelegate,
                Collections.emptySet(), 0);
        verifyDelegateChanged(c2.delegateController, pendingC2Change, grantedAndDeniedC2.first,
                grantedAndDeniedC2.second, 0);
        verifyDelegateChanged(c3.delegateController, pendingC3Change, grantedAndDeniedC3.first,
                grantedAndDeniedC3.second, 0);
        verifyDelegateRegistrationChangedEvent(1, 2 * THROTTLE_MS);

        // Destroy the first and second controller in quick succession, this should only generate
        // one reevaluate for the third controller.
        CompletableFuture<Boolean> pendingChangeC3 = setChangeSupportedFeatureTagsFuture(
                c3.delegateController, thirdDelegate, Collections.emptySet());
        CompletableFuture<Integer> pendingDestroyC1 = destroyDelegate(controller,
                c1.delegateController, false /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        CompletableFuture<Integer> pendingDestroyC2 = destroyDelegate(controller,
                c2.delegateController, false /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertTrue(scheduleDelayedWait(2 * THROTTLE_MS));
        verifyDestroyDelegate(c1.delegateController, pendingDestroyC1, false /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verifyDestroyDelegate(c2.delegateController, pendingDestroyC2, false /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);

        // All requested features should now be granted
        completePendingChange(pendingChangeC3, true);
        verify(c3.delegateController)
                .changeSupportedFeatureTags(thirdDelegate, Collections.emptySet());
        // In total reeval should have only been called twice.
        verify(c3.delegateController, times(2))
                .changeSupportedFeatureTags(any(), any());
        verifyDelegateRegistrationChangedEvent(2 /*times*/, 2 * THROTTLE_MS);
    }

    @SmallTest
    @Test
    public void testSubIdChangeDestroyTriggered() throws Exception {
        SipTransportController controller = setupLiveTransportController();

        ArraySet<String> firstDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        DelegateRequest firstDelegateRequest = new DelegateRequest(firstDelegate);
        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME,
                firstDelegateRequest);
        createDelegateAndVerify(controller, c, firstDelegate, Collections.emptySet());
        verifyDelegateRegistrationChangedEvent(1 /*times*/, 0 /*waitMs*/);

        CompletableFuture<Integer> pendingDestroy =  setDestroyFuture(c.delegateController, true,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN);
        controller.onAssociatedSubscriptionUpdated(TEST_SUB_ID + 1);
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verifyDestroyDelegate(c.delegateController, pendingDestroy, true /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN);
        verifyDelegateRegistrationChangedEvent(2 /*times*/, 0 /*waitMs*/);
    }

    @SmallTest
    @Test
    public void testRcsManagerGoneDestroyTriggered() throws Exception {
        SipTransportController controller = setupLiveTransportController();

        ArraySet<String> firstDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        DelegateRequest firstDelegateRequest = new DelegateRequest(firstDelegate);
        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME,
                firstDelegateRequest);
        createDelegateAndVerify(controller, c, firstDelegate, Collections.emptySet());

        CompletableFuture<Integer> pendingDestroy =  setDestroyFuture(c.delegateController, true,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        controller.onRcsDisconnected();
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verifyDestroyDelegate(c.delegateController, pendingDestroy, true /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        verifyDelegateRegistrationChangedEvent(1, 0 /*waitMs*/);
    }

    @SmallTest
    @Test
    public void testDestroyTriggered() throws Exception {
        SipTransportController controller = setupLiveTransportController();

        ArraySet<String> firstDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        DelegateRequest firstDelegateRequest = new DelegateRequest(firstDelegate);
        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME,
                firstDelegateRequest);
        createDelegateAndVerify(controller, c, firstDelegate, Collections.emptySet());

        CompletableFuture<Integer> pendingDestroy =  setDestroyFuture(c.delegateController,
                true, SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN);
        controller.onDestroy();
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verifyDelegateDeregistrationEvent();
        // verify change was called.
        verify(c.delegateController).destroy(true /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN);
        // ensure thread is not blocked while waiting for pending complete.
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        completePendingDestroy(pendingDestroy,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN);
    }

    @SmallTest
    @Test
    public void testTimingSubIdChangedAndCreateNewSubId() throws Exception {
        SipTransportController controller = setupLiveTransportController(THROTTLE_MS, 0);
        setFeatureAllowedConfig(TEST_SUB_ID + 1, new String[]{ImsSignallingUtils.MMTEL_TAG,
                ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG, ImsSignallingUtils.GROUP_CHAT_TAG,
                ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG});

        ArraySet<String> firstDelegate = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        DelegateRequest firstDelegateRequest = new DelegateRequest(firstDelegate);
        SipDelegateControllerContainer c1 = injectMockDelegateController(TEST_PACKAGE_NAME,
                firstDelegateRequest);
        CompletableFuture<Boolean> pendingC1Change = createDelegate(controller, c1,
                firstDelegate, Collections.emptySet());
        assertTrue(scheduleDelayedWait(2 * THROTTLE_MS));
        verifyDelegateChanged(c1.delegateController, pendingC1Change, firstDelegate,
                Collections.emptySet(), 0);


        CompletableFuture<Integer> pendingDestroy =  setDestroyFuture(c1.delegateController,
                true, SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN);
        // triggers reeval now.
        controller.onAssociatedSubscriptionUpdated(TEST_SUB_ID + 1);
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);

        // mock a second delegate with the new subId associated with the slot.
        ArraySet<String> secondDelegate = new ArraySet<>();
        secondDelegate.add(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);
        secondDelegate.add(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG);
        DelegateRequest secondDelegateRequest = new DelegateRequest(secondDelegate);
        SipDelegateControllerContainer c2 = injectMockDelegateController(TEST_SUB_ID + 1,
                TEST_PACKAGE_NAME, secondDelegateRequest);
        CompletableFuture<Boolean> pendingC2Change = createDelegate(controller, c2, secondDelegate,
                Collections.emptySet());
        assertTrue(scheduleDelayedWait(THROTTLE_MS));

        //trigger destroyed event
        verifyDestroyDelegate(c1.delegateController, pendingDestroy, true /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN);
        assertTrue(scheduleDelayedWait(2 * THROTTLE_MS));
        verifyDelegateChanged(c2.delegateController, pendingC2Change, secondDelegate,
                Collections.emptySet(), 0);
    }

    @SmallTest
    @Test
    public void testFeatureTagsDeniedByConfig() throws Exception {
        setFeatureAllowedConfig(TEST_SUB_ID, new String[]{ImsSignallingUtils.GROUP_CHAT_TAG,
                ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG});
        SipTransportController controller = setupLiveTransportController(THROTTLE_MS, 0);

        ArraySet<String> requestTags = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        ArraySet<String> allowedTags = new ArraySet<>(requestTags);
        ArraySet<String> deniedTags = new ArraySet<>();
        DelegateRequest delegateRequest = new DelegateRequest(requestTags);
        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME,
                delegateRequest);
        CompletableFuture<Boolean> pendingScChange = createDelegate(controller, c, requestTags,
                Collections.emptySet());
        allowedTags.remove(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);
        deniedTags.add(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);

        assertTrue(scheduleDelayedWait(2 * THROTTLE_MS));
        verifyDelegateChanged(c.delegateController, pendingScChange, allowedTags,
                getDeniedTagsForReason(deniedTags,
                        SipDelegateManager.DENIED_REASON_NOT_ALLOWED), 0);
    }

    @SmallTest
    @Test
    public void testFeatureTagsDeniedByOverride() throws Exception {
        RcsProvisioningMonitor.getInstance().overrideImsFeatureValidation(TEST_SUB_ID, false);
        SipTransportController controller = setupLiveTransportController(THROTTLE_MS, 0);

        ArraySet<String> requestTags = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        ArraySet<String> deniedTags = new ArraySet<>(requestTags);
        DelegateRequest delegateRequest = new DelegateRequest(requestTags);
        SipDelegateControllerContainer c = injectMockDelegateController(TEST_PACKAGE_NAME,
                delegateRequest);
        CompletableFuture<Boolean> pendingScChange = createDelegate(controller, c, requestTags,
                Collections.emptySet());

        assertTrue(scheduleDelayedWait(2 * THROTTLE_MS));
        verifyDelegateChanged(c.delegateController, pendingScChange, Collections.emptySet(),
                getDeniedTagsForReason(deniedTags,
                        SipDelegateManager.DENIED_REASON_NOT_ALLOWED), 0);
    }

    @SmallTest
    @Test
    public void testFeatureTagsDeniedByConfigAllowedByOverride() throws Exception {
        setFeatureAllowedConfig(TEST_SUB_ID, new String[]{});
        RcsProvisioningMonitor.getInstance().overrideImsFeatureValidation(TEST_SUB_ID, true);
        SipTransportController controller = setupLiveTransportController(THROTTLE_MS, 0);

        ArraySet<String> requestTags = new ArraySet<>(getBaseDelegateRequest().getFeatureTags());
        ArraySet<String> allowedTags = new ArraySet<>(requestTags);
        DelegateRequest delegateRequest = new DelegateRequest(requestTags);
        SipDelegateControllerContainer controllerContainer =
                injectMockDelegateController(TEST_PACKAGE_NAME, delegateRequest);
        CompletableFuture<Boolean> pendingScChange = createDelegate(controller, controllerContainer,
                requestTags, Collections.emptySet());

        assertTrue(scheduleDelayedWait(2 * THROTTLE_MS));
        verifyDelegateChanged(controllerContainer.delegateController, pendingScChange, allowedTags,
                Collections.emptySet(), 0);
    }

    @SafeVarargs
    private final Pair<Set<String>, Set<FeatureTagState>> getAllowedAndDeniedTagsForConfig(
            DelegateRequest r, int denyReason, Set<String>... previousRequestedTagSets) {
        ArraySet<String> rejectedTags = new ArraySet<>(r.getFeatureTags());
        ArraySet<String> grantedTags = new ArraySet<>(r.getFeatureTags());
        Set<String> previousRequestedTags = new ArraySet<>();
        for (Set<String> s : previousRequestedTagSets) {
            previousRequestedTags.addAll(s);
        }
        rejectedTags.retainAll(previousRequestedTags);
        grantedTags.removeAll(previousRequestedTags);
        Set<FeatureTagState> deniedTags = getDeniedTagsForReason(rejectedTags, denyReason);
        return new Pair<>(grantedTags, deniedTags);
    }

    private void completePendingChange(CompletableFuture<Boolean> change, boolean result) {
        mExecutorService.execute(() -> change.complete(result));
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
    }

    private void completePendingDestroy(CompletableFuture<Integer> destroy, int result) {
        mExecutorService.execute(() -> destroy.complete(result));
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
    }

    private SipTransportController setupLiveTransportController() throws Exception {
        return setupLiveTransportController(0 /*throttleMs*/, 0 /*regDelayMs*/);
    }

    private SipTransportController setupLiveTransportController(int throttleMs, int regDelayMs)
            throws Exception {
        mExecutorService = Executors.newSingleThreadScheduledExecutor();
        SipTransportController controller = createControllerAndThrottle(mExecutorService,
                throttleMs, regDelayMs);
        doReturn(mSipTransport).when(mRcsManager).getSipTransport();
        controller.onAssociatedSubscriptionUpdated(TEST_SUB_ID);
        controller.onRcsConnected(mRcsManager);
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        return controller;
    }

    private void createDelegateAndVerify(SipTransportController controller,
            SipDelegateControllerContainer controllerContainer, Set<String> allowedTags,
            Set<FeatureTagState> deniedTags, int numPreviousChanges) {

        CompletableFuture<Boolean> pendingChange = createDelegate(controller, controllerContainer,
                allowedTags, deniedTags);
        verifyDelegateChanged(controllerContainer.delegateController, pendingChange, allowedTags,
                deniedTags, numPreviousChanges);
    }

    private void createDelegateAndVerify(SipTransportController controller,
            SipDelegateControllerContainer controllerContainer, Set<String> allowedTags,
            Set<FeatureTagState> deniedTags) {
        createDelegateAndVerify(controller, controllerContainer, allowedTags, deniedTags, 0);
    }

    private CompletableFuture<Boolean> createDelegate(SipTransportController controller,
            SipDelegateControllerContainer delegateControllerContainer, Set<String> allowedTags,
            Set<FeatureTagState> deniedTags) {
        CompletableFuture<Boolean> pendingChange = setChangeSupportedFeatureTagsFuture(
                delegateControllerContainer.delegateController, allowedTags, deniedTags);
        try {
            controller.createSipDelegate(delegateControllerContainer.subId, TEST_UID,
                    delegateControllerContainer.delegateRequest,
                    delegateControllerContainer.packageName,
                    delegateControllerContainer.mockDelegateConnectionCallback,
                    delegateControllerContainer.mockMessageCallback);
        } catch (ImsException e) {
            fail("ImsException thrown:" + e);
        }
        // move to internal & schedule eval
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        // reeval
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        return pendingChange;
    }

    private void verifyDelegateChanged(SipDelegateController delegateController,
            CompletableFuture<Boolean> pendingChange, Set<String> allowedTags,
            Set<FeatureTagState> deniedTags, int numPreviousChangeStages) {
        // empty the queue of pending changeSupportedFeatureTags before running the one we are
        // interested in, since the reevaluate waits for one stage to complete before moving to the
        // next.
        for (int i = 0; i < numPreviousChangeStages + 1; i++) {
            assertTrue(waitForExecutorAction(mExecutorService, TIMEOUT_MS));
        }
        // verify change was called.
        verify(delegateController).changeSupportedFeatureTags(allowedTags, deniedTags);
        // ensure thread is not blocked while waiting for pending complete.
        assertTrue(waitForExecutorAction(mExecutorService, TIMEOUT_MS));
        completePendingChange(pendingChange, true);
        // process pending change.
        assertTrue(waitForExecutorAction(mExecutorService, TIMEOUT_MS));
    }

    private void destroyDelegateAndVerify(SipTransportController controller,
            SipDelegateControllerContainer controllerContainer, boolean force, int reason) {
        SipDelegateController delegateController = controllerContainer.delegateController;
        CompletableFuture<Integer> pendingDestroy =  destroyDelegate(controller, delegateController,
                force, reason);
        verifyDestroyDelegate(delegateController, pendingDestroy, force, reason);
    }

    private CompletableFuture<Integer> destroyDelegate(SipTransportController controller,
            SipDelegateController delegateController, boolean force, int reason) {
        CompletableFuture<Integer> pendingDestroy =  setDestroyFuture(delegateController, force,
                reason);
        controller.destroySipDelegate(TEST_SUB_ID, delegateController.getSipDelegateInterface(),
                reason);
        // move to internal & schedule eval
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        // reeval
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        return pendingDestroy;
    }

    private void verifyDestroyDelegate(SipDelegateController delegateController,
            CompletableFuture<Integer> pendingDestroy, boolean force, int reason) {
        // verify destroy was called.
        verify(delegateController).destroy(force, reason);
        // ensure thread is not blocked while waiting for pending complete.
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        completePendingDestroy(pendingDestroy, reason);
    }

    private void triggerFullNetworkRegistrationAndVerify(SipTransportController controller,
            SipDelegateControllerContainer controllerContainer) {
        SipDelegateController delegateController = controllerContainer.delegateController;
        controller.triggerFullNetworkRegistration(TEST_SUB_ID,
                delegateController.getSipDelegateInterface(), 403, "forbidden");
        // move to internal & trigger event
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verify(delegateController).triggerFullNetworkRegistration(403, "forbidden");
    }

    private void triggerFullNetworkRegistrationAndVerifyNever(SipTransportController controller,
            SipDelegateControllerContainer controllerContainer) {
        SipDelegateController delegateController = controllerContainer.delegateController;
        controller.triggerFullNetworkRegistration(TEST_SUB_ID,
                delegateController.getSipDelegateInterface(), 403, "forbidden");
        // move to internal & potentially trigger event
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verify(delegateController, never()).triggerFullNetworkRegistration(anyInt(), anyString());
    }

    private DelegateRequest getBaseDelegateRequest() {
        Set<String> featureTags = new ArraySet<>();
        featureTags.add(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);
        featureTags.add(ImsSignallingUtils.GROUP_CHAT_TAG);
        featureTags.add(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG);
        return new DelegateRequest(featureTags);
    }

    private Set<FeatureTagState> getDeniedTagsForReason(Set<String> deniedTags, int reason) {
        return deniedTags.stream().map(t -> new FeatureTagState(t, reason))
                .collect(Collectors.toSet());
    }

    private SipDelegateControllerContainer injectMockDelegateController(String packageName,
            DelegateRequest r) {
        return injectMockDelegateController(TEST_SUB_ID, packageName, r);
    }

    private SipDelegateControllerContainer injectMockDelegateController(int subId,
            String packageName, DelegateRequest r) {
        SipDelegateControllerContainer c = new SipDelegateControllerContainer(subId,
                packageName, r);
        mMockControllers.add(c);
        return c;
    }

    private SipDelegateController getMockDelegateController(int subId, String packageName,
            DelegateRequest r) {
        return mMockControllers.stream()
                .filter(c -> c.subId == subId && c.packageName.equals(packageName)
                        && c.delegateRequest.equals(r))
                .map(c -> c.delegateController).findFirst().orElse(null);
    }

    private CompletableFuture<Boolean> setChangeSupportedFeatureTagsFuture(SipDelegateController c,
            Set<String> supportedSet, Set<FeatureTagState> deniedSet) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        doReturn(result).when(c).changeSupportedFeatureTags(eq(supportedSet), eq(deniedSet));
        return result;
    }

    private CompletableFuture<Integer> setDestroyFuture(SipDelegateController c, boolean force,
            int destroyReason) {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        doReturn(result).when(c).destroy(force, destroyReason);
        return result;
    }

    private void setSmsRoleAndEvaluate(SipTransportController c, String packageName) {
        verify(mMockRoleManager).addOnRoleHoldersChangedListenerAsUser(any(), any(), any());
        mSmsPackageName.clear();
        mSmsPackageName.add(packageName);
        c.onRoleHoldersChanged(RoleManager.ROLE_SMS, UserHandle.SYSTEM);
        // finish internal throttled re-evaluate
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
    }

    private void verifyNoDelegateRegistrationChangedEvent() throws Exception {
        // event is scheduled and then executed.
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verify(mImsRegistration, never()).triggerUpdateSipDelegateRegistration();
    }

    private void verifyDelegateRegistrationChangedEvent(int times, int waitMs)
            throws Exception {
        // event is scheduled and then executed.
        assertTrue(scheduleDelayedWait(waitMs));
        waitForExecutorAction(mExecutorService, TIMEOUT_MS);
        verify(mImsRegistration, times(times)).triggerUpdateSipDelegateRegistration();
    }


    private void verifyDelegateDeregistrationEvent() throws Exception {
        verify(mImsRegistration).triggerSipDelegateDeregistration();
    }

    private SipTransportController createController(ScheduledExecutorService e) {
        return createControllerAndThrottle(e, 0 /*throttleMs*/, 0 /*regDelayMs*/);
    }

    private SipTransportController createControllerAndThrottle(ScheduledExecutorService e,
            int throttleMs, int regDelayMs) {
        return new SipTransportController(mContext, 0 /*slotId*/, TEST_SUB_ID,
                mMockDelegateControllerFactory, mMockRoleManager,
                // Remove delays for testing.
                new SipTransportController.TimerAdapter() {
                    @Override
                    public int getReevaluateThrottleTimerMilliseconds() {
                        return throttleMs;
                    }

                    @Override
                    public int getUpdateRegistrationDelayMilliseconds() {
                        return regDelayMs;
                    }
                }, e);
    }

    private boolean scheduleDelayedWait(long timeMs) {
        CountDownLatch l = new CountDownLatch(1);
        mExecutorService.schedule(l::countDown, timeMs, TimeUnit.MILLISECONDS);
        while (l.getCount() > 0) {
            try {
                return l.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // try again
            }
        }
        return true;
    }

    private void setFeatureAllowedConfig(int subId, String[] tags) {
        PersistableBundle bundle = mContext.getCarrierConfig(subId);
        bundle.putStringArray(
                CarrierConfigManager.Ims.KEY_RCS_FEATURE_TAG_ALLOWED_STRING_ARRAY, tags);
    }
}
