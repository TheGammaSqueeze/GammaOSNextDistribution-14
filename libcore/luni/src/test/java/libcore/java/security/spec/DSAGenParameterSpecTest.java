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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.spec.DSAGenParameterSpec;

@RunWith(JUnit4.class)
public class DSAGenParameterSpecTest {

    @Test
    public void testGetters() {
        // 1024-bit prime P, 160-bit sub-prime Q, 160-bit seed
        DSAGenParameterSpec dsaParams1 = new DSAGenParameterSpec(1024, 160);
        assertEquals(1024, dsaParams1.getPrimePLength());
        assertEquals(160, dsaParams1.getSubprimeQLength());
        assertEquals(160, dsaParams1.getSeedLength());

        // 2048-bit prime P, 224-bit sub-prime Q, 224-bit seed
        DSAGenParameterSpec dsaParams2 = new DSAGenParameterSpec(2048, 224, 256);
        assertEquals(2048, dsaParams2.getPrimePLength());
        assertEquals(224, dsaParams2.getSubprimeQLength());
        assertEquals(256, dsaParams2.getSeedLength());

        // 2048-bit prime P, 256-bit sub-prime Q, 256-bit seed
        DSAGenParameterSpec dsaParams3 = new DSAGenParameterSpec(2048, 256);
        assertEquals(2048, dsaParams3.getPrimePLength());
        assertEquals(256, dsaParams3.getSubprimeQLength());
        assertEquals(256, dsaParams3.getSeedLength());

        // 3072-bit prime P, 256-bit sub-prime Q, 256-bit seed
        DSAGenParameterSpec dsaParams4 = new DSAGenParameterSpec(3072, 256);
        assertEquals(3072, dsaParams4.getPrimePLength());
        assertEquals(256, dsaParams4.getSubprimeQLength());
        assertEquals(256, dsaParams4.getSeedLength());

        // 1024-bit prime P, 256-bit sub-prime Q, 256-bit seed (not allowed)
        assertThrows(
                IllegalArgumentException.class,
                () -> new DSAGenParameterSpec(1024, 256));

        // 2048-bit prime P, 160-bit sub-prime Q, 224-bit seed (not allowed)
        assertThrows(
                IllegalArgumentException.class,
                () -> new DSAGenParameterSpec(2048, 160, 224));
    }
}
