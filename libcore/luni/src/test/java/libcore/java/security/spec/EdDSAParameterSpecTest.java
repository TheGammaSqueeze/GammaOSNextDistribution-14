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

package libcore.java.security.spec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.InvalidParameterException;
import java.security.spec.EdDSAParameterSpec;
import java.util.Optional;

@RunWith(JUnit4.class)
public class EdDSAParameterSpecTest {

    @Test
    public void testIsPrehash() {
        EdDSAParameterSpec spec = new EdDSAParameterSpec(true);
        assertTrue(spec.isPrehash());
        assertEquals(Optional.empty(), spec.getContext());
    }

    @Test
    public void testGetContext() {
        byte[] context = new byte[] { 1, 2, 3 };
        EdDSAParameterSpec spec = new EdDSAParameterSpec(false, context);
        assertFalse(spec.isPrehash());
        assertArrayEquals(context, spec.getContext().get());
    }

    @Test
    public void testEdDSAParameterSpec_nullContext() {
        assertThrows(NullPointerException.class, () -> new EdDSAParameterSpec(false, null));
    }

    @Test
    public void testEdDSAParameterSpec_contextTooLarge() {
        byte[] context = new byte[256];
        assertThrows(InvalidParameterException.class, () -> new EdDSAParameterSpec(false, context));
    }
}
