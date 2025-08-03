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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;
import androidx.window.extensions.WindowExtensions;
import androidx.window.extensions.WindowExtensionsProvider;
import androidx.window.extensions.area.WindowAreaComponent;
import androidx.window.extensions.layout.DisplayFeature;
import androidx.window.extensions.layout.FoldingFeature;
import androidx.window.extensions.layout.WindowLayoutComponent;
import androidx.window.extensions.layout.WindowLayoutInfo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for extensions tests, providing methods for checking if a device supports
 * extensions, retrieving and validating the extension version, and getting the instance of
 * {@link WindowExtensions}.
 */
public class ExtensionsUtil {

    private static final String EXTENSION_TAG = "Extension";

    public static final Version EXTENSION_VERSION_1 = new Version(1, 0, 0, "");

    public static final Version EXTENSION_VERSION_2 = new Version(1, 1, 0, "");

    /**
     * Returns the current version of {@link WindowExtensions} if present on the device.
     */
    @NonNull
    public static Version getExtensionVersion() {
        try {
            WindowExtensions extensions = getWindowExtensions();
            if (extensions != null) {
                return new Version(extensions.getVendorApiLevel() /* major */, 0 /* minor */,
                        0 /* patch */, "" /* description */);
            }
        } catch (NoClassDefFoundError e) {
            Log.d(EXTENSION_TAG, "Extension version not found");
        } catch (UnsupportedOperationException e) {
            Log.d(EXTENSION_TAG, "Stub Extension");
        }
        return Version.UNKNOWN;
    }

    /**
     * Returns {@code true} if the version reported on the device is at least the version provided.
     * This is used in CTS tests to try to add coverage without strict enforcement. We can not apply
     * strict enforcement between dessert releases.
     * @param targetVersion minimum version to be checked.
     * @return true if the version on the device is at least the target version inclusively.
     */
    public static boolean isExtensionVersionAtLeast(Version targetVersion) {
        final Version version = getExtensionVersion();
        return version.compareTo(targetVersion) >= 0;
    }

    public static boolean isExtensionVersionLatest() {
        return isExtensionVersionAtLeast(EXTENSION_VERSION_2);
    }

    /**
     * If called on a device with the vendor api level less than the bound then the test will be
     * ignored.
     * @param vendorApiLevel minimum {@link WindowExtensions#getVendorApiLevel()} for a test to
     *                       succeed
     */
    public static void assumeVendorApiLevelAtLeast(int vendorApiLevel) {
        final Version version = getExtensionVersion();
        assumeTrue(
                "Needs vendorApiLevel " + vendorApiLevel + " but has " + version.getMajor(),
                version.getMajor() >= vendorApiLevel
        );
    }

    /**
     * Returns {@code true} if the extensions version is greater than 0.
     */
    public static boolean isExtensionVersionValid() {
        final Version version = getExtensionVersion();
        // Check that the extension version on the device is at least the minimum valid version.
        return version.compareTo(EXTENSION_VERSION_1) >= 0;
    }

    /**
     * Returns the {@link WindowExtensions} if it is present on the device, {@code null} otherwise.
     */
    @Nullable
    public static WindowExtensions getWindowExtensions() {
        try {
            return WindowExtensionsProvider.getWindowExtensions();
        } catch (NoClassDefFoundError e) {
            Log.d(EXTENSION_TAG, "Extension implementation not found");
        } catch (UnsupportedOperationException e) {
            Log.d(EXTENSION_TAG, "Stub Extension");
        }
        return null;
    }

    /**
     * Assumes that extensions is present on the device and asserts that the extension version is
     * valid.
     */
    public static void assumeExtensionSupportedDevice() {
        final boolean extensionNotNull = getWindowExtensions() != null;
        assumeTrue("Device does not support extensions", extensionNotNull);
        // If extensions are on the device, make sure that the version is valid.
        assertTrue("Extension version is invalid, must be at least "
                + EXTENSION_VERSION_1.toString(), isExtensionVersionValid());
    }

    /**
     * Returns the {@link WindowLayoutComponent} if it is present on the device, {@code null}
     * otherwise.
     */
    @Nullable
    public static WindowLayoutComponent getExtensionWindowLayoutComponent() {
        WindowExtensions extension = getWindowExtensions();
        if (extension == null) {
            return null;
        }
        return extension.getWindowLayoutComponent();
    }

