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
package android.platform.test.scenario;

import android.platform.helpers.IAppHelper;
import android.platform.test.rule.TestWatcher;
import android.util.Log;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class HotAppStartupRunRule<T extends IAppHelper> implements TestRule {
    private final RuleChain mRuleChain;

    public HotAppStartupRunRule(T appHelper) {
        mRuleChain =
                RuleChain.outerRule(new SwitchOutAppRule())
                        .around(new SleepAtTestStartRule(2000))
                        .around(new SleepAtTestFinishRule(3000));
    }

    public Statement apply(final Statement base, final Description description) {
        return mRuleChain.apply(base, description);
    }

    // Custom rule to move away from app under test
    private static class SwitchOutAppRule extends TestWatcher {
        private static final String GO_HOME_PARAM_NAME = "go-home";
        private static final String GO_HOME_DEFAULT = "true";
        private static final String LAUNCHER_PARAM_NAME = "app-package";
        private static final String CARLAUNCHER_PACKAGE = "com.android.car.carlauncher";
        private static final String APP_ACTIVITY_PARAM_NAME = "app-activity";
        private static final String APP_GRID_ACTIVITY =
                "com.android.car.carlauncher.AppGridActivity";
        private static final String LOG_TAG = SwitchOutAppRule.class.getSimpleName();

        private boolean mGoHome;
        private String mAppPackage;
        private String mAppActivity;

        @Override
        protected void starting(Description description) {
            mGoHome =
                    Boolean.parseBoolean(
                            getArguments().getString(GO_HOME_PARAM_NAME, GO_HOME_DEFAULT));
            mAppPackage = getArguments().getString(LAUNCHER_PARAM_NAME, CARLAUNCHER_PACKAGE);
            mAppActivity = getArguments().getString(APP_ACTIVITY_PARAM_NAME, APP_GRID_ACTIVITY);

            // Default behavior is to press home
            if (mGoHome) {
                Log.v(LOG_TAG, "Pressing home");
                getUiDevice().pressHome();
            } else {
                Log.i(LOG_TAG, String.format("Starting %s/%s", mAppPackage, mAppActivity));
                String openAppGridCommand =
                        String.format("am start -n %s/%s", mAppPackage, mAppActivity);
                executeShellCommand(openAppGridCommand);
            }
        }
    }
}
