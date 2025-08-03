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

package android.platform.test.scenario;

import android.platform.helpers.IAppHelper;
import android.platform.test.rule.DropCachesRule;
import android.platform.test.rule.KillAppsRule;
import android.platform.test.rule.PressHomeRule;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * This rule is intended to be used as @Rule, which runs for every test run for measuring app
 * startup latency.
 */
public class AppStartupRunRule<T extends IAppHelper> implements TestRule {

    private final IAppHelper mAppHelper;
    private final RuleChain mRuleChain;

    public AppStartupRunRule(T appHelper) {
        mAppHelper = appHelper;

        /**
         * The following constraints on instrumentation arguments must hold.
         * +------------+-------+-------+
         * | Action     | Hot   | Cold  |
         * +------------+-------+-------+
         * | press-home | true  | false |
         * +------------+-------+-------+
         * | kill-app   | false | true  |
         * +------------+-------+-------+
         * | drop-cache | false | true  |
         * +------------+-------+-------+
         */

        // RuleChain for maintaining the order of evalution.
        mRuleChain =
                RuleChain
                        // cold startup needs to kill the app and clear cache before every test run.
                        .outerRule(new KillAppsRule(appHelper.getPackage()))
                        .around(new DropCachesRule())
                        // Test Finish rule order reversed
                        // so adding Sleep before and after Press Home
                        // to avoid getting such issues in future
                        .around(new SleepAtTestFinishRule(3000))
                        // hot startup needs to press home to exit an app after every test run.
                        .around(new PressHomeRule())
                        // sleep to make sure that asynchrounous app launch and relevant metric
                        // reporting by statsd happens before metrics listeners try to collect them.
                        .around(new SleepAtTestFinishRule(3000));
    }

    public Statement apply(final Statement base, final Description description) {
        return mRuleChain.apply(base, description);
    }
}
