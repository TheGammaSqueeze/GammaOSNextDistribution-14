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

package com.android.internal.net.eap.test.message.ttls;

import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.internal.net.eap.test.message.ttls.EapTtlsOutboundFragmentationHelper.FragmentationResult;

import org.junit.Before;
import org.junit.Test;

public class EapTtlsOutboundFragmentationHelperTest {
    private static final int FRAGMENT_SIZE = EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES.length;

    private EapTtlsOutboundFragmentationHelper mFragmentationHelper;

    @Before
    public void setUp() {
        mFragmentationHelper = new EapTtlsOutboundFragmentationHelper(FRAGMENT_SIZE);
    }

    @Test
    public void testOutboundFragmentation_initialFragment() {
        mFragmentationHelper.setupOutboundFragmentation(
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES);

        FragmentationResult result = mFragmentationHelper.getNextOutboundFragment();

        assertTrue(result.hasRemainingFragments);
        assertTrue(mFragmentationHelper.hasRemainingFragments());
        assertArrayEquals(EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES, result.fragmentedData);
    }

    @Test
    public void testOutboundFragmentation_finalFragment() {
        mFragmentationHelper.setupOutboundFragmentation(
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES);
        mFragmentationHelper.getNextOutboundFragment();

        FragmentationResult result = mFragmentationHelper.getNextOutboundFragment();

        assertFalse(result.hasRemainingFragments);
        assertFalse(mFragmentationHelper.hasRemainingFragments());
        assertArrayEquals(EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES, result.fragmentedData);
    }

    @Test
    public void testOutboundFragmentation_noFragmentation() {
        mFragmentationHelper.setupOutboundFragmentation(EAP_TTLS_DUMMY_DATA_BYTES);

        FragmentationResult result = mFragmentationHelper.getNextOutboundFragment();

        assertFalse(result.hasRemainingFragments);
        assertFalse(mFragmentationHelper.hasRemainingFragments());
        assertArrayEquals(EAP_TTLS_DUMMY_DATA_BYTES, result.fragmentedData);
    }

    @Test(expected = IllegalStateException.class)
    public void testOutboundFragmentation_nullBuffer() {
        mFragmentationHelper.getNextOutboundFragment();
    }

    @Test(expected = IllegalStateException.class)
    public void testOutboundFragmentation_endOfBuffer() {
        mFragmentationHelper.setupOutboundFragmentation(EAP_TTLS_DUMMY_DATA_BYTES);
        mFragmentationHelper.getNextOutboundFragment();

        mFragmentationHelper.getNextOutboundFragment();
    }
}
