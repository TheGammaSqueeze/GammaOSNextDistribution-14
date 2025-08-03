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
package android.autofillservice.cts.activities;

import android.autofillservice.cts.R;
import android.os.Bundle;
import android.widget.EditText;

/**
 * Same as {@link LoginActivity}, but with login fields integrated with CredentialManager, and an
 * additional payment field to make the activity mixed.
 */
public class LoginCredentialMixedActivity extends LoginActivity {

    private static final String CREDENTIAL_HINT =
            "credential={\"get\":{\"credentialOptions\":[{\"type\":\"android.credentials."
                    + "TYPE_PASSWORD_CREDENTIAL\",\"requestData\":{\"androidx.credentials.BUNDLE"
                    + "_KEY_IS_AUTO_SELECT_ALLOWED\":false,\"androidx.credentials.BUNDLE_KEY_"
                    + "ALLOWED_USER_IDS\":[]},\"candidateQueryData\":{\"androidx.credentials."
                    + "BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED\":false,\"androidx.credentials.BUNDLE_"
                    + "KEY_ALLOWED_USER_IDS\":[]},\"isSystemProviderRequired\":false}]}}";
    private static final String USERNAME_HINT = "username";
    private static final String PASSWORD_HINT = "password";
    private static final String CREDIT_HINT = "creditCardNumber";

    // a complete credential hint has a json credential option appended to the hint.
    private static final String INCOMPLETE_CREDENTIAL_HINT = "credential";

    private EditText mCreditEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mUsernameEditText.setAutofillHints(USERNAME_HINT, CREDENTIAL_HINT);
        this.mPasswordEditText.setAutofillHints(PASSWORD_HINT, CREDENTIAL_HINT);
        this.mCreditEditText = findViewById(R.id.card_number);
        this.mCreditEditText.setAutofillHints(CREDIT_HINT, INCOMPLETE_CREDENTIAL_HINT);
    }

    @Override
    protected int getContentView() {
        return R.layout.mixed_fields_important_for_credential_manager;
    }

    /**
     * Sets the expectation for an autofill request (for credit card only), so it can be asserted
     * through {@link #assertAutoFilled()} later.
     *
     * <p><strong>NOTE: </strong>This method checks the result of text change, it should not call
     * this method too early, it may cause test fail. Call this method before checking autofill
     * behavior. {@see #expectAutoFill(String)} for how it should be used.
     */
    public void expectCreditCardAutoFill(String creditNumber) {
        mExpectation = new FillExpectation("credit", creditNumber, mCreditEditText);
        mCreditEditText.addTextChangedListener(mExpectation.mCustomFieldWatcher);
    }
}
