/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tests.sdkprovider.restrictions;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.UserDictionary;
import android.view.View;

import java.util.List;

public class RestrictionsSandboxedSdkProvider extends SandboxedSdkProvider {

    static class RestrictionsSdkApiImpl extends IRestrictionsSdkApi.Stub {
        private final Context mContext;
        private final ContentResolver mContentResolver;

        RestrictionsSdkApiImpl(Context sdkContext) {
            mContext = sdkContext;
            mContentResolver = mContext.getContentResolver();
        }

        @Override
        public void registerBroadcastReceiver(List<String> actions) {
            IntentFilter filter = new IntentFilter();
            for (String action : actions) {
                filter.addAction(action);
            }
            mContext.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {}
                    },
                    filter,
                    Context.RECEIVER_EXPORTED);
        }

        @Override
        public void getContentProvider() {
            mContentResolver.query(
                    UserDictionary.Words.CONTENT_URI,
                    /*projectionName=*/ null,
                    /*queryArgs=*/ null,
                    /*cancellationSignal=*/ null);
        }

        @Override
        public void getContentProviderByAuthority(String authority) {
            mContentResolver.query(
                    Uri.parse("content://" + authority),
                    /*projectionName=*/ null,
                    /*queryArgs=*/ null,
                    /*cancellationSignal=*/ null);
        }

        @Override
        public void registerContentObserver() {
            final ContentObserver observer =
                    new ContentObserver(new Handler(Looper.getMainLooper())) {};
            mContentResolver.registerContentObserver(
                    UserDictionary.Words.CONTENT_URI, /*notifyForDescendants=*/ true, observer);
        }
    }

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {
        return new SandboxedSdk(new RestrictionsSdkApiImpl(getContext()));
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        return new View(windowContext);
    }
}
