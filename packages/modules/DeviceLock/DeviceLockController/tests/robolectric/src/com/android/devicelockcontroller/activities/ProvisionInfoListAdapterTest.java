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
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.SpannableString;
import android.text.SpannedString;
import android.text.style.ClickableSpan;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.activities.ProvisionInfo.ProvisionInfoType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class ProvisionInfoListAdapterTest {
    private static final List<ProvisionInfo> TEST_PROVISION_INFOS = List.of(
            DeviceFinancingProvisionInfoViewModel.PROVISION_INFOS);
    private static final String TEST_PROVIDER = "Test Provider";
    private static final String TEST_URL = "testUrl";
    private ProvisionInfoListAdapter mAdapter;
    private TestDeviceLockControllerApplication mTestApp;
    private final Activity mTestActivity = Robolectric.buildActivity(Activity.class).setup().get();
    private TextView mTextView;
    private ProvisionInfoListAdapter.ProvisionInfoViewHolder mProvisionInfoViewHolder;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        mAdapter = new ProvisionInfoListAdapter();
        mTextView = new TextView(mTestActivity);
        mTextView.setId(R.id.text_view_item_provision_info);
        mProvisionInfoViewHolder = new ProvisionInfoListAdapter.ProvisionInfoViewHolder(mTextView);
        mAdapter.submitList(TEST_PROVISION_INFOS);
    }

    @Test
    public void onCreateViewHolder_viewHolderContainsExpectedView() {
        ProvisionInfoListAdapter.ProvisionInfoViewHolder viewHolder = mAdapter.onCreateViewHolder(
                new ViewGroup(mTestApp) {
                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    }
                }, /* viewType= */ 0);
        assertThat(viewHolder.itemView.getSourceLayoutResId()).isEqualTo(
                R.layout.item_provision_info);
    }

    @Test
    public void onBindViewHolder_regularView_noProviderName_setEmptyText() {
        int position = 0;
        ProvisionInfo regularInfo = TEST_PROVISION_INFOS.get(position);
        String emptyProviderName = "";
        regularInfo.setProviderName(emptyProviderName);

        assume().that(regularInfo.getType()).isEqualTo(ProvisionInfoType.REGULAR);

        mAdapter.onBindViewHolder(mProvisionInfoViewHolder, position);

        assertThat(mTextView.getText().toString()).isEqualTo(emptyProviderName);
        assertDrawableId(regularInfo.getDrawableId());
    }

    @Test
    public void onBindViewHolder_regularView_providerNameAvailable_setText() {
        int position = 0;
        ProvisionInfo regularInfo = TEST_PROVISION_INFOS.get(position);
        regularInfo.setProviderName(TEST_PROVIDER);

        assume().that(regularInfo.getType()).isEqualTo(ProvisionInfoType.REGULAR);

        mAdapter.onBindViewHolder(mProvisionInfoViewHolder, position);

        String text = String.format(mTestActivity.getString(regularInfo.getTextId()),
                TEST_PROVIDER);
        assertThat(mTextView.getText().toString()).isEqualTo(text);
        assertDrawableId(regularInfo.getDrawableId());
    }

    @Test
    public void onBindViewHolder_urlProvisionInfo_urlNotAvailable_setTextWithEmptyUrl() {
        int position = 1;
        ProvisionInfo urlProvisionInfo = TEST_PROVISION_INFOS.get(position);
        urlProvisionInfo.setProviderName(TEST_PROVIDER);
        String testEmptyUrl = "";
        urlProvisionInfo.setUrl(testEmptyUrl);

        assume().that(urlProvisionInfo.getType()).isNotEqualTo(ProvisionInfoType.REGULAR);

        mAdapter.onBindViewHolder(mProvisionInfoViewHolder, position);

        CharSequence actualText = mTextView.getText();
        assertThat(actualText.toString()).isEqualTo(
                getExpectedUrlString(testEmptyUrl, urlProvisionInfo.getTextId()));
        assertUrlIsExpected(actualText, testEmptyUrl);

        assertDrawableId(urlProvisionInfo.getDrawableId());
    }

    @Test
    public void onBindViewHolder_urlProvisionInfo_urlAvailable_setTextWithUrl() {
        int position = 1;
        ProvisionInfo urlProvisionInfo = TEST_PROVISION_INFOS.get(position);
        urlProvisionInfo.setProviderName(TEST_PROVIDER);
        urlProvisionInfo.setUrl(TEST_URL);

        assume().that(urlProvisionInfo.getType()).isNotEqualTo(ProvisionInfoType.REGULAR);

        mAdapter.onBindViewHolder(mProvisionInfoViewHolder, position);

        CharSequence actualText = mTextView.getText();
        assertThat(actualText.toString()).isEqualTo(
                getExpectedUrlString(TEST_URL, urlProvisionInfo.getTextId()));
        assertUrlIsExpected(actualText, TEST_URL);
        assertDrawableId(urlProvisionInfo.getDrawableId());
    }

    private String getExpectedUrlString(String expectedUrl, int expectedTextId) {
        String formattedString = String.format(
                mTestActivity.getString(expectedTextId), TEST_PROVIDER, expectedUrl);
        return new SpannableString(
                Html.fromHtml(formattedString, Html.FROM_HTML_MODE_COMPACT)).toString();
    }

    private void assertUrlIsExpected(CharSequence actualText, String expectedUrl) {
        SpannedString actualSpannedString = SpannedString.valueOf(actualText);
        ClickableSpan[] spans = actualSpannedString.getSpans(0, actualSpannedString.length(),
                ClickableSpan.class);
        assertThat(spans.length).isAtLeast(1);
        spans[0].onClick(mTextView);
        assertThat(Shadows.shadowOf(mTestActivity).getNextStartedActivity().getStringExtra(
                HelpActivity.EXTRA_URL_PARAM)).isEqualTo(expectedUrl);
    }

    private void assertDrawableId(int expectedDrawableId) {
        Drawable[] compoundDrawablesRelative = mTextView.getCompoundDrawablesRelative();
        assertThat(compoundDrawablesRelative.length).isAtLeast(1);
        assertThat(Shadows.shadowOf(compoundDrawablesRelative[0]).getCreatedFromResId()).isEqualTo(
                expectedDrawableId);
    }
}
