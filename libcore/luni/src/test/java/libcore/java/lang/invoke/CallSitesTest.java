/*
 * Copyright (C) 2017 The Android Open Source Project
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

package libcore.java.lang.invoke;

import junit.framework.TestCase;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;
import java.lang.invoke.WrongMethodTypeException;

import static java.lang.invoke.MethodHandles.Lookup.*;

public class CallSitesTest extends TestCase {
    public void test_ConstantCallSiteConstructorNullMethodHandle() throws Throwable {
        try {
            ConstantCallSite site = new ConstantCallSite((MethodHandle) null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    private static MethodHandle methodHandleForAdd2() throws Throwable {
        final MethodType mt = MethodType.methodType(int.class, int.class, int.class);
        return MethodHandles.lookup().findStatic(CallSitesTest.class, "add2", mt);
    }

    public void test_ConstantCallSite() throws Throwable {
        final MethodHandle mh = methodHandleForAdd2();
        ConstantCallSite site = new ConstantCallSite(mh);
        int n = (int) site.dynamicInvoker().invokeExact(7, 37);
        assertEquals(44, n);
        try {
            site.setTarget(mh);
            fail();
        } catch (UnsupportedOperationException e) {
        }
    }

    static class OurConstantCallSite extends ConstantCallSite {
        OurConstantCallSite(MethodType targetType, MethodHandle createTargetHook) throws Throwable {
            super(targetType, createTargetHook);
        }

        static MethodHandle createTargetHook(OurConstantCallSite callSite) throws Throwable {
            final MethodType add2MethodType =
                    MethodType.methodType(int.class, int.class, int.class);
            return MethodHandles.lookup().findStatic(CallSitesTest.class, "add2", add2MethodType);
        }
    }

    public void test_ConstantCallSiteWithHook() throws Throwable {
        final MethodType targetType =
                MethodType.methodType(int.class, int.class, int.class);
        MethodHandle createTargetHook =
                MethodHandles.lookup().findStatic(OurConstantCallSite.class, "createTargetHook",
                                                  MethodType.methodType(MethodHandle.class,
                                                                        OurConstantCallSite.class));
        OurConstantCallSite callSite = new OurConstantCallSite(targetType, createTargetHook);
        int x = (int) callSite.getTarget().invoke(1, 2);
        assertEquals(3, x);
    }

    public void test_MutableCallSiteConstructorNullMethodType() throws Throwable {
        try {
            MutableCallSite callSite = new MutableCallSite((MethodType) null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    public void test_MutableCallSiteConstructorNullMethodHandle() throws Throwable {
        try {
            MutableCallSite callSite = new MutableCallSite((MethodHandle) null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    public void test_MutableCallsiteNoMethodHandle() throws Throwable {
        try {
            MutableCallSite callSite =
                    new MutableCallSite(MethodType.methodType(int.class, int.class, int.class));
            int result = (int) callSite.getTarget().invokeExact(1, 2);
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void test_MutableCallSite() throws Throwable {
        final MethodHandle mh = methodHandleForAdd2();
        final MutableCallSite site = new MutableCallSite(mh);

        // Invocation test
        int n = (int) site.getTarget().invokeExact(7, 37);
        assertEquals(44, n);

        // setTarget() tests
        try {
            final MethodType mt_add3 =
                    MethodType.methodType(int.class, int.class, int.class, int.class);
            final MethodHandle mh_add3 =
                    MethodHandles.lookup().findStatic(CallSitesTest.class, "add3", mt_add3);

            site.setTarget(mh_add3);
            fail();
        } catch (WrongMethodTypeException e) {
        }
        try {
            site.setTarget(null);
            fail();
        } catch (NullPointerException e) {
        }
        final MethodHandle mh_sub2 =
                MethodHandles.lookup().findStatic(CallSitesTest.class, "sub2",
                                                  MethodType.methodType(int.class, int.class, int.class));
        site.setTarget(mh_sub2);
        n = (int) site.getTarget().invokeExact(7, 37);
        assertEquals(-30, n);
    }

    public void test_VolatileCallSiteConstructorNullMethodType() throws Throwable {
        try {
            VolatileCallSite vc = new VolatileCallSite((MethodType) null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    public void test_VolatileCallSiteConstructorNullMethodHandle() throws Throwable {
        try {
            VolatileCallSite vc = new VolatileCallSite((MethodHandle) null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    public void test_VolatileCallsiteNoMethodHandle() throws Throwable {
        try {
            VolatileCallSite vc =
                    new VolatileCallSite(MethodType.methodType(int.class, int.class, int.class));
            int result = (int) vc.getTarget().invokeExact(1, 2);
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void test_VolatileCallSite() throws Throwable {
        final MethodHandle mh = methodHandleForAdd2();
        final VolatileCallSite site = new VolatileCallSite(mh);

        // Invocation test
        int n = (int) site.getTarget().invokeExact(7, 37);
        assertEquals(44, n);

        // setTarget() tests
        try {
            final MethodType mt_add3 =
                    MethodType.methodType(int.class, int.class, int.class, int.class);
            final MethodHandle mh_add3 =
                    MethodHandles.lookup().findStatic(CallSitesTest.class, "add3", mt_add3);
            site.setTarget(mh_add3);
            fail();
        } catch (WrongMethodTypeException e) {
        }
        try {
            site.setTarget(null);
            fail();
        } catch (NullPointerException e) {
        }

        // One last invocation test
        final MethodHandle mh_sub2 =
                MethodHandles.lookup().findStatic(CallSitesTest.class, "sub2",
                                                  MethodType.methodType(int.class, int.class, int.class));
        site.setTarget(mh_sub2);
        n = (int) site.getTarget().invokeExact(7, 37);
        assertEquals(-30, n);
    }

    public void test_EarlyBoundMutableCallSite() throws Throwable {
        final MethodType type = MethodType.methodType(int.class, int.class, int.class);
        final MethodHandle add2 =
                MethodHandles.lookup().findStatic(CallSitesTest.class, "add2", type);
        MutableCallSite site = new MutableCallSite(type);
        commonMutableCallSitesTest(site, add2);
    }

    public void test_EarlyBoundVolatileCallSite() throws Throwable {
        final MethodType type = MethodType.methodType(int.class, int.class, int.class);
        final MethodHandle add2 =
                MethodHandles.lookup().findStatic(CallSitesTest.class, "add2", type);
        VolatileCallSite site = new VolatileCallSite(type);
        commonMutableCallSitesTest(site, add2);
    }

    public void test_LateBoundMutableCallSite() throws Throwable {
        final MethodType type = MethodType.methodType(int.class, int.class, int.class);
        MutableCallSite site = new MutableCallSite(type);
        assertEquals(type, site.type());
        try {
            int fake = (int) site.getTarget().invokeExact(1, 1);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("uninitialized call site", e.getMessage());
        }
        final MethodHandle add2 =
                MethodHandles.lookup().findStatic(CallSitesTest.class, "add2", type);
        site.setTarget(add2);
        commonMutableCallSitesTest(site, add2);
    }

    public void test_LateBoundVolatileCallSite() throws Throwable {
        final MethodType type = MethodType.methodType(int.class, int.class, int.class);
        VolatileCallSite site = new VolatileCallSite(type);
        assertEquals(type, site.type());
        try {
            int fake = (int) site.getTarget().invokeExact(1, 1);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("uninitialized call site", e.getMessage());
        }
        final MethodHandle add2 =
                MethodHandles.lookup().findStatic(CallSitesTest.class, "add2", type);
        site.setTarget(add2);
        commonMutableCallSitesTest(site, add2);
    }

    private static void commonMutableCallSitesTest(CallSite site,
                                                   MethodHandle firstTarget) throws Throwable {
        site.setTarget(firstTarget);
        site.setTarget(firstTarget);

        int x = (int) firstTarget.invokeExact(2, 6);
        assertEquals(8, x);

        int y = (int) site.getTarget().invokeExact(2, 6);
        assertEquals(8, y);

        int z = (int) site.dynamicInvoker().invokeExact(2, 6);
        assertEquals(8, z);

        try {
            site.setTarget(null);
            fail();
        } catch (NullPointerException e) {
        }

        final MethodHandle other = MethodHandles.lookup().findStatic(
            CallSitesTest.class, "add3",
            MethodType.methodType(int.class, int.class, int.class, int.class));
        try {
            site.setTarget(other);
            fail();
        } catch (WrongMethodTypeException e) {
        }
        assertEquals(firstTarget, site.getTarget());

        final MethodHandle sub2 =
                MethodHandles.lookup().findStatic(CallSitesTest.class, "sub2", firstTarget.type());
        site.setTarget(sub2);
        assertEquals(sub2, site.getTarget());
        assertEquals(100, (int) site.dynamicInvoker().invokeExact(147, 47));
    }

    public static int add2(int x, int y) {
        return x + y;
    }

    public static int add3(int x, int y, int z) {
        return x + y + z;
    }

    public static int sub2(int x, int y) {
        return x - y;
    }
}

