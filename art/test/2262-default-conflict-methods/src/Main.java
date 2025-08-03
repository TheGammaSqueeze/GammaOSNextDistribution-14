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
class Main implements Iface, Iface2 {
    public static void main(String[] args) {
        System.loadLibrary(args[0]);
        System.out.println("Create Main instance");
        Main m = new Main();
        System.out.println("Calling functions on concrete Main");
        callMain(m);
    }

    public static void callMain(Main m) {
        System.out.println("Calling non-conflicting function on Main");
        m.test();
        long main_id = GetMethodId(false, Main.class, "test", "()V");
        long iface_id = GetMethodId(false, Iface.class, "test", "()V");
        try {
            long iface2_id = GetMethodId(false, Main.class, "test", "()V");
            System.out.println("Unexpected normal exit from GetMethodId");
        } catch (NoSuchMethodError e) {
            System.out.println("Expected NoSuchMethodError thrown on Iface2");
        }
        if (main_id != iface_id) {
            throw new Error("Default methods have different method ids");
        }
        CallNonvirtual(m, Main.class, main_id);

        System.out.println("Calling conflicting function on Main");
        try {
            m.test_throws();
        } catch (IncompatibleClassChangeError e) {
            System.out.println("Expected ICCE on main");
        }

        long main_throws_id = GetMethodId(false, Main.class, "test_throws", "()V");
        long iface_throws_id = GetMethodId(false, Iface.class, "test_throws", "()V");
        long iface2_throws_id = GetMethodId(false, Iface2.class, "test_throws", "()V");
        if (main_throws_id == iface_throws_id || main_throws_id == iface2_throws_id) {
            System.out.println(
                    "Unexpected: method id of default conflicting matches one of the interface methods");
        }

        try {
            CallNonvirtual(m, Main.class, main_throws_id);
        } catch (IncompatibleClassChangeError e) {
            System.out.println("Expected ICCE on main");
        }
        return;
    }

    private static native long GetMethodId(boolean is_static, Class k, String name, String sig);
    private static native long CallNonvirtual(Object obj, Class k, long methodid);
}
