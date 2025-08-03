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

package com.android.car.settings.enterprise;

import static com.android.car.settings.enterprise.ActionDisabledByAdminActivity.FRAGMENT_TAG;

import static com.google.common.truth.Truth.assertThat;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;

import androidx.fragment.app.FragmentManager;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class ActionDisabledByAdminActivityTest extends BaseEnterpriseTestCase {

    private static final String RESTRICTION_STRING = "RESTRICTION_STRING";

    private FragmentManager mFragmentManager;
    private ActionDisabledByAdminDialogFragment mFragment;

    @Rule
    public ActivityTestRule<ActionDisabledByAdminActivity> mActivityTestRule =
            new ActivityTestRule<>(ActionDisabledByAdminActivity.class, true, false);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mockNullEnforcedAdmin(RESTRICTION_STRING, 0);
        mockNullEnforcedAdmin(RESTRICTION_STRING, mRealContext.getUserId());
    }

    @Test
    public void onCreate_showDialog() {
        mActivityTestRule.launchActivity(getIntent(false));
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        mFragment = (ActionDisabledByAdminDialogFragment)
                mFragmentManager.findFragmentByTag(FRAGMENT_TAG);

        assertThat(mFragment).isNotNull();
        AlertDialog dialog = (AlertDialog) mFragment.getDialog();
        assertThat(dialog).isNotNull();
        assertThat(dialog.isShowing()).isTrue();
    }

    @Test
    public void onCreate_readsIntentRestriction() {
        mActivityTestRule.launchActivity(getIntent(true));
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        mFragment = (ActionDisabledByAdminDialogFragment)
                mFragmentManager.findFragmentByTag(FRAGMENT_TAG);

        assertThat(mFragment).isNotNull();
        assertThat(mFragment.mRestriction).isEqualTo(RESTRICTION_STRING);
    }

    private Intent getIntent(boolean hasRestrictionFlag) {
        Intent intent = new Intent(mSpiedContext, ActionDisabledByAdminActivity.class);
        if (hasRestrictionFlag) {
            intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION, RESTRICTION_STRING);
        }

        return intent;
    }
}
