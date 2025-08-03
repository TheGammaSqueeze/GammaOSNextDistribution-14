/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.verifier.notifications;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.cts.verifier.R;

/**
 * A simple activity which will show over the lockscreen (like an ongoing phone call).
 */
public class ShowWhenLockedActivity extends Activity {
    private static final String KEY_DESCRIPTION = "desc";

    /**
     * Create the intent which can start this activity
     * @param description The text to be shown on the activity
     */
    public static Intent makeActivityIntent(Context context, String description,
            boolean clearActivity) {
        Intent intent = new Intent(context, ShowWhenLockedActivity.class)
                .putExtra(KEY_DESCRIPTION, description);
        if (clearActivity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_when_locked);
        String description = getIntent().getStringExtra(KEY_DESCRIPTION);
        this.<TextView>findViewById(R.id.description).setText(description);
    }
}
