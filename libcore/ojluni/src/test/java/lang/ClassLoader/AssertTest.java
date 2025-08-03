/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 4290640 4785473
 * @build package1.Class1 package2.Class2 package1.package3.Class3 Assert
 * @run main/othervm Assert
 * @summary Test the assertion facility
 * @author Mike McCloskey
 * @key randomness
 */

// Android-changed: Adapt structure and expectations to Android.
// Android does not use AssertionStatuses, so this test changes expectations to reflect that.
// Furthermore, the test structure is simplified to avoid relying on args[] and use the
// org.testng.annotations.Test package instead of a main() method.
package test.java.lang.ClassLoader;

import test.java.lang.ClassLoader.package1.*;
import test.java.lang.ClassLoader.package2.*;
import test.java.lang.ClassLoader.package1.package3.*;
import java.util.Random;

import org.testng.annotations.Test;

public class AssertTest {

    private static Class1 testClass1;
    private static Class2 testClass2;
    private static Class3 testClass3;
    private static Random generator = new Random();

    /**
     * AssertionStatuses don't actually do anything on Android, this test proves as much.
     */
    @Test
    public void testAssert() {
        // Switch values: 0=don't touch, 1=off, 2 = on
        int[] switches = new int[7];
        for(int x=0; x<10; x++) {
            int temp = generator.nextInt(2187);
            for (int i = 0; i < 7; i++) {
                switches[i] = temp % 3;
                temp = temp / 3;
            }
            SetAssertionSwitches(switches);
            ConstructClassTree();
            TestClassTree();
        }


        // Android-added: Add testing of clearAssertionStatus().
        for(int x=0; x<7; x++) {
            switches[x]=2;
        }
        ClassLoader loader = SetAssertionSwitches(switches);
        loader.clearAssertionStatus(); // Clearing also does nothing
        ConstructClassTree();
        TestClassTree();
    }

    /*
     * Activate/Deactivate the assertions in the tree according to the
     * specified switches.
     */
    private static ClassLoader SetAssertionSwitches(int[] switches) {
        ClassLoader loader = ClassLoader.getSystemClassLoader();

        if (switches[0] != 0)
            loader.setDefaultAssertionStatus(switches[0]==2);
        if (switches[1] != 0)
            loader.setPackageAssertionStatus("package1", switches[1]==2);
        if (switches[2] != 0)
            loader.setPackageAssertionStatus("package2", switches[2]==2);
        if (switches[3] != 0)
            loader.setPackageAssertionStatus("package1.package3", switches[3]==2);
        if (switches[4] != 0)
            loader.setClassAssertionStatus("package1.Class1", switches[4]==2);
        if (switches[5] != 0)
            loader.setClassAssertionStatus("package2.Class2", switches[5]==2);
        if (switches[6] != 0)
            loader.setClassAssertionStatus("package1.package3.Class3", switches[6]==2);
        return loader;
    }

    /*
     * Verify that the assertions are activated or deactivated as specified
     * by the switches.
     */
    private static void TestClassTree() {
        testClass1.testAssert(false);
        Class1.Class11.testAssert(false);
        testClass2.testAssert(false);
        testClass3.testAssert(false);
        Class3.Class31.testAssert(false);

    }

    /*
     * Create the class tree to be tested. Each test run must reload the classes
     * of the tree since assertion status is determined at class load time.
     */
    private static void ConstructClassTree() {
        testClass1 = new Class1();
        testClass2 = new Class2();
        testClass3 = new Class3();
    }

}