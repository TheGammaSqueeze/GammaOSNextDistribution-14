/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class WebActivityTest {
    private static final String TEST_URL = "http://www.test.com/support";

    @Rule
    public ActivityTestRule<WebActivity> mActivityRule = new ActivityTestRule<>(
            WebActivity.class, true, false);
    private Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    @Test
    public void testNoUrl() {
        Intent intent = WebActivity.createIntent(mInstrumentation.getTargetContext(), null);

        assertThat(intent).isNull();
    }

    @Ignore("b/181323689")
    @Test
    public void testUrlLaunched() {
        final AtomicReference<String> urlRef = new AtomicReference<>(null);

        Activity activity = mActivityRule.launchActivity(
                WebActivity.createIntent(mInstrumentation.getTargetContext(),
                        TEST_URL));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                urlRef.set(((WebView) ((ViewGroup) activity
                        .findViewById(android.R.id.content)).getChildAt(0)).getUrl()));

        assertThat(activity.isFinishing()).isFalse();
        assertThat(urlRef.get()).isEqualTo(TEST_URL);
    }
}