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

package libcore.java.util.regex;

import static org.junit.Assert.assertThrows;

import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PatternTest {

    @Test
    public void testCANON_EQ() {
        assertThrows(IllegalArgumentException.class, () -> Pattern.compile("a", Pattern.CANON_EQ));
    }

    @Test
    public void testUNICODE_CHARACTER_CLASS() {
        assertThrows(IllegalArgumentException.class,
                () -> Pattern.compile("a", Pattern.UNICODE_CHARACTER_CLASS));
    }
}
