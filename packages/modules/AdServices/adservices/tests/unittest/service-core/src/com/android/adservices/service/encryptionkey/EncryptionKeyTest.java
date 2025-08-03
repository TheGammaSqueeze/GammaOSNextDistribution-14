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

package com.android.adservices.service.encryptionkey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Test;

import java.util.Set;

/** Unit tests for {@link EncryptionKey} */
public class EncryptionKeyTest {

    private static final String ID = "1";
    private static final String ENROLLMENT_ID1 = "10";
    private static final String ENROLLMENT_ID2 = "11";
    private static final Uri REPORTING_ORIGIN = Uri.parse("https://test1.com/trigger");
    private static final String ENCRYPTION_KEY_URL = "https://test1.com/encryption-keys";
    private static final int KEY_COMMITMENT_ID = 1;
    private static final String BODY = "WVZBTFVF";
    private static final long EXPIRATION = 100000L;
    private static final long LAST_FETCH_TIME = 12345L;

    private static EncryptionKey createKeyCommitment(String enrollmentId) {
        return new EncryptionKey.Builder()
                .setId(ID)
                .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                .setEnrollmentId(enrollmentId)
                .setReportingOrigin(REPORTING_ORIGIN)
                .setEncryptionKeyUrl(ENCRYPTION_KEY_URL)
                .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                .setKeyCommitmentId(KEY_COMMITMENT_ID)
                .setBody(BODY)
                .setExpiration(EXPIRATION)
                .setLastFetchTime(LAST_FETCH_TIME)
                .build();
    }

    /** Unit test for encryption key creation using builder. */
    @Test
    public void testCreation() throws Exception {
        EncryptionKey result = createKeyCommitment(ENROLLMENT_ID1);

        assertEquals(ID, result.getId());
        assertEquals(EncryptionKey.KeyType.ENCRYPTION, result.getKeyType());
        assertEquals(ENROLLMENT_ID1, result.getEnrollmentId());
        assertEquals(REPORTING_ORIGIN, result.getReportingOrigin());
        assertEquals(ENCRYPTION_KEY_URL, result.getEncryptionKeyUrl());
        assertEquals(EncryptionKey.ProtocolType.HPKE, result.getProtocolType());
        assertEquals(KEY_COMMITMENT_ID, result.getKeyCommitmentId());
        assertEquals(BODY, result.getBody());
        assertEquals(EXPIRATION, result.getExpiration());
        assertEquals(LAST_FETCH_TIME, result.getLastFetchTime());
    }

    /** Unit test for encryption key default creation. */
    @Test
    public void testDefaults() throws Exception {
        EncryptionKey result = new EncryptionKey.Builder().build();

        assertNull(result.getId());
        assertEquals(EncryptionKey.KeyType.ENCRYPTION, result.getKeyType());
        assertNull(result.getEnrollmentId());
        assertNull(result.getReportingOrigin());
        assertNull(result.getEncryptionKeyUrl());
        assertEquals(EncryptionKey.ProtocolType.HPKE, result.getProtocolType());
        assertEquals(0, result.getKeyCommitmentId());
        assertNull(result.getBody());
        assertEquals(0L, result.getExpiration());
        assertEquals(0L, result.getLastFetchTime());
    }

    /** Unit test for encryption key hashcode equals. */
    @Test
    public void testHashCode_equals() throws Exception {
        final EncryptionKey result1 = createKeyCommitment(ENROLLMENT_ID1);
        final EncryptionKey result2 = createKeyCommitment(ENROLLMENT_ID1);
        final Set<EncryptionKey> resultSet1 = Set.of(result1);
        final Set<EncryptionKey> resultSet2 = Set.of(result2);

        assertEquals(result1.hashCode(), result2.hashCode());
        assertEquals(result1, result2);
        assertEquals(resultSet1, resultSet2);
    }

    /** Unit test for encryption key hashcode not equals. */
    @Test
    public void testHashCode_notEquals() throws Exception {
        final EncryptionKey result1 = createKeyCommitment(ENROLLMENT_ID1);
        final EncryptionKey result2 = createKeyCommitment(ENROLLMENT_ID2);
        final Set<EncryptionKey> resultSet1 = Set.of(result1);
        final Set<EncryptionKey> resultSet2 = Set.of(result2);

        assertNotEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result2);
        assertNotEquals(resultSet1, resultSet2);
    }
}
