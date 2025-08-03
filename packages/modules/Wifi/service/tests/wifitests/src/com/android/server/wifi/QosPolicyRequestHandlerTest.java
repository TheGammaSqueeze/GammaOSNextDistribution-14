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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.net.DscpPolicy;
import android.net.NetworkAgent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.test.TestLooper;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyClassifierParams;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyRequest;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyRequestType;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyStatus;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

public class QosPolicyRequestHandlerTest extends WifiBaseTest{
    private static final String TEST_IFACE_NAME = "mockWlan";
    private static final byte[] TEST_INET_ADDR = {127, 0, 0, 1};
    private static final int QOS_REQUEST_DIALOG_TOKEN = 124;

    private QosPolicyRequestHandler mQosPolicyRequestHandler;
    private TestLooper mLooper;
    private Handler mNetworkAgentHandler;
    @Mock WifiNetworkAgent mWifiNetworkAgent;
    @Mock WifiNetworkAgent mWifiNetworkAgentAlt;
    @Mock WifiNative mWifiNative;
    @Mock ClientModeImpl mClientModeImpl;
    @Mock HandlerThread mHandlerThread;

    @Captor ArgumentCaptor<List<QosPolicyStatus>> mQosStatusListCaptor;
    private InOrder mInOrder;

    @Before
    public void setUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        when(mHandlerThread.getLooper()).thenReturn(mLooper.getLooper());
        mQosPolicyRequestHandler = new QosPolicyRequestHandler(
                TEST_IFACE_NAME, mWifiNative, mClientModeImpl, mHandlerThread);
        mQosPolicyRequestHandler.setNetworkAgent(mWifiNetworkAgent);
        mNetworkAgentHandler = new Handler(mLooper.getLooper());

