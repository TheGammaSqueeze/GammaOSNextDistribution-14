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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Consumer;

public class Main {
    static final String DEX_FILE = System.getenv("DEX_LOCATION") + "/141-class-unload-ex.jar";
    static final String LIBRARY_SEARCH_PATH = System.getProperty("java.library.path");
    static String nativeLibraryName;

    public static void main(String[] args) throws Exception {
        nativeLibraryName = args[0];
        Class<?> pathClassLoader = Class.forName("dalvik.system.PathClassLoader");
        if (pathClassLoader == null) {
            throw new AssertionError("Couldn't find path class loader class");
        }
        Constructor<?> constructor =
            pathClassLoader.getDeclaredConstructor(String.class, String.class, ClassLoader.class);
        try {
            testUnloadClass(constructor);
            testUnloadLoader(constructor);
            // Test that we don't unload if we have an instance.
            testNoUnloadInstance(constructor);
            // Test JNI_OnLoad and JNI_OnUnload.
            testLoadAndUnloadLibrary(constructor);
            // Test that stack traces keep the classes live.
            testStackTrace(constructor);
            // Stress test to make sure we dont leak memory.
            stressTest(constructor);
            // Test that the oat files are unloaded.
            testOatFilesUnloaded(getPid());
            // Test that objects keep class loader live for sticky GC.
            testStickyUnload(constructor);
            // Test that copied methods recorded in a stack trace prevents unloading.
            testCopiedMethodInStackTrace(constructor);
            // Test that code preventing unloading holder classes of copied methods recorded in
            // a stack trace does not crash when processing a copied method in the boot class path.
            testCopiedBcpMethodInStackTrace();
            // Test that code preventing unloading holder classes of copied methods recorded in
            // a stack trace does not crash when processing a copied method in an app image.
            testCopiedAppImageMethodInStackTrace();
            // Test that the runtime uses the right allocator when creating conflict methods.
            testConflictMethod(constructor);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private static void testOatFilesUnloaded(int pid) throws Exception {
        System.loadLibrary(nativeLibraryName);
        // Stop the JIT to ensure its threads and work queue are not keeping classes
        // artifically alive.
        stopJit();
        doUnloading();
        System.runFinalization();
        BufferedReader reader = new BufferedReader(new FileReader ("/proc/" + pid + "/maps"));
        String line;
        int count = 0;
        while ((line = reader.readLine()) != null) {
            if (line.contains("141-class-unload-ex.odex") ||
                line.contains("141-class-unload-ex.vdex")) {
                System.out.println(line);
                ++count;
            }
        }
        System.out.println("Number of loaded unload-ex maps " + count);
        startJit();
    }

    private static void stressTest(Constructor<?> constructor) throws Exception {
        for (int i = 0; i <= 100; ++i) {
            setUpUnloadLoader(constructor, false);
            if (i % 10 == 0) {
                Runtime.getRuntime().gc();
            }
        }
    }

    private static void doUnloading() {
      // Do multiple GCs to prevent rare flakiness if some other thread is keeping the
      // classloader live.
      for (int i = 0; i < 5; ++i) {
         Runtime.getRuntime().gc();
      }
    }

    private static void testUnloadClass(Constructor<?> constructor) throws Exception {
        WeakReference<Class> klass = setUpUnloadClassWeak(constructor);
        // No strong references to class loader, should get unloaded.
        doUnloading();
        WeakReference<Class> klass2 = setUpUnloadClassWeak(constructor);
        doUnloading();
        // If the weak reference is cleared, then it was unloaded.
        System.out.println(klass.get());
        System.out.println(klass2.get());
    }

    private static void testUnloadLoader(Constructor<?> constructor) throws Exception {
        WeakReference<ClassLoader> loader = setUpUnloadLoader(constructor, true);
        // No strong references to class loader, should get unloaded.
        doUnloading();
        // If the weak reference is cleared, then it was unloaded.
        System.out.println(loader.get());
    }

    private static void testStackTrace(Constructor<?> constructor) throws Exception {
        Class<?> klass = setUpUnloadClass(constructor);
        WeakReference<Class> weak_klass = new WeakReference(klass);
        Method stackTraceMethod = klass.getDeclaredMethod("generateStackTrace");
        Throwable throwable = (Throwable) stackTraceMethod.invoke(klass);
        stackTraceMethod = null;
        klass = null;
        doUnloading();
        boolean isNull = weak_klass.get() == null;
        System.out.println("class null " + isNull + " " + throwable.getMessage());
    }

    private static void testLoadAndUnloadLibrary(Constructor<?> constructor) throws Exception {
        WeakReference<ClassLoader> loader = setUpLoadLibrary(constructor);
        // No strong references to class loader, should get unloaded.
        doUnloading();
        // If the weak reference is cleared, then it was unloaded.
        System.out.println(loader.get());
    }

    private static Object testNoUnloadHelper(ClassLoader loader) throws Exception {
        Class<?> intHolder = loader.loadClass("IntHolder");
        return intHolder.newInstance();
    }

    static class Pair {
        public Pair(Object o, ClassLoader l) {
            object = o;
            classLoader = new WeakReference<ClassLoader>(l);
        }

        public Object object;
        public WeakReference<ClassLoader> classLoader;
    }

    // Make the method not inline-able to prevent the compiler optimizing away the allocation.
    private static Pair $noinline$testNoUnloadInstanceHelper(Constructor<?> constructor)
            throws Exception {
        ClassLoader loader = (ClassLoader) constructor.newInstance(
                DEX_FILE, LIBRARY_SEARCH_PATH, ClassLoader.getSystemClassLoader());
        Object o = testNoUnloadHelper(loader);
        return new Pair(o, loader);
    }

    private static void testNoUnloadInstance(Constructor<?> constructor) throws Exception {
        Pair p = $noinline$testNoUnloadInstanceHelper(constructor);
        doUnloading();
        boolean isNull = p.classLoader.get() == null;
        System.out.println("loader null " + isNull);
    }

    private static Class<?> setUpUnloadClass(Constructor<?> constructor) throws Exception {
        ClassLoader loader = (ClassLoader) constructor.newInstance(
                DEX_FILE, LIBRARY_SEARCH_PATH, ClassLoader.getSystemClassLoader());
        Class<?> intHolder = loader.loadClass("IntHolder");
        Method getValue = intHolder.getDeclaredMethod("getValue");
        Method setValue = intHolder.getDeclaredMethod("setValue", Integer.TYPE);
        // Make sure we don't accidentally preserve the value in the int holder, the class
        // initializer should be re-run.
        System.out.println((int) getValue.invoke(intHolder));
        setValue.invoke(intHolder, 2);
        System.out.println((int) getValue.invoke(intHolder));
        waitForCompilation(intHolder);
        return intHolder;
    }

    private static Object allocObjectInOtherClassLoader(Constructor<?> constructor)
            throws Exception {
      ClassLoader loader = (ClassLoader) constructor.newInstance(
              DEX_FILE, LIBRARY_SEARCH_PATH, ClassLoader.getSystemClassLoader());
      return loader.loadClass("IntHolder").newInstance();
    }

    // Regression test for public issue 227182.
    private static void testStickyUnload(Constructor<?> constructor) throws Exception {
        String s = "";
        for (int i = 0; i < 10; ++i) {
            s = "";
            // The object is the only thing preventing the class loader from being unloaded.
            Object o = allocObjectInOtherClassLoader(constructor);
            for (int j = 0; j < 1000; ++j) {
                s += j + " ";
            }
            // Make sure the object still has a valid class (hasn't been incorrectly unloaded).
            s += o.getClass().getName();
            o = null;
        }
        System.out.println("Too small " + (s.length() < 1000));
    }

    private static void assertStackTraceContains(Throwable t, String className, String methodName) {
        boolean found = false;
        for (StackTraceElement e : t.getStackTrace()) {
            if (className.equals(e.getClassName()) && methodName.equals(e.getMethodName())) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new Error("Did not find " + className + "." + methodName);
        }
    }

    private static void $noinline$callAllMethods(ConflictIface iface) {
        // Call all methods in the interface to make sure we hit conflicts in the IMT.
        iface.method1();
        iface.method2();
        iface.method3();
        iface.method4();
        iface.method5();
        iface.method6();
        iface.method7();
        iface.method8();
        iface.method9();
        iface.method10();
        iface.method11();
        iface.method12();
        iface.method13();
        iface.method14();
        iface.method15();
        iface.method16();
        iface.method17();
        iface.method18();
        iface.method19();
        iface.method20();
        iface.method21();
        iface.method22();
        iface.method23();
        iface.method24();
        iface.method25();
        iface.method26();
        iface.method27();
        iface.method28();
        iface.method29();
        iface.method30();
        iface.method31();
        iface.method32();
        iface.method33();
        iface.method34();
        iface.method35();
        iface.method36();
        iface.method37();
        iface.method38();
        iface.method39();
        iface.method40();
        iface.method41();
        iface.method42();
        iface.method43();
        iface.method44();
        iface.method45();
        iface.method46();
        iface.method47();
        iface.method48();
        iface.method49();
        iface.method50();
        iface.method51();
        iface.method52();
        iface.method53();
        iface.method54();
        iface.method55();
        iface.method56();
        iface.method57();
        iface.method58();
        iface.method59();
        iface.method60();
        iface.method61();
        iface.method62();
        iface.method63();
        iface.method64();
        iface.method65();
        iface.method66();
        iface.method67();
        iface.method68();
        iface.method69();
        iface.method70();
        iface.method71();
        iface.method72();
        iface.method73();
        iface.method74();
        iface.method75();
        iface.method76();
        iface.method77();
        iface.method78();
        iface.method79();
    }

    private static void $noinline$invokeConflictMethod(Constructor<?> constructor)
            throws Exception {
        ClassLoader loader = (ClassLoader) constructor.newInstance(
                DEX_FILE, LIBRARY_SEARCH_PATH, ClassLoader.getSystemClassLoader());
        Class<?> impl = loader.loadClass("ConflictImpl");
        ConflictIface iface = (ConflictIface) impl.newInstance();
        $noinline$callAllMethods(iface);
    }

    private static void testConflictMethod(Constructor<?> constructor) throws Exception {
        // Load and unload a few class loaders to force re-use of the native memory where we
        // used to allocate the conflict table.
        for (int i = 0; i < 2; i++) {
            $noinline$invokeConflictMethod(constructor);
            doUnloading();
        }
        Class<?> impl = Class.forName("ConflictSuper");
        ConflictIface iface = (ConflictIface) impl.newInstance();
        $noinline$callAllMethods(iface);
    }

    private static void testCopiedMethodInStackTrace(Constructor<?> constructor) throws Exception {
        Throwable t = $noinline$createStackTraceWithCopiedMethod(constructor);
        doUnloading();
        assertStackTraceContains(t, "Iface", "invokeRun");
    }

    private static Throwable $noinline$createStackTraceWithCopiedMethod(Constructor<?> constructor)
            throws Exception {
      ClassLoader loader = (ClassLoader) constructor.newInstance(
              DEX_FILE, LIBRARY_SEARCH_PATH, Main.class.getClassLoader());
      Iface impl = (Iface) loader.loadClass("Impl").newInstance();
      Runnable throwingRunnable = new Runnable() {
          public void run() {
              throw new Error();
          }
      };
      try {
          impl.invokeRun(throwingRunnable);
          System.out.println("UNREACHABLE");
          return null;
      } catch (Error expected) {
          return expected;
      }
    }

    private static void testCopiedBcpMethodInStackTrace() {
        Consumer<Object> consumer = new Consumer<Object>() {
            public void accept(Object o) {
                throw new Error();
            }
        };
        Error err = null;
        try {
            Arrays.asList(new Object[] { new Object() }).iterator().forEachRemaining(consumer);
        } catch (Error expected) {
            err = expected;
        }
        assertStackTraceContains(err, "Main", "testCopiedBcpMethodInStackTrace");
    }

    private static void testCopiedAppImageMethodInStackTrace() throws Exception {
        Iface limpl = (Iface) Class.forName("Impl2").newInstance();
        Runnable throwingRunnable = new Runnable() {
            public void run() {
                throw new Error();
            }
        };
        Error err = null;
        try {
            limpl.invokeRun(throwingRunnable);
        } catch (Error expected) {
            err = expected;
        }
        assertStackTraceContains(err, "Main", "testCopiedAppImageMethodInStackTrace");
    }

    private static WeakReference<Class> setUpUnloadClassWeak(Constructor<?> constructor)
            throws Exception {
        return new WeakReference<Class>(setUpUnloadClass(constructor));
    }

    private static WeakReference<ClassLoader> setUpUnloadLoader(Constructor<?> constructor,
                                                                boolean waitForCompilation)
        throws Exception {
        ClassLoader loader = (ClassLoader) constructor.newInstance(
            DEX_FILE, LIBRARY_SEARCH_PATH, ClassLoader.getSystemClassLoader());
        Class<?> intHolder = loader.loadClass("IntHolder");
        Method setValue = intHolder.getDeclaredMethod("setValue", Integer.TYPE);
        setValue.invoke(intHolder, 2);
        if (waitForCompilation) {
            waitForCompilation(intHolder);
        }
        return new WeakReference(loader);
    }

    private static void waitForCompilation(Class<?> intHolder) throws Exception {
      // Load the native library so that we can call waitForCompilation.
      Method loadLibrary = intHolder.getDeclaredMethod("loadLibrary", String.class);
      loadLibrary.invoke(intHolder, nativeLibraryName);
      // Wait for JIT compilation to finish since the async threads may prevent unloading.
      Method waitForCompilation = intHolder.getDeclaredMethod("waitForCompilation");
      waitForCompilation.invoke(intHolder);
    }

    private static WeakReference<ClassLoader> setUpLoadLibrary(Constructor<?> constructor)
        throws Exception {
        ClassLoader loader = (ClassLoader) constructor.newInstance(
            DEX_FILE, LIBRARY_SEARCH_PATH, ClassLoader.getSystemClassLoader());
        Class<?> intHolder = loader.loadClass("IntHolder");
        Method loadLibrary = intHolder.getDeclaredMethod("loadLibrary", String.class);
        loadLibrary.invoke(intHolder, nativeLibraryName);
        waitForCompilation(intHolder);
        return new WeakReference(loader);
    }

    private static int getPid() throws Exception {
        return Integer.parseInt(new File("/proc/self").getCanonicalFile().getName());
    }

    public static native void stopJit();
    public static native void startJit();
}
