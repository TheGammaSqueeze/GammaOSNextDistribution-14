/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts.activities;

import android.autofillservice.cts.R;
import android.autofillservice.cts.testcore.OneTimeTextWatcher;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.EditText;

import java.util.Locale;

/**
 * Simple activity that attaches a new base context.
 */
public class AttachedContextActivity extends AbstractAutoFillActivity {
    public static final String ID_INPUT = "input";

    public EditText mInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.simple_save_activity);

        mInput = findViewById(R.id.input);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        final Context localContext = applyLocale(newBase, "en");
        super.attachBaseContext(localContext);
    }

    private Context applyLocale(Context context, String language) {
        final Resources resources = context.getResources();
        final Configuration configuration = resources.getConfiguration();
        configuration.setLocale(new Locale(language));
        return context.createConfigurationContext(configuration);
    }

    public FillExpectation expectAutoFill(String input) {
        final FillExpectation expectation = new FillExpectation(input);
        mInput.addTextChangedListener(expectation.mInputWatcher);
        return expectation;
    }

    public final class FillExpectation {
        private final OneTimeTextWatcher mInputWatcher;

        private FillExpectation(String input) {
            mInputWatcher = new OneTimeTextWatcher("input", mInput, input);
        }

        public void assertAutoFilled() throws Exception {
            mInputWatcher.assertAutoFilled();
        }
    }
}
