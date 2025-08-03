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

package com.android.car.qc.view;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;
import android.widget.TextView;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCRow;
import com.android.car.qc.QCTile;
import com.android.car.qc.R;
import com.android.car.ui.uxr.DrawableStateToggleButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QCTileViewTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private QCTileView mView;

    @Before
    public void setUp() {
        mView = new QCTileView(mContext);
    }

    @Test
    public void onChanged_null_noViews() {
        mView.onChanged(null);
        assertThat(mView.getChildCount()).isEqualTo(0);
    }

    @Test
    public void onChanged_invalidType_throwsIllegalArgumentException() {
        QCRow row = new QCRow.Builder().build();
        assertThrows(IllegalArgumentException.class,
                () -> mView.onChanged(row));
    }

    @Test
    @UiThreadTest
    public void onChanged_setsSubtitleView() {
        String subtitle = "TEST_SUBTITLE";
        QCTile tile = new QCTile.Builder().setSubtitle(subtitle).build();
        mView.onChanged(tile);
        TextView subtitleView = mView.findViewById(android.R.id.summary);
        assertThat(subtitleView.getText().toString()).isEqualTo(subtitle);
    }

    @Test
    @UiThreadTest
    public void onChanged_setsButtonState() {
        QCTile tile = new QCTile.Builder().setChecked(true).setEnabled(true).build();
        mView.onChanged(tile);
        DrawableStateToggleButton button = mView.findViewById(R.id.qc_tile_toggle_button);
        assertThat(button.isEnabled()).isTrue();
        assertThat(button.isChecked()).isTrue();
    }

    @Test
    @UiThreadTest
    public void onChanged_setsIcon() {
        Icon icon = Icon.createWithResource(mContext, android.R.drawable.btn_star);
        QCTile tile = new QCTile.Builder().setIcon(icon).build();
        mView.onChanged(tile);
        DrawableStateToggleButton button = mView.findViewById(R.id.qc_tile_toggle_button);
        Drawable buttonDrawable = button.getButtonDrawable();
        assertThat(buttonDrawable).isNotNull();
        assertThat(buttonDrawable instanceof LayerDrawable).isTrue();
        assertThat(((LayerDrawable) buttonDrawable).getNumberOfLayers()).isEqualTo(2);
    }

    @Test
    @UiThreadTest
    public void onClick_firesAction() throws PendingIntent.CanceledException {
        PendingIntent action = mock(PendingIntent.class);
        QCTile tile = new QCTile.Builder().setChecked(false).setAction(action).build();
        mView.onChanged(tile);
        mView.findViewById(R.id.qc_tile_wrapper).performClick();
        DrawableStateToggleButton button = mView.findViewById(R.id.qc_tile_toggle_button);
        assertThat(button.isChecked()).isTrue();
        verify(action).send(any(Context.class), anyInt(), any(Intent.class));
    }
}
