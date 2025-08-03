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

import dalvik.system.PathClassLoader;

public class Main {
    static String DEX_FILE = System.getenv("DEX_LOCATION") + "/833-background-verification.jar";
    static String DEX_FILE_EX = System.getenv("DEX_LOCATION") + "/833-background-verification-ex.jar";

    public static void main(String[] args) throws Exception {
      runTest(new UnknownLoader(DEX_FILE_EX));
      runTest(new PathClassLoader(DEX_FILE_EX, new UnknownLoader(DEX_FILE)));
    }

    public static void runTest(ClassLoader loader) throws Exception {
      // Use the class that will be verified the last by the background verifier. This maximises our
      // chance of hitting the race. We load it now so that the background verifier can also load
      // it.
      Class<?> clsa = loader.loadClass("Classa");
      Class<?> cls1 = Class.forName("Class1", true, loader);
      // Give some time for the background verification to verify "Classa".
      Thread.sleep(1000);
      clsa.newInstance();
    }
}

class UnknownLoader extends PathClassLoader {
  public UnknownLoader(String dexFile) {
    super(dexFile, Object.class.getClassLoader());
  }
}
