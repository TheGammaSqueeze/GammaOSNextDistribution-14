/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.media.audiotestharness.server.config;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

/**
 * Lightweight Guice module that binds the provided instance of the HostConfiguration item to the
 * {@link SharedHostConfiguration} class so it can be shared across any users of the Injector this
 * module is installed in.
 */
public class SharedHostConfigurationModule extends AbstractModule {

    private final SharedHostConfiguration mSharedHostConfiguration;

    private SharedHostConfigurationModule(SharedHostConfiguration sharedHostConfiguration) {
        mSharedHostConfiguration = sharedHostConfiguration;
    }

    public static SharedHostConfigurationModule create(
            SharedHostConfiguration sharedHostConfiguration) {
        Preconditions.checkNotNull(sharedHostConfiguration);
        return new SharedHostConfigurationModule(sharedHostConfiguration);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), SharedHostConfiguration.class)
                .setBinding()
                .toInstance(mSharedHostConfiguration);
    }
}
