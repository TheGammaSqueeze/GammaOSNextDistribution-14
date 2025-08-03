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

package android.grammaticalinflection.atom.app;

import android.app.Activity;
import android.os.Bundle;

import android.app.GrammaticalInflectionManager;
import android.content.res.Configuration;

/**
 * Activity which tries to apply the gender which in the intent when invoked.
 */
public class SettingGrammaticalGenderActivity extends Activity {
    public static final int GRAMMATICAL_GENDER_NOT_SPECIFIED = 0;
    private final static String GENDER_KEY = "gender";

    @Override
    public void onCreate(Bundle data) {
        super.onCreate(data);
        int gender = getIntent().getIntExtra(GENDER_KEY, GRAMMATICAL_GENDER_NOT_SPECIFIED);
        setApplicationGrammaticalGender(gender);
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // handle configuration change to prevent recreate
        super.onConfigurationChanged(newConfig);
    }

    public void setApplicationGrammaticalGender(int gender) {
        GrammaticalInflectionManager a = getSystemService(GrammaticalInflectionManager.class);
        a.setRequestedApplicationGrammaticalGender(gender);
    }
}
