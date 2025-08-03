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
package com.android.adservices.ui.settings;

import static com.android.adservices.ui.settings.DialogFragmentManager.sIsShowing;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.io.Serializable;

/** Child class of DialogFragment for the Speed Bump Dialog of the AdServices Settings App. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class SpeedBumpDialogFragment extends DialogFragment {
    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_POS_BTN_TEXT = "positiveButtonText";
    private static final String ARG_NEG_BTN_TEXT = "negativeButtonText";
    private static final String ARG_POS_BTN_LISTENER = "positiveButtonListener";

    private static boolean sHasNegButton;

    /**
     * create a new instance of SpeedBumpDialogFragment
     *
     * @param title dialog title
     * @param message dialog message
     * @param positiveButtonText string of positive button
     * @param negativeButtonText string of negative button, pass in empty string if there is no
     *     negative button
     * @param positiveButtonListener Action listener of positive button
     * @return a new instance of SpeedBumpDialogFragment
     */
    public static SpeedBumpDialogFragment newInstance(
            @NonNull String title,
            @NonNull String message,
            @NonNull String positiveButtonText,
            @NonNull String negativeButtonText,
            @NonNull OnClickListener positiveButtonListener) {
        SpeedBumpDialogFragment fragment = new SpeedBumpDialogFragment();

        Bundle args = new Bundle();
        sHasNegButton = !negativeButtonText.equals("");
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POS_BTN_TEXT, positiveButtonText);
        args.putString(ARG_NEG_BTN_TEXT, negativeButtonText);

        SerializableOnClickListener posListenerSerializable =
                new SerializableOnClickListener(positiveButtonListener);
        args.putSerializable(ARG_POS_BTN_LISTENER, posListenerSerializable);

        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments().getString(ARG_TITLE);
        String message = getArguments().getString(ARG_MESSAGE);
        String positiveButtonText = getArguments().getString(ARG_POS_BTN_TEXT);
        String negativeButtonTextId = getArguments().getString(ARG_NEG_BTN_TEXT);

        OnClickListener positiveButtonListener =
                (OnClickListener) getArguments().getSerializable(ARG_POS_BTN_LISTENER);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveButtonText, positiveButtonListener);
        if (sHasNegButton) {
            builder.setNegativeButton(negativeButtonTextId, getNegativeOnClickListener());
        }
        return builder.create();
    }

    @Override
    public void show(@NonNull FragmentManager manager, @Nullable String tag) {
        if (this.getDialog() != null && this.getDialog().isShowing()) return;
        super.show(manager, tag);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        sIsShowing = false;
    }

    private static OnClickListener getNegativeOnClickListener() {
        return (dialogInterface, buttonId) -> sIsShowing = false;
    }

    static class SerializableOnClickListener implements OnClickListener, Serializable {
        private final OnClickListener mListener;

        SerializableOnClickListener(OnClickListener positiveButtonListener) {
            this.mListener = positiveButtonListener;
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            this.mListener.onClick(dialogInterface, i);
        }
    }
}
