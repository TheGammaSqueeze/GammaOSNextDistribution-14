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

package com.android.modules.updatablesharedlibs.apps.targetTWithLib;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

/**
 * Tests an app targeting T which also includes a shared library that is part of the BOOTCLASSPATH
 * from T.
 *
 * <p>This means the shared library should not be explicitly in the shared libraries in order
 * to avoid duplication of the BCP.
 */
@RunWith(AndroidJUnit4.class)
public class UpdatableSharedLibraryTargetTWithLibraryTest {
    // private static final Context sContext = InstrumentationRegistry.getInstrumentation()
    //          .getContext();

    @Test
    public void checkHasNoSharedLibrary() throws Exception {
        // TODO(b/205261027): not possible to test yet

        // This is the code we'd like to run. But because before API finalisation PM
        // will see the shared library as
        // having "on-bcp-since 9001" this means that it never considers that shared
        // library to be in the BCP.

        // String packageName = getContext().getPackageName();
        // ApplicationInfo appInfo = getPackageManager().getApplicationInfo(packageName,
        //      PackageManager.GET_SHARED_LIBRARY_FILES);
        // Log.d(TAG, "checkHasNoSharedLibrary in " + appInfo.sharedLibraryFiles.length
        //      + " libraries");
        // for (String path : appInfo.sharedLibraryFiles) {
        //      if (path.contains("com.android.modules.updatablesharedlibs")) {
        //          Assert.fail("Unexpectedly found a shared library: " + path);
        //      }
        // }
    }
}
