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

package com.android.rkpdapp.unittest;

import static com.google.common.truth.Truth.assertThat;

import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.database.ProvisionedKey;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class ProvisionedKeyTest {
    private static final Random sRandom = new Random();

    private static byte[] randBytes() {
        int length = 16 + sRandom.nextInt(112);
        byte[] bytes = new byte[length];
        sRandom.nextBytes(bytes);
        return bytes;
    }

    private static String randString() {
        return Base64.encodeToString(randBytes(), Base64.URL_SAFE);
    }

    private static ProvisionedKey randomKey() {
        ProvisionedKey key = new ProvisionedKey(randBytes(), randString(), randBytes(), randBytes(),
                Instant.now());
        key.clientUid = sRandom.nextInt();
        key.keyId = sRandom.nextInt();
        return key;
    }

    private static <T> T clone(T original) {
        if (original == null) {
            return null;
        } else if (original instanceof byte[]) {
            return (T) ((byte[]) original).clone();
        } else if (original instanceof String) {
            return (T) new String((String) original);
        } else if (original instanceof Instant) {
            Instant originalInstant = (Instant) original;
            return (T) Instant.ofEpochSecond(
                    originalInstant.getEpochSecond(), originalInstant.getNano());
        } else if (original instanceof Integer) {
            return (T) Integer.valueOf(((Integer) original).intValue());
        } else if (original instanceof ProvisionedKey) {
            ProvisionedKey origKey = (ProvisionedKey) original;
            ProvisionedKey cloneKey = new ProvisionedKey(clone(origKey.keyBlob),
                    clone(origKey.irpcHal), clone(origKey.publicKey),
                    clone(origKey.certificateChain), clone(origKey.expirationTime));
            cloneKey.clientUid = clone(origKey.clientUid);
            cloneKey.keyId = clone(origKey.keyId);
            return (T) cloneKey;
        }
        throw new IllegalArgumentException("I don't know how to clone "
                + original.getClass().getName());
    }

    @Test
    public void testEquality() throws Exception {
        final ProvisionedKey expected = randomKey();
        final ProvisionedKey actual = randomKey();

        for (Field f: expected.getClass().getFields()) {
            assertThat(actual).isNotEqualTo(expected);
            assertThat(actual.hashCode()).isNotEqualTo(expected.hashCode());
            f.set(actual, clone(f.get(expected)));
        }
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.hashCode()).isEqualTo(expected.hashCode());
    }

    @Test
    public void testEqualityWithNullFields() throws Exception {
        for (Field f : ProvisionedKey.class.getFields()) {
            ProvisionedKey expected = randomKey();
            ProvisionedKey actual = clone(expected);
            assertThat(actual).isEqualTo(expected);
            assertThat(actual.hashCode()).isEqualTo(expected.hashCode());

            f.set(actual, null);
            assertThat(actual).isNotEqualTo(expected);
            assertThat(actual.hashCode()).isNotEqualTo(expected.hashCode());

            f.set(expected, null);
            assertThat(actual).isEqualTo(expected);
            assertThat(actual.hashCode()).isEqualTo(expected.hashCode());
        }
    }

    @Test
    public void testEqualityWithDifferingTimestampAccuracy() {
        ProvisionedKey expected = randomKey();
        ProvisionedKey actual = clone(expected);

        Instant millisecondFloor = expected.expirationTime.truncatedTo(ChronoUnit.MILLIS);

        actual.expirationTime = millisecondFloor;
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.hashCode()).isEqualTo(expected.hashCode());

        actual.expirationTime = millisecondFloor.plusNanos(999999);
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.hashCode()).isEqualTo(expected.hashCode());

        actual.expirationTime = millisecondFloor.plusMillis(1);
        assertThat(actual).isNotEqualTo(expected);
        assertThat(actual.hashCode()).isNotEqualTo(expected.hashCode());

        actual.expirationTime = millisecondFloor.minusNanos(1);
        assertThat(actual).isNotEqualTo(expected);
        assertThat(actual.hashCode()).isNotEqualTo(expected.hashCode());
    }
}
