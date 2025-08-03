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

package android.security.cts;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith(AndroidJUnit4.class)
public class WallpaperManagerTest extends StsExtraBusinessLogicTestCase {
    private static final String TAG = "WallpaperManagerSTS";
    private static final long PNG_SIZE = 7503368920L;

    private Context mContext;
    private WallpaperManager mWallpaperManager;

    @Before
    public void setUp() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SET_WALLPAPER_HINTS,
                        Manifest.permission.SET_WALLPAPER);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWallpaperManager = WallpaperManager.getInstance(mContext);
        assumeTrue("Device does not support wallpapers", mWallpaperManager.isWallpaperSupported());
    }

    @After
    public void tearDown() throws Exception {
        if (mWallpaperManager != null) {
            mWallpaperManager.clear(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    // b/204316511
    @Test
    @AsbSecurityTest(cveBugId = 204316511)
    public void testSetDisplayPadding() {
        Rect validRect = new Rect(1, 1, 1, 1);
        // This should work, no exception expected
        mWallpaperManager.setDisplayPadding(validRect);

        Rect negativeRect = new Rect(-1, 0 , 0, 0);
        try {
            mWallpaperManager.setDisplayPadding(negativeRect);
            Assert.fail("setDisplayPadding should fail for a Rect with negative values");
        } catch (IllegalArgumentException e) {
            //Expected exception
        }

        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        Display primaryDisplay = dm.getDisplay(DEFAULT_DISPLAY);
        Context windowContext = mContext.createWindowContext(primaryDisplay,
                TYPE_APPLICATION, null);
        Display display = windowContext.getDisplay();

        Rect tooWideRect = new Rect(0, 0, display.getMaximumSizeDimension() + 1, 0);
        try {
            mWallpaperManager.setDisplayPadding(tooWideRect);
            Assert.fail("setDisplayPadding should fail for a Rect width larger than "
                    + display.getMaximumSizeDimension());
        } catch (IllegalArgumentException e) {
            //Expected exception
        }

        Rect tooHighRect = new Rect(0, 0, 0, display.getMaximumSizeDimension() + 1);
        try {
            mWallpaperManager.setDisplayPadding(tooHighRect);
            Assert.fail("setDisplayPadding should fail for a Rect height larger than "
                    + display.getMaximumSizeDimension());
        } catch (IllegalArgumentException e) {
            //Expected exception
        }
    }

    @RequiresDevice
    @Test
    @AsbSecurityTest(cveBugId = 204087139)
    public void testSetMaliciousStream() {
        ActivityManager am = mContext.getSystemService(ActivityManager.class);
        assumeFalse(am.isLowRamDevice());
        final File testImage = unZipMaliciousImageFile();
        Assert.assertTrue(testImage.exists());
        try (InputStream s = mContext.getContentResolver()
                .openInputStream(Uri.fromFile(testImage))) {
            final int oldWallpaperId = mWallpaperManager.getWallpaperId(
                    WallpaperManager.FLAG_SYSTEM);
            final int newWallpaperId = mWallpaperManager.setStream(
                    s, null, true,
                    WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
            Assert.assertNotEquals(oldWallpaperId, newWallpaperId);
        } catch (IOException ex) {
        } finally {
            if (testImage.exists()) {
                testImage.delete();
            }
        }
    }

    private File unZipMaliciousImageFile() {
        File png = new File(mContext.getExternalFilesDir(null) + "/exploit.png");
        if (!png.exists() || png.length() < PNG_SIZE) {
            AssetManager am = mContext.getAssets();
            try {
                InputStream is = am.open("exploit.zip");
                try (ZipInputStream zis = new ZipInputStream(
                        new BufferedInputStream(is))) {
                    ZipEntry ze;
                    int count;
                    byte[] buffer = new byte[8192];
                    while ((ze = zis.getNextEntry()) != null) {
                        File file = new File(mContext.getExternalFilesDir(
                                null), ze.getName());
                        if (ze.isDirectory()) {
                            continue;
                        }
                        try (FileOutputStream fout = new FileOutputStream(file)) {
                            while ((count = zis.read(buffer)) != -1) {
                                fout.write(buffer, 0, count);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "UnZip error:", e);
            }
        }
        return png;
    }
}
