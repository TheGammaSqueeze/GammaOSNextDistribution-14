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

package android.platform.tests;

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.test.option.StringOption;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingSearchTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private static final String SEARCH_APP = "search-app";
    private static final String SEARCH_SETTING = "search-setting";

    @ClassRule
    public static StringOption mSearchApp = new StringOption(SEARCH_APP).setRequired(false);

    @ClassRule
    public static StringOption mSearchSetting = new StringOption(SEARCH_SETTING).setRequired(false);

    private static final String SEARCH_DEFAULT_APP = "Contacts";
    private static final String SEARCH_DEFAULT_SETTING = "Date & time";

    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public SettingSearchTest() throws Exception {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }


    @Before
    public void openSetting() {
        mSettingHelper.get().openFullSettings();
    }

    @After
    public void exitSettings() {
        mSettingHelper.get().exit();
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testSearchApplication() {
        String searchApp = SEARCH_DEFAULT_APP;
        if (mSearchApp != null && mSearchApp.get() != null && !mSearchApp.get().isEmpty()) {
            searchApp = mSearchApp.get();
        }
        mSettingHelper.get().searchAndSelect(searchApp);
        mSettingHelper.get().checkMenuExists("Allowed");
        // TODO(b/268707480) Updating the test use checkMenuExists and
        // its fails for resource id - PERMISSIONS_PAGE_TITLE.
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testSearchSetting() {
        String searchSetting = SEARCH_DEFAULT_SETTING;
        if (mSearchSetting != null
                && mSearchSetting.get() != null
                && !mSearchSetting.get().isEmpty()) {
            searchSetting = mSearchSetting.get();
        }
        mSettingHelper.get().searchAndSelect(searchSetting);
        assertTrue(
                "Page title does not contains searched setting name",
                mSettingHelper.get().isValidPageTitle(searchSetting));
    }
}
