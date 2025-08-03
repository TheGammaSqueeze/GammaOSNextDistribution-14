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

// Note that the empty string is present in the BootImage but the other one is a BSS string. We are
// testing both AOT LoadString kinds.

public class Main {
    public static void main(String[] args) {
        $noinline$testLength();
        $noinline$testIsEmpty();
    }

    private static void $noinline$testLength() {
        assertEquals(0, $noinline$testLengthEmptyString());
        assertEquals(0, $noinline$testLengthEmptyStringWithInline());
        assertEquals(32, $noinline$testLengthBssString());
        assertEquals(32, $noinline$testLengthBssStringWithInline());
    }

    /// CHECK-START: int Main.$noinline$testLengthEmptyString() constant_folding (before)
    /// CHECK: LoadString load_kind:BootImageRelRo
    /// CHECK: <<Length:i\d+>> ArrayLength
    /// CHECK:                 Return [<<Length>>]

    /// CHECK-START: int Main.$noinline$testLengthEmptyString() constant_folding (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]

    /// CHECK-START: int Main.$noinline$testLengthEmptyString() dead_code_elimination$initial (after)
    /// CHECK-NOT: LoadString

    /// CHECK-START: int Main.$noinline$testLengthEmptyString() dead_code_elimination$initial (after)
    /// CHECK-NOT: ArrayLength
    private static int $noinline$testLengthEmptyString() {
        String str = "";
        return str.length();
    }

    /// CHECK-START: int Main.$noinline$testLengthEmptyStringWithInline() constant_folding$after_inlining (before)
    /// CHECK: LoadString load_kind:BootImageRelRo
    /// CHECK: <<Length:i\d+>> ArrayLength
    /// CHECK:                 Return [<<Length>>]

    /// CHECK-START: int Main.$noinline$testLengthEmptyStringWithInline() constant_folding$after_inlining (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]

    /// CHECK-START: int Main.$noinline$testLengthEmptyStringWithInline() dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: LoadString

    /// CHECK-START: int Main.$noinline$testLengthEmptyStringWithInline() dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: ArrayLength
    private static int $noinline$testLengthEmptyStringWithInline() {
        String str = "";
        return $inline$returnLength(str);
    }

    /// CHECK-START: int Main.$noinline$testLengthBssString() constant_folding (before)
    /// CHECK: LoadString load_kind:BssEntry
    /// CHECK: <<Length:i\d+>> ArrayLength
    /// CHECK:                 Return [<<Length>>]

    /// CHECK-START: int Main.$noinline$testLengthBssString() constant_folding (after)
    /// CHECK: <<Const32:i\d+>> IntConstant 32
    /// CHECK:                  Return [<<Const32>>]

    // We don't remove LoadString load_kind:BssEntry even if they have no uses, since IsRemovable()
    // returns false for them.
    /// CHECK-START: int Main.$noinline$testLengthBssString() dead_code_elimination$initial (after)
    /// CHECK: LoadString load_kind:BssEntry

    /// CHECK-START: int Main.$noinline$testLengthBssString() dead_code_elimination$initial (after)
    /// CHECK-NOT: ArrayLength
    private static int $noinline$testLengthBssString() {
        String str = "2047-checker-const-string-length";
        return str.length();
    }

    /// CHECK-START: int Main.$noinline$testLengthBssStringWithInline() constant_folding$after_inlining (before)
    /// CHECK: LoadString load_kind:BssEntry
    /// CHECK: <<Length:i\d+>> ArrayLength
    /// CHECK:                 Return [<<Length>>]

    /// CHECK-START: int Main.$noinline$testLengthBssStringWithInline() constant_folding$after_inlining (after)
    /// CHECK: <<Const32:i\d+>> IntConstant 32
    /// CHECK:                  Return [<<Const32>>]

    // We don't remove LoadString load_kind:BssEntry even if they have no uses, since IsRemovable()
    // returns false for them.
    /// CHECK-START: int Main.$noinline$testLengthBssStringWithInline() dead_code_elimination$after_inlining (after)
    /// CHECK: LoadString load_kind:BssEntry

    /// CHECK-START: int Main.$noinline$testLengthBssStringWithInline() dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: ArrayLength
    private static int $noinline$testLengthBssStringWithInline() {
        String str = "2047-checker-const-string-length";
        return $inline$returnLength(str);
    }

    private static int $inline$returnLength(String str) {
        return str.length();
    }

