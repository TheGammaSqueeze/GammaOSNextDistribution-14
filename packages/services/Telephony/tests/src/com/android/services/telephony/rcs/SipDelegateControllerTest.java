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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Binder;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.TestExecutorService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class SipDelegateControllerTest extends TelephonyTestBase {
    private static final int TEST_SUB_ID = 1;

    @Mock private ISipDelegate mMockSipDelegate;
    @Mock private MessageTransportWrapper mMockMessageTracker;
    @Mock private ISipDelegateMessageCallback mMockMessageCallback;
    @Mock private DelegateStateTracker mMockDelegateStateTracker;
    @Mock private DelegateBinderStateManager mMockBinderConnection;
    @Captor private ArgumentCaptor<BiConsumer<ISipDelegate, Set<FeatureTagState>>> mCreatedCaptor;
    @Captor private ArgumentCaptor<Consumer<Boolean>> mBooleanConsumerCaptor;
    @Captor private ArgumentCaptor<Consumer<Integer>> mIntegerConsumerCaptor;

    private ScheduledExecutorService mExecutorService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(mMockMessageTracker.getMessageCallback()).thenReturn(mMockMessageCallback);
        mExecutorService = new TestExecutorService();
    }

    @After
    public void tearDown() throws Exception {
        mExecutorService.shutdownNow();
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testCreateDelegate() throws Exception {
        DelegateRequest request = getBaseDelegateRequest();
        SipDelegateController controller = getTestDelegateController(request,
                Collections.emptySet());

        doReturn(true).when(mMockBinderConnection).create(eq(mMockMessageCallback), any());
        CompletableFuture<Boolean> future = controller.create(request.getFeatureTags(),
                Collections.emptySet() /*denied tags*/);
        BiConsumer<ISipDelegate, Set<FeatureTagState>> consumer =
                verifyConnectionCreated(1);
        assertNotNull(consumer);

        assertFalse(future.isDone());
        consumer.accept(mMockSipDelegate, Collections.emptySet());
        assertTrue(future.get());
        verify(mMockMessageTracker).openTransport(mMockSipDelegate, request.getFeatureTags(),
                Collections.emptySet());
        verify(mMockDelegateStateTracker).sipDelegateConnected(request.getFeatureTags(),
                Collections.emptySet());
    }

    @SmallTest
    @Test
    public void testCreateDeniedFeatures() throws Exception {
        DelegateRequest request = getLargeDelegateRequest();
        ArraySet<FeatureTagState> deniedTags = new ArraySet<>(1);
        deniedTags.add(new FeatureTagState(ImsSignallingUtils.GROUP_CHAT_TAG,
                SipDelegateManager.DENIED_REASON_NOT_ALLOWED));
        SipDelegateController controller = getTestDelegateController(request,
                deniedTags);

        doReturn(true).when(mMockBinderConnection).create(eq(mMockMessageCallback), any());
        CompletableFuture<Boolean> future = controller.create(request.getFeatureTags(),
                deniedTags);
        BiConsumer<ISipDelegate, Set<FeatureTagState>> consumer =
                verifyConnectionCreated(1);
        assertNotNull(consumer);

        assertFalse(future.isDone());
        // Send in additional tags denied by the service
        deniedTags.add(new FeatureTagState(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG,
                SipDelegateManager.DENIED_REASON_NOT_ALLOWED));
        consumer.accept(mMockSipDelegate, deniedTags);
        assertTrue(future.get());
        // Allowed tags should be initial request set - denied tags
        ArraySet<String> allowedTags = new ArraySet<>(request.getFeatureTags());
        allowedTags.removeAll(deniedTags.stream().map(FeatureTagState::getFeatureTag)
                .collect(Collectors.toSet()));
        verify(mMockMessageTracker).openTransport(mMockSipDelegate, allowedTags, deniedTags);
        verify(mMockDelegateStateTracker).sipDelegateConnected(allowedTags, deniedTags);
    }

    @SmallTest
    @Test
    public void testCreateDelegateTransportDied() throws Exception {
        DelegateRequest request = getBaseDelegateRequest();
        SipDelegateController controller = getTestDelegateController(request,
                Collections.emptySet());

        //Create operation fails
        doReturn(false).when(mMockBinderConnection).create(eq(mMockMessageCallback), any());
        CompletableFuture<Boolean> future = controller.create(request.getFeatureTags(),
                Collections.emptySet() /*denied tags*/);

        assertFalse(future.get());
    }

    @SmallTest
    @Test
    public void testDestroyDelegate() throws Exception {
        DelegateRequest request = getBaseDelegateRequest();
        SipDelegateController controller = getTestDelegateController(request,
                Collections.emptySet());
        createSipDelegate(request, controller);

        CompletableFuture<Integer> pendingDestroy = controller.destroy(false /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertFalse(pendingDestroy.isDone());
        Consumer<Boolean> pendingClosedConsumer = verifyMessageTrackerCloseGracefully();
        verify(mMockDelegateStateTracker).sipDelegateChanging(
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING);

        // verify we do not call destroy on the delegate until the message tracker releases the
        // transport.
        verify(mMockBinderConnection, never()).destroy(anyInt(), any());
        pendingClosedConsumer.accept(true);
        Consumer<Integer> pendingDestroyedConsumer = verifyBinderConnectionDestroy();
        pendingDestroyedConsumer.accept(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(mMockDelegateStateTracker).sipDelegateDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertTrue(pendingDestroy.isDone());
        assertEquals(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP,
                pendingDestroy.get().intValue());
    }

    @SmallTest
    @Test
    public void testDestroyDelegateForce() throws Exception {
        DelegateRequest request = getBaseDelegateRequest();
        SipDelegateController controller = getTestDelegateController(request,
                Collections.emptySet());
        createSipDelegate(request, controller);

        CompletableFuture<Integer> pendingDestroy = controller.destroy(true /*force*/,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertFalse(pendingDestroy.isDone());
        // Do not wait for message transport close in this case.
        verify(mMockMessageTracker).close(
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED);
        verify(mMockDelegateStateTracker, never()).sipDelegateChanging(
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING);

        //verify destroy is called
        Consumer<Integer> pendingDestroyedConsumer = verifyBinderConnectionDestroy();
        pendingDestroyedConsumer.accept(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(mMockDelegateStateTracker).sipDelegateDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        assertTrue(pendingDestroy.isDone());
        assertEquals(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP,
                pendingDestroy.get().intValue());
    }

    @SmallTest
    @Test
    public void testChangeSupportedFeatures() throws Exception {
        DelegateRequest request = getBaseDelegateRequest();
        SipDelegateController controller = getTestDelegateController(request,
                Collections.emptySet());
        createSipDelegate(request, controller);

        Set<String> newFts = getBaseFTSet();
        newFts.add(ImsSignallingUtils.GROUP_CHAT_TAG);
        CompletableFuture<Boolean> pendingChange = controller.changeSupportedFeatureTags(
                newFts, Collections.emptySet());
        assertFalse(pendingChange.isDone());
        // message tracker should close gracefully.
        Consumer<Boolean> pendingClosedConsumer = verifyMessageTrackerCloseGracefully();
        verify(mMockDelegateStateTracker).sipDelegateChanging(
                DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING);
        verify(mMockBinderConnection, never()).destroy(anyInt(), any());
        pendingClosedConsumer.accept(true);
        Consumer<Integer> pendingDestroyedConsumer = verifyBinderConnectionDestroy();
        pendingDestroyedConsumer.accept(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(mMockDelegateStateTracker, never()).sipDelegateDestroyed(anyInt());

        // This will cause any exceptions to be printed if something completed exceptionally.
        assertNull(pendingChange.getNow(null));
        BiConsumer<ISipDelegate, Set<FeatureTagState>> consumer =
                verifyConnectionCreated(2);
        assertNotNull(consumer);
        consumer.accept(mMockSipDelegate, Collections.emptySet());
        assertTrue(pendingChange.get());

        verify(mMockMessageTracker).openTransport(mMockSipDelegate, request.getFeatureTags(),
                Collections.emptySet());
        verify(mMockMessageTracker).openTransport(mMockSipDelegate, newFts,
                Collections.emptySet());
        verify(mMockDelegateStateTracker).sipDelegateConnected(
                request.getFeatureTags(), Collections.emptySet());
        verify(mMockDelegateStateTracker).sipDelegateConnected(newFts, Collections.emptySet());
    }

    private void createSipDelegate(DelegateRequest request, SipDelegateController controller)
            throws Exception {
        doReturn(true).when(mMockBinderConnection).create(eq(mMockMessageCallback), any());
        CompletableFuture<Boolean> future = controller.create(request.getFeatureTags(),
                Collections.emptySet() /*denied tags*/);
        BiConsumer<ISipDelegate, Set<FeatureTagState>> consumer =
                verifyConnectionCreated(1);
        assertNotNull(consumer);
        consumer.accept(mMockSipDelegate, Collections.emptySet());
        assertTrue(future.get());
    }

    private ArraySet<String> getBaseFTSet() {
        ArraySet<String> request = new ArraySet<>();
        request.add(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);
        return request;
    }

    private ArraySet<String> getLargeFTSet() {
        ArraySet<String> request = new ArraySet<>();
        request.add(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);
        request.add(ImsSignallingUtils.GROUP_CHAT_TAG);
        request.add(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG);
        return request;
    }

    private DelegateRequest getBaseDelegateRequest() {
        return new DelegateRequest(getBaseFTSet());
    }

    private DelegateRequest getLargeDelegateRequest() {
        return new DelegateRequest(getLargeFTSet());
    }

    private SipDelegateController getTestDelegateController(DelegateRequest request,
            Set<FeatureTagState> deniedSet) {
        return new SipDelegateController(TEST_SUB_ID, Binder.getCallingUid(), request, "",
                mExecutorService, mMockMessageTracker, mMockDelegateStateTracker,
                (a, b, deniedFeatureSet, d, e) ->  {
                    assertEquals(deniedSet, deniedFeatureSet);
                    return mMockBinderConnection;
                });
    }

    private BiConsumer<ISipDelegate, Set<FeatureTagState>> verifyConnectionCreated(int numTimes) {
        verify(mMockBinderConnection, times(numTimes)).create(eq(mMockMessageCallback),
                mCreatedCaptor.capture());
        return mCreatedCaptor.getValue();
    }

    private Consumer<Boolean> verifyMessageTrackerCloseGracefully() {
        verify(mMockMessageTracker).closeGracefully(anyInt(), anyInt(),
                mBooleanConsumerCaptor.capture());
        return mBooleanConsumerCaptor.getValue();
    }
    private Consumer<Integer> verifyBinderConnectionDestroy() {
        verify(mMockBinderConnection).destroy(anyInt(), mIntegerConsumerCaptor.capture());
        return mIntegerConsumerCaptor.getValue();
    }

}
