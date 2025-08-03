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
    private static boolean usingDalvik = "Dalvik".equals(System.getProperty("java.vm.name"));

    public static void expectSameString(String expected, String actual) {
        if (expected != actual) {
            throw new Error("Expected " + expected + ", got " + actual + " (different object)");
        }
    }

    public static void expectDefault(Super target) {
        String output = target.testMethod();  // invoke-virtual Super.testMethod()
        Abstract abstractTarget = target;
        String output2 = abstractTarget.testMethod();  // invoke-interface Abstract.testMethod()
        expectSameString(output, output2);
        System.out.println("Output from " + target.getClass().getName() + ": " + output);
    }

    public static void expectConflict(Super target) {
        try {
            String output = target.testMethod();  // invoke-virtual Super.testMethod()
            throw new Error("Unexpected success for " + target.getClass().getName() +
                            " output: " + output);
        } catch (AbstractMethodError ame) {
            if (usingDalvik) {
                throw new Error("Unexpected AbstractMethodError", ame);
            }  // else the AME is expected on RI.
        } catch (IncompatibleClassChangeError expected) {
        }
        try {
            Abstract abstractTarget = target;
            String output = abstractTarget.testMethod();  // invoke-interface Abstract.testMethod()
            throw new Error("Unexpected success for " + target.getClass().getName() +
                            " output: " + output);
        } catch (AbstractMethodError ame) {
            if (usingDalvik) {
                throw new Error("Unexpected AbstractMethodError", ame);
            }  // else the AME is expected on RI.
        } catch (IncompatibleClassChangeError expected) {
        }
        System.out.println("Conflict in class " + target.getClass().getName());
    }

    public static void expectMiranda(Super target) {
        try {
            String output = target.testMethod();  // invoke-virtual Super.testMethod()
            throw new Error("Unexpected success for " + target.getClass().getName() +
                            " output: " + output);
        } catch (AbstractMethodError expected) {
        }
        try {
            Abstract abstractTarget = target;
            String output = abstractTarget.testMethod();  // invoke-interface Abstract.testMethod()
            throw new Error("Unexpected success for " + target.getClass().getName() +
                            " output: " + output);
        } catch (AbstractMethodError expected) {
        }
        System.out.println("Miranda in class " + target.getClass().getName());
    }

    public static void main(String args[]) {
        // Basic tests that have the interfaces D<n> with default method and/or
        // D<n>M interfaces that mask the default method ordered by <n>.
        expectMiranda(new Super());
        expectDefault(new Default_D1());
        expectDefault(new Default_D2());
        expectDefault(new Default_D3());
        expectMiranda(new Miranda_D1M());
        expectMiranda(new Miranda_D2M());
        expectMiranda(new Miranda_D3M());
        expectConflict(new Conflict_D1_D2());
        expectDefault(new Default_D1_D2M());
        expectDefault(new Default_D1M_D2());
        expectMiranda(new Miranda_D1M_D2M());
        expectConflict(new Conflict_D1_D2_D3());
        expectConflict(new Conflict_D1_D2_D3M());
        expectConflict(new Conflict_D1_D2M_D3());
        expectDefault(new Default_D1_D2M_D3M());
        expectConflict(new Conflict_D1M_D2_D3());
        expectDefault(new Default_D1M_D2_D3M());
        expectDefault(new Default_D1M_D2M_D3());
        expectMiranda(new Miranda_D1M_D2M_D3M());

        // Cases where one interface masks the method in more than one superinterface.
        expectMiranda(new Miranda_D1D2M());
        expectDefault(new Default_D1D2D());
        expectMiranda(new Miranda_AD1D2M());
        expectDefault(new Default_AD1D2D());

        // Cases where the interface D2 is early in the interface table but masked by D2M later.
        expectDefault(new Default_D2_D1_D2M());
        expectMiranda(new Miranda_D2_D1M_D2M());

        // Cases that involve a superclass with miranda method in the vtable.
        // Note: The above cases also include a miranda method in the superclass vtable
        // anyway because we want to test `invoke-virtual Super.testMethod()` as well
        // as the `invoke-interface Abstract.testMethod()`. However, miranda methods in
        // superclass vtable mean that all default methods in superclass interfaces,
        // if any, have been masked by abstract method, so processing them is a no-op.
        expectDefault(new Default_D1M_x_D2());
        expectMiranda(new Miranda_D1M_x_D2M());
        expectConflict(new Conflict_D1M_x_D2_D3());
        expectDefault(new Default_D1M_x_D2_D3M());
        expectDefault(new Default_D1M_x_D2M_D3());
        expectMiranda(new Miranda_D1M_x_D2M_D3M());

        // Cases that involve a superclass with default method in the vtable.
        expectConflict(new Conflict_D1_x_D2());
        expectDefault(new Default_D1_x_D2M());
        expectDefault(new Default_D1_x_D1MD());
        expectMiranda(new Miranda_D1_x_D1M());
        expectConflict(new Conflict_D1_x_D2_D3());
        expectConflict(new Conflict_D1_x_D2_D3M());
        expectConflict(new Conflict_D1_x_D2M_D3());
        expectDefault(new Default_D1_x_D2M_D3M());
        expectConflict(new Conflict_D1_x_D1MD_D2());
        expectDefault(new Default_D1_x_D1MD_D2M());
        expectDefault(new Default_D1_x_D1M_D2());
        expectMiranda(new Miranda_D1_x_D1M_D2M());

        // Cases that involve a superclass with conflict method in the vtable.
        expectDefault(new Default_D1_D2_x_D2M());
        expectDefault(new Default_D1_D2_x_D1M());
        expectMiranda(new Miranda_D1_D2_x_D1M_D2M());
        expectDefault(new Default_D1_D2_x_D1MD_D2M());
        expectConflict(new Conflict_D1_D2_x_D1M_D3());
        expectDefault(new Default_D1_D2_x_D1M_D3M());
        expectConflict(new Conflict_D1_D2_x_D2M_D3());
        expectDefault(new Default_D1_D2_x_D2M_D3M());
        expectConflict(new Conflict_D1_D2_x_D1MD_D3());
        expectConflict(new Conflict_D1_D2_x_D1MD_D3M());
        expectDefault(new Default_D1_D2_D3M_x_D1MD_D2M());
        expectMiranda(new Miranda_D1_D2_D3M_x_D1M_D2M());
        expectDefault(new Default_D1_D2_x_D1D2D());
        expectMiranda(new Miranda_D1_D2_x_D1D2M());
        expectDefault(new Default_D1_D2_x_AD1D2D());
        expectMiranda(new Miranda_D1_D2_x_AD1D2M());
        expectConflict(new Conflict_D1_D2_D3_x_D1D2D());
        expectDefault(new Default_D1_D2_D3_x_D1D2M());
        expectConflict(new Conflict_D1_D2_D3_x_AD1D2D());
        expectDefault(new Default_D1_D2_D3_x_AD1D2M());
        expectDefault(new Default_D1_D2_D3M_x_D1D2D());
        expectMiranda(new Miranda_D1_D2_D3M_x_D1D2M());
        expectDefault(new Default_D1_D2_D3M_x_AD1D2D());
        expectMiranda(new Miranda_D1_D2_D3M_x_AD1D2M());

        regressionTestB215510819();
    }

    static public void regressionTestB215510819() {
        // The failure to fill IMT correctly would have resulted in calling the wrong method,
        // or triggering a check when interpreting in debug mode.
        Abstract abstractTarget = new B215510819Test();
        String result = abstractTarget.testMethod();
        System.out.println("B215510819 test result: " + result);
    }
}

