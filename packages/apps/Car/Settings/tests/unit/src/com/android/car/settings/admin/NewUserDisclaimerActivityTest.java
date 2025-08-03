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
package com.android.car.settings.admin;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.car.Car;
import android.car.admin.CarDevicePolicyManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.AndroidMockitoHelper;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.common.ConfirmationDialogFragment;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public final class NewUserDisclaimerActivityTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = NewUserDisclaimerActivityTest.class.getSimpleName();

    // NOTE: Cannot launch activity automatically as we need to mock Car.createCar() first
    @Rule
    public ActivityTestRule<NewUserDisclaimerActivity> mActivityRule = new ActivityTestRule(
            NewUserDisclaimerActivity.class,  /* initialTouchMode= */ false,
            /* launchActivity= */ false);

    private NewUserDisclaimerActivity mActivity;

    @Mock
    private CarDevicePolicyManager mCarDevicePolicyManager;

    @Mock
    private Car mCar;

    public NewUserDisclaimerActivityTest() {
        super(NewUserDisclaimerActivity.LOG.getTag());
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(Car.class);
    }

    @Before
    public void setFixtures() {
        Log.v(TAG, "setFixtures(): mocking Car.createCar()");
        doReturn(mCar).when(() -> Car.createCar(any()));

        when(mCar.getCarManager(Car.CAR_DEVICE_POLICY_SERVICE))
                .thenReturn(mCarDevicePolicyManager);

        Log.v(TAG, "setFixtures(): launching activitiy");
        mActivity = mActivityRule.launchActivity(/* intent= */ null);

        // It's called onResume() to show on current user
        verify(mCarDevicePolicyManager).setUserDisclaimerShown(mActivity.getUser());
    }

    @Test
    public void testAccept() throws Exception {
        AndroidMockitoHelper.syncRunOnUiThread(mActivity, () -> {
            Button button = getConfirmationDialog().getButton(DialogInterface.BUTTON_POSITIVE);
            Log.d(TAG, "Clicking accept button: " + button);
            button.performClick();
        });

        verify(mCarDevicePolicyManager).setUserDisclaimerAcknowledged(mActivity.getUser());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertWithMessage("activity is finishing").that(mActivity.isFinishing()).isTrue();
    }

    private AlertDialog getConfirmationDialog() {
        return (AlertDialog) ((ConfirmationDialogFragment) mActivity.getSupportFragmentManager()
                .findFragmentByTag(NewUserDisclaimerActivity.DIALOG_TAG))
                .getDialog();
    }
}
