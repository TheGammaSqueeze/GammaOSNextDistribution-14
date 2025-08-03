/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* @test
 * @bug 8195649
 * @summary Basic functional test of OptionalLong
 * @author Mike Duigou
 * @build ObscureException
 * @run testng BasicLong
 */
package test.java.util.Optional;

// Android-added: support for wrapper to avoid d8 backporting of Optional methods (b/191859202).
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class BasicLong {
    static final long LONGVAL = 2_305_843_008_139_952_128L;
    static final long UNEXPECTED = 0xFEEDBEEFCAFEBABEL;

    /**
     * Checks a block of assertions over an empty OptionalLong.
     */
    void checkEmpty(OptionalLong empty) {
        assertTrue(empty.equals(OptionalLong.empty()));
        assertTrue(OptionalLong.empty().equals(empty));
        assertFalse(empty.equals(OptionalLong.of(UNEXPECTED)));
        assertFalse(OptionalLong.of(UNEXPECTED).equals(empty));
        assertFalse(empty.equals("unexpected"));

        assertFalse(empty.isPresent());
        // Android-changed: Avoid backporting of isEmpty()) (b/191859202).
        // assertTrue(empty.isEmpty());
        assertTrue(OptionalLong_isEmpty(empty));
        assertEquals(empty.hashCode(), 0);
        assertEquals(empty.orElse(UNEXPECTED), UNEXPECTED);
        assertEquals(empty.orElseGet(() -> UNEXPECTED), UNEXPECTED);

        assertThrows(NoSuchElementException.class, () -> empty.getAsLong());
        // Android-changed: Avoid backporting of orElseThrow()) (b/191859202).
        // assertThrows(NoSuchElementException.class, () -> empty.orElseThrow());
        assertThrows(NoSuchElementException.class, () -> OptionalLong_orElseThrow(empty));
        assertThrows(ObscureException.class,       () -> empty.orElseThrow(ObscureException::new));

        var b = new AtomicBoolean();
        empty.ifPresent(s -> b.set(true));
        assertFalse(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        // Android-changed: Avoid backporting of ifPresentOrElse() (b/191859202).
        // empty.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        OptionalLong_ifPresentOrElse(empty, s -> b1.set(true), () -> b2.set(true));
        assertFalse(b1.get());
        assertTrue(b2.get());

        assertEquals(empty.toString(), "OptionalLong.empty");
    }

    /**
     * Checks a block of assertions over an OptionalLong that is expected to
     * have a particular value present.
     */
    void checkPresent(OptionalLong opt, long expected) {
        assertFalse(opt.equals(OptionalLong.empty()));
        assertFalse(OptionalLong.empty().equals(opt));
        assertTrue(opt.equals(OptionalLong.of(expected)));
        assertTrue(OptionalLong.of(expected).equals(opt));
        assertFalse(opt.equals(OptionalLong.of(UNEXPECTED)));
        assertFalse(OptionalLong.of(UNEXPECTED).equals(opt));
        assertFalse(opt.equals("unexpected"));

        assertTrue(opt.isPresent());
        // Android-changed: Avoid backporting of isEmpty()) (b/191859202).
        // assertFalse(opt.isEmpty());
        assertFalse(OptionalLong_isEmpty(opt));
        assertEquals(opt.hashCode(), Long.hashCode(expected));
        assertEquals(opt.orElse(UNEXPECTED), expected);
        assertEquals(opt.orElseGet(() -> UNEXPECTED), expected);

        assertEquals(opt.getAsLong(), expected);
        // Android-changed: Avoid backporting of orElseThrow()) (b/191859202).
        // assertEquals(opt.orElseThrow(), expected);
        assertEquals(OptionalLong_orElseThrow(opt), expected);
        assertEquals(opt.orElseThrow(ObscureException::new), expected);

        var b = new AtomicBoolean(false);
        opt.ifPresent(s -> b.set(true));
        assertTrue(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        // Android-changed: Avoid backporting of ifPresentOrElse() (b/191859202).
        // opt.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        OptionalLong_ifPresentOrElse(opt, s -> b1.set(true), () -> b2.set(true));
        assertTrue(b1.get());
        assertFalse(b2.get());

        assertEquals(opt.toString(), "OptionalLong[" + expected + "]");
    }

    @Test(groups = "unit")
    public void testEmpty() {
        checkEmpty(OptionalLong.empty());
    }

    @Test(groups = "unit")
    public void testPresent() {
        checkPresent(OptionalLong.of(LONGVAL), LONGVAL);
    }

    @Test
    public void testStreamEmpty() {
        // Android-changed: Avoid backporting of stream() (b/191859202).
        // assertEquals(OptionalLong.empty().stream().toArray(), new long[] { });
        assertEquals(OptionalLong_stream(OptionalLong.empty()).toArray(), new long[] { });
    }

    @Test
    public void testStreamPresent() {
        // Android-changed: Avoid backporting of stream() (b/191859202).
        // assertEquals(OptionalLong.of(LONGVAL).stream().toArray(), new long[] { LONGVAL });
        assertEquals(OptionalLong_stream(OptionalLong.of(LONGVAL)).toArray(),
                     new long[] { LONGVAL });
    }

    // Android-added: wrapper for d8 backport of OptionalLong.ifPresentOrElse() (b/191859202).
    private static void OptionalLong_ifPresentOrElse(
            OptionalLong receiver, LongConsumer action, Runnable emptyAction) {
        try {
            MethodType type =
                    MethodType.methodType(void.class, LongConsumer.class, Runnable.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalLong.class, "ifPresentOrElse", type);
            mh.invokeExact(receiver, action, emptyAction);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalLong.isEmpty() (b/191859202).
    private static boolean OptionalLong_isEmpty(OptionalLong receiver) {
        try {
            MethodType type = MethodType.methodType(boolean.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalLong.class, "isEmpty", type);
            return (boolean) mh.invokeExact(receiver);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalLong.orElseThrow() (b/191859202).
    private static long OptionalLong_orElseThrow(OptionalLong receiver) {
        try {
            MethodType type = MethodType.methodType(long.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalLong.class, "orElseThrow", type);
            return (long) mh.invokeExact(receiver);
        } catch (NoSuchElementException expected) {
            throw expected;  // Underlying method may throw NoSuchElementException
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalLong.stream() (b/191859202).
    private static LongStream OptionalLong_stream(OptionalLong receiver) {
        try {
            MethodType type = MethodType.methodType(LongStream.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalLong.class, "stream", type);
            return (LongStream) mh.invokeExact(receiver);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
