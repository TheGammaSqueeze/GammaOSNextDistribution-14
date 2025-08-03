/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike;

import static com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.REQUEST_PRIORITY_HIGH;
import static com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.REQUEST_PRIORITY_NORMAL;
import static com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.REQUEST_PRIORITY_URGENT;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_IKE;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CMD_LOCAL_REQUEST_MOBIKE;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import androidx.test.InstrumentationRegistry;

import com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.IProcedureConsumer;
import com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.LocalRequest;
import com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.LocalRequestFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

public final class IkeLocalRequestSchedulerTest {
    private static final int REQUESTS_TO_QUEUE = 10;

    private IkeLocalRequestScheduler mScheduler;

    private IProcedureConsumer mMockConsumer;
    private LocalRequest[] mMockRequestArray;

    private int mNextRequestId;

    private ArgumentCaptor<LocalRequest> mLocalRequestCaptor =
            ArgumentCaptor.forClass(LocalRequest.class);

    private Context mSpyContext;
    private PowerManager mMockPowerManager;
    protected PowerManager.WakeLock mMockWakelock;

    @Before
    public void setUp() {
        mMockConsumer = mock(IProcedureConsumer.class);

        mSpyContext = spy(InstrumentationRegistry.getContext());
        mMockPowerManager = mock(PowerManager.class);
        mMockWakelock = mock(WakeLock.class);

        doReturn(mMockPowerManager).when(mSpyContext).getSystemService(eq(PowerManager.class));
        doReturn(mMockWakelock).when(mMockPowerManager).newWakeLock(anyInt(), anyString());

        mScheduler = new IkeLocalRequestScheduler(mMockConsumer, mSpyContext);

        mNextRequestId = 0;

        mMockRequestArray = new LocalRequest[REQUESTS_TO_QUEUE];
        for (int i = 0; i < mMockRequestArray.length; i++) {
            mMockRequestArray[i] = mock(LocalRequest.class);
            when(mMockRequestArray[i].getPriority()).thenReturn(REQUEST_PRIORITY_NORMAL);
        }
    }

    @Test
    public void testAddMultipleRequestProcessOnlyOne() {
        addAllRequestsToScheduler(mMockRequestArray);

        // Verify that no procedure was preemptively pulled from the queue
        verify(mMockConsumer, never()).onNewProcedureReady(any());

        // Check that the onNewPrcedureReady called exactly once, on the first item
        mScheduler.readyForNextProcedure();
        verify(mMockConsumer, times(1)).onNewProcedureReady(any());
        verify(mMockConsumer, times(1)).onNewProcedureReady(mMockRequestArray[0]);
        for (int i = 1; i < mMockRequestArray.length; i++) {
            verify(mMockConsumer, never()).onNewProcedureReady(mMockRequestArray[i]);
        }
    }

    private void addAllRequestsToScheduler(LocalRequest[] mockRequests) {
        for (LocalRequest r : mockRequests) {
            when(r.getRequestId()).thenReturn(mNextRequestId++);
            mScheduler.addRequest(r);
        }
    }

    @Test
    public void testProcessOrder() {
        InOrder inOrder = inOrder(mMockConsumer);

        addAllRequestsToScheduler(mMockRequestArray);
        for (int i = 0; i < mMockRequestArray.length; i++) mScheduler.readyForNextProcedure();

        for (LocalRequest r : mMockRequestArray) {
            inOrder.verify(mMockConsumer).onNewProcedureReady(r);
        }
    }

    @Test
    public void testPriorityProcessOrder() {
        InOrder inOrder = inOrder(mMockConsumer);

        LocalRequest[] mockUrgentPriorityRequestArray =
                createMockRequestArrayWithPriority(REQUEST_PRIORITY_URGENT);
        LocalRequest[] mockHighPriorityRequestArray =
                createMockRequestArrayWithPriority(REQUEST_PRIORITY_HIGH);

        addAllRequestsToScheduler(mMockRequestArray);
        addAllRequestsToScheduler(mockHighPriorityRequestArray);
        addAllRequestsToScheduler(mockUrgentPriorityRequestArray);

        int requestsToHandle =
                mockUrgentPriorityRequestArray.length
                        + mockHighPriorityRequestArray.length
                        + mMockRequestArray.length;
        for (int i = 0; i < requestsToHandle; i++) {
            mScheduler.readyForNextProcedure();
        }

        // Verify processing order: mockUrgentPriorityRequestArray before
        // mockHighPriorityRequestArray before mMockRequestArray
        for (LocalRequest r : mockUrgentPriorityRequestArray) {
            inOrder.verify(mMockConsumer).onNewProcedureReady(r);
        }
        for (LocalRequest r : mockHighPriorityRequestArray) {
            inOrder.verify(mMockConsumer).onNewProcedureReady(r);
        }
        for (LocalRequest r : mMockRequestArray) {
            inOrder.verify(mMockConsumer).onNewProcedureReady(r);
        }
    }

    private LocalRequest[] createMockRequestArrayWithPriority(int requestPriority) {
        LocalRequest[] mockRequestArray = new LocalRequest[REQUESTS_TO_QUEUE];
        for (int i = 0; i < mockRequestArray.length; i++) {
            mockRequestArray[i] = mock(LocalRequest.class);

            when(mockRequestArray[i].getPriority()).thenReturn(requestPriority);
        }
        return mockRequestArray;
    }

    @Test
    public void testProcedureTypeToPriority() {
        assertEquals(
                REQUEST_PRIORITY_URGENT,
                LocalRequestFactory.procedureTypeToPriority(CMD_LOCAL_REQUEST_DELETE_IKE));
        assertEquals(
                REQUEST_PRIORITY_HIGH,
                LocalRequestFactory.procedureTypeToPriority(CMD_LOCAL_REQUEST_MOBIKE));
        assertEquals(
                REQUEST_PRIORITY_NORMAL,
                LocalRequestFactory.procedureTypeToPriority(CMD_LOCAL_REQUEST_CREATE_IKE));
    }
}
