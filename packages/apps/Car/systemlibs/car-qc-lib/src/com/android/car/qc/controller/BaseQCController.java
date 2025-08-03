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

package com.android.car.qc.controller;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.Observer;

import com.android.car.qc.QCItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Base controller class for Quick Controls.
 */
public abstract class BaseQCController implements QCItemCallback {
    protected final Context mContext;
    protected final List<Observer<QCItem>> mObservers = new ArrayList<>();
    protected boolean mShouldListen = false;
    protected boolean mWasListening = false;
    protected QCItem mQCItem;

    public BaseQCController(Context context) {
        mContext = context;
    }

    /**
     * Update whether or not the controller should be listening to updates from the provider.
     */
    public void listen(boolean shouldListen) {
        mShouldListen = shouldListen;
        updateListening();
    }

    /**
     * Add a QCItem observer to the controller.
     */
    @UiThread
    public void addObserver(Observer<QCItem> observer) {
        mObservers.add(observer);
        updateListening();
    }

    /**
     * Remove a QCItem observer from the controller.
     */
    @UiThread
    public void removeObserver(Observer<QCItem> observer) {
        mObservers.remove(observer);
        updateListening();
    }

    @UiThread
    @Override
    public void onQCItemUpdated(@Nullable QCItem item) {
        mQCItem = item;
        mObservers.forEach(o -> o.onChanged(mQCItem));
    }

    /**
     * Destroy the controller. This should be called when the controller is no longer needed so
     * the listeners can be cleaned up.
     */
    public void destroy() {
        mShouldListen = false;
        mObservers.clear();
        updateListening();
    }

    /**
     * Perform a single retrieval from the provider (without subscribing to live updates).
     */
    public abstract void bind();

    /**
     * Subclasses must override this method to handle a listening update.
     */
    protected abstract void updateListening();
}