        // Accept any policy sent to the NetworkAgent.
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {

                Object[] args = invocation.getArguments();
                DscpPolicy policy = (DscpPolicy) args[0];
                mNetworkAgentHandler.post(() -> mQosPolicyRequestHandler.setQosPolicyStatus(
                        policy.getPolicyId(), NetworkAgent.DSCP_POLICY_STATUS_SUCCESS));
                return null;
            }
        }).when(mWifiNetworkAgent).sendAddDscpPolicy(any(DscpPolicy.class));
        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            int policyId = (int) args[0];
            mNetworkAgentHandler.post(() -> mQosPolicyRequestHandler.setQosPolicyStatus(
                    policyId, NetworkAgent.DSCP_POLICY_STATUS_DELETED));
            return null;
        }).when(mWifiNetworkAgent).sendRemoveDscpPolicy(anyInt());
    }

    private QosPolicyRequest createQosPolicyRequest(
            int policyId, @QosPolicyRequestType int requestType,
            int dscp, @Nullable byte[] srcIp, @Nullable byte[] dstIp,
            @Nullable Integer srcPort, @Nullable int[] dstPortRange,
            @Nullable Integer protocol) {
        QosPolicyClassifierParams classifierParams = new QosPolicyClassifierParams(
                srcIp != null, srcIp,
                dstIp != null, dstIp,
                srcPort != null ? srcPort : DscpPolicy.SOURCE_PORT_ANY,
                dstPortRange,
                protocol != null ? protocol : DscpPolicy.PROTOCOL_ANY);
        return new QosPolicyRequest((byte) policyId, requestType, (byte) dscp, classifierParams);
    }

    /**
     * Tests the handling of a valid QoS policy request event.
     */
    @Test
    public void testSingleQosPolicyRequestEvent() throws Exception {
        ArrayList<QosPolicyRequest> policies = new ArrayList();
        policies.add(createQosPolicyRequest(1, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                TEST_INET_ADDR /* srcIp */, null, null, null, null));
        policies.add(createQosPolicyRequest(2, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, 1337 /* srcPort */, null, null));
        policies.add(createQosPolicyRequest(3, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_REMOVE, 0,
                null, null, null, null, null));
        policies.add(createQosPolicyRequest(4, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, null, new int[]{350, 150} /* invalid dstPortRange */, null));
        QosPolicyRequest removePolicyRequest = policies.get(2);
        QosPolicyRequest invalidPolicyRequest = policies.get(3);

        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN, policies);
        mLooper.dispatchAll();

        verify(mWifiNetworkAgent, times(2))
                .sendAddDscpPolicy(any());  /* excludes add request with invalid parameters */
        verify(mWifiNetworkAgent, times(1)).sendRemoveDscpPolicy(anyInt());
        verify(mWifiNative).sendQosPolicyResponse(eq(TEST_IFACE_NAME), eq(QOS_REQUEST_DIALOG_TOKEN),
                eq(true), mQosStatusListCaptor.capture());

        assertEquals(policies.size(), mQosStatusListCaptor.getValue().size());
        for (QosPolicyStatus status : mQosStatusListCaptor.getValue()) {
            if (status.policyId == invalidPolicyRequest.policyId) {
                assertEquals(NetworkAgent.DSCP_POLICY_STATUS_REQUEST_DECLINED,
                        status.statusCode);
            } else if (status.policyId == removePolicyRequest.policyId) {
                assertEquals(NetworkAgent.DSCP_POLICY_STATUS_DELETED, status.statusCode);
            } else {  // valid add request
                assertEquals(NetworkAgent.DSCP_POLICY_STATUS_SUCCESS, status.statusCode);
            }
        }
    }

    /**
     * Tests the handling of a series of QoS policy request events, ensuring that they
     * are queued and processed correctly.
     */
    @Test
    public void testMultipleQosPolicyRequestEvents() throws Exception {
        ArrayList<QosPolicyRequest> policies = new ArrayList();
        policies.add(createQosPolicyRequest(1, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                TEST_INET_ADDR /* srcIp */, null, null, null, null));
        policies.add(createQosPolicyRequest(2, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, 1337 /* srcPort */, null, null));
        policies.add(createQosPolicyRequest(3, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, null, new int[]{15, 256} /* dstPortRange */, null));

        mInOrder = inOrder(mWifiNative);
        int numQosPolicyRequestEvents = 10;
        for (int i = 0; i < numQosPolicyRequestEvents; i++) {
            mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN + i, policies);
        }
        mLooper.dispatchAll();

        verify(mWifiNetworkAgent, times(policies.size() * numQosPolicyRequestEvents))
                .sendAddDscpPolicy(any());
        for (int i = 0; i < numQosPolicyRequestEvents; i++) {
            mInOrder.verify(mWifiNative).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                    eq(QOS_REQUEST_DIALOG_TOKEN + i), eq(true), mQosStatusListCaptor.capture());
            assertEquals(policies.size(), mQosStatusListCaptor.getValue().size());
            for (QosPolicyStatus status : mQosStatusListCaptor.getValue()) {
                assertEquals(NetworkAgent.DSCP_POLICY_STATUS_SUCCESS, status.statusCode);
            }
        }
    }

    /**
     * Tests that a QoS policy request event containing duplicate policy id's
     * is handled correctly.
     */
    @Test
    public void testQosPolicyRequestEventWithDuplicatePolicyIds() throws Exception {
        ArrayList<QosPolicyRequest> policies = new ArrayList();
        policies.add(createQosPolicyRequest(1, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                TEST_INET_ADDR /* srcIp */, null, null, null, null));
        policies.add(createQosPolicyRequest(2, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, 1337, null, null));
        policies.add(createQosPolicyRequest(1 /* duplicate id */,
                SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD,
                0, null, null, 1776 /* srcPort */, null, null));

        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN, policies);
        mLooper.dispatchAll();

        verify(mWifiNetworkAgent, never()).sendAddDscpPolicy(any());
        verify(mWifiNative).sendQosPolicyResponse(eq(TEST_IFACE_NAME), eq(QOS_REQUEST_DIALOG_TOKEN),
                eq(true), mQosStatusListCaptor.capture());
        assertEquals(policies.size(), mQosStatusListCaptor.getValue().size());
        for (QosPolicyStatus status : mQosStatusListCaptor.getValue()) {
            assertEquals(NetworkAgent.DSCP_POLICY_STATUS_REQUEST_DECLINED, status.statusCode);
        }
    }

    /**
     * Tests that a QoS policy request event containing no policies is handled correctly.
     */
    @Test
    public void testQosPolicyRequestEventWithEmptyPolicyList() throws Exception {
        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN, new ArrayList());
        mLooper.dispatchAll();
        verify(mWifiNetworkAgent, never()).sendAddDscpPolicy(any());
        verify(mWifiNetworkAgent, never()).sendRemoveDscpPolicy(anyInt());
        verify(mWifiNative).sendQosPolicyResponse(eq(TEST_IFACE_NAME), eq(QOS_REQUEST_DIALOG_TOKEN),
                eq(true), mQosStatusListCaptor.capture());
        assertEquals(0, mQosStatusListCaptor.getValue().size());
    }

    /**
     * Tests that moreResources is set to false in the QoS policy response if a policy is rejected
     * with status NetworkAgent.DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES.
     */
    @Test
    public void testMoreResourcesIsFalseIfInsufficientResources() throws Exception {
        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            DscpPolicy policy = (DscpPolicy) args[0];
            // Reject policy 3 for this test case.
            int policyId = policy.getPolicyId();
            int status = policyId == 3
                    ? NetworkAgent.DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES
                    : NetworkAgent.DSCP_POLICY_STATUS_SUCCESS;
            mNetworkAgentHandler.post(() -> mQosPolicyRequestHandler.setQosPolicyStatus(
                    policyId, status));
            return null;
        }).when(mWifiNetworkAgent).sendAddDscpPolicy(any(DscpPolicy.class));

        ArrayList<QosPolicyRequest> policies = new ArrayList();
        policies.add(createQosPolicyRequest(1, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                TEST_INET_ADDR /* srcIp */, null, null, null, null));
        policies.add(createQosPolicyRequest(2, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, 1337 /* srcPort */, null, null));
        policies.add(createQosPolicyRequest(3, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, null, new int[]{15, 256} /* dstPortRange */, null));
        policies.add(createQosPolicyRequest(4, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, 1776 /* srcPort */, null, null));

        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN, policies);
        mLooper.dispatchAll();

        verify(mWifiNetworkAgent, times(policies.size())).sendAddDscpPolicy(any());
        verify(mWifiNative).sendQosPolicyResponse(eq(TEST_IFACE_NAME), eq(QOS_REQUEST_DIALOG_TOKEN),
                eq(false) /* morePolicies */, mQosStatusListCaptor.capture());
        assertEquals(policies.size(), mQosStatusListCaptor.getValue().size());

        for (QosPolicyStatus status : mQosStatusListCaptor.getValue()) {
            if (status.policyId == 3) {
                assertEquals(NetworkAgent.DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES,
                        status.statusCode);
            }  else {
                assertEquals(NetworkAgent.DSCP_POLICY_STATUS_SUCCESS, status.statusCode);
            }
        }
    }

    /*
     * Tests that the processing queue and current request state are cleared if the network agent
     * is set to null during processing.
     */
    @Test
    public void testNetworkAgentSetToNullDuringProcessing() throws Exception {
        ArrayList<QosPolicyRequest> policies = new ArrayList();
        policies.add(createQosPolicyRequest(1, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                TEST_INET_ADDR /* srcIp */, null, null, null, null));
        policies.add(createQosPolicyRequest(2, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, 1337 /* srcPort */, null, null));
        policies.add(createQosPolicyRequest(3, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, null, new int[]{15, 256} /* dstPortRange */, null));

        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN, policies);
        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN + 1, policies);
        mQosPolicyRequestHandler.setNetworkAgent(null);
        mLooper.dispatchAll();

        mQosPolicyRequestHandler.setNetworkAgent(mWifiNetworkAgent);
        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN + 2, policies);
        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN + 3, policies);
        mLooper.dispatchAll();

        // First 2 requests should have been cleared out when the network agent was set to null.
        verify(mWifiNative, never()).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                eq(QOS_REQUEST_DIALOG_TOKEN), eq(true), mQosStatusListCaptor.capture());
        verify(mWifiNative, never()).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                eq(QOS_REQUEST_DIALOG_TOKEN + 1), eq(true), mQosStatusListCaptor.capture());

        // Next 2 requests should have been processed as expected.
        verify(mWifiNative).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                eq(QOS_REQUEST_DIALOG_TOKEN + 2), eq(true), mQosStatusListCaptor.capture());
        verify(mWifiNative).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                eq(QOS_REQUEST_DIALOG_TOKEN + 3), eq(true), mQosStatusListCaptor.capture());
    }

    /*
     * Tests that if the existing network agent is replaced by a new one (ex. in the case of a NUD
     * failure), we clear out the queue and reset all existing policies.
     */
    @Test
    public void testNetworkAgentReplacedDuringProcessing() throws Exception {
        ArrayList<QosPolicyRequest> policies = new ArrayList();
        policies.add(createQosPolicyRequest(1, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                TEST_INET_ADDR /* srcIp */, null, null, null, null));
        policies.add(createQosPolicyRequest(2, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, 1337 /* srcPort */, null, null));
        policies.add(createQosPolicyRequest(3, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                null, null, null, new int[]{15, 256} /* dstPortRange */, null));

        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN, policies);
        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN + 1, policies);
        mQosPolicyRequestHandler.setNetworkAgent(mWifiNetworkAgentAlt);
        mLooper.dispatchAll();

        verify(mWifiNative, never()).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                eq(QOS_REQUEST_DIALOG_TOKEN), anyBoolean(), any());
        verify(mWifiNative, never()).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                eq(QOS_REQUEST_DIALOG_TOKEN + 1), anyBoolean(), any());
        verify(mClientModeImpl).clearQueuedQosMessages();
        verify(mWifiNetworkAgentAlt).sendRemoveAllDscpPolicies();
        verify(mWifiNative).removeAllQosPolicies(TEST_IFACE_NAME);
    }

    /*
     * Tests that if we time out while waiting for a response from the network agent,
     * we clear out the queue and reset all existing policies.
     */
    @Test
    public void testRequestTimesOutDuringProcessing() throws Exception {
        // Do not respond to any remove requests for this test case.
        doNothing().when(mWifiNetworkAgent).sendRemoveDscpPolicy(anyInt());
        ArrayList<QosPolicyRequest> removePolicies = new ArrayList();
        removePolicies.add(createQosPolicyRequest(1,
                SupplicantStaIfaceHal.QOS_POLICY_REQUEST_REMOVE, 0, null, null, null, null, null));

        ArrayList<QosPolicyRequest> addPolicies = new ArrayList();
        addPolicies.add(createQosPolicyRequest(1, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, 0,
                TEST_INET_ADDR /* srcIp */, null, null, null, null));

        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN, removePolicies);
        mQosPolicyRequestHandler.queueQosPolicyRequest(QOS_REQUEST_DIALOG_TOKEN + 1, addPolicies);
        mLooper.dispatchAll();

        mLooper.moveTimeForward(QosPolicyRequestHandler.PROCESSING_TIMEOUT_MILLIS + 1);
        mLooper.dispatchAll();

        verify(mWifiNative, never()).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                eq(QOS_REQUEST_DIALOG_TOKEN), anyBoolean(), any());
        verify(mWifiNative, never()).sendQosPolicyResponse(eq(TEST_IFACE_NAME),
                eq(QOS_REQUEST_DIALOG_TOKEN + 1), anyBoolean(), any());
        verify(mClientModeImpl).clearQueuedQosMessages();
        verify(mWifiNetworkAgent).sendRemoveAllDscpPolicies();
        verify(mWifiNative).removeAllQosPolicies(TEST_IFACE_NAME);
    }
}
