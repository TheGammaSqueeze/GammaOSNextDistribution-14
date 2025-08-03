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
import android.platform.test.rule.DropCachesRule;
import android.platform.test.rule.KillAppsRule;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ColdAppStartupRunRule<T extends IAppHelper> implements TestRule {
    private final RuleChain mRuleChain;

    public ColdAppStartupRunRule(T appHelper) {
        mRuleChain =
                RuleChain.outerRule(new KillAppsRule(appHelper.getPackage()))
                        .around(new DropCachesRule())
                        .around(new SleepAtTestStartRule(3000))
                        .around(new SleepAtTestFinishRule(3000));
    }

    public Statement apply(final Statement base, final Description description) {
        return mRuleChain.apply(base, description);
    }
}
