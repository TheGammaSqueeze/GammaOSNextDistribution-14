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

import static com.android.services.telephony.rcs.DelegateStateTracker.SUPPORT_REGISTERING_DELEGATE_STATE;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.compat.CompatChanges;
import android.net.InetAddresses;
import android.os.Binder;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateConnectionStateCallback;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.metrics.RcsStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class DelegateStateTrackerTest extends TelephonyTestBase {
    private static final int TEST_SUB_ID = 1;

    @Mock private ISipDelegate mSipDelegate;
    @Mock private ISipDelegateConnectionStateCallback mAppCallback;
    @Mock private RcsStats mRcsStats;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * When an underlying SipDelegate is created, the app should only receive one onCreated callback
     * independent of how many times sipDelegateConnected is called. Once created, registration
     * and IMS configuration events should propagate up to the app as well.
     */
    @SmallTest
    @Test
    public void testDelegateCreated() throws Exception {
        DelegateStateTracker stateTracker = new DelegateStateTracker(TEST_SUB_ID,
                Binder.getCallingUid(), mAppCallback, mSipDelegate, mRcsStats);
        Set<FeatureTagState> deniedTags = getMmTelDeniedTag();
        Set<String> supportedTags = getSupportedTags();
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        // Calling connected multiple times should not generate multiple onCreated events.
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        verify(mAppCallback).onCreated(mSipDelegate);
        verify(mRcsStats).createSipDelegateStats(TEST_SUB_ID, supportedTags);

        // Ensure status updates are sent to app as expected.
        DelegateRegistrationState regState = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG)
                .build();
        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        stateTracker.onRegistrationStateChanged(regState);
        stateTracker.onConfigurationChanged(c);
        verify(mAppCallback).onFeatureTagStatusChanged(eq(regState),
                eq(new ArrayList<>(deniedTags)));
        verify(mRcsStats).onSipTransportFeatureTagStats(TEST_SUB_ID, deniedTags,
                regState.getDeregisteredFeatureTags(),
                regState.getRegisteredFeatureTags());
        verify(mAppCallback).onConfigurationChanged(c);
        verify(mAppCallback, never()).onDestroyed(anyInt());
    }

    /**
     * onDestroyed should be called when sipDelegateDestroyed is called.
     */
    @SmallTest
    @Test
    public void testDelegateDestroyed() throws Exception {
        DelegateStateTracker stateTracker = new DelegateStateTracker(TEST_SUB_ID,
                Binder.getCallingUid(), mAppCallback, mSipDelegate, mRcsStats);
        Set<FeatureTagState> deniedTags = getMmTelDeniedTag();
        Set<String> supportedTags = getSupportedTags();
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        verify(mRcsStats).createSipDelegateStats(eq(TEST_SUB_ID), eq(supportedTags));

        stateTracker.sipDelegateDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(mAppCallback).onDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(mRcsStats).onSipDelegateStats(eq(TEST_SUB_ID), eq(supportedTags),
                eq(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP));
    }

    /**
     * When a SipDelegate is created and then an event occurs that will destroy->create a new
     * SipDelegate underneath, we need to move the state of the features that are reporting
     * registered to DEREGISTERING_REASON_FEATURE_TAGS_CHANGING so that the app can close dialogs on
     * it. Once the new underlying SipDelegate is created, we must verify that the new registration
     * is propagated up without any overrides.
     */
    @SmallTest
    @Test
    public void testDelegateChangingRegisteredTagsOverride() throws Exception {
        DelegateStateTracker stateTracker = new DelegateStateTracker(TEST_SUB_ID,
                Binder.getCallingUid(), mAppCallback, mSipDelegate, mRcsStats);
        Set<FeatureTagState> deniedTags = getMmTelDeniedTag();
        Set<String> supportedTags = getSupportedTags();
        Set<String> registeringTags = getRegisteringTag();
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);

        // SipDelegate created
        verify(mAppCallback).onCreated(mSipDelegate);

        DelegateRegistrationState regState = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG)
                .addRegisteringFeatureTags(registeringTags)
                .addDeregisteringFeatureTag(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE)
                .addDeregisteredFeatureTag(ImsSignallingUtils.GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED)
                .build();

        stateTracker.onRegistrationStateChanged(regState);
        // Simulate underlying SipDelegate switch
        stateTracker.sipDelegateChanging(
                DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING);
        // onFeatureTagStatusChanged should now be called with registered features overridden with
        // DEREGISTERING_REASON_FEATURE_TAGS_CHANGING
        DelegateRegistrationState.Builder overrideBuilder =
                new DelegateRegistrationState.Builder()
                .addDeregisteringFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING)
                // Already Deregistering/Deregistered tags should not be overridden.
                .addDeregisteringFeatureTag(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE)
                .addDeregisteredFeatureTag(ImsSignallingUtils.GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED);
        if (CompatChanges.isChangeEnabled(SUPPORT_REGISTERING_DELEGATE_STATE,
                Binder.getCallingUid())) {
            for (String tag : registeringTags) {
                overrideBuilder.addDeregisteringFeatureTag(tag,
                        DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING);
            }
        } else {
            for (String tag : registeringTags) {
                overrideBuilder.addDeregisteredFeatureTag(tag,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED);
            }
        }

        DelegateRegistrationState overrideRegState = overrideBuilder.build();

        // new underlying SipDelegate created
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        stateTracker.onRegistrationStateChanged(regState);

        DelegateRegistrationState.Builder builder = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG)
                .addDeregisteringFeatureTag(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_PROVISIONING_CHANGE)
                .addDeregisteredFeatureTag(ImsSignallingUtils.GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED);
        if (CompatChanges.isChangeEnabled(SUPPORT_REGISTERING_DELEGATE_STATE,
                Binder.getCallingUid())) {
            builder.addRegisteringFeatureTags(registeringTags);
        } else {
            for (String tag : registeringTags) {
                builder.addDeregisteredFeatureTag(tag,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED);
            }
        }
        DelegateRegistrationState regStateResult = builder.build();
        // Verify registration state through the process:
        ArgumentCaptor<DelegateRegistrationState> regCaptor =
                ArgumentCaptor.forClass(DelegateRegistrationState.class);
        verify(mAppCallback, times(3)).onFeatureTagStatusChanged(
                regCaptor.capture(), eq(new ArrayList<>(deniedTags)));
        List<DelegateRegistrationState> testStates = regCaptor.getAllValues();
        // feature tags should first be registered
        assertEquals(regStateResult, testStates.get(0));
        // registered feature tags should have moved to deregistering
        assertEquals(overrideRegState, testStates.get(1));
        // and then moved back to registered after underlying FT change done.
        assertEquals(regStateResult, testStates.get(2));

        //onCreate should only have been called once and onDestroy should have never been called.
        verify(mAppCallback).onCreated(mSipDelegate);
        verify(mAppCallback, never()).onDestroyed(anyInt());
    }

    /**
     * When registration states are changed in a case that an application doesn't support the new
     * 'registering' state the 'registering' state will be moved to the 'registered' state
     * as the old behavior.
     *
     * This method tests the case where the application doesn't support consuming the
     * DEREGISTERING_REASON_LOSING_PDN reason.
     */
    @Test
    public void testDelegateChangingRegisteredTagsRegisteringDisable() throws Exception {
        DelegateStateTracker stateTracker = new DelegateStateTracker(TEST_SUB_ID,
                Binder.getCallingUid(), mAppCallback, mSipDelegate, mRcsStats);
        stateTracker.setCompatChangesFactory((changeId, uid) -> false);
        Set<FeatureTagState> deniedTags = getMmTelDeniedTag();
        Set<String> supportedTags = getSupportedTags();
        Set<String> registeringTags = getRegisteringTag();
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        // SipDelegate created
        verify(mAppCallback).onCreated(mSipDelegate);
        DelegateRegistrationState regState = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG)
                .addRegisteringFeatureTags(registeringTags)
                .addDeregisteringFeatureTag(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_LOSING_PDN)
                .addDeregisteredFeatureTag(ImsSignallingUtils.GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED)
                .build();
        stateTracker.onRegistrationStateChanged(regState);

        DelegateRegistrationState.Builder builder = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG)
                .addDeregisteringFeatureTag(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_PDN_CHANGE)
                .addDeregisteredFeatureTag(ImsSignallingUtils.GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED);
        for (String tag : registeringTags) {
            builder.addDeregisteredFeatureTag(tag,
                    DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED);
        }
        DelegateRegistrationState overrideRegState = builder.build();


        // Verify registration state through the process:
        ArgumentCaptor<DelegateRegistrationState> regCaptor =
                ArgumentCaptor.forClass(DelegateRegistrationState.class);
        verify(mAppCallback, times(1)).onFeatureTagStatusChanged(
                regCaptor.capture(), eq(new ArrayList<>(deniedTags)));
        List<DelegateRegistrationState> testStates = regCaptor.getAllValues();

        assertEquals(overrideRegState, testStates.get(0));
        verify(mAppCallback).onCreated(mSipDelegate);
    }

    /**
     * When registration states are changed in a case that an application supports the new
     * 'registering' state the state will be kept.
     *
     *  This method tests the case where the application supports consuming the
     *  DEREGISTERING_REASON_LOSING_PDN reason.
     */
    @Test
    public void testDelegateChangingRegisteredTagsRegisteringEnable() throws Exception {
        DelegateStateTracker stateTracker = new DelegateStateTracker(TEST_SUB_ID,
                Binder.getCallingUid(), mAppCallback, mSipDelegate, mRcsStats);
        stateTracker.setCompatChangesFactory((changeId, uid) -> true);
        Set<FeatureTagState> deniedTags = getMmTelDeniedTag();
        Set<String> supportedTags = getSupportedTags();
        Set<String> registeringTags = getRegisteringTag();
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        // SipDelegate created
        verify(mAppCallback).onCreated(mSipDelegate);

        DelegateRegistrationState regState = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG)
                .addRegisteringFeatureTags(registeringTags)
                .addDeregisteringFeatureTag(ImsSignallingUtils.FILE_TRANSFER_HTTP_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_LOSING_PDN)
                .addDeregisteredFeatureTag(ImsSignallingUtils.GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED)
                .build();
        stateTracker.onRegistrationStateChanged(regState);

        // Verify registration state through the process:
        ArgumentCaptor<DelegateRegistrationState> regCaptor =
                ArgumentCaptor.forClass(DelegateRegistrationState.class);
        verify(mAppCallback, times(1)).onFeatureTagStatusChanged(
                regCaptor.capture(), eq(new ArrayList<>(deniedTags)));
        List<DelegateRegistrationState> testStates = regCaptor.getAllValues();

        assertEquals(regState, testStates.get(0));
        verify(mAppCallback).onCreated(mSipDelegate);
    }


    /**
     * Test the case that when the underlying Denied tags change in the SipDelegate, the change is
     * properly shown in the registration update event.
     */
    @SmallTest
    @Test
    public void testDelegateChangingDeniedTagsChanged() throws Exception {
        DelegateStateTracker stateTracker = new DelegateStateTracker(TEST_SUB_ID,
                Binder.getCallingUid(), mAppCallback, mSipDelegate, mRcsStats);
        Set<FeatureTagState> deniedTags = getMmTelDeniedTag();
        Set<String> supportedTags = getSupportedTags();
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        // SipDelegate created
        verify(mAppCallback).onCreated(mSipDelegate);
        DelegateRegistrationState regState = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG)
                .build();
        stateTracker.onRegistrationStateChanged(regState);
        // Simulate underlying SipDelegate switch
        stateTracker.sipDelegateChanging(
                DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING);
        // onFeatureTagStatusChanged should now be called with registered features overridden with
        // DEREGISTERING_REASON_FEATURE_TAGS_CHANGING
        DelegateRegistrationState overrideRegState = new DelegateRegistrationState.Builder()
                .addDeregisteringFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING)
                .build();
        // Verify registration state so far.
        ArgumentCaptor<DelegateRegistrationState> regCaptor =
                ArgumentCaptor.forClass(DelegateRegistrationState.class);
        verify(mAppCallback, times(2)).onFeatureTagStatusChanged(
                regCaptor.capture(), eq(new ArrayList<>(deniedTags)));
        List<DelegateRegistrationState> testStates = regCaptor.getAllValues();
        assertEquals(2, testStates.size());
        // feature tags should first be registered
        assertEquals(regState, testStates.get(0));
        // registered feature tags should have moved to deregistering
        assertEquals(overrideRegState, testStates.get(1));

        // new underlying SipDelegate created, but SipDelegate denied one to one chat
        deniedTags.add(new FeatureTagState(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG,
                SipDelegateManager.DENIED_REASON_NOT_ALLOWED));
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        DelegateRegistrationState fullyDeniedRegState = new DelegateRegistrationState.Builder()
                .build();
        // In this special case, it will be the SipDelegateConnectionBase that will trigger
        // reg state change.
        stateTracker.onRegistrationStateChanged(fullyDeniedRegState);
        verify(mAppCallback).onFeatureTagStatusChanged(regCaptor.capture(),
                eq(new ArrayList<>(deniedTags)));
        // now all feature tags denied, so we should see only denied tags.
        assertEquals(fullyDeniedRegState, regCaptor.getValue());

        //onCreate should only have been called once and onDestroy should have never been called.
        verify(mAppCallback).onCreated(mSipDelegate);
        verify(mAppCallback, never()).onDestroyed(anyInt());
    }

    /**
     * Test that when we move from changing tags state to the delegate being destroyed, we get the
     * correct onDestroy event sent to the app.
     */
    @SmallTest
    @Test
    public void testDelegateChangingDeniedTagsChangingToDestroy() throws Exception {
        DelegateStateTracker stateTracker = new DelegateStateTracker(TEST_SUB_ID,
                Binder.getCallingUid(), mAppCallback, mSipDelegate, mRcsStats);
        Set<FeatureTagState> deniedTags = getMmTelDeniedTag();
        Set<String> supportedTags = getSupportedTags();
        stateTracker.sipDelegateConnected(supportedTags, deniedTags);
        // SipDelegate created
        verify(mAppCallback).onCreated(mSipDelegate);
        DelegateRegistrationState regState = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG)
                .addDeregisteredFeatureTag(ImsSignallingUtils.GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED)
                .build();
        stateTracker.onRegistrationStateChanged(regState);
        verify(mAppCallback).onFeatureTagStatusChanged(any(),
                eq(new ArrayList<>(deniedTags)));
        // Simulate underlying SipDelegate switch
        stateTracker.sipDelegateChanging(
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING);
        // Destroy
        stateTracker.sipDelegateDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);

        // onFeatureTagStatusChanged should now be called with registered features overridden with
        // DEREGISTERING_REASON_DESTROY_PENDING
        DelegateRegistrationState overrideRegState = new DelegateRegistrationState.Builder()
                .addDeregisteringFeatureTag(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING)
                // Deregistered should stay the same.
                .addDeregisteredFeatureTag(ImsSignallingUtils.GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_PROVISIONED)
                .build();
        // Verify registration state through process:
        ArgumentCaptor<DelegateRegistrationState> regCaptor =
                ArgumentCaptor.forClass(DelegateRegistrationState.class);
        verify(mAppCallback, times(2)).onFeatureTagStatusChanged(regCaptor.capture(),
                eq(new ArrayList<>(deniedTags)));
        List<DelegateRegistrationState> testStates = regCaptor.getAllValues();
        assertEquals(2, testStates.size());
        // feature tags should first be registered
        assertEquals(regState, testStates.get(0));
        // registered feature tags should have moved to deregistering
        assertEquals(overrideRegState, testStates.get(1));
        //onCreate/onDestroy should only be called once.
        verify(mAppCallback).onCreated(mSipDelegate);
        verify(mAppCallback).onDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    private Set<FeatureTagState> getMmTelDeniedTag() {
        Set<FeatureTagState> deniedTags = new ArraySet<>();
        deniedTags.add(new FeatureTagState(ImsSignallingUtils.MMTEL_TAG,
                SipDelegateManager.DENIED_REASON_NOT_ALLOWED));
        return deniedTags;
    }

    private Set<String> getSupportedTags() {
        Set<String> supportedTags = new ArraySet<>();
        supportedTags.add(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);
        supportedTags.add(ImsSignallingUtils.GROUP_CHAT_TAG);
        return supportedTags;
    }

    private Set<String> getRegisteringTag() {
        Set<String> registeringTags = new ArraySet<>();
        registeringTags.add(ImsSignallingUtils.CHATBOT_COMMUNICATION_USING_SESSION_TAG);
        return registeringTags;

    }
}