class Default_D1 extends Super implements Abstract, D1 {}
class Default_D2 extends Super implements Abstract, D2 {}
class Default_D3 extends Super implements Abstract, D3 {}
class Miranda_D1M extends Super implements Abstract, D1M {}
class Miranda_D2M extends Super implements Abstract, D2M {}
class Miranda_D3M extends Super implements Abstract, D3M {}
class Conflict_D1_D2 extends Super implements Abstract, D1, D2 {}
class Default_D1_D2M extends Super implements Abstract, D1, D2M {}
class Default_D1M_D2 extends Super implements Abstract, D1M, D2 {}
class Miranda_D1M_D2M extends Super implements Abstract, D1M, D2M {}
class Conflict_D1_D2_D3 extends Super implements Abstract, D1, D2, D3 {}
class Conflict_D1_D2_D3M extends Super implements Abstract, D1, D2, D3M {}
class Conflict_D1_D2M_D3 extends Super implements Abstract, D1, D2M, D3 {}
class Default_D1_D2M_D3M extends Super implements Abstract, D1, D2M, D3M {}
class Conflict_D1M_D2_D3 extends Super implements Abstract, D1M, D2, D3 {}
class Default_D1M_D2_D3M extends Super implements Abstract, D1M, D2, D3M {}
class Default_D1M_D2M_D3 extends Super implements Abstract, D1M, D2M, D3 {}
class Miranda_D1M_D2M_D3M extends Super implements Abstract, D1M, D2M, D3M {}

class Miranda_D1D2M extends Super implements D1D2M {}
class Default_D1D2D extends Super implements D1D2D {}
class Miranda_AD1D2M extends Super implements AD1D2M {}
class Default_AD1D2D extends Super implements AD1D2D {}

