/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.platform.test.scenario.facebook;

import android.platform.helpers.HelperAccessor;
import android.platform.test.option.IntegerOption;
import android.platform.test.option.StringOption;
import android.platform.test.rule.AppVersionRule;
import android.platform.test.scenario.annotation.Scenario;

import androidx.test.uiautomator.Direction;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Opens Facebook App, Logs into provided user account and scrolls through newsfeed */
@Scenario
@RunWith(JUnit4.class)
public class OpenApp {
    private static final String PACKAGE_NAME = "com.facebook.katana";

    // Class-level rules
    @ClassRule
    public static AppVersionRule appVersionRule =
            new AppVersionRule(PACKAGE_NAME, "251.0.0.31.111");

    @ClassRule
    public static StringOption sUsernameOption = new StringOption("username").setRequired(true);

    @ClassRule
    public static StringOption sPasswordOption = new StringOption("password").setRequired(true);

    @ClassRule
    public static IntegerOption sScrollCount = new IntegerOption("scroll-count").setDefault(3);

    private static HelperAccessor<IFacebookAppHelper> sHelper =
            new HelperAccessor<>(IFacebookAppHelper.class);

    @Test
    public void testLoginWithUi() {
        sHelper.get().open();
        sHelper.get().loginWithUi(sUsernameOption.get(), sPasswordOption.get());
        sHelper.get().dismissInitialDialogs();
        sHelper.get().scrollNewsfeed(Direction.DOWN, sScrollCount.get());
        sHelper.get().exit();
    }
}
