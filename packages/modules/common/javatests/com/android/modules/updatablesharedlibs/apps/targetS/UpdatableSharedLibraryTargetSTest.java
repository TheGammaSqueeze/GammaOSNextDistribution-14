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

package com.android.modules.updatablesharedlibs.apps.targetS;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import junit.framework.Assert;

import com.google.common.truth.Correspondence;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

/**
 * Tests an app targeting S.
 *
 * <p>One of the shared libraries included in this test claims to be in the
 * BOOTCLASSPATH before T so it should be added transparently to this app.
 */
@RunWith(AndroidJUnit4.class)
public class UpdatableSharedLibraryTargetSTest {
    private static String SHARED_LIB_PATH = "/apex/test_com.android.modules.updatablesharedlibs"
            + "/javalib/com.android.modules.updatablesharedlibs.libs.before.t.jar";

    private static final Context sContext = InstrumentationRegistry.getInstrumentation()
            .getContext();

    @Test
    public void checkSharedLibrary() throws Exception {
        String packageName = sContext.getPackageName();
        ApplicationInfo appInfo = sContext.getPackageManager().getApplicationInfo(packageName,
                PackageManager.GET_SHARED_LIBRARY_FILES);

        assertThat(appInfo.sharedLibraryFiles)
                .asList()
                .contains(SHARED_LIB_PATH);
    }

    @Test
    public void callApi() throws Exception {
        Object api = Class.forName("com.android.modules.updatablesharedlibs.libs.before.t.Api")
                .getDeclaredConstructor()
                .newInstance();

        String actual = (String) api.getClass().getDeclaredMethod("methodBeforeT").invoke(api);
        assertThat(actual).isEqualTo("Success");
    }

    @Test
    public void testGetSystemSharedLibraryNames() {
        String[] libraries = sContext.getPackageManager().getSystemSharedLibraryNames();
        assertThat(libraries)
                .asList()
                .containsAtLeast(
                    "com.android.modules.updatablesharedlibs.libs.before.t",
                    "com.android.modules.updatablesharedlibs.libs.since.t");
    }
}
