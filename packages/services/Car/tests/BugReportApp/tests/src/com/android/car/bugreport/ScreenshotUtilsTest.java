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

package com.android.car.bugreport;

import static com.google.common.truth.Truth.assertThat;

import android.car.Car;
import android.content.Context;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public class ScreenshotUtilsTest {
    private static final String TAG = ScreenshotUtils.class.getSimpleName();

    private Context mContext;
    private Car mCar;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mCar = Car.createCar(mContext);
        deleteScreenshotDir();
    }

    @Test
    public void test_getScreenshotDir_returnsScreenshotDir() throws Exception {
        String screenshotDir = ScreenshotUtils.getScreenshotDir();

        assertThat(screenshotDir).endsWith("screenshots");
        assertThat(new File(screenshotDir).exists()).isTrue();
    }

    @Test
    public void test_takeScreenshot_takesAndStoresScreenshot() throws Exception {
        ScreenshotUtils.takeScreenshot(mContext, mCar);

        File screenshotDir = new File(ScreenshotUtils.getScreenshotDir());


        assertThat(screenshotDir.exists()).isTrue();
        String[] screenshots = screenshotDir.list();
        assertThat(screenshots).hasLength(1);
        assertThat(screenshots[0]).matches(
                "extra_screenshot_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}.png");
    }

    private void deleteScreenshotDir() {
        boolean result = true;
        String screenshotDirPath = ScreenshotUtils.getScreenshotDir();
        File screenshotDir = new File(screenshotDirPath);
        if (screenshotDir.exists() && screenshotDir.isDirectory()) {
            String[] children = screenshotDir.list();
            for (String child : children) {
                result &= new File(screenshotDir, child).delete();
            }
        }
        result &= screenshotDir.delete();
        Log.d(TAG, "deleteScreenshotDir result = " + result);
    }
}
