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

package android.app.cts.wallpapers;

import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;

import android.content.Context;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to check the status of the wallpaper windows.
 * Includes tool to handle the keyguard state to check both home and lock screen wallpapers.
 */
public class WallpaperWindowsTestUtils {
    private static Context sContext;

    public static void setContext(Context context) {
        sContext = context;
    }

    public static class WallpaperWindowsHelper {
        private final WindowManagerStateHelper mWmState;
        private List<WindowManagerState.WindowState> mWallpaperWindows = new ArrayList<>();
        private List<String> mPackageNames = new ArrayList<>();

        public WallpaperWindowsHelper(WindowManagerStateHelper windowManagerStateHelper) {
            mWmState = windowManagerStateHelper;
        }

        public String dumpWindows() {
            StringBuilder msgWindows = new StringBuilder();
            mWallpaperWindows.forEach(w -> msgWindows.append(w.toLongString()).append(", "));
            return "[" + msgWindows + "]";
        }

        public String dumpPackages() {
            StringBuilder msgWindows = new StringBuilder();
            mPackageNames.forEach(p -> msgWindows.append(p).append(", "));
            return "[" + msgWindows + "]";
        }

        /**
         * Wait until the visibility of the window is the one expected,
         * and return false if it does not happen within N iterations
         */
        public boolean waitForMatchingWindowVisibility(String name,
                boolean expectedVisibility) {
            return mWmState.waitFor(
                    (wmState) -> checkMatchingWindowVisibility(name, expectedVisibility),
                    "Visibility of " + name + " is not " + expectedVisibility);
        }

        /**
         * Wait until the packages of the wallpapers match exactly the expected ones,
         * and return false if it does not happen within N iterations
         */
        public boolean waitForMatchingPackages(List<String> expected) {
            return mWmState.waitFor(
                    (wmState) -> checkMatchingPackages(expected),
                    "Provided packages and observed ones do not match");
        }

        private boolean checkMatchingWindowVisibility(String name, boolean expectedVisibility) {
            updateWindows();
            return mWallpaperWindows.stream().anyMatch(
                    w -> w.getName().equals(name) && w.isSurfaceShown() == expectedVisibility);
        }

        private boolean checkMatchingPackages(List<String> expected) {
            updateWindows();
            if (expected.size() != mPackageNames.size()) {
                return false;
            }
            for (int i = 0; i < expected.size(); i++) {
                if (!expected.get(i).equals(mPackageNames.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private void updateWindows() {
            mWmState.waitForAppTransitionIdleOnDisplay(sContext.getDisplayId());
            mWmState.computeState();
            mWallpaperWindows = mWmState.getMatchingWindowType(TYPE_WALLPAPER);
            mPackageNames = new ArrayList<>();
            mWallpaperWindows.forEach(w -> mPackageNames.add(w.getPackageName()));
        }
    }
}
