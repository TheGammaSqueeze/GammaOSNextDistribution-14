/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import java.util.Scanner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ScannerTest {
    @Test
    public void testReset() {
        Scanner scanner = new Scanner("123 45");
        Locale locale = Locale.forLanguageTag("zh");
        scanner.useDelimiter("123");
        scanner.useLocale(locale);
        scanner.useRadix(8);
        assertEquals("123", scanner.delimiter().pattern());
        assertEquals(locale, scanner.locale());
        assertEquals(8, scanner.radix());

        scanner.reset();
        assertEquals("\\p{javaWhitespace}+", scanner.delimiter().pattern());
        assertEquals(Locale.getDefault(Locale.Category.FORMAT), scanner.locale());
        assertEquals(10, scanner.radix());
    }
}
