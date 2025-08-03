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

package com.android.devicelockcontroller.activities;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.content.Context;
import android.text.SpannedString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;
import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
public class UrlTextPreferenceTest {

    @Test
    public void onBindViewHolder_checkClickableLinkIsCorrect() {
        Context applicationContext = ApplicationProvider.getApplicationContext();
        UrlTextPreference preference = new UrlTextPreference(applicationContext, /* attrs= */ null);
        String expectedUrl = "www.test.com";
        String testUrlText = String.format(
                applicationContext.getString(R.string.settings_contact_provider),
                "test provider", expectedUrl);
        Activity emptyActivity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView title = new TextView(emptyActivity);
        preference.setTitle(testUrlText);
        preference.setViewId(android.R.id.title);
        PreferenceViewHolder viewHolder = PreferenceViewHolder.createInstanceForTests(title);

        preference.onBindViewHolder(viewHolder);

        assertThat(title.getMovementMethod()).isInstanceOf(LinkMovementMethod.class);

        SpannedString actualSpannedString = SpannedString.valueOf(title.getText());
        ClickableSpan[] spans = actualSpannedString.getSpans(0, actualSpannedString.length(),
                ClickableSpan.class);
        assertThat(spans.length).isAtLeast(1);
        spans[0].onClick(title);
        assertThat(Shadows.shadowOf(emptyActivity).getNextStartedActivity().getStringExtra(
                HelpActivity.EXTRA_URL_PARAM)).isEqualTo(expectedUrl);
    }
}
