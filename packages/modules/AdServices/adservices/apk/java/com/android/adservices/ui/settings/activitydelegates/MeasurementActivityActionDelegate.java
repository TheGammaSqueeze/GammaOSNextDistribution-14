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
package com.android.adservices.ui.settings.activitydelegates;

import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.activities.MeasurementActivity;
import com.android.adservices.ui.settings.viewmodels.MeasurementViewModel;
import com.android.adservices.ui.settings.viewmodels.MeasurementViewModel.MeasurementViewModelUiEvent;
import com.android.settingslib.widget.MainSwitchBar;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class MeasurementActivityActionDelegate extends BaseActionDelegate {
    private final MeasurementViewModel mMeasurementViewModel;

    public MeasurementActivityActionDelegate(
            MeasurementActivity measurementActivity, MeasurementViewModel measurementViewModel) {
        super(measurementActivity);
        this.mMeasurementViewModel = measurementViewModel;
        initWithUx(measurementActivity, measurementActivity.getApplicationContext());
        listenToMeasurementViewModelUiEvents();
    }

    @Override
    public void initBeta() {
        mActivity.setTitle(R.string.settingsUI_measurement_view_title);
        configureSharedElements();
    }

    @Override
    public void initGA() {
        mActivity.setTitle(R.string.settingsUI_measurement_ga_title);
        configureSharedElements();
    }

    @Override
    public void initU18() {
        mActivity.setTitle(R.string.settingsUI_measurement_ga_title);
        configureSharedElements();
    }

    @Override
    public void initRvc() {
        initU18();
    }

    @Override
    public void initGaUxWithPas() {
        initGA();
        configureElement(R.id.measurement_footer, R.string.settingsUI_pas_msmt_view_footer);
        configureLink(R.id.measurement_footer);
    }

    private void configureSharedElements() {
        // consent switch
        configureElement(
                R.id.measurement_switch_bar,
                switchBar ->
                        mMeasurementViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar),
                mMeasurementViewModel.getMeasurementConsent(),
                switchBar -> ((MainSwitchBar) switchBar)::setChecked);
        // reset msmt button
        configureElement(
                R.id.reset_measurement_button,
                view -> mMeasurementViewModel.resetMeasurementButtonClickHandler());
        // privacy policy link
        configureLink(R.id.measurement_footer);
    }

    private void listenToMeasurementViewModelUiEvents() {
        Observer<MeasurementViewModelUiEvent> observer =
                event -> {
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case SWITCH_ON_MEASUREMENT:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptInMeasurementDialog(mActivity);
                                }
                                mMeasurementViewModel.setMeasurementConsent(true);
                                break;
                            case SWITCH_OFF_MEASUREMENT:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptOutMeasurementDialog(
                                            mActivity, mMeasurementViewModel);
                                } else {
                                    mMeasurementViewModel.setMeasurementConsent(false);
                                }
                                break;
                            case RESET_MEASUREMENT:
                                UiStatsLogger.logResetMeasurementSelected();
                                mMeasurementViewModel.resetMeasurement();
                                Toast.makeText(
                                                mActivity,
                                                R.string.settingsUI_measurement_are_reset,
                                                Toast.LENGTH_SHORT)
                                        .show();
                                break;
                        }
                    } finally {
                        mMeasurementViewModel.uiEventHandled();
                    }
                };
        mMeasurementViewModel.getUiEvents().observe(mActivity, observer);
    }
}
