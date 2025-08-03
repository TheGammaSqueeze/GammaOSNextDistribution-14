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

package com.android.car.settings.qc;

import static android.content.ContentResolver.NOTIFY_NO_DELAY;

import android.annotation.MainThread;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.ArrayMap;

import com.android.car.settings.common.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;

/**
 * Base background worker class to allow for CarSetting Quick Control items to work with data that
 * can change continuously.
 * @param <E> {@link SettingsQCItem} class that the worker is operating on.
 */
public abstract class SettingsQCBackgroundWorker<E extends SettingsQCItem> implements Closeable {

    private static final Logger LOG = new Logger(SettingsQCBackgroundWorker.class);

    private static final long QC_UPDATE_THROTTLE_INTERVAL = 300L;

    private static final Map<Uri, SettingsQCBackgroundWorker> LIVE_WORKERS = new ArrayMap<>();

    private final Context mContext;
    private final Uri mUri;
    private SettingsQCItem mQCItem;

    protected SettingsQCBackgroundWorker(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
    }

    protected Uri getUri() {
        return mUri;
    }

    protected Context getContext() {
        return mContext;
    }

    protected E getQCItem() {
        return (E) mQCItem;
    }

    void setQCItem(SettingsQCItem item) {
        mQCItem = item;
    }

    /**
     * Returns the singleton instance of {@link SettingsQCBackgroundWorker} for specified
     * {@link Uri} if exists
     */
    @Nullable
    public static <T extends SettingsQCBackgroundWorker> T getInstance(Uri uri) {
        return (T) LIVE_WORKERS.get(uri);
    }

    /**
     * Returns the singleton instance of {@link SettingsQCBackgroundWorker} for specified {@link
     * SettingsQCItem}
     */
    static SettingsQCBackgroundWorker getInstance(Context context, SettingsQCItem qcItem, Uri uri) {
        SettingsQCBackgroundWorker worker = getInstance(uri);
        if (worker == null) {
            Class<? extends SettingsQCBackgroundWorker> workerClass =
                    qcItem.getBackgroundWorkerClass();
            worker = createInstance(context.getApplicationContext(), uri, workerClass);
            LIVE_WORKERS.put(uri, worker);
        }
        worker.setQCItem(qcItem);
        return worker;
    }

    private static SettingsQCBackgroundWorker createInstance(Context context, Uri uri,
            Class<? extends SettingsQCBackgroundWorker> clazz) {
        LOG.d("create instance: " + clazz);
        try {
            return clazz.getConstructor(Context.class, Uri.class).newInstance(context, uri);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException
                | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Invalid qc background worker: " + clazz, e);
        }
    }

    static void shutdown() {
        for (SettingsQCBackgroundWorker worker : LIVE_WORKERS.values()) {
            try {
                worker.close();
            } catch (IOException e) {
                LOG.w("Shutting down worker failed", e);
            }
        }
        LIVE_WORKERS.clear();
    }

    static void shutdown(Uri uri) {
        SettingsQCBackgroundWorker worker = LIVE_WORKERS.get(uri);
        if (worker != null) {
            try {
                worker.close();
            } catch (IOException e) {
                LOG.w("Shutting down worker failed", e);
            }
            LIVE_WORKERS.remove(uri);
        }
    }

    /**
     * Called when the QCItem is subscribed to. This is the place to register callbacks or
     * initialize scan tasks.
     */
    @MainThread
    protected abstract void onQCItemSubscribe();

    /**
     * Called when the QCItem is unsubscribed from. This is the place to unregister callbacks or
     * perform any final cleanup.
     */
    @MainThread
    protected abstract void onQCItemUnsubscribe();

    /**
     * Notify that data was updated and attempt to sync changes to the QCItem.
     */
    protected final void notifyQCItemChange() {
        NotifyQCItemChangeHandler.getInstance().updateQCItem(this);
    }

    void subscribe() {
        onQCItemSubscribe();
    }

    void unsubscribe() {
        onQCItemUnsubscribe();
        NotifyQCItemChangeHandler.getInstance().cancelQCItemUpdate(this);
    }

    private static class NotifyQCItemChangeHandler extends Handler {

        private static final int MSG_UPDATE_QCITEM = 1000;
        private static NotifyQCItemChangeHandler sHandler;
        private final Map<Uri, Long> mLastUpdateTimeLookup = Collections.synchronizedMap(
                new ArrayMap<>());

        private static NotifyQCItemChangeHandler getInstance() {
            if (sHandler == null) {
                HandlerThread workerThread = new HandlerThread("NotifyQCItemChangeHandler",
                        Process.THREAD_PRIORITY_BACKGROUND);
                workerThread.start();
                sHandler = new NotifyQCItemChangeHandler(workerThread.getLooper());
            }
            return sHandler;
        }

        private NotifyQCItemChangeHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what != MSG_UPDATE_QCITEM) {
                return;
            }

            SettingsQCBackgroundWorker worker = (SettingsQCBackgroundWorker) msg.obj;
            Uri uri = worker.getUri();
            Context context = worker.getContext();
            mLastUpdateTimeLookup.put(uri, SystemClock.uptimeMillis());
            if (UserManager.isVisibleBackgroundUsersEnabled()
                    && UserManager.get(context).isUserVisible()) {
                context.getContentResolver().notifyChange(uri, /* observer= */ null,
                        NOTIFY_NO_DELAY);
            } else {
                context.getContentResolver().notifyChange(uri, /* observer= */ null);
            }
        }

        private void updateQCItem(SettingsQCBackgroundWorker worker) {
            if (hasMessages(MSG_UPDATE_QCITEM, worker)) {
                return;
            }

            Message message = obtainMessage(MSG_UPDATE_QCITEM, worker);
            long lastUpdateTime = mLastUpdateTimeLookup.getOrDefault(worker.getUri(), 0L);
            if (lastUpdateTime == 0L) {
                // Postpone the first update triggering by onQCItemSubscribe() to avoid being too
                // close to the first QCItem bind.
                sendMessageDelayed(message, QC_UPDATE_THROTTLE_INTERVAL);
            } else if (SystemClock.uptimeMillis() - lastUpdateTime
                    > QC_UPDATE_THROTTLE_INTERVAL) {
                sendMessage(message);
            } else {
                sendMessageAtTime(message, lastUpdateTime + QC_UPDATE_THROTTLE_INTERVAL);
            }
        }

        private void cancelQCItemUpdate(SettingsQCBackgroundWorker worker) {
            removeMessages(MSG_UPDATE_QCITEM, worker);
            mLastUpdateTimeLookup.remove(worker.getUri());
        }
    };
}
