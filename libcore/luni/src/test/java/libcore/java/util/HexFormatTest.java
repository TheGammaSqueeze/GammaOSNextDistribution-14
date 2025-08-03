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
package libcore.java.util;

import java.util.HexFormat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Before;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class HexFormatTest {

    @Test
    public void toHexDigits_short() {
        HexFormat hex = HexFormat.of();

        final short shortValue = 0x4321;
        final String expectedHexDigits = "4321";
        String actualHexDigits = hex.toHexDigits(shortValue);
        assertEquals(actualHexDigits, expectedHexDigits);

        int actualInt = HexFormat.fromHexDigits(actualHexDigits);
        assertTrue(actualInt >= Short.MIN_VALUE && actualInt <= Short.MAX_VALUE);
        short actualShort = (short) actualInt;
        assertEquals(actualShort, shortValue);
    }

}
