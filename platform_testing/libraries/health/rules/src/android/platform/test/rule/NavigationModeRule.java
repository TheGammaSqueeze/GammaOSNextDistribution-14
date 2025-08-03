/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.platform.test.rule;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_3BUTTON_OVERLAY;
import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_GESTURAL_OVERLAY;

import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.systemui.shared.system.QuickStepContract;

import org.junit.runner.Description;

import java.io.IOException;

/** This rule runs the test in the specified navigation mode. */
public class NavigationModeRule extends TestWatcher {
    private static final String TAG = "NavigationModeRule";

    private final String mRequestedOverlayPackage;
    private final String mOriginalOverlayPackage;
    private final boolean mChangeNavigationModeAfterTest;

    public NavigationModeRule(String requestedOverlayPackage) {
        this(requestedOverlayPackage, /* changeNavigationModeAfterTest */ true);
    }

    public NavigationModeRule(
            String requestedOverlayPackage, boolean changeNavigationModeAfterTest) {
        mChangeNavigationModeAfterTest = changeNavigationModeAfterTest;
        mRequestedOverlayPackage = requestedOverlayPackage;
        mOriginalOverlayPackage =
                getCurrentOverlayPackage(
                        LauncherInstrumentation.getCurrentInteractionMode(
                                getInstrumentation().getContext()));
    }

    private static boolean packageExists(String overlayPackage) {
        try {
            PackageManager pm = getInstrumentation().getContext().getPackageManager();
            if (pm.getApplicationInfo(overlayPackage, 0 /* flags */) == null) {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    private static boolean setActiveOverlay(String overlayPackage) {
        if (!packageExists(overlayPackage)) {
            Log.d(TAG, "setActiveOverlay: " + overlayPackage + " pkg does not exist");
            return false;
        }

        Log.d(TAG, "setActiveOverlay: " + overlayPackage + "...");
        try {
            UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                    "cmd overlay enable-exclusive --category " + overlayPackage);
            UiDevice.getInstance(getInstrumentation())
                    .executeShellCommand("am wait-for-broadcast-barrier");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static String getCurrentOverlayPackage(int currentInteractionMode) {
        return QuickStepContract.isGesturalMode(currentInteractionMode)
                ? NAV_BAR_MODE_GESTURAL_OVERLAY
                : NAV_BAR_MODE_3BUTTON_OVERLAY;
    }

    @Override
    protected void starting(Description description) {
        if (!mOriginalOverlayPackage.equals(mRequestedOverlayPackage)) {
            if (!setActiveOverlay(mRequestedOverlayPackage)) {
                throw new RuntimeException(
                        "Couldn't set the requested overlay package " + mRequestedOverlayPackage);
            }
        }

        super.starting(description);
    }

    @Override
    protected void finished(Description description) {
        super.finished(description);

        if (mChangeNavigationModeAfterTest
                && !mOriginalOverlayPackage.equals(mRequestedOverlayPackage)) {
            if (!setActiveOverlay(mOriginalOverlayPackage)) {
                throw new RuntimeException(
                        "Couldn't set the original overlay package " + mOriginalOverlayPackage);
            }
        }
    }
}
