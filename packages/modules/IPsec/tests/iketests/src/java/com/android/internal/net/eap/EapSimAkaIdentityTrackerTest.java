/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.net.eap.test;

import static com.android.internal.net.TestUtils.hexStringToByteArray;
import static com.android.internal.net.eap.test.EapSimAkaIdentityTracker.MAX_NUMBER_OF_REAUTH_INFO;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class EapSimAkaIdentityTrackerTest {
    private static final int COUNTER_INIT = 0;
    private static final int COUNTER = 10;

    private static final String TEST_EAP_IDENTITY = "0testPermanentId@google.com";
    private static final String TEST_REAUTH_ID_PREFIX = "4testReauthId";
    private static final String TEST_REAUTH_ID_REALM = "@google.com";
    private static final String TEST_MK_PREFIX = "F21AB6D0AA1103269C0760F94B28C957745EF8";
    private static final String TEST_KENCR_PREFIX = "1C2B848ADA2B9485C52517D1A92BF4";
    private static final String TEST_KAUT_PREFIX = "C9500EC59DC62C7D7F5E9E445FA1A3";

    private EapSimAkaIdentityTracker mEapSimAkaIdentityTracker;

    @Before
    public void setUp() {
        mEapSimAkaIdentityTracker = EapSimAkaIdentityTracker.getInstance();
    }

    @Test
    public void testRegisterReauthCredentials() throws Exception {
        mEapSimAkaIdentityTracker.clearReauthInfoMap();
        for (int i = 0; i < MAX_NUMBER_OF_REAUTH_INFO; i++) {
            mEapSimAkaIdentityTracker.registerReauthCredentials(
                    generateReauthIdWithIndex(i),
                    TEST_EAP_IDENTITY,
                    COUNTER_INIT,
                    generateSecurityKeyWithIndex(TEST_MK_PREFIX, i),
                    generateSecurityKeyWithIndex(TEST_KENCR_PREFIX, i),
                    generateSecurityKeyWithIndex(TEST_KAUT_PREFIX, i));
        }
        assertEquals(MAX_NUMBER_OF_REAUTH_INFO, mEapSimAkaIdentityTracker.getNumberOfReauthInfo());
        for (int i = MAX_NUMBER_OF_REAUTH_INFO - 1; i >= 0; i--) {
            verifyReauthInfoWithIndex(i);
            mEapSimAkaIdentityTracker.deleteReauthInfo(
                    generateReauthIdWithIndex(i), TEST_EAP_IDENTITY);
        }
        assertEquals(0, mEapSimAkaIdentityTracker.getNumberOfReauthInfo());
    }

    @Test
    public void testReachMaxNumberOfReauthInfo() throws Exception {
        int numReauthInfo = 100;
        mEapSimAkaIdentityTracker.clearReauthInfoMap();
        for (int i = 0; i < numReauthInfo; i++) {
            mEapSimAkaIdentityTracker.registerReauthCredentials(
                    generateReauthIdWithIndex(i),
                    TEST_EAP_IDENTITY,
                    COUNTER_INIT,
                    generateSecurityKeyWithIndex(TEST_MK_PREFIX, i),
                    generateSecurityKeyWithIndex(TEST_KENCR_PREFIX, i),
                    generateSecurityKeyWithIndex(TEST_KAUT_PREFIX, i));
        }
        assertEquals(MAX_NUMBER_OF_REAUTH_INFO, mEapSimAkaIdentityTracker.getNumberOfReauthInfo());
        for (int i = numReauthInfo - 1; i >= numReauthInfo - MAX_NUMBER_OF_REAUTH_INFO; i--) {
            verifyReauthInfoWithIndex(i);
        }
        assertTrue(mEapSimAkaIdentityTracker.getNumberOfReauthInfo() == MAX_NUMBER_OF_REAUTH_INFO);
    }

    private void verifyReauthInfoWithIndex(int i) {
        EapSimAkaIdentityTracker.ReauthInfo info =
                mEapSimAkaIdentityTracker.getReauthInfo(
                        generateReauthIdWithIndex(i), TEST_EAP_IDENTITY);
        assertNotNull(info);
        assertArrayEquals(generateSecurityKeyWithIndex(TEST_MK_PREFIX, i), info.getMk());
        assertArrayEquals(
                generateSecurityKeyWithIndex(TEST_KENCR_PREFIX, i), info.getKeyEncr());
        assertArrayEquals(generateSecurityKeyWithIndex(TEST_KAUT_PREFIX, i), info.getKeyAut());
    }

    private String generateReauthIdWithIndex(int index) {
        if (index < 0 || index > 99) {
            throw new IllegalArgumentException();
        }
        String indexString = Integer.toString(index);
        if (indexString.length() == 1) {
            indexString = "0" + indexString;
        }
        return TEST_REAUTH_ID_PREFIX + indexString + TEST_REAUTH_ID_REALM;
    }

    private byte[] generateSecurityKeyWithIndex(String keyPrefix, int index) {
        if (keyPrefix.isEmpty() || index < 0 || index > 99) {
            throw new IllegalArgumentException();
        }
        String indexString = Integer.toString(index);
        if (indexString.length() == 1) {
            indexString = "0" + indexString;
        }
        return hexStringToByteArray(keyPrefix + indexString);
    }
}
