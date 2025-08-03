/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.photopicker.cts.util;

import static android.provider.MediaStore.ACTION_PICK_IMAGES;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

public class PhotoPickerPackageUtils {
    /**
     * Clears the package data.
     */
    public static void clearPackageData(String packageName) throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("pm clear " + packageName);

        // We should ideally be listening to an effective measure to know if package data was
        // cleared, like listening to a broadcasts or checking a value. But that information is
        // very package private and not available.
        Thread.sleep(500);
    }

    /**
     * Return package name of Documents UI.
     */
    @NonNull
    public static String getDocumentsUiPackageName() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        return getActivityPackageNameFromIntent(intent);
    }

    /**
     * Return the package name of the given intent.
     */
    @NonNull
    public static String getActivityPackageNameFromIntent(@NonNull Intent intent) {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final ResolveInfo ri = inst.getContext().getPackageManager().resolveActivity(intent, 0);
        return ri.activityInfo.packageName;
    }

    @NonNull
    public static String getPhotoPickerPackageName() {
        return getActivityPackageNameFromIntent(new Intent(ACTION_PICK_IMAGES));
    }
}
