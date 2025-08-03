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

package com.android.tests.providers.sdkmeasurement;

import android.adservices.clients.measurement.MeasurementClient;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.adservices.common.WebUtil;
import com.android.compatibility.common.util.TestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SdkMeasurement extends SandboxedSdkProvider {
    private static final String TAG = "SdkMeasurement";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Uri SOURCE_REGISTRATION_URI = WebUtil.validUri("https://test.test/source");
    private static final Uri TRIGGER_REGISTRATION_URI =
            WebUtil.validUri("https://test.test/trigger");
    private static final Uri DESTINATION = WebUtil.validUri("http://destination.test");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://os.destination");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("http://web-destination.test");
    private static final Uri ORIGIN_URI = WebUtil.validUri("http://origin-uri.test");
    private static final Uri DOMAIN_URI = WebUtil.validUri("http://domain-uri.test");

    private MeasurementClient mMeasurementClient;

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {
        try {
            setup();
        } catch (Exception e) {
            final String errorMessage = "Error setting up SdkMeasurement: " + e.getMessage();
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(e, new Bundle());
        }

        execute(
                "registerSource",
                () ->
                        mMeasurementClient
                                .registerSource(SOURCE_REGISTRATION_URI, /* inputEvent = */ null)
                                .get());

        execute(
                "registerTrigger",
                () -> mMeasurementClient.registerTrigger(TRIGGER_REGISTRATION_URI).get());

        execute(
                "registerWebSource",
                () ->
                        mMeasurementClient
                                .registerWebSource(buildDefaultWebSourceRegistrationRequest())
                                .get());

        execute(
                "registerWebTrigger",
                () ->
                        mMeasurementClient
                                .registerWebTrigger(buildDefaultWebTriggerRegistrationRequest())
                                .get());

        execute(
                "deleteRegistrations",
                () ->
                        mMeasurementClient
                                .deleteRegistrations(
                                        new DeletionRequest.Builder()
                                                .setOriginUris(
                                                        Collections.singletonList(ORIGIN_URI))
                                                .setDomainUris(
                                                        Collections.singletonList(DOMAIN_URI))
                                                .setStart(Instant.ofEpochMilli(0))
                                                .setEnd(Instant.now())
                                                .build())
                                .get());

        // If we got this far, that means the test succeeded
        return new SandboxedSdk(new Binder());
    }

    @Override
    public View getView(
            @NonNull Context windowContext, @NonNull Bundle params, int width, int height) {
        return null;
    }

    private void setup() {
        mMeasurementClient =
                new MeasurementClient.Builder()
                        .setContext(getContext())
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
    }

    private void execute(String apiName, TestUtils.RunnableWithThrow executable)
            throws LoadSdkException {
        try {
            executable.run();
        } catch (Exception e) {
            final String errorMessage = String.format("Error in %s: %s", apiName, e.getMessage());
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(e, new Bundle());
        }
    }

    private WebSourceRegistrationRequest buildDefaultWebSourceRegistrationRequest() {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(SOURCE_REGISTRATION_URI)
                        .setDebugKeyAllowed(false)
                        .build();

        return new WebSourceRegistrationRequest.Builder(
                        Collections.singletonList(webSourceParams), SOURCE_REGISTRATION_URI)
                .setInputEvent(null)
                .setAppDestination(OS_DESTINATION)
                .setWebDestination(WEB_DESTINATION)
                .setVerifiedDestination(null)
                .build();
    }

    private WebTriggerRegistrationRequest buildDefaultWebTriggerRegistrationRequest() {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(TRIGGER_REGISTRATION_URI).build();

        return new WebTriggerRegistrationRequest.Builder(
                        Collections.singletonList(webTriggerParams), DESTINATION)
                .build();
    }
}
