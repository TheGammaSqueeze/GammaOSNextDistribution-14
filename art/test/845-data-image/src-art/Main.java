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

import dalvik.system.DexFile;
import dalvik.system.VMRuntime;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.concurrent.CyclicBarrier;

// This class helps testing that we don't mark `InheritsBigInteger` as initialized,
// given we do not expect `BigInteger` to be initialized in the boot image.
class InheritsBigInteger extends BigInteger {
  InheritsBigInteger(String value) {
    super(value);
  }
}

class SuperClass {}

class ClassWithStatics extends SuperClass {
  public static final String STATIC_STRING = "foo";
  public static final int STATIC_INT = 42;
}

class ClassWithStaticType {
  public static final Class<?> STATIC_TYPE = Object.class;
}

// Add an interface for testing generating classes and interfaces.
interface Itf {
  public int someMethod();
  public default int someDefaultMethod() { return 42; }
}

// Add a second interface with many methods to force a conflict in the IMT. We want a second
// interface to make sure `Itf` gets entries with the imt_unimplemented_method runtime method.
interface Itf2 {
  default int defaultMethod1() { return 1; }
  default int defaultMethod2() { return 2; }
  default int defaultMethod3() { return 3; }
  default int defaultMethod4() { return 4; }
  default int defaultMethod5() { return 5; }
  default int defaultMethod6() { return 6; }
  default int defaultMethod7() { return 7; }
  default int defaultMethod8() { return 8; }
  default int defaultMethod9() { return 9; }
  default int defaultMethod10() { return 10; }
  default int defaultMethod11() { return 11; }
  default int defaultMethod12() { return 12; }
  default int defaultMethod13() { return 13; }
  default int defaultMethod14() { return 14; }
  default int defaultMethod15() { return 15; }
  default int defaultMethod16() { return 16; }
  default int defaultMethod17() { return 17; }
  default int defaultMethod18() { return 18; }
  default int defaultMethod19() { return 19; }
  default int defaultMethod20() { return 20; }
  default int defaultMethod21() { return 21; }
  default int defaultMethod22() { return 22; }
  default int defaultMethod23() { return 23; }
  default int defaultMethod24() { return 24; }
  default int defaultMethod25() { return 25; }
  default int defaultMethod26() { return 26; }
  default int defaultMethod27() { return 27; }
  default int defaultMethod28() { return 28; }
  default int defaultMethod29() { return 29; }
  default int defaultMethod30() { return 30; }
  default int defaultMethod31() { return 31; }
  default int defaultMethod32() { return 32; }
  default int defaultMethod33() { return 33; }
  default int defaultMethod34() { return 34; }
  default int defaultMethod35() { return 35; }
  default int defaultMethod36() { return 36; }
  default int defaultMethod37() { return 37; }
  default int defaultMethod38() { return 38; }
  default int defaultMethod39() { return 39; }
  default int defaultMethod40() { return 40; }
  default int defaultMethod41() { return 41; }
  default int defaultMethod42() { return 42; }
  default int defaultMethod43() { return 43; }
  default int defaultMethod44() { return 44; }
  default int defaultMethod45() { return 45; }
  default int defaultMethod46() { return 46; }
  default int defaultMethod47() { return 47; }
  default int defaultMethod48() { return 48; }
  default int defaultMethod49() { return 49; }
  default int defaultMethod50() { return 50; }
  default int defaultMethod51() { return 51; }
}

class Itf2Impl implements Itf2 {
}

class ClassWithDefaultConflict implements IfaceWithSayHi, IfaceWithSayHiAtRuntime {
}

public class Main implements Itf {
  static String myString = "MyString";

  static class MyThread extends Thread {
    CyclicBarrier barrier;

    public MyThread(CyclicBarrier barrier) {
      this.barrier = barrier;
    }
    public void run() {
      try {
        synchronized (Main.myString) {
          barrier.await();
          barrier.reset();
          // Infinite wait.
          barrier.await();
        }
      } catch (Exception e) {
        throw new Error(e);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    System.loadLibrary(args[0]);

    // Register the dex file so that the runtime can pick up which
    // dex file to compile for the image.
    File file = null;
    try {
      file = createTempFile();
      String codePath = DEX_LOCATION + "/845-data-image.jar";
      VMRuntime.registerAppInfo(
          "test.app",
          file.getPath(),
          file.getPath(),
          new String[] {codePath},
          VMRuntime.CODE_PATH_TYPE_PRIMARY_APK);
    } finally {
      if (file != null) {
        file.delete();
      }
    }

    if (!hasOatFile() || !hasImage()) {
      // We only generate an app image if there is at least a vdex file and a boot image.
      return;
    }

    if (args.length == 2 && "--second-run".equals(args[1])) {
      DexFile.OptimizationInfo info = VMRuntime.getBaseApkOptimizationInfo();
      if (!info.isOptimized() && !isInImageSpace(Main.class)) {
        throw new Error("Expected image to be loaded");
      }
    }

    runClassTests();

    // Test that we emit an empty lock word. If we are not, then this synchronized call here would
    // block on a run with the runtime image.
    synchronized (myString) {
    }

    // Create a thread that makes sure `myString` is locked while the main thread is generating
    // the runtime image.
    CyclicBarrier barrier = new CyclicBarrier(2);
    Thread t = new MyThread(barrier);
    t.setDaemon(true);
    t.start();
    barrier.await();

    VMRuntime runtime = VMRuntime.getRuntime();
    runtime.notifyStartupCompleted();

    String filter = getCompilerFilter(Main.class);
    if ("speed-profile".equals(filter) || "speed".equals(filter)) {
      // We only generate an app image for filters that don't compile.
      return;
    }

    String instructionSet = VMRuntime.getCurrentInstructionSet();
    // Wait for the file to be generated.
    File image = new File(DEX_LOCATION + "/" + instructionSet + "/845-data-image.art");
    while (!image.exists()) {
      Thread.yield();
    }
  }

  static class MyProxy implements InvocationHandler {

    private Object obj;

    public static Object newInstance(Object obj) {
        return java.lang.reflect.Proxy.newProxyInstance(
            obj.getClass().getClassLoader(),
            obj.getClass().getInterfaces(),
            new MyProxy(obj));
    }

    private MyProxy(Object obj) {
        this.obj = obj;
    }

    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
      return m.invoke(obj, args);
    }
  }

