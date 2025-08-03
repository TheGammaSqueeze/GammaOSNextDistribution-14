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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

@RunWith(JUnit4.class)
public final class MapCopyOfTest {

    @Test
    public void shouldThrowNPE_whenArgumentIsNull() {
        assertThrows(NullPointerException.class, () -> Map.Entry.copyOf(null));
    }

    @Test
    public void createsCopy_whenNonNull() {
        var key = "this is key";
        var value = "this is value";
        var map = Map.of(key, value);
        var entry = map.entrySet().iterator().next();

        var copy = Map.Entry.copyOf(entry);

        assertSame(copy.getKey(), key);
        assertSame(copy.getValue(), value);
    }

}
