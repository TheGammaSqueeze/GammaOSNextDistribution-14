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
import android.util.Log;
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
 * Use the {@link TestConfigFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class TestConfigFragment extends Fragment {

    private static final String TAG = "TEST_CONFIG";

    private static TestConfigFragment sInstance;

    private Settings mSettings;

    private EditText mAppType;
    private EditText mUrl;
    private EditText mOrg;
    private EditText mSpId;
    private EditText mTlsCs;
    private CheckBox mForce;

    /** get the instance of TestConfigFragment */
    public static TestConfigFragment newInstance() {
        if (sInstance == null) {
            sInstance = new TestConfigFragment();
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
        View viewHierarchy = inflater.inflate(R.layout.fragment_test_config, container, false);
        mAppType = viewHierarchy.findViewById(R.id.editAppType);
        mUrl = viewHierarchy.findViewById(R.id.editUrl);
        mOrg = viewHierarchy.findViewById(R.id.editOrg);
        mSpId = viewHierarchy.findViewById(R.id.editSpId);
        mTlsCs = viewHierarchy.findViewById(R.id.editTlsCs);
        mForce = viewHierarchy.findViewById(R.id.checkBoxForce);

        setDefault();

        Button buttonDone = viewHierarchy.findViewById(R.id.client_config_done);
        buttonDone.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "updateTestConfig");
                        mSettings.updateTestConfig(getActivity(),
                                Integer.parseInt(mAppType.getText().toString()),
                                mUrl.getText().toString(),
                                mForce.isChecked(),
                                Integer.parseInt(mOrg.getText().toString()),
                                Integer.parseInt(mSpId.getText().toString()),
                                Integer.parseInt(mTlsCs.getText().toString()));
                        getFragmentManager().beginTransaction().remove(
                                TestConfigFragment.this).commitNow();
                    }
                }
        );

        Button buttonClear = viewHierarchy.findViewById(R.id.client_config_clear);
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

    void setDefault() {
        Log.d(TAG, "setDefault");
        mAppType.setText(Integer.toString(mSettings.getAppType()));
        String naf = mSettings.getNafUrl();
        if (naf == null || naf.isEmpty()) {
            naf = getString(R.string.sample_naf);
        }
        mUrl.setText(naf);
        mOrg.setText(Integer.toString(mSettings.getOrg()));
        mSpId.setText(Integer.toString(mSettings.getSpId()));
        mTlsCs.setText(Integer.toString(mSettings.getTlsCs()));
        mForce.setChecked(mSettings.isForceBootstrap());
    }
}
