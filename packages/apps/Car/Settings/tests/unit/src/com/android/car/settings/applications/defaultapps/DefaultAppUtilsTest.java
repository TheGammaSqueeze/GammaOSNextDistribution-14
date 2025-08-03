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

package com.android.car.settings.applications.defaultapps;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DefaultAppUtilsTest {

    @Test
    public void setSafeIcon_smallerThanLimit() {
        Context context = ApplicationProvider.getApplicationContext();
        Drawable drawable = context.getDrawable(R.drawable.test_icon_2);
        int height = drawable.getMinimumHeight();
        int width = drawable.getMinimumWidth();

        // Set to some value larger than current height or width;
        int testMaxDimensions = Math.max(height, width) + 1;
        Drawable icon = DefaultAppUtils.getSafeIcon(drawable, testMaxDimensions);

        assertThat(icon.getMinimumHeight()).isEqualTo(height);
        assertThat(icon.getMinimumWidth()).isEqualTo(width);
    }

    @Test
    public void setSafeIcon_largerThanLimit() {
        Context context = ApplicationProvider.getApplicationContext();
        Drawable drawable = context.getDrawable(R.drawable.test_icon_2);
        int height = drawable.getMinimumHeight();
        int width = drawable.getMinimumWidth();

        // Set to some value smaller than current height or width;
        int testMaxDimensions = Math.min(height, width) - 5;
        Drawable icon = DefaultAppUtils.getSafeIcon(drawable, testMaxDimensions);

        assertThat(icon.getMinimumHeight()).isEqualTo(testMaxDimensions);
        assertThat(icon.getMinimumWidth()).isEqualTo(testMaxDimensions);
    }

    @Test
    public void setSafeIcon_noChangeForVectorDrawable() {
        Context context = ApplicationProvider.getApplicationContext();
        Drawable drawable = context.getDrawable(R.drawable.test_icon);
        int height = drawable.getMinimumHeight();
        int width = drawable.getMinimumWidth();

        // Set to some value smaller than current height or width;
        int testMaxDimensions = Math.min(height, width) - 1;
        Drawable icon = DefaultAppUtils.getSafeIcon(drawable, testMaxDimensions);

        assertThat(icon.getMinimumHeight()).isEqualTo(height);
        assertThat(icon.getMinimumWidth()).isEqualTo(width);
    }
}
