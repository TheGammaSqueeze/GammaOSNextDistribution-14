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

package android.server.wm.window;

import static android.content.pm.PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
import static android.content.pm.PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityTaskManager;
import android.content.Context;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

/**
 * To verify the APIs of whether multi windowing features are supported.
 *
 * <p>Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:MultiWindowAvailabilityTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class MultiWindowAvailabilityTests extends ActivityManagerTestBase {

    /**
     * When a device supports multi window
     * {@link com.android.internal.R.bool#config_supportsMultiWindow}, it must support at least
     * one multi window feature; otherwise the config is treated as {@code false} in WindowManager.
     */
    @ApiTest(apis = {"android.app.ActivityTaskManager#supportsMultiWindow",
            "android.content.pm.PackageManager#FEATURE_FREEFORM_WINDOW_MANAGEMENT",
            "android.content.pm.PackageManager#FEATURE_PICTURE_IN_PICTURE"})
    @Test
    public void testSupportsMultiWindow() {
        assumeTrue("Device doesn't support multi window",
                ActivityTaskManager.supportsMultiWindow(mContext));

        final boolean supportsFreeform = supportsFreeform(mContext);
        final boolean supportsSplitScreen = supportsSplitScreenMultiWindow();
        final boolean supportsPictureInPicture = supportsPictureInPicture(mContext);
        final boolean supportsMultiDisplay = supportsMultiDisplay(mContext);

        assertTrue("Device that declares to support multi window must support at least one"
                        + " multi window feature",
                supportsFreeform || supportsSplitScreen || supportsPictureInPicture
                        || supportsMultiDisplay);
    }

    /**
     * When a device doesn't supports multi window
     * {@link com.android.internal.R.bool#config_supportsMultiWindow}, it must not declare to
     * support freeform, split screen, or picture-in-picture; otherwise apps can read that the
     * system supports those features while the system doesn't.
     */
    @ApiTest(apis = {"android.app.ActivityTaskManager#supportsMultiWindow",
            "android.content.pm.PackageManager#FEATURE_FREEFORM_WINDOW_MANAGEMENT",
            "android.content.pm.PackageManager#FEATURE_PICTURE_IN_PICTURE"})
    @Test
    public void testNotSupportsMultiWindow() {
        assumeFalse("Device supports multi window",
                ActivityTaskManager.supportsMultiWindow(mContext));

        assertFalse("Device declares to not support multi window, but declares to"
                + " support freeform", supportsFreeform(mContext));
        assertFalse("Device declares to not support multi window, but declares to"
                + " support split screen", supportsSplitScreenMultiWindow());
        assertFalse("Device declares to not support multi window, but declares to"
                + " support picture-in-picture", supportsPictureInPicture(mContext));
    }

    /**
     * When a device declares to support expanded picture-in-picture, it must declare to support
     * picture-in-picture.
     */
    @ApiTest(apis = {"android.content.pm.PackageManager#FEATURE_PICTURE_IN_PICTURE",
            "android.content.pm.PackageManager#FEATURE_EXPANDED_PICTURE_IN_PICTURE"})
    @Test
    public void testSupportsExpandedPictureInPicture() {
        assumeTrue("Device doesn't support expanded picture-in-picture",
                supportsExpandedPictureInPicture(mContext));

        assertTrue("Device that declares to support expanded picture-in-picture must support"
                + " picture-in-picture", supportsPictureInPicture(mContext));
    }

    /** Returns {@code true} if the system declares to support freeform. */
    private static boolean supportsFreeform(@NonNull Context context) {
        return context.getPackageManager().hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT);
    }

    /** Returns {@code true} if the system declares to support picture-in-picture. */
    private static boolean supportsPictureInPicture(@NonNull Context context) {
        return context.getPackageManager().hasSystemFeature(FEATURE_PICTURE_IN_PICTURE);
    }

    /** Returns {@code true} if the system declares to support expanded picture-in-picture. */
    private static boolean supportsExpandedPictureInPicture(@NonNull Context context) {
        return context.getPackageManager().hasSystemFeature(FEATURE_EXPANDED_PICTURE_IN_PICTURE);
    }

    /**
     * Returns {@code true} if the system declares to support running activities on secondary
     * displays.
     */
    public static boolean supportsMultiDisplay(@NonNull Context context) {
        return context.getPackageManager().hasSystemFeature(
                FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
    }
}
