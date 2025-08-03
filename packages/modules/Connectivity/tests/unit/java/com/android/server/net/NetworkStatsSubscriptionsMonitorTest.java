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

package com.android.server.net;

import static android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE;
import static android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.os.Looper;
import android.os.Parcel;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import com.android.internal.util.CollectionUtils;
import com.android.server.net.NetworkStatsSubscriptionsMonitor.RatTypeListener;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public final class NetworkStatsSubscriptionsMonitorTest {
    private static final int TEST_SUBID1 = 3;
    private static final int TEST_SUBID2 = 5;
    private static final String TEST_IMSI1 = "466921234567890";
    private static final String TEST_IMSI2 = "466920987654321";
    private static final String TEST_IMSI3 = "466929999999999";

    @Mock private Context mContext;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private TelephonyManager mTelephonyManager;
    private final SparseArray<TelephonyManager> mTelephonyManagerOfSub = new SparseArray<>();
    private final SparseArray<RatTypeListener> mRatTypeListenerOfSub = new SparseArray<>();
    @Mock private NetworkStatsSubscriptionsMonitor.Delegate mDelegate;
    private final List<Integer> mTestSubList = new ArrayList<>();

    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private NetworkStatsSubscriptionsMonitor mMonitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // TODO(b/213280079): Start a different thread and prepare the looper, create the monitor
        //  on that thread instead of using the test main thread looper.
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        when(mContext.getSystemService(eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE)))
                .thenReturn(mSubscriptionManager);
        when(mContext.getSystemService(eq(Context.TELEPHONY_SERVICE)))
                .thenReturn(mTelephonyManager);

        mMonitor = new NetworkStatsSubscriptionsMonitor(mContext, mExecutor, mDelegate);
    }

    @Test
    public void testStartStop() {
        // Verify that addOnSubscriptionsChangedListener() is never called before start().
        verify(mSubscriptionManager, never())
                .addOnSubscriptionsChangedListener(mExecutor, mMonitor);
        mMonitor.start();
        verify(mSubscriptionManager).addOnSubscriptionsChangedListener(mExecutor, mMonitor);

        // Verify that removeOnSubscriptionsChangedListener() is never called before stop()
        verify(mSubscriptionManager, never()).removeOnSubscriptionsChangedListener(mMonitor);
        mMonitor.stop();
        verify(mSubscriptionManager).removeOnSubscriptionsChangedListener(mMonitor);
    }

    @NonNull
    private static int[] convertArrayListToIntArray(@NonNull List<Integer> arrayList) {
        final int[] list = new int[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            list[i] = arrayList.get(i);
        }
        return list;
    }

    private TelephonyDisplayInfo makeTelephonyDisplayInfo(
            int networkType, int overrideNetworkType) {
        // Create from parcel since final classes cannot be mocked and there is no exposed public
        // constructors.
        Parcel p = Parcel.obtain();
        p.writeInt(networkType);
        p.writeInt(overrideNetworkType);

        p.setDataPosition(0);
        return TelephonyDisplayInfo.CREATOR.createFromParcel(p);
    }

    private void setRatTypeForSub(int subId, int type) {
        setRatTypeForSub(subId, type, OVERRIDE_NETWORK_TYPE_NONE);
    }

    private void setRatTypeForSub(int subId, int type, int overrideType) {
        final TelephonyDisplayInfo displayInfo = makeTelephonyDisplayInfo(type, overrideType);
        final RatTypeListener match = mRatTypeListenerOfSub.get(subId);
        if (match == null) {
            fail("Could not find listener with subId: " + subId);
        }
        match.onDisplayInfoChanged(displayInfo);
    }

    private void addTestSub(int subId, String subscriberId) {
        // add SubId to TestSubList.
        if (mTestSubList.contains(subId)) fail("The subscriber list already contains this ID");

        mTestSubList.add(subId);

        final int[] subList = convertArrayListToIntArray(mTestSubList);
        when(mSubscriptionManager.getCompleteActiveSubscriptionIdList()).thenReturn(subList);
        updateSubscriberIdForTestSub(subId, subscriberId);
    }

    private void updateSubscriberIdForTestSub(int subId, @Nullable final String subscriberId) {
        final TelephonyManager telephonyManagerOfSub;
        if (mTelephonyManagerOfSub.contains(subId)) {
            telephonyManagerOfSub = mTelephonyManagerOfSub.get(subId);
        } else {
            telephonyManagerOfSub = mock(TelephonyManager.class);
            mTelephonyManagerOfSub.put(subId, telephonyManagerOfSub);
        }
        when(telephonyManagerOfSub.getSubscriberId()).thenReturn(subscriberId);
        when(mTelephonyManager.createForSubscriptionId(subId)).thenReturn(telephonyManagerOfSub);
        mMonitor.onSubscriptionsChanged();
    }

    private void assertAndCaptureRatTypeListenerRegistration(int subId) {
        final ArgumentCaptor<RatTypeListener> ratTypeListenerCaptor =
                ArgumentCaptor.forClass(RatTypeListener.class);
        verify(mTelephonyManagerOfSub.get(subId))
                .registerTelephonyCallback(any(), ratTypeListenerCaptor.capture());
        final RatTypeListener listener = CollectionUtils
                .find(ratTypeListenerCaptor.getAllValues(), it -> it.getSubId() == subId);
        assertNotNull(listener);
        mRatTypeListenerOfSub.put(subId, listener);
    }

    private void removeTestSub(int subId) {
        // Remove subId from TestSubList.
        mTestSubList.removeIf(it -> it == subId);
        final int[] subList = convertArrayListToIntArray(mTestSubList);
        when(mSubscriptionManager.getCompleteActiveSubscriptionIdList()).thenReturn(subList);
        mMonitor.onSubscriptionsChanged();
        assertRatTypeListenerDeregistration(subId);
        mRatTypeListenerOfSub.delete(subId);
        mTelephonyManagerOfSub.delete(subId);
    }

    private void assertRatTypeListenerDeregistration(int subId) {
        verify(mTelephonyManagerOfSub.get(subId))
                .unregisterTelephonyCallback(eq(mRatTypeListenerOfSub.get(subId)));
    }

    private void assertRatTypeChangedForSub(String subscriberId, int ratType) {
        assertEquals(ratType, mMonitor.getRatTypeForSubscriberId(subscriberId));
        final ArgumentCaptor<Integer> typeCaptor = ArgumentCaptor.forClass(Integer.class);
        // Verify callback with the subscriberId and the RAT type should be as expected.
        // It will fail if get a callback with an unexpected RAT type.
        verify(mDelegate).onCollapsedRatTypeChanged(eq(subscriberId), typeCaptor.capture());
        final int type = typeCaptor.getValue();
        assertEquals(ratType, type);
    }

    private void assertRatTypeNotChangedForSub(String subscriberId, int ratType) {
        assertEquals(mMonitor.getRatTypeForSubscriberId(subscriberId), ratType);
        // Should never get callback with any RAT type.
        verify(mDelegate, never()).onCollapsedRatTypeChanged(eq(subscriberId), anyInt());
    }

    @Test
    public void testSubChangedAndRatTypeChanged() {
        mMonitor.start();
        // Insert sim1, verify RAT type is NETWORK_TYPE_UNKNOWN, and never get any callback
        // before changing RAT type.
        addTestSub(TEST_SUBID1, TEST_IMSI1);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);

        // Insert sim2.
        addTestSub(TEST_SUBID2, TEST_IMSI2);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertAndCaptureRatTypeListenerRegistration(TEST_SUBID1);
        assertAndCaptureRatTypeListenerRegistration(TEST_SUBID2);
        reset(mDelegate);

        // Set RAT type of sim1 to UMTS.
        // Verify RAT type of sim1 after subscription gets onCollapsedRatTypeChanged() callback
        // and others remain untouched.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_UMTS);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UMTS);
        assertRatTypeNotChangedForSub(TEST_IMSI2, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertRatTypeNotChangedForSub(TEST_IMSI3, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        reset(mDelegate);

        // Set RAT type of sim2 to LTE.
        // Verify RAT type of sim2 after subscription gets onCollapsedRatTypeChanged() callback
        // and others remain untouched.
        setRatTypeForSub(TEST_SUBID2, TelephonyManager.NETWORK_TYPE_LTE);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UMTS);
        assertRatTypeChangedForSub(TEST_IMSI2, TelephonyManager.NETWORK_TYPE_LTE);
        assertRatTypeNotChangedForSub(TEST_IMSI3, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        reset(mDelegate);

        // Remove sim2 and verify that callbacks are fired and RAT type is correct for sim2.
        // while the other two remain untouched.
        removeTestSub(TEST_SUBID2);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UMTS);
        assertRatTypeChangedForSub(TEST_IMSI2, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertRatTypeNotChangedForSub(TEST_IMSI3, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        reset(mDelegate);

        // Set RAT type of sim1 to UNKNOWN. Then stop monitoring subscription changes
        // and verify that the listener for sim1 is removed.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        reset(mDelegate);

        mMonitor.stop();
        assertRatTypeListenerDeregistration(TEST_SUBID1);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }


    @Test
    public void test5g() {
        mMonitor.start();
        // Insert sim1, verify RAT type is NETWORK_TYPE_UNKNOWN, and never get any callback
        // before changing RAT type. Also capture listener for later use.
        addTestSub(TEST_SUBID1, TEST_IMSI1);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertAndCaptureRatTypeListenerRegistration(TEST_SUBID1);
        final RatTypeListener listener = mRatTypeListenerOfSub.get(TEST_SUBID1);

        // Set RAT type to 5G NSA (non-standalone) mode, verify the monitor outputs
        // NETWORK_TYPE_5G_NSA.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_LTE,
                OVERRIDE_NETWORK_TYPE_NR_NSA);
        assertRatTypeChangedForSub(TEST_IMSI1, NetworkStatsManager.NETWORK_TYPE_5G_NSA);
        reset(mDelegate);

        // Set RAT type to LTE without NR connected, the RAT type should be downgraded to LTE.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_LTE,
                OVERRIDE_NETWORK_TYPE_NONE);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_LTE);
        reset(mDelegate);

        // Verify NR connected with other RAT type does not take effect.
        // This should not be happened in practice.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_UMTS,
                OVERRIDE_NETWORK_TYPE_NR_NSA);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UMTS);
        reset(mDelegate);

        // Set RAT type to 5G standalone mode, the RAT type should be NR.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_NR);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_NR);
        reset(mDelegate);

        // Set NR state to none in standalone mode does not change anything.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_NR, OVERRIDE_NETWORK_TYPE_NONE);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_NR);
    }

    @Test
    public void testSubscriberIdUnavailable() {
        mMonitor.start();
        // Insert sim1, set subscriberId to null which is normal in SIM PIN locked case.
        // Verify RAT type is NETWORK_TYPE_UNKNOWN and service will not perform listener
        // registration.
        addTestSub(TEST_SUBID1, null);
        verify(mTelephonyManagerOfSub.get(TEST_SUBID1), never()).listen(any(), anyInt());
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);

        // Set IMSI for sim1, verify the listener will be registered.
        updateSubscriberIdForTestSub(TEST_SUBID1, TEST_IMSI1);
        assertAndCaptureRatTypeListenerRegistration(TEST_SUBID1);
        reset(mTelephonyManager);

        // Set RAT type of sim1 to UMTS. Verify RAT type of sim1 is changed.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_UMTS);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UMTS);
        reset(mDelegate);

        // Set IMSI to null again to simulate somehow IMSI is not available, such as
        // modem crash. Verify service should unregister listener.
        updateSubscriberIdForTestSub(TEST_SUBID1, null);
        assertRatTypeListenerDeregistration(TEST_SUBID1);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        reset(mDelegate);
        clearInvocations(mTelephonyManagerOfSub.get(TEST_SUBID1));

        // Simulate somehow IMSI is back. Verify service will register with
        // another listener and fire callback accordingly.
        final ArgumentCaptor<RatTypeListener> ratTypeListenerCaptor2 =
                ArgumentCaptor.forClass(RatTypeListener.class);
        updateSubscriberIdForTestSub(TEST_SUBID1, TEST_IMSI1);
        assertAndCaptureRatTypeListenerRegistration(TEST_SUBID1);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        reset(mDelegate);
        clearInvocations(mTelephonyManagerOfSub.get(TEST_SUBID1));

        // Set RAT type of sim1 to LTE. Verify RAT type of sim1 still works.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_LTE);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_LTE);
        reset(mDelegate);

        mMonitor.stop();
        assertRatTypeListenerDeregistration(TEST_SUBID1);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

    /**
     * Verify that when IMSI suddenly changed for a given subId, the service will register a new
     * listener and unregister the old one, and report changes on updated IMSI. This is for modem
     * feature that may be enabled for certain carrier, which changes to use a different IMSI while
     * roaming on certain networks for multi-IMSI SIM cards, but the subId stays the same.
     */
    @Test
    public void testSubscriberIdChanged() {
        mMonitor.start();
        // Insert sim1, verify RAT type is NETWORK_TYPE_UNKNOWN, and never get any callback
        // before changing RAT type.
        addTestSub(TEST_SUBID1, TEST_IMSI1);
        assertAndCaptureRatTypeListenerRegistration(TEST_SUBID1);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);

        // Set RAT type of sim1 to UMTS.
        // Verify RAT type of sim1 changes accordingly.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_UMTS);
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UMTS);
        reset(mDelegate);
        clearInvocations(mTelephonyManagerOfSub.get(TEST_SUBID1));

        // Simulate IMSI of sim1 changed to IMSI2. Verify the service will register with
        // another listener and remove the old one. The RAT type of new IMSI stays at
        // NETWORK_TYPE_UNKNOWN until received initial callback from telephony.
        updateSubscriberIdForTestSub(TEST_SUBID1, TEST_IMSI2);
        final RatTypeListener oldListener = mRatTypeListenerOfSub.get(TEST_SUBID1);
        assertAndCaptureRatTypeListenerRegistration(TEST_SUBID1);
        verify(mTelephonyManagerOfSub.get(TEST_SUBID1), times(1))
                .unregisterTelephonyCallback(eq(oldListener));
        assertRatTypeChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertRatTypeNotChangedForSub(TEST_IMSI2, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        reset(mDelegate);

        // Set RAT type of sim1 to UMTS for new listener to simulate the initial callback received
        // from telephony after registration. Verify RAT type of sim1 changes with IMSI2
        // accordingly.
        setRatTypeForSub(TEST_SUBID1, TelephonyManager.NETWORK_TYPE_UMTS);
        assertRatTypeNotChangedForSub(TEST_IMSI1, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertRatTypeChangedForSub(TEST_IMSI2, TelephonyManager.NETWORK_TYPE_UMTS);
        reset(mDelegate);
    }
}
