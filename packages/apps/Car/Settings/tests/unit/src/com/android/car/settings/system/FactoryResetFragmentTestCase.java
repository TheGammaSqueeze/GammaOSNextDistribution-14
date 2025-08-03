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

package com.android.car.settings.system;

import static android.app.Activity.RESULT_CANCELED;

import static com.android.car.settings.system.FactoryResetFragment.CHECK_LOCK_REQUEST_CODE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.security.CheckLockActivity;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.TestEventListener;
import com.android.car.ui.toolbar.MenuItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public abstract class FactoryResetFragmentTestCase<T extends BaseCarSettingsTestActivity> {

    protected Context mContext = ApplicationProvider.getApplicationContext();
    protected BaseCarSettingsTestActivity mActivity;
    protected TestFactoryResetFragment mFragment;
    protected FragmentManager mFragmentManager;

    abstract ActivityTestRule<T> getActivityTestRule();

    @Before
    public void setUp() throws Throwable {
        mActivity = getActivityTestRule().getActivity();
        mFragmentManager = getActivityTestRule().getActivity().getSupportFragmentManager();
        setUpFragment();
    }

    @Test
    public void factoryResetButtonClicked_launchesCheckLockActivity() throws Throwable {
        getActivityTestRule().runOnUiThread(() -> {
            MenuItem factoryResetButton = mFragment.getToolbarMenuItems().get(0);
            factoryResetButton.setEnabled(true);
            factoryResetButton.performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mFragment.mStartActivityForResult.assertCalled();
        assertThat(mFragment.mStartActivityForResult.getLastValue().getComponent().getClassName())
                .isEqualTo(CheckLockActivity.class.getName());
    }

    @Test
    @FlakyTest(bugId = 316036256)
    public void processActivityResult_otherResultCode_doesNothing() {
        mFragment.processActivityResult(CHECK_LOCK_REQUEST_CODE, RESULT_CANCELED, /* data= */ null);

        Fragment launchedFragment = mFragmentManager.findFragmentById(
                R.id.fragment_container);

        assertThat(launchedFragment).isInstanceOf(FactoryResetFragment.class);
    }

    private void setUpFragment() throws Throwable {
        String factoryResetFragmentTag = "factory_reset_fragment";
        getActivityTestRule().runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new TestFactoryResetFragment(),
                            factoryResetFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (TestFactoryResetFragment) mFragmentManager
                .findFragmentByTag(factoryResetFragmentTag);
    }

    public static class TestFactoryResetFragment extends FactoryResetFragment {

        TestEventListener<Intent> mStartActivityForResult = new TestEventListener<>();

        @Override
        public void startActivityForResult(Intent intent, int requestCode,
                ActivityResultCallback callback) {
            mStartActivityForResult.accept(intent);
        }
    }
}
