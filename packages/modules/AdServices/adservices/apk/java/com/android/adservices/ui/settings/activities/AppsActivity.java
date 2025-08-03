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
import com.android.adservices.ui.settings.activitydelegates.AppsActivityActionDelegate;
import com.android.adservices.ui.settings.delegates.AppsActionDelegate;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;

/**
 * Android application activity for controlling applications which interacted with FLEDGE
 * (Remarketing) APIs.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppsActivity extends AdServicesBaseActivity {
    private AppsActionDelegate mActionDelegate;

    /** @return the action delegate for the activity. */
    public AppsActionDelegate getActionDelegate() {
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
    public void onResume() {
        super.onResume();
        AppsViewModel viewModel = new ViewModelProvider(this).get(AppsViewModel.class);
        viewModel.refresh();
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
    public void initU18() {}

    @Override
    public void initRvc() {}

    private void initFragment() {
        setContentView(R.layout.adservices_settings_main_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container_view, AdServicesSettingsAppsFragment.class, null)
                .setReorderingAllowed(true)
                .commit();
        mActionDelegate =
                new AppsActionDelegate(this, new ViewModelProvider(this).get(AppsViewModel.class));
    }

    private void initActivity() {
        setContentView(R.layout.apps_activity);
        // no need to store since not using
        new AppsActivityActionDelegate(this, new ViewModelProvider(this).get(AppsViewModel.class));
    }
}
