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
 * @summary Basic functional test of Optional
 * @author Mike Duigou
 * @build ObscureException
 * @run testng Basic
 */
package test.java.util.Optional;

// Android-added: support for wrapper to avoid d8 backporting of Optional methods (b/191859202).
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;
import java.util.function.Supplier;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.stream.Collectors.toList;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class Basic {

    /**
     * Checks a block of assertions over an empty Optional.
     */
    void checkEmpty(Optional<String> empty) {
        assertTrue(empty.equals(Optional.empty()));
        assertTrue(Optional.empty().equals(empty));
        assertFalse(empty.equals(Optional.of("unexpected")));
        assertFalse(Optional.of("unexpected").equals(empty));
        assertFalse(empty.equals("unexpected"));

        assertFalse(empty.isPresent());
        // Android-changed: use Optional_isEmpty() to a void d8 backporting (b/191859202).
        // assertTrue(empty.isEmpty());
        assertTrue(Optional_isEmpty(empty));
        assertEquals(empty.hashCode(), 0);
        assertEquals(empty.orElse("x"), "x");
        assertEquals(empty.orElseGet(() -> "y"), "y");

        assertThrows(NoSuchElementException.class, () -> empty.get());
        // Android-changed: use Optional_orElseThrow() to a void d8 backporting (b/191859202).
        // assertThrows(NoSuchElementException.class, () -> empty.orElseThrow());
        assertThrows(NoSuchElementException.class, () -> Optional_orElseThrow(empty));
        assertThrows(ObscureException.class,       () -> empty.orElseThrow(ObscureException::new));

        var b = new AtomicBoolean();
        empty.ifPresent(s -> b.set(true));
        assertFalse(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        // Android-changed: use Optional_ifPresentOrElse() to a void d8 backporting (b/191859202).
        // empty.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        Optional_ifPresentOrElse(empty, s -> b1.set(true), () -> b2.set(true));
        assertFalse(b1.get());
        assertTrue(b2.get());

        assertEquals(empty.toString(), "Optional.empty");
    }

    /**
     * Checks a block of assertions over an Optional that is expected to
     * have a particular value present.
     */
    void checkPresent(Optional<String> opt, String expected) {
        assertFalse(opt.equals(Optional.empty()));
        assertFalse(Optional.empty().equals(opt));
        assertTrue(opt.equals(Optional.of(expected)));
        assertTrue(Optional.of(expected).equals(opt));
        assertFalse(opt.equals(Optional.of("unexpected")));
        assertFalse(Optional.of("unexpected").equals(opt));
        assertFalse(opt.equals("unexpected"));

        assertTrue(opt.isPresent());
        // Android-changed: use Optional_isEmpty() to a void d8 backporting (b/191859202).
        //assertFalse(opt.isEmpty());
        assertFalse(Optional_isEmpty(opt));
        assertEquals(opt.hashCode(), expected.hashCode());
        assertEquals(opt.orElse("unexpected"), expected);
        assertEquals(opt.orElseGet(() -> "unexpected"), expected);

        assertEquals(opt.get(), expected);
        // Android-changed: use Optional_orElseThrow() to a void d8 backporting (b/191859202).
        // assertEquals(opt.orElseThrow(), expected);
        assertEquals(Optional_orElseThrow(opt), expected);
        assertEquals(opt.orElseThrow(ObscureException::new), expected);

        var b = new AtomicBoolean(false);
        opt.ifPresent(s -> b.set(true));
        assertTrue(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        // Android-changed: use Optional_ifPresentOrElse() to a void d8 backporting (b/191859202).
        // opt.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        Optional_ifPresentOrElse(opt, s -> b1.set(true), () -> b2.set(true));
        assertTrue(b1.get());
        assertFalse(b2.get());

        assertEquals(opt.toString(), "Optional[" + expected + "]");
    }

    @Test(groups = "unit")
    public void testEmpty() {
        checkEmpty(Optional.empty());
    }

    @SuppressWarnings("NullArgumentForNonNullParameter")
    @Test(groups = "unit")
    public void testOfNull() {
        assertThrows(NullPointerException.class, () -> Optional.of(null));
    }

    @Test(groups = "unit")
    public void testOfPresent() {
        checkPresent(Optional.of("xyzzy"), "xyzzy");
    }

    @Test(groups = "unit")
    public void testOfNullableNull() {
        checkEmpty(Optional.ofNullable(null));
    }

    @Test(groups = "unit")
    public void testOfNullablePresent() {
        checkPresent(Optional.ofNullable("xyzzy"), "xyzzy");
    }

    @Test(groups = "unit")
    public void testFilterEmpty() {
        checkEmpty(Optional.<String>empty().filter(s -> { fail(); return true; }));
    }

    @Test(groups = "unit")
    public void testFilterFalse() {
        checkEmpty(Optional.of("xyzzy").filter(s -> s.equals("plugh")));
    }

    @Test(groups = "unit")
    public void testFilterTrue() {
        checkPresent(Optional.of("xyzzy").filter(s -> s.equals("xyzzy")), "xyzzy");
    }

    @Test(groups = "unit")
    public void testMapEmpty() {
        checkEmpty(Optional.empty().map(s -> { fail(); return ""; }));
    }

    @Test(groups = "unit")
    public void testMapPresent() {
        checkPresent(Optional.of("xyzzy").map(s -> s.replace("xyzzy", "plugh")), "plugh");
    }

    @Test(groups = "unit")
    public void testFlatMapEmpty() {
        checkEmpty(Optional.empty().flatMap(s -> { fail(); return Optional.of(""); }));
    }

    @Test(groups = "unit")
    public void testFlatMapPresentReturnEmpty() {
        checkEmpty(Optional.of("xyzzy")
                           .flatMap(s -> { assertEquals(s, "xyzzy"); return Optional.empty(); }));
    }

    @Test
    public void testFlatMapPresentReturnPresent() {
        checkPresent(Optional.of("xyzzy")
                             .flatMap(s -> { assertEquals(s, "xyzzy"); return Optional.of("plugh"); }),
                     "plugh");
    }

    @Test
    public void testOrEmptyEmpty() {
        // Android-changed: use Optional_or() to a void d8 backporting (b/191859202).
        // checkEmpty(Optional.<String>empty().or(() -> Optional.empty()));
        checkEmpty(Optional_or(Optional.<String>empty(), () -> Optional.empty()));
    }

    @Test
    public void testOrEmptyPresent() {
        // Android-changed: use Optional_or() to a void d8 backporting (b/191859202).
        // checkPresent(Optional.<String>empty().or(() -> Optional.of("plugh")), "plugh");
        checkPresent(Optional_or(Optional.<String>empty(), () -> Optional.of("plugh")), "plugh");
    }

    @Test
    public void testOrPresentDontCare() {
        // Android-changed: use Optional_or() to a void d8 backporting (b/191859202).
        // checkPresent(Optional.of("xyzzy").or(() -> { fail(); return Optional.of("plugh"); }), "xyzzy");
        checkPresent(Optional_or(Optional.of("xyzzy"), () -> { fail(); return Optional.of("plugh"); }), "xyzzy");
    }

    @Test
    public void testStreamEmpty() {
        // Android-changed: use Optional_stream() to a void d8 backporting (b/191859202).
        // assertEquals(Optional.empty().stream().collect(toList()), List.of());
        assertEquals(Optional_stream(Optional.empty()).collect(toList()), List.of());
    }

    @Test
    public void testStreamPresent() {
        // Android-changed: use Optional_stream() to a void d8 backporting (b/191859202).
        // assertEquals(Optional.of("xyzzy").stream().collect(toList()), List.of("xyzzy"));
        assertEquals(Optional_stream(Optional.of("xyzzy")).collect(toList()), List.of("xyzzy"));
    }

    // BEGIN Android-added: More tests for coverage http://b/203822442.
    // Also improves coverage for Optional{Int,Long,Double}
    private static final Optional<Integer> P = Optional.<Integer>of(3);
    private static final Optional<Integer> E = Optional.<Integer>empty();

    @Test
    void testIfPresentOrElse_empty() {
        AtomicInteger flag = new AtomicInteger(0);
        // Note use Optional_ifPresentOrElse() to a void d8 backporting (b/191859202).
        // E.ifPresentOrElse(integer -> flag.set(1), () -> flag.set(2));
        Optional_ifPresentOrElse(E, integer -> flag.set(1), () -> flag.set(2));
        assertEquals(flag.get(), 2);
    }

    @Test
    void testIfPresentOrElse_present() {
        AtomicInteger flag = new AtomicInteger(0);
        // Note use Optional_ifPresentOrElse() to a void d8 backporting (b/191859202).
        // P.ifPresentOrElse(integer -> flag.set(1), () -> flag.set(2));
        Optional_ifPresentOrElse(P, integer -> flag.set(1), () -> flag.set(2));
        assertEquals(flag.get(), 1);
    }

    @Test
    void testOr_empty() {
        // Note use Optional_or() to a void d8 backporting (b/191859202).
        Optional<Integer> o = Optional_or(E, () -> Optional.of(5));
        assertEquals((int) o.get(), 5);
    }

    @Test
    void testOr_present() {
        // Note use Optional_or() to a void d8 backporting (b/191859202).
        // Optional<Integer> o = P.or(() -> Optional.of(5));
        Optional<Integer> o = Optional_or(P, () -> Optional.of(5));
        assertEquals((int) o.get(), 3);
    }

    @Test
    public void testStream_empty() {
        // Not use Optional_stream() to a void d8 backporting (b/191859202).
        // Stream<Integer> s = E.stream();
        Stream<Integer> s = Optional_stream(E);
        assertEquals(s.collect(Collectors.toList()), List.of());
    }

    @Test
    public void testStream_present() {
        // Not use Optional_stream() to a void d8 backporting (b/191859202).
        // Stream<Integer> s = P.stream();
        Stream<Integer> s = Optional_stream(P);
        assertEquals(s.collect(Collectors.toList()), List.of(3));
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public void testOrElseThrow_empty() {
        // Note use Optional_orElseThrow() to a void d8 backporting (b/191859202).
        // E.orElseThrow();
        Optional_orElseThrow(E);
    }

    @Test
    public void testOrElseThrow_present() {
        // Note use Optional_orElseThrow() to a void d8 backporting (b/191859202).
        // assertEquals((int) P.orElseThrow(), 3);
        assertEquals((int) Optional_orElseThrow(P), 3);
    }

    @Test
    public void testIsEmpty_empty() {
        // Note use Optional_isEmpty() to a void d8 backporting (b/191859202).
        // assertTrue(E.isEmpty());
        assertTrue(Optional_isEmpty(E));
    }

    @Test
    public void testIsEmpty_present() {
        // Note use Optional_isEmpty() to a void d8 backporting (b/191859202).
        // assertFalse(P.isEmpty());
        assertFalse(Optional_isEmpty(P));
    }
    // END Android-added: More tests for coverage http://b/203822442.

    // Android-added: wrapper to avoid d8 backporting of Optional.ifPresentOrElse() (b/191859202).
    private static <T> void Optional_ifPresentOrElse(
            Optional<T> receiver, Consumer<? super T> action, Runnable emptyAction) {
        try {
            MethodType type = MethodType.methodType(void.class, Consumer.class, Runnable.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(Optional.class, "ifPresentOrElse", type);
            mh.invokeExact(receiver, action, emptyAction);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backporting of Optional.isEmpty() (b/191859202).
    private static <T> boolean Optional_isEmpty(Optional<T> receiver) {
        try {
            MethodType type = MethodType.methodType(boolean.class);
            MethodHandle mh = MethodHandles.lookup().findVirtual(Optional.class, "isEmpty", type);
            return (boolean) mh.invokeExact(receiver);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backporting of Optional.or(Supplier) (b/191859202).
    private static <T> Optional<T> Optional_or(Optional<T> receiver,
                                               Supplier<? extends Optional<? extends T>> supplier) {
        try {
            MethodType type = MethodType.methodType(Optional.class, Supplier.class);
            MethodHandle mh = MethodHandles.lookup().findVirtual(Optional.class, "or", type);
            return (Optional<T>) mh.invokeExact(receiver, supplier);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backporting of Optional.orElseThrow() (b/191859202).
    private static <T> T Optional_orElseThrow(Optional<T> receiver) {
        try {
            MethodType type = MethodType.methodType(Object.class);
            MethodHandle mh =
                    MethodHandles.lookup().findVirtual(Optional.class, "orElseThrow", type);
            return (T) mh.invokeExact(receiver);
        } catch (NoSuchElementException expected) {
            throw expected;  // Underlying method may throw NoSuchElementException
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backporting of Optional.stream() (b/191859202).
    private static <T> Stream<T> Optional_stream(Optional<T> receiver) {
        try {
            MethodType type = MethodType.methodType(Stream.class);
            MethodHandle mh = MethodHandles.lookup().findVirtual(Optional.class, "stream", type);
            return (Stream<T>) mh.invokeExact(receiver);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