  public static Itf itf = new Main();
  public static Itf2 itf2 = new Itf2Impl();
  public static ClassWithStatics statics = new ClassWithStatics();
  public static ClassWithStaticType staticType = new ClassWithStaticType();
  public static ClassWithDefaultConflict defaultConflict = new ClassWithDefaultConflict();

  public static void runClassTests() {
    // Test Class.getName, app images expect all strings to have hash codes.
    assertEquals("Main", Main.class.getName());

    // Basic tests for invokes with a copied method.
    assertEquals(3, new Main().someMethod());
    assertEquals(42, new Main().someDefaultMethod());

    assertEquals(3, itf.someMethod());
    assertEquals(42, itf.someDefaultMethod());

    // Test with a proxy class.
    Itf foo = (Itf) MyProxy.newInstance(new Main());
    assertEquals(3, foo.someMethod());
    assertEquals(42, foo.someDefaultMethod());

    // Test with array classes.
    assertEquals("[LMain;", Main[].class.getName());
    assertEquals("[[LMain;", Main[][].class.getName());

    assertEquals("[LMain;", new Main[4].getClass().getName());
    assertEquals("[[LMain;", new Main[1][2].getClass().getName());

    Main array[] = new Main[] { new Main() };
    assertEquals("[LMain;", array.getClass().getName());

    assertEquals(Object[][][][].class, Array.newInstance(Object.class, 0, 0, 0, 0).getClass());
    assertEquals("int", int.class.getName());
    assertEquals("[I", int[].class.getName());

    assertEquals("foo", statics.STATIC_STRING);
    assertEquals(42, statics.STATIC_INT);

    assertEquals(Object.class, staticType.STATIC_TYPE);

    // Call all interface methods to trigger the creation of a imt conflict method.
    itf2.defaultMethod1();
    itf2.defaultMethod2();
    itf2.defaultMethod3();
    itf2.defaultMethod4();
    itf2.defaultMethod5();
    itf2.defaultMethod6();
    itf2.defaultMethod7();
    itf2.defaultMethod8();
    itf2.defaultMethod9();
    itf2.defaultMethod10();
    itf2.defaultMethod11();
    itf2.defaultMethod12();
    itf2.defaultMethod13();
    itf2.defaultMethod14();
    itf2.defaultMethod15();
    itf2.defaultMethod16();
    itf2.defaultMethod17();
    itf2.defaultMethod18();
    itf2.defaultMethod19();
    itf2.defaultMethod20();
    itf2.defaultMethod21();
    itf2.defaultMethod22();
    itf2.defaultMethod23();
    itf2.defaultMethod24();
    itf2.defaultMethod25();
    itf2.defaultMethod26();
    itf2.defaultMethod27();
    itf2.defaultMethod28();
    itf2.defaultMethod29();
    itf2.defaultMethod30();
    itf2.defaultMethod31();
    itf2.defaultMethod32();
    itf2.defaultMethod33();
    itf2.defaultMethod34();
    itf2.defaultMethod35();
    itf2.defaultMethod36();
    itf2.defaultMethod37();
    itf2.defaultMethod38();
    itf2.defaultMethod39();
    itf2.defaultMethod40();
    itf2.defaultMethod41();
    itf2.defaultMethod42();
    itf2.defaultMethod43();
    itf2.defaultMethod44();
    itf2.defaultMethod45();
    itf2.defaultMethod46();
    itf2.defaultMethod47();
    itf2.defaultMethod48();
    itf2.defaultMethod49();
    itf2.defaultMethod50();
    itf2.defaultMethod51();

    InheritsBigInteger bigInteger = new InheritsBigInteger("42");
    assertEquals("42", bigInteger.toString());
  }

  private static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new Error("Expected " + expected + ", got " + actual);
    }
  }

  private static void assertEquals(Object expected, Object actual) {
    if (!expected.equals(actual)) {
      throw new Error("Expected \"" + expected + "\", got \"" + actual + "\"");
    }
  }

  public int someMethod() {
    return 3;
  }

  private static native boolean hasOatFile();
  private static native boolean hasImage();
  private static native String getCompilerFilter(Class<?> cls);
  private static native boolean isInImageSpace(Class<?> cls);

  private static final String TEMP_FILE_NAME_PREFIX = "temp";
  private static final String TEMP_FILE_NAME_SUFFIX = "-file";
  private static final String DEX_LOCATION = System.getenv("DEX_LOCATION");

  private static File createTempFile() throws Exception {
    try {
      return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
    } catch (IOException e) {
      System.setProperty("java.io.tmpdir", "/data/local/tmp");
      try {
        return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
      } catch (IOException e2) {
        System.setProperty("java.io.tmpdir", "/sdcard");
        return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
      }
    }
  }
}
