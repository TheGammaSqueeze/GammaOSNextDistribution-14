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

import pkg1.A;
import pkg1.C;
import pkg1.C2;
import pkg1.C2I1;
import pkg1.C2I2;
import pkg1.CXI1;
import pkg1.CXI2;
import pkg1.I1;
import pkg2.B;
import pkg2.D;
import pkg2.D2;
import pkg2.D2I1;
import pkg2.D2I2;
import pkg2.DXI1;
import pkg2.DXI2;
import pkg2.I2;

public class Main {
    public static void main(String args[]) {
        // A single method signature can result in multiple vtable entries
        // when package-private methods from different packages are involved.
        // All classes here define the method `void foo()` but classes
        //     class pkg1.A { ... }
        //     class pkg2.B extends pkg1.A { ... }
        //     class pkg1.C extends pkg2.B { ... }
        //     class pkg2.D extends pkg1.C { ... }
        // define it as package-private and classes
        //     class pkg1.C2 extends pkg2.B { ... }
        //     class pkg2.D2 extends pkg1.C2 { ... }
        // define it as public, so that we can test different cases of overriding.

        A a = new A();
        a.callAFoo();  // pkg1.A.foo

        B b = new B();
        b.callAFoo();  // pkg1.A.foo (not overridden by pkg2.B.foo)
        b.callBFoo();  // pkg2.B.foo

        C c = new C();
        c.callAFoo();  // pkg1.C.foo (overriddes pkg1.A.foo)
        c.callBFoo();  // pkg2.B.foo (not overridden by pkg1.C.foo)
        c.callCFoo();  // pkg1.C.foo

        D d = new D();
        d.callAFoo();  // pkg1.C.foo (not overridden by pkg2.D.foo)
        d.callBFoo();  // pkg2.D.foo (overrides pkg2.B.foo)
        d.callCFoo();  // pkg1.C.foo (not overridden by pkg2.D.foo)
        d.callDFoo();  // pkg2.D.foo

        C2 c2 = new C2();
        c2.callAFoo();  // pkg1.C2.foo (overriddes pkg1.A.foo)
        c2.callBFoo();  // pkg2.B.foo (not overridden by pkg1.C2.foo)
        c2.callC2Foo();  // pkg1.C2.foo

        D2 d2 = new D2();
        d2.callAFoo();  // pkg2.D2.foo (overrides public pkg2.C2.foo which overrides pkg1.A.foo)
        d2.callBFoo();  // pkg2.D2.foo (overrides package-private pkg2.B.foo in the same package)
        d2.callC2Foo();  // pkg2.D2.foo (overrides public pkg2.C2.foo)
        d2.callD2Foo();  // pkg2.D2.foo

        // Interface methods always target the method in the most-derived class with implementation
        // even when package-private methods from different packages are involved.
        //
        // Test interface calls through the following interfaces:
        //    interface pkg1.I1 { ... }
        //    interface pkg2.I2 { ... }
        // that declare a public `void foo()` for concrete classes
        //    class pkg1.C2I1 extends pkg1.C2 implements pkg1.I1 {}
        //    class pkg1.C2I2 extends pkg1.C2 implements pkg2.I2 {}
        //    class pkg2.D2I1 extends pkg2.D2 implements pkg1.I1 {}
        //    class pkg2.D2I2 extends pkg2.D2 implements pkg2.I2 {}
        //    class pkg1.CXI1 extends pkg1.CX implements pkg1.I1 {}
        //    class pkg1.CXI2 extends pkg1.CX implements pkg2.I2 {}
        //    class pkg2.DXI1 extends pkg2.DX implements pkg1.I1 {}
        //    class pkg2.DXI2 extends pkg2.DX implements pkg2.I2 {}
        // with helper classes `pkg1.C2` and `pkg2.D2` from previous tests and helper class
        //    class pkg2.BX extends pkg1.A { ... }
        // defining a public `void foo()` but helper classes
        //    class pkg1.CX extends pkg2.BX { ... }
        //    class pkg2.DX extends pkg1.CX { ... }
        // defining a package-private `void foo()`. This is a compilation error in Java,
        // so we're using different definitions for `pkg1.I1`, `pkg2.I2` and `pkg2.BX` in
        // src/ for compiling other classes and in src2/ for their run-time definition.

        C2I1 c2i1 = new C2I1();
        I1.callI1Foo(c2i1);  // pkg1.C2.foo

        C2I2 c2i2 = new C2I2();
        I2.callI2Foo(c2i2);  // pkg1.C2.foo

        D2I1 d2i1 = new D2I1();
        I1.callI1Foo(d2i1);  // pkg1.D2.foo

        D2I2 d2i2 = new D2I2();
        I2.callI2Foo(d2i2);  // pkg1.D2.foo

        try {
            CXI1 cxi1 = new CXI1();
            I1.callI1Foo(cxi1);
        } catch (IllegalAccessError expected) {
            System.out.println("Caught IllegalAccessError");
        }

        try {
            CXI2 cxi2 = new CXI2();
            I2.callI2Foo(cxi2);
        } catch (IllegalAccessError expected) {
            System.out.println("Caught IllegalAccessError");
        }

        try {
            DXI1 dxi1 = new DXI1();
            I1.callI1Foo(dxi1);
        } catch (IllegalAccessError expected) {
            System.out.println("Caught IllegalAccessError");
        }

        try {
            DXI2 dxi2 = new DXI2();
            I2.callI2Foo(dxi2);
        } catch (IllegalAccessError expected) {
            System.out.println("Caught IllegalAccessError");
        }
    }
}
