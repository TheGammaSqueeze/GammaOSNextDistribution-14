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

import static com.android.car.qc.provider.BaseQCProvider.EXTRA_ITEM;
import static com.android.car.qc.provider.BaseQCProvider.EXTRA_URI;
import static com.android.car.qc.provider.BaseQCProvider.METHOD_BIND;
import static com.android.car.qc.provider.BaseQCProvider.METHOD_DESTROY;
import static com.android.car.qc.provider.BaseQCProvider.METHOD_SUBSCRIBE;
import static com.android.car.qc.provider.BaseQCProvider.METHOD_UNSUBSCRIBE;

import android.content.ContentProviderClient;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.car.qc.QCItem;

import java.util.concurrent.Executor;

/**
 * Controller for binding to remote quick control providers.
 */
public class RemoteQCController extends BaseQCController {
    private static final String TAG = "RemoteQCController";
    private static final long PROVIDER_ANR_TIMEOUT = 3000L;

    private final Uri mUri;
    private final Executor mBackgroundExecutor;
    private final HandlerThread mBackgroundHandlerThread;
    private final ArrayMap<Pair<Uri, QCItemCallback>, QCObserver> mObserverLookup =
            new ArrayMap<>();

    public RemoteQCController(Context context, Uri uri) {
        super(context);
        mUri = uri;
        mBackgroundHandlerThread = new HandlerThread(/* name= */ TAG + "HandlerThread");
        mBackgroundHandlerThread.start();
        mBackgroundExecutor = new HandlerExecutor(
                new Handler(mBackgroundHandlerThread.getLooper()));
    }

    @VisibleForTesting
    RemoteQCController(Context context, Uri uri, Executor backgroundExecutor) {
        super(context);
        mUri = uri;
        mBackgroundHandlerThread = null;
        mBackgroundExecutor = backgroundExecutor;
    }

    @Override
    public void bind() {
        mBackgroundExecutor.execute(this::updateQCItem);
    }

    @Override
    protected void updateListening() {
        boolean listen = mShouldListen && !mObservers.isEmpty();
        mBackgroundExecutor.execute(() -> updateListeningBg(listen));
    }

    @Override
    public void destroy() {
        super.destroy();
        if (mBackgroundHandlerThread != null) {
            mBackgroundHandlerThread.quit();
        }
        try (ContentProviderClient client = getClient()) {
            if (client == null) {
                return;
            }
            Bundle b = new Bundle();
            b.putParcelable(EXTRA_URI, mUri);
            try {
                client.call(METHOD_DESTROY, /* arg= */ null, b);
            } catch (Exception e) {
                Log.d(TAG, "Error destroying QCItem", e);
            }
        }
    }

    @WorkerThread
    private void updateListeningBg(boolean isListening) {
        if (mWasListening != isListening) {
            mWasListening = isListening;
            if (isListening) {
                registerQCCallback(mContext.getMainExecutor(), /* callback= */ this);
                // Update one-time on a different thread so that it can display in parallel
                mBackgroundExecutor.execute(this::updateQCItem);
            } else {
                unregisterQCCallback(this);
            }
        }
    }

    @WorkerThread
    private void updateQCItem() {
        try {
            QCItem item = getQCItem();
            mContext.getMainExecutor().execute(() -> onQCItemUpdated(item));
        } catch (Exception e) {
            Log.d(TAG, "Error fetching QCItem", e);
        }
    }

    private QCItem getQCItem() {
        try (ContentProviderClient provider = getClient()) {
            if (provider == null) {
                return null;
            }
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_URI, mUri);
            Bundle res = provider.call(METHOD_BIND, /* arg= */ null, extras);
            if (res == null) {
                return null;
            }
            res.setDefusable(true);
            res.setClassLoader(QCItem.class.getClassLoader());
            Parcelable parcelable = res.getParcelable(EXTRA_ITEM);
            if (parcelable instanceof QCItem) {
                return (QCItem) parcelable;
            }
            return null;
        } catch (RemoteException e) {
            Log.d(TAG, "Error binding QCItem", e);
            return null;
        }
    }

    private void subscribe() {
        try (ContentProviderClient client = getClient()) {
            if (client == null) {
                return;
            }
            Bundle b = new Bundle();
            b.putParcelable(EXTRA_URI, mUri);
            try {
                client.call(METHOD_SUBSCRIBE, /* arg= */ null, b);
            } catch (Exception e) {
                Log.d(TAG, "Error subscribing to QCItem", e);
            }
        }
    }

    private void unsubscribe()  {
        try (ContentProviderClient client = getClient()) {
            if (client == null) {
                return;
            }
            Bundle b = new Bundle();
            b.putParcelable(EXTRA_URI, mUri);
            try {
                client.call(METHOD_UNSUBSCRIBE, /* arg= */ null, b);
            } catch (Exception e) {
                Log.d(TAG, "Error unsubscribing from QCItem", e);
            }
        }
    }

    @VisibleForTesting
    ContentProviderClient getClient() {
        ContentProviderClient client = mContext.getContentResolver()
                .acquireContentProviderClient(mUri);
        if (client == null) {
            return null;
        }
        client.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
        return client;
    }

    private void registerQCCallback(@NonNull Executor executor, @NonNull QCItemCallback callback) {
        getObserver(callback, new QCObserver(mUri, executor, callback)).startObserving();
    }

    private void unregisterQCCallback(@NonNull QCItemCallback callback) {
        synchronized (mObserverLookup) {
            QCObserver observer = mObserverLookup.remove(new Pair<>(mUri, callback));
            if (observer != null) {
                observer.stopObserving();
            }
        }
    }

    private QCObserver getObserver(QCItemCallback callback, QCObserver observer) {
        Pair<Uri, QCItemCallback> key = new Pair<>(mUri, callback);
        synchronized (mObserverLookup) {
            QCObserver oldObserver = mObserverLookup.put(key, observer);
            if (oldObserver != null) {
                oldObserver.stopObserving();
            }
        }
        return observer;
    }

    private class QCObserver {
        private final Uri mUri;
        private final Executor mExecutor;
        private final QCItemCallback mCallback;
        private boolean mIsSubscribed;

        private final Runnable mUpdateItem = new Runnable() {
            @Override
            public void run() {
                trySubscribe();
                QCItem item = getQCItem();
                mExecutor.execute(() -> mCallback.onQCItemUpdated(item));
            }
        };

        private final ContentObserver mObserver = new ContentObserver(
                new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                android.os.AsyncTask.execute(mUpdateItem);
            }
        };

        QCObserver(Uri uri, Executor executor, QCItemCallback callback) {
            mUri = uri;
            mExecutor = executor;
            mCallback = callback;
        }

        void startObserving() {
            ContentProviderClient provider =
                    mContext.getContentResolver().acquireContentProviderClient(mUri);
            if (provider != null) {
                provider.close();
                mContext.getContentResolver().registerContentObserver(
                        mUri, /* notifyForDescendants= */ true, mObserver);
                trySubscribe();
            }
        }

        void trySubscribe() {
            if (!mIsSubscribed) {
                subscribe();
                mIsSubscribed = true;
            }
        }

        void stopObserving() {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            if (mIsSubscribed) {
                unsubscribe();
                mIsSubscribed = false;
            }
        }
    }
}
