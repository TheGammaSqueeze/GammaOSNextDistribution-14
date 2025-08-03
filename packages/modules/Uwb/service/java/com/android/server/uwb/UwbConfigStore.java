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

package com.android.server.uwb;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.proto.uwb.UwbConfigProto;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides a mechanism to save data to persistent store files {@link StoreFile}.
 * Modules can register a {@link StoreData} instance indicating the {@link StoreFile}
 * into which they want to save their data to.
 *
 * NOTE:
 * <li>Modules can register their {@link StoreData} using
 * {@link UwbConfigStore#registerStoreData(StoreData)} directly, but should
 * use {@link UwbConfigStore#saveToStore(boolean)} for any writes.</li>
 * <li>{@link UwbConfigStore} controls {@link UwbConfigStore} and initiates read at bootup and
 * store file changes on user switch.</li>
 * <li>Not thread safe!</li>
 */
public class UwbConfigStore {
    /**
     * Config store file for general shared store file.
     */
    public static final int STORE_FILE_SHARED_GENERAL = 0;

    /**
     * Config store file for general user store file.
     */
    public static final int STORE_FILE_USER_GENERAL = 1;

    @IntDef(prefix = { "STORE_FILE_" }, value = {
            STORE_FILE_SHARED_GENERAL,
            STORE_FILE_USER_GENERAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StoreFileId { }

    /**
     * Current config store data version. This will be incremented for any additions.
     */
    private static final int CURRENT_CONFIG_STORE_DATA_VERSION = 1;

    /** This list of older versions will be used to restore data from older config store. */
    /**
     * First version of the config store data format.
     */
    public static final int INITIAL_CONFIG_STORE_DATA_VERSION = 1;

    /**
     * Alarm tag to use for starting alarms for buffering file writes.
     */
    @VisibleForTesting
    public static final String BUFFERED_WRITE_ALARM_TAG = "WriteBufferAlarm";
    /**
     * Log tag.
     */
    private static final String TAG = "UwbConfigStore";
    /**
     * Time interval for buffering file writes for non-forced writes
     */
    private static final int BUFFERED_WRITE_ALARM_INTERVAL_MS = 10 * 1000;
    /**
     * Config store file name for general shared store file.
     */
    private static final String STORE_FILE_NAME_SHARED_GENERAL = "UwbConfigStore.bin";
    /**
     * Config store file name for general user store file.
     */
    private static final String STORE_FILE_NAME_USER_GENERAL = "UwbConfigStore.bin";
    /**
     * Mapping of Store file Id to Store file names.
     */
    private static final SparseArray<String> STORE_ID_TO_FILE_NAME =
            new SparseArray<String>() {{
                put(STORE_FILE_SHARED_GENERAL, STORE_FILE_NAME_SHARED_GENERAL);
                put(STORE_FILE_USER_GENERAL, STORE_FILE_NAME_USER_GENERAL);
            }};
    /**
     * Handler instance to post alarm timeouts to
     */
    private final Handler mEventHandler;

    /**
     * Alarm manager instance to start buffer timeout alarms.
     */
    private final AlarmManager mAlarmManager;
    /**
     * Reference to UwbInjector
     */
    private final UwbInjector mUwbInjector;

    /**
     * Shared config store file instance. There are 2 shared store files:
     * {@link #STORE_FILE_NAME_SHARED_GENERAL}.
     */
    private final List<StoreFile> mSharedStores;
    /**
     * User specific store file instances. There are 2 user store files:
     * {@link #STORE_FILE_NAME_USER_GENERAL}.
     */
    private List<StoreFile> mUserStores;

    /**
     * Flag to indicate if there is a buffered write pending.
     */
    private boolean mBufferedWritePending = false;
    /**
     * Alarm listener for flushing out any buffered writes.
     */
    private boolean mPendingStoreRead = false;
    /**
     * Flag to indicate if the user unlock was deferred until the store load occurs.
     */
    private boolean mDeferredUserUnlockRead = false;
    /**
     * Current logged in user ID.
     */
    private int mCurrentUserId = UserHandle.SYSTEM.getIdentifier();
    /**
     * Flag to indicate that the new user's store has not yet been read since user switch.
     * Initialize this flag to |true| to trigger a read on the first user unlock after
     * bootup.
     */
    private boolean mPendingUnlockStoreRead = true;
    /**
     * For verbose logs
     */
    private boolean mVerboseLoggingEnabled = true;

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    private final AlarmManager.OnAlarmListener mBufferedWriteListener =
            () -> {
                try {
                    writeBufferedData();
                } catch (IOException e) {
                    Log.wtf(TAG, "Buffered write failed", e);
                }
            };

    public List<StoreData> getStoreDataList() {
        return mStoreDataList;
    }

    /**
     * List of data containers.
     */
    private final List<StoreData> mStoreDataList;

    /**
     * Create a new instance of UwbConfigStore.
     * Note: The store file instances have been made inputs to this class to ease unit-testing.
     *
     * @param context     context to use for retrieving the alarm manager.
     * @param handler     handler instance to post alarm timeouts to.
     * @param uwbInjector  reference to UwbInjector.
     * @param sharedStores List of {@link StoreFile} instances pointing to the shared store files.
     *                     This should be retrieved using {@link #createSharedFiles()}}
     *                     method.
     */
    public UwbConfigStore(Context context, Handler handler, UwbInjector uwbInjector,
            List<StoreFile> sharedStores) {

        mAlarmManager = context.getSystemService(AlarmManager.class);
        mEventHandler = handler;
        mUwbInjector = uwbInjector;
        mStoreDataList = new ArrayList<>();

        // Initialize the store files.
        mSharedStores = sharedStores;
        // The user store is initialized to null, this will be set when the user unlocks and
        // CE storage is accessible via |switchUserStoresAndRead|.
        mUserStores = null;
    }

    /**
     * Read the config store and load the in-memory lists from the store data retrieved and sends
     * out the networks changed broadcast.
     *
     * This reads all the network configurations from:
     * 1. Shared UwbConfigStore.bin
     * 2. User UwbConfigStore.bin
     *
     * @return true on success or not needed (fresh install), false otherwise.
     */
    public boolean loadFromStore() {
        // If the user unlock comes in before we load from store, which means the user store have
        // not been setup yet for the current user. Setup the user store before the read so that
        // configurations for the current user will also being loaded.
        if (mDeferredUserUnlockRead) {
            Log.i(TAG, "Handling user unlock before loading from store.");
            List<UwbConfigStore.StoreFile> userStoreFiles =
                    UwbConfigStore.createUserFiles(UserHandle.SYSTEM.getIdentifier());
            if (userStoreFiles == null) {
                Log.wtf(TAG, "Failed to create user store files");
                return false;
            }
            setUserStores(userStoreFiles);
            mDeferredUserUnlockRead = false;
        }
        mPendingStoreRead = true;
        try {
            read();
        } catch (IOException | IllegalStateException e) {
            Log.wtf(TAG, "Reading from new store failed. All saved networks are lost!", e);
            // TODO Need to handle this based on dev build vs prod
        }
        mPendingStoreRead = false;
        return true;
    }

    /**
     * Handles the unlock of foreground user. This maybe needed to read the store file if the user's
     * CE storage is not visible when {@link #handleUserSwitch(int)} is invoked.
     *
     * Need to be called when {@link com.android.server.SystemService#onUserUnlocking} is invoked.
     *
     * @param userId The identifier of the user that unlocked.
     */
    public void handleUserUnlock(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user unlock for " + userId);
        }
        if (userId != mCurrentUserId) {
            Log.e(TAG, "Ignore user unlock for non current user " + userId);
            return;
        }
        if (mPendingStoreRead) {
            Log.w(TAG, "Ignore user unlock until store is read!");
            mDeferredUserUnlockRead = true;
            return;
        }
        if (mPendingUnlockStoreRead) {
            handleUserUnlockOrSwitch(mCurrentUserId);
        }
    }

    /**
     * Helper method to perform the following operations during user switch/unlock:
     * - Remove private networks of the old user.
     * - Load from the new user store file.
     * - Save the store files again to migrate any user specific networks from the shared store
     *   to user store.
     * This method assumes the user store is visible (i.e CE storage is unlocked). So, the caller
     * should ensure that the stores are accessible before invocation.
     *
     * @param userId The identifier of the new foreground user, after the unlock or switch.
     */
    private void handleUserUnlockOrSwitch(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Loading from store after user switch/unlock for " + userId);
        }
        // Switch out the user store file.
        if (loadFromUserStoreAfterUnlockOrSwitch(userId)) {
            saveToStore(true);
            mPendingUnlockStoreRead = false;
        }
    }

    /**
     * Read the user config store and load the in-memory lists from the store data retrieved and
     * sends out the networks changed broadcast.
     * This should be used for all user switches/unlocks to only load networks from the user
     * specific store and avoid reloading the shared networks.
     *
     * This reads all the network configurations from:
     * 1. User UwbConfigStore.bin
     *
     * @param userId The identifier of the foreground user.
     * @return true on success, false otherwise.
     */
    private boolean loadFromUserStoreAfterUnlockOrSwitch(int userId) {
        try {
            List<StoreFile> userStoreFiles = createUserFiles(userId);
            if (userStoreFiles == null) {
                Log.e(TAG, "Failed to create user store files");
                return false;
            }
            switchUserStoresAndRead(userStoreFiles);
        } catch (IOException | IllegalStateException e) {
            Log.wtf(TAG, "Reading from new store failed. All saved private networks are lost!", e);
            return false;
        }
        return true;
    }

    /**
     * Handles the switch to a different foreground user:
     * - Flush the current state to the old user's store file.
     * - Switch the user specific store file.
     * - Reload the networks from the store files (shared & user).
     * - Write the store files to move any user specific private networks from shared store to user
     *   store.
     *
     * Need to be called when {@link com.android.server.SystemService#onUserSwitching} is invoked.
     *
     * @param userId The identifier of the new foreground user, after the switch.
     */
    public void handleUserSwitch(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user switch for " + userId);
        }
        if (userId == mCurrentUserId) {
            Log.w(TAG, "User already in foreground " + userId);
        }
        if (mPendingStoreRead) {
            Log.w(TAG, "User switch before store is read!");
            mCurrentUserId = userId;
            // Reset any state from previous user unlock.
            mDeferredUserUnlockRead = false;
            // Cannot read data from new user's CE store file before they log-in.
            mPendingUnlockStoreRead = true;
        }

        if (mUwbInjector.getUserManager()
                .isUserUnlockingOrUnlocked(UserHandle.of(mCurrentUserId))) {
            saveToStore(true);
        }
        // Remove any private config of the old user before switching the userId.
        clearInternalDataForUser();
        mCurrentUserId = userId;

        if (mUwbInjector.getUserManager()
                .isUserUnlockingOrUnlocked(UserHandle.of(mCurrentUserId))) {
            handleUserUnlockOrSwitch(mCurrentUserId);
        } else {
            // Cannot read data from new user's CE store file before they log-in.
            mPendingUnlockStoreRead = true;
            Log.i(TAG, "Waiting for user unlock to load from store");
        }
    }

    void clearInternalDataForUser() {
        if (mUserStores != null) {
            for (StoreFile userStoreFile : mUserStores) {
                List<StoreData> storeDataList = retrieveStoreDataListForStoreFile(userStoreFile);
                for (StoreData storeData : storeDataList) {
                    storeData.resetData();
                }
            }
        }
    }

    /**
     * Save the current snapshot of the in-memory lists to the config store.
     *
     * @param forceWrite Whether the write needs to be forced or not.
     * @return Whether the write was successful or not, this is applicable only for force writes.
     */
    public boolean saveToStore(boolean forceWrite) {
        if (mPendingStoreRead) {
            Log.e(TAG, "Cannot save to store before store is read!");
            return false;
        }
        try {
            write(forceWrite);
        } catch (IOException | IllegalStateException e) {
            Log.wtf(TAG, "Writing to store failed. Saved networks maybe lost!", e);
            return false;
        }
        return true;
    }

    /**
     * Set the user store files.
     * (Useful for mocking in unit tests).
     * @param userStores List of {@link StoreFile} created using
     * {@link #createUserFiles(int)} }.
     */
    public void setUserStores(@NonNull List<StoreFile> userStores) {
        Preconditions.checkNotNull(userStores);
        mUserStores = userStores;
    }

    /**
     * Register a {@link StoreData} to read/write data from/to a store. A {@link StoreData} is
     * responsible for a block of data in the store file, and provides serialization/deserialization
     * functions for those data.
     *
     * @param storeData The store data to be registered to the config store
     * @return true if registered successfully, false if the store file name is not valid.
     */
    public boolean registerStoreData(StoreData storeData) {
        if (storeData == null) {
            Log.e(TAG, "Unable to register null store data");
            return false;
        }
        int storeFileId = storeData.getStoreFileId();
        if (STORE_ID_TO_FILE_NAME.get(storeFileId) == null) {
            Log.e(TAG, "Invalid shared store file specified" + storeFileId);
            return false;
        }
        mStoreDataList.add(storeData);
        return true;
    }

    /**
     * Helper method to create a store file instance for either the shared store or user store.
     * Note: The method creates the store directory if not already present. This may be needed for
     * user store files.
     *
     * @param storeDir Base directory under which the store file is to be stored. The store file
     *                 will be at <storeDir>/UwbConfigStore.bin.
     * @param fileId Identifier for the file. See {@link StoreFileId}.
     * @return new instance of the store file or null if the directory cannot be created.
     */
    @Nullable
    private static StoreFile createFile(@NonNull File storeDir,
            @StoreFileId int fileId) {
        if (!storeDir.exists()) {
            if (!storeDir.mkdir()) {
                Log.w(TAG, "Could not create store directory " + storeDir);
                return null;
            }
        }
        File file = new File(storeDir, STORE_ID_TO_FILE_NAME.get(fileId));
        return new StoreFile(file, fileId);
    }

    @Nullable
    private static List<StoreFile> createFiles(File storeDir, List<Integer> storeFileIds) {
        List<StoreFile> storeFiles = new ArrayList<>();
        for (int fileId : storeFileIds) {
            StoreFile storeFile =
                    createFile(storeDir, fileId);
            if (storeFile == null) {
                return null;
            }
            storeFiles.add(storeFile);
        }
        return storeFiles;
    }

    /**
     * Create a new instance of the shared store file.
     *
     * @return new instance of the store file or null if the directory cannot be created.
     */
    @NonNull
    public static List<StoreFile> createSharedFiles() {
        return createFiles(
                UwbInjector.getDeviceProtectedDataDir(),
                Arrays.asList(STORE_FILE_SHARED_GENERAL));
    }

    /**
     * Create new instances of the user specific store files.
     * The user store file is inside the user's encrypted data directory.
     *
     * @param userId userId corresponding to the currently logged-in user.
     * @return List of new instances of the store files created or null if the directory cannot be
     * created.
     */
    @Nullable
    public static List<StoreFile> createUserFiles(int userId) {
        return createFiles(
                UwbInjector.getCredentialProtectedDataDirForUser(userId),
                Arrays.asList(STORE_FILE_USER_GENERAL));
    }

    /**
     * Retrieve the list of {@link StoreData} instances registered for the provided
     * {@link StoreFile}.
     */
    private List<StoreData> retrieveStoreDataListForStoreFile(@NonNull StoreFile storeFile) {
        return mStoreDataList
                .stream()
                .filter(s -> s.getStoreFileId() == storeFile.getFileId())
                .collect(Collectors.toList());
    }

    /**
     * Check if any of the provided list of {@link StoreData} instances registered
     * for the provided {@link StoreFile }have indicated that they have new data to serialize.
     */
    private boolean hasNewDataToSerialize(@NonNull StoreFile storeFile) {
        List<StoreData> storeDataList = retrieveStoreDataListForStoreFile(storeFile);
        return storeDataList.stream().anyMatch(StoreData::hasNewDataToSerialize);
    }

    /**
     * API to write the data provided by registered store data to config stores.
     * The method writes the user specific configurations to user specific config store and the
     * shared configurations to shared config store.
     *
     * @param forceSync boolean to force write the config stores now. if false, the writes are
     *                  buffered and written after the configured interval.
     */
    public void write(boolean forceSync) throws  IOException {
        boolean hasAnyNewData = false;
        // Serialize the provided data and send it to the respective stores. The actual write will
        // be performed later depending on the |forceSync| flag .
        for (StoreFile sharedStoreFile : mSharedStores) {
            if (hasNewDataToSerialize(sharedStoreFile)) {
                byte[] sharedDataBytes = serializeData(sharedStoreFile);
                sharedStoreFile.storeRawDataToWrite(sharedDataBytes);
                hasAnyNewData = true;
            }
        }

        if (mUserStores != null) {
            for (StoreFile userStoreFile : mUserStores) {
                if (hasNewDataToSerialize(userStoreFile)) {
                    byte[] userDataBytes = serializeData(userStoreFile);
                    userStoreFile.storeRawDataToWrite(userDataBytes);
                    hasAnyNewData = true;
                }
            }
        }

        if (hasAnyNewData) {
            // Every write provides a new snapshot to be persisted, so |forceSync| flag overrides
            // any pending buffer writes.
            if (forceSync) {
                writeBufferedData();
            } else {
                startBufferedWriteAlarm();
            }
        } else if (forceSync && mBufferedWritePending) {
            // no new data to write, but there is a pending buffered write. So, |forceSync| should
            // flush that out.
            writeBufferedData();
        }
    }

    /**
     * Serialize all the data from all the {@link StoreData} clients registered for the provided
     * {@link StoreFile}.
     *
     * @param storeFile StoreFile that we want to write to.
     * @return byte[] of serialized bytes
     */
    private byte[] serializeData(@NonNull StoreFile storeFile) {
        List<StoreData> storeDataList = retrieveStoreDataListForStoreFile(storeFile);
        UwbConfigProto.UwbConfig.Builder builder = UwbConfigProto.UwbConfig.newBuilder();
        builder.setVersion(CURRENT_CONFIG_STORE_DATA_VERSION);
        for (StoreData storeData : storeDataList) {
            storeData.serializeData(builder);
        }
        return builder.build().toByteArray();
    }

    /**
     * Helper method to start a buffered write alarm if one doesn't already exist.
     */
    private void startBufferedWriteAlarm() {
        if (!mBufferedWritePending) {
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    mUwbInjector.getElapsedSinceBootMillis() + BUFFERED_WRITE_ALARM_INTERVAL_MS,
                    BUFFERED_WRITE_ALARM_TAG, mBufferedWriteListener, mEventHandler);
            mBufferedWritePending = true;
        }
    }

    /**
     * Helper method to stop a buffered write alarm if one exists.
     */
    private void stopBufferedWriteAlarm() {
        if (mBufferedWritePending) {
            mAlarmManager.cancel(mBufferedWriteListener);
            mBufferedWritePending = false;
        }
    }

    /**
     * Helper method to actually perform the writes to the file. This flushes out any write data
     * being buffered in the respective stores and cancels any pending buffer write alarms.
     */
    private void writeBufferedData() throws IOException {
        stopBufferedWriteAlarm();

        long writeStartTime = mUwbInjector.getElapsedSinceBootMillis();
        for (StoreFile sharedStoreFile : mSharedStores) {
            sharedStoreFile.writeBufferedRawData();
        }
        if (mUserStores != null) {
            for (StoreFile userStoreFile : mUserStores) {
                userStoreFile.writeBufferedRawData();
            }
        }
        long writeTime = mUwbInjector.getElapsedSinceBootMillis() - writeStartTime;
        Log.d(TAG, "Writing to stores completed in " + writeTime + " ms.");
    }

    /**
     * Helper method to read from the shared store files.
     */
    private void readFromSharedStoreFiles() throws IOException {
        for (StoreFile sharedStoreFile : mSharedStores) {
            byte[] sharedDataBytes = sharedStoreFile.readRawData();
            deserializeData(sharedDataBytes, sharedStoreFile);
        }
    }

    /**
     * Helper method to read from the user store files.
     */
    private void readFromUserStoreFiles() {
        for (StoreFile userStoreFile : mUserStores) {
            byte[] userDataBytes = userStoreFile.readRawData();
            deserializeData(userDataBytes, userStoreFile);
        }
    }

    /**
     * API to read the store data from the config stores.
     * The method reads the user specific configurations from user specific config store and the
     * shared configurations from the shared config store.
     */
    public void read() throws IOException {
        // Reset both share and user store data.
        for (StoreFile sharedStoreFile : mSharedStores) {
            resetStoreData(sharedStoreFile);
        }
        if (mUserStores != null) {
            for (StoreFile userStoreFile : mUserStores) {
                resetStoreData(userStoreFile);
            }
        }
        long readStartTime = mUwbInjector.getElapsedSinceBootMillis();
        readFromSharedStoreFiles();
        if (mUserStores != null) {
            readFromUserStoreFiles();
        }
        long readTime = mUwbInjector.getElapsedSinceBootMillis() - readStartTime;
        Log.d(TAG, "Reading from all stores completed in " + readTime + " ms.");
    }

    /**
     * Handles a user switch. This method changes the user specific store files and reads from the
     * new user's store files.
     *
     * @param userStores List of {@link StoreFile} created using {@link #createUserFiles(int)}.
     */
    public void switchUserStoresAndRead(@NonNull List<StoreFile> userStores)
            throws IOException {
        //TODO Not yet supported.
        Preconditions.checkNotNull(userStores);
        // Reset user store data.
        if (mUserStores != null) {
            for (StoreFile userStoreFile : mUserStores) {
                resetStoreData(userStoreFile);
            }
        }

        // Stop any pending buffered writes, if any.
        stopBufferedWriteAlarm();
        mUserStores = userStores;

        // Now read from the user store files.
        long readStartTime = mUwbInjector.getElapsedSinceBootMillis();
        readFromUserStoreFiles();
        long readTime = mUwbInjector.getElapsedSinceBootMillis() - readStartTime;
        Log.d(TAG, "Reading from user stores completed in " + readTime + " ms.");
    }

    /**
     * Reset data for all {@link StoreData} instances registered for this {@link StoreFile}.
     */
    private void resetStoreData(@NonNull StoreFile storeFile) {
        for (StoreData storeData: retrieveStoreDataListForStoreFile(storeFile)) {
            storeData.resetData();
        }
    }

    // Inform all the provided store data clients that there is nothing in the store for them.
    private void indicateNoDataForStoreDatas(Collection<StoreData> storeDataSet) {
        for (StoreData storeData : storeDataSet) {
            storeData.deserializeData(null);
        }
    }

    /**
     * Deserialize data from a {@link StoreFile} for all {@link StoreData} instances registered.
     *
     * @param dataBytes The data to parse
     * @param storeFile StoreFile that we read from. Will be used to retrieve the list of clients
     *                  who have data to deserialize from this file.
     */
    private void deserializeData(@NonNull byte[] dataBytes, @NonNull StoreFile storeFile) {
        List<StoreData> storeDataList = retrieveStoreDataListForStoreFile(storeFile);
        if (dataBytes == null) {
            indicateNoDataForStoreDatas(storeDataList);
            return;
        }

        Set<StoreData> storeDatasInvoked = new HashSet<>();
        UwbConfigProto.UwbConfig uwbConfig;
        try {
            uwbConfig = UwbConfigProto.UwbConfig.parseFrom(dataBytes);
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Wrong Uwb config proto version");
            return;
        }
        for (StoreData storeData: mStoreDataList) {
            storeData.deserializeData(uwbConfig);
            storeDatasInvoked.add(storeData);
        }
        // Inform all the other registered store data clients that there is nothing in the store
        // for them.
        Set<StoreData> storeDatasNotInvoked = new HashSet<>(storeDataList);
        storeDatasNotInvoked.removeAll(storeDatasInvoked);
        indicateNoDataForStoreDatas(storeDatasNotInvoked);
    }

    /**
     * Dump the local log buffer and other internal state of UwbConfigManager.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of UwbConfigStore ----");
        pw.println("UwbConfigStore - Store File Begin ----");
        Stream.of(mSharedStores, mUserStores)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .forEach((storeFile) -> {
                    pw.print("Name: " + storeFile.mFileName);
                    pw.println(", File Id: " + storeFile.mFileId);
                });
        pw.println("UwbConfigStore - Store Data Begin ----");
        for (StoreData storeData : mStoreDataList) {
            pw.print("StoreData =>");
            pw.print(" ");
            pw.print("Name: " + storeData.getName());
            pw.print(", ");
            pw.print("File Id: " + storeData.getStoreFileId());
            pw.print(", ");
            pw.println("File Name: " + STORE_ID_TO_FILE_NAME.get(storeData.getStoreFileId()));
        }
        pw.println("---- Dump of UwbConfigStore ----");
    }

    /**
     * Class to encapsulate all file writes. This is a wrapper over {@link AtomicFile} to write/read
     * raw data from the persistent file with integrity. This class provides helper methods to
     * read/write the entire file into a byte array.
     * This helps to separate out the processing, parsing, and integrity checking from the actual
     * file writing.
     */
    public static class StoreFile {
        /**
         * The store file to be written to.
         */
        private final AtomicFile mAtomicFile;
        /**
         * This is an intermediate buffer to store the data to be written.
         */
        private byte[] mWriteData;
        /**
         * Store the file name for setting the file permissions/logging purposes.
         */
        private final String mFileName;
        /**
         * {@link StoreFileId} Type of store file.
         */
        @StoreFileId
        private final int mFileId;

        public StoreFile(File file, @StoreFileId int fileId) {
            mAtomicFile = new AtomicFile(file);
            mFileName = file.getAbsolutePath();
            mFileId = fileId;
        }

        public String getName() {
            return mAtomicFile.getBaseFile().getName();
        }

        @StoreFileId
        public int getFileId() {
            return mFileId;
        }

        /**
         * Read the entire raw data from the store file and return in a byte array.
         *
         * @return raw data read from the file or null if the file is not found or the data has
         *  been altered.
         */
        public byte[] readRawData() {
            byte[] bytes;
            try {
                bytes = mAtomicFile.readFully();
            } catch (IOException e) {
                return null;
            }
            return bytes;
        }

        /**
         * Store the provided byte array to be written when {@link #writeBufferedRawData()} method
         * is invoked.
         * This intermediate step is needed to help in buffering file writes.
         *
         * @param data raw data to be written to the file.
         */
        public void storeRawDataToWrite(byte[] data) {
            mWriteData = data;
        }

        /**
         * Write the stored raw data to the store file.
         * After the write to file, the mWriteData member is reset.
         * @throws IOException if an error occurs. The output stream is always closed by the method
         * even when an exception is encountered.
         */
        public void writeBufferedRawData() throws IOException {
            if (mWriteData == null) return; // No data to write for this file.
            // Write the data to the atomic file.
            FileOutputStream out = null;
            try {
                out = mAtomicFile.startWrite();
                out.write(mWriteData);
                mAtomicFile.finishWrite(out);
            } catch (IOException e) {
                if (out != null) {
                    mAtomicFile.failWrite(out);
                }
                throw e;
            }
            // Reset the pending write data after write.
            mWriteData = null;
        }
    }

    /**
     * Interface to be implemented by a module that contained data in the config store file.
     *
     * The module will be responsible for serializing/deserializing their own data.
     * Whenever {@link UwbConfigStore#read()} is invoked, all registered StoreData instances will
     * be notified that a read was performed via {@link StoreData#deserializeData
     * UwbConfig)} regardless of whether there is any data for them or not in the
     * store file.
     *
     */
    public interface StoreData {
        /**
         * @param builder UwbConfigProto builder
         * @throws NullPointerException failure serializing data
         */
        void serializeData(UwbConfigProto.UwbConfig.Builder builder) throws NullPointerException;

        /**
         * @param uwbConfig config read from file to be deserialized
         */
        void deserializeData(UwbConfigProto.UwbConfig uwbConfig);

        /**
         * Reset configuration data.
         */
        void resetData();

        /**
         * Check if there is any new data to persist from the last write.
         *
         * @return true if the module has new data to persist, false otherwise.
         */
        boolean hasNewDataToSerialize();

        /**
         * Return the name of this store data.  The data will be enclosed under this tag in
         * the XML block.
         *
         * @return The name of the store data
         */
        String getName();

        /**
         * File Id where this data needs to be written to.
         * This should be one of {@link #STORE_FILE_SHARED_GENERAL},
         * {@link #STORE_FILE_USER_GENERAL}
         *
         * Note: For most uses, the shared or user general store is sufficient. Creating and
         * managing store files are expensive. Only use specific store files if you have a large
         * amount of data which may not need to be persisted frequently (or at least not as
         * frequently as the general store).
         * @return Id of the file where this data needs to be persisted.
         */
        @StoreFileId int getStoreFileId();
    }
}
