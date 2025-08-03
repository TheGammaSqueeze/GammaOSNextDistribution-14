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

package android.util.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.util.Pair;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PairTest {
    @Test
    public void testBasic() {
        Pair pairA = Pair.create(1, 2);
        assertEquals(1, pairA.first);
        assertEquals(2, pairA.second);

        Pair pairB = new Pair(1, 2);
        assertEquals(1, pairB.first);
        assertEquals(2, pairB.second);
    }

    @Test
    public void testEqualsHashCode() {
        Pair pairA = Pair.create(1, 2);
        Pair pairB = Pair.create(1, 2);
        assertEquals(pairA.hashCode(), pairB.hashCode());
        assertEquals(pairA, pairB);

        Pair pairC = Pair.create(42, 42);
        assertNotEquals(pairA.hashCode(), pairC.hashCode());
        assertNotEquals(pairA, pairC);
    }

    @Test
    public void testNull() {
        Pair pairA = Pair.create(1, null);
        Pair pairB = Pair.create(1, null);
        assertEquals(pairA.hashCode(), pairB.hashCode());
        assertEquals(pairA, pairB);

        Pair pairC = Pair.create(42, null);
        assertNotEquals(pairA.hashCode(), pairC.hashCode());
        assertNotEquals(pairA, pairC);
    }
}