class Default_D2_D1_D2M extends Super implements Abstract, D1, D2M {}
class Miranda_D2_D1M_D2M extends Super implements Abstract, D1M, D2M {}

class Default_D1M_x_D2 extends Miranda_D1M implements D2 {}
class Miranda_D1M_x_D2M extends Miranda_D1M implements D2M {}
class Conflict_D1M_x_D2_D3 extends Miranda_D1M implements D2, D3 {}
class Default_D1M_x_D2_D3M extends Miranda_D1M implements D2, D3M {}
class Default_D1M_x_D2M_D3 extends Miranda_D1M implements D2M, D3 {}
class Miranda_D1M_x_D2M_D3M extends Miranda_D1M implements D2M, D3M {}

class Conflict_D1_x_D2 extends Default_D1 implements D2 {}
class Default_D1_x_D2M extends Default_D1 implements D2M {}
class Default_D1_x_D1MD extends Default_D1 implements D1MD {}
class Miranda_D1_x_D1M extends Default_D1 implements D1M {}
class Conflict_D1_x_D2_D3 extends Default_D1 implements D2, D3 {}
class Conflict_D1_x_D2_D3M extends Default_D1 implements D2, D3M {}
class Conflict_D1_x_D2M_D3 extends Default_D1 implements D2M, D3 {}
class Default_D1_x_D2M_D3M extends Default_D1 implements D2M, D3M {}
class Conflict_D1_x_D1MD_D2 extends Default_D1 implements D1MD, D2 {}
class Default_D1_x_D1MD_D2M extends Default_D1 implements D1MD, D2M {}
class Default_D1_x_D1M_D2 extends Default_D1 implements D1M, D2 {}
class Miranda_D1_x_D1M_D2M extends Default_D1 implements D1M, D2M {}

class Default_D1_D2_x_D2M extends Conflict_D1_D2 implements D2M {}
class Default_D1_D2_x_D1M extends Conflict_D1_D2 implements D1M {}
class Miranda_D1_D2_x_D1M_D2M extends Conflict_D1_D2 implements D1M, D2M {}
class Default_D1_D2_x_D1MD_D2M extends Conflict_D1_D2 implements D1MD, D2M {}
class Conflict_D1_D2_x_D1M_D3 extends Conflict_D1_D2 implements D1M, D3 {}
class Default_D1_D2_x_D1M_D3M extends Conflict_D1_D2 implements D1M, D3M {}
class Conflict_D1_D2_x_D2M_D3 extends Conflict_D1_D2 implements D2M, D3 {}
class Default_D1_D2_x_D2M_D3M extends Conflict_D1_D2 implements D2M, D3M {}
class Conflict_D1_D2_x_D1MD_D3 extends Conflict_D1_D2 implements D1MD, D3 {}
class Conflict_D1_D2_x_D1MD_D3M extends Conflict_D1_D2 implements D1MD, D3M {}
class Default_D1_D2_D3M_x_D1MD_D2M extends Conflict_D1_D2_D3M implements D1MD, D2M {}
class Miranda_D1_D2_D3M_x_D1M_D2M extends Conflict_D1_D2_D3M implements D1M, D2M {}
class Miranda_D1_D2_x_D1D2M extends Conflict_D1_D2 implements D1D2M {}
class Default_D1_D2_x_D1D2D extends Conflict_D1_D2 implements D1D2D {}
class Miranda_D1_D2_x_AD1D2M extends Conflict_D1_D2 implements AD1D2M {}
class Default_D1_D2_x_AD1D2D extends Conflict_D1_D2 implements AD1D2D {}
class Default_D1_D2_D3_x_D1D2M extends Conflict_D1_D2_D3 implements D1D2M {}
class Conflict_D1_D2_D3_x_D1D2D extends Conflict_D1_D2_D3 implements D1D2D {}
class Default_D1_D2_D3_x_AD1D2M extends Conflict_D1_D2_D3 implements AD1D2M {}
class Conflict_D1_D2_D3_x_AD1D2D extends Conflict_D1_D2_D3 implements AD1D2D {}
class Miranda_D1_D2_D3M_x_D1D2M extends Conflict_D1_D2_D3M implements D1D2M {}
class Default_D1_D2_D3M_x_D1D2D extends Conflict_D1_D2_D3M implements D1D2D {}
class Miranda_D1_D2_D3M_x_AD1D2M extends Conflict_D1_D2_D3M implements AD1D2M {}
class Default_D1_D2_D3M_x_AD1D2D extends Conflict_D1_D2_D3M implements AD1D2D {}

