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

package com.google.android.car.kitchensink.qc;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.qc.controller.RemoteQCController;
import com.android.car.qc.view.QCView;

import com.google.android.car.kitchensink.R;

public final class QCViewerFragment extends Fragment {
    private static final String KEY_CURRENT_URI_STRING = "CURRENT_URI_STRING";

    private RemoteQCController mController;
    private String mUriString;
    private EditText mInput;
    private Button mButton;
    private QCView mQCView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mUriString = savedInstanceState.getString(KEY_CURRENT_URI_STRING);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.qc_viewer, container, false);
        mInput = v.findViewById(R.id.qc_uri_input);
        mButton = v.findViewById(R.id.submit_uri_btn);
        mQCView = v.findViewById(R.id.qc_view);

        mButton.setOnClickListener(v1 -> {
            mUriString = mInput.getText().toString();
            Uri uri = Uri.parse(mUriString);
            updateQCView(uri);
        });

        if (mUriString != null) {
            Uri uri = Uri.parse(mUriString);
            updateQCView(uri);
        }

        return v;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT_URI_STRING, mUriString);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mController != null) {
            mController.listen(true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mController != null) {
            mController.listen(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mUriString = null;
        if (mController != null) {
            mController.destroy();
            mController = null;
        }
    }

    private void updateQCView(Uri uri) {
        if (uri == null) {
            return;
        }
        if (mController != null) {
            // destroy old controller
            mController.destroy();
        }

        mController = new RemoteQCController(getContext(), uri);
        mController.addObserver(mQCView);
        mController.listen(true);
    }
}
