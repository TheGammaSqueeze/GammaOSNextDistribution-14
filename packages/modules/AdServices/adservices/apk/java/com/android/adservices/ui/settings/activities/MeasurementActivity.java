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
package com.android.adservices.ui.settings.activities;

import static com.android.adservices.ui.UxUtil.isUxStatesReady;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.ViewModelProvider;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.activitydelegates.MeasurementActivityActionDelegate;
import com.android.adservices.ui.settings.delegates.MeasurementActionDelegate;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMeasurementFragment;
import com.android.adservices.ui.settings.viewmodels.MeasurementViewModel;

/** Android application activity provides functionality to control measurement data and consent. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class MeasurementActivity extends AdServicesBaseActivity {
    private MeasurementActionDelegate mActionDelegate;

    /** @return the action delegate for the activity. */
    public MeasurementActionDelegate getActionDelegate() {
        return mActionDelegate;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isUxStatesReady(this)) {
            initFragment();
        }
    }

    @Override
    public void initBeta() {
        initActivity();
    }

    @Override
    public void initGA() {
        initActivity();
    }

    @Override
    public void initU18() {
        initActivity();
    }

    @Override
    public void initRvc() {
        initU18();
    }

    private void initFragment() {
        setContentView(R.layout.adservices_settings_main_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container_view,
                        AdServicesSettingsMeasurementFragment.class,
                        null)
                .setReorderingAllowed(true)
                .commit();
        mActionDelegate =
                new MeasurementActionDelegate(
                        this, new ViewModelProvider(this).get(MeasurementViewModel.class));
    }

    private void initActivity() {
        setContentView(R.layout.measurement_activity);
        // no need to store since not using
        new MeasurementActivityActionDelegate(
                this, new ViewModelProvider(this).get(MeasurementViewModel.class));
    }
}
