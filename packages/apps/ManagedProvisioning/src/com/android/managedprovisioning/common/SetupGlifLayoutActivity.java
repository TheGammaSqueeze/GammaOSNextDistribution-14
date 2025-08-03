/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.common;

import android.annotation.Nullable;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;

import com.google.android.setupdesign.GlifLayout;


/**
 * Base class for setting up the layout.
 */
public abstract class SetupGlifLayoutActivity extends SetupLayoutActivity {

    private int mInitialHeaderMaxLines;

    public SetupGlifLayoutActivity() {
        super();
    }

    @VisibleForTesting
    protected SetupGlifLayoutActivity(
            Utils utils, SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        theme.applyStyle(R.style.SetupWizardPartnerResource, true);
        super.onApplyThemeResource(theme, resid, first);
    }

    protected void initializeLayoutParams(
            int layoutResourceId, @Nullable Integer headerResourceId) {
        setContentView(layoutResourceId);
        GlifLayout layout = findViewById(R.id.setup_wizard_layout);

        if (headerResourceId != null) {
            layout.setHeaderText(headerResourceId);
        }

        TextView header = findViewById(com.google.android.setupdesign.R.id.suc_layout_title);
        if (header != null) {
            mInitialHeaderMaxLines = header.getMaxLines();
            header.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    increaseMaxLinesIfNecessary(header, mInitialHeaderMaxLines);
                }
            });

            increaseMaxLinesIfNecessary(header, mInitialHeaderMaxLines);
        }

        layout.setIcon(getDrawable(R.drawable.ic_enterprise_blue_24dp));
    }

    /**
     * If the text takes more than its {@code textView}'s {@code initialMaxLines}, expand it one
     * more line.
     */
    private void increaseMaxLinesIfNecessary(TextView textView, int initialMaxLines) {
        textView.setMaxLines(initialMaxLines);
        textView.post(() -> {
            Layout layout = textView.getLayout();
            if (layout == null) {
                return;
            }
            int lineCount = layout.getLineCount();
            if (lineCount > 0 && layout.getEllipsisCount(lineCount - 1) > 0) {
                textView.setMaxLines(initialMaxLines + 1);
            }
        });
    }
}
