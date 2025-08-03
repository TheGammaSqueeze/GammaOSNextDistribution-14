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
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * A simple activity that upon submission launches {@link SimpleSaveActivity}.
 */
public class PreSimpleSaveActivity extends AbstractAutoFillActivity {

    public static final String ID_PRE_LABEL = "preLabel";
    public static final String ID_PRE_INPUT = "preInput";

    private static PreSimpleSaveActivity sInstance;

    public TextView mPreLabel;
    public EditText mPreInput;
    public Button mSubmit;

    public static PreSimpleSaveActivity getInstance() {
        return sInstance;
    }

    public PreSimpleSaveActivity() {
        sInstance = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pre_simple_save_activity);

        mPreLabel = findViewById(R.id.preLabel);
        mPreInput = findViewById(R.id.preInput);
        mSubmit = findViewById(R.id.submit);

        mSubmit.setOnClickListener((v) -> {
            finish();
            startActivity(new Intent(this, SimpleSaveActivity.class));
        });
    }

    /**
     * Set the EditText input or password value and wait until text change.
     */
    public void setTextAndWaitTextChange(String input) throws Exception {
        final FillExpectation expectation = expectInputTextChange(input);
        syncRunOnUiThread(() -> mPreInput.setText(input));
        expectation.assertTextChange();
    }

    public FillExpectation expectInputTextChange(String input) {
        final FillExpectation expectation = new FillExpectation(input);
        mPreInput.addTextChangedListener(expectation.mInputWatcher);
        return expectation;
    }

    public FillExpectation expectAutoFill(String input) {
        return expectInputTextChange(input);
    }

    public EditText getPreInput() {
        return mPreInput;
    }

    public final class FillExpectation {
        private final OneTimeTextWatcher mInputWatcher;

        private FillExpectation(String input) {
            mInputWatcher = new OneTimeTextWatcher("input", mPreInput, input);
        }

        public void assertTextChange() throws Exception {
            mInputWatcher.assertAutoFilled();
        }

        public void assertAutoFilled() throws Exception {
            mInputWatcher.assertAutoFilled();
        }
    }
}
