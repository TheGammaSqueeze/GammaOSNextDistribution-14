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

package com.android.car.settings.common;

import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_ICON_BACKGROUND_ARGB;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_ICON_BACKGROUND_HINT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TopLevelIconTest {
    private Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void createIcon_shouldSetBackgroundAndInset() {
        TopLevelIcon icon = new TopLevelIcon(mContext, new ColorDrawable(Color.BLACK));

        assertThat(icon.getNumberOfLayers()).isEqualTo(2);
        assertThat(icon.getDrawable(0)).isInstanceOf(TopLevelIconShapeDrawable.class);
    }

    @Test
    public void setBackgroundColor_shouldUpdateTintList() {
        TopLevelIcon icon = spy(new TopLevelIcon(mContext, new ColorDrawable(Color.BLACK)));
        GradientDrawable background = mock(GradientDrawable.class);
        when(icon.getDrawable(0)).thenReturn(background);
        icon.setBackgroundColor(Color.BLUE);

        verify(background).setTintList(any(ColorStateList.class));
    }

    @Test
    public void injectedPreferenceWithBackgroundColorRawValue_shouldSetColor() {
        Bundle metaData = new Bundle();
        metaData.putInt(META_DATA_PREFERENCE_ICON_BACKGROUND_ARGB, 0xff0000);
        TopLevelIcon icon = new TopLevelIcon(mContext, new ColorDrawable(Color.BLACK));
        icon.setBackgroundColor(mContext, metaData, mContext.getPackageName());

        assertThat(icon.mBackgroundColorStateList.getDefaultColor()).isEqualTo(0xff0000);
    }

    @Test
    public void injectedPreferenceWithoutBackgroundColor_shouldSetDefaultBackgroundColor() {
        Bundle metaData = new Bundle();
        TopLevelIcon icon = new TopLevelIcon(mContext, new ColorDrawable(Color.BLACK));
        icon.setBackgroundColor(mContext, metaData, mContext.getPackageName());

        assertThat(icon.mBackgroundColorStateList.getDefaultColor()).isEqualTo(
                mContext.getColor(R.color.top_level_injected_default_background));
    }

    @Test
    public void injectedPreferenceWithBackgroundColorHintValue_shouldSetColor() {
        Bundle metaData = new Bundle();
        metaData.putInt(META_DATA_PREFERENCE_ICON_BACKGROUND_HINT, android.R.color.darker_gray);
        TopLevelIcon icon = new TopLevelIcon(mContext, new ColorDrawable(Color.BLACK));
        icon.setBackgroundColor(mContext, metaData, mContext.getPackageName());

        assertThat(icon.mBackgroundColorStateList.getDefaultColor()).isEqualTo(
                mContext.getColor(android.R.color.darker_gray));
    }

    @Test
    public void getConstantState_returnCorrectState() {
        TopLevelIcon icon = new TopLevelIcon(mContext, new ColorDrawable(Color.BLACK));
        icon.setBackgroundColor(Color.YELLOW);

        TopLevelIcon.AdaptiveConstantState state =
                (TopLevelIcon.AdaptiveConstantState) icon.getConstantState();

        assertThat(state.mColor).isEqualTo(Color.YELLOW);
        assertThat(state.mContext).isEqualTo(mContext);
    }
}
