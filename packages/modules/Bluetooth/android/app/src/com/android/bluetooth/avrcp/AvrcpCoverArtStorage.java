/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.bluetooth.avrcp;

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A class abstracting the storage method of cover art images
 */
final class AvrcpCoverArtStorage {
    private static final String TAG = "AvrcpCoverArtStorage";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Object mHandlesLock = new Object();
    private int mNextImageHandle = 0;

    private final Object mImagesLock = new Object();
    private final int mMaxImages;
    private final Map<String, String> mImageHandles;
    private final Map<String, CoverArt> mImages;

    /**
     * Make an image storage object with no bounds on the amount of images it can store
     */
    AvrcpCoverArtStorage() {
        this(0);
    }

    /**
     * Make an image storage object with a bound on the amount of images it can store
     */
    AvrcpCoverArtStorage(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize < 0");
        }
        mMaxImages = maxSize;

        mImageHandles = new HashMap<String, String>();

        // Using a LinkedHashMap allows us to having items ordered LRU -> MRU (true param does this)
        // This way, if we need run out of space we can remove from the front to remove the least
        // recently accessed items
        mImages = new LinkedHashMap<String, CoverArt>(0, 0.75f /* default load factor */, true);
    }

    /**
     * Store an image and get the image handle it's been associated with.
     */
    public String storeImage(CoverArt coverArt) {
        debug("storeImage(CoverArt='" + coverArt + "')");
        if (coverArt == null || coverArt.getImage() == null) {
            debug("Received a null image");
            return null;
        }

        String imageHandle = null;
        String hash = coverArt.getImageHash();
        if (hash == null) {
            error("Failed to get the hash of the image");
            return null;
        }

        synchronized (mImagesLock) {
            if (mImageHandles.containsKey(hash)) {
                debug("Already have image of hash '" + hash + "'");
                imageHandle = mImageHandles.get(hash);
                debug("Sending back existing handle '" + imageHandle + "'");
                return imageHandle;
            } else {
                debug("Got a new image, hash='" + hash + "'");
                imageHandle = getNextImageHandle();
                if (imageHandle != null) {
                    mImageHandles.put(hash, imageHandle);
                }
            }

            if (imageHandle != null) {
                debug("Image " + coverArt + " stored at handle '" + imageHandle + "'");
                coverArt.setImageHandle(imageHandle);
                mImages.put(imageHandle, coverArt);
                trimToSize();
            } else {
                error("Failed to store image. Could not get a handle.");
            }
        }
        return imageHandle;
    }

    /**
     * Get the image stored at the given image handle, if it exists
     */
    public CoverArt getImage(String imageHandle) {
        debug("getImage(" + imageHandle + ")");
        if (imageHandle == null) return null;
        synchronized (mImagesLock) {
            CoverArt coverArt = mImages.get(imageHandle);
            debug("Image handle '" + imageHandle + "' -> " + coverArt);
            return coverArt;
        }
    }

    /**
     * Clear out all stored images and image handles
     */
    public void clear() {
        synchronized (mImagesLock) {
            mImages.clear();
            mImageHandles.clear();
        }

        synchronized (mHandlesLock) {
            mNextImageHandle = 0;
        }
    }

    private void trimToSize() {
        if (mMaxImages <= 0) return;
        synchronized (mImagesLock) {
            while (mImages.size() > mMaxImages) {
                Map.Entry<String, CoverArt> entry = mImages.entrySet().iterator().next();
                String imageHandle = entry.getKey();
                CoverArt coverArt = entry.getValue();
                debug("Evicting '" + imageHandle + "' -> " + coverArt);
                mImages.remove(imageHandle);
                mImageHandles.remove(coverArt.getImageHash());
            }
        }
    }

    /**
     * Get the next available image handle value if one is available.
     *
     * Values are integers in the domain [0, 9999999], represented as zero-padded strings. Getting
     * an image handle assumes you will use it.
     */
    private String getNextImageHandle() {
        synchronized (mHandlesLock) {
            if (mNextImageHandle > 9999999) {
                error("No more image handles left");
                return null;
            }

            String handle = String.valueOf(mNextImageHandle);
            while (handle.length() != 7) {
                handle = "0" + handle;
            }

            debug("Allocated handle " + mNextImageHandle + " --> '" + handle + "'");
            mNextImageHandle++;
            return handle;
        }
    }

    public void dump(StringBuilder sb) {
        int bytes = 0;
        sb.append("\n\timages (" + mImageHandles.size());
        if (mMaxImages > 0) sb.append(" / " + mMaxImages);
        sb.append("):");
        sb.append("\n\t\tHandle   : Hash                              : CoverArt");
        synchronized (mImagesLock) {
            // Be sure to use entry set below or each access well count to the ordering
            for (Map.Entry<String, CoverArt> entry : mImages.entrySet()) {
                String imageHandle = entry.getKey();
                CoverArt coverArt = entry.getValue();
                String hash = "<           NOT IN SET          >";
                for (String key : mImageHandles.keySet()) {
                    String handle = mImageHandles.get(key);
                    if (imageHandle.equals(handle)) {
                        hash = key;
                    }
                }
                sb.append(String.format("\n\t\t%-8s : %-32s : %s", imageHandle, hash, coverArt));
                bytes += coverArt.size();
            }
        }
        sb.append("\n\tImage bytes: " + bytes);
    }

    /**
     * Print a message to DEBUG if debug output is enabled
     */
    private void debug(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    /**
     * Print a message to ERROR
     */
    private void error(String msg) {
        Log.e(TAG, msg);
    }
}
