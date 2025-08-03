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

package com.android.tv.dialog;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TextView;
import com.android.tv.R;
import com.android.tv.common.SoftPreconditions;

import java.util.function.Function;

import dagger.android.AndroidInjection;

@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class InteractiveAppDialogFragment extends SafeDismissDialogFragment {
    private static final boolean DEBUG = false;

    public static final String DIALOG_TAG = InteractiveAppDialogFragment.class.getName();
    private static final String TRACKER_LABEL = "Interactive App Dialog";
    private static final String TV_IAPP_NAME = "tv_iapp_name";
    private boolean mIsChoseOK;
    private String mIAppName;
    private Function mUpdateAitInfo;

    public static InteractiveAppDialogFragment create(String iappName) {
        InteractiveAppDialogFragment fragment = new InteractiveAppDialogFragment();
        Bundle args = new Bundle();
        args.putString(TV_IAPP_NAME, iappName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        AndroidInjection.inject(this);
        super.onAttach(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIAppName = getArguments().getString(TV_IAPP_NAME);
        setStyle(STYLE_NO_TITLE, 0);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dlg = super.onCreateDialog(savedInstanceState);
        dlg.getWindow().getAttributes().windowAnimations = R.style.pin_dialog_animation;
        mIsChoseOK = false;
        return dlg;
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Dialog size is determined by its windows size, not inflated view size.
        // So apply view size to window after the DialogFragment.onStart() where dialog is shown.
        Dialog dlg = getDialog();
        if (dlg != null) {
            dlg.getWindow()
                    .setLayout(
                            getResources().getDimensionPixelSize(R.dimen.pin_dialog_width),
                            LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.tv_app_dialog, container, false);
        TextView mTitleView = (TextView) v.findViewById(R.id.title);
        mTitleView.setText(getString(R.string.tv_app_dialog_title, mIAppName));
        Button okButton = v.findViewById(R.id.ok);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exit(true);
            }
        });
        Button cancelButton = v.findViewById(R.id.cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exit(false);
            }
        });
        return v;
    }

    private void exit(boolean isokclick) {
        mIsChoseOK = isokclick;
        dismiss();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        SoftPreconditions.checkState(getActivity() instanceof OnInteractiveAppCheckedListener);
        if (getActivity() instanceof OnInteractiveAppCheckedListener) {
            ((OnInteractiveAppCheckedListener) getActivity())
                    .onInteractiveAppChecked(mIsChoseOK);
        }
    }

    public interface OnInteractiveAppCheckedListener {
        void onInteractiveAppChecked(boolean checked);
    }
}
