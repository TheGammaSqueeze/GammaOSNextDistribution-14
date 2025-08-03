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
import android.widget.EditText;

import androidx.fragment.app.Fragment;

import com.android.phone.testapps.gbatestapp.R;
import com.android.phone.testapps.gbatestapp.Settings;
import com.android.phone.testapps.gbatestapp.TestGbaService;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CarrierConfigFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CarrierConfigFragment extends Fragment {
    private static final String TAG = "CARRIER";

    private static CarrierConfigFragment sInstance;

    private Settings mSettings;
    private EditText mEditPackageName;
    private EditText mEditReleaseTime;

    /** get the instance of CarrierConfigFragment */
    public static CarrierConfigFragment newInstance() {
        if (sInstance == null) {
            Log.d(TAG, "new instance:");
            sInstance = new CarrierConfigFragment();
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
        // Inflate the layout for this fragment
        View viewHierarchy = inflater.inflate(R.layout.fragment_carrier_config, container, false);
        mEditPackageName = viewHierarchy.findViewById(R.id.editServicePackageName);
        mEditReleaseTime = viewHierarchy.findViewById(R.id.editServiceReleaseTime);
        getConfig(false);

        Button buttonDone = viewHierarchy.findViewById(R.id.carrier_config_done);
        buttonDone.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mSettings.updateCarrierConfig(getActivity(),
                                mEditPackageName.getText().toString(),
                                Integer.parseInt(mEditReleaseTime.getText().toString()));
                        getFragmentManager().beginTransaction().remove(
                                CarrierConfigFragment.this).commitNow();
                    }
                }
        );

        Button buttonClear = viewHierarchy.findViewById(R.id.carrier_config_clear);
        buttonClear.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getConfig(true);
                    }
                }
        );
        return viewHierarchy;
    }

    private void getConfig(boolean isDefault) {
        String packagename = mSettings.getServicePackageName();
        if (isDefault || packagename == null) {
            packagename = TestGbaService.class.getPackage().getName();
        }
        mEditPackageName.setText(packagename);
        mEditReleaseTime.setText(isDefault ? "0" : Integer.toString(mSettings.getReleaseTime()));
    }
}
