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

import annotations.ConstantMethodType;

import java.lang.invoke.MethodType;
import java.util.Objects;

public class Main {
    public static void main(String... args) throws Throwable {
        testEquality();
        // Subsequent const-method-type call should take value from from .bss.
        testEquality();
        testNonEquality();
        testNonEquality();
        testWithUninitializableClass();
    }

    private static void unreachable() {
        throw new Error("unreachable");
    }

    @ConstantMethodType(
        returnType = void.class,
        parameterTypes = {
            boolean.class,
            byte.class,
            char.class,
            short.class,
            int.class,
            long.class,
            float.class,
            double.class,
            Object.class,
            int[].class })
    private static MethodType takesEverythingReturnsVoid() {
        unreachable();
        return null;
    }

    public static void testEquality() throws Throwable {
        MethodType actual = takesEverythingReturnsVoid();

        MethodType expected = MethodType.methodType(
            void.class,
            boolean.class,
            byte.class,
            char.class,
            short.class,
            int.class,
            long.class,
            float.class,
            double.class,
            Object.class,
            int[].class);

        assertSame(expected, actual);
        assertSame(takesEverythingReturnsVoid(), takesEverythingReturnsVoid());
    }

    public static void testNonEquality() throws Throwable {
        MethodType actual = takesEverythingReturnsVoid();

        MethodType expected = MethodType.methodType(
            boolean.class,
            boolean.class,
            byte.class,
            char.class,
            short.class,
            int.class,
            long.class,
            float.class,
            double.class,
            Object.class,
            int[].class);

        assertNotEqual(expected, actual);
    }

    @ConstantMethodType(
        returnType = void.class,
        parameterTypes = { UnloadableClass.class })
    private static MethodType takesUnloadableReturnsVoid() {
        unreachable();
        return null;
    }

    public static volatile int x = 0;

    private static class UnloadableClass {
        static {
            if (x == x) {
                throw new RuntimeException("don't init me");
            }
        }
    }

    public static void testWithUninitializableClass() {
        MethodType actual = takesUnloadableReturnsVoid();

        MethodType expected = MethodType.methodType(void.class, UnloadableClass.class);

        assertSame(expected, actual);
    }

    public static void assertNotEqual(Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            String msg = "Expected to be non equal, but got: " + expected;
            throw new AssertionError(msg);
        }
    }

    public static void assertSame(Object expected, Object actual) {
        if (actual != expected) {
            String msg = String.format("Expected: %s(%X), got %s(%X)",
                                  expected,
                                  System.identityHashCode(expected),
                                  actual,
                                  System.identityHashCode(actual));
            throw new AssertionError(msg);
        }
    }
}