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

public final class Main {
    public static void main(String[] args) {
        try {
            $noinline$testAlwaysThrowsDevirtualization();
            System.out.println("Expected to throw error.");
        } catch (Error expected) {
        }
    }

    public void throwsIfParamIsZero(int param) {
        if (param == 0) {
            throw new Error("");
        }
    }

    /// CHECK-START: void Main.$noinline$testAlwaysThrowsDevirtualization() inliner (before)
    /// CHECK:     InvokeVirtual method_name:Main.throwsIfParamIsZero always_throws:false
    /// CHECK:     ReturnVoid

    /// CHECK-START: void Main.$noinline$testAlwaysThrowsDevirtualization() inliner (after)
    /// CHECK-NOT: InvokeVirtual

    /// CHECK-START: void Main.$noinline$testAlwaysThrowsDevirtualization() inliner (after)
    /// CHECK:     InvokeStaticOrDirect method_name:Main.throwsIfParamIsZero always_throws:true

    public static void $noinline$testAlwaysThrowsDevirtualization() {
        Main m = new Main();
        m.throwsIfParamIsZero(0);
    }
}
