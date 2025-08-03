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

package com.android.mms.service.metrics;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.mms.IncomingMms;
import com.android.mms.OutgoingMms;
import com.android.mms.PersistMmsAtoms;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PersistMmsAtomsStorage {
    private static final String TAG = PersistMmsAtomsStorage.class.getSimpleName();

    /** Name of the file where cached statistics are saved to. */
    private static final String FILENAME = "persist_mms_atoms.pb";

    /** Delay to store atoms to persistent storage to bundle multiple operations together. */
    private static final int SAVE_TO_FILE_DELAY_FOR_UPDATE_MILLIS = 30000;

    /**
     * Delay to store atoms to persistent storage during pulls to avoid unnecessary operations.
     *
     * <p>This delay should be short to avoid duplicating atoms or losing pull timestamp in case of
     * crash or power loss.
     */
    private static final int SAVE_TO_FILE_DELAY_FOR_GET_MILLIS = 500;
    private static final SecureRandom sRandom = new SecureRandom();
    /**
     * Maximum number of MMS to store between pulls.
     * Incoming MMS and outgoing MMS are counted separately.
     */
    private final int mMaxNumMms;
    private final Context mContext;
    private final Handler mHandler;
    private final HandlerThread mHandlerThread;
    /** Stores persist atoms and persist states of the puller. */
    @VisibleForTesting
    protected PersistMmsAtoms mPersistMmsAtoms;
    private final Runnable mSaveRunnable =
            new Runnable() {
                @Override
                public void run() {
                    saveAtomsToFileNow();
                }
            };
    /** Whether atoms should be saved immediately, skipping the delay. */
    @VisibleForTesting
    protected boolean mSaveImmediately;

    public PersistMmsAtomsStorage(Context context) {
        mContext = context;

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_RAM_LOW)) {
            Log.i(TAG, "[PersistMmsAtomsStorage]: Low RAM device");
            mMaxNumMms = 5;
        } else {
            mMaxNumMms = 25;
        }
        mPersistMmsAtoms = loadAtomsFromFile();
        mHandlerThread = new HandlerThread("PersistMmsAtomsThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mSaveImmediately = false;
    }

    /** Loads {@link  PersistMmsAtoms} from a file in private storage. */
    private PersistMmsAtoms loadAtomsFromFile() {
        try {
            PersistMmsAtoms atoms = PersistMmsAtoms.parseFrom(
                    Files.readAllBytes(mContext.getFileStreamPath(FILENAME).toPath()));

            // Start from scratch if build changes, since mixing atoms from different builds could
            // produce strange results.
            if (!Build.FINGERPRINT.equals(atoms.getBuildFingerprint())) {
                Log.d(TAG, "[loadAtomsFromFile]: Build changed");
                return makeNewPersistMmsAtoms();
            }
            // check all the fields in case of situations such as OTA or crash during saving.
            List<IncomingMms> incomingMms = sanitizeAtoms(atoms.getIncomingMmsList(), mMaxNumMms);
            List<OutgoingMms> outgoingMms = sanitizeAtoms(atoms.getOutgoingMmsList(), mMaxNumMms);
            long incomingMmsPullTimestamp = sanitizeTimestamp(
                    atoms.getIncomingMmsPullTimestampMillis());
            long outgoingMmsPullTimestamp = sanitizeTimestamp(
                    atoms.getOutgoingMmsPullTimestampMillis());

            // Rebuild atoms after sanitizing.
            atoms = atoms.toBuilder()
                    .clearIncomingMms()
                    .clearOutgoingMms()
                    .addAllIncomingMms(incomingMms)
                    .addAllOutgoingMms(outgoingMms)
                    .setIncomingMmsPullTimestampMillis(incomingMmsPullTimestamp)
                    .setOutgoingMmsPullTimestampMillis(outgoingMmsPullTimestamp)
                    .build();
            return atoms;
        } catch (NoSuchFileException e) {
            Log.e(TAG, "[loadAtomsFromFile]: PersistMmsAtoms file not found");
        } catch (IOException | NullPointerException e) {
            Log.e(TAG, "[loadAtomsFromFile]: cannot load/parse PersistMmsAtoms", e);
        }
        return makeNewPersistMmsAtoms();
    }

    /** Adds an IncomingMms to the storage. */
    public synchronized void addIncomingMms(IncomingMms mms) {
        int existingMmsIndex = findIndex(mms);
        if (existingMmsIndex != -1) {
            // Update mmsCount and avgIntervalMillis of existingMms.
            IncomingMms existingMms = mPersistMmsAtoms.getIncomingMms(existingMmsIndex);
            long updatedMmsCount = existingMms.getMmsCount() + 1;
            long updatedAvgIntervalMillis =
                    (((existingMms.getAvgIntervalMillis() * existingMms.getMmsCount())
                            + mms.getAvgIntervalMillis()) / updatedMmsCount);
            existingMms = existingMms.toBuilder()
                    .setMmsCount(updatedMmsCount)
                    .setAvgIntervalMillis(updatedAvgIntervalMillis)
                    .build();

            mPersistMmsAtoms = mPersistMmsAtoms.toBuilder()
                    .setIncomingMms(existingMmsIndex, existingMms)
                    .build();
        } else {
            // Insert new mms at random place.
            List<IncomingMms> incomingMmsList = insertAtRandomPlace(
                    mPersistMmsAtoms.getIncomingMmsList(), mms, mMaxNumMms);
            mPersistMmsAtoms = mPersistMmsAtoms.toBuilder()
                    .clearIncomingMms()
                    .addAllIncomingMms(incomingMmsList)
                    .build();
        }
        saveAtomsToFile(SAVE_TO_FILE_DELAY_FOR_UPDATE_MILLIS);
    }

    /** Adds an OutgoingMms to the storage. */
    public synchronized void addOutgoingMms(OutgoingMms mms) {
        int existingMmsIndex = findIndex(mms);
        if (existingMmsIndex != -1) {
            // Update mmsCount and avgIntervalMillis of existingMms.
            OutgoingMms existingMms = mPersistMmsAtoms.getOutgoingMms(existingMmsIndex);
            long updatedMmsCount = existingMms.getMmsCount() + 1;
            long updatedAvgIntervalMillis =
                    (((existingMms.getAvgIntervalMillis() * existingMms.getMmsCount())
                            + mms.getAvgIntervalMillis()) / updatedMmsCount);
            existingMms = existingMms.toBuilder()
                    .setMmsCount(updatedMmsCount)
                    .setAvgIntervalMillis(updatedAvgIntervalMillis)
                    .build();

            mPersistMmsAtoms = mPersistMmsAtoms.toBuilder()
                    .setOutgoingMms(existingMmsIndex, existingMms)
                    .build();
        } else {
            // Insert new mms at random place.
            List<OutgoingMms> outgoingMmsList = insertAtRandomPlace(
                    mPersistMmsAtoms.getOutgoingMmsList(), mms, mMaxNumMms);
            mPersistMmsAtoms = mPersistMmsAtoms.toBuilder()
                    .clearOutgoingMms()
                    .addAllOutgoingMms(outgoingMmsList)
                    .build();
        }
        saveAtomsToFile(SAVE_TO_FILE_DELAY_FOR_UPDATE_MILLIS);
    }

    /**
     * Returns and clears the IncomingMms if last pulled longer than {@code minIntervalMillis} ago,
     * otherwise returns {@code null}.
     */
    @Nullable
    public synchronized List<IncomingMms> getIncomingMms(long minIntervalMillis) {
        if ((getWallTimeMillis() - mPersistMmsAtoms.getIncomingMmsPullTimestampMillis())
                > minIntervalMillis) {
            List<IncomingMms> previousIncomingMmsList = mPersistMmsAtoms.getIncomingMmsList();
            mPersistMmsAtoms = mPersistMmsAtoms.toBuilder()
                    .setIncomingMmsPullTimestampMillis(getWallTimeMillis())
                    .clearIncomingMms()
                    .build();
            saveAtomsToFile(SAVE_TO_FILE_DELAY_FOR_GET_MILLIS);
            return previousIncomingMmsList;
        } else {
            return null;
        }
    }

    /**
     * Returns and clears the OutgoingMms if last pulled longer than {@code minIntervalMillis} ago,
     * otherwise returns {@code null}.
     */
    @Nullable
    public synchronized List<OutgoingMms> getOutgoingMms(long minIntervalMillis) {
        if ((getWallTimeMillis() - mPersistMmsAtoms.getOutgoingMmsPullTimestampMillis())
                > minIntervalMillis) {
            List<OutgoingMms> previousOutgoingMmsList = mPersistMmsAtoms.getOutgoingMmsList();
            mPersistMmsAtoms = mPersistMmsAtoms.toBuilder()
                    .setOutgoingMmsPullTimestampMillis(getWallTimeMillis())
                    .clearOutgoingMms()
                    .build();
            saveAtomsToFile(SAVE_TO_FILE_DELAY_FOR_GET_MILLIS);
            return previousOutgoingMmsList;
        } else {
            return null;
        }
    }

    /** Saves a pending {@link PersistMmsAtoms} to a file in private storage immediately. */
    public void flushAtoms() {
        if (mHandler.hasCallbacks(mSaveRunnable)) {
            mHandler.removeCallbacks(mSaveRunnable);
            saveAtomsToFileNow();
        }
    }

    /** Returns an empty PersistMmsAtoms with pull timestamp set to current time. */
    private PersistMmsAtoms makeNewPersistMmsAtoms() {
        // allow pulling only after some time so data are sufficiently aggregated.
        long currentTime = getWallTimeMillis();
        PersistMmsAtoms atoms = PersistMmsAtoms.newBuilder()
                .setBuildFingerprint(Build.FINGERPRINT)
                .setIncomingMmsPullTimestampMillis(currentTime)
                .setOutgoingMmsPullTimestampMillis(currentTime)
                .build();
        return atoms;
    }

    /**
     * Posts message to save a copy of {@link PersistMmsAtoms} to a file after a delay.
     *
     * <p>The delay is introduced to avoid too frequent operations to disk, which would negatively
     * impact the power consumption.
     */
    private void saveAtomsToFile(int delayMillis) {
        if (delayMillis > 0 && !mSaveImmediately) {
            mHandler.removeCallbacks(mSaveRunnable);
            if (mHandler.postDelayed(mSaveRunnable, delayMillis)) {
                return;
            }
        }
        // In case of error posting the event or if delay is 0, save immediately.
        saveAtomsToFileNow();
    }

    /** Saves a copy of {@link PersistMmsAtoms} to a file in private storage. */
    private synchronized void saveAtomsToFileNow() {
        try (FileOutputStream stream = mContext.openFileOutput(FILENAME, Context.MODE_PRIVATE)) {
            stream.write(mPersistMmsAtoms.toByteArray());
        } catch (IOException e) {
            Log.e(TAG, "[saveAtomsToFileNow]: Cannot save PersistMmsAtoms", e);
        }
    }

    /**
     * Inserts a new element in a random position.
     */
    private static <T> List<T> insertAtRandomPlace(List<T> storage, T instance, int maxSize) {
        final int storage_size = storage.size();
        List<T> result = new ArrayList<>(storage);
        if (storage_size == 0) {
            result.add(instance);
        } else if (storage_size == maxSize) {
            // Index of the item suitable for eviction is chosen randomly when the array is full.
            int insertAt = sRandom.nextInt(maxSize);
            result.set(insertAt, instance);
        } else {
            // Insert at random place (by moving the item at the random place to the end).
            int insertAt = sRandom.nextInt(storage_size);
            result.add(result.get(insertAt));
            result.set(insertAt, instance);
        }
        return result;
    }

    /**
     * Returns IncomingMms atom index that has the same dimension values with the given one,
     * or {@code -1} if it does not exist.
     */
    private int findIndex(IncomingMms key) {
        for (int i = 0; i < mPersistMmsAtoms.getIncomingMmsCount(); i++) {
            IncomingMms mms = mPersistMmsAtoms.getIncomingMms(i);
            if (mms.getRat() == key.getRat()
                    && mms.getResult() == key.getResult()
                    && mms.getRoaming() == key.getRoaming()
                    && mms.getSimSlotIndex() == key.getSimSlotIndex()
                    && mms.getIsMultiSim() == key.getIsMultiSim()
                    && mms.getIsEsim() == key.getIsEsim()
                    && mms.getCarrierId() == key.getCarrierId()
                    && mms.getRetryId() == key.getRetryId()
                    && mms.getHandledByCarrierApp() == key.getHandledByCarrierApp()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns OutgoingMms atom index that has the same dimension values with the given one,
     * or {@code -1} if it does not exist.
     */
    private int findIndex(OutgoingMms key) {
        for (int i = 0; i < mPersistMmsAtoms.getOutgoingMmsCount(); i++) {
            OutgoingMms mms = mPersistMmsAtoms.getOutgoingMms(i);
            if (mms.getRat() == key.getRat()
                    && mms.getResult() == key.getResult()
                    && mms.getRoaming() == key.getRoaming()
                    && mms.getSimSlotIndex() == key.getSimSlotIndex()
                    && mms.getIsMultiSim() == key.getIsMultiSim()
                    && mms.getIsEsim() == key.getIsEsim()
                    && mms.getCarrierId() == key.getCarrierId()
                    && mms.getIsFromDefaultApp() == key.getIsFromDefaultApp()
                    && mms.getRetryId() == key.getRetryId()
                    && mms.getHandledByCarrierApp() == key.getHandledByCarrierApp()) {
                return i;
            }
        }
        return -1;
    }

    /** Sanitizes the loaded list of atoms to avoid null values. */
    private <T> List<T> sanitizeAtoms(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /** Sanitizes the loaded list of atoms loaded to avoid null values and enforce max length. */
    private <T> List<T> sanitizeAtoms(List<T> list, int maxSize) {
        list = sanitizeAtoms(list);
        if (list.size() > maxSize) {
            return list.subList(0, maxSize);
        }
        return list;
    }

    /** Sanitizes the timestamp of the last pull loaded from persistent storage. */
    private long sanitizeTimestamp(long timestamp) {
        return timestamp <= 0L ? getWallTimeMillis() : timestamp;
    }

    @VisibleForTesting
    protected long getWallTimeMillis() {
        // Epoch time in UTC, preserved across reboots, but can be adjusted e.g. by the user or NTP.
        return System.currentTimeMillis();
    }
}