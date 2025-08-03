/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.phone.testapps.satellitetestapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteResult;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Activity related to SatelliteControl APIs for satellite.
 */
public class SatelliteControl extends Activity {

    private static final long TIMEOUT = 3000;

    private SatelliteManager mSatelliteManager;
    private SubscriptionManager mSubscriptionManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSatelliteManager = getSystemService(SatelliteManager.class);
        mSubscriptionManager = getSystemService(SubscriptionManager.class);

        setContentView(R.layout.activity_SatelliteControl);
        findViewById(R.id.enableSatellite)
                .setOnClickListener(this::enableSatelliteApp);
        findViewById(R.id.disableSatellite)
                .setOnClickListener(this::disableSatelliteApp);
        findViewById(R.id.requestIsSatelliteEnabled)
                .setOnClickListener(this::requestIsEnabledApp);
        findViewById(R.id.requestIsDemoModeEnabled)
                .setOnClickListener(this::requestIsDemoModeEnabledApp);
        findViewById(R.id.requestIsSatelliteSupported)
                .setOnClickListener(this::requestIsSupportedApp);
        findViewById(R.id.requestSatelliteCapabilities)
                .setOnClickListener(this::requestCapabilitiesApp);
        findViewById(R.id.requestIsSatelliteCommunicationAllowedForCurrentLocation)
                .setOnClickListener(this::requestIsCommunicationAllowedForCurrentLocationApp);
        findViewById(R.id.requestTimeForNextSatelliteVisibility)
                .setOnClickListener(this::requestTimeForNextSatelliteVisibilityApp);
        findViewById(R.id.removeUserRestrictReason)
                .setOnClickListener(this::removeUserRestrictReasonApp);
        findViewById(R.id.addUserRestrictReason)
                .setOnClickListener(this::addUserRestrictReasonApp);
        findViewById(R.id.getSatellitePlmn)
                .setOnClickListener(this::getSatellitePlmnApp);
        findViewById(R.id.getAllSatellitePlmn)
                .setOnClickListener(this::getAllSatellitePlmnApp);
        findViewById(R.id.isSatelliteEnabledForCarrier)
                .setOnClickListener(this::isSatelliteEnabledForCarrierApp);
        findViewById(R.id.isRequestIsSatelliteEnabledForCarrier)
                .setOnClickListener(this::isRequestIsSatelliteEnabledForCarrierApp);
        findViewById(R.id.Back).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SatelliteControl.this, SatelliteTestApp.class));
            }
        });
    }

    private void enableSatelliteApp(View view) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        mSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(true).setDemoMode(true).build(),
                Runnable::run, error::offer);
        TextView textView = findViewById(R.id.text_id);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out to enable the satellite");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to enable the satellite, error ="
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText("Successfully enabled the satellite");
            }
        } catch (InterruptedException e) {
            textView.setText("Enable SatelliteService exception caught =" + e);
        }
    }

    private void disableSatelliteApp(View view) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        mSatelliteManager.requestEnabled(new EnableRequestAttributes.Builder(false).build(),
                Runnable::run, error::offer);
        TextView textView = findViewById(R.id.text_id);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out to disable the satellite");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to disable the satellite, error ="
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText("Successfully disabled the satellite");
            }
        } catch (InterruptedException e) {
            textView.setText("Disable SatelliteService exception caught =" + e);
        }
    }

    private void requestIsEnabledApp(View view) {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
            @Override
            public void onResult(Boolean result) {
                enabled.set(result);
                TextView textView = findViewById(R.id.text_id);
                if (enabled.get()) {
                    textView.setText("requestIsEnabled is true");
                } else {
                    textView.setText("Status for requestIsEnabled result : "
                            + enabled.get());
                }
            }

            @Override
            public void onError(SatelliteManager.SatelliteException exception) {
                errorCode.set(exception.getErrorCode());
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestIsEnabled error : "
                        + SatelliteErrorUtils.mapError(errorCode.get()));
            }
        };
        mSatelliteManager.requestIsEnabled(Runnable::run, receiver);
    }

    private void requestIsDemoModeEnabledApp(View view) {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
            @Override
            public void onResult(Boolean result) {
                enabled.set(result);
                TextView textView = findViewById(R.id.text_id);
                if (enabled.get()) {
                    textView.setText("requestIsDemoModeEnabled is true");
                } else {
                    textView.setText("Status for requestIsDemoModeEnabled result : "
                            + enabled.get());
                }
            }

            @Override
            public void onError(SatelliteManager.SatelliteException exception) {
                errorCode.set(exception.getErrorCode());
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestIsDemoModeEnabled error : "
                        + SatelliteErrorUtils.mapError(errorCode.get()));
            }
        };
        mSatelliteManager.requestIsDemoModeEnabled(Runnable::run, receiver);
    }

    private void requestIsSupportedApp(View view) {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
            @Override
            public void onResult(Boolean result) {
                enabled.set(result);
                TextView textView = findViewById(R.id.text_id);
                if (enabled.get()) {
                    textView.setText("requestIsSupported is true");
                } else {
                    textView.setText("Status for requestIsSupported result : "
                            + enabled.get());
                }
            }

            @Override
            public void onError(SatelliteManager.SatelliteException exception) {
                errorCode.set(exception.getErrorCode());
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestIsSupported error : "
                        + SatelliteErrorUtils.mapError(errorCode.get()));
            }
        };
        mSatelliteManager.requestIsSupported(Runnable::run, receiver);
    }

    private void requestCapabilitiesApp(View view) {
        final AtomicReference<SatelliteCapabilities> capabilities = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
            @Override
            public void onResult(SatelliteCapabilities result) {
                capabilities.set(result);
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestCapabilities result: "
                        + capabilities.get());
            }

            @Override
            public void onError(SatelliteManager.SatelliteException exception) {
                errorCode.set(exception.getErrorCode());
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestCapabilities error : "
                        + SatelliteErrorUtils.mapError(errorCode.get()));
            }
        };
        mSatelliteManager.requestCapabilities(Runnable::run, receiver);
    }

    private void requestIsCommunicationAllowedForCurrentLocationApp(View view) {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        String display = "requestIsCommunicationAllowedForCurrentLocation";
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
            @Override
            public void onResult(Boolean result) {
                enabled.set(result);
                TextView textView = findViewById(R.id.text_id);
                if (enabled.get()) {
                    textView.setText(display + "is true");
                } else {
                    textView.setText("Status for" + display + "result: " + enabled.get());
                }

            }

            @Override
            public void onError(SatelliteManager.SatelliteException exception) {
                errorCode.set(exception.getErrorCode());
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for"  + display + "error: "
                        + SatelliteErrorUtils.mapError(errorCode.get()));
            }
        };
        mSatelliteManager.requestIsCommunicationAllowedForCurrentLocation(Runnable::run,
                receiver);
    }

    private void requestTimeForNextSatelliteVisibilityApp(View view) {
        final AtomicReference<Duration> nextVisibilityDuration = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Duration, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
            @Override
            public void onResult(Duration result) {
                nextVisibilityDuration.set(result);
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestTimeForNextSatelliteVisibility result : "
                        + result.getSeconds());
            }

            @Override
            public void onError(SatelliteManager.SatelliteException exception) {
                errorCode.set(exception.getErrorCode());
                TextView textView = findViewById(R.id.text_id);
                textView.setText("Status for requestTimeForNextSatelliteVisibility error : "
                        + SatelliteErrorUtils.mapError(errorCode.get()));
            }
        };
        mSatelliteManager.requestTimeForNextSatelliteVisibility(Runnable::run, receiver);
    }

    private void removeUserRestrictReasonApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        List<SubscriptionInfo> infoList = mSubscriptionManager.getAvailableSubscriptionInfoList();
        List<Integer> subIdList = infoList.stream()
                .map(SubscriptionInfo::getSubscriptionId)
                .toList();
        for (int subId : subIdList) {
            mSatelliteManager.removeAttachRestrictionForCarrier(subId,
                    SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER,
                    Runnable::run, error::offer);
        }

        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out to removeAttachRestrictionForCarrier");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to removeAttachRestrictionForCarrier with error = "
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText(subIdList == null || subIdList.isEmpty() ? "no active subId list" :
                        "removeAttachRestrictionForCarrier for all subIdList=" + subIdList);
            }
        } catch (InterruptedException e) {
            textView.setText("removeAttachRestrictionForCarrier exception caught =" + e);
        }
    }

    private void addUserRestrictReasonApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        List<SubscriptionInfo> infoList = mSubscriptionManager.getAvailableSubscriptionInfoList();
        List<Integer> subIdList = infoList.stream()
                .map(SubscriptionInfo::getSubscriptionId)
                .toList();
        for (int subId : subIdList) {
            mSatelliteManager.addAttachRestrictionForCarrier(subId,
                    SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_USER,
                    Runnable::run, error::offer);
        }

        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out to addAttachRestrictionForCarrier");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to addAttachRestrictionForCarrier with error = "
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText(subIdList == null || subIdList.isEmpty() ? "no active subId list" :
                        "addAttachRestrictionForCarrier for all subIdList=" + subIdList);
            }
        } catch (InterruptedException e) {
            textView.setText("addAttachRestrictionForCarrier exception caught =" + e);
        }
    }

    private void getSatellitePlmnApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        textView.setText("[SatelliteService] getSatellitePlmnApp = "
                + SatelliteTestApp.getTestSatelliteService().getCarrierPlmnList());
    }

    private void getAllSatellitePlmnApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        textView.setText("[SatelliteService] getAllSatellitePlmnApp = "
                + SatelliteTestApp.getTestSatelliteService().getAllSatellitePlmnList());
    }

    private void isSatelliteEnabledForCarrierApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        textView.setText("[SatelliteService] isSatelliteEnabledForCarrier= "
                + SatelliteTestApp.getTestSatelliteService().isSatelliteEnabledForCarrier());
    }

    private void isRequestIsSatelliteEnabledForCarrierApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        textView.setText("[SatelliteService] isRequestIsSatelliteEnabledForCarrier= "
                + SatelliteTestApp.getTestSatelliteService()
                .isRequestIsSatelliteEnabledForCarrier());
    }
}
