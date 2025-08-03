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

class MultipleObject {
    Object inner;
    Object inner2;

    static Object inner_static;
}

public class Main {
    public static void main(String[] args) throws Error {
        // Several sets, same receiver.
        $noinline$testInstanceFieldSets(new Main(), new Object(), new Object(), new Object());
        $noinline$testStaticFieldSets(new Object(), new Object(), new Object());
        // Object ArraySets can throw since they need a type check so we cannot perform the
        // optimization.
        $noinline$testArraySets(new Object[3], new Object(), new Object(), new Object());
        // If we are swapping elements in the array, no need for a type check.
        $noinline$testSwapArray(new Object[3]);
        // If the array and the values have the same RTI, no need for a type check.
        $noinline$testArraySetsSameRTI();

        // We cannot rely on `null` sets to perform the optimization.
        $noinline$testNullInstanceFieldSets(new Main(), new Object());
        $noinline$testNullStaticFieldSets(new Object());
        $noinline$testNullArraySets(new Object[3], new Object());

        // Several sets, multiple receivers. (set obj1, obj2, obj1 and see that the card of obj1
        // gets eliminated)
        $noinline$testInstanceFieldSetsMultipleReceivers(
                new Main(), new Object(), new Object(), new Object());
        $noinline$testStaticFieldSetsMultipleReceivers(new Object(), new Object(), new Object());
        $noinline$testArraySetsMultipleReceiversSameRTI();

        // The write barrier elimination optimization is blocked by invokes, suspend checks, and
        // instructions that can throw.
        $noinline$testInstanceFieldSetsBlocked(
                new Main(), new Object(), new Object(), new Object());
        $noinline$testStaticFieldSetsBlocked(new Main(), new Object(), new Object(), new Object());
        $noinline$testArraySetsSameRTIBlocked(new Main());
    }

    /// CHECK-START: Main Main.$noinline$testInstanceFieldSets(Main, java.lang.Object, java.lang.Object, java.lang.Object) disassembly (after)
    /// CHECK: InstanceFieldSet field_name:Main.inner field_type:Reference write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: InstanceFieldSet field_name:Main.inner2 field_type:Reference write_barrier_kind:DontEmit
    /// CHECK: InstanceFieldSet field_name:Main.inner3 field_type:Reference write_barrier_kind:DontEmit
    private static Main $noinline$testInstanceFieldSets(Main m, Object o, Object o2, Object o3) {
        m.inner = o;
        m.inner2 = o2;
        m.inner3 = o3;
        return m;
    }

    /// CHECK-START: void Main.$noinline$testStaticFieldSets(java.lang.Object, java.lang.Object, java.lang.Object) disassembly (after)
    /// CHECK: StaticFieldSet field_name:Main.inner_static field_type:Reference write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: StaticFieldSet field_name:Main.inner_static2 field_type:Reference write_barrier_kind:DontEmit
    /// CHECK: StaticFieldSet field_name:Main.inner_static3 field_type:Reference write_barrier_kind:DontEmit
    private static void $noinline$testStaticFieldSets(Object o, Object o2, Object o3) {
        inner_static = o;
        inner_static2 = o2;
        inner_static3 = o3;
    }

