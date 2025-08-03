/*
 * Copyright (C) 2024 The Android Open Source Project
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
    public static void main(String[] args) {
        $noinline$testHonorWriteBarrier();
        $noinline$testDontSkipWriteBarrier();
    }

    public static void $noinline$testHonorWriteBarrier() {
        String[] arr = {"Hello", "World"};
        // We first run the gc to make sure the card is clean.
        Runtime.getRuntime().gc();
        // Continually call $noinline$testArraySetsHonorWriteBarrier while allocating over 64 MiB of
        // memory (with heap size limited to 16 MiB), in order to increase memory pressure and
        // eventually trigger a concurrent garbage collection, which will start by putting the GC in
        // marking mode to trigger the bug.
        for (int i = 0; i != 64 * 1024; ++i) {
            $noinline$allocateAtLeast1KiB();
            $noinline$testArraySetsHonorWriteBarrier(arr, "Universe");
        }
    }

    // When the bug was present, $noinline$testArraySetsHonorWriteBarrier would never set the card
    // as dirty (which is incorrect). arr[1]'s' write barrier depends on arr[0]'s write barrier. The
    // disappeared BoundType in prepare_for_register_allocation shouldn't skip marking the card
    // dirty.

    /// CHECK-START: java.lang.String[] Main.$noinline$testArraySetsHonorWriteBarrier(java.lang.String[], java.lang.String) prepare_for_register_allocation (before)
    /// CHECK: <<Null:l\d+>>   NullConstant
    /// CHECK: <<BT:l\d+>>     BoundType [<<Null>>]
    /// CHECK: ArraySet [<<arr:l\d+>>,<<index:i\d+>>,<<BT>>] value_can_be_null:true needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    /// CHECK: ArraySet value_can_be_null:true needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitNotBeingReliedOn

    /// CHECK-START: java.lang.String[] Main.$noinline$testArraySetsHonorWriteBarrier(java.lang.String[], java.lang.String) prepare_for_register_allocation (after)
    /// CHECK: <<Null:l\d+>>   NullConstant
    /// CHECK: ArraySet [<<arr:l\d+>>,<<index:i\d+>>,<<Null>>] value_can_be_null:true needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    /// CHECK: ArraySet value_can_be_null:true needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitNotBeingReliedOn

    /// CHECK-START: java.lang.String[] Main.$noinline$testArraySetsHonorWriteBarrier(java.lang.String[], java.lang.String) prepare_for_register_allocation (after)
    /// CHECK-NOT: BoundType

    /// CHECK-START: java.lang.String[] Main.$noinline$testArraySetsHonorWriteBarrier(java.lang.String[], java.lang.String) disassembly (after)
    /// CHECK: ArraySet value_can_be_null:true needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    /// CHECK: ArraySet value_can_be_null:true needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitNotBeingReliedOn
    private static java.lang.String[] $noinline$testArraySetsHonorWriteBarrier(
            String[] arr, String o2) {
        Object o = null;
        arr[0] = (String) o;
        arr[1] = o2;
        return arr;
    }

    public static void $noinline$testDontSkipWriteBarrier() {
        String[] arr = {"Hello", "World"};
        // We first run the gc to make sure the card is clean.
        Runtime.getRuntime().gc();
        // Continually call $noinline$testArraySets while allocating over 64 MiB of memory (with
        // heap size limited to 16 MiB), in order to increase memory pressure and eventually trigger
        // a concurrent garbage collection, which will start by putting the GC in marking mode to
        // trigger the bug.
        for (int i = 0; i != 64 * 1024; ++i) {
            $noinline$allocateAtLeast1KiB();
            $noinline$testArraySetsDontSkipWriteBarrier(arr, null, "Universe");
        }
    }

    // When the bug was present, $noinline$testArraySetsDontSkipWriteBarrier would never set the
    // card as dirty (which is incorrect). arr[1]'s write barrier depends on arr[0]'s write barrier.
    // The code path should mark the card dirty without a null check on `o` in `arr[0] = o`.

    /// CHECK-START: java.lang.String[] Main.$noinline$testArraySetsDontSkipWriteBarrier(java.lang.String[], java.lang.String, java.lang.String) disassembly (after)
    /// CHECK: ArraySet value_can_be_null:true needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ArraySet value_can_be_null:true needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    private static java.lang.String[] $noinline$testArraySetsDontSkipWriteBarrier(
            String[] arr, String o, String o2) {
        arr[0] = o;
        arr[1] = o2;
        return arr;
    }

    // Allocate at least 1 KiB of memory on the managed heap.
    // Retain some allocated memory and release old allocations so that the
    // garbage collector has something to do.
    public static void $noinline$allocateAtLeast1KiB() {
        memory[allocationIndex] = new Object[1024 / 4];
        ++allocationIndex;
        if (allocationIndex == memory.length) {
            allocationIndex = 0;
        }
    }

    public static Object[] memory = new Object[1024];
    public static int allocationIndex = 0;
}
