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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.wifi.QosCharacteristics;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.WifiManager;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ApplicationQosPolicyTrackingTableTest {
    private static final int MIN_VIRTUAL_POLICY_ID = 0;
    private static final int MAX_VIRTUAL_POLICY_ID = 9;
    private static final int NUM_VIRTUAL_POLICY_IDS = 10;
    private static final int TEST_UID = 12345;
    private static final int TEST_PHYSICAL_POLICY_ID_START = 1;

    private ApplicationQosPolicyTrackingTable mDut;

    @Before
    public void setUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        mDut = new ApplicationQosPolicyTrackingTable(MIN_VIRTUAL_POLICY_ID, MAX_VIRTUAL_POLICY_ID);
    }

    private List<QosPolicyParams> generatePolicyList(int size, int policyIdStart) {
        List<QosPolicyParams> policyList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            policyList.add(new QosPolicyParams.Builder(
                    policyIdStart + i, QosPolicyParams.DIRECTION_DOWNLINK)
                    .setUserPriority(QosPolicyParams.USER_PRIORITY_BACKGROUND_LOW)
                    .setIpVersion(QosPolicyParams.IP_VERSION_4)
                    .build());
        }
        return policyList;
    }

    private List<QosPolicyParams> generateUplinkPolicyList(int size, int policyIdStart) {
        QosCharacteristics mockQosCharacteristics = mock(QosCharacteristics.class);
        when(mockQosCharacteristics.validate()).thenReturn(true);

        List<QosPolicyParams> policyList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            policyList.add(new QosPolicyParams.Builder(
                    policyIdStart + i, QosPolicyParams.DIRECTION_UPLINK)
                    .setQosCharacteristics(mockQosCharacteristics)
                    .build());
        }
        return policyList;
    }

    private List<Integer> getPolicyIdsFromPolicyList(List<QosPolicyParams> policyList) {
        List<Integer> policyIds = new ArrayList<>();
        for (QosPolicyParams policy : policyList) {
            policyIds.add(policy.getPolicyId());
        }
        return policyIds;
    }

    private List<Integer> getVirtualPolicyIdsFromPolicyList(List<QosPolicyParams> policyList) {
        List<Integer> policyIds = new ArrayList<>();
        for (QosPolicyParams policy : policyList) {
            policyIds.add(policy.getTranslatedPolicyId());
        }
        return policyIds;
    }

    private void verifyStatusList(List<Integer> statusList,
            @WifiManager.QosRequestStatus int expectedStatus) {
        for (int status : statusList) {
            assertEquals(expectedStatus, status);
        }
    }

    /**
     * Test that policies can be added successfully to the tracking table.
     */
    @Test
    public void testAddPoliciesSuccess() {
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS, TEST_PHYSICAL_POLICY_ID_START);
        List<Integer> statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_TRACKING);
    }

    /**
     * Test that policies with the same policyIds can be added to the table
     * if they are added by different requesters.
     */
    @Test
    public void testAddPoliciesSuccess_multipleUids() {
        List<QosPolicyParams> sharedPolicyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS / 2, TEST_PHYSICAL_POLICY_ID_START);
        List<Integer> statusList1 = mDut.addPolicies(sharedPolicyList, TEST_UID);
        List<Integer> statusList2 = mDut.addPolicies(sharedPolicyList, TEST_UID + 1);
        verifyStatusList(statusList1, WifiManager.QOS_REQUEST_STATUS_TRACKING);
        verifyStatusList(statusList2, WifiManager.QOS_REQUEST_STATUS_TRACKING);
    }

    /**
     * Test that a policy with a duplicate policyId cannot be added to the table
     * by the same requester.
     */
    @Test
    public void testAddPoliciesFail_duplicatePolicy() {
        List<QosPolicyParams> policyList = generatePolicyList(1, TEST_PHYSICAL_POLICY_ID_START);
        List<Integer> statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_TRACKING);

        // Attempt to re-add the same policy.
        statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_ALREADY_ACTIVE);
    }

    /**
     * Tests that all policies in the batch are rejected if there is
     * not enough room in the table.
     */
    @Test
    public void testAddPoliciesFail_tableFull() {
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS + 1, TEST_PHYSICAL_POLICY_ID_START);
        List<Integer> statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);
    }

    /**
     * Tests that policies can be successfully added and removed from the table.
     */
    @Test
    public void testRemovePoliciesSuccess() {
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS, TEST_PHYSICAL_POLICY_ID_START);
        List<Integer> statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_TRACKING);

        // Remove all existing policies from the table.
        List<Integer> policyIds = getPolicyIdsFromPolicyList(policyList);
        mDut.removePolicies(policyIds, TEST_UID);

        // There should now be space in the table to re-add all policies.
        statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_TRACKING);
    }

    /**
     * Tests that invalid policies in a remove request are ignored.
     */
    @Test
    public void testRemovePoliciesFail_invalidPolicies() {
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS, TEST_PHYSICAL_POLICY_ID_START);
        List<Integer> statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_TRACKING);

        // Policies belong to a different UID.
        List<Integer> policyIds = getPolicyIdsFromPolicyList(policyList);
        mDut.removePolicies(policyIds, TEST_UID + 1);

        // Re-adding the policies should fail because the table is still full.
        statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);

        // Non-existent policy IDs.
        for (int i = 0; i < policyIds.size(); i++) {
            policyIds.set(i, policyIds.get(i) + NUM_VIRTUAL_POLICY_IDS);
        }
        mDut.removePolicies(policyIds, TEST_UID);
        statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);
    }

    /**
     * Tests the {@link ApplicationQosPolicyTrackingTable#filterUntrackedPolicies(List, int)}
     * method.
     */
    @Test
    public void testFilterUntrackedPolicies() {
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS, TEST_PHYSICAL_POLICY_ID_START);
        List<Integer> statusList = mDut.addPolicies(policyList, TEST_UID);
        verifyStatusList(statusList, WifiManager.QOS_REQUEST_STATUS_TRACKING);

        // Successful case.
        List<QosPolicyParams> filteredPolicyList =
                mDut.filterUntrackedPolicies(policyList, TEST_UID);
        assertTrue(policyList.equals(filteredPolicyList));

        // Policies belong to a different UID.
        filteredPolicyList = mDut.filterUntrackedPolicies(policyList, TEST_UID + 1);
        assertTrue(filteredPolicyList.isEmpty());

        // Policies are not in the table.
        policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS, TEST_PHYSICAL_POLICY_ID_START + NUM_VIRTUAL_POLICY_IDS);
        filteredPolicyList = mDut.filterUntrackedPolicies(policyList, TEST_UID);
        assertTrue(filteredPolicyList.isEmpty());
    }

    /**
     * Tests that policy IDs can be translated successfully to virtual IDs.
     */
    @Test
    public void testTranslatePolicyIdsSuccess() {
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS, TEST_PHYSICAL_POLICY_ID_START);
        mDut.addPolicies(policyList, TEST_UID);

        List<Integer> policyIds = getPolicyIdsFromPolicyList(policyList);
        List<Integer> expectedVirtualPolicyIds = getVirtualPolicyIdsFromPolicyList(policyList);
        List<Integer> virtualPolicyIds = mDut.translatePolicyIds(policyIds, TEST_UID);
        assertTrue(expectedVirtualPolicyIds.equals(virtualPolicyIds));
    }

    /**
     * Tests that invalid policy IDs are not translated to virtual IDs.
     */
    @Test
    public void testTranslatePolicyIdsFail_invalidPolicies() {
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS, TEST_PHYSICAL_POLICY_ID_START);
        mDut.addPolicies(policyList, TEST_UID);
        List<Integer> policyIds = getPolicyIdsFromPolicyList(policyList);

        // Policies belong to a different UID.
        List<Integer> virtualPolicyIds = mDut.translatePolicyIds(policyIds, TEST_UID + 1);
        assertTrue(virtualPolicyIds.isEmpty());

        // Non-existent policy IDs.
        for (int i = 0; i < policyIds.size(); i++) {
            policyIds.set(i, policyIds.get(i) + NUM_VIRTUAL_POLICY_IDS);
        }
        virtualPolicyIds = mDut.translatePolicyIds(policyIds, TEST_UID);
        assertTrue(virtualPolicyIds.isEmpty());
    }

    /**
     * Tests that {@link ApplicationQosPolicyTrackingTable#getAllPolicyIdsOwnedByUid(int)}
     * returns the expected policy IDs.
     */
    @Test
    public void testGetAllPolicyIdsOwnedByUid() {
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS, TEST_PHYSICAL_POLICY_ID_START);
        List<Integer> expectedPolicyIds = getPolicyIdsFromPolicyList(policyList);
        mDut.addPolicies(policyList, TEST_UID);

        List<Integer> retrievedPolicyIds = mDut.getAllPolicyIdsOwnedByUid(TEST_UID);
        assertTrue(expectedPolicyIds.equals(retrievedPolicyIds));

        // Non-existent UID should return an empty policy ID list.
        retrievedPolicyIds = mDut.getAllPolicyIdsOwnedByUid(TEST_UID + 1);
        assertTrue(retrievedPolicyIds.isEmpty());
    }

    /**
     * Tests the {@link ApplicationQosPolicyTrackingTable#getAllPolicies(int)} method when
     * all policies have the same direction.
     */
    @Test
    public void testGetAllPoliciesSameDirection() {
        // Empty table should return an empty list.
        List<QosPolicyParams> retrievedPolicies =
                mDut.getAllPolicies(QosPolicyParams.DIRECTION_DOWNLINK);
        assertTrue(retrievedPolicies.isEmpty());

        // Fill table with downlink policies from multiple requesters.
        List<QosPolicyParams> policyList = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS / 2, TEST_PHYSICAL_POLICY_ID_START);
        mDut.addPolicies(policyList, TEST_UID);
        mDut.addPolicies(policyList, TEST_UID + 1);

        // getAllPolicies should return all policies across all requesters.
        retrievedPolicies = mDut.getAllPolicies(QosPolicyParams.DIRECTION_DOWNLINK);
        assertEquals(NUM_VIRTUAL_POLICY_IDS, retrievedPolicies.size());
    }

    /**
     * Tests the {@link ApplicationQosPolicyTrackingTable#getAllPolicies(int)} method when
     * the policies in the table have different directions.
     */
    @Test
    public void testGetAllPoliciesDifferentDirection() {
        assumeTrue(SdkLevel.isAtLeastV());
        List<QosPolicyParams> downlinkPolicies = generatePolicyList(
                NUM_VIRTUAL_POLICY_IDS / 2, TEST_PHYSICAL_POLICY_ID_START);
        mDut.addPolicies(downlinkPolicies, TEST_UID);

        // Table should contain several downlink and no uplink policies.
        List<QosPolicyParams> retrievedPolicies =
                mDut.getAllPolicies(QosPolicyParams.DIRECTION_DOWNLINK);
        assertEquals(downlinkPolicies.size(), retrievedPolicies.size());
        retrievedPolicies = mDut.getAllPolicies(QosPolicyParams.DIRECTION_UPLINK);
        assertEquals(0, retrievedPolicies.size());

        List<QosPolicyParams> uplinkPolicies = generateUplinkPolicyList(
                NUM_VIRTUAL_POLICY_IDS / 2, TEST_PHYSICAL_POLICY_ID_START);
        mDut.addPolicies(uplinkPolicies, TEST_UID + 1);

        // Table should contain both uplink and downlink policies.
        retrievedPolicies = mDut.getAllPolicies(QosPolicyParams.DIRECTION_DOWNLINK);
        assertEquals(downlinkPolicies.size(), retrievedPolicies.size());
        retrievedPolicies = mDut.getAllPolicies(QosPolicyParams.DIRECTION_UPLINK);
        assertEquals(uplinkPolicies.size(), retrievedPolicies.size());
    }
}
