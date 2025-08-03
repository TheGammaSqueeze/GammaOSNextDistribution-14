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

package com.android.eventlib.premade;

import android.app.contentsuggestions.ClassificationsRequest;
import android.app.contentsuggestions.ContentSuggestionsManager;
import android.app.contentsuggestions.SelectionsRequest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.service.contentsuggestions.ContentSuggestionsService;

import com.android.eventlib.events.services.ServiceBoundEvent;
import com.android.eventlib.events.services.ServiceConfigurationChangedEvent;
import com.android.eventlib.events.services.ServiceCreatedEvent;
import com.android.eventlib.events.services.ServiceDestroyedEvent;
import com.android.eventlib.events.services.ServiceLowMemoryEvent;
import com.android.eventlib.events.services.ServiceMemoryTrimmedEvent;
import com.android.eventlib.events.services.ServiceReboundEvent;
import com.android.eventlib.events.services.ServiceStartedEvent;
import com.android.eventlib.events.services.ServiceTaskRemovedEvent;
import com.android.eventlib.events.services.ServiceUnboundEvent;

/**
 * A {@link ContentSuggestionsService} which logs events.
 *
 * <p>Note that this does not support {@link ServiceBoundEvent}.
 */
public class EventLibContentSuggestionsService extends ContentSuggestionsService {

    private String mOverrideServiceClassName;

    public void setOverrideServiceClassName(String overrideServiceClassName) {
        mOverrideServiceClassName = overrideServiceClassName;
    }

    /**
     * Gets the class name of this service.
     *
     * <p>If the class name has been overridden, that will be returned instead.
     */
    public String getClassName() {
        if (mOverrideServiceClassName != null) {
            return mOverrideServiceClassName;
        }

        return EventLibService.class.getName();
    }

    public ComponentName getComponentName() {
        return new ComponentName(getApplication().getPackageName(), getClassName());
    }

    @Override
    public void onProcessContextImage(int taskId, Bitmap contextImage, Bundle extras) {
        // TODO(279397270): Fill in
    }

    @Override
    public void onSuggestContentSelections(SelectionsRequest request,
            ContentSuggestionsManager.SelectionsCallback callback) {
        // TODO(279397270): Fill in
    }

    @Override
    public void onClassifyContentSelections(ClassificationsRequest request,
            ContentSuggestionsManager.ClassificationsCallback callback) {
        // TODO(279397270): Fill in
    }

    @Override
    public void onNotifyInteraction(String requestId, Bundle interaction) {
        // TODO(279397270): Fill in
    }

    @Override
    public void onCreate() {
        ServiceCreatedEvent.logger(this, getClassName()).log();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ServiceStartedEvent.logger(this, getClassName(), intent, flags, startId).log();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        ServiceDestroyedEvent.logger(this, getClassName()).log();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        ServiceConfigurationChangedEvent.logger(this, getClassName(), newConfig).log();
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        ServiceLowMemoryEvent.logger(this, getClassName()).log();
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        ServiceMemoryTrimmedEvent.logger(this, getClassName(), level).log();
        super.onTrimMemory(level);
    }

    // Cannot override onBind due to final

    @Override
    public boolean onUnbind(Intent intent) {
        ServiceUnboundEvent.logger(this, getClassName(), intent).log();
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        ServiceReboundEvent.logger(this, getClassName(), intent).log();
        super.onRebind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        ServiceTaskRemovedEvent.logger(this, getClassName(), rootIntent).log();
        super.onTaskRemoved(rootIntent);
    }
}
