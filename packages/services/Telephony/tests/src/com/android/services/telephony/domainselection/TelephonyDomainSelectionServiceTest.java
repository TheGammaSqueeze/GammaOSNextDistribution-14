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

package com.android.services.telephony.domainselection;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.BarringInfo;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.DomainSelectionService.SelectorType;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TransportSelectorCallback;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TestContext;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Unit tests for TelephonyDomainSelectionService.
 */
@RunWith(AndroidJUnit4.class)
public class TelephonyDomainSelectionServiceTest {
    private TelephonyDomainSelectionService.ImsStateTrackerFactory mImsStateTrackerFactory =
            new TelephonyDomainSelectionService.ImsStateTrackerFactory() {
                @Override
                public ImsStateTracker create(Context context, int slotId,
                        @NonNull Looper looper) {
                    return mImsStateTracker;
                }
            };
    private TelephonyDomainSelectionService.DomainSelectorFactory mDomainSelectorFactory =
            new TelephonyDomainSelectionService.DomainSelectorFactory() {
                @Override
                public DomainSelectorBase create(Context context, int slotId, int subId,
                        @SelectorType int selectorType, boolean isEmergency,
                        @NonNull Looper looper, @NonNull ImsStateTracker imsStateTracker,
                        @NonNull DomainSelectorBase.DestroyListener listener,
                        @NonNull CrossSimRedialingController crossSimRedialingController,
                        @NonNull CarrierConfigHelper carrierConfigHelper,
                        @NonNull EmergencyCallbackModeHelper ecbmHelper) {
                    switch (selectorType) {
                        case DomainSelectionService.SELECTOR_TYPE_CALLING: // fallthrough
                        case DomainSelectionService.SELECTOR_TYPE_SMS:
                            mDomainSelectorDestroyListener = listener;
                            if (subId == SUB_1) {
                                return mDomainSelectorBase1;
                            } else {
                                return mDomainSelectorBase2;
                            }
                        default:
                            return null;
                    }
                }
            };
    private static class TestTelephonyDomainSelectionService
            extends TelephonyDomainSelectionService {
        private final Context mContext;

        TestTelephonyDomainSelectionService(Context context,
                @NonNull ImsStateTrackerFactory imsStateTrackerFactory,
                @NonNull DomainSelectorFactory domainSelectorFactory,
                @Nullable CarrierConfigHelper carrierConfigHelper,
                @Nullable EmergencyCallbackModeHelper ecbmHelper) {
            super(imsStateTrackerFactory, domainSelectorFactory, carrierConfigHelper, ecbmHelper);
            mContext = context;
        }

        @Override
        public void onCreate() {
            // attach test context.
            attachBaseContext(mContext);
            super.onCreate();
        }
    }
    private static final int SLOT_0 = 0;
    private static final int SUB_1 = 1;
    private static final int SUB_2 = 2;
    private static final String CALL_ID = "Call_1";
    private static final @SelectorType int TEST_SELECTOR_TYPE =
            DomainSelectionService.SELECTOR_TYPE_CALLING;
    private static final @SelectorType int INVALID_SELECTOR_TYPE = -1;

    @Mock private DomainSelectorBase mDomainSelectorBase1;
    @Mock private DomainSelectorBase mDomainSelectorBase2;
    @Mock private TransportSelectorCallback mSelectorCallback1;
    @Mock private TransportSelectorCallback mSelectorCallback2;
    @Mock private ImsStateTracker mImsStateTracker;
    @Mock private CarrierConfigHelper mCarrierConfigHelper;
    @Mock private EmergencyCallbackModeHelper mEcbmHelper;

