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
package android.autofillservice.cts.activities;

import android.autofillservice.cts.R;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.EditText;


/**
 * The Activity that is the same as {@link LoginActivity} layout but without password field.
 */
public class FieldsNoPasswordActivity extends AbstractAutoFillActivity {
    public static final String STRING_ID_PHONE = "phone_number";
    public static final String STRING_ID_CREDIT_CARD_NUMBER = "cc_number";
    public static final String STRING_ID_EMAILADDRESS = "email_address";

    private static FieldsNoPasswordActivity sCurrentActivity;

    private EditText mUsernameEditText;

    public static FieldsNoPasswordActivity getCurrentActivity() {
        return sCurrentActivity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentView());
        sCurrentActivity = this;

        mUsernameEditText = findViewById(R.id.username);
    }

    protected int getContentView() {
        return R.layout.multiple_hints_without_password_activity;
    }

    public WindowInsets getRootWindowInsets() {
        return mUsernameEditText.getRootWindowInsets();
    }
}
