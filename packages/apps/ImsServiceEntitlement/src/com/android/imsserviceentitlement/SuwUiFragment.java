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

package com.android.imsserviceentitlement;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifLayout;

/** A {@link Fragment} with SuW GlifLayout. */
public class SuwUiFragment extends Fragment {
    private static final String TITLE_RES_ID_KEY = "TITLE_RES_ID_KEY";
    private static final String TEXT_RES_ID_KEY = "TEXT_RES_ID_KEY";
    private static final String PROGRESS_BAR_SHOWN_KEY = "PROGRESS_BAR_SHOWN_KEY";
    private static final String PRIMARY_BUTTON_TEXT_ID_KEY = "PRIMARY_BUTTON_TEXT_ID_KEY";
    private static final String PRIMARY_BUTTON_RESULT_KEY = "PRIMARY_BUTTON_RESULT_KEY";
    private static final String SECONDARY_BUTTON_TEXT_ID_KEY = "SECONDARY_BUTTON_TEXT_ID_KEY";

    /** Static constructor */
    public static SuwUiFragment newInstance(
            @StringRes int title,
            @StringRes int text,
            boolean progressBarShown,
            @StringRes int primaryButtonText,
            int primaryResult,
            @StringRes int secondaryButtonText) {
        SuwUiFragment frag = new SuwUiFragment();
        Bundle args = new Bundle();
        args.putInt(TITLE_RES_ID_KEY, title);
        args.putInt(TEXT_RES_ID_KEY, text);
        args.putBoolean(PROGRESS_BAR_SHOWN_KEY, progressBarShown);
        args.putInt(PRIMARY_BUTTON_TEXT_ID_KEY, primaryButtonText);
        // Action for primaryButton is: finishActivity(primaryResult)
        args.putInt(PRIMARY_BUTTON_RESULT_KEY, primaryResult);
        args.putInt(SECONDARY_BUTTON_TEXT_ID_KEY, secondaryButtonText);
        // Action for secondaryButton is: finishActivity(Activity.RESULT_CANCELED)
        frag.setArguments(args);
        return frag;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_suw_ui, container, false);

        Bundle arguments = getArguments();
        int titleResId = arguments.getInt(TITLE_RES_ID_KEY, 0);
        int textResId = arguments.getInt(TEXT_RES_ID_KEY, 0);
        boolean progressBarShown = arguments.getBoolean(PROGRESS_BAR_SHOWN_KEY, false);
        int primaryButtonText = arguments.getInt(PRIMARY_BUTTON_TEXT_ID_KEY, 0);
        int primaryResult = arguments.getInt(PRIMARY_BUTTON_RESULT_KEY, Activity.RESULT_CANCELED);
        int secondaryButtonText = arguments.getInt(SECONDARY_BUTTON_TEXT_ID_KEY, 0);

        GlifLayout layout = view.findViewById(R.id.setup_wizard_layout);
        if (titleResId != 0) {
            layout.setHeaderText(titleResId);
        }

        layout.setProgressBarShown(progressBarShown);
        if (progressBarShown) {
            // Keep screen on if something in progress. And remove the flag on destroy view.
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (textResId != 0) {
            TextView text = view.findViewById(R.id.entry_text);
            text.setText(textResId);
        }

        final FooterBarMixin buttonFooterMixin = layout.getMixin(FooterBarMixin.class);

        if (primaryButtonText != 0) {
            buttonFooterMixin.setPrimaryButton(
                    new FooterButton.Builder(getContext())
                            .setListener(v -> finishActivity(primaryResult))
                            .setText(primaryButtonText)
                            .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary)
                            .build());
        }

        if (secondaryButtonText != 0) {
            buttonFooterMixin.setSecondaryButton(
                    new FooterButton.Builder(getContext())
                            .setListener(v -> finishActivity(Activity.RESULT_CANCELED))
                            .setText(secondaryButtonText)
                            .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary)
                            .build());
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        boolean progressBarShown = getArguments().getBoolean(PROGRESS_BAR_SHOWN_KEY, false);
        if (progressBarShown) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        super.onDestroyView();
    }

    /** Finishes the associated activity with {@code result}; no-op if no activity associated. */
    private void finishActivity(int result) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        ((WfcActivationUi) activity).setResultAndFinish(result);
    }
}
