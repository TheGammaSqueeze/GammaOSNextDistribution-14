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

package com.android.telephony.sats2range;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.telephony.sats2range.write.PushBackIterator;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/** Tests for {@link PushBackIterator}. */
public class PushBackIteratorTest {

    @Test
    public void test() {
        List<String> values = listOf("One", "Two", "Three", "Four");
        PushBackIterator<String> iterator = new PushBackIterator<>(values.iterator());
        assertTrue(iterator.hasNext());

        // iterator = One, Two, Three, Four
        iterator.pushBack("Zero");
        assertTrue(iterator.hasNext());
        assertEquals("Zero", iterator.next());

        // iterator = One, Two, Three, Four
        assertTrue(iterator.hasNext());
        assertEquals("One", iterator.next());

        // iterator = Two, Three, Four
        iterator.pushBack("One");
        iterator.pushBack("Zero");
        assertTrue(iterator.hasNext());
        assertEquals("Zero", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("One", iterator.next());

        // iterator = Two, Three, Four
        assertTrue(iterator.hasNext());
        assertEquals("Two", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("Three", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("Four", iterator.next());
        assertFalse(iterator.hasNext());

        assertThrows(NoSuchElementException.class, iterator::next);

        // iterator = Empty
        iterator.pushBack("Four");
        assertTrue(iterator.hasNext());
        assertEquals("Four", iterator.next());
    }

    @Test
    public void removeNotSupported() {
        List<String> values = listOf("One", "Two", "Three", "Four");
        PushBackIterator<String> iterator = new PushBackIterator<>(values.iterator());
        assertEquals("One", iterator.next());

        assertThrows(UnsupportedOperationException.class, iterator::remove);

        iterator.pushBack("One");
        iterator.pushBack("Zero");

        assertThrows(UnsupportedOperationException.class, iterator::remove);

        assertEquals("Zero", iterator.next());
        assertThrows(UnsupportedOperationException.class, iterator::remove);
    }

    /** Returns a list from a varargs param. */
    @SafeVarargs
    private static <E> List<E> listOf(E... values) {
        return Arrays.asList(values);
    }
}
