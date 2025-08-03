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

package com.android.ondevicepersonalization.libraries.plugin.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.FileUtils;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import com.android.ondevicepersonalization.libraries.plugin.PluginInfo.ArchiveInfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Class responsible for managing Plugin archives in the application's cache directory, and
 * forwarding file descriptors for them to a PluginTask.
 */
public final class PluginArchiveManager {
    private static final String TAG = "PluginArchiveManager";
    private static final String CHECKSUM_SUFFIX = ".md5";
    private static final long BIND_TIMEOUT_MS = 2_000;
    private final Context mApplicationContext;

    public PluginArchiveManager(Context applicationContext) {
        this.mApplicationContext = applicationContext;
    }

    /** Interface to wrap the service call the PluginManager wants to make. */
    @FunctionalInterface
    @SuppressWarnings("AndroidApiChecker")
    public interface PluginTask {
        /** Executes the task specified in info. */
        void run(PluginInfoInternal info) throws RemoteException;
    }

    /**
     * Create a File for the specified archive in the cache directory, together with the archive's
     * checksum.
     */
    private File createArchiveFileInCacheDir(ArchiveInfo archive) {
        String filename;
        if (archive.filename() != null) {
            filename = archive.filename();
        } else {
            filename = archive.packageName() + ".apk";
        }
        return new File(mApplicationContext.getCacheDir(), filename);
    }

    /**
     * Copy the passed in Plugin archives to the app's cache directory, open file descriptors for
     * them, wait for the service to be ready, and perform a PluginTask.
     */
    public boolean copyPluginArchivesToCacheAndAwaitService(
            SettableFuture<Boolean> serviceReadiness,
            String serviceName,
            PluginInfoInternal.Builder infoBuilder,
            List<ArchiveInfo> pluginArchives,
            PluginTask pluginTask) {
        // Copy the plugin in app's assets to a file in app's cache directory then await
        // readiness of the service before moving forward.
        // This minimizes the amount of data/code within which the pluginClassName is looked up at
        // sandbox container.
        // Avoid using Context.getAssets().openFd() as it wraps a file descriptor mapped to
        // the-entire-app-apk instead of the-plugin-archive-in-the-app-apk.
        for (ArchiveInfo pluginArchive : pluginArchives) {
            if (pluginArchive.packageName() != null && pluginArchive.filename() != null) {
                // If the package is not null, and the file name is not null, the Plugin APK is an
                // asset of
                // the installed package.
                if (!copyPluginFromPackageAssetsToCacheDir(pluginArchive)) {
                    return false;
                }
            } else if (pluginArchive.packageName() != null && pluginArchive.filename() == null) {
                if (!copyPluginFromInstalledPackageToCacheDir(pluginArchive)) {
                    // If the package is not null, but the file name is null, the Plugin is the APK
                    // from the
                    // installed package.
                    return false;
                }
            } else if (pluginArchive.packageName() == null && pluginArchive.filename() != null) {
                // If the package is null, and the filename is not null, the Plugin is an APK in the
                // current
                // application's assets.
                if (!copyPluginFromAssetsToCacheDir(pluginArchive.filename())) {
                    return false;
                }
            } else {
                Log.e(TAG, "Archive filename and package cannot both be null!");
                return false;
            }
        }

        // Consider further optimizations and restrictions e.g.,
        //  - Cache file descriptors (be careful of the shared file offset among all fd.dup())
        //  - Restrict cpu affinity and usage i.e. background execution

        ImmutableList<Pair<File, String>> archivesInCacheDir =
                ImmutableList.copyOf(
                        Lists.transform(
                                pluginArchives,
                                (ArchiveInfo archive) ->
                                        new Pair<>(
                                                createArchiveFileInCacheDir(archive),
                                                getArchiveChecksum(archive))));
        try (CloseableList<PluginCode> files =
                createCloseablePluginCodeListFromFiles(archivesInCacheDir)) {
            infoBuilder.setPluginCodeList(ImmutableList.copyOf(files.closeables()));

            PluginInfoInternal info = infoBuilder.build();

            if (!maybeAwaitPluginServiceReady(serviceName, serviceReadiness)) {
                return false;
            }
            pluginTask.run(info);
            return true;
        } catch (RemoteException e) {
            Log.e(
                    TAG,
                    String.format(
                            "Error trying to call %s for the plugin: %s",
                            serviceName, pluginArchives));
        } catch (IOException e) {
            Log.e(TAG, String.format("Error trying to load the plugin: %s", pluginArchives));
        }
        return false;
    }

