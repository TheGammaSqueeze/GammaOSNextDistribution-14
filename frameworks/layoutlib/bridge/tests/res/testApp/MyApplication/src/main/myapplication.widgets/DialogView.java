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

package com.android.layoutlib.test.myapplication.widgets;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class DialogView extends View {
    private Dialog dialog;

    public DialogView(Context context) {
        super(context);
        init(context);
    }

    public DialogView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DialogView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        Builder builder = new Builder(context);
        dialog = builder.setMessage("My message")
                .setTitle("My title")
                .setPositiveButton("Ok", (dialog, id) -> {})
                .setNegativeButton("Cancel", (dialog, id) -> {})
                .create();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This creates the dialog view and adds it to the hierarchy before
        // measuring happens.
        dialog.show();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
