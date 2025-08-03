/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tradefed.build.cache;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Utility to cache partial download files based on their content. This is based of the zip content
 * so it includes crc for free from the metadata.
 */
public class PartialZipDownloadCache {

    private static PartialZipDownloadCache sDefaultInstance;

    private final LoadingCache<String, File> mFileCache;
    private final File mCacheDir;

    public static PartialZipDownloadCache getDefaultCache() {
        if (sDefaultInstance == null) {
            sDefaultInstance = new PartialZipDownloadCache();
        }
        return sDefaultInstance;
    }

    @VisibleForTesting
    protected PartialZipDownloadCache() {
        try {
            mCacheDir = FileUtil.createTempDir("partial_download_cache_dir");
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread() {
                                @Override
                                public void run() {
                                    synchronized (mFileCache) {
                                        FileUtil.recursiveDelete(mCacheDir);
                                        mFileCache.invalidateAll();
                                    }
                                }
                            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        RemovalListener<String, File> listener =
                new RemovalListener<String, File>() {
                    @Override
                    public void onRemoval(RemovalNotification<String, File> n) {
                        if (n.wasEvicted()) {
                            FileUtil.deleteFile(n.getValue());
                        }
                    }
                };

        mFileCache =
                CacheBuilder.newBuilder()
                        .maximumSize(20000)
                        .expireAfterAccess(3, TimeUnit.HOURS)
                        .removalListener(listener)
                        .build(
                                new CacheLoader<String, File>() {
                                    @Override
                                    public File load(String key) throws IOException {
                                        File cachedFile = new File(mCacheDir, key);
                                        if (cachedFile.exists()) {
                                            return cachedFile;
                                        }
                                        return null;
                                    }
                                });
    }

    /**
     * Finds a file in the cache matching the path and crc
     *
     * @param targetFile location where to return the file
     * @param fileName Path of file
     * @param crc checksum of file in zip
     * @return True if cache file exists, false otherwise
     */
    public boolean getCachedFile(File targetFile, String fileName, String crc) {
        String key = String.format("%s:%s", fileName, crc);
        try {
            synchronized (mFileCache) {
                File cachedFile = mFileCache.getIfPresent(key);
                if (cachedFile != null) {
                    targetFile.getParentFile().mkdirs();
                    FileUtil.hardlinkFile(cachedFile, targetFile, true);
                    return true;
                }
                mFileCache.invalidate(key);
            }
        } catch (IOException e) {
            CLog.e(e);
        }
        return false;
    }

    /**
     * Populate the file in the cache
     *
     * @param toCache File to put in cache
     * @param fileName the path of the file
     * @param crc The crc checksum of file in zip
     */
    public void populateCacheFile(File toCache, String fileName, String crc) {
        synchronized (mFileCache) {
            String key = String.format("%s:%s", fileName, crc);
            File cacheDestination = new File(mCacheDir, key);
            cacheDestination.getParentFile().mkdirs();
            FileUtil.deleteFile(cacheDestination);
            try {
                FileUtil.hardlinkFile(toCache, cacheDestination, true);
                mFileCache.put(key, cacheDestination);
            } catch (IOException e) {
                CLog.e(e);
            }
        }
    }

    @VisibleForTesting
    protected void cleanUpCache() {
        mFileCache.invalidateAll();
        FileUtil.recursiveDelete(mCacheDir);
    }
}
