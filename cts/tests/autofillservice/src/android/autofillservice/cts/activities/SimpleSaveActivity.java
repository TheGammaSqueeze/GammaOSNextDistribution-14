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
import android.os.Bundle;
import android.util.Log;
import android.view.autofill.AutofillManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Simple activity that has an edit text and buttons to cancel or commit the autofill context.
 */
public class SimpleSaveActivity extends AbstractAutoFillActivity {

    private static final String TAG = "SimpleSaveActivity";

    public static final String ID_LABEL = "label";
    public static final String ID_INPUT = "input";
    public static final String ID_PASSWORD = "password";
    public static final String ID_COMMIT = "commit";
    public static final String TEXT_LABEL = "Label:";

    private static SimpleSaveActivity sInstance;

    public TextView mLabel;
    public EditText mInput;
    public EditText mPassword;
    public Button mCancel;
    public Button mCommit;

    private boolean mAutoCommit = true;
    private boolean mClearFieldsOnSubmit = false;

    public static SimpleSaveActivity getInstance() {
        return sInstance;
    }

    public SimpleSaveActivity() {
        sInstance = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.simple_save_activity);

        mLabel = findViewById(R.id.label);
        mInput = findViewById(R.id.input);
        mPassword = findViewById(R.id.password);
        mCancel = findViewById(R.id.cancel);
        mCommit = findViewById(R.id.commit);

        mCancel.setOnClickListener((v) -> getAutofillManager().cancel());
        mCommit.setOnClickListener((v) -> onCommit());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
    }

    private void onCommit() {
        if (mClearFieldsOnSubmit) {
            resetFields();
        }
        if (mAutoCommit) {
            Log.d(TAG, "onCommit(): calling AFM.commit()");
            getAutofillManager().commit();
        } else {
            Log.d(TAG, "onCommit(): NOT calling AFM.commit()");
        }
    }

    private void resetFields() {
        Log.d(TAG, "resetFields()");
        mInput.setText("");
        mPassword.setText("");
    }

    /**
     * Defines whether the activity should automatically call {@link AutofillManager#commit()} when
     * the commit button is tapped.
     */
    public void setAutoCommit(boolean flag) {
        mAutoCommit = flag;
    }

    /**
     * Defines whether the activity should automatically clear its fields when submit is clicked.
     */
    public void setClearFieldsOnSubmit(boolean flag) {
        mClearFieldsOnSubmit = flag;
    }

    /**
     * Set the EditText input or password value and wait until text change.
     */
    public void setTextAndWaitTextChange(String input, String password) throws Exception {
        FillExpectation changeExpectation = expectInputPasswordTextChange(input, password);
        syncRunOnUiThread(() -> {
            if (input != null) {
                mInput.setText(input);
            }
            if (password != null) {
                mPassword.setText(password);
            }
        });
        changeExpectation.assertTextChange();
    }

    public FillExpectation expectAutoFill(String input) {
        final FillExpectation expectation = new FillExpectation(input, null);
        mInput.addTextChangedListener(expectation.mInputWatcher);
        return expectation;
    }

    public FillExpectation expectInputPasswordTextChange(String input, String password) {
        final FillExpectation expectation = new FillExpectation(input, password);
        if (expectation.mInputWatcher != null) {
            mInput.addTextChangedListener(expectation.mInputWatcher);
        }
        if (expectation.mPasswordWatcher != null) {
            mPassword.addTextChangedListener(expectation.mPasswordWatcher);
        }
        return expectation;
    }

    public FillExpectation expectAutoFill(String input, String password) {
        return expectInputPasswordTextChange(input, password);
    }

    public EditText getInput() {
        return mInput;
    }

    public final class FillExpectation {
        private final OneTimeTextWatcher mInputWatcher;
        private final OneTimeTextWatcher mPasswordWatcher;

        private FillExpectation(String input, String password) {
            mInputWatcher = input == null ? null : new OneTimeTextWatcher("input", mInput, input);
            mPasswordWatcher =
                    password == null
                            ? null
                            : new OneTimeTextWatcher("password", mPassword, password);
        }

        public void assertTextChange() throws Exception {
            assertAutoFilled();
        }

        public void assertAutoFilled() throws Exception {
            if (mInputWatcher != null) {
                mInputWatcher.assertAutoFilled();
            }
            if (mPasswordWatcher != null) {
                mPasswordWatcher.assertAutoFilled();
            }
        }
    }
}
