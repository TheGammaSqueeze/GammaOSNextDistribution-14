/*
 * SPDX-FileCopyrightText: 2015 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Display a message with a dismiss putton, and optionally a second button.
 */
public class AlertDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {

    public interface OnClickListener {
        /**
         * This method will be invoked when a button in the dialog is clicked.
         *
         * @param fragment the AlertDialogFragment that received the click
         * @param which the button that was clicked (e.g.
         *            {@link DialogInterface#BUTTON_POSITIVE}) or the position
         *            of the item clicked
         */
        void onClick(AlertDialogFragment fragment, int which);
    }

    private static final String NAME = AlertDialogFragment.class.getName();
    private static final String KEY_MESSAGE = NAME + "_message";
    private static final String KEY_BUTTON_NEGATIVE = NAME + "_button_negative";
    private static final String KEY_BUTTON_POSITIVE = NAME + "_button_positive";
    private static final String KEY_TITLE = NAME + "_title";

    /**
     * Convenience method for creating and showing a DialogFragment with the given message and
     * title.
     *
     * @param activity originating Activity
     * @param title resource id for the title string
     * @param message resource id for the displayed message string
     * @param positiveButtonLabel label for second button, if any.  If non-null, activity must
     * implement AlertDialogFragment.OnClickListener to respond.
     */
    public static void showMessageDialog(AppCompatActivity activity, @StringRes int title,
                                         @StringRes int message, @StringRes int positiveButtonLabel,
                                         @Nullable String tag) {
        showMessageDialog(activity, title != 0 ? activity.getString(title) : null,
                activity.getString(message),
                positiveButtonLabel != 0 ? activity.getString(positiveButtonLabel) : null,
                tag);
    }

    /**
     * Create and show a DialogFragment with the given message.
     *
     * @param activity originating Activity
     * @param title displayed title, if any
     * @param message displayed message
     * @param positiveButtonLabel label for second button, if any.  If non-null, activity must
     * implement AlertDialogFragment.OnClickListener to respond.
     */
    public static void showMessageDialog(AppCompatActivity activity, @Nullable CharSequence title,
                                         CharSequence message,
                                         @Nullable CharSequence positiveButtonLabel,
                                         @Nullable String tag)
    {
        final FragmentManager manager = activity.getSupportFragmentManager();
        if (manager == null || manager.isDestroyed()) {
            return;
        }
        final AlertDialogFragment dialogFragment = new AlertDialogFragment();
        final Bundle args = new Bundle();
        args.putCharSequence(KEY_MESSAGE, message);
        args.putCharSequence(KEY_BUTTON_NEGATIVE, activity.getString(R.string.dismiss));
        if (positiveButtonLabel != null) {
            args.putCharSequence(KEY_BUTTON_POSITIVE, positiveButtonLabel);
        }
        args.putCharSequence(KEY_TITLE, title);
        dialogFragment.setArguments(args);
        dialogFragment.show(manager, tag /* tag */);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments() == null ? Bundle.EMPTY : getArguments();
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());

        final LayoutInflater inflater = LayoutInflater.from(builder.getContext());
        final TextView messageView = (TextView) inflater.inflate(
                R.layout.dialog_message, null /* root */);
        messageView.setText(args.getCharSequence(KEY_MESSAGE));
        builder.setView(messageView);

        builder.setNegativeButton(args.getCharSequence(KEY_BUTTON_NEGATIVE), null /* listener */);

        final CharSequence positiveButtonLabel = args.getCharSequence(KEY_BUTTON_POSITIVE);
        if (positiveButtonLabel != null) {
            builder.setPositiveButton(positiveButtonLabel, this);
        }

        builder.setTitle(args.getCharSequence(KEY_TITLE));

        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        final Activity activity = getActivity();
        if (activity instanceof AlertDialogFragment.OnClickListener /* always true */) {
            ((AlertDialogFragment.OnClickListener) activity).onClick(this, which);
        }
    }
}
