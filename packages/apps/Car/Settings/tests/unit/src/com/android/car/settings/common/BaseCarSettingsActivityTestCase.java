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

import static org.testng.Assert.assertThrows;

import android.content.Context;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.ui.preference.PreferenceFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
abstract class BaseCarSettingsActivityTestCase<T extends BaseCarSettingsTestActivity> {
    protected Context mContext = ApplicationProvider.getApplicationContext();
    protected BaseCarSettingsTestActivity mActivity;
    protected TopLevelMenuFragment mTopLevelFragment;
    protected FragmentManager mFragmentManager;

    abstract ActivityTestRule<T> getActivityTestRule();

    @Before
    public void setUp() throws Throwable {
        mActivity = getActivityTestRule().getActivity();
        mFragmentManager = mActivity.getSupportFragmentManager();
    }

    @Test
    public void launchFragment_dialogFragment_throwsError() {
        DialogFragment dialogFragment = new DialogFragment();

        assertThrows(IllegalArgumentException.class,
                () -> mActivity.launchFragment(dialogFragment));
    }

    protected PreferenceFragment getCurrentFragment() {
        return (PreferenceFragment) mFragmentManager.findFragmentById(R.id.fragment_container);
    }
}
