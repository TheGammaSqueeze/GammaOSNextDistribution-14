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

package com.android.eventlib;

import android.content.Context;

import java.time.Instant;

/** Superclass of all classes which allow creating new event logs. */
public abstract class EventLogger<E extends Event> {

    /** Default {@link EventLogger} to be used when there are no custom fields to be logged. */
    public static class Default<F extends Event> extends EventLogger<F> {
        public Default(Context context, F event) {
            super(context, event);
        }
    }

    protected EventLogger(Context context, E event) {
        if (context == null || event == null) {
            throw new NullPointerException();
        }
        mContext = context.getApplicationContext();
        mEvent = event;
    }

    private final Context mContext;
    protected final E mEvent;

    /**
     * Commit the log so it is accessible to be queried.
     *
     * <p>This will record the current package name and timestamp.
     */
    public void log() {
        mEvent.mPackageName = mContext.getPackageName();
        mEvent.mTimestamp = Instant.now();

        Events.getInstance(mContext, /* needsHistory= */ false).log(mEvent);
    }
}