    /**
     * Publishes a WindowLayoutInfo update to a test consumer. In EXTENSION_VERSION_1, only type
     * Activity can be the listener to WindowLayoutInfo changes. This method should be called at
     * most once for each given Activity because addWindowLayoutInfoListener implementation
     * assumes a 1-1 mapping between the activity and consumer.
     */
    @Nullable
    public static WindowLayoutInfo getExtensionWindowLayoutInfo(Activity activity)
            throws InterruptedException {
        WindowLayoutComponent windowLayoutComponent = getExtensionWindowLayoutComponent();
        if (windowLayoutComponent == null) {
            return null;
        }
        TestValueCountConsumer<WindowLayoutInfo> windowLayoutInfoConsumer =
                new TestValueCountConsumer<>();
        windowLayoutComponent.addWindowLayoutInfoListener(activity, windowLayoutInfoConsumer);
        WindowLayoutInfo info = windowLayoutInfoConsumer.waitAndGet();

        // The default implementation only allows a single listener per activity. Since we are using
        // a local windowLayoutInfoConsumer within this function, we must remember to clean up.
        // Otherwise, subsequent calls to addWindowLayoutInfoListener with the same activity will
        // fail to have its callback registered.
        windowLayoutComponent.removeWindowLayoutInfoListener(windowLayoutInfoConsumer);
        return info;
    }

    /**
     * Publishes a WindowLayoutInfo update to a test consumer. In EXTENSION_VERSION_2 both type
     * WindowContext and Activity can be listeners. This method should be called at most once for
     * each given Context because addWindowLayoutInfoListener implementation assumes a 1-1
     * mapping between the context and consumer.
     */
    @Nullable
    public static WindowLayoutInfo getExtensionWindowLayoutInfo(@UiContext Context context)
            throws InterruptedException {
        assertTrue(isExtensionVersionAtLeast(EXTENSION_VERSION_2));
        WindowLayoutComponent windowLayoutComponent = getExtensionWindowLayoutComponent();
        if (windowLayoutComponent == null) {
            return null;
        }
        TestValueCountConsumer<WindowLayoutInfo> windowLayoutInfoConsumer =
                new TestValueCountConsumer<>();
        windowLayoutComponent.addWindowLayoutInfoListener(context, windowLayoutInfoConsumer);
        WindowLayoutInfo info = windowLayoutInfoConsumer.waitAndGet();

        // The default implementation only allows a single listener per context. Since we are using
        // a local windowLayoutInfoConsumer within this function, we must remember to clean up.
        // Otherwise, subsequent calls to addWindowLayoutInfoListener with the same context will
        // fail to have its callback registered.
        windowLayoutComponent.removeWindowLayoutInfoListener(windowLayoutInfoConsumer);
        return info;
    }

    /**
     * Returns an int array containing the raw values of the currently visible fold types.
     * @param activity An {@link Activity} that is visible and intersects the folds
     * @return an int array containing the raw values for the current visible fold types.
     * @throws InterruptedException when the async collection of the {@link WindowLayoutInfo}
     * is interrupted.
     */
    @NonNull
    public static int[] getExtensionDisplayFeatureTypes(Activity activity)
            throws InterruptedException {
        WindowLayoutInfo windowLayoutInfo = getExtensionWindowLayoutInfo(activity);
        if (windowLayoutInfo == null) {
            return new int[0];
        }
        List<DisplayFeature> displayFeatureList = windowLayoutInfo.getDisplayFeatures();
        return displayFeatureList
                .stream()
                .filter(d -> d instanceof FoldingFeature)
                .map(d -> ((FoldingFeature) d).getType())
                .mapToInt(i -> i.intValue())
                .toArray();
    }

    /**
     * Returns whether the device reports at least one display feature.
     */
    public static void assumeHasDisplayFeatures(WindowLayoutInfo windowLayoutInfo) {
        // If WindowLayoutComponent is implemented, then WindowLayoutInfo and the list of display
        // features cannot be null. However the list can be empty if the device does not report
        // any display features.
        assertNotNull(windowLayoutInfo);
        assertNotNull(windowLayoutInfo.getDisplayFeatures());
        assumeFalse(windowLayoutInfo.getDisplayFeatures().isEmpty());
    }

