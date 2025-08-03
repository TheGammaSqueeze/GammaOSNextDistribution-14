/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.IListListener;
import android.net.wifi.QosCharacteristics;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.WifiManager;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.test.TestLooper;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApplicationQosPolicyRequestHandlerTest {
    private static final String TEST_IFACE_NAME_0 = "wlan0";
    private static final String TEST_IFACE_NAME_1 = "wlan1";
    private static final int TEST_POLICY_ID_START = 10;
    private static final int TEST_UID = 12345;

    private ApplicationQosPolicyRequestHandler mDut;
    private TestLooper mLooper;

    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock WifiNative mWifiNative;
    @Mock HandlerThread mHandlerThread;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock Context mContext;
    @Mock IListListener mIListListener;
    @Mock ClientModeManager mClientModeManager0;
    @Mock ClientModeManager mClientModeManager1;
    @Mock IBinder mBinder;

    @Captor ArgumentCaptor<SupplicantStaIfaceHal.QosScsResponseCallback> mApCallbackCaptor;
    @Captor ArgumentCaptor<List<Integer>> mApplicationCallbackResultCaptor;
    @Captor ArgumentCaptor<List<QosPolicyParams>> mPolicyListCaptor;
    @Captor ArgumentCaptor<List<Byte>> mPolicyIdByteListCaptor;
    @Captor ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

    private List<String> mApCallbackReceivedIfaces;

    private class ApplicationQosPolicyRequestHandlerSpy extends ApplicationQosPolicyRequestHandler {
        ApplicationQosPolicyRequestHandlerSpy() {
            super(mActiveModeWarden, mWifiNative, mHandlerThread, mDeviceConfigFacade, mContext);
        }

        @Override
        protected void logApCallbackMockable(String ifaceName,
                List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList) {
            mApCallbackReceivedIfaces.add(ifaceName);
        }
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        MockitoAnnotations.initMocks(this);
        mApCallbackReceivedIfaces = new ArrayList<>();

        mLooper = new TestLooper();
        when(mHandlerThread.getLooper()).thenReturn(mLooper.getLooper());
        mDut = new ApplicationQosPolicyRequestHandlerSpy();
        verify(mWifiNative).registerQosScsResponseCallback(mApCallbackCaptor.capture());

        when(mBinder.pingBinder()).thenReturn(true);
        when(mClientModeManager0.getInterfaceName()).thenReturn(TEST_IFACE_NAME_0);
        when(mClientModeManager1.getInterfaceName()).thenReturn(TEST_IFACE_NAME_1);
        setupSynchronousResponse(SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_SENT);
    }

    private QosPolicyParams createDownlinkPolicy(int policyId) {
        return new QosPolicyParams.Builder(policyId, QosPolicyParams.DIRECTION_DOWNLINK)
                .setUserPriority(QosPolicyParams.USER_PRIORITY_BACKGROUND_LOW)
                .setIpVersion(QosPolicyParams.IP_VERSION_4)
                .build();
    }

    private QosPolicyParams createUplinkPolicy(int policyId) {
        QosCharacteristics mockQosCharacteristics = mock(QosCharacteristics.class);
        when(mockQosCharacteristics.validate()).thenReturn(true);
        return new QosPolicyParams.Builder(policyId, QosPolicyParams.DIRECTION_UPLINK)
                .setQosCharacteristics(mockQosCharacteristics)
                .build();
    }

    private List<QosPolicyParams> createDownlinkPolicyList(int size, int basePolicyId) {
        List<QosPolicyParams> policies = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            policies.add(createDownlinkPolicy(basePolicyId + i));
        }
        return policies;
    }

    private List<QosPolicyParams> createUplinkPolicyList(int size, int basePolicyId) {
        List<QosPolicyParams> policies = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            policies.add(createUplinkPolicy(basePolicyId + i));
        }
        return policies;
    }

    private List<Integer> generateIntegerList(int size, int val) {
        List<Integer> integerList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            integerList.add(val);
        }
        return integerList;
    }

    private List<SupplicantStaIfaceHal.QosPolicyStatus> generateHalStatusList(
            List<QosPolicyParams> policyList, List<Integer> statusList) {
        List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList = new ArrayList<>();
        for (int i = 0; i < policyList.size(); i++) {
            halStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                    policyList.get(i).getTranslatedPolicyId(), statusList.get(i)));
        }
        return halStatusList;
    }

    private void addPoliciesToTable(List<QosPolicyParams> policyList) {
        List<List<QosPolicyParams>> batches = mDut.divideRequestIntoBatches(policyList);
        for (List<QosPolicyParams> batch : batches) {
            mDut.queueAddRequest(batch, mIListListener, mBinder, TEST_UID);
            triggerAndVerifyApCallback(TEST_IFACE_NAME_0, batch,
                    SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        }
    }

    private void setupSynchronousResponse(
            @SupplicantStaIfaceHal.QosPolicyScsRequestStatusCode int expectedStatus) {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                List<QosPolicyParams> policyList = (List<QosPolicyParams>) args[1];
                List<Integer> statusList = generateIntegerList(policyList.size(), expectedStatus);
                return generateHalStatusList(policyList, statusList);
            }
        }).when(mWifiNative).addQosPolicyRequestForScs(anyString(), anyList());

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                List<Byte> policyIds = (List<Byte>) args[1];
                List<SupplicantStaIfaceHal.QosPolicyStatus> statusList = new ArrayList<>();
                for (byte policyId : policyIds) {
                    statusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                            policyId, expectedStatus));
                }
                return statusList;
            }
        }).when(mWifiNative).removeQosPolicyForScs(anyString(), anyList());
    }

    private List<Integer> getVirtualPolicyIds(List<QosPolicyParams> policyList) {
        List<Integer> virtualPolicyIds = new ArrayList<>();
        for (QosPolicyParams policy : policyList) {
            virtualPolicyIds.add(policy.getTranslatedPolicyId());
        }
        return virtualPolicyIds;
    }

    private void triggerApCallback(String ifaceName, List<QosPolicyParams> policyList,
            @SupplicantStaIfaceHal.QosPolicyScsResponseStatusCode int expectedStatus) {
        List<Integer> virtualPolicyIds = getVirtualPolicyIds(policyList);
        List<Integer> expectedStatusList =
                generateIntegerList(policyList.size(), expectedStatus);

        // Generate the HAL status list.
        List<SupplicantStaIfaceHal.QosPolicyStatus> cbStatusList = new ArrayList<>();
        for (int i = 0; i < virtualPolicyIds.size(); i++) {
            cbStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                    virtualPolicyIds.get(i), expectedStatusList.get(i)));
        }

        // Trigger callback from the AP.
        mApCallbackCaptor.getValue().onApResponse(ifaceName, cbStatusList);
        mLooper.dispatchAll();
    }

    private void triggerAndVerifyApCallback(String ifaceName, List<QosPolicyParams> policyList,
            @SupplicantStaIfaceHal.QosPolicyScsResponseStatusCode int expectedStatus) {
        triggerApCallback(ifaceName, policyList, expectedStatus);
        assertTrue(mApCallbackReceivedIfaces.contains(ifaceName));
    }

    private void verifyApplicationCallback(@WifiManager.QosRequestStatus int expectedStatus)
            throws Exception {
        verify(mIListListener, times(1)).onResult(mApplicationCallbackResultCaptor.capture());
        List<Integer> expectedStatusList = generateIntegerList(
                mApplicationCallbackResultCaptor.getValue().size(), expectedStatus);
        List<Integer> applicationStatusList = mApplicationCallbackResultCaptor.getValue();
        assertTrue(expectedStatusList.equals(applicationStatusList));
    }

    private List<Integer> getPolicyIdsFromPolicyList(List<QosPolicyParams> policyList) {
        List<Integer> policyIds = new ArrayList<>();
        for (QosPolicyParams policy : policyList) {
            policyIds.add(policy.getPolicyId());
        }
        return policyIds;
    }

    /**
     * Tests that the policy add is rejected immediately if there are no active
     * ClientModeManagers providing internet.
     */
    @Test
    public void testDownlinkPolicyAddNoClientModeManagers() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(new ArrayList<>());
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener, mBinder, TEST_UID);
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);
    }

    /**
     * Tests the policy add and remove flow when there is a single ClientModeManager.
     */
    @Test
    public void testDownlinkPolicyAddAndRemove_oneClientModeManager() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener, mBinder, TEST_UID);

        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_TRACKING);
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        mLooper.dispatchAll();
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_TRACKING);

        // Request removal of all the policies.
        List<Integer> policyIds = getPolicyIdsFromPolicyList(policyList);
        mDut.queueRemoveRequest(policyIds, TEST_UID);
        verify(mWifiNative).removeQosPolicyForScs(eq(TEST_IFACE_NAME_0), anyList());
    }

    /**
     * Tests the policy add and remove flow when there are multiple ClientModeManagers.
     */
    @Test
    public void testDownlinkPolicyAddAndRemove_twoClientModeManagers() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0, mClientModeManager1));
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener, mBinder, TEST_UID);

        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_1), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_TRACKING);

        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        triggerAndVerifyApCallback(TEST_IFACE_NAME_1, policyList,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);

        // Request removal of all the policies.
        List<Integer> policyIds = getPolicyIdsFromPolicyList(policyList);
        mDut.queueRemoveRequest(policyIds, TEST_UID);
        verify(mWifiNative).removeQosPolicyForScs(eq(TEST_IFACE_NAME_0), anyList());
        verify(mWifiNative).removeQosPolicyForScs(eq(TEST_IFACE_NAME_1), anyList());
    }

    /**
     * Tests the policy add flow when there are multiple ClientModeManagers,
     * and an error occurs while processing the request for the first ClientModeManager.
     */
    @Test
    public void testDownlinkPolicyAddError_twoClientModeManagers() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0, mClientModeManager1));
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);

        // Immediately reject the policies in WifiNative when the request is first processed.
        setupSynchronousResponse(SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_ERROR_UNKNOWN);
        mDut.queueAddRequest(policyList, mIListListener, mBinder, TEST_UID);

        // Policies should not be sent to WifiNative when the 2nd interface processes the request.
        verify(mWifiNative, times(1)).addQosPolicyRequestForScs(anyString(), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN);
    }

    /**
     * Tests the policy add flow when multiple policy requests are queued.
     */
    @Test
    public void testMultipleDownlinkPolicyRequests() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policyList1 = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        List<QosPolicyParams> policyList2 = createDownlinkPolicyList(5, TEST_POLICY_ID_START + 15);
        IListListener mockPolicy1Listener = mock(IListListener.class);
        IListListener mockPolicy2Listener = mock(IListListener.class);

        mDut.queueAddRequest(policyList1, mockPolicy1Listener, mBinder, TEST_UID);
        mDut.queueAddRequest(policyList2, mockPolicy2Listener, mBinder, TEST_UID);
        verify(mWifiNative, times(1)).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verify(mockPolicy1Listener).onResult(anyList());
        verify(mockPolicy2Listener, never()).onResult(anyList());

        // Trigger callback from the AP for policyList1.
        // This should initiate processing for the next request.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList1,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mWifiNative, times(2)).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verify(mockPolicy2Listener).onResult(anyList());

        // Trigger callback from the AP for policyList2.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList2,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
    }

    /**
     * Tests that an error code is returned immediately if the HAL encounters an error
     * during a policy add.
     */
    @Test
    public void testDownlinkPolicyAddHalError() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        when(mWifiNative.addQosPolicyRequestForScs(anyString(), anyList())).thenReturn(null);
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener, mBinder, TEST_UID);
        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN);
    }

    /**
     * Tests that an error code is immediately returned if the requested policy is rejected by
     * the supplicant due to invalid arguments.
     */
    @Test
    public void testDownlinkPolicyAddInvalidArguments() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        setupSynchronousResponse(SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_INVALID);
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener, mBinder, TEST_UID);
        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_INVALID_PARAMETERS);
    }

    /**
     * Tests that unsolicited callbacks from the AP are ignored.
     */
    @Test
    public void testDownlinkPolicyUnsolicitedCallback() throws Exception {
        // Queue two add requests.
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policyList1 = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        List<QosPolicyParams> policyList2 = createDownlinkPolicyList(5, TEST_POLICY_ID_START + 15);
        IListListener mockPolicy1Listener = mock(IListListener.class);
        IListListener mockPolicy2Listener = mock(IListListener.class);
        mDut.queueAddRequest(policyList1, mockPolicy1Listener, mBinder, TEST_UID);
        mDut.queueAddRequest(policyList2, mockPolicy2Listener, mBinder, TEST_UID);

        // Verify that the first request is processed and the second is not.
        verify(mockPolicy1Listener).onResult(anyList());
        verify(mockPolicy2Listener, never()).onResult(anyList());

        // Trigger an unsolicited callback containing the incorrect policy IDs.
        // Verify that the second request is not processed.
        triggerApCallback(TEST_IFACE_NAME_0, policyList2,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mockPolicy2Listener, never()).onResult(anyList());

        // Trigger an unsolicited callback on the wrong interface.
        triggerApCallback(TEST_IFACE_NAME_1, policyList1,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mockPolicy2Listener, never()).onResult(anyList());

        // Trigger the expected callback. Expect that the second request is processed.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList1,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mockPolicy2Listener).onResult(anyList());
    }

    /**
     * Tests that all policies in a request are rejected if the policy tracking table is full.
     */
    @Test
    public void testPolicyTrackingTableFull() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));

        // Fill tracking table to nearly full capacity.
        List<QosPolicyParams> maxSizePolicyList = createDownlinkPolicyList(254, 1);
        addPoliciesToTable(maxSizePolicyList);
        reset(mWifiNative, mIListListener);

        // Adding 10 new policies should fail due to insufficient resources.
        List<QosPolicyParams> policyList = createDownlinkPolicyList(10, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener, mBinder, TEST_UID);
        verify(mWifiNative, never()).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);
    }

    /**
     * Tests that any policies that are already being tracked are not re-sent to the HAL.
     */
    @Test
    public void testPoliciesAlreadyTrackedByTable() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policies = createDownlinkPolicyList(3, 1);

        // Send the first policy. Will get added to the table.
        mDut.queueAddRequest(policies.subList(0, 1), mIListListener, mBinder, TEST_UID);
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policies.subList(0, 1),
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);

        // Send all 3 policies.
        mDut.queueAddRequest(policies, mIListListener, mBinder, TEST_UID);
        verify(mWifiNative, times(2))
                .addQosPolicyRequestForScs(anyString(), mPolicyListCaptor.capture());

        // Expect that during the second add, only the 2 new policies are sent to the HAL.
        assertEquals(2, mPolicyListCaptor.getAllValues().get(1).size());
    }

    /**
     * Tests that if the caller requests the removal of any policies that are not tracked by
     * the table, the request is not sent to the HAL.
     */
    @Test
    public void testRemovePoliciesNotTrackedByTable() {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<Integer> nonExistentPolicyIds = Arrays.asList(1, 2, 3);
        mDut.queueRemoveRequest(nonExistentPolicyIds, TEST_UID);
        verify(mWifiNative, never()).removeQosPolicyForScs(anyString(), anyList());
    }

    /**
     * Tests that the synchronous response from the HAL is handled properly. This means that:
     *  - The HAL status list is merged correctly with the main status list.
     *  - Any policies rejected by the HAL are removed from the tracking table.
     */
    @Test
    public void testProcessHalAddResponse() {
        // First policy in the request is already tracked by the policy tracking table.
        List<QosPolicyParams> policyList = createDownlinkPolicyList(4, 1);
        List<Integer> statusList = Arrays.asList(
                WifiManager.QOS_REQUEST_STATUS_ALREADY_ACTIVE,
                WifiManager.QOS_REQUEST_STATUS_TRACKING,
                WifiManager.QOS_REQUEST_STATUS_TRACKING,
                WifiManager.QOS_REQUEST_STATUS_TRACKING);

        // Reject the 1st and 3rd policies that were sent to the HAL.
        // Note that only 3 policies were sent to the HAL, since one is already being tracked.
        List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList = new ArrayList<>();
        halStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                2, SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_INVALID));
        halStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                3, SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_SENT));
        halStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                4, SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_INVALID));

        // Check that the status lists are merged correctly.
        List<Integer> expectedStatusList = Arrays.asList(
                WifiManager.QOS_REQUEST_STATUS_ALREADY_ACTIVE,  // Already tracked
                WifiManager.QOS_REQUEST_STATUS_INVALID_PARAMETERS,  // Rejected by HAL
                WifiManager.QOS_REQUEST_STATUS_TRACKING,
                WifiManager.QOS_REQUEST_STATUS_INVALID_PARAMETERS); // Rejected by HAL
        statusList = mDut.processSynchronousHalResponse(
                statusList, halStatusList, policyList, TEST_UID);
        assertTrue(expectedStatusList.equals(statusList));
    }

    /**
     * Tests that if the requester owns a large number of policies, then a removeAll request
     * is divided correctly into several individual remove transactions.
     */
    @Test
    public void testLargeRemoveAllRequest() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));

        // Add 18 policies to the table.
        int numOwnedPolicies = 18;
        List<QosPolicyParams> policyList =
                createDownlinkPolicyList(numOwnedPolicies, TEST_POLICY_ID_START);
        addPoliciesToTable(policyList);

        // Expect that the removeAll is divided into two batches of size 16 and 2, respectively.
        mDut.queueRemoveAllRequest(TEST_UID);
        verify(mWifiNative).removeQosPolicyForScs(
                eq(TEST_IFACE_NAME_0), mPolicyIdByteListCaptor.capture());
        assertEquals(16, mPolicyIdByteListCaptor.getValue().size());

        // Trigger AP callback to start processing next request.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList.subList(0, 16),
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);

        verify(mWifiNative, times(2)).removeQosPolicyForScs(
                eq(TEST_IFACE_NAME_0), mPolicyIdByteListCaptor.capture());
        assertEquals(2, mPolicyIdByteListCaptor.getValue().size());
    }

    /**
     * Tests that if the requesting application dies before its request is processed,
     * the request is not sent to the HAL.
     */
    @Test
    public void testBinderDiedBeforeProcessing() {
        when(mBinder.pingBinder()).thenReturn(false);
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener, mBinder, TEST_UID);
        verify(mWifiNative, never()).addQosPolicyRequestForScs(anyString(), anyList());
    }

    /**
     * Tests that if the callback timeout period expires and no callback is received,
     * the timeout handler starts processing the next queued request.
     */
    @Test
    public void testCallbackTimedOut() throws Exception {
        // Queue two add requests.
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policyList1 = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        List<QosPolicyParams> policyList2 = createDownlinkPolicyList(5, TEST_POLICY_ID_START + 15);
        IListListener mockPolicy1Listener = mock(IListListener.class);
        IListListener mockPolicy2Listener = mock(IListListener.class);
        mDut.queueAddRequest(policyList1, mockPolicy1Listener, mBinder, TEST_UID);
        mDut.queueAddRequest(policyList2, mockPolicy2Listener, mBinder, TEST_UID);
        verify(mWifiNative, times(1)).addQosPolicyRequestForScs(anyString(), anyList());

        // Allow the callback timeout period to expire.
        mLooper.moveTimeForward(ApplicationQosPolicyRequestHandler.CALLBACK_TIMEOUT_MILLIS + 1);
        mLooper.dispatchAll();

        // Verify that the second request is processed.
        verify(mWifiNative, times(2)).addQosPolicyRequestForScs(anyString(), anyList());
        verify(mockPolicy2Listener).onResult(anyList());
    }

    /**
     * Tests that if the death recipient is called with an application's binder,
     * all policies owned by that application are removed.
     */
    @Test
    public void testBinderDeathRecipient() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));

        // Make 2 add requests, each containing 5 new policies.
        int numPolicies = 10;
        List<QosPolicyParams> policyList = createDownlinkPolicyList(
                numPolicies, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList.subList(0, 5), mIListListener, mBinder, TEST_UID);
        mDut.queueAddRequest(policyList.subList(5, 10), mIListListener, mBinder, TEST_UID);

        // Trigger the callbacks to complete processing.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList.subList(0, 5),
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList.subList(5, 10),
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mWifiNative, times(2)).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());

        // Trigger the death recipient. Expect that all 10 policies are removed.
        verify(mBinder).linkToDeath(mDeathRecipientCaptor.capture(), anyInt());
        mDeathRecipientCaptor.getValue().binderDied(mBinder);
        mLooper.dispatchAll();

        verify(mWifiNative).removeQosPolicyForScs(
                eq(TEST_IFACE_NAME_0), mPolicyIdByteListCaptor.capture());
        assertEquals(numPolicies, mPolicyIdByteListCaptor.getValue().size());
    }

    /*
     * Tests that if a large number of policies are in the table, then a call to
     * {@link ApplicationQosPolicyRequestHandler#queueAllPoliciesOnIface} divides the policies
     * correctly into a series of individual add requests.
     */
    @Test
    public void testLargeQueueAllPoliciesRequest() {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));

        // Add 18 policies to the table.
        int numOwnedPolicies = 18;
        List<QosPolicyParams> policyList =
                createDownlinkPolicyList(numOwnedPolicies, TEST_POLICY_ID_START);
        addPoliciesToTable(policyList);

        // Expect that the request is divided into two batches of size 16 and 2, respectively.
        mDut.queueAllPoliciesOnIface(TEST_IFACE_NAME_1, false);
        verify(mWifiNative).addQosPolicyRequestForScs(
                eq(TEST_IFACE_NAME_1), mPolicyListCaptor.capture());
        assertEquals(16, mPolicyListCaptor.getValue().size());

        // Trigger AP callback to start processing next request.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_1, mPolicyListCaptor.getValue(),
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);

        verify(mWifiNative, times(2)).addQosPolicyRequestForScs(
                eq(TEST_IFACE_NAME_1), mPolicyListCaptor.capture());
        assertEquals(2, mPolicyListCaptor.getValue().size());
    }

    /**
     * Tests that uplink policies are only included in the queueAllPolicies request
     * if the includeUplink parameter is true.
     */
    @Test
    public void testQueueAllPoliciesRequest_mixedDirection() {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        int numDownlinkPolicies = 10;
        int numUplinkPolicies = 5;
        addPoliciesToTable(createDownlinkPolicyList(numDownlinkPolicies, TEST_POLICY_ID_START));
        addPoliciesToTable(createUplinkPolicyList(numUplinkPolicies,
                TEST_POLICY_ID_START + numDownlinkPolicies));

        // Only downlink policies.
        mDut.queueAllPoliciesOnIface(TEST_IFACE_NAME_0, false);
        verify(mWifiNative, atLeastOnce()).addQosPolicyRequestForScs(
                eq(TEST_IFACE_NAME_0), mPolicyListCaptor.capture());
        assertEquals(numDownlinkPolicies, mPolicyListCaptor.getValue().size());

        // Trigger AP callback to complete the current request.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, mPolicyListCaptor.getValue(),
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);

        // Both uplink and downlink policies included in the request.
        mDut.queueAllPoliciesOnIface(TEST_IFACE_NAME_0, true);
        verify(mWifiNative, atLeastOnce()).addQosPolicyRequestForScs(
                eq(TEST_IFACE_NAME_0), mPolicyListCaptor.capture());
        assertEquals(numDownlinkPolicies + numUplinkPolicies, mPolicyListCaptor.getValue().size());
    }
}
