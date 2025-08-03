/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import junit.framework.TestCase;

import org.junit.Assert;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class OptionalLongTest extends TestCase {
    public void testEmpty_sameInstance() {
        assertSame(OptionalLong.empty(), OptionalLong.empty());
    }

    public void testGet() {
        assertEquals(56, OptionalLong.of(56).getAsLong());

        try {
            OptionalLong.empty().getAsLong();
            fail();
        } catch (NoSuchElementException nsee) {
        }
    }

    public void testIsPresent() {
        assertTrue(OptionalLong.of(56).isPresent());
        assertFalse(OptionalLong.empty().isPresent());
    }

    public void testIfPresent() {
        LongConsumer alwaysFails = value -> fail();
        OptionalLong.empty().ifPresent(alwaysFails);

        final AtomicLong reference = new AtomicLong();
        LongConsumer recorder = (long value) -> reference.set(value);
        OptionalLong.of(56).ifPresent(recorder);
        assertEquals(56, reference.get());
    }

    public void testOrElse() {
        assertEquals(57, OptionalLong.empty().orElse(57));
        assertEquals(56, OptionalLong.of(56).orElse(57));
    }

    public void testOrElseGet() {
        LongSupplier alwaysFails = () -> { fail(); return 57; };
        assertEquals(56, OptionalLong.of(56).orElseGet(alwaysFails));

        LongSupplier supplies57 = () -> 57;
        assertEquals(57, OptionalLong.empty().orElseGet(supplies57));
    }

    public void testOrElseThrow() throws IOException {
        final IOException bar = new IOException("bar");

        Supplier<IOException> barSupplier = () -> bar;
        assertEquals(57, OptionalLong.of(57).orElseThrow(barSupplier));

        try {
            OptionalLong.empty().orElseThrow(barSupplier);
            fail();
        } catch (IOException expected) {
            assertSame(bar, expected);
        }
    }

    public void testEquals() {
        assertEquals(OptionalLong.empty(), OptionalLong.empty());
        assertEquals(OptionalLong.of(56), OptionalLong.of(56));
        assertFalse(OptionalLong.empty().equals(OptionalLong.of(56)));
        assertFalse(OptionalLong.of(57).equals(OptionalLong.of(56)));
    }

    public void test_isPresentOrElse_whenEmpty() {
        var heldValueHolder = new AtomicLong(-1);
        var whenEmptyHolder = new AtomicLong(-1);

        OptionalLong.empty().ifPresentOrElse(heldValueHolder::set, () -> whenEmptyHolder.set(42));

        assertEquals(42, whenEmptyHolder.get());
        assertEquals(-1, heldValueHolder.get());
    }

    public void test_isPresentOrElse_whenNonEmpty() {
        var heldValueHolder = new AtomicLong(-1);
        var whenEmptyHolder = new AtomicLong(-1);

        OptionalLong.of(1001L).ifPresentOrElse(heldValueHolder::set, () -> whenEmptyHolder.set(42));

        assertEquals(-1, whenEmptyHolder.get());
        assertEquals(1001L, heldValueHolder.get());
    }

    public void test_orElseThrow_nonEmpty() {
        assertEquals(43, OptionalLong.of(43).orElseThrow());
    }

    public void test_orElseThrow_empty() {
        assertThrows(NoSuchElementException.class, () -> OptionalLong.empty().orElseThrow());
    }

    public void test_stream_nonEmpty() {
        var elements = OptionalLong.of(42).stream().boxed().toList();

        assertEquals(List.of(42L), elements);
    }

    public void test_stream_empty() {
        var elements = OptionalLong.empty().stream().boxed().toList();

        assertEquals(List.of(), elements);
    }

    public void testHashCode() {
        assertEquals(Long.hashCode(57), OptionalLong.of(57).hashCode());
    }
}
