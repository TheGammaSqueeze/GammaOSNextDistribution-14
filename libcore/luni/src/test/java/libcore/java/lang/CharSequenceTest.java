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

package libcore.java.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CharSequenceTest {
    @Test
    public void testCompare() {
        assertEquals(0, CharSequence.compare("", ""));
        assertEquals(0, CharSequence.compare("a", "a"));
        assertEquals(0, CharSequence.compare("abc", "abc"));
        assertTrue(0 > CharSequence.compare("", "a"));
        assertTrue(0 > CharSequence.compare("", "\u0161"));
        assertTrue(0 > CharSequence.compare("a", "\u0161"));
        assertEquals(0, CharSequence.compare("\u0161", "\u0161"));
        assertEquals(0, CharSequence.compare("\u0161abc\0\u0162\u0163", "\u0161abc\0\u0162\u0163"));
        assertEquals(0, CharSequence.compare("\u0161abc\0\u0162\u0163",
                new StringBuilder("\u0161abc\0\u0162\u0163")));
        assertEquals(0, CharSequence.compare("\u0161abc\0\u0162\u0163",
                new StringBuffer("\u0161abc\0\u0162\u0163")));
    }

}
