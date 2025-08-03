/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.download.mdd;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.android.libraries.mobiledatadownload.Flags;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.MobileDataDownloadBuilder;
import com.google.android.libraries.mobiledatadownload.TimeSource;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.backends.AndroidFileBackend;
import com.google.android.libraries.mobiledatadownload.file.backends.JavaFileBackend;
import com.google.android.libraries.mobiledatadownload.monitor.NetworkUsageMonitor;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;

/** Mobile Data Download Factory. */
public class MobileDataDownloadFactory {
    private static MobileDataDownload sSingleton;
    private static SynchronousFileStorage sSynchronousFileStorage;

    private MobileDataDownloadFactory() {
    }

    /** Returns a singleton of MobileDataDownload. */
    @NonNull
    public static synchronized MobileDataDownload getMdd(
            @NonNull Context context) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSingleton != null) {
                return sSingleton;
            }
        }
        return getMdd(context, getControlExecutor(), getDownloadExecutor());
    }

    /** Returns a singleton of MobileDataDownload. */
    @NonNull
    public static synchronized MobileDataDownload getMdd(
            @NonNull Context context,
            @NonNull ListeningExecutorService controlExecutor,
            @NonNull ListeningExecutorService downloadExecutor) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSingleton == null) {
                SynchronousFileStorage fileStorage = getFileStorage(context);

                // TODO(b/241009783): This only adds the core MDD code. We still need other
                //  components:
                // 1) Add Logger
                // 2) Set Flags
                // 3) Add Configurator.
                sSingleton =
                        MobileDataDownloadBuilder.newBuilder()
                                .setContext(context)
                                .setControlExecutor(controlExecutor)
                                .setTaskScheduler(Optional.of(new MddTaskScheduler(context)))
                                .setNetworkUsageMonitor(getNetworkUsageMonitor(context))
                                .setFileStorage(fileStorage)
                                .setFileDownloaderSupplier(
                                        () -> getFileDownloader(context, downloadExecutor))
                                .addFileGroupPopulator(
                                        new OnDevicePersonalizationFileGroupPopulator(context))
                                .setFlagsOptional(Optional.of(getFlags()))
                                .build();
            }

            return sSingleton;
        }
    }

    @NonNull
    private static NetworkUsageMonitor getNetworkUsageMonitor(@NonNull Context context) {
        return new NetworkUsageMonitor(context, new TimeSource() {
                    @Override
                    public long currentTimeMillis() {
                        return System.currentTimeMillis();
                    }

                    @Override
                    public long elapsedRealtimeNanos() {
                        return SystemClock.elapsedRealtimeNanos();
                    }
                });
    }

    @NonNull
    public static SynchronousFileStorage getFileStorage(@NonNull Context context) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSynchronousFileStorage == null) {
                sSynchronousFileStorage =
                        new SynchronousFileStorage(
                                ImmutableList.of(
                                        /*backends*/ AndroidFileBackend.builder(context).build(),
                                        new JavaFileBackend()),
                                ImmutableList.of(/*transforms*/),
                                ImmutableList.of(/*monitors*/));
            }
            return sSynchronousFileStorage;
        }
    }

    @NonNull
    private static ListeningExecutorService getControlExecutor() {
        return OnDevicePersonalizationExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static FileDownloader getFileDownloader(
            @NonNull Context context,
            @NonNull ListeningExecutorService downloadExecutor) {
        return new OnDevicePersonalizationFileDownloader(getFileStorage(context),
                downloadExecutor, context);
    }

    @NonNull
    private static ListeningExecutorService getDownloadExecutor() {
        return OnDevicePersonalizationExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static Flags getFlags() {
        return new OnDevicePersonalizationMddFlags();
    }
}
