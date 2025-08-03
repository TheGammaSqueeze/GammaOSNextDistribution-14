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

package com.android.car.qc.provider;

import android.content.Context;

import com.android.car.qc.QCItem;

/**
 * Base class for local Quick Control providers.
 */
public abstract class BaseLocalQCProvider {

    /**
     * Callback to be executed when the QCItem updates.
     */
    public interface Notifier {
        /**
         * Called when the QCItem has been updated.
         */
        default void notifyUpdate() {
        }
    }

    private Notifier mNotifier;
    private boolean mIsListening;
    protected final Context mContext;

    public BaseLocalQCProvider(Context context) {
        mContext = context;
    }

    /**
     * Set the notifier that should be called when the QCItem updates.
     */
    public void setNotifier(Notifier notifier) {
        mNotifier = notifier;
    }

    /**
     * Update whether or not the provider should be listening for live updates.
     */
    public void shouldListen(boolean listen) {
        if (mIsListening == listen) {
            return;
        }
        mIsListening = listen;
        if (listen) {
            onSubscribed();
        } else {
            onUnsubscribed();
        }
    }

    /**
     * Method to create and return a {@link QCItem}.
     */
    public abstract QCItem getQCItem();

    /**
     * Called to inform the provider that it has been subscribed to.
     */
    protected void onSubscribed() {
    }

    /**
     * Called to inform the provider that it has been unsubscribed from.
     */
    protected void onUnsubscribed() {
    }

    /**
     * Called to inform the provider that it is being destroyed.
     */
    public void onDestroy() {
    }

    protected void notifyChange() {
        if (mNotifier != null) {
            mNotifier.notifyUpdate();
        }
    }
}
