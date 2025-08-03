/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.phone.testapps.gbatestapp.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.fragment.app.Fragment;

import com.android.phone.testapps.gbatestapp.R;
import com.android.phone.testapps.gbatestapp.Settings;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ServiceConfigFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ServiceConfigFragment extends Fragment {

    private static final String TAG = "SERVICE";

    private static ServiceConfigFragment sInstance;

    private Settings mSettings;

    private CheckBox mAuthResult;
    private EditText mGbaKey;
    private EditText mBTid;
    private EditText mFailReason;

    /** get the instance of ServiceConfigFragment */
    public static ServiceConfigFragment newInstance() {
        if (sInstance == null) {
            sInstance = new ServiceConfigFragment();
        }
        return sInstance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = Settings.getSettings(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View  viewHierarchy = inflater.inflate(R.layout.fragment_service_config, container, false);

        mAuthResult = viewHierarchy.findViewById(R.id.checkBoxResult);
        mFailReason = viewHierarchy.findViewById(R.id.editFailReason);
        mGbaKey = viewHierarchy.findViewById(R.id.editKey);
        mBTid = viewHierarchy.findViewById(R.id.editBTid);

        setDefault();

        Button buttonDone = viewHierarchy.findViewById(R.id.service_config_done);
        buttonDone.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mSettings.updateServiceConfig(getActivity(), mAuthResult.isChecked(),
                                Integer.parseInt(mFailReason.getText().toString()),
                                mGbaKey.getText().toString(), mBTid.getText().toString());
                        getFragmentManager().beginTransaction()
                                .remove(ServiceConfigFragment.this).commitNow();
                    }
                }
        );

        Button buttonClear = viewHierarchy.findViewById(R.id.service_config_clear);
        buttonClear.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setDefault();
                    }
                }
        );

        return viewHierarchy;
    }

    private void setDefault() {
        mAuthResult.setChecked(mSettings.getAuthResult());
        String key = mSettings.getGbaKey();
        if (key == null || key.isEmpty()) {
            key = getString(R.string.sample_key);
        }
        mGbaKey.setText(key);
        String id = mSettings.getBTid();
        if (id == null || id.isEmpty()) {
            id = getString(R.string.sample_btid);
        }
        mBTid.setText(id);
        mFailReason.setText(Integer.toString(mSettings.getFailReason()));
    }
}
