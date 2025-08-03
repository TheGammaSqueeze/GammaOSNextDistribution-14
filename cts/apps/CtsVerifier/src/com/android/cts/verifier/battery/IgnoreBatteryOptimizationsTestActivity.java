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

package com.android.cts.verifier.battery;

import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;

import com.android.cts.verifier.OrderedTestActivity;
import com.android.cts.verifier.R;

/** Test activity to check fulfillment of the ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent. */
public class IgnoreBatteryOptimizationsTestActivity extends OrderedTestActivity {
    private PowerManager mPowerManager;
    private UsageStatsManager mUsageStatsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setInfoResources(R.string.ibo_test, R.string.ibo_test_info, -1);

        mPowerManager = getSystemService(PowerManager.class);
        mUsageStatsManager = getSystemService(UsageStatsManager.class);
    }

    @Override
    protected Test[] getTests() {
        return new Test[]{
                mCheckAutoPowerModesEnabled,
                mConfirmNotExemptedAtStart,
                mRequestExemption,
                mIntermediate,
                mConfirmIsExempted,
                mRemoveExemption,
                mIntermediate,
                mConfirmIsNotExempted,
                mOpenAppExemptionListToExempt,
                mIntermediate,
                mConfirmIsExempted,
                mOpenAppExemptionListToUnexempt,
                mIntermediate,
                mConfirmIsNotExempted
        };
    }

    private boolean isExempted() {
        return mPowerManager.isIgnoringBatteryOptimizations(getPackageName())
                // getAppStandbyBucket() should return EXEMPTED if standby buckets are disabled,
                // so no need to check UsageStatsManager.isAppStandbyEnabled() here.
                && mUsageStatsManager.getAppStandbyBucket()
                == UsageStatsManager.STANDBY_BUCKET_EXEMPTED;
    }

    private boolean isFullyNotExempted() {
        // Use an OR so we check both values to make sure neither of them say the app is exempted.
        if (mPowerManager.isIgnoringBatteryOptimizations(getPackageName())
                || (mUsageStatsManager.isAppStandbyEnabled()
                    && mUsageStatsManager.getAppStandbyBucket()
                        == UsageStatsManager.STANDBY_BUCKET_EXEMPTED)) {
            return false;
        }
        return true;
    }

    private void openIgnoreBatteryOptimizationsAppList() {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        startActivity(intent);
    }

    private final Test mCheckAutoPowerModesEnabled =
            new Test(R.string.ibo_auto_power_modes_enabled) {
                @Override
                protected void run() {
                    super.run();

                    if (mPowerManager.areAutoPowerSaveModesEnabled()) {
                        succeed();
                    } else {
                        // Auto power save modes disabled. This test doesn't apply to this device.
                        mInstructions.setText(R.string.tests_completed_successfully);
                        mNextButton.setVisibility(View.GONE);
                        findViewById(R.id.pass_button).setVisibility(View.VISIBLE);
                        findViewById(R.id.fail_button).setVisibility(View.GONE);
                    }
                }
            };

    private final Test mConfirmNotExemptedAtStart = new Test(R.string.ibo_test_start_unexempt_app) {
        @Override
        protected void run() {
            super.run();

            if (isFullyNotExempted()) {
                succeed();
            }
        }

        @Override
        protected void onNextClick() {
            if (isExempted()) {
                openIgnoreBatteryOptimizationsAppList();
            } else {
                succeed();
            }
        }
    };

    private final Test mRequestExemption = new Test(R.string.ibo_exempt_app_request) {
        @Override
        protected void onNextClick() {
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            request.setData(Uri.parse("package:" + getPackageName()));
            startActivity(request);
            succeed();
        }
    };

    private final Test mIntermediate = new Test(R.string.ibo_next_to_confirm) {
        @Override
        protected void onNextClick() {
            succeed();
        }
    };

    private final Test mConfirmIsExempted = new Test(R.string.ibo_app_not_exempted) {
        @Override
        protected void run() {
            super.run();

            if (isExempted()) {
                succeed();
            } else {
                findViewById(R.id.btn_next).setVisibility(View.GONE);
            }
        }
    };

    private final Test mRemoveExemption = new Test(R.string.ibo_unexempt_app) {
        @Override
        protected void run() {
            super.run();

            if (isFullyNotExempted()) {
                succeed();
            }
        }

        @Override
        protected void onNextClick() {
            if (isExempted()) {
                openIgnoreBatteryOptimizationsAppList();
            } else {
                succeed();
            }
        }
    };

    private final Test mConfirmIsNotExempted = new Test(R.string.ibo_app_is_exempted) {
        @Override
        protected void run() {
            super.run();

            if (isFullyNotExempted()) {
                succeed();
            } else {
                findViewById(R.id.btn_next).setVisibility(View.GONE);
            }
        }
    };

    private final Test mOpenAppExemptionListToExempt = new Test(R.string.ibo_exempt_app_list) {
        @Override
        protected void onNextClick() {
            openIgnoreBatteryOptimizationsAppList();
            succeed();
        }
    };

    private final Test mOpenAppExemptionListToUnexempt = new Test(R.string.ibo_unexempt_app_list) {
        @Override
        protected void onNextClick() {
            openIgnoreBatteryOptimizationsAppList();
            succeed();
        }
    };
}
