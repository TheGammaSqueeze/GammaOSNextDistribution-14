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

package tests.javax.crypto.spec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import javax.crypto.spec.ChaCha20ParameterSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ChaCha20ParameterSpec}.
 */
@RunWith(JUnit4.class)
public class ChaCha20ParameterSpecTest {

    @Test
    public void constructor_withValidValues() {
        byte[] nonce = new byte[] {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0a, 0x0b
        };
        int counter = 1234567890;
        ChaCha20ParameterSpec spec = new ChaCha20ParameterSpec(nonce, counter);

        assertEquals(counter, spec.getCounter());
        assertArrayEquals(nonce, spec.getNonce());
    }

    @Test
    public void constructor_withNullNonce() {
        assertThrows(NullPointerException.class, () -> new ChaCha20ParameterSpec(null, 1234567890));
    }

    @Test
    public void constructor_withNonceTooShort() {
        byte[] nonce = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 };
        assertThrows(IllegalArgumentException.class, () -> new ChaCha20ParameterSpec(nonce,
                1234567890));
    }
}