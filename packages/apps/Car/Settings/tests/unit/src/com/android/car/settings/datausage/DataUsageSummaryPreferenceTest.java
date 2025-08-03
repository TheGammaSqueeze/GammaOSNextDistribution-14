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

package com.android.car.settings.datausage;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
public class DataUsageSummaryPreferenceTest {

    private static final String TEST_LABEL = "TEST_LABEL";
    private static final Intent TEST_INTENT = new Intent("test_action");

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private PreferenceViewHolder mViewHolder;
    private DataUsageSummaryPreference mDataUsageSummaryPreference;

    @Before
    public void setUp() {
        View rootView = View.inflate(mContext, R.layout.data_usage_summary_preference,
                /* root= */ null);
        mViewHolder = PreferenceViewHolder.createInstanceForTests(rootView);
        mDataUsageSummaryPreference = new DataUsageSummaryPreference(mContext);
    }

    @Test
    public void onBindViewHolder_noCarrierInfoText_isGone() {
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getCarrierInfoText().getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_hasCarrierInfoText_isVisible() {
        mDataUsageSummaryPreference.setCarrierInfoText(TEST_LABEL);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getCarrierInfoText().getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_hasCarrierInfoText_setsText() {
        mDataUsageSummaryPreference.setCarrierInfoText(TEST_LABEL);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText().toString()).isEqualTo(
                TEST_LABEL);
    }

    @Test
    public void onBindViewHolder_noManagePlanIntent_isGone() {
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getManageSubscriptionButton().getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_hasManagePlanIntent_isVisible() {
        mDataUsageSummaryPreference.setManageSubscriptionIntent(TEST_INTENT);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getManageSubscriptionButton().getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onButtonClick_hasManagePlanIntent_startsActivity() {
        mDataUsageSummaryPreference.setManageSubscriptionIntent(TEST_INTENT);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);
        doNothing().when(mContext).startActivity(any());
        getManageSubscriptionButton().performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());

        Intent intent = captor.getValue();
        assertThat(intent.getAction()).isEqualTo(TEST_INTENT.getAction());
    }

    private TextView getCarrierInfoText() {
        return (TextView) mViewHolder.findViewById(R.id.carrier_info_text);
    }

    private Button getManageSubscriptionButton() {
        return (Button) mViewHolder.findViewById(R.id.manage_subscription_button);
    }
}
