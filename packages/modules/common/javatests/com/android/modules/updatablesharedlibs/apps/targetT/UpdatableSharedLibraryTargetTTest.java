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

package com.android.modules.updatablesharedlibs.apps.targetT;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

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
 * Tests an app targeting T.
 *
 * <p>With the shared libraries included in this test, none of the shared libraries present in this
 * test should be included for this app.
 */
@RunWith(AndroidJUnit4.class)
public class UpdatableSharedLibraryTargetTTest {
    private static final Correspondence<String, String> CONTAINS_SUBSTRING =
            Correspondence.from(String::contains, "contains");
    private static final Context sContext = InstrumentationRegistry.getInstrumentation()
            .getContext();

    @Test
    public void checkHasNoSharedLibrary() throws Exception {
        String packageName = sContext.getPackageName();
        ApplicationInfo appInfo = sContext.getPackageManager().getApplicationInfo(packageName,
                PackageManager.GET_SHARED_LIBRARY_FILES);

        assertThat(appInfo.sharedLibraryFiles)
            .asList()
            .comparingElementsUsing(CONTAINS_SUBSTRING)
            .doesNotContain("com.android.modules.updatablesharedlibs");
    }

    @Test
    public void checkHasNoApiAccess() throws Exception {
        assertThrows(
            ClassNotFoundException.class,
            () -> Class.forName("com.android.modules.updatablesharedlibs.libs.before.t.Api")
        );
    }
}
