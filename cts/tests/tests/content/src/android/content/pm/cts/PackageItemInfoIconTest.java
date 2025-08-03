/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.content.pm.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.cts.R;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the icons returned by PackageManager.
 *
 * This ignores the PackageItemInfo.showUserIcon override case because it's not worthwhile to
 * ensure a secondary user with a different icon is available, and because fetching the user's icon
 * to compare against requires the privileged MANAGE_USERS permission which can't be granted.
 */
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class PackageItemInfoIconTest {

    private static final String PACKAGE_NAME = "android.content.cts";
    private static final String ACTIVITY_NAME = "android.content.pm.cts.TestPmActivity";

    private Context mContext;
    private PackageManager mPackageManager;

    @Before
    public void before() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPackageManager = mContext.getPackageManager();
    }

    @Test
    public void testSystemDefault() {
        Drawable expectedIcon = mContext.getDrawable(
                com.android.internal.R.drawable.sym_def_app_icon);

        PackageItemInfo itemInfo = new PackageItemInfo();
        itemInfo.icon = 0;

        Drawable icon = mPackageManager.loadUnbadgedItemIcon(itemInfo, null);

        assertThat(comparePixelData(expectedIcon, icon)).isTrue();
    }

    @Test
    public void testFromAppInfo() throws PackageManager.NameNotFoundException {
        // size_48x48 is defined as the application icon in this test's AndroidManifest.xml
        Drawable expectedIcon = mContext.getDrawable(R.drawable.size_48x48);

        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        PackageItemInfo itemInfo = new PackageItemInfo();
        itemInfo.icon = 0;

        Drawable icon = mPackageManager.loadUnbadgedItemIcon(itemInfo, appInfo);

        assertThat(comparePixelData(expectedIcon, icon)).isTrue();
    }

    @Test
    public void testFromActivity() throws PackageManager.NameNotFoundException {
        // start is defined as the Activity icon in this test's AndroidManifest.xml
        Drawable expectedIcon = mContext.getDrawable(R.drawable.start);
        // pass is NOT defined as the Activity icon in this test's AndroidManifest.xml. But it has
        // the same width and the same height as start.
        Drawable wrongIcon = mContext.getDrawable(R.drawable.pass);

        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));
        PackageItemInfo itemInfo = getTestItemInfo();

        assertThat(itemInfo.icon).isEqualTo(R.drawable.start);

        Drawable icon = mPackageManager.loadUnbadgedItemIcon(itemInfo, appInfo);

        assertThat(comparePixelData(wrongIcon, icon)).isFalse();
        assertThat(comparePixelData(expectedIcon, icon)).isTrue();
    }

    @Test
    public void testDelegatedSystemDefault() {
        Drawable expectedIcon = mContext.getDrawable(
                com.android.internal.R.drawable.sym_def_app_icon);

        PackageItemInfo itemInfo = new PackageItemInfo();
        itemInfo.icon = 0;

        Drawable icon = itemInfo.loadUnbadgedIcon(mPackageManager);

        assertThat(comparePixelData(expectedIcon, icon)).isTrue();
    }

    @Test
    public void testDelegatedFromAppInfo() throws PackageManager.NameNotFoundException {
        // size_48x48 is defined as the app icon in this test's AndroidManifest.xml
        Drawable expectedIcon = mContext.getDrawable(R.drawable.size_48x48);

        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(0));

        Drawable icon = appInfo.loadUnbadgedIcon(mPackageManager);

        assertThat(comparePixelData(expectedIcon, icon)).isTrue();
    }

    @Test
    public void testDelegatedFromActivity() throws PackageManager.NameNotFoundException {
        // start is defined as the Activity icon in this test's AndroidManifest.xml
        Drawable expectedIcon = mContext.getDrawable(R.drawable.start);
        // pass is NOT defined as the Activity icon in this test's AndroidManifest.xml. But it has
        // the same width and the same height as start.
        Drawable wrongIcon = mContext.getDrawable(R.drawable.pass);

        PackageItemInfo itemInfo = getTestItemInfo();

        assertThat(itemInfo.icon).isEqualTo(R.drawable.start);

        Drawable icon = itemInfo.loadUnbadgedIcon(mPackageManager);

        assertThat(comparePixelData(wrongIcon, icon)).isFalse();
        assertThat(comparePixelData(expectedIcon, icon)).isTrue();
    }

    private PackageItemInfo getTestItemInfo() throws PackageManager.NameNotFoundException {
        ComponentName componentName = new ComponentName(PACKAGE_NAME, ACTIVITY_NAME);
        return mPackageManager.getActivityInfo(componentName,
                PackageManager.ComponentInfoFlags.of(0));
    }

    private boolean comparePixelData(Drawable one, Drawable two) {
        // If they pass an object equals, skip expensive pixel check
        if (one.equals(two)) {
            return true;
        }

        return comparePixelData(drawableToBitmap(one), drawableToBitmap(two));
    }

    private boolean comparePixelData(Bitmap one, Bitmap two) {
        if (one.getWidth() != two.getWidth()) {
            return false;
        }

        if (one.getHeight() != two.getHeight()) {
            return false;
        }

        for (int x = 0; x < one.getWidth(); x++) {
            for (int y = 0; y < one.getHeight(); y++) {
                if (one.getPixel(x, y) != two.getPixel(x, y)) {
                    return false;
                }
            }
        }

        return true;
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        final int width = drawable.getIntrinsicWidth();
        final int height = drawable.getIntrinsicHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(/* left */ 0, /* top */ 0, width, height);
        drawable.draw(canvas);
        return bitmap;
    }
}
