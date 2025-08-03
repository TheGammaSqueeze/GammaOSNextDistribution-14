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
import static com.android.internal.net.eap.test.message.ttls.EapTtlsInboundFragmentationHelper.FRAGMENTATION_STATUS_ACK;
import static com.android.internal.net.eap.test.message.ttls.EapTtlsInboundFragmentationHelper.FRAGMENTATION_STATUS_ASSEMBLED;
import static com.android.internal.net.eap.test.message.ttls.EapTtlsInboundFragmentationHelper.FRAGMENTATION_STATUS_INVALID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.internal.net.eap.test.crypto.TlsSession;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

public class EapTtlsInboundFragmentationHelperTest {

    static final int BUFFER_SIZE_INVALID = EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES.length / 2;
    static final int BUFFER_SIZE_FRAGMENT_ONE = EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES.length;
    static final int BUFFER_SIZE_FRAGMENT_TWO = EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES.length;
    static final int BUFFER_SIZE_ASSEMBLED_FRAGMENTS =
            BUFFER_SIZE_FRAGMENT_ONE + BUFFER_SIZE_FRAGMENT_TWO;

    static final EapTtlsTypeData EAP_TTLS_TYPE_DATA_INITIAL_FRAGMENT =
            EapTtlsTypeData.getEapTtlsTypeData(
                    true /* isFragmented */,
                    false /* start */,
                    0 /* version */,
                    BUFFER_SIZE_ASSEMBLED_FRAGMENTS,
                    EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES);
    static final EapTtlsTypeData EAP_TTLS_TYPE_DATA_FINAL_FRAGMENT =
            EapTtlsTypeData.getEapTtlsTypeData(
                    false /* isFragmented */,
                    false /* start */,
                    0 /* version */,
                    0 /* length */,
                    EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES);
    static final EapTtlsTypeData EAP_TTLS_TYPE_DATA =
            EapTtlsTypeData.getEapTtlsTypeData(
                    false /* isFragmented */,
                    false /* start */,
                    0 /* version */,
                    0 /* length */,
                    EAP_TTLS_DUMMY_DATA_BYTES);
    static final EapTtlsTypeData EAP_TTLS_TYPE_FINAL_FRAGMENT_LENGTH_INCLUDED =
            EapTtlsTypeData.getEapTtlsTypeData(
                    false /* isFragmented */,
                    false /* start */,
                    0 /* version */,
                    BUFFER_SIZE_FRAGMENT_TWO,
                    EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES);
    static final EapTtlsTypeData EAP_TTLS_TYPE_INITIAL_FRAGMENT_INVALID_LENGTH =
            EapTtlsTypeData.getEapTtlsTypeData(
                    true /* isFragmented */,
                    false /* start */,
                    0 /* version */,
                    BUFFER_SIZE_INVALID,
                    EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES);

    private EapTtlsInboundFragmentationHelper mFragmentationHelper;

    @Before
    public void setUp() {
        mFragmentationHelper = new EapTtlsInboundFragmentationHelper();
    }

    @Test
    public void testInboundFragmentation_initialFragment() {
        int status =
                mFragmentationHelper.assembleInboundMessage(EAP_TTLS_TYPE_DATA_INITIAL_FRAGMENT);

        assertEquals(FRAGMENTATION_STATUS_ACK, status);
        assertTrue(mFragmentationHelper.isAwaitingFragments());
        assertArrayEquals(
                EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES,
                TlsSession.getByteArrayFromBuffer(mFragmentationHelper.mFragmentedData));
    }

    @Test
    public void testInboundFragmentation_finalFragment() {
        mFragmentationHelper.mIsAwaitingFragments = true;
        mFragmentationHelper.mFragmentedData = ByteBuffer.allocate(BUFFER_SIZE_ASSEMBLED_FRAGMENTS);
        mFragmentationHelper.mFragmentedData.put(EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES);

        int status = mFragmentationHelper.assembleInboundMessage(EAP_TTLS_TYPE_DATA_FINAL_FRAGMENT);

        assertEquals(FRAGMENTATION_STATUS_ASSEMBLED, status);
        assertFalse(mFragmentationHelper.isAwaitingFragments());
        assertArrayEquals(
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES,
                mFragmentationHelper.getAssembledInboundFragment());
    }

    @Test
    public void testInboundFragmentation_secondFragment_lengthIncluded() {
        mFragmentationHelper.mIsAwaitingFragments = true;

        int status =
                mFragmentationHelper.assembleInboundMessage(
                        EAP_TTLS_TYPE_FINAL_FRAGMENT_LENGTH_INCLUDED);

        assertEquals(FRAGMENTATION_STATUS_INVALID, status);
    }

    @Test
    public void testInboundFragmentation_invalidLength() {
        int status =
                mFragmentationHelper.assembleInboundMessage(
                        EAP_TTLS_TYPE_INITIAL_FRAGMENT_INVALID_LENGTH);

        assertEquals(FRAGMENTATION_STATUS_INVALID, status);
    }

    @Test
    public void testInboundFragmentation_noFragmentation() {
        int status = mFragmentationHelper.assembleInboundMessage(EAP_TTLS_TYPE_DATA);

        assertEquals(FRAGMENTATION_STATUS_ASSEMBLED, status);
        assertFalse(mFragmentationHelper.isAwaitingFragments());
        assertArrayEquals(
                EAP_TTLS_DUMMY_DATA_BYTES, mFragmentationHelper.getAssembledInboundFragment());
    }
}
