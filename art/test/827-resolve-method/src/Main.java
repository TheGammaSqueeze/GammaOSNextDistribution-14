/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class Main {
  public static void main(String[] args) throws Exception {
    System.loadLibrary(args[0]);
    MyLocalClass.callMethod();
    pkg1.SubClass.callMethod();
    loadClass();
  }

  public static void loadClass() throws Exception {
    Class<?> pathClassLoader = Class.forName("dalvik.system.PathClassLoader");
    if (pathClassLoader == null) {
      throw new AssertionError("Couldn't find path class loader class");
    }
    Constructor<?> constructor =
       pathClassLoader.getDeclaredConstructor(String.class, String.class, ClassLoader.class);
    ClassLoader loader = (ClassLoader) constructor.newInstance(
        DEX_FILE, LIBRARY_SEARCH_PATH, ClassLoader.getSystemClassLoader());
    Class<?> otherClass = loader.loadClass("Caller");

    // Run the method in interpreter / AOT mode.
    otherClass.getMethod("doCall").invoke(null);

    // Run the method  in JIT mode.
    ensureJitCompiled(otherClass, "doCall");
    otherClass.getMethod("doCall").invoke(null);
  }

  private static final String LIBRARY_SEARCH_PATH = System.getProperty("java.library.path");
  private static final String DEX_LOCATION = System.getenv("DEX_LOCATION");
  private static final String DEX_FILE =
      new File(DEX_LOCATION, "827-resolve-method-ex.jar").getAbsolutePath();

  private static native void ensureJitCompiled(Class<?> cls, String methodName);
}

class MyLocalClass extends pkg1.SubClass {
}
