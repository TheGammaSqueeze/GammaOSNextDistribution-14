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

package com.android.queryable.info;

import android.app.Service;
import android.content.IntentFilter;

import java.util.HashSet;
import java.util.Set;


/**
 * Wrapper for information about an {@link Service}.
 *
 * <p>This is used instead of {@link Service} so that it can be easily serialized.
 */
public final class ServiceInfo extends ClassInfo {

    private final Set<IntentFilter> mIntentFilters;

    /** Return a new builder for {@link ServiceInfo}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Return a builder for {@link ServiceInfo} with the service class name set. */
    public static Builder builder(android.content.pm.ServiceInfo serviceInfo) {
        return builder()
                .serviceClass(serviceInfo.name);
    }

    private ServiceInfo(String serviceClass, Set<IntentFilter> intentFilters) {
        super(serviceClass);
        if (intentFilters == null) {
            mIntentFilters = new HashSet<>();
        } else {
            mIntentFilters = intentFilters;
        }
    }

    /** Return the intent filters of this service.*/
    public Set<IntentFilter> intentFilters() {
        return mIntentFilters;
    }

    @Override
    public String toString() {
        return "Service{"
                + "class=" + super.toString()
                + "intentFilters=" + mIntentFilters
                + "}";
    }

    /** Builder for {@link ServiceInfo}. */
    public static final class Builder {
        String mServiceClass;
        Set<IntentFilter> mIntentFilters;

        /** Set the serviceClassName with the class name provided. */
        public Builder serviceClass(String serviceClassName) {
            mServiceClass = serviceClassName;
            return this;
        }

        /** Set the serviceClassName with the class name of the service provided. */
        public Builder serviceClass(Service service) {
            return serviceClass(service.getClass());
        }

        /** Set the serviceClassName with the class name from the service class provided. */
        public Builder serviceClass(Class<? extends Service> serviceClass) {
            return serviceClass(serviceClass.getName());
        }

        /** Set the intent filters with the set of intent filters provided */
        public ServiceInfo.Builder intentFilters(Set<IntentFilter> intentFilters) {
            mIntentFilters = intentFilters;
            return this;
        }

        /** Build the {@link ServiceInfo}*/
        public ServiceInfo build() {
            return new ServiceInfo(
                    mServiceClass,
                    mIntentFilters
            );
        }
    }
}