    /**
     * Asserts that the {@link WindowLayoutInfo} is not empty.
     */
    public static void assertHasDisplayFeatures(WindowLayoutInfo windowLayoutInfo) {
        // If WindowLayoutComponent is implemented, then WindowLayoutInfo and the list of display
        // features cannot be null. However the list can be empty if the device does not report
        // any display features.
        assertNotNull(windowLayoutInfo);
        assertNotNull(windowLayoutInfo.getDisplayFeatures());
        assertFalse(windowLayoutInfo.getDisplayFeatures().isEmpty());
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
            @NonNull WindowLayoutInfo portraitWindowLayoutInfo,
            @NonNull WindowLayoutInfo landscapeWindowLayoutInfo,
            @NonNull Rect portraitBounds, @NonNull Rect landscapeBounds,
            boolean doesDisplayRotateForOrientation) {
        // Compute the portrait and landscape features that are within both the portrait and
        // landscape activity bounds.
        final List<DisplayFeature> portraitFeaturesWithinBoth = getMutualDisplayFeatures(
                portraitWindowLayoutInfo, portraitBounds, landscapeBounds);
        List<DisplayFeature> landscapeFeaturesWithinBoth = getMutualDisplayFeatures(
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
                        if (!(d instanceof FoldingFeature)) {
                            return d;
                        }
                        final FoldingFeature f = (FoldingFeature) d;
                        final Rect oldBounds = d.getBounds();
                        // Rotate the bounds by 90 degrees
                        final Rect newBounds = new Rect(oldBounds.top, oldBounds.left,
                                oldBounds.bottom, oldBounds.right);
                        return new FoldingFeature(newBounds, f.getType(), f.getState());
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
            final DisplayFeature portraitDisplayFeature = portraitFeaturesWithinBoth
                    .get(portraitIndex);
            for (int landscapeIndex = 0; landscapeIndex < nFeatures; landscapeIndex++) {
                if (landscapeFeatureMatched[landscapeIndex]) {
                    // A match has already been found for this landscape display feature
                    continue;
                }
                final DisplayFeature landscapeDisplayFeature = landscapeFeaturesWithinBoth
                        .get(landscapeIndex);
                // Only continue comparing if both display features are the same type of display
                // feature (e.g. FoldingFeature) and they have the same bounds
                if (!portraitDisplayFeature.getClass().equals(landscapeDisplayFeature.getClass())
                        || !portraitDisplayFeature.getBounds().equals(
                                landscapeDisplayFeature.getBounds())) {
                    continue;
                }
                // If both are folding features, then only continue comparing if the type and state
                // match
                if (portraitDisplayFeature instanceof FoldingFeature) {
                    FoldingFeature portraitFoldingFeature = (FoldingFeature) portraitDisplayFeature;
                    FoldingFeature landscapeFoldingFeature =
                            (FoldingFeature) landscapeDisplayFeature;
                    if (portraitFoldingFeature.getType() != landscapeFoldingFeature.getType()
                            || portraitFoldingFeature.getState()
                            != landscapeFoldingFeature.getState()) {
                        continue;
                    }
                }
                // The display features match
                portraitFeatureMatched[portraitIndex] = true;
                landscapeFeatureMatched[landscapeIndex] = true;
            }
        }

        // Check that a match was found for each display feature
        for (int i = 0; i < nFeatures; i++) {
            assertTrue(portraitFeatureMatched[i] && landscapeFeatureMatched[i]);
        }
    }

    /**
     * Returns the subset of {@code windowLayoutInfo} display features that are shared by the
     * activity bounds in the current orientation and the activity bounds in the other orientation.
     */
    private static List<DisplayFeature> getMutualDisplayFeatures(
            @NonNull WindowLayoutInfo windowLayoutInfo, @NonNull Rect currentOrientationBounds,
            @NonNull Rect otherOrientationBounds) {
        return windowLayoutInfo
                .getDisplayFeatures()
                .stream()
                .map(d -> {
                    if (!(d instanceof FoldingFeature)) {
                        return d;
                    }
                    // The display features are positioned relative to the activity bounds, so
                    // re-position them absolutely within the task.
                    final FoldingFeature f = (FoldingFeature) d;
                    final Rect r = f.getBounds();
                    r.offset(currentOrientationBounds.left, currentOrientationBounds.top);
                    return new FoldingFeature(r, f.getType(), f.getState());
                })
                .filter(d -> otherOrientationBounds.contains(d.getBounds()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the {@link WindowAreaComponent} available in {@link WindowExtensions} if available.
     * If the component is not available, returns null.
     */
    @Nullable
    public static WindowAreaComponent getExtensionWindowAreaComponent() {
        WindowExtensions extension = getWindowExtensions();
        if (extension == null || extension.getVendorApiLevel() < 2) {
            return null;
        }
        return extension.getWindowAreaComponent();
    }
}
