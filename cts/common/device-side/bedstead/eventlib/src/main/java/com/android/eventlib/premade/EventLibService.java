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

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

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
 * An {@link Service} which logs events for all lifecycle events.
 */
public class EventLibService extends Service {

    private String mOverrideServiceClassName;
    private final IBinder mBinder = new Binder();

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
    public void onCreate() {
        ServiceCreatedEvent.logger(this, getClassName()).log();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ServiceStartedEvent.logger(this, getClassName(), intent, flags, startId).log();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        ServiceDestroyedEvent.logger(this, getClassName()).log();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        ServiceConfigurationChangedEvent.logger(this, getClassName(), newConfig).log();
    }

    @Override
    public void onLowMemory() {
        ServiceLowMemoryEvent.logger(this, getClassName()).log();
    }

    @Override
    public void onTrimMemory(int level) {
        ServiceMemoryTrimmedEvent.logger(this, getClassName(), level).log();
    }

    @Override
    public IBinder onBind(Intent intent) {
        ServiceBoundEvent.logger(this, getClassName(), intent).log();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        ServiceUnboundEvent.logger(this, getClassName(), intent).log();
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        ServiceReboundEvent.logger(this, getClassName(), intent).log();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        ServiceTaskRemovedEvent.logger(this, getClassName(), rootIntent).log();
    }
}
