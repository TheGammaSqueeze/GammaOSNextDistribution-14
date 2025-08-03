/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8050818
 * @run testng PredicateNotTest
 */
package test.java.util.function;

// Android-added: workaround for d8 backports.
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.util.List;
import java.util.function.Predicate;
import org.testng.annotations.Test;

import static java.util.stream.Collectors.joining;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class PredicateNotTest {
    // BEGIN Android-added
    // MethodHandle for invoking Predicate.not() to prevent d8 inserting it's backported
    // `Predicate.not()` method (b/191859202, OpenJDK 11) and masking test coverage results.
    static final MethodHandle NOT = initializeNot();

    private static MethodHandle initializeNot()
    {
        try {
            MethodType notType = MethodType.methodType(Predicate.class, Predicate.class);
            return MethodHandles.lookup().findStatic(Predicate.class, "not", notType);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static <T> Predicate<T> not(Predicate<? super T> target) {
        try {
            return (Predicate<T>) NOT.invoke(target);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    // END Android-added

    static class IsEmptyPredicate implements Predicate<String> {
        @Override
        public boolean test(String s) {
            return s.isEmpty();
        }
    }

    @Test
    public void test() {
        List<String> test = List.of(
           "A non-empty line",
           "",
           "A non-empty line",
           "",
           "A non-empty line",
           "",
           "A non-empty line",
           ""
        );
        String expected = "A non-empty line\nA non-empty line\nA non-empty line\nA non-empty line";

        assertEquals(test.stream().filter(not(String::isEmpty)).collect(joining("\n")), expected);
        assertEquals(test.stream().filter(not(s -> s.isEmpty())).collect(joining("\n")), expected);
        assertEquals(test.stream().filter(not(new IsEmptyPredicate())).collect(joining("\n")), expected);
        assertEquals(test.stream().filter(not(not(not(String::isEmpty)))).collect(joining("\n")), expected);
        assertEquals(test.stream().filter(not(not(not(s -> s.isEmpty())))).collect(joining("\n")), expected);
        assertEquals(test.stream().filter(not(not(not(new IsEmptyPredicate())))).collect(joining("\n")), expected);
    }
}

