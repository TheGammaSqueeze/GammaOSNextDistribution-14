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

package android.server.wm.jetpack.extensions.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.extensions.layout.WindowLayoutInfo;
import androidx.window.sidecar.SidecarDisplayFeature;
import androidx.window.sidecar.SidecarInterface;
import androidx.window.sidecar.SidecarProvider;
import androidx.window.sidecar.SidecarWindowLayoutInfo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for sidecar tests, providing methods for checking if a device supports sidecar,
 * retrieving and validating the sidecar version, and getting the instance of
 * {@link SidecarInterface}.
 */
public class SidecarUtil {

    private static final String SIDECAR_TAG = "Sidecar";

    public static final Version MINIMUM_SIDECAR_VERSION = new Version(1, 0, 0, "");

    /**
     * Returns the Sidecar version in a semantic version format.
     */
    @SuppressWarnings("deprecation")
    @NonNull
    public static Version getSidecarVersion() {
        try {
            String sidecarVersionStr = SidecarProvider.getApiVersion();
            if (Version.isValidVersion(sidecarVersionStr)) {
                return Version.parse(sidecarVersionStr);
            }
        } catch (NoClassDefFoundError e) {
            Log.d(SIDECAR_TAG, "Sidecar version not found");
        } catch (UnsupportedOperationException e) {
            Log.d(SIDECAR_TAG, "Stub Sidecar");
        }
        return Version.UNKNOWN;
    }

    /**
     * Returns true if the sidecar version is greater than 0.
     */
    public static boolean isSidecarVersionValid() {
        final Version version = getSidecarVersion();
        // Check that the sidecar version on the device is at least the minimum valid version.
        return version.compareTo(MINIMUM_SIDECAR_VERSION) >= 0;
    }

    /**
     * Returns the current {@link SidecarInterface} if present on the device.
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public static SidecarInterface getSidecarInterface(Context context) {
        try {
            return SidecarProvider.getSidecarImpl(context);
        } catch (NoClassDefFoundError e) {
            Log.d(SIDECAR_TAG, "Sidecar implementation not found");
        } catch (UnsupportedOperationException e) {
            Log.d(SIDECAR_TAG, "Stub Sidecar");
        }
        return null;
    }

    /**
     * Assumes that sidecar is present and has a version above 0.
     */
    public static void assumeSidecarSupportedDevice(Context context) {
        final boolean sidecarInterfaceNotNull = getSidecarInterface(context) != null;
        assumeTrue("Device does not support sidecar", sidecarInterfaceNotNull);
        // If sidecar is on the device, make sure that the version is valid.
        assertTrue("Sidecar version is invalid, must be at least "
                + MINIMUM_SIDECAR_VERSION.toString(), isSidecarVersionValid());
    }

    /**
     * Returns an int array containing the raw values of the currently visible fold types.
     * @param activity An {@link Activity} that is visible and intersects the folds
     * @return an int array containing the raw values for the current visible fold types.
     * @throws InterruptedException when the async collection of the {@link WindowLayoutInfo}
     * is interrupted.
     */
    @SuppressWarnings("deprecation") // SidecarInterface is deprecated but required.
    @NonNull
    public static int[] getSidecarDisplayFeatureTypes(Activity activity) {
        SidecarInterface sidecarInterface = getSidecarInterface(activity);
        if (sidecarInterface == null) {
            return new int[0];
        }
        SidecarWindowLayoutInfo windowLayoutInfo = sidecarInterface.getWindowLayoutInfo(
                getActivityWindowToken(activity));
        if (windowLayoutInfo == null) {
            return new int[0];
        }
        return windowLayoutInfo.displayFeatures
                .stream()
                .map(d -> d.getType())
                .mapToInt(i -> i.intValue())
                .toArray();
    }

    /**
     * Assumes that the window associated to the window token has at least one display feature.
     * @param sidecarInterface the implementation of sidecar from the device.
     * @param windowToken the token for the active window.
     */
    @SuppressWarnings("deprecation") // SidecarInterface is deprecated but required.
    public static void assumeHasDisplayFeatures(SidecarInterface sidecarInterface,
            IBinder windowToken) {
        SidecarWindowLayoutInfo windowLayoutInfo = sidecarInterface.getWindowLayoutInfo(
                windowToken);
        assertNotNull(windowLayoutInfo); // window layout info cannot be null
        List<SidecarDisplayFeature> displayFeatures = windowLayoutInfo.displayFeatures;
        assertNotNull(displayFeatures); // list cannot be null
        assumeFalse(displayFeatures.isEmpty()); // list can be empty
    }

