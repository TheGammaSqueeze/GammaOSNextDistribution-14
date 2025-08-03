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

package android.app.cts.wallpapers;

import static android.Manifest.permission.QUERY_ALL_PACKAGES;
import static android.Manifest.permission.READ_WALLPAPER_INTERNAL;
import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.app.cts.wallpapers.util.WallpaperTestUtils.getBitmap;
import static android.app.cts.wallpapers.util.WallpaperTestUtils.isSimilar;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Tests for {@link WallpaperManager} and related classes for target SDK 33.
 * This tests that for API U and above,
 * apps that target SDK 33 will receive data from the default wallpaper
 * when trying to read the wallpaper through the public API, except if the app has the permission
 * {@value android.Manifest.permission#READ_WALLPAPER_INTERNAL}.
 *
 * <p>
 * See the different test cases to see which methods are concerned.
 * </p>
 */
@RunWith(AndroidJUnit4.class)
public class WallpaperManagerSdk33Test {

    private static WallpaperManager sWallpaperManager;
    private static Bitmap sDefaultBitmap;
    private static Bitmap sRedBitmap;
    private static boolean sTvTarget;

    /**
     * Note: all tests in this class assume to start with a red bitmap on both system & lock screen.
     * As changing the wallpaper takes time, this initialization is only done here
     * and not in {@link #setUp}, since doing so would slow down the test suite substantially.
     * Tests that change the wallpaper are responsible to restore the red wallpaper
     * with {@link #setRedWallpaper()} when finished.
     */
    @BeforeClass
    public static void setUpClass() throws IOException {
        Context context = InstrumentationRegistry.getTargetContext();

        // ignore for TV targets
        PackageManager packageManager = context.getPackageManager();
        sTvTarget = packageManager != null
                && (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION));
        if (sTvTarget) return;

        sWallpaperManager = WallpaperManager.getInstance(context);
        sWallpaperManager.clear(FLAG_SYSTEM | FLAG_LOCK);

        // ignore for targets that have a live default wallpaper
        runWithShellPermissionIdentity(
                () -> assumeTrue(sWallpaperManager.getWallpaperInfo(FLAG_SYSTEM) == null),
                QUERY_ALL_PACKAGES);

        sDefaultBitmap = runWithShellPermissionIdentity(
                () -> sWallpaperManager.getBitmap(), READ_WALLPAPER_INTERNAL);

        sRedBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sRedBitmap);
        canvas.drawColor(Color.RED);
        setRedWallpaper();
    }

    @Before
    public void setUp() throws Exception {
        assumeFalse("WallpaperManagerSdk33Test is not meaningful for TV targets", sTvTarget);
        assumeTrue("Device does not support wallpapers", sWallpaperManager.isWallpaperSupported());
        MockitoAnnotations.initMocks(this);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        if (sRedBitmap != null && !sRedBitmap.isRecycled()) sRedBitmap.recycle();
        if (sWallpaperManager != null) sWallpaperManager.clear(FLAG_SYSTEM | FLAG_LOCK);
        sWallpaperManager = null;
    }

    @Test
    public void getDrawable_noPermission_returnsDefault() {
        assertReturnsDefault(sWallpaperManager::getDrawable, "getDrawable");
    }

    @Test
    public void getDrawable_withPermission_returnsCurrent() {
        assertReturnsCurrent(sWallpaperManager::getDrawable, "getDrawable");
    }

    @Test
    public void getFastDrawable_noPermission_returnsDefault() {
        assertReturnsDefault(sWallpaperManager::getFastDrawable, "getFastDrawable");
    }

    @Test
    public void getFastDrawable_withPermission_returnsCurrent() {
        assertReturnsCurrent(sWallpaperManager::getFastDrawable, "getFastDrawable");
    }

    @Test
    public void peekDrawable_noPermission_returnsDefault() {
        assertReturnsDefault(sWallpaperManager::peekDrawable, "peekDrawable");
    }

    @Test
    public void peekDrawable_withPermission_returnsCurrent() {
        assertReturnsCurrent(sWallpaperManager::peekDrawable, "peekDrawable");
    }

    @Test
    public void peekFastDrawable_noPermission_returnsDefault() {
        assertReturnsDefault(sWallpaperManager::peekFastDrawable, "peekFastDrawable");
    }

    @Test
    public void peekFastDrawable_withPermission_returnsCurrent() {
        assertReturnsCurrent(sWallpaperManager::peekFastDrawable, "peekFastDrawable");
    }

    @Test
    public void getWallpaperFile_system_noPermission_returnsDefault() {
        ParcelFileDescriptor parcelFileDescriptor = sWallpaperManager.getWallpaperFile(FLAG_SYSTEM);
        if (parcelFileDescriptor != null) {
            Bitmap bitmap = BitmapFactory.decodeFileDescriptor(
                    parcelFileDescriptor.getFileDescriptor());
            assertWithMessage(
                    "with no permission, getWallpaperFile(FLAG_SYSTEM) "
                            + "should return the default system wallpaper file")
                    .that(isSimilar(bitmap, sDefaultBitmap, false))
                    .isTrue();
        }
    }

    @Test
    public void getWallpaperFile_lock_noPermission_returnsDefault() throws IOException {
        sWallpaperManager.setBitmap(sRedBitmap, null, true, FLAG_LOCK);
        ParcelFileDescriptor parcelFileDescriptor = sWallpaperManager.getWallpaperFile(FLAG_LOCK);
        sWallpaperManager.clear(FLAG_SYSTEM | FLAG_LOCK);
        try {
            if (parcelFileDescriptor != null) {
                Bitmap bitmap = BitmapFactory.decodeFileDescriptor(
                        parcelFileDescriptor.getFileDescriptor());
                assertWithMessage(
                        "with no permission, getWallpaperFile(FLAG_LOCK) "
                                + "should return the default system wallpaper file")
                        .that(isSimilar(bitmap, sDefaultBitmap, false))
                        .isTrue();
            }
        } finally {
            setRedWallpaper();
        }
    }

    @Test
    public void getWallpaperFile_system_withPermission_returnsCurrent() {
        ParcelFileDescriptor parcelFileDescriptor = runWithShellPermissionIdentity(
                () -> sWallpaperManager.getWallpaperFile(FLAG_SYSTEM), READ_WALLPAPER_INTERNAL);
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(
                parcelFileDescriptor.getFileDescriptor());
        assertWithMessage(
                "with permission, getWallpaperFile(FLAG_SYSTEM)"
                        + "should return the currennt system wallpaper file")
                .that(isSimilar(bitmap, sRedBitmap, true))
                .isTrue();
    }

    @Test
    public void getWallpaperFile_lock_withPermission_doesNotReturnDefault() throws IOException {
        sWallpaperManager.setBitmap(sRedBitmap, null, true, FLAG_LOCK);
        ParcelFileDescriptor parcelFileDescriptor = runWithShellPermissionIdentity(
                () -> sWallpaperManager.getWallpaperFile(FLAG_LOCK), READ_WALLPAPER_INTERNAL);
        try {
            if (parcelFileDescriptor != null) {
                Bitmap bitmap = BitmapFactory.decodeFileDescriptor(
                        parcelFileDescriptor.getFileDescriptor());
                assertWithMessage(
                        "with permission, getWallpaperFile(FLAG_LOCK)"
                                + "should not return the default system wallpaper")
                        .that(isSimilar(bitmap, sDefaultBitmap, true))
                        .isFalse();
            }
        } finally {
            setRedWallpaper();
        }
    }

    private void assertReturnsDefault(Supplier<Drawable> methodToTest, String methodName) {
        Drawable drawable = methodToTest.get();
        assertWithMessage(
                "with no permission, " + methodName + " should return null or the default bitmap")
                .that(drawable == null || isSimilar(getBitmap(drawable), sDefaultBitmap, false))
                .isTrue();
    }

    private void assertReturnsCurrent(Supplier<Drawable> methodToTest, String methodName) {
        Drawable drawable = runWithShellPermissionIdentity(
                () -> methodToTest.get(), READ_WALLPAPER_INTERNAL);
        assertWithMessage(
                "with permission, " + methodName + " should return the current bitmap")
                .that(isSimilar(getBitmap(drawable), sRedBitmap, true)).isTrue();
    }

    private static void setRedWallpaper() throws IOException {
        sWallpaperManager.setBitmap(sRedBitmap,
                null /* visibleCropHint= */,
                true /* allowBackup */,
                FLAG_SYSTEM | FLAG_LOCK /* which */);
    }
}
