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

package android.localemanager.atom.app;

import android.app.Activity;
import android.app.LocaleManager;
import android.os.Bundle;
import android.os.LocaleList;

/**
 * Activity which tries to call setApplicationLocales with null packageName when invoked.
 */
public class ActivityForNullCheckForInputPackageName extends Activity {
    private static final String DEFAULT_LANGUAGE_TAGS = "hi-IN,de-DE";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        callSetApplicationLocalesWithNullPackage();

        finish();
    }

    public void callSetApplicationLocalesWithNullPackage() {
        // This function is to verify that the service throws an exception when null target
        // packageName is passed, and this scenario is logged with a failure in the
        // ApplicationLocalesChangedAtom.
        LocaleManager mLocaleManager = this.getSystemService(LocaleManager.class);
        try {
            mLocaleManager.setApplicationLocales(null,
                    LocaleList.forLanguageTags(DEFAULT_LANGUAGE_TAGS));
        } catch (NullPointerException expected) {
        }
    }
}
