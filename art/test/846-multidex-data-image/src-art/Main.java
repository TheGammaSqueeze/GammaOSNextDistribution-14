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

public class Main {
  public static void main(String[] args) throws Exception {
    System.loadLibrary(args[0]);

    // Register the dex file so that the runtime can pick up which
    // dex file to compile for the image.
    File file = null;
    try {
      file = createTempFile();
      String codePath = DEX_LOCATION + "/846-multidex-data-image.jar";
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

    VMRuntime runtime = VMRuntime.getRuntime();
    runtime.notifyStartupCompleted();

    String filter = getCompilerFilter(Main.class);
    if ("speed-profile".equals(filter) || "speed".equals(filter)) {
      // We only generate an app image for filters that don't compile.
      return;
    }

    String instructionSet = VMRuntime.getCurrentInstructionSet();
    // Wait for the file to be generated.
    File image = new File(DEX_LOCATION + "/" + instructionSet + "/846-multidex-data-image.art");
    while (!image.exists()) {
      Thread.yield();
    }

    // Test that we can load a class from the other dex file. We do this after creating the image to
    // check that the runtime can deal with a missing dex cache.
    Class.forName("Foo");
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
