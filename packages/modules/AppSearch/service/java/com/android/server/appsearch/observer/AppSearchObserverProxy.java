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

package com.android.server.appsearch.observer;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.aidl.IAppSearchObserverProxy;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Objects;

/**
 * A wrapper that adapts {@link android.app.appsearch.aidl.IAppSearchObserverProxy} to the
 * {@link android.app.appsearch.observer.ObserverCallback} interface.
 *
 * <p>When using this class, you must register for {@link android.os.IBinder#linkToDeath}
 * notifications on the stub you provide to the constructor, to unregister this class from
 * {@link com.android.server.appsearch.external.localstorage.AppSearchImpl} when binder dies.
 *
 * @hide
 */
public class AppSearchObserverProxy implements ObserverCallback {
    private static final String TAG = "AppSearchObserverProxy";

    private final IAppSearchObserverProxy mStub;

    public AppSearchObserverProxy(@NonNull IAppSearchObserverProxy stub) {
        mStub = Objects.requireNonNull(stub);
    }

    @Override
    public void onSchemaChanged(@NonNull SchemaChangeInfo changeInfo) {
        try {
            mStub.onSchemaChanged(
                    changeInfo.getPackageName(),
                    changeInfo.getDatabaseName(),
                    new ArrayList<>(changeInfo.getChangedSchemaNames()));
        } catch (RemoteException e) {
            onRemoteException(e);
        }
    }

    @Override
    public void onDocumentChanged(@NonNull DocumentChangeInfo changeInfo) {
        try {
            mStub.onDocumentChanged(
                    changeInfo.getPackageName(),
                    changeInfo.getDatabaseName(),
                    changeInfo.getNamespace(),
                    changeInfo.getSchemaName(),
                    new ArrayList<>(changeInfo.getChangedDocumentIds()));
        } catch (RemoteException e) {
            onRemoteException(e);
        }
    }

    private void onRemoteException(@NonNull RemoteException e) {
        // The originating app has disconnected. The user of this class must watch for binder
        // disconnections and unregister us, so we don't have to take any special action.
        Log.w(TAG, "AppSearchObserver failed to fire; stub disconnected", e);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchObserverProxy)) return false;
        AppSearchObserverProxy that = (AppSearchObserverProxy) o;
        return Objects.equals(mStub.asBinder(), that.mStub.asBinder());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mStub.asBinder());
    }
}