interface B215510819Iface {
    // The IMT size is currently 43 and we want to cover all 43 indexes with non-copied
    // implementations. The IMT index for abstract methods is calculated with a hash that
    // includes the method name, so 43 consecutive characters in the method name would be best.
    // (The fact that the name hash is multiplied by 16 is OK because the size of the IMT is a
    // prime number and thus GCD(16, 43) = 1.) However, we do not have a contiguous range of 43
    // valid characters, so we need to rely on the `hash % 43` to mask out the difference when
    // we use `0`..`5` between `Z` and `a`. ('Z' + 1 - 43 = '0' and '5' + 1 + 43 = 'a'.)
    String method_A();
    String method_B();
    String method_C();
    String method_D();
    String method_E();
    String method_F();
    String method_G();
    String method_H();
    String method_I();
    String method_J();
    String method_K();
    String method_L();
    String method_M();
    String method_N();
    String method_O();
    String method_P();
    String method_Q();
    String method_R();
    String method_S();
    String method_T();
    String method_U();
    String method_V();
    String method_W();
    String method_X();
    String method_Y();
    String method_Z();
    String method_0();
    String method_1();
    String method_2();
    String method_3();
    String method_4();
    String method_5();
    String method_a();
    String method_b();
    String method_c();
    String method_d();
    String method_e();
    String method_f();
    String method_g();
    String method_h();
    String method_i();
    String method_j();
    String method_k();
}
// Note: Marked as abstract to avoid IMT table in this class.
abstract class B215510819Base extends Default_D1 implements B215510819Iface {
    public String method_A() { return "B215510819 - wrong method_A!"; }
    public String method_B() { return "B215510819 - wrong method_B!"; }
    public String method_C() { return "B215510819 - wrong method_C!"; }
    public String method_D() { return "B215510819 - wrong method_D!"; }
    public String method_E() { return "B215510819 - wrong method_E!"; }
    public String method_F() { return "B215510819 - wrong method_F!"; }
    public String method_G() { return "B215510819 - wrong method_G!"; }
    public String method_H() { return "B215510819 - wrong method_H!"; }
    public String method_I() { return "B215510819 - wrong method_I!"; }
    public String method_J() { return "B215510819 - wrong method_J!"; }
    public String method_K() { return "B215510819 - wrong method_K!"; }
    public String method_L() { return "B215510819 - wrong method_L!"; }
    public String method_M() { return "B215510819 - wrong method_M!"; }
    public String method_N() { return "B215510819 - wrong method_N!"; }
    public String method_O() { return "B215510819 - wrong method_O!"; }
    public String method_P() { return "B215510819 - wrong method_P!"; }
    public String method_Q() { return "B215510819 - wrong method_Q!"; }
    public String method_R() { return "B215510819 - wrong method_R!"; }
    public String method_S() { return "B215510819 - wrong method_S!"; }
    public String method_T() { return "B215510819 - wrong method_T!"; }
    public String method_U() { return "B215510819 - wrong method_U!"; }
    public String method_V() { return "B215510819 - wrong method_V!"; }
    public String method_W() { return "B215510819 - wrong method_W!"; }
    public String method_X() { return "B215510819 - wrong method_X!"; }
    public String method_Y() { return "B215510819 - wrong method_Y!"; }
    public String method_Z() { return "B215510819 - wrong method_Z!"; }
    public String method_0() { return "B215510819 - wrong method_0!"; }
    public String method_1() { return "B215510819 - wrong method_1!"; }
    public String method_2() { return "B215510819 - wrong method_2!"; }
    public String method_3() { return "B215510819 - wrong method_3!"; }
    public String method_4() { return "B215510819 - wrong method_4!"; }
    public String method_5() { return "B215510819 - wrong method_5!"; }
    public String method_a() { return "B215510819 - wrong method_a!"; }
    public String method_b() { return "B215510819 - wrong method_b!"; }
    public String method_c() { return "B215510819 - wrong method_c!"; }
    public String method_d() { return "B215510819 - wrong method_d!"; }
    public String method_e() { return "B215510819 - wrong method_e!"; }
    public String method_f() { return "B215510819 - wrong method_f!"; }
    public String method_g() { return "B215510819 - wrong method_g!"; }
    public String method_h() { return "B215510819 - wrong method_h!"; }
    public String method_i() { return "B215510819 - wrong method_i!"; }
    public String method_j() { return "B215510819 - wrong method_j!"; }
    public String method_k() { return "B215510819 - wrong method_k!"; }
}
// Regression test for bug 215510819 where we failed to properly fill the IMT table
// when there were no new methods or interfaces and the superclass did not have an IMT
// table, so we filled the IMT from the superclass IfTable and erroneously ignored
// copied implementation methods in the process. Thus calls that should go to copied
// methods via an IMT conflict resolution trampoline would just end up in unrelated
// concrete method when called from compiled code or from interpreter in release mode.
// In debug mode, interpreter would fail a debug check.
class B215510819Test extends B215510819Base {}  // No new interfaces or virtuals.
