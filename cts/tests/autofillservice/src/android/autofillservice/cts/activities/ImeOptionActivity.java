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
 * This activity is specifically for testing out ime action filter on AutofillManager that it
 * can filter out editText with certain imeActions. (For example, ime_action_go)
 *
 */
public final class ImeOptionActivity extends AbstractAutoFillActivity {
    private static ImeOptionActivity sCurrentActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ime_option_activity);

        sCurrentActivity = this;
    }

    /**
     * Gests the latest instance.
     *
     * <p>Typically used in test cases that rotates the activity
     */
    @SuppressWarnings("unchecked") // Its up to caller to make sure it's setting the right one
    public static <T extends ImeOptionActivity> T getCurrentActivity() {
        return (T) sCurrentActivity;
    }
}
