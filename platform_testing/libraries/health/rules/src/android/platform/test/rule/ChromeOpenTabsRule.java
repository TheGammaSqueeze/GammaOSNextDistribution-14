/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.platform.test.rule;

import android.os.SystemClock;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IChromeHelper;

import androidx.annotation.VisibleForTesting;

import org.junit.runner.Description;

/** This rule allows to execute CUJ while multiple Chrome tabs are opened. */
public class ChromeOpenTabsRule extends TestWatcher {

    @VisibleForTesting static final String CHROME_TABS_COUNT = "tabs-count";
    int tabsCount = 2;

    @VisibleForTesting static final String CHROME_LINKS = "chrome-links";
    String link = "https://www.google.com";

    private static HelperAccessor<IChromeHelper> sChomeHelper =
            new HelperAccessor<>(IChromeHelper.class);

    @Override
    protected void starting(Description description) {
        link = getArguments().getString(CHROME_LINKS, "https://www.google.com");
        tabsCount = Integer.valueOf(getArguments().getString(CHROME_TABS_COUNT, "2"));
        int initialTabCount = tabsCount;
        String[] links = link.split(",", tabsCount);

        sChomeHelper.get().open();
        for (int i = 0; i < tabsCount; i++) {
            for (int j = 0; j < links.length; j++) {
                sChomeHelper.get().addNewTab(links[j]);
                SystemClock.sleep(3000);
                tabsCount--;
            }
        }
        sChomeHelper.get().tabsCount(initialTabCount);
        sChomeHelper.get().exit();
    }

    @Override
    protected void finished(Description description) {
        sChomeHelper.get().open();
        sChomeHelper.get().closeAllTabs();
    }
}
