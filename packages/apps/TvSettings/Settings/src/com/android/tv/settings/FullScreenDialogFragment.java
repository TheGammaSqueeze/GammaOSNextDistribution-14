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

package com.android.tv.settings;

import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

/**
 * Full screen dialog fragment.
 *
 * Use {@link DialogBuilder} to set up the arguments to the fragment.
 * To be used with the {@link R.style#TvSettingsDialog_FullScreen} theme.
 */
public class FullScreenDialogFragment extends Fragment {
    /** Constant for {@link #onButtonPressed(int)} indicating the positive button was pressed. */
    public static final int ACTION_POSITIVE = 0;
    /** Constant for {@link #onButtonPressed(int)} indicating the negative button was pressed. */
    public static final int ACTION_NEGATIVE = 1;

    private static final String ARGUMENT_ICON = "ARGUMENT_ICON";
    private static final String ARGUMENT_TITLE = "ARGUMENT_TITLE";
    private static final String ARGUMENT_MESSAGE = "ARGUMENT_MESSAGE";
    private static final String ARGUMENT_HINT_ICON = "ARGUMENT_HINT_ICON";
    private static final String ARGUMENT_HINT_TEXT = "ARGUMENT_HINT_TEXT";
    private static final String ARGUMENT_POSITIVE_BUTTON_LABEL = "ARGUMENT_POSITIVE_BUTTON_LABEL";
    private static final String ARGUMENT_NEGATIVE_BUTTON_LABEL = "ARGUMENT_NEGATIVE_BUTTON_LABEL";

    /** Builder that sets up arguments to the dialog fragment */
    public static final class DialogBuilder {
        private final Bundle mArgs = new Bundle();

        /** Builder that sets up arguments to the dialog fragment */
        public DialogBuilder() {
        }

        /** Sets the icon of the dialog */
        public DialogBuilder setIcon(Icon icon) {
            mArgs.putParcelable(ARGUMENT_ICON, icon);
            return this;
        }

        /** Sets the title of the dialog */
        public DialogBuilder setTitle(String title) {
            mArgs.putString(ARGUMENT_TITLE, title);
            return this;
        }

        /** Sets the message of the dialog */
        public DialogBuilder setMessage(String message) {
            mArgs.putString(ARGUMENT_MESSAGE, message);
            return this;
        }

        /** Sets the hint icon (shown below the message) of the dialog */
        public DialogBuilder setHintIcon(Icon hintIcon) {
            mArgs.putParcelable(ARGUMENT_HINT_ICON, hintIcon);
            return this;
        }

        /** Sets the hint text (shown below the message) of the dialog */
        public DialogBuilder setHintText(String hintText) {
            mArgs.putString(ARGUMENT_HINT_TEXT, hintText);
            return this;
        }

        /** Sets the label of the positive button */
        public DialogBuilder setPositiveButton(String positiveButtonLabel) {
            mArgs.putString(ARGUMENT_POSITIVE_BUTTON_LABEL, positiveButtonLabel);
            return this;
        }

        /** Sets the label of the negative button */
        public DialogBuilder setNegativeButton(String negativeButtonLabel) {
            mArgs.putString(ARGUMENT_NEGATIVE_BUTTON_LABEL, negativeButtonLabel);
            return this;
        }

        /** Returns arguments for the dialog fragment */
        public Bundle build() {
            return mArgs;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        LayoutInflater themedInflater = inflater.cloneInContext(
                new ContextThemeWrapper(inflater.getContext(),
                        R.style.TvSettingsDialog_FullScreen));
        View view = themedInflater.inflate(R.layout.full_screen_dialog, container, false);

        ImageView iconView = view.findViewById(R.id.dialog_icon);
        TextView titleView = view.findViewById(R.id.dialog_title);
        TextView messageView = view.findViewById(R.id.dialog_message);
        ViewGroup customViewContainer = view.findViewById(R.id.custom_view_container);
        ImageView hintIconView = view.findViewById(R.id.hint_icon);
        TextView hintTextView = view.findViewById(R.id.hint_text);
        ViewGroup hintContainerView = view.findViewById(R.id.hint_container);
        Button positiveButton = view.findViewById(R.id.positive_button);
        Button negativeButton = view.findViewById(R.id.negative_button);

        final Bundle args = getArguments();
        final Icon icon = args.getParcelable(ARGUMENT_ICON, Icon.class);
        if (icon != null) {
            iconView.setVisibility(View.VISIBLE);
            iconView.setImageIcon(icon);
        }

        titleView.setText(args.getString(ARGUMENT_TITLE));
        CharSequence message = getMessage();
        if (!TextUtils.isEmpty(message)) {
            messageView.setText(message);
        } else {
            messageView.setVisibility(View.GONE);
        }

        View customView = createCustomView(customViewContainer);
        if (customView != null) {
            customViewContainer.addView(customView);
        } else {
            customViewContainer.setVisibility(View.GONE);
        }

        final Icon hintIcon = args.getParcelable(ARGUMENT_HINT_ICON, Icon.class);
        final String hintText = args.getString(ARGUMENT_HINT_TEXT);
        final boolean hasHint = !TextUtils.isEmpty(hintText);
        hintContainerView.setVisibility(hasHint ? View.VISIBLE : View.GONE);
        if (hasHint) {
            hintTextView.setText(hintText);
            if (hintIcon != null) {
                hintIconView.setVisibility(View.VISIBLE);
                hintIconView.setImageIcon(hintIcon);
            }
        }

        final String positiveButtonLabel = args.getString(ARGUMENT_POSITIVE_BUTTON_LABEL);
        positiveButton.setVisibility(
                TextUtils.isEmpty(positiveButtonLabel) ? View.GONE : View.VISIBLE);
        positiveButton.setText(positiveButtonLabel);
        positiveButton.setOnClickListener((v) -> onButtonPressed(ACTION_POSITIVE));

        final String negativeButtonLabel = args.getString(ARGUMENT_NEGATIVE_BUTTON_LABEL);
        negativeButton.setVisibility(
                TextUtils.isEmpty(negativeButtonLabel) ? View.GONE : View.VISIBLE);
        negativeButton.setText(negativeButtonLabel);
        negativeButton.setOnClickListener((v) -> onButtonPressed(ACTION_NEGATIVE));


        return view;
    }

    /** Returns the dialog message. */
    public CharSequence getMessage() {
        return getArguments().getString(ARGUMENT_MESSAGE);
    }

    /** Returns the custom view (shown below the message) */
    public View createCustomView(ViewGroup parent) {
        return null;
    }

    /**
     * Called when a dialog button was pressed.
     *
     * @param action Indicates which button was pressed
     * @see #ACTION_POSITIVE
     * @see #ACTION_NEGATIVE
     */
    public void onButtonPressed(int action) {
    }
}
