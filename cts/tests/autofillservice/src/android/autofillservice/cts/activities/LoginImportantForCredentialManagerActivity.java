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

/**
 * Same as {@link LoginActivity}, but with fields integrated with CredentialManager.
 */
public class LoginImportantForCredentialManagerActivity extends LoginActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean useAutofillHint = getIntent().getExtras().getBoolean("useAutofillHint");
        // Explicitly set credman to true for the password field.
        // Username field is set to credman in xml layout itself.
        // This way we test credman integration from xml as well as dynamically.
        if (useAutofillHint) {
            findViewById(R.id.password).setAutofillHints("password", "credential");
        } else {
            findViewById(R.id.password).setIsCredential(true);
        }
    }

    @Override
    protected int getContentView() {
        return R.layout.login_activity_important_for_credential_manager;
    }
}