    private static CloseableList<PluginCode> createCloseablePluginCodeListFromFiles(
            Collection<Pair<File, String>> fileChecksumPairs) throws IOException {
        List<PluginCode> fileList = new ArrayList<>();
        for (Pair<File, String> fileChecksumPair : fileChecksumPairs) {
            File file = fileChecksumPair.first;
            String checksum = fileChecksumPair.second;
            fileList.add(
                    PluginCode.builder()
                            .setNativeFd(
                                    ParcelFileDescriptor.open(
                                            file, ParcelFileDescriptor.MODE_READ_ONLY))
                            .setNonNativeFd(
                                    ParcelFileDescriptor.open(
                                            file, ParcelFileDescriptor.MODE_READ_ONLY))
                            .setChecksum(checksum)
                            .build());
        }

        return new CloseableList<>(fileList);
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private static boolean maybeAwaitPluginServiceReady(
            String serviceName, SettableFuture<Boolean> readiness) {
        try {
            // Don't block-wait at app's main thread for service readiness as the readiness
            // signal is asserted at onServiceConnected which also run at apps' main thread
            // ends up deadlock or starvation since the signal will not be handled until the
            // maybeAwaitPluginServiceReady finished.
            if (isMainThread()) {
                if (!readiness.isDone() || !readiness.get()) {
                    Log.w(TAG, String.format("%s is not ready yet", serviceName));
                    return false;
                }
            } else {
                return readiness.get(BIND_TIMEOUT_MS, MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.e(TAG, String.format("Error binding to %s", serviceName));
            return false;
        }
        return true;
    }

    /**
     * When a checksum cannot be found, the empty string tells the executor to fall back to
     * non-caching mode where preparation and setup for a plugin is executed from scratch.
     */
    private static final String DEFAULT_CHECKSUM = "";

    private String getArchiveChecksum(ArchiveInfo pluginArchive) {
        AssetManager assetManager;
        if (pluginArchive.packageName() != null) {
            // TODO(b/247119575): resolve a mutant here. Test for cache hits & misses when expected.
            if (pluginArchive.filename() == null) {
                // TODO(b/248365642): return some other cacheKey, like lastUpdateTime, here.
                return DEFAULT_CHECKSUM;
            }
            try {
                assetManager = packageAssetManager(pluginArchive.packageName());
            } catch (NameNotFoundException e) {
                Log.e(TAG, String.format("Unknown package name %s", pluginArchive.packageName()));
                return DEFAULT_CHECKSUM;
            }
        } else {
            assetManager = mApplicationContext.getAssets();
        }

        String checksumFile =
                Files.getNameWithoutExtension(pluginArchive.filename()) + CHECKSUM_SUFFIX;
        try (InputStream checksumInAssets = assetManager.open(checksumFile);
                InputStreamReader checksumInAssetsReader =
                        new InputStreamReader(checksumInAssets)) {
            return CharStreams.toString(checksumInAssetsReader);
        } catch (IOException e) {
            return DEFAULT_CHECKSUM;
        }
    }

    // TODO(b/247119575): Cover packageAssetManager() with unit tests.
    private AssetManager packageAssetManager(String pluginPackage) throws NameNotFoundException {
        Context pluginContext = mApplicationContext.createPackageContext(pluginPackage, 0);
        return pluginContext.getAssets();
    }

    private boolean copyPluginFromInstalledPackageToCacheDir(ArchiveInfo pluginArchive) {
        try {
            PackageInfo packageInfo =
                    mApplicationContext
                            .getPackageManager()
                            .getPackageInfo(pluginArchive.packageName(), 0);

            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            if (applicationInfo == null) {
                Log.e(
                        TAG,
                        String.format(
                                "Package %s has no ApplicationInfo", pluginArchive.packageName()));
                return false;
            }

            String pluginApkPath = applicationInfo.sourceDir;
            File pluginInCacheDir = createArchiveFileInCacheDir(pluginArchive);

            try (InputStream pluginSrc = new FileInputStream(pluginApkPath);
                    OutputStream pluginDst = new FileOutputStream(pluginInCacheDir);) {
                FileUtils.copy(pluginSrc, pluginDst);
                return true;
            } catch (IOException e) {
                Log.e(TAG, String.format("Error copying %s to cache dir", pluginArchive));
            }
            return false;

        } catch (NameNotFoundException e) {
            Log.e(TAG, String.format("Unknown package name %s", pluginArchive.packageName()));
        }
        return false;
    }

    private boolean copyPluginFromPackageAssetsToCacheDir(ArchiveInfo pluginArchive) {
        try {
            AssetManager assetManager = packageAssetManager(pluginArchive.packageName());
            return copyPluginToCacheDir(pluginArchive.filename(), assetManager);
        } catch (NameNotFoundException e) {
            Log.e(TAG, String.format("Unknown package name %s", pluginArchive.packageName()));
        }
        return false;
    }

    private boolean copyPluginFromAssetsToCacheDir(String pluginArchive) {
        // Checksum filename should be in the format of <plugin_filename>.<CHECKSUM_SUFFIX>.
        // E.g. plugin filename is foo.apk/foo.zip then checksum filename should be foo.md5
        return copyPluginToCacheDir(pluginArchive, mApplicationContext.getAssets());
    }

    // TODO(b/247119575): Cover copyPluginToCacheDir() with unit tests.
    /**
     * Copy the plugin to the cache directory, or reuse it, if it is already present with a matching
     * checksum. Return true if the plugin has been copied or can be reused, return false if there
     * is no reusable plugin in the cache directory and copying was unsuccessful.
     */
    private boolean copyPluginToCacheDir(String pluginArchive, AssetManager assetManager) {
        // If pluginArchive has no file extension, append CHECKSUM_SUFFIX directly.
        String checksumFile = Files.getNameWithoutExtension(pluginArchive) + CHECKSUM_SUFFIX;
        if (canReusePluginInCacheDir(pluginArchive, checksumFile, assetManager)) {
            return true;
        }

        File pluginInCacheDir = new File(mApplicationContext.getCacheDir(), pluginArchive);
        File checksumInCacheDir = new File(mApplicationContext.getCacheDir(), checksumFile);
        try (InputStream pluginSrc = assetManager.open(pluginArchive);
                OutputStream pluginDst = new FileOutputStream(pluginInCacheDir);
                InputStream checksumSrc = assetManager.open(checksumFile);
                OutputStream checksumDst = new FileOutputStream(checksumInCacheDir)) {
            // Data := content (plugin) + metadata (checksum)
            // Enforce the Data writing order: (content -> metadata) like what common file
            // systems do to ensure better fault tolerance and data integrity.
            FileUtils.copy(pluginSrc, pluginDst);
            FileUtils.copy(checksumSrc, checksumDst);
            return true;
        } catch (IOException e) {
            Log.e(
                    TAG,
                    String.format("Error copying %s/%s to cache dir", pluginArchive, checksumFile));
        }
        return false;
    }

    private boolean canReusePluginInCacheDir(
            String pluginArchive, String checksumFile, AssetManager assetManager) {
        // Can reuse the plugin at app's cache directory when both are met:
        //  - The plugin already existed at app's cache directory
        //  - Checksum of plugin_in_assets == Checksum of plugin_at_cache_dir
        File pluginInCacheDir = new File(mApplicationContext.getCacheDir(), pluginArchive);
        if (!pluginInCacheDir.exists()) {
            return false;
        }
        try (InputStream checksumInAssets = assetManager.open(checksumFile);
                InputStreamReader checksumInAssetsReader = new InputStreamReader(checksumInAssets);
                InputStream checksumInCacheDir =
                        new FileInputStream(
                                new File(mApplicationContext.getCacheDir(), checksumFile));
                InputStreamReader checksumInCacheDirReader =
                        new InputStreamReader(checksumInCacheDir)) {
            return CharStreams.toString(checksumInAssetsReader)
                    .equals(CharStreams.toString(checksumInCacheDirReader));
        } catch (IOException e) {
            return false;
        }
    }
}
