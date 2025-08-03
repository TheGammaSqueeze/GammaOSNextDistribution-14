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

package android.localemanager.atom.overridelocaleconfig;

import android.app.Activity;
import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.LocaleList;

/**
 * Activity which tries to call setOverrideLocaleConfig.
 */
public class ActivityForSettingOverrideLocaleConfig extends Activity {
    private static final String OVERRIDE_LOCALE_CONFIG_KEY = "override_locale_config";
    private static final String EXTRA_REMOVE_OVERRIDE = "remove_override";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(OVERRIDE_LOCALE_CONFIG_KEY)) {
            String extra = intent.getStringExtra(OVERRIDE_LOCALE_CONFIG_KEY);
            callSetOverrideLocaleConfig(extra);
        }

        finish();
    }

    public void callSetOverrideLocaleConfig(String extra) {
        LocaleConfig localeConfig = extra.equals(EXTRA_REMOVE_OVERRIDE) ? null : new LocaleConfig(
                LocaleList.forLanguageTags(extra));
        LocaleManager localeManager = this.getSystemService(LocaleManager.class);
        localeManager.setOverrideLocaleConfig(localeConfig);
    }
}
