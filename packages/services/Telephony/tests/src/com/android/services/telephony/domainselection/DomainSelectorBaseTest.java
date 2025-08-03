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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.TransportSelectorCallback;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for DomainSelectorBase.
 */
@RunWith(AndroidJUnit4.class)
public class DomainSelectorBaseTest {
    public class TestDomainSelectorBase extends DomainSelectorBase {
        public TestDomainSelectorBase(Context context, int slotId, int subId,
                @NonNull Looper looper, @NonNull ImsStateTracker imsStateTracker,
                @NonNull DomainSelectorBase.DestroyListener listener, String logTag) {
            super(context, slotId, subId, looper, imsStateTracker, listener, logTag);
        }

        @Override
        public void reselectDomain(@NonNull SelectionAttributes attr) {
            // No operations.
        }

        @Override
        public void finishSelection() {
            // No operations.
        }

        @Override
        public void selectDomain(SelectionAttributes attr, TransportSelectorCallback callback) {
            // No operations.
        }
    }

    private static final String TAG = DomainSelectorBaseTest.class.getSimpleName();
    private static final int SLOT_0 = 0;
    private static final int SUB_1 = 1;

    @Mock private DomainSelectorBase.DestroyListener mDomainSelectorDestroyListener;
    @Mock private ImsStateTracker mImsStateTracker;

    private Context mContext;
    private Looper mLooper;
    private TestDomainSelectorBase mDomainSelector;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContext();

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mLooper = handlerThread.getLooper();
        mDomainSelector = new TestDomainSelectorBase(mContext, SLOT_0, SUB_1, mLooper,
                mImsStateTracker, mDomainSelectorDestroyListener, TAG);
    }

    @After
    public void tearDown() throws Exception {
        if (mDomainSelector != null) {
            mDomainSelector.destroy();
            mDomainSelector = null;
        }

        if (mLooper != null) {
            mLooper.quit();
            mLooper = null;
        }

        mDomainSelectorDestroyListener = null;
        mImsStateTracker = null;
        mContext = null;
    }

    @Test
    @SmallTest
    public void testInit() {
        assertEquals(SLOT_0, mDomainSelector.getSlotId());
        assertEquals(SUB_1, mDomainSelector.getSubId());
    }

    @Test
    @SmallTest
    public void testDestroy() {
        mDomainSelector.destroy();
        verify(mDomainSelectorDestroyListener).onDomainSelectorDestroyed(eq(mDomainSelector));
        mDomainSelector = null;
    }
}