    /// CHECK-START: java.lang.Object[] Main.$noinline$testArraySets(java.lang.Object[], java.lang.Object, java.lang.Object, java.lang.Object) disassembly (after)
    /// CHECK: ArraySet needs_type_check:true can_trigger_gc:true write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: ArraySet needs_type_check:true can_trigger_gc:true write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: ArraySet needs_type_check:true can_trigger_gc:true write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    private static java.lang.Object[] $noinline$testArraySets(
            Object[] arr, Object o, Object o2, Object o3) {
        arr[0] = o;
        arr[1] = o2;
        arr[2] = o3;
        return arr;
    }

    /// CHECK-START: java.lang.Object[] Main.$noinline$testSwapArray(java.lang.Object[]) disassembly (after)
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    private static java.lang.Object[] $noinline$testSwapArray(Object[] arr) {
        arr[0] = arr[1];
        arr[1] = arr[2];
        arr[2] = arr[0];
        return arr;
    }

    /// CHECK-START: java.lang.Object[] Main.$noinline$testArraySetsSameRTI() disassembly (after)
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    private static java.lang.Object[] $noinline$testArraySetsSameRTI() {
        Object[] arr = new Object[3];
        arr[0] = inner_static;
        arr[1] = inner_static2;
        arr[2] = inner_static3;
        return arr;
    }

    /// CHECK-START: Main Main.$noinline$testNullInstanceFieldSets(Main, java.lang.Object) disassembly (after)
    /// CHECK: InstanceFieldSet field_name:Main.inner field_type:Reference write_barrier_kind:DontEmit
    /// CHECK: InstanceFieldSet field_name:Main.inner2 field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: InstanceFieldSet field_name:Main.inner3 field_type:Reference write_barrier_kind:DontEmit
    private static Main $noinline$testNullInstanceFieldSets(Main m, Object o) {
        m.inner = null;
        m.inner2 = o;
        m.inner3 = null;
        return m;
    }

    /// CHECK-START: void Main.$noinline$testNullStaticFieldSets(java.lang.Object) disassembly (after)
    /// CHECK: StaticFieldSet field_name:Main.inner_static field_type:Reference write_barrier_kind:DontEmit
    /// CHECK: StaticFieldSet field_name:Main.inner_static2 field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: StaticFieldSet field_name:Main.inner_static3 field_type:Reference write_barrier_kind:DontEmit
    private static void $noinline$testNullStaticFieldSets(Object o) {
        inner_static = null;
        inner_static2 = o;
        inner_static3 = null;
    }

    /// CHECK-START: java.lang.Object[] Main.$noinline$testNullArraySets(java.lang.Object[], java.lang.Object) disassembly (after)
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    /// CHECK: ArraySet needs_type_check:true can_trigger_gc:true write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    private static Object[] $noinline$testNullArraySets(Object[] arr, Object o) {
        arr[0] = null;
        arr[1] = o;
        arr[2] = null;
        return arr;
    }

    /// CHECK-START: Main Main.$noinline$testInstanceFieldSetsMultipleReceivers(Main, java.lang.Object, java.lang.Object, java.lang.Object) disassembly (after)
    /// CHECK: InstanceFieldSet field_name:MultipleObject.inner field_type:Reference write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: InstanceFieldSet field_name:MultipleObject.inner field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: InstanceFieldSet field_name:MultipleObject.inner2 field_type:Reference write_barrier_kind:DontEmit
    private static Main $noinline$testInstanceFieldSetsMultipleReceivers(
            Main m, Object o, Object o2, Object o3) throws Error {
        m.mo = new MultipleObject();
        m.mo2 = new MultipleObject();

        m.mo.inner = o;
        // This card table for `m.mo2` can't me removed. Note that in `m.mo2 = new
        // MultipleObject();` above the receiver is `m`, not `m.mo2.
        m.mo2.inner = o2;
        // This card table for `m.mo` can me removed.
        m.mo.inner2 = o3;
        return m;
    }

    /// CHECK-START: void Main.$noinline$testStaticFieldSetsMultipleReceivers(java.lang.Object, java.lang.Object, java.lang.Object) disassembly (after)
    /// CHECK: StaticFieldSet field_name:MultipleObject.inner_static field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: StaticFieldSet field_name:Main.inner_static2 field_type:Reference write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: StaticFieldSet field_name:Main.inner_static3 field_type:Reference write_barrier_kind:DontEmit
    private static void $noinline$testStaticFieldSetsMultipleReceivers(
            Object o, Object o2, Object o3) {
        MultipleObject.inner_static = o;
        inner_static2 = o2;
        inner_static3 = o3;
    }

    /// CHECK-START: java.lang.Object[][] Main.$noinline$testArraySetsMultipleReceiversSameRTI() disassembly (after)
    // Initializing the values
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    // Setting the `array_of_arrays`.
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:DontEmit
    private static java.lang.Object[][] $noinline$testArraySetsMultipleReceiversSameRTI() {
        Object[] arr = new Object[3];
        Object[] other_arr = new Object[3];

        arr[0] = inner_static;
        other_arr[1] = inner_static2;
        arr[2] = inner_static3;

        // Return them so that LSE doesn't delete them
        Object[][] array_of_arrays = {arr, other_arr};
        return array_of_arrays;
    }

    private static void $noinline$emptyMethod() {}

    /// CHECK-START: Main Main.$noinline$testInstanceFieldSetsBlocked(Main, java.lang.Object, java.lang.Object, java.lang.Object) disassembly (after)
    /// CHECK: InstanceFieldSet field_name:Main.inner field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: InvokeStaticOrDirect method_name:Main.$noinline$emptyMethod
    /// CHECK: InstanceFieldSet field_name:Main.inner2 field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: MonitorOperation kind:enter
    /// CHECK: InstanceFieldSet field_name:Main.inner3 field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    private static Main $noinline$testInstanceFieldSetsBlocked(
            Main m, Object o, Object o2, Object o3) {
        m.inner = o;
        $noinline$emptyMethod();
        m.inner2 = o2;
        synchronized (m) {
            m.inner3 = o3;
        }
        return m;
    }

    /// CHECK-START: void Main.$noinline$testStaticFieldSetsBlocked(Main, java.lang.Object, java.lang.Object, java.lang.Object) disassembly (after)
    /// CHECK: StaticFieldSet field_name:Main.inner_static field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: InvokeStaticOrDirect method_name:Main.$noinline$emptyMethod
    /// CHECK: StaticFieldSet field_name:Main.inner_static2 field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: MonitorOperation kind:enter
    /// CHECK: StaticFieldSet field_name:Main.inner_static3 field_type:Reference write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    private static void $noinline$testStaticFieldSetsBlocked(
            Main m, Object o, Object o2, Object o3) {
        inner_static = o;
        $noinline$emptyMethod();
        inner_static2 = o2;
        synchronized (m) {
            inner_static3 = o3;
        }
    }

    /// CHECK-START: java.lang.Object[] Main.$noinline$testArraySetsSameRTIBlocked(Main) disassembly (after)
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: InvokeStaticOrDirect method_name:Main.$noinline$emptyMethod
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    /// CHECK: MonitorOperation kind:enter
    /// CHECK: ArraySet needs_type_check:false can_trigger_gc:false write_barrier_kind:EmitNotBeingReliedOn
    /// CHECK: ; card_table
    private static java.lang.Object[] $noinline$testArraySetsSameRTIBlocked(Main m) {
        Object[] arr = new Object[3];
        arr[0] = inner_static;
        $noinline$emptyMethod();
        arr[1] = inner_static2;
        synchronized (m) {
            arr[2] = inner_static3;
        }
        return arr;
    }

    Object inner;
    Object inner2;
    Object inner3;

    MultipleObject mo;
    MultipleObject mo2;

    static Object inner_static;
    static Object inner_static2;
    static Object inner_static3;
}
