/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.AbstractMap;

@RunWith(JUnit4.class)
public class AbstractMapTest {


    @Test
    public void testSimpleEntrySetValue() {
        AbstractMap.SimpleEntry<String, Integer> entry = new AbstractMap.SimpleEntry<>("abc", 1);
        assertEquals(Integer.valueOf(1), entry.getValue());
        entry.setValue(-1);
        assertEquals(Integer.valueOf(-1), entry.getValue());

        AbstractMap.SimpleImmutableEntry<String, Integer> immutableEntry =
                new AbstractMap.SimpleImmutableEntry<>("abc", 1);
        assertEquals(Integer.valueOf(1), immutableEntry.getValue());
        Assert.assertThrows(UnsupportedOperationException.class, () -> immutableEntry.setValue(-1));
    }
}
