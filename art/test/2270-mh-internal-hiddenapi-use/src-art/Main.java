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

import java.lang.invoke.*;
import dalvik.system.VMRuntime;

public class Main {
    public static void main(String... args) throws Throwable {
        System.loadLibrary(args[0]);
        enableHiddenApiChecks();
        // MH.identity(...) methods were marked as hidden in aosp/321456.
        VMRuntime.getRuntime().setTargetSdkVersion(28);

        MethodHandle intIdentity = MethodHandles.identity(int.class);

        int value = 42;
        int returnedValue = (int) intIdentity.invokeExact(value);

        if (returnedValue != value) {
            System.out.printf("Expected: %d, but identity MH returned %d\n",
                value, returnedValue);
            throw new AssertionError("identity fail");
        }

        value = 101;
        MethodHandle intConstant = MethodHandles.constant(int.class, value);
        returnedValue = (int) intConstant.invokeExact();

        if (returnedValue != value) {
            System.out.printf("Expected: %d, but constant MH returned %d\n",
                value, returnedValue);
            throw new AssertionError("constant failed");
        }

        int secondCallValue = (int) intConstant.invokeExact();
        if (secondCallValue != value) {
            System.out.printf("Expected: %d, but constant MH returned %d on subsequent call\n",
                value, returnedValue);
            throw new AssertionError("constant failed");
        }
    }

    private static native void enableHiddenApiChecks();
}
