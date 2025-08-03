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
 * @summary Basic functional test of OptionalInt
 * @author Mike Duigou
 * @build ObscureException
 * @run testng BasicInt
 */
package test.java.util.Optional;

// Android-added: support for wrapper to avoid d8 backporting of Optional methods (b/191859202).
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class BasicInt {

    static final int INTVAL = 33_550_336;
    static final int UNEXPECTED = 0xCAFEBABE;

    /**
     * Checks a block of assertions over an empty OptionalInt.
     */
    void checkEmpty(OptionalInt empty) {
        assertTrue(empty.equals(OptionalInt.empty()));
        assertTrue(OptionalInt.empty().equals(empty));
        assertFalse(empty.equals(OptionalInt.of(UNEXPECTED)));
        assertFalse(OptionalInt.of(UNEXPECTED).equals(empty));
        assertFalse(empty.equals("unexpected"));

        assertFalse(empty.isPresent());
        // Android-changed: Avoid backporting of isEmpty() (b/191859202).
        // assertTrue(empty.isEmpty());
        assertTrue(OptionalInt_isEmpty(empty));
        assertEquals(empty.hashCode(), 0);
        assertEquals(empty.orElse(UNEXPECTED), UNEXPECTED);
        assertEquals(empty.orElseGet(() -> UNEXPECTED), UNEXPECTED);

        assertThrows(NoSuchElementException.class, () -> empty.getAsInt());
        // Android-changed: Avoid backporting of orElseThrow() (b/191859202).
        // assertThrows(NoSuchElementException.class, () -> empty.orElseThrow());
        assertThrows(NoSuchElementException.class, () -> OptionalInt_orElseThrow(empty));
        assertThrows(ObscureException.class,       () -> empty.orElseThrow(ObscureException::new));

        var b = new AtomicBoolean();
        empty.ifPresent(s -> b.set(true));
        assertFalse(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        // Android-changed: Avoid backporting of ifPresentOrElse() (b/191859202).
        // empty.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        OptionalInt_ifPresentOrElse(empty, s -> b1.set(true), () -> b2.set(true));
        assertFalse(b1.get());
        assertTrue(b2.get());

        assertEquals(empty.toString(), "OptionalInt.empty");
    }

    /**
     * Checks a block of assertions over an OptionalInt that is expected to
     * have a particular value present.
     */
    void checkPresent(OptionalInt opt, int expected) {
        assertFalse(opt.equals(OptionalInt.empty()));
        assertFalse(OptionalInt.empty().equals(opt));
        assertTrue(opt.equals(OptionalInt.of(expected)));
        assertTrue(OptionalInt.of(expected).equals(opt));
        assertFalse(opt.equals(OptionalInt.of(UNEXPECTED)));
        assertFalse(OptionalInt.of(UNEXPECTED).equals(opt));
        assertFalse(opt.equals("unexpected"));

        assertTrue(opt.isPresent());
        // Android-changed: Avoid backporting of isEmpty() (b/191859202).
        // assertFalse(opt.isEmpty());
        assertFalse(OptionalInt_isEmpty(opt));
        assertEquals(opt.hashCode(), Integer.hashCode(expected));
        assertEquals(opt.orElse(UNEXPECTED), expected);
        assertEquals(opt.orElseGet(() -> UNEXPECTED), expected);

        assertEquals(opt.getAsInt(), expected);
        // Android-changed: Avoid backporting of orElseThrow() (b/191859202).
        assertEquals(OptionalInt_orElseThrow(opt), expected);
        assertEquals(opt.orElseThrow(ObscureException::new), expected);

        var b = new AtomicBoolean(false);
        opt.ifPresent(s -> b.set(true));
        assertTrue(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        // Android-changed: Avoid backporting of ifPresentOrElse() (b/191859202).
        // opt.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        OptionalInt_ifPresentOrElse(opt, s -> b1.set(true), () -> b2.set(true));
        assertTrue(b1.get());
        assertFalse(b2.get());

        assertEquals(opt.toString(), "OptionalInt[" + expected + "]");
    }

    @Test(groups = "unit")
    public void testEmpty() {
        checkEmpty(OptionalInt.empty());
    }

    @Test(groups = "unit")
    public void testPresent() {
        checkPresent(OptionalInt.of(INTVAL), INTVAL);
    }

    @Test
    public void testStreamEmpty() {
        // Android-changed: Avoid backporting of stream() (b/191859202).
        // assertEquals(OptionalInt.empty().stream().toArray(), new int[] { });
        assertEquals(OptionalInt_stream(OptionalInt.empty()).toArray(), new int[] { });
    }

    @Test
    public void testStreamPresent() {
        // Android-changed: Avoid backporting of stream() (b/191859202).
        // assertEquals(OptionalInt.of(INTVAL).stream().toArray(), new int[] { INTVAL });
        assertEquals(OptionalInt_stream(OptionalInt.of(INTVAL)).toArray(), new int[] { INTVAL });
    }

        // Android-added: wrapper for d8 backport of OptionalInt.ifPresentOrElse() (b/191859202).
    private static void OptionalInt_ifPresentOrElse(
            OptionalInt receiver, IntConsumer action, Runnable emptyAction) {
        try {
            MethodType type = MethodType.methodType(void.class, IntConsumer.class, Runnable.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalInt.class, "ifPresentOrElse", type);
            mh.invokeExact(receiver, action, emptyAction);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalInt.isEmpty() (b/191859202).
    private static boolean OptionalInt_isEmpty(OptionalInt receiver) {
        try {
            MethodType type = MethodType.methodType(boolean.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalInt.class, "isEmpty", type);
            return (boolean) mh.invokeExact(receiver);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalInt.orElseThrow() (b/191859202).
    private static int OptionalInt_orElseThrow(OptionalInt receiver) {
        try {
            MethodType type = MethodType.methodType(int.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalInt.class, "orElseThrow", type);
            return (int) mh.invokeExact(receiver);
        } catch (NoSuchElementException expected) {
            throw expected;  // Underlying method may throw NoSuchElementException
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalInt.stream() (b/191859202).
    private static IntStream OptionalInt_stream(OptionalInt receiver) {
        try {
            MethodType type = MethodType.methodType(IntStream.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalInt.class, "stream", type);
            return (IntStream) mh.invokeExact(receiver);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
