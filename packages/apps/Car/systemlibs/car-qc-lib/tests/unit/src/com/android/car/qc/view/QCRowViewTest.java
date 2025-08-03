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

import static com.android.car.qc.QCItem.QC_TYPE_ACTION_SWITCH;
import static com.android.car.qc.QCItem.QC_TYPE_ACTION_TOGGLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Icon;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCRow;
import com.android.car.qc.QCSlider;
import com.android.car.qc.R;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QCRowViewTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private QCRowView mView;

    @Before
    public void setUp() {
        mView = new QCRowView(mContext);
    }

    @Test
    public void setRow_null_notVisible() {
        mView.setRow(null);
        assertThat(mView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void setRow_notNull_visible() {
        QCRow row = new QCRow.Builder().build();
        mView.setRow(row);
        assertThat(mView.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void setRow_setsTitle() {
        String title = "TEST_TITLE";
        QCRow row = new QCRow.Builder().setTitle(title).build();
        mView.setRow(row);
        TextView titleView = mView.findViewById(R.id.qc_title);
        assertThat(titleView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(titleView.getText().toString()).isEqualTo(title);
    }

    @Test
    public void setRow_setsSubtitle() {
        String subtitle = "TEST_TITLE";
        QCRow row = new QCRow.Builder().setSubtitle(subtitle).build();
        mView.setRow(row);
        TextView subtitleView = mView.findViewById(R.id.qc_summary);
        assertThat(subtitleView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(subtitleView.getText().toString()).isEqualTo(subtitle);
    }

    @Test
    public void setRow_setsIcon() {
        Icon icon = Icon.createWithResource(mContext, android.R.drawable.btn_star);
        QCRow row = new QCRow.Builder().setIcon(icon).build();
        mView.setRow(row);
        ImageView iconView = mView.findViewById(R.id.qc_icon);
        assertThat(iconView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(iconView.getDrawable()).isNotNull();
    }

    @Test
    @UiThreadTest
    public void setRow_createsStartItems() {
        QCRow row = new QCRow.Builder()
                .addStartItem(new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH).build())
                .addStartItem(new QCActionItem.Builder(QC_TYPE_ACTION_TOGGLE).build())
                .build();
        mView.setRow(row);
        LinearLayout startContainer = mView.findViewById(R.id.qc_row_start_items);
        assertThat(startContainer.getChildCount()).isEqualTo(2);
        assertThat((View) startContainer.getChildAt(0).findViewById(
                android.R.id.switch_widget)).isNotNull();
        assertThat((View) startContainer.getChildAt(1).findViewById(
                R.id.qc_toggle_button)).isNotNull();
    }

    @Test
    @UiThreadTest
    public void setRow_createsEndItems() {
        QCRow row = new QCRow.Builder()
                .addEndItem(new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH).build())
                .addEndItem(new QCActionItem.Builder(QC_TYPE_ACTION_TOGGLE).build())
                .build();
        mView.setRow(row);
        LinearLayout endContainer = mView.findViewById(R.id.qc_row_end_items);
        assertThat(endContainer.getChildCount()).isEqualTo(2);
        assertThat((View) endContainer.getChildAt(0).findViewById(
                android.R.id.switch_widget)).isNotNull();
        assertThat((View) endContainer.getChildAt(1).findViewById(
                R.id.qc_toggle_button)).isNotNull();
    }

    @Test
    public void setRow_noSlider_sliderViewNotVisible() {
        QCRow row = new QCRow.Builder().build();
        mView.setRow(row);
        LinearLayout sliderContainer = mView.findViewById(R.id.qc_seekbar_wrapper);
        assertThat(sliderContainer.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    @UiThreadTest
    public void setRow_hasSlider_sliderViewVisible() {
        QCRow row = new QCRow.Builder()
                .addSlider(new QCSlider.Builder().build())
                .build();
        mView.setRow(row);
        LinearLayout sliderContainer = mView.findViewById(R.id.qc_seekbar_wrapper);
        assertThat(sliderContainer.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onRowClick_firesAction() throws PendingIntent.CanceledException {
        PendingIntent action = mock(PendingIntent.class);
        QCRow row = new QCRow.Builder().setPrimaryAction(action).build();
        mView.setRow(row);
        mView.findViewById(R.id.qc_row_content).performClick();
        verify(action).send(any(Context.class), anyInt(), eq(null));
    }

    @Test
    public void onSwitchClick_firesAction() throws PendingIntent.CanceledException {
        PendingIntent action = mock(PendingIntent.class);
        QCRow row = new QCRow.Builder()
                .addEndItem(
                        new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH).setAction(action).build())
                .build();
        mView.setRow(row);
        LinearLayout endContainer = mView.findViewById(R.id.qc_row_end_items);
        assertThat(endContainer.getChildCount()).isEqualTo(1);
        endContainer.getChildAt(0).performClick();
        verify(action).send(any(Context.class), anyInt(), any(Intent.class));
    }

    @Test
    @UiThreadTest
    public void onToggleClick_firesAction() throws PendingIntent.CanceledException {
        PendingIntent action = mock(PendingIntent.class);
        QCRow row = new QCRow.Builder()
                .addEndItem(
                        new QCActionItem.Builder(QC_TYPE_ACTION_TOGGLE).setAction(action).build())
                .build();
        mView.setRow(row);
        LinearLayout endContainer = mView.findViewById(R.id.qc_row_end_items);
        assertThat(endContainer.getChildCount()).isEqualTo(1);
        endContainer.getChildAt(0).performClick();
        verify(action).send(any(Context.class), anyInt(), any(Intent.class));
    }

    @Test
    @UiThreadTest
    public void onSliderChange_firesAction() throws PendingIntent.CanceledException {
        PendingIntent action = mock(PendingIntent.class);
        QCRow row = new QCRow.Builder()
                .addSlider(new QCSlider.Builder().setInputAction(action).build())
                .build();
        mView.setRow(row);
        SeekBar seekBar = mView.findViewById(R.id.qc_seekbar);
        seekBar.setProgress(50);
        MotionEvent motionEvent = ExtendedMockito.mock(MotionEvent.class);
        ExtendedMockito.when(motionEvent.getAction()).thenReturn(MotionEvent.ACTION_UP);
        seekBar.onTouchEvent(motionEvent);
        verify(action).send(any(Context.class), anyInt(), any(Intent.class));
    }

    @Test
    @UiThreadTest
    public void setRow_switchViewThumbTintList() {
        PendingIntent action = mock(PendingIntent.class);
        QCRow row = new QCRow.Builder()
                .addEndItem(
                        new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH).setAction(action).build())
                .build();
        mView.setRow(row);
        LinearLayout endContainer = mView.findViewById(R.id.qc_row_end_items);
        assertThat(endContainer.getChildCount()).isEqualTo(1);
        Switch switchView = (Switch) endContainer.getChildAt(0);
        assertThat(switchView.getThumbTintList()).isNotNull();

        ColorStateList switchColorStateList = switchView.getThumbTintList();
        int[] enabledState = {android.R.attr.state_enabled};
        int[] disabledState = {-android.R.attr.state_enabled};
        assertThat(switchColorStateList.getColorForState(enabledState, 0)).isNotEqualTo(
                switchColorStateList.getColorForState(disabledState, 0));
    }

    @Test
    @UiThreadTest
    public void setRow_sliderViewThumbTintList() {
        PendingIntent action = mock(PendingIntent.class);
        QCRow row = new QCRow.Builder()
                .addSlider(new QCSlider.Builder().setInputAction(action).build())
                .build();
        mView.setRow(row);
        SeekBar seekBar = mView.findViewById(R.id.qc_seekbar);
        assertThat(seekBar.getThumbTintList()).isNotNull();

        ColorStateList seekBarColorStateList = seekBar.getThumbTintList();
        int[] enabledState = {android.R.attr.state_enabled};
        int[] disabledState = {-android.R.attr.state_enabled};
        assertThat(seekBarColorStateList.getColorForState(enabledState, 0)).isNotEqualTo(
                seekBarColorStateList.getColorForState(disabledState, 0));
    }
}
