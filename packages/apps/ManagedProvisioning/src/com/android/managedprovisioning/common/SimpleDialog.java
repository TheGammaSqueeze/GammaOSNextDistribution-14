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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.os.Bundle;

import androidx.annotation.StringRes;

import com.android.managedprovisioning.util.LazyStringResource;

/**
 * Utility class wrapping a {@link AlertDialog} in a {@link DialogFragment}
 * <p> In order to properly handle Dialog lifecycle we follow the practice of wrapping of them
 * in a Dialog Fragment.
 * <p> If buttons are to be used (enabled by setting a button message), the creator {@link Activity}
 * must implement {@link SimpleDialogListener}.
 */
public class SimpleDialog extends DialogFragment {
    private static final String TITLE = "title";
    private static final String MESSAGE = "message";
    private static final String LOCALIZED_MESSAGE = "localized_message";
    private static final String NEGATIVE_BUTTON_MESSAGE = "negativeButtonMessage";
    private static final String POSITIVE_BUTTON_MESSAGE = "positiveButtonMessage";

    /**
     * Use the {@link Builder} instead. Keeping the constructor public only because
     * a {@link DialogFragment} must have an empty constructor that is public.
     */
    public SimpleDialog() {
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        var context = getContext();
        final SimpleDialogListener dialogListener = (SimpleDialogListener) getActivity();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        Bundle args = getArguments();
        if (args.containsKey(TITLE)) {
            builder.setTitle(LazyStringResource.of(args.getBundle(TITLE)).value(context));
        }

        if (args.containsKey(MESSAGE)) {
            builder.setMessage(LazyStringResource.of(args.getBundle(MESSAGE)).value(context));
        }

        if (args.containsKey(NEGATIVE_BUTTON_MESSAGE)) {
            builder.setNegativeButton(
                    LazyStringResource.of(args.getBundle(NEGATIVE_BUTTON_MESSAGE)).value(context),
                    (dialog, which) -> dialogListener.onNegativeButtonClick(SimpleDialog.this));
        }

        if (args.containsKey(POSITIVE_BUTTON_MESSAGE)) {
            builder.setPositiveButton(
                    LazyStringResource.of(args.getBundle(POSITIVE_BUTTON_MESSAGE)).value(context),
                    (dialog, which) -> dialogListener.onPositiveButtonClick(SimpleDialog.this));
        }

        return builder.create();
    }

    /**
     * Throws an exception informing of a lack of a handler for a dialog button click
     * <p> Useful when implementing {@link SimpleDialogListener}
     */
    public static void throwButtonClickHandlerNotImplemented(DialogFragment dialog) {
        throw new IllegalArgumentException("Button click handler not implemented for dialog: "
                + dialog.getTag());
    }

    /**
     * A builder for SimpleDialog
     */
    public static class Builder implements DialogBuilder {
        private LazyStringResource mTitle;
        private LazyStringResource mMessage;
        private LazyStringResource mNegativeButtonMessage;
        private LazyStringResource mPositiveButtonMessage;
        private Boolean mCancelable;

        /**
         * Sets the title
         *
         * @param title Title resource.
         */
        public Builder setTitle(LazyStringResource title) {
            this.mTitle = title;
            return this;
        }

        /**
         * Sets the title
         *
         * @param titleId Title resource id.
         */
        public Builder setTitle(@StringRes int titleId) {
            return setTitle(LazyStringResource.of(titleId));
        }

        /**
         * Sets the message
         *
         * @param message Message resource.
         */
        public Builder setMessage(LazyStringResource message) {
            this.mMessage = message;
            return this;
        }

        /**
         * Sets the message
         *
         * @param messageId Message resource id.
         */
        public Builder setMessage(@StringRes int messageId) {
            return setMessage(LazyStringResource.of(messageId));
        }

        /**
         * Sets a message for the button.
         *
         * <p>Makes the button appear (without setting a button message, a button is not displayed)
         *
         * <p>Callback must be handled by a creator {@link Activity}, which must implement {@link
         * SimpleDialogListener}.
         *
         * @param negativeButtonMessage Message resource.
         */
        public Builder setNegativeButtonMessage(LazyStringResource negativeButtonMessage) {
            this.mNegativeButtonMessage = negativeButtonMessage;
            return this;
        }

        /**
         * Sets a message for the button.
         *
         * <p>Makes the button appear (without setting a button message, a button is not displayed)
         *
         * <p>Callback must be handled by a creator {@link Activity}, which must implement {@link
         * SimpleDialogListener}.
         *
         * @param negativeButtonMessageId Message resource id.
         */
        public Builder setNegativeButtonMessage(@StringRes int negativeButtonMessageId) {
            return setNegativeButtonMessage(LazyStringResource.of(negativeButtonMessageId));
        }

        /**
         * Sets a message for the button.
         *
         * <p>Makes the button appear (without setting a button message, a button is not displayed)
         *
         * <p>Callback must be handled by a creator {@link Activity}, which must implement {@link
         * SimpleDialogListener}.
         *
         * @param positiveButtonMessage Message resource.
         */
        public Builder setPositiveButtonMessage(LazyStringResource positiveButtonMessage) {
            this.mPositiveButtonMessage = positiveButtonMessage;
            return this;
        }

        /**
         * Sets a message for the button.
         *
         * <p>Makes the button appear (without setting a button message, a button is not displayed)
         *
         * <p>Callback must be handled by a creator {@link Activity}, which must implement {@link
         * SimpleDialogListener}.
         *
         * @param positiveButtonMessageId Message resource id.
         */
        public Builder setPositiveButtonMessage(@StringRes int positiveButtonMessageId) {
            return setPositiveButtonMessage(LazyStringResource.of(positiveButtonMessageId));
        }

        /** Sets whether the dialog is cancelable or not. Default is true. */
        public Builder setCancelable(boolean cancelable) {
            mCancelable = cancelable;
            return this;
        }

        /** Creates an {@link SimpleDialog} with the arguments supplied to this builder. */
        @Override
        public SimpleDialog build() {
            SimpleDialog instance = new SimpleDialog();
            Bundle args = new Bundle();

            if (mTitle != null) {
                args.putBundle(TITLE, mTitle.toBundle());
            }

            if (mMessage != null) {
                args.putBundle(MESSAGE, mMessage.toBundle());
            }

            if (mNegativeButtonMessage != null) {
                args.putBundle(NEGATIVE_BUTTON_MESSAGE, mNegativeButtonMessage.toBundle());
            }

            if (mPositiveButtonMessage != null) {
                args.putBundle(POSITIVE_BUTTON_MESSAGE, mPositiveButtonMessage.toBundle());
            }

            if (mCancelable != null) {
                instance.setCancelable(mCancelable);
            }

            instance.setArguments(args);
            return instance;
        }
    }

    /**
     * Interface for handling callbacks from {@link SimpleDialog} buttons.
     *
     * <p>If multiple dialogs are used in a context of a single {@link Activity},
     * a consumer of the interface can differentiate between dialogs using
     * e.g. a {@link DialogFragment#getTag()}, or {@link DialogFragment#getArguments()}.
     */
    public interface SimpleDialogListener {
        /**
         * Called when a user clicks on the positive dialog button.
         * <p> To be implemented by a host {@link Activity} object.
         * @param dialog {@link DialogFragment} where the click happened.
         */
        void onPositiveButtonClick(DialogFragment dialog);

        /**
         * Called when a user clicks on the negative dialog button.
         * <p> To be implemented by a host {@link Activity} object.
         * @param dialog {@link DialogFragment} where the click happened.
         */
        void onNegativeButtonClick(DialogFragment dialog);
    }
}