    /**
     * Checks that display features are consistent across portrait and landscape orientations.
     * It is possible for the display features to be different between portrait and landscape
     * orientations because only display features within the activity bounds are provided to the
     * activity and the activity may be letterboxed if orientation requests are ignored. So, only
     * check that display features that are within both portrait and landscape activity bounds
     * are consistent. To be consistent, the feature bounds must be the same (potentially rotated if
     * orientation requests are respected) and their type and state must be the same.
     */
    public static void assertEqualWindowLayoutInfo(
            @NonNull SidecarWindowLayoutInfo portraitWindowLayoutInfo,
            @NonNull SidecarWindowLayoutInfo landscapeWindowLayoutInfo,
            @NonNull Rect portraitBounds, @NonNull Rect landscapeBounds,
            boolean doesDisplayRotateForOrientation) {
        // Compute the portrait and landscape features that are within both the portrait and
        // landscape activity bounds.
        final List<SidecarDisplayFeature> portraitFeaturesWithinBoth = getMutualDisplayFeatures(
                portraitWindowLayoutInfo, portraitBounds, landscapeBounds);
        List<SidecarDisplayFeature> landscapeFeaturesWithinBoth = getMutualDisplayFeatures(
                landscapeWindowLayoutInfo, landscapeBounds, portraitBounds);
        assertEquals(portraitFeaturesWithinBoth.size(), landscapeFeaturesWithinBoth.size());
        final int nFeatures = portraitFeaturesWithinBoth.size();
        if (nFeatures == 0) {
            return;
        }

        // If the display rotates to respect orientation, then to make the landscape display
        // features comparable to the portrait display features rotate the landscape features.
        if (doesDisplayRotateForOrientation) {
            landscapeFeaturesWithinBoth = landscapeFeaturesWithinBoth
                    .stream()
                    .map(d -> {
                        final Rect oldBounds = d.getRect();
                        // Rotate the bounds by 90 degrees
                        final Rect newBounds = new Rect(oldBounds.top, oldBounds.left,
                                oldBounds.bottom, oldBounds.right);
                        SidecarDisplayFeature newDisplayFeature = new SidecarDisplayFeature();
                        newDisplayFeature.setRect(newBounds);
                        newDisplayFeature.setType(d.getType());
                        return newDisplayFeature;
                    })
                    .collect(Collectors.toList());
        }

        // Check that the list of features are the same
        final boolean[] portraitFeatureMatched = new boolean[nFeatures];
        final boolean[] landscapeFeatureMatched = new boolean[nFeatures];
        for (int portraitIndex = 0; portraitIndex < nFeatures; portraitIndex++) {
            if (portraitFeatureMatched[portraitIndex]) {
                // A match has already been found for this portrait display feature
                continue;
            }
            final SidecarDisplayFeature portraitDisplayFeature = portraitFeaturesWithinBoth
                    .get(portraitIndex);
            for (int landscapeIndex = 0; landscapeIndex < nFeatures; landscapeIndex++) {
                if (landscapeFeatureMatched[landscapeIndex]) {
                    // A match has already been found for this landscape display feature
                    continue;
                }
                final SidecarDisplayFeature landscapeDisplayFeature = landscapeFeaturesWithinBoth
                        .get(landscapeIndex);
                // Check that the bounds and type match
                if (portraitDisplayFeature.getRect().equals(landscapeDisplayFeature.getRect())
                        && portraitDisplayFeature.getType() == landscapeDisplayFeature.getType()) {
                    // The display features match
                    portraitFeatureMatched[portraitIndex] = true;
                    landscapeFeatureMatched[landscapeIndex] = true;
                }
            }
        }

        // Check that a match was found for each display feature
        for (int i = 0; i < nFeatures; i++) {
            assertTrue(portraitFeatureMatched[i] && landscapeFeatureMatched[i]);
        }
    }

    /**
     * Returns the subset of {@param windowLayoutInfo} display features that are shared by the
     * activity bounds in the current orientation and the activity bounds in the other orientation.
     */
    private static List<SidecarDisplayFeature> getMutualDisplayFeatures(
            @NonNull SidecarWindowLayoutInfo windowLayoutInfo,
            @NonNull Rect currentOrientationBounds, @NonNull Rect otherOrientationBounds) {
        return windowLayoutInfo.displayFeatures
                .stream()
                .map(d -> {
                    // The display features are positioned relative to the activity bounds, so
                    // re-position them absolutely within the task.
                    final Rect newBounds = new Rect(d.getRect());
                    newBounds.offset(currentOrientationBounds.left, currentOrientationBounds.top);
                    SidecarDisplayFeature newDisplayFeature = new SidecarDisplayFeature();
                    newDisplayFeature.setRect(newBounds);
                    newDisplayFeature.setType(d.getType());
                    return newDisplayFeature;
                })
                .filter(d -> otherOrientationBounds.contains(d.getRect()))
                .collect(Collectors.toList());
    }

    private static IBinder getActivityWindowToken(Activity activity) {
        return activity.getWindow().getAttributes().token;
    }
}
