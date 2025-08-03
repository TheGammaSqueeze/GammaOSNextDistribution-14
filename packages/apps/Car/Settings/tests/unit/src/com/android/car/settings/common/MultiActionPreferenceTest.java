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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.PreferenceViewHolder;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class MultiActionPreferenceTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private View mRootView;
    private MultiActionPreference mPref;
    private PreferenceViewHolder mHolder;

    @Mock
    private TypedArray mTypedArray;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mRootView = View.inflate(mContext, R.layout.multi_action_preference, null);
        mHolder = PreferenceViewHolder.createInstanceForTests(mRootView);
        // Mocked int and boolean values from TypedArray seem to be 0, so default values can't be
        // used. Must manually set tested values
        doReturn(mTypedArray).when(mContext).obtainStyledAttributes(nullable(AttributeSet.class),
                eq(R.styleable.MultiActionPreference));
    }

    @Test
    @UiThreadTest
    public void onBindViewHolder_actionItemsNullByDefault() {
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_one),
                anyInt())).thenReturn(-1);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_two),
                anyInt())).thenReturn(-1);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_three),
                anyInt())).thenReturn(-1);
        mPref = new MultiActionPreference(mContext);
        mPref.onBindViewHolder(mHolder);

        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM1))
                .isNull();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM2))
                .isNull();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM3))
                .isNull();
    }

    @Test
    @UiThreadTest
    public void onBindViewHolder_toggleButtons_attributesVisible() {
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_one),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_two),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_three),
                anyInt())).thenReturn(0);
        when(mTypedArray.getBoolean(eq(R.styleable.MultiActionPreference_action_item_one_shown),
                anyBoolean())).thenReturn(true);
        when(mTypedArray.getBoolean(eq(R.styleable.MultiActionPreference_action_item_two_shown),
                anyBoolean())).thenReturn(true);
        when(mTypedArray.getBoolean(eq(R.styleable.MultiActionPreference_action_item_three_shown),
                anyBoolean())).thenReturn(true);
        mPref = new MultiActionPreference(mContext);
        mPref.onBindViewHolder(mHolder);

        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM1)
                .isVisible()).isTrue();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM2)
                .isVisible()).isTrue();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM3)
                .isVisible()).isTrue();
    }

    @Test
    @UiThreadTest
    public void onBindViewHolder_toggleButtons_attributesEnabled() {
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_one),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_two),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_three),
                anyInt())).thenReturn(0);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_one_enabled), anyBoolean()))
                .thenReturn(true);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_two_enabled), anyBoolean()))
                .thenReturn(true);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_three_enabled), anyBoolean()))
                .thenReturn(true);
        mPref = new MultiActionPreference(mContext);
        mPref.onBindViewHolder(mHolder);

        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM1)
                .isEnabled()).isTrue();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM2)
                .isEnabled()).isTrue();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM3)
                .isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void onBindViewHolder_toggleButtons_attributesNotVisible() {
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_one),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_two),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_three),
                anyInt())).thenReturn(0);
        when(mTypedArray.getBoolean(eq(R.styleable.MultiActionPreference_action_item_one_shown),
                anyBoolean())).thenReturn(false);
        when(mTypedArray.getBoolean(eq(R.styleable.MultiActionPreference_action_item_two_shown),
                anyBoolean())).thenReturn(false);
        when(mTypedArray.getBoolean(eq(R.styleable.MultiActionPreference_action_item_three_shown),
                anyBoolean())).thenReturn(false);
        mPref = new MultiActionPreference(mContext);
        mPref.onBindViewHolder(mHolder);

        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM1)
                .isVisible()).isFalse();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM2)
                .isVisible()).isFalse();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM3)
                .isVisible()).isFalse();
    }

    @Test
    @UiThreadTest
    public void onBindViewHolder_toggleButtons_attributesNotEnabled() {
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_one),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_two),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_three),
                anyInt())).thenReturn(0);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_one_enabled), anyBoolean()))
                .thenReturn(false);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_two_enabled), anyBoolean()))
                .thenReturn(false);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_three_enabled), anyBoolean()))
                .thenReturn(false);
        mPref = new MultiActionPreference(mContext);
        mPref.onBindViewHolder(mHolder);

        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM1)
                .isEnabled()).isFalse();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM2)
                .isEnabled()).isFalse();
        assertThat(mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM3)
                .isEnabled()).isFalse();
    }

    @Test
    @UiThreadTest
    public void toggleButtons_onClick_checkedStateChanges() {
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_one),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_two),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInt(eq(R.styleable.MultiActionPreference_action_item_three),
                anyInt())).thenReturn(0);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_one_enabled), anyBoolean()))
                .thenReturn(true);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_two_enabled), anyBoolean()))
                .thenReturn(true);
        when(mTypedArray.getBoolean(
                eq(R.styleable.MultiActionPreference_action_item_three_enabled), anyBoolean()))
                .thenReturn(true);
        mPref = new MultiActionPreference(mContext);
        // OnClickListeners needed for check state to change
        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM1)).setOnClickListener(c -> {});
        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM2)).setOnClickListener(c -> {});
        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM3)).setOnClickListener(c -> {});
        mPref.onBindViewHolder(mHolder);

        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM1)).isChecked()).isTrue();
        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM2)).isChecked()).isTrue();
        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM3)).isChecked()).isTrue();

        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM1)).onClick();
        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM2)).onClick();
        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM3)).onClick();

        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM1)).isChecked()).isFalse();
        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM2)).isChecked()).isFalse();
        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM3)).isChecked()).isFalse();
    }

    @Test
    @UiThreadTest
    public void toggleButtons_onClickRestricted_checkedStateDoesNotChange() {
        when(mTypedArray.getInteger(eq(R.styleable.MultiActionPreference_action_item_one),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInteger(eq(R.styleable.MultiActionPreference_action_item_two),
                anyInt())).thenReturn(0);
        when(mTypedArray.getInteger(eq(R.styleable.MultiActionPreference_action_item_three),
                anyInt())).thenReturn(0);
        mPref = new MultiActionPreference(mContext);
        mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM1)
                .setRestricted(true);
        mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM2)
                .setRestricted(true);
        mPref.getActionItem(MultiActionPreference.ActionItem.ACTION_ITEM3)
                .setRestricted(true);
        mPref.onBindViewHolder(mHolder);

        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM1)).isChecked()).isTrue();
        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM2)).isChecked()).isTrue();
        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM3)).isChecked()).isTrue();

        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM1)).onClick();
        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM2)).onClick();
        ((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM3)).onClick();

        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM1)).isChecked()).isTrue();
        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM2)).isChecked()).isTrue();
        assertThat(((ToggleButtonActionItem) mPref.getActionItem(
                MultiActionPreference.ActionItem.ACTION_ITEM3)).isChecked()).isTrue();
    }
}
