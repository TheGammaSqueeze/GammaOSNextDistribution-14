/*
 * Copyright (C) 2024 The Android Open Source Project
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

package libcore.android.crypto.hpke;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import android.crypto.hpke.XdhKeySpec;

import java.util.Random;

@RunWith(JUnit4.class)
public class XdhKeySpecTest {
    byte[] keyBytes = new byte[32];

    @Before
    public void setup() {
        Random random = new Random();
        random.nextBytes(keyBytes);
    }

    @Test
    public void create() {
        XdhKeySpec spec = new XdhKeySpec(keyBytes);
        assertEquals("raw", spec.getFormat().toLowerCase());
        assertArrayEquals(keyBytes, spec.getEncoded());
        assertArrayEquals(keyBytes, spec.getKey());
    }

    @Test
    public void equality() {
        XdhKeySpec spec1 = new XdhKeySpec(keyBytes);
        XdhKeySpec spec2 = new XdhKeySpec(keyBytes);
        assertEquals(spec1, spec2);
        assertNotSame(spec1, spec2);
    }
}
