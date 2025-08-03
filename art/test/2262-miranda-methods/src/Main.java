/*
 * Copyright (C) 2015 The Android Open Source Project
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
class Main extends Impl implements Iface {
    public static void main(String[] args) {
        System.loadLibrary(args[0]);
        System.out.println("Create Main instance");
        Main m = new Main();
        callMain(m);
    }

    public static void callMain(Main m) {
        System.out.println("Test method with concrete implementation");
        m.test_correct();
        System.out.println("Test method with concrete implementation via JNI call");
        long iface_id = GetMethodId(false, Iface.class, "test_correct", "()V");
        long main_id = GetMethodId(false, Main.class, "test_correct", "()V");
        long impl_id = GetMethodId(false, Impl.class, "test_correct", "()V");
        if (iface_id != main_id) {
            System.out.println("Abstract interface and Main have different method ids");
        } else {
            System.out.println("Unexpected: Abstract interface and Main have same method ids");
        }
        CallNonvirtual(m, Main.class, main_id);

        System.out.println("Test method with no concrete implementation");
        try {
            m.test_throws();
        } catch (AbstractMethodError e) {
            System.out.println("Expected AME Thrown on Main");
        }
        long iface_throws_id = GetMethodId(false, Iface.class, "test_throws", "()V");
        long main_throws_id = GetMethodId(false, Main.class, "test_throws", "()V");
        try {
            long id = GetMethodId(false, Impl.class, "test_throws", "()V");
        } catch (NoSuchMethodError e) {
            System.out.println("Expected NoSuchMethodError on Main");
        }
        if (iface_throws_id == main_throws_id) {
            System.out.println("Abstract interface and Main have same method ids");
        } else {
            System.out.println("Unexpected: Abstract interface and Main have different method ids");
        }

        try {
            CallNonvirtual(m, Main.class, main_throws_id);
        } catch (AbstractMethodError e) {
            System.out.println("Expected AME Thrown on Main via JNI call");
        }
        return;
    }

    private static native long GetMethodId(boolean is_static, Class k, String name, String sig);
    private static native long CallNonvirtual(Object obj, Class k, long methodid);
}
