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

package android.server.wm.backlegacyapp;

import android.app.Activity;
import android.os.Bundle;
import android.server.wm.TestJournalProvider;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.Nullable;

public class BackNavigationLegacyActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnBackInvokedCallback onBackInvokedCallback = () -> {
            TestJournalProvider.putExtras(
                    BackNavigationLegacyActivity.this,
                    Components.BACK_LEGACY,
                    bundle -> bundle.putBoolean(Components.KEY_ON_BACK_INVOKED_CALLED, true));
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, onBackInvokedCallback
        );
    }

    @Override
    public void onBackPressed() {
        TestJournalProvider.putExtras(this, Components.BACK_LEGACY,
                bundle -> bundle.putBoolean(Components.KEY_ON_BACK_PRESSED_CALLED, true));
        super.onBackPressed();
    }
}
