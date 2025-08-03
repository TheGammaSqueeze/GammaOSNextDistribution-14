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

import static android.telephony.DomainSelectionService.SELECTOR_TYPE_SMS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.RadioAccessNetworkType;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TransportSelectorCallback;
import android.telephony.WwanSelectorCallback;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

/**
 * Unit tests for SmsDomainSelector.
 */
@RunWith(AndroidJUnit4.class)
public class SmsDomainSelectorTest {
    private static final int SLOT_0 = 0;
    private static final int SUB_1 = 1;

    @Mock private TransportSelectorCallback mTransportSelectorCallback;
    @Mock private WwanSelectorCallback mWwanSelectorCallback;
    @Mock private ImsStateTracker mImsStateTracker;
    @Mock private DomainSelectorBase.DestroyListener mDomainSelectorDestroyListener;

    private final SelectionAttributes mSelectionAttributes =
            new SelectionAttributes.Builder(SLOT_0, SUB_1, SELECTOR_TYPE_SMS).build();
    private Context mContext;
    private Looper mLooper;
    private TestableLooper mTestableLooper;
    private SmsDomainSelector mDomainSelector;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContext();
        HandlerThread handlerThread = new HandlerThread(
                SmsDomainSelectorTest.class.getSimpleName());
        handlerThread.start();
        mLooper = handlerThread.getLooper();
        mTestableLooper = new TestableLooper(mLooper);
        mDomainSelector = new SmsDomainSelector(mContext, SLOT_0, SUB_1,
                mLooper, mImsStateTracker, mDomainSelectorDestroyListener);
    }

    @After
    public void tearDown() throws Exception {
        if (mTestableLooper != null) {
            mTestableLooper.destroy();
            mTestableLooper = null;
        }

        if (mDomainSelector != null) {
            mDomainSelector.destroy();
            verify(mImsStateTracker).removeImsStateListener(eq(mDomainSelector));
        }

        if (mLooper != null) {
            mLooper.quit();
            mLooper = null;
        }

        mDomainSelector = null;
        mWwanSelectorCallback = null;
        mTransportSelectorCallback = null;
        mImsStateTracker = null;
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnEutran() {
        selectDomain(AccessNetworkType.EUTRAN);
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnNgran() {
        selectDomain(AccessNetworkType.NGRAN);
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnIwlan() {
        selectDomain(AccessNetworkType.IWLAN);
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegistered() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpImsStateListener(true, false, false);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenWwanSelectorCallbackNull() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            final Consumer<WwanSelectorCallback> callback =
                    (Consumer<WwanSelectorCallback>) args[0];
            callback.accept(null);
            return null;
        }).when(mTransportSelectorCallback).onWwanSelected(any(Consumer.class));

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mTransportSelectorCallback).onSelectionTerminated(anyInt());
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testSelectDomainWhilePreviousRequestInProgress() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        processAllMessages();

        // onDomainSelected will be invoked only once
        // even though the domain selection was requested twice.
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testFinishSelection() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        mDomainSelector.finishSelection();

        assertFalse(mDomainSelector.isDomainSelectionRequested());
        verify(mDomainSelectorDestroyListener).onDomainSelectorDestroyed(eq(mDomainSelector));
    }

    @Test
    @SmallTest
    public void testReselectDomain() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN, AccessNetworkType.IWLAN);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());

        mDomainSelector.reselectDomain(mSelectionAttributes);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mTransportSelectorCallback).onWlanSelected(eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testReselectDomainWhilePreviousRequestInProgress() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN, AccessNetworkType.IWLAN);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        mDomainSelector.reselectDomain(mSelectionAttributes);
        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
        verify(mTransportSelectorCallback, never()).onWlanSelected(eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testOnImsRegistrationStateChangedWhenNotRegistered() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpImsStateListener(false, true, false);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testOnImsRegistrationStateChangedWhenRegisteredAndSmsCapable() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN, true);
        setUpImsStateListener(false, true, false);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testOnImsRegistrationStateChangedWhenRegisteredAndSmsIncapable() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN, false);
        setUpImsStateListener(false, true, false);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testOnImsMmTelCapabilitiesChangedWhenSmsCapable() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN, true);
        setUpImsStateListener(false, false, true);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testOnImsMmTelCapabilitiesChangedWhenSmsIncapable() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN, false);
        setUpImsStateListener(false, false, true);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    private void selectDomain(@RadioAccessNetworkType int accessNetworkType) {
        setUpImsStateTracker(accessNetworkType);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        processAllMessages();

        if (accessNetworkType == AccessNetworkType.IWLAN) {
            verify(mTransportSelectorCallback).onWlanSelected(eq(false));
        } else {
            verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                    eq(false));
        }
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    private void setUpImsStateTracker(@RadioAccessNetworkType int accessNetworkType) {
        setUpImsStateTracker(accessNetworkType, true);
    }

    private void setUpImsStateTracker(@RadioAccessNetworkType int accessNetworkType,
            boolean smsCapable) {
        when(mImsStateTracker.isMmTelFeatureAvailable()).thenReturn(true);
        when(mImsStateTracker.isImsRegistered())
                .thenReturn(accessNetworkType != AccessNetworkType.UNKNOWN);
        when(mImsStateTracker.isImsRegisteredOverWlan())
                .thenReturn(accessNetworkType == AccessNetworkType.IWLAN);
        when(mImsStateTracker.getImsAccessNetworkType()).thenReturn(accessNetworkType);
        when(mImsStateTracker.isImsSmsCapable()).thenReturn(smsCapable);
    }

    private void setUpImsStateTracker(@RadioAccessNetworkType int firstAccessNetworkType,
            @RadioAccessNetworkType int secondAccessNetworkType) {
        when(mImsStateTracker.isMmTelFeatureAvailable()).thenReturn(true);
        when(mImsStateTracker.isImsRegistered()).thenReturn(
                firstAccessNetworkType != AccessNetworkType.UNKNOWN,
                secondAccessNetworkType != AccessNetworkType.UNKNOWN);
        when(mImsStateTracker.isImsRegisteredOverWlan()).thenReturn(
                firstAccessNetworkType == AccessNetworkType.IWLAN,
                secondAccessNetworkType == AccessNetworkType.IWLAN);
        when(mImsStateTracker.getImsAccessNetworkType()).thenReturn(
                firstAccessNetworkType,
                secondAccessNetworkType);
        when(mImsStateTracker.isImsSmsCapable()).thenReturn(true);
    }

    private void setUpWwanSelectorCallback() {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            final Consumer<WwanSelectorCallback> callback =
                    (Consumer<WwanSelectorCallback>) args[0];
            callback.accept(mWwanSelectorCallback);
            return null;
        }).when(mTransportSelectorCallback).onWwanSelected(any(Consumer.class));
    }

    private void setUpImsStateListener(boolean notifyMmTelFeatureAvailable,
            boolean notifyImsRegState, boolean notifyMmTelCapability) {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            final ImsStateTracker.ImsStateListener listener =
                    (ImsStateTracker.ImsStateListener) args[0];
            mDomainSelector.post(() -> {
                if (notifyMmTelFeatureAvailable) {
                    listener.onImsMmTelFeatureAvailableChanged();
                }
                if (notifyImsRegState) {
                    listener.onImsRegistrationStateChanged();
                }
                if (notifyMmTelCapability) {
                    listener.onImsMmTelCapabilitiesChanged();
                }
            });
            return null;
        }).when(mImsStateTracker).addImsStateListener(any(ImsStateTracker.ImsStateListener.class));
    }

    private void processAllMessages() {
        while (!mTestableLooper.getLooper().getQueue().isIdle()) {
            mTestableLooper.processAllMessages();
        }
    }
}
