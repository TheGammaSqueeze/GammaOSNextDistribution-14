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

package com.android.cts.devicecontrolsapp;

import android.service.controls.Control;
import android.service.controls.ControlsProviderService;
import android.service.controls.actions.ControlAction;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class ControlsService extends ControlsProviderService {

    @NonNull
    @Override
    public Flow.Publisher<Control> createPublisherForAllAvailable() {
        return Flow.Subscriber::onComplete;
    }

    @NonNull
    @Override
    public Flow.Publisher<Control> createPublisherFor(@NonNull List<String> controlIds) {
        return Flow.Subscriber::onComplete;
    }

    @Override
    public void performControlAction(@NonNull String controlId, @NonNull ControlAction action,
            @NonNull Consumer<Integer> consumer) {

    }
}
