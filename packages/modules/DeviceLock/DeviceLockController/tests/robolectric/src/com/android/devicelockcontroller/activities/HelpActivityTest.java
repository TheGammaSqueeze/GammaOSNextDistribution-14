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

package com.android.devicelockcontroller.activities;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class HelpActivityTest {

    private static final String TEST_URL = "https://www.google.com";
    private static final String EXTRA_BAD_PARAM = "test-param";
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void helpActivity_loadUrlSuccess() {
        Intent intent = new Intent(mContext, HelpActivity.class);
        intent.putExtra(HelpActivity.EXTRA_URL_PARAM, TEST_URL);
        HelpActivity activity = Robolectric.buildActivity(HelpActivity.class, intent).setup().get();
        assertThat(shadowOf(activity.getWebView()).getLastLoadedUrl()).isEqualTo(TEST_URL);
    }

    @Test
    public void helpActivity_finishWhenExtrasNotPresent() {
        HelpActivity activity = Robolectric.buildActivity(HelpActivity.class).setup().get();
        assertThat(activity.isFinishing()).isTrue();
    }

    @Test
    public void helpActivity_finishWhenURLNotPresent() {
        Intent intent = new Intent(mContext, HelpActivity.class);
        intent.putExtra(EXTRA_BAD_PARAM, TEST_URL);
        HelpActivity activity = Robolectric.buildActivity(HelpActivity.class, intent).setup().get();
        assertThat(activity.isFinishing()).isTrue();
    }
}
