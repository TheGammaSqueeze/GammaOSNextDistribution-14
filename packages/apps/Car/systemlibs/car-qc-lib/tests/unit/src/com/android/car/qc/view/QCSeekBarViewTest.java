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

package com.android.car.qc.view;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.SeekBar;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class QCSeekBarViewTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final QCSeekBarView mView = new QCSeekBarView(mContext);

    @Mock
    private MotionEvent mMotionEvent;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_UP);
    }

    @Test
    public void enabled_standardTouchEvent() {
        assertThat(mView.onTouchEvent(mMotionEvent)).isTrue();
    }

    @Test
    public void disabled_standardTouchEvent() {
        mView.setEnabled(false);

        assertThat(mView.onTouchEvent(mMotionEvent)).isFalse();
    }

    @Test
    public void clickableWhileDisabled_customTouchEvent() {
        mView.setEnabled(false);
        mView.setClickableWhileDisabled(true);

        assertThat(mView.onTouchEvent(mMotionEvent)).isTrue();
    }

    @Test
    public void clickableWhileDisabled_actionDown_doesNotTriggerDisabledClickListener() {
        AtomicBoolean called = new AtomicBoolean(false);
        Consumer<SeekBar> disabledClickListener = seekBar -> called.set(true);
        mView.setEnabled(false);
        mView.setClickableWhileDisabled(true);
        mView.setDisabledClickListener(disabledClickListener);
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_DOWN);

        assertThat(mView.onTouchEvent(mMotionEvent)).isTrue();
        assertThat(called.get()).isFalse();
    }

    @Test
    public void clickableWhileDisabled_actionUp_triggersDisabledClickListener() {
        AtomicBoolean called = new AtomicBoolean(false);
        Consumer<SeekBar> disabledClickListener = seekBar -> called.set(true);
        mView.setEnabled(false);
        mView.setClickableWhileDisabled(true);
        mView.setDisabledClickListener(disabledClickListener);

        assertThat(mView.onTouchEvent(mMotionEvent)).isTrue();
        assertThat(called.get()).isTrue();
    }
}
