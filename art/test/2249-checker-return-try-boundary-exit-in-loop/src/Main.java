/*
 * Copyright (C) 2022 The Android Open Source Project
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

public class Main {
    public static void main(String[] args) throws Exception {
        assertNotNull($noinline$testReturnTryBoundaryExitInLoop(new Object()));
    }

    public static void assertNotNull(Object o) {
        if (o == null) {
            throw new Error("Expected not null!");
        }
    }

    // Simple method to have a call inside of the synchronized block.
    private static Object $noinline$call() {
        return new Object();
    }

    // Consistency check: Three try boundary kind:exit. One for the explicit try catch, and two for
    // the synchronized block (normal, and exceptional path).

    /// CHECK-START: java.lang.Object Main.$inline$inner(java.lang.Object) builder (after)
    /// CHECK:     TryBoundary kind:exit
    /// CHECK:     TryBoundary kind:exit
    /// CHECK:     TryBoundary kind:exit
    /// CHECK-NOT: TryBoundary kind:exit

    /// CHECK-START: java.lang.Object Main.$inline$inner(java.lang.Object) builder (after)
    /// CHECK: Return loop:B2

    /// CHECK-START: java.lang.Object Main.$inline$inner(java.lang.Object) builder (after)
    /// CHECK: TryBoundary kind:exit loop:B2
    /// CHECK: TryBoundary kind:exit loop:B2
    /// CHECK: TryBoundary kind:exit loop:B2
    private static Object $inline$inner(Object o) {
        for (int i = 0; i < 4; i++) {
            try {
                synchronized (o) {
                    return $noinline$call();
                }
            } catch (Error e) {
                continue;
            }
        }
        return null;
    }

    // Simple outer to inline `inner`.
    private static Object $noinline$testReturnTryBoundaryExitInLoop(Object o) {
        return $inline$inner(o);
    }
}
