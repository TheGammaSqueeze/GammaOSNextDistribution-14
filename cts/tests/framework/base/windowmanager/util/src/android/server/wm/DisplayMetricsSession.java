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

package android.server.wm;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.server.wm.ActivityManagerTestBase.ReportedDisplayMetrics;

import android.util.Size;

/**
 * A test helper class that queries or changes the latest DisplayMetrics override in WM by
 * sending Shell wm commands. Provides handling for errors, orientation types and fallback
 * behaviors.
 * Example usage: This can be used to resize a Display in CTS test and trigger configuration
 * changes.
 */
public class DisplayMetricsSession implements AutoCloseable {
    private final ReportedDisplayMetrics mInitialDisplayMetrics;
    private final int mDisplayId;

    public DisplayMetricsSession(int displayId) {
        mDisplayId = displayId;
        mInitialDisplayMetrics = ReportedDisplayMetrics.getDisplayMetrics(
                mDisplayId);
    }

    public ReportedDisplayMetrics getInitialDisplayMetrics() {
        return mInitialDisplayMetrics;
    }

    public ReportedDisplayMetrics getDisplayMetrics() {
        return ReportedDisplayMetrics.getDisplayMetrics(mDisplayId);
    }

    public void changeAspectRatio(double aspectRatio, int orientation) {
        final Size originalSize = mInitialDisplayMetrics.physicalSize;
        final int smaller = Math.min(originalSize.getWidth(), originalSize.getHeight());
        final int larger = (int) (smaller * aspectRatio);
        Size overrideSize;
        if (orientation == ORIENTATION_LANDSCAPE) {
            overrideSize = new Size(larger, smaller);
        } else {
            overrideSize = new Size(smaller, larger);
        }
        overrideDisplayMetrics(overrideSize, mInitialDisplayMetrics.physicalDensity);
    }

    public void changeDisplayMetrics(double sizeRatio, double densityRatio) {
        // Given a display may already have an override applied before the test is begun,
        // resize based upon the override.
        final Size originalSize;
        final int density;
        if (mInitialDisplayMetrics.overrideSize != null) {
            originalSize = mInitialDisplayMetrics.overrideSize;
        } else {
            originalSize = mInitialDisplayMetrics.physicalSize;
        }

        if (mInitialDisplayMetrics.overrideDensity != null) {
            density = mInitialDisplayMetrics.overrideDensity;
        } else {
            density = mInitialDisplayMetrics.physicalDensity;
        }

        final Size overrideSize = new Size((int)(originalSize.getWidth() * sizeRatio),
                (int)(originalSize.getHeight() * sizeRatio));
        final int overrideDensity = (int)(density * densityRatio);
        overrideDisplayMetrics(overrideSize, overrideDensity);
    }

    public void overrideDisplayMetrics(final Size size, final int density) {
        mInitialDisplayMetrics.setDisplayMetrics(size, density);
    }

    public void restoreDisplayMetrics() {
        mInitialDisplayMetrics.restoreDisplayMetrics();
    }

    @Override
    public void close() {
        restoreDisplayMetrics();
    }
}