    private final ServiceState mServiceState = new ServiceState();
    private final BarringInfo mBarringInfo = new BarringInfo();
    private Context mContext;
    private Handler mServiceHandler;
    private TestableLooper mTestableLooper;
    private SubscriptionManager mSubscriptionManager;
    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener;
    private DomainSelectorBase.DestroyListener mDomainSelectorDestroyListener;
    private TelephonyDomainSelectionService mDomainSelectionService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mContext = new TestContext();
        mDomainSelectionService = new TestTelephonyDomainSelectionService(mContext,
                mImsStateTrackerFactory, mDomainSelectorFactory, mCarrierConfigHelper, mEcbmHelper);
        mDomainSelectionService.onCreate();
        mServiceHandler = new Handler(mDomainSelectionService.getLooper());
        mTestableLooper = new TestableLooper(mDomainSelectionService.getLooper());

        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        if (Flags.workProfileApiSplit()) {
            doReturn(mSubscriptionManager).when(mSubscriptionManager).createForAllUserProfiles();
        }
        ArgumentCaptor<OnSubscriptionsChangedListener> listenerCaptor =
                ArgumentCaptor.forClass(OnSubscriptionsChangedListener.class);
        verify(mSubscriptionManager).addOnSubscriptionsChangedListener(
                any(Executor.class), listenerCaptor.capture());
        mOnSubscriptionsChangedListener = listenerCaptor.getValue();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestableLooper != null) {
            mTestableLooper.destroy();
            mTestableLooper = null;
        }
        mServiceHandler = null;

        if (mDomainSelectionService != null) {
            mDomainSelectionService.onDestroy();
            mDomainSelectionService = null;
        }

        mDomainSelectorBase1 = null;
        mDomainSelectorBase2 = null;
        mSelectorCallback1 = null;
        mSelectorCallback2 = null;
        mImsStateTracker = null;
        mSubscriptionManager = null;
        mOnSubscriptionsChangedListener = null;
        mDomainSelectorDestroyListener = null;
    }

    @Test
    @SmallTest
    public void testGetExecutor() {
        assertNotNull(mDomainSelectionService.getExecutor());
    }

    @Test
    @SmallTest
    public void testOnDomainSelection() {
        SelectionAttributes attr1 = new SelectionAttributes.Builder(
                SLOT_0, SUB_1, TEST_SELECTOR_TYPE)
                .setCallId(CALL_ID)
                .setEmergency(true)
                .build();
        mDomainSelectionService.onDomainSelection(attr1, mSelectorCallback1);
        processAllMessages();

        verify(mImsStateTracker).start(eq(SUB_1));
        verify(mSelectorCallback1).onCreated(eq(mDomainSelectorBase1));
        verifyNoMoreInteractions(mSelectorCallback1);
        verify(mDomainSelectorBase1).selectDomain(eq(attr1), eq(mSelectorCallback1));
    }

    @Test
    @SmallTest
    public void testOnDomainSelectionWithInvalidSelectorType() {
        SelectionAttributes attr1 = new SelectionAttributes.Builder(
                SLOT_0, SUB_1, INVALID_SELECTOR_TYPE)
                .setCallId(CALL_ID)
                .setEmergency(true)
                .build();
        mDomainSelectionService.onDomainSelection(attr1, mSelectorCallback1);
        processAllMessages();

        verify(mImsStateTracker, never()).start(anyInt());
        verify(mSelectorCallback1).onSelectionTerminated(anyInt());
        verifyNoMoreInteractions(mSelectorCallback1);
        verify(mDomainSelectorBase1, never()).selectDomain(eq(attr1), eq(mSelectorCallback1));
    }

    @Test
    @SmallTest
    public void testOnDomainSelectionTwiceWithDestroy() {
        SelectionAttributes attr1 = new SelectionAttributes.Builder(
                SLOT_0, SUB_1, TEST_SELECTOR_TYPE)
                .setCallId(CALL_ID)
                .setEmergency(true)
                .build();
        mDomainSelectionService.onDomainSelection(attr1, mSelectorCallback1);
        processAllMessages();

        verify(mImsStateTracker).start(eq(SUB_1));
        verify(mSelectorCallback1).onCreated(eq(mDomainSelectorBase1));
        verifyNoMoreInteractions(mSelectorCallback1);
        verify(mDomainSelectorBase1).selectDomain(eq(attr1), eq(mSelectorCallback1));

        // Notify the domain selection service that this domain selector is destroyed.
        mDomainSelectorDestroyListener.onDomainSelectorDestroyed(mDomainSelectorBase1);

        SelectionAttributes attr2 = new SelectionAttributes.Builder(
                SLOT_0, SUB_2, TEST_SELECTOR_TYPE)
                .setCallId(CALL_ID)
                .setEmergency(true)
                .build();
        mDomainSelectionService.onDomainSelection(attr2, mSelectorCallback2);
        processAllMessages();

        verify(mImsStateTracker).start(eq(SUB_2));
        verify(mSelectorCallback2).onCreated(eq(mDomainSelectorBase2));
        verifyNoMoreInteractions(mSelectorCallback2);
        verify(mDomainSelectorBase2).selectDomain(eq(attr2), eq(mSelectorCallback2));
    }

    @Test
    @SmallTest
    public void testOnDomainSelectionTwiceWithoutDestroy() {
        SelectionAttributes attr1 = new SelectionAttributes.Builder(
                SLOT_0, SUB_1, TEST_SELECTOR_TYPE)
                .setCallId(CALL_ID)
                .setEmergency(true)
                .build();
        mDomainSelectionService.onDomainSelection(attr1, mSelectorCallback1);
        processAllMessages();

        verify(mImsStateTracker).start(eq(SUB_1));
        verify(mSelectorCallback1).onCreated(eq(mDomainSelectorBase1));
        verifyNoMoreInteractions(mSelectorCallback1);
        verify(mDomainSelectorBase1).selectDomain(eq(attr1), eq(mSelectorCallback1));

        SelectionAttributes attr2 = new SelectionAttributes.Builder(
                SLOT_0, SUB_2, TEST_SELECTOR_TYPE)
                .setCallId(CALL_ID)
                .setEmergency(true)
                .build();
        mDomainSelectionService.onDomainSelection(attr2, mSelectorCallback2);
        processAllMessages();

        verify(mImsStateTracker).start(eq(SUB_2));
        verify(mSelectorCallback2).onCreated(eq(mDomainSelectorBase2));
        verifyNoMoreInteractions(mSelectorCallback2);
        verify(mDomainSelectorBase2).selectDomain(eq(attr2), eq(mSelectorCallback2));
    }

    @Test
    @SmallTest
    public void testOnServiceStateUpdated() {
        mDomainSelectionService.onServiceStateUpdated(SLOT_0, SUB_1, mServiceState);

        verify(mImsStateTracker).updateServiceState(eq(mServiceState));
    }

    @Test
    @SmallTest
    public void testOnBarringInfoUpdated() {
        mDomainSelectionService.onBarringInfoUpdated(SLOT_0, SUB_1, mBarringInfo);

        verify(mImsStateTracker).updateBarringInfo(eq(mBarringInfo));
    }

    @Test
    @SmallTest
    public void testOnDestroy() {
        SelectionAttributes attr1 = new SelectionAttributes.Builder(
                SLOT_0, SUB_1, TEST_SELECTOR_TYPE)
                .setCallId(CALL_ID)
                .setEmergency(true)
                .build();
        mDomainSelectionService.onDomainSelection(attr1, mSelectorCallback1);
        processAllMessages();

        mDomainSelectionService.onDestroy();

        verify(mImsStateTracker).destroy();
        verify(mDomainSelectorBase1).destroy();
        verify(mSubscriptionManager).removeOnSubscriptionsChangedListener(any());
    }

    @Test
    @SmallTest
    public void testHandleSubscriptionsChangedWithEmptySubscriptionInfo() {
        when(mSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(null, new ArrayList<SubscriptionInfo>());

        mOnSubscriptionsChangedListener.onSubscriptionsChanged();
        mOnSubscriptionsChangedListener.onSubscriptionsChanged();

        verify(mImsStateTracker, never()).start(anyInt());
    }

    @Test
    @SmallTest
    public void testHandleSubscriptionsChangedWithActiveSubscriptionInfoAndInvalidSlotIndex() {
        SubscriptionInfo subsInfo = Mockito.mock(SubscriptionInfo.class);
        List<SubscriptionInfo> subsInfoList = new ArrayList<>();
        subsInfoList.add(subsInfo);
        when(mSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(subsInfoList);
        when(subsInfo.getSimSlotIndex()).thenReturn(SubscriptionManager.INVALID_SIM_SLOT_INDEX);

        mOnSubscriptionsChangedListener.onSubscriptionsChanged();

        verify(mImsStateTracker, never()).start(anyInt());
    }

    @Test
    @SmallTest
    public void testHandleSubscriptionsChangedWithActiveSubscriptionInfo() {
        SubscriptionInfo subsInfo = Mockito.mock(SubscriptionInfo.class);
        List<SubscriptionInfo> subsInfoList = new ArrayList<>();
        subsInfoList.add(subsInfo);
        when(mSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(subsInfoList);
        when(subsInfo.getSubscriptionId())
                .thenReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID, SUB_1);
        when(subsInfo.getSimSlotIndex()).thenReturn(SLOT_0);

        mOnSubscriptionsChangedListener.onSubscriptionsChanged();
        mOnSubscriptionsChangedListener.onSubscriptionsChanged();

        verify(mImsStateTracker).start(eq(SubscriptionManager.INVALID_SUBSCRIPTION_ID));
        verify(mImsStateTracker).start(eq(SUB_1));
    }

    private void processAllMessages() {
        while (!mTestableLooper.getLooper().getQueue().isIdle()) {
            mTestableLooper.processAllMessages();
        }
    }
}
