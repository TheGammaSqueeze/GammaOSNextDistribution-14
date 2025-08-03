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

import com.android.car.qc.provider.BaseLocalQCProvider;

/**
 * Controller for binding to local quick control providers.
 */
public class LocalQCController extends BaseQCController {

    private final BaseLocalQCProvider mProvider;

    private final BaseLocalQCProvider.Notifier mProviderNotifier =
            new BaseLocalQCProvider.Notifier() {
                @Override
                public void notifyUpdate() {
                    if (mShouldListen && !mObservers.isEmpty()) {
                        onQCItemUpdated(mProvider.getQCItem());
                    }
                }
            };

    public LocalQCController(Context context, BaseLocalQCProvider provider) {
        super(context);
        mProvider = provider;
        mProvider.setNotifier(mProviderNotifier);
        mQCItem = mProvider.getQCItem();
    }

    @Override
    public void bind() {
        onQCItemUpdated(mProvider.getQCItem());
    }

    @Override
    protected void updateListening() {
        boolean listen = mShouldListen && !mObservers.isEmpty();
        if (mWasListening != listen) {
            mWasListening = listen;
            mProvider.shouldListen(listen);
            if (listen) {
                mQCItem = mProvider.getQCItem();
                onQCItemUpdated(mQCItem);
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        mProvider.onDestroy();
    }
}
