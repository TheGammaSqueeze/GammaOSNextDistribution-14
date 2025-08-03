/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.service;

import com.android.libraries.rcs.simpleclient.SimpleRcsClientContext;

import java.util.Set;

/**
 * Covers service state and feature tag association.
 */
public interface ImsService {

    /**
     * Associated feature tags.
     * Services will started and stopped according to the registration state of the feature tags.
     */
    Set<String> getFeatureTags();

    /**
     * Services started when their feature tags are enabled from the
     * {@link com.android.libraries.rcs.simpleclient.registration.RegistrationController}.
     * Context is made available to the ImsService here.
     */
    void start(SimpleRcsClientContext context);

    /**
     * Services stopped when their feature tags are disabled from
     * {@link com.android.libraries.rcs.simpleclient.registration.RegistrationController}
     */
    void stop();

    /**
     * Simple callback mechanism for monitoring feature tag/ims service state.
     */
    void onStateChange(StateChangeCallback cb);
}
