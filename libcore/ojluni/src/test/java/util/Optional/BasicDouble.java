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
 * @summary Basic functional test of OptionalDouble
 * @author Mike Duigou
 * @build ObscureException
 * @run testng BasicDouble
 */
package test.java.util.Optional;

// Android-added: support for wrapper to avoid d8 backporting of Optional methods (b/191859202).
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.DoubleConsumer;
import java.util.stream.DoubleStream;

import java.util.NoSuchElementException;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class BasicDouble {
    static final double DOUBLEVAL = Math.PI;
    static final double UNEXPECTED = 6.62607004E-34;

    /**
     * Checks a block of assertions over an empty OptionalDouble.
     */
    void checkEmpty(OptionalDouble empty) {
        assertTrue(empty.equals(OptionalDouble.empty()));
        assertTrue(OptionalDouble.empty().equals(empty));
        assertFalse(empty.equals(OptionalDouble.of(UNEXPECTED)));
        assertFalse(OptionalDouble.of(UNEXPECTED).equals(empty));
        assertFalse(empty.equals("unexpected"));

        assertFalse(empty.isPresent());
        // Android-changed: Avoid backporting of isEmpty() (b/191859202).
        // assertTrue(empty.isEmpty());
        assertTrue(OptionalDouble_isEmpty(empty));
        assertEquals(empty.hashCode(), 0);
        assertEquals(empty.orElse(UNEXPECTED), UNEXPECTED);
        assertEquals(empty.orElseGet(() -> UNEXPECTED), UNEXPECTED);

        assertThrows(NoSuchElementException.class, () -> empty.getAsDouble());
        // Android-changed: Avoid backporting of orElseThrow() (b/191859202).
        // assertThrows(NoSuchElementException.class, () -> empty.orElseThrow());
        assertThrows(NoSuchElementException.class, () -> OptionalDouble_orElseThrow(empty));
        assertThrows(ObscureException.class,       () -> empty.orElseThrow(ObscureException::new));

        var b = new AtomicBoolean();
        empty.ifPresent(s -> b.set(true));
        assertFalse(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        // Android-changed: Avoid backporting of ifPresentOrElse() (b/191859202).
        // empty.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        OptionalDouble_ifPresentOrElse(empty, s -> b1.set(true), () -> b2.set(true));
        assertFalse(b1.get());
        assertTrue(b2.get());

        assertEquals(empty.toString(), "OptionalDouble.empty");
    }

    /**
     * Checks a block of assertions over an OptionalDouble that is expected to
     * have a particular value present.
     */
    void checkPresent(OptionalDouble opt, double expected) {
        assertFalse(opt.equals(OptionalDouble.empty()));
        assertFalse(OptionalDouble.empty().equals(opt));
        assertTrue(opt.equals(OptionalDouble.of(expected)));
        assertTrue(OptionalDouble.of(expected).equals(opt));
        assertFalse(opt.equals(OptionalDouble.of(UNEXPECTED)));
        assertFalse(OptionalDouble.of(UNEXPECTED).equals(opt));
        assertFalse(opt.equals("unexpected"));

        assertTrue(opt.isPresent());
        // Android-changed: Avoid backporting of isEmpty() (b/191859202).
        // assertFalse(opt.isEmpty());
        assertFalse(OptionalDouble_isEmpty(opt));
        assertEquals(opt.hashCode(), Double.hashCode(expected));
        assertEquals(opt.orElse(UNEXPECTED), expected);
        assertEquals(opt.orElseGet(() -> UNEXPECTED), expected);

        assertEquals(opt.getAsDouble(), expected);
        // Android-changed: Avoid backporting of orElseThrow() (b/191859202).
        // assertEquals(opt.orElseThrow(), expected);
        assertEquals(OptionalDouble_orElseThrow(opt), expected);
        assertEquals(opt.orElseThrow(ObscureException::new), expected);

        var b = new AtomicBoolean(false);
        opt.ifPresent(s -> b.set(true));
        assertTrue(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        // Android-changed: Avoid backporting of ifPresentOrElse() (b/191859202).
        // opt.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        OptionalDouble_ifPresentOrElse(opt, s -> b1.set(true), () -> b2.set(true));
        assertTrue(b1.get());
        assertFalse(b2.get());

        assertEquals(opt.toString(), "OptionalDouble[" + expected + "]");
    }

    @Test(groups = "unit")
    public void testEmpty() {
        checkEmpty(OptionalDouble.empty());
    }

    @Test(groups = "unit")
    public void testPresent() {
        checkPresent(OptionalDouble.of(DOUBLEVAL), DOUBLEVAL);
    }

    @Test
    public void testStreamEmpty() {
        // Android-changed: Avoid backporting of stream() (b/191859202).
        // assertEquals(OptionalDouble.empty().stream().toArray(), new double[] { });
        assertEquals(OptionalDouble_stream(OptionalDouble.empty()).toArray(), new double[] { });
    }

    @Test
    public void testStreamPresent() {
        // Android-changed: Avoid backporting of stream() (b/191859202).
        // assertEquals(OptionalDouble.of(DOUBLEVAL).stream().toArray(), new double[] { DOUBLEVAL });
        assertEquals(OptionalDouble_stream(OptionalDouble.of(DOUBLEVAL)).toArray(),
                     new double[] { DOUBLEVAL });
    }

    // Android-added: wrapper for d8 backport of OptionalDouble.ifPresentOrElse() (b/191859202).
    private static void OptionalDouble_ifPresentOrElse(
            OptionalDouble receiver, DoubleConsumer action, Runnable emptyAction) {
        try {
            MethodType type =
                    MethodType.methodType(void.class, DoubleConsumer.class, Runnable.class);
            MethodHandle mh = MethodHandles.lookup().findVirtual(OptionalDouble.class,
                                                                 "ifPresentOrElse",
                                                                 type);
            mh.invokeExact(receiver, action, emptyAction);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalDouble.isEmpty() (b/191859202).
    private static boolean OptionalDouble_isEmpty(OptionalDouble receiver) {
        try {
            MethodType type = MethodType.methodType(boolean.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalDouble.class, "isEmpty", type);
            return (boolean) mh.invokeExact(receiver);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalDouble.orElseThrow() (b/191859202).
    private static double OptionalDouble_orElseThrow(OptionalDouble receiver) {
        try {
            MethodType type = MethodType.methodType(double.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalDouble.class, "orElseThrow", type);
            return (double) mh.invokeExact(receiver);
        } catch (NoSuchElementException expected) {
            throw expected;  // Underlying method may throw NoSuchElementException
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backport of OptionalDouble.stream() (b/191859202).
    private static DoubleStream OptionalDouble_stream(OptionalDouble receiver) {
        try {
            MethodType type = MethodType.methodType(DoubleStream.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(OptionalDouble.class, "stream", type);
            return (DoubleStream) mh.invokeExact(receiver);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
