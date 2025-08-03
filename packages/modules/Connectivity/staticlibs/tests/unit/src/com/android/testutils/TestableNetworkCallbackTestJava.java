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

package com.android.testutils;

import static com.android.testutils.RecorderCallback.CallbackEntry.AVAILABLE;
import static com.android.testutils.TestableNetworkCallbackKt.anyNetwork;

import static org.junit.Assume.assumeTrue;

import org.junit.Test;

public class TestableNetworkCallbackTestJava {
    @Test
    void testAllExpectOverloads() {
        // This test should never run, it only checks that all overloads exist and build
        assumeTrue(false);
        final TestableNetworkCallback callback = new TestableNetworkCallback();
        TestableNetworkCallback.HasNetwork hn = TestableNetworkCallbackKt::anyNetwork;

        // Method with all arguments (version that takes a Network)
        callback.expect(AVAILABLE, anyNetwork(), 10, "error", cb -> true);

        // Overloads omitting one argument. One line for omitting each argument, in positional
        // order. Versions that take a Network.
        callback.expect(AVAILABLE, 10, "error", cb -> true);
        callback.expect(AVAILABLE, anyNetwork(), "error", cb -> true);
        callback.expect(AVAILABLE, anyNetwork(), 10, cb -> true);
        callback.expect(AVAILABLE, anyNetwork(), 10, "error");

        // Overloads for omitting two arguments. One line for omitting each pair of arguments.
        // Versions that take a Network.
        callback.expect(AVAILABLE, "error", cb -> true);
        callback.expect(AVAILABLE, 10, cb -> true);
        callback.expect(AVAILABLE, 10, "error");
        callback.expect(AVAILABLE, anyNetwork(), cb -> true);
        callback.expect(AVAILABLE, anyNetwork(), "error");
        callback.expect(AVAILABLE, anyNetwork(), 10);

        // Overloads for omitting three arguments. One line for each remaining argument.
        // Versions that take a Network.
        callback.expect(AVAILABLE, cb -> true);
        callback.expect(AVAILABLE, "error");
        callback.expect(AVAILABLE, 10);
        callback.expect(AVAILABLE, anyNetwork());

        // Java overload for omitting all four arguments.
        callback.expect(AVAILABLE);

        // Same orders as above, but versions that take a HasNetwork. Except overloads that
        // were already tested because they omitted the Network argument
        callback.expect(AVAILABLE, hn, 10, "error", cb -> true);
        callback.expect(AVAILABLE, hn, "error", cb -> true);
        callback.expect(AVAILABLE, hn, 10, cb -> true);
        callback.expect(AVAILABLE, hn, 10, "error");

        callback.expect(AVAILABLE, hn, cb -> true);
        callback.expect(AVAILABLE, hn, "error");
        callback.expect(AVAILABLE, hn, 10);

        callback.expect(AVAILABLE, hn);
    }
}
