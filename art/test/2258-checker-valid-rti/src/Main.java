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

public class Main {
    public static void main(String[] args) {}

    /// CHECK-START: java.lang.Object[] Main.testNoRTILoopNoIterations() builder (after)
    /// CHECK-DAG:     <<Null:l\d+>> NullConstant
    /// CHECK-DAG:     <<Phi:l\d+>> Phi [<<Null>>,<<PhiBoundType:l\d+>>] klass:invalid
    /// CHECK-DAG:     <<Check:l\d+>> NullCheck [<<Phi>>] klass:invalid
    /// CHECK-DAG:     <<ArrayGet:l\d+>> ArrayGet [<<Check>>,<<BoundsCheck:i\d+>>] klass:invalid
    /// CHECK-DAG:     <<BoundType:l\d+>> BoundType [<<ArrayGet>>] klass:invalid
    // Due to the circular uses, this is how we check that the BoundType is a use of the Phi.
    /// CHECK-EVAL:    "<<PhiBoundType>>" == "<<BoundType>>"
    private static Object[] testNoRTILoopNoIterations() {
        Object[] h = null;
        for (int j = 0; j < 0; j++) {
            h = (Object[]) h[0];
        }
        return h;
    }

    private static void $inline$doNothing(Object[] obj) {}

    // This test also has no iterations, but we won't know until LSE. We inline the doNothing
    // method, try to apply the invalid RTI and crash.

    /// CHECK-START: java.lang.Object[] Main.testNoRTILoopNoIterationsWithInlining() builder (after)
    /// CHECK-DAG:     <<Null:l\d+>> NullConstant
    /// CHECK-DAG:     <<Phi:l\d+>> Phi [<<Null>>,<<PhiBoundType:l\d+>>] klass:invalid
    /// CHECK-DAG:     InvokeStaticOrDirect [<<Phi>>] method_name:Main.$inline$doNothing
    /// CHECK-DAG:     <<Check:l\d+>> NullCheck [<<Phi>>] klass:invalid
    /// CHECK-DAG:     <<ArrayGet:l\d+>> ArrayGet [<<Check>>,<<BoundsCheck:i\d+>>] klass:invalid
    /// CHECK-DAG:     <<BoundType:l\d+>> BoundType [<<ArrayGet>>] klass:invalid
    // Due to the circular uses, this is how we check that the BoundType is a use of the Phi.
    /// CHECK-EVAL:    "<<PhiBoundType>>" == "<<BoundType>>"
    public static Object[] testNoRTILoopNoIterationsWithInlining() {
        Object[] h = null;
        int iterations = 0;
        int other_iterations = iterations;
        for (int j = 0; j < other_iterations; j++) {
            $inline$doNothing(h);
            h = (Object[]) h[0];
        }
        return h;
    }

}
