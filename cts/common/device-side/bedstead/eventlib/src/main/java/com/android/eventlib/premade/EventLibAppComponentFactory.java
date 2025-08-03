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

package com.android.eventlib.premade;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.util.Log;

/**
 * An {@link AppComponentFactory} which redirects invalid class names to premade EventLib classes.
 */
public final class EventLibAppComponentFactory extends AppComponentFactory {

    private static final String LOG_TAG = "EventLibACF";

    @Override
    public Activity instantiateActivity(ClassLoader classLoader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {

        try {
            return super.instantiateActivity(classLoader, className, intent);
        } catch (ClassNotFoundException e) {
            Log.d(LOG_TAG,
                    "Activity class (" + className + ") not found, routing to EventLibActivity");
            EventLibActivity activity =
                    (EventLibActivity) super.instantiateActivity(
                            classLoader, EventLibActivity.class.getName(), intent);
            activity.setOverrideActivityClassName(className);
            return activity;
        }
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader classLoader, String className,
            Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            return super.instantiateReceiver(classLoader, className, intent);
        } catch (ClassNotFoundException e) {
            if (className.endsWith("DeviceAdminReceiver")) {
                Log.d(LOG_TAG, "Broadcast Receiver class (" + className
                        + ") not found, routing to TestAppDeviceAdminReceiver");
                EventLibDeviceAdminReceiver receiver = (EventLibDeviceAdminReceiver)
                        super.instantiateReceiver(
                                classLoader, EventLibDeviceAdminReceiver.class.getName(),
                                intent);
                receiver.setOverrideDeviceAdminReceiverClassName(className);
                return receiver;
            } else if (className.endsWith("DelegatedAdminReceiver")) {
                Log.d(LOG_TAG, "Broadcast Receiver class (" + className
                        + ") not found, routing to EventLibDelegatedAdminReceiver");
                EventLibDelegatedAdminReceiver receiver = (EventLibDelegatedAdminReceiver)
                        super.instantiateReceiver(
                                classLoader, EventLibDelegatedAdminReceiver.class.getName(),
                                intent);
                receiver.setOverrideDelegatedAdminReceiverClassName(className);
                return receiver;
            }

            Log.d(LOG_TAG, "Broadcast Receiver class (" + className
                    + ") not found, routing to EventLibBroadcastReceiver");

            EventLibBroadcastReceiver receiver = (EventLibBroadcastReceiver)
                    super.instantiateReceiver(
                            classLoader, EventLibBroadcastReceiver.class.getName(), intent);
            receiver.setOverrideBroadcastReceiverClassName(className);
            return receiver;
        }
    }

    @Override
    public Service instantiateService(ClassLoader classLoader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            return super.instantiateService(classLoader, className, intent);
        } catch (ClassNotFoundException e) {
            if (className.endsWith("ContentSuggestionsService")) {
                Log.d(LOG_TAG, "Service class (" + className
                        + ") not found, routing to EventLibContentSuggestionsService");

                EventLibContentSuggestionsService service = (EventLibContentSuggestionsService)
                        super.instantiateService(classLoader,
                                EventLibContentSuggestionsService.class.getName(), intent);
                service.setOverrideServiceClassName(className);
                return service;
            }

            Log.d(LOG_TAG, "Service class (" + className
                    + ") not found, routing to EventLibService");

            EventLibService service = (EventLibService)
                    super.instantiateService(
                            classLoader, EventLibService.class.getName(), intent);
            service.setOverrideServiceClassName(className);
            return service;
        }
    }
}