    private static void $noinline$testIsEmpty() {
        assertEquals(true, $noinline$testIsEmptyEmptyString());
        assertEquals(true, $noinline$testIsEmptyEmptyStringWithInline());
        assertEquals(false, $noinline$testIsEmptyBssString());
        assertEquals(false, $noinline$testIsEmptyBssStringWithInline());
    }

    /// CHECK-START: boolean Main.$noinline$testIsEmptyEmptyString() constant_folding (before)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK: LoadString load_kind:BootImageRelRo
    /// CHECK: <<Length:i\d+>> ArrayLength
    /// CHECK: <<Eq:z\d+>>     Equal [<<Length>>,<<Const0>>]
    /// CHECK:                 Return [<<Eq>>]

    /// CHECK-START: boolean Main.$noinline$testIsEmptyEmptyString() constant_folding (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]

    /// CHECK-START: boolean Main.$noinline$testIsEmptyEmptyString() dead_code_elimination$initial (after)
    /// CHECK-NOT: LoadString

    /// CHECK-START: boolean Main.$noinline$testIsEmptyEmptyString() dead_code_elimination$initial (after)
    /// CHECK-NOT: ArrayLength
    private static boolean $noinline$testIsEmptyEmptyString() {
        String str = "";
        return str.isEmpty();
    }

    /// CHECK-START: boolean Main.$noinline$testIsEmptyEmptyStringWithInline() constant_folding$after_inlining (before)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK: LoadString load_kind:BootImageRelRo
    /// CHECK: <<Length:i\d+>> ArrayLength
    /// CHECK: <<Eq:z\d+>>     Equal [<<Length>>,<<Const0>>]
    /// CHECK:                 Return [<<Eq>>]

    /// CHECK-START: boolean Main.$noinline$testIsEmptyEmptyStringWithInline() constant_folding$after_inlining (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]

    /// CHECK-START: boolean Main.$noinline$testIsEmptyEmptyStringWithInline() dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: LoadString

    /// CHECK-START: boolean Main.$noinline$testIsEmptyEmptyStringWithInline() dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: ArrayLength
    private static boolean $noinline$testIsEmptyEmptyStringWithInline() {
        String str = "";
        return $inline$returnIsEmpty(str);
    }

    /// CHECK-START: boolean Main.$noinline$testIsEmptyBssString() constant_folding (before)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK: LoadString load_kind:BssEntry
    /// CHECK: <<Length:i\d+>> ArrayLength
    /// CHECK: <<Eq:z\d+>>     Equal [<<Length>>,<<Const0>>]
    /// CHECK:                 Return [<<Eq>>]

    /// CHECK-START: boolean Main.$noinline$testIsEmptyBssString() constant_folding (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]

    // We don't remove LoadString load_kind:BssEntry even if they have no uses, since IsRemovable()
    // returns false for them.
    /// CHECK-START: boolean Main.$noinline$testIsEmptyBssString() dead_code_elimination$initial (after)
    /// CHECK: LoadString load_kind:BssEntry

    /// CHECK-START: boolean Main.$noinline$testIsEmptyBssString() dead_code_elimination$initial (after)
    /// CHECK-NOT: ArrayLength
    private static boolean $noinline$testIsEmptyBssString() {
        String str = "2047-checker-const-string-length";
        return str.isEmpty();
    }

    /// CHECK-START: boolean Main.$noinline$testIsEmptyBssStringWithInline() constant_folding$after_inlining (before)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK: LoadString load_kind:BssEntry
    /// CHECK: <<Length:i\d+>> ArrayLength
    /// CHECK: <<Eq:z\d+>>     Equal [<<Length>>,<<Const0>>]
    /// CHECK:                 Return [<<Eq>>]

    /// CHECK-START: boolean Main.$noinline$testIsEmptyBssStringWithInline() constant_folding$after_inlining (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]

    // We don't remove LoadString load_kind:BssEntry even if they have no uses, since IsRemovable()
    // returns false for them.
    /// CHECK-START: boolean Main.$noinline$testIsEmptyBssStringWithInline() dead_code_elimination$after_inlining (after)
    /// CHECK: LoadString load_kind:BssEntry

    /// CHECK-START: boolean Main.$noinline$testIsEmptyBssStringWithInline() dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: ArrayLength
    private static boolean $noinline$testIsEmptyBssStringWithInline() {
        String str = "2047-checker-const-string-length";
        return $inline$returnIsEmpty(str);
    }

    private static boolean $inline$returnIsEmpty(String str) {
        return str.isEmpty();
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " got " + actual);
        }
    }

    static void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " got " + actual);
        }
    }
}
