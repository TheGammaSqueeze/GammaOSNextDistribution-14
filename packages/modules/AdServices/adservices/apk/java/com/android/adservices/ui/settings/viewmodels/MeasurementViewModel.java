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
package com.android.adservices.ui.settings.viewmodels;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMeasurementFragment;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.widget.MainSwitchBar;

/**
 * View model for the Measurement view of the AdServices Settings App. This view model is
 * responsible for serving Measurement to the Measurement view, and interacting with the {@link
 * ConsentManager} that persists and changes the Measurement data in a storage.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class MeasurementViewModel extends AndroidViewModel {

    private final MutableLiveData<MeasurementViewModelUiEvent> mEventTrigger =
            new MutableLiveData<>();
    private final MutableLiveData<Boolean> mMeasurementConsent;
    private final ConsentManager mConsentManager;

    /** UI event in measurement triggered by view model */
    public enum MeasurementViewModelUiEvent {
        SWITCH_ON_MEASUREMENT,
        SWITCH_OFF_MEASUREMENT,
        RESET_MEASUREMENT
    }

    public MeasurementViewModel(@NonNull Application application) {
        super(application);
        mConsentManager = ConsentManager.getInstance();
        mMeasurementConsent = new MutableLiveData<>(getMeasurementConsentFromConsentManager());
    }

    @VisibleForTesting
    public MeasurementViewModel(@NonNull Application application, ConsentManager consentManager) {
        super(application);
        mConsentManager = consentManager;
        mMeasurementConsent = new MutableLiveData<>(true);
    }

    /**
     * Provides {@link AdServicesApiConsent} displayed in {@link
     * AdServicesSettingsMeasurementFragment} as a Switch value.
     *
     * @return mMeasurementConsent indicates if user has consented to Mesaurement Api usage.
     */
    public MutableLiveData<Boolean> getMeasurementConsent() {
        return mMeasurementConsent;
    }

    /**
     * Sets the user consent for PP APIs.
     *
     * @param newMeasurementConsentValue the new value that user consent should be set to for
     *     Measurement PP APIs.
     */
    public void setMeasurementConsent(Boolean newMeasurementConsentValue) {
        if (newMeasurementConsentValue) {
            mConsentManager.enable(getApplication(), AdServicesApiType.MEASUREMENTS);
        } else {
            mConsentManager.disable(getApplication(), AdServicesApiType.MEASUREMENTS);
        }
        mMeasurementConsent.postValue(getMeasurementConsentFromConsentManager());
        if (FlagsFactory.getFlags().getRecordManualInteractionEnabled()) {
            ConsentManager.getInstance()
                    .recordUserManualInteractionWithConsent(
                            ConsentManager.MANUAL_INTERACTIONS_RECORDED);
        }
    }

    /** Reset all information related to Measurement */
    public void resetMeasurement() {
        mConsentManager.resetMeasurement();
    }

    /** Returns an observable but immutable event enum representing an action on UI. */
    public LiveData<MeasurementViewModelUiEvent> getUiEvents() {
        return mEventTrigger;
    }

    /**
     * Sets the UI Event as handled so the action will not be handled again if activity is
     * recreated.
     */
    public void uiEventHandled() {
        mEventTrigger.postValue(null);
    }

    /** Triggers a reset of all Measurement related data. */
    public void resetMeasurementButtonClickHandler() {
        mEventTrigger.postValue(MeasurementViewModelUiEvent.RESET_MEASUREMENT);
    }

    /**
     * Triggers opt out process for Privacy Sandbox. Also reverts the switch state, since
     * confirmation dialog will handle switch change.
     */
    public void consentSwitchClickHandler(MainSwitchBar measurementSwitchBar) {
        if (measurementSwitchBar.isChecked()) {
            measurementSwitchBar.setChecked(false);
            mEventTrigger.postValue(MeasurementViewModelUiEvent.SWITCH_ON_MEASUREMENT);
        } else {
            measurementSwitchBar.setChecked(true);
            mEventTrigger.postValue(MeasurementViewModelUiEvent.SWITCH_OFF_MEASUREMENT);
        }
    }

    private boolean getMeasurementConsentFromConsentManager() {
        return mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven();
    }
}
