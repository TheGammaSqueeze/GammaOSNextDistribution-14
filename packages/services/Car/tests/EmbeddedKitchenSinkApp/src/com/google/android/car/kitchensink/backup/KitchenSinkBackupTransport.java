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

package com.google.android.car.kitchensink.backup;
import android.annotation.Nullable;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupTransport;
import android.app.backup.RestoreDescription;
import android.app.backup.RestoreSet;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.Log;

import libcore.io.IoUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class KitchenSinkBackupTransport extends BackupTransport {
    private static final String TRANSPORT_DIR_NAME =
            "com.google.android.car.kitchensink.backup.KitchenSinkBackupTransport";

    private static final String TRANSPORT_DESTINATION_STRING =
            "Backing up to debug-only private cache";

    private static final String TRANSPORT_DATA_MANAGEMENT_LABEL = "";
    private static final String FULL_DATA_DIR = "_full";
    private static final String INCREMENTAL_DIR = "_delta";
    private static final String DEFAULT_DEVICE_NAME_FOR_RESTORE_SET = "flash";
    // The currently-active restore set always has the same (nonzero) token, which is 1 in this case
    private static final long CURRENT_SET_TOKEN = 1;
    private static final String TAG = KitchenSinkBackupTransport.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final long FULL_BACKUP_SIZE_QUOTA = 25 * 1024 * 1024;
    private static final long KEY_VALUE_BACKUP_SIZE_QUOTA = 5 * 1024 * 1024;
    // set of other possible backups currently available over this transport.
    static final long[] POSSIBLE_SETS = { 2, 3, 4, 5, 6, 7, 8, 9 };
    private static final int FULL_RESTORE_BUFFER_BYTE_SIZE = 2 * 1024;
    private static final int FULL_BACKUP_BUFFER_BYTE_SIZE = 4096;

    private final Context mContext;
    private File mDataDir;
    private File mCurrentSetDir;
    private File mCurrentSetFullDir;
    private File mCurrentSetIncrementalDir;

    // Kay/Value restore
    private PackageInfo[] mRestorePackages;
    private int mRestorePackageIndex;  // Index into mRestorePackages
    private int mRestoreType;
    private File mRestoreSetDir;
    private File mRestoreSetIncrementalDir;
    private File mRestoreSetFullDir;

    private byte[] mFullBackupBuffer;
    private long mFullBackupSize;
    private ParcelFileDescriptor mSocket;
    private String mFullTargetPackage;
    private FileInputStream mSocketInputStream;
    private BufferedOutputStream mFullBackupOutputStream;

    private byte[] mFullRestoreBuffer;
    private FileInputStream mCurFullRestoreStream;

    private void makeDataDirs() {
        if (DEBUG) Log.v(TAG, "Making new data directories.");
        mDataDir = mContext.getFilesDir();
        mCurrentSetDir = new File(mDataDir, Long.toString(CURRENT_SET_TOKEN));
        mCurrentSetFullDir = new File(mCurrentSetDir, FULL_DATA_DIR);
        mCurrentSetIncrementalDir = new File(mCurrentSetDir, INCREMENTAL_DIR);

        mCurrentSetDir.mkdirs();
        mCurrentSetFullDir.mkdir();
        mCurrentSetIncrementalDir.mkdir();
    }
    public KitchenSinkBackupTransport(Context context) {
        mContext = context;
        makeDataDirs();
    }

    @Override
    public String name() {
        return new ComponentName(mContext, this.getClass()).flattenToShortString();
    }

    @Override
    public String transportDirName() {
        return TRANSPORT_DIR_NAME;
    }

    @Override
    public String currentDestinationString() {
        return TRANSPORT_DESTINATION_STRING;
    }

    @Override
    public Intent configurationIntent() {
        // The KitchenSink transport is not user-configurable
        return null;
    }

    public Intent dataManagementIntent() {
        // The KitchenSink transport does not present a data-management UI
        return null;
    }
    @Override
    public CharSequence dataManagementIntentLabel() {
        return TRANSPORT_DATA_MANAGEMENT_LABEL;
    }

    @Override
    public long requestBackupTime() {
        if (DEBUG) Log.d(TAG, "request backup time");
        // any time is a good time for local backup
        return 0;
    }

    @Override
    public int initializeDevice() {
        if (DEBUG) {
            Log.d(TAG, "initializing server side storage for this device; wiping all data");
        }
        // Deletes all data from current storage set
        deleteContents(mCurrentSetDir);
        makeDataDirs();
        return TRANSPORT_OK;
    }

    // Deletes the contents recursively
    private void deleteContents(File dirname) {
        if (DEBUG) Log.d(TAG, "Deleting data from: " + dirname);
        File[] contents = dirname.listFiles();
        if (contents == null) return;
        for (File f : contents) {
            if (f.isDirectory()) {
                    // delete the directory's contents then fall through
                    // and delete the directory itself.
                deleteContents(f);
            }
                // deletes the directory itself after deleting everything in it
            f.delete();
        }

    }

    // Encapsulation of a single k/v element change
    private static final class KVOperation {
        // Element filename after base 64 encoding as the key, for efficiency
        final String mKey;
        // An allocated byte array where data is placed when read from the stream
        // null when this is a deletion operation
        final @Nullable byte[] mValue;

        KVOperation(String k, @Nullable byte[] v) {
            mKey = k;
            mValue = v;
        }
    }

    @Override
    public int performBackup(PackageInfo packageInfo, ParcelFileDescriptor data) {
        return performBackup(packageInfo, data, /* flags= */ 0);
    }

    @Override
    public int performBackup(PackageInfo packageInfo, ParcelFileDescriptor data, int flags) {
        Log.i(TAG, "perform backup is called for: " + packageInfo.packageName);
        try {
            return performBackupInternal(packageInfo, data, flags);
        } finally {
            // close the output stream regardless of whether an exception is thrown or caught.
            IoUtils.closeQuietly(data);
        }
    }

    private int performBackupInternal(PackageInfo packageInfo, ParcelFileDescriptor data,
            int flags) {
        Log.i(TAG, "perform backup internal is called for: " + packageInfo.packageName);
        if ((flags & FLAG_DATA_NOT_CHANGED) != 0) {
            // For unchanged data we do nothing and tell the caller everything was OK
            Log.i(TAG, "Data is not changed, no backup needed for " + packageInfo.packageName);
            return TRANSPORT_OK;
        }
        boolean isIncremental = (flags & FLAG_INCREMENTAL) != 0;
        boolean isNonIncremental = (flags & FLAG_NON_INCREMENTAL) != 0;

        if (isIncremental) {
            Log.i(TAG, "Performing incremental backup for " + packageInfo.packageName);
        } else if (isNonIncremental) {
            Log.i(TAG, "Performing non-incremental backup for " + packageInfo.packageName);
        } else {
            Log.i(TAG, "Performing backup for " + packageInfo.packageName);
        }

        if (DEBUG) {
            try {
                // get detailed information about the file, access system API
                StructStat ss = Os.fstat(data.getFileDescriptor());
                Log.v(TAG, "performBackup() pkg=" + packageInfo.packageName
                        + " size=" + ss.st_size + " flags=" + flags);
            } catch (ErrnoException e) {
                Log.w(TAG, " Unable to stat input file in performBackup() " + e);
            }
        }

        File packageDir = new File(mCurrentSetIncrementalDir, packageInfo.packageName);
        boolean hasDataForPackage = !packageDir.mkdirs();

        if (isNonIncremental && hasDataForPackage) {
            Log.w(TAG, "Requested non-incremental, deleting existing data.");
            clearBackupData(packageInfo);
            packageDir.mkdirs();
        }

        // go through the entire input data stream to make a list of all the updates to apply later
        ArrayList<KVOperation> changeOps;
        try {
            changeOps = parseBackupStream(data);
        } catch (IOException e) {
            // if something goes wrong, abort the operation and return error.
            Log.v(TAG, "Exception reading backup input", e);
            return TRANSPORT_ERROR;
        }

        // calculate the sum of the current in-datastore size per key to detect quota overrun
        ArrayMap<String, Integer> datastore = new ArrayMap<>();
        int totalSize = parseKeySizes(packageDir, datastore);
        Log.i(TAG, "Total size of the current data:" + totalSize);
        // find out the datastore size that will result from applying the
        // sequence of delta operations
        if (DEBUG) {
            int numOps = changeOps.size();
            if (numOps > 0) {
                Log.v(TAG, "Calculating delta size impact for " + numOps + "updates.");
            } else {
                Log.v(TAG, "No operations in backup stream, so no size change");
            }
        }

        int updatedSize = totalSize;
        for (KVOperation op : changeOps) {
            // Deduct the size of the key we're about to replace, if any
            final Integer curSize = datastore.get(op.mKey);
            if (curSize != null) {
                updatedSize -= curSize.intValue();
                if (DEBUG && op.mValue == null) {
                    Log.d(TAG, "delete " + op.mKey + ", updated total " + updatedSize);
                }
            }

            // And add back the size of the value we're about to store, if any
            if (op.mValue != null) {
                updatedSize += op.mValue.length;
                if (DEBUG) {
                    Log.d(TAG, ((curSize == null) ? "  new " : "  replace ")
                            +  op.mKey + ", updated total " + updatedSize);
                }
            }
        }

        // If our final size is over quota, report the failure
        if (updatedSize > KEY_VALUE_BACKUP_SIZE_QUOTA) {
            Log.w(TAG, "New datastore size " + updatedSize
                    + " exceeds quota " + KEY_VALUE_BACKUP_SIZE_QUOTA);
            return TRANSPORT_QUOTA_EXCEEDED;
        }
        // No problem with storage size, so go ahead and apply the delta operations
        // (in the order that the app provided them)
        for (KVOperation op : changeOps) {
            File element = new File(packageDir, op.mKey);

            // this is either a deletion or a rewrite-from-zero, so we can just remove
            // the existing file and proceed in either case.
            Log.v(TAG, "Deleting the existing file: " + element.getPath());
            element.delete();

            // if this wasn't a deletion, put the new data in place
            if (op.mValue != null) {
                try (FileOutputStream out = new FileOutputStream(element)) {
                    out.write(op.mValue, 0, op.mValue.length);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to update key file " + element, e);
                    return TRANSPORT_ERROR;
                }
            }
        }
        Log.i(TAG, "KVBackup is successful.");
        return TRANSPORT_OK;
    }

    // Parses the input with Base64-encode and returns the value with a newly allocated byte[]
    private ArrayList<KVOperation> parseBackupStream(ParcelFileDescriptor data)
            throws IOException {
        ArrayList<KVOperation> changeOps = new ArrayList<>();
        BackupDataInput changeSet = new BackupDataInput(data.getFileDescriptor());
        while (changeSet.readNextHeader()) {
            String key = changeSet.getKey();
            String base64Key = new String(Base64.encode(key.getBytes(), Base64.NO_WRAP));
            int dataSize = changeSet.getDataSize();
            if (DEBUG) {
                Log.d(TAG, "Delta operation key: " + key + "; size: " + dataSize
                        + "; key64: " + base64Key);
            }

            byte[] buf = null;
            if (dataSize >= 0) {
                buf = new byte[dataSize];
                changeSet.readEntityData(buf, 0, dataSize);
            }
            changeOps.add(new KVOperation(base64Key, buf));
        }
        return changeOps;
    }

    // Reads the given datastore directory, building a table of the value size of each
    // keyed element, and returning the summed total.
    private int parseKeySizes(File packageDir, ArrayMap<String, Integer> datastore) {
        int totalSize = 0;
        final String[] elements = packageDir.list();
        if (elements != null) {
            if (DEBUG) {
                Log.d(TAG, "Existing datastore contents: " + packageDir);
            }
            for (String file : elements) {
                File element = new File(packageDir, file);
                String key = file;  // filename
                int size = (int) element.length();
                totalSize += size;
                if (DEBUG) {
                    Log.d(TAG, "  key " + key + "   size " + size);
                }
                datastore.put(key, size);
            }
            if (DEBUG) {
                Log.d(TAG, "TOTAL: " + totalSize);
            }
        } else {
            if (DEBUG) {
                Log.d(TAG, "No existing data for package: " + packageDir);
            }
        }
        return totalSize;
    }

    @Override
    public IBinder getBinder() {
        if (DEBUG) Log.d(TAG, "get binder");
        return super.getBinder();
    }

    @Override
    public int getTransportFlags() {
        if (DEBUG) Log.d(TAG, "get transport flags");
        return super.getTransportFlags();
    }


    @Override
    public int clearBackupData(PackageInfo packageInfo) {
        File packageDir = new File(mCurrentSetIncrementalDir, packageInfo.packageName);
        if (DEBUG) {
            Log.d(TAG, "clear backup data for package: " + packageInfo.packageName
                    + " package directory: " + packageDir);
        }
        final File[] incrementalFiles = packageDir.listFiles();
        // deletes files in incremental file set
        if (incrementalFiles != null) {
            for (File f : incrementalFiles) {
                f.delete();
            }
            packageDir.delete();
        }
        // deletes files in current file set
        packageDir = new File(mCurrentSetFullDir, packageInfo.packageName);
        final File[] currentFiles = packageDir.listFiles();
        if (currentFiles != null) {
            for (File f : currentFiles) {
                f.delete();
            }
            packageDir.delete();
        }
        return TRANSPORT_OK;
    }

    // calls after performBackup(), performFullBackup(), clearBackupData()
    @Override
    public int finishBackup() {
        if (DEBUG) Log.d(TAG, "finish backup for:" + mFullTargetPackage);
        return closeFullBackup();
    }

    private int closeFullBackup() {
        if (mSocket == null) {
            return TRANSPORT_OK;
        }
        try {
            if (mFullBackupOutputStream != null) {
                // forces any buffered output bytes
                // to be written out to the underlying output stream.
                mFullBackupOutputStream.flush();
                mFullBackupOutputStream.close();
            }
            mSocketInputStream = null;
            mFullTargetPackage = null;
            mSocket.close();
        } catch (IOException e) {
            if (DEBUG) {
                Log.w(TAG, "Exception caught in closeFullBackup()", e);
            }
            return TRANSPORT_ERROR;
        } finally {
            mSocket = null;
            mFullBackupOutputStream = null;
        }
        return TRANSPORT_OK;
    }

    // ------------------------------------------------------------------------------------
    // Full backup handling

    @Override
    public long requestFullBackupTime() {
        if (DEBUG) Log.d(TAG, "request full backup time");
        return 0;
    }

    @Override
    public int checkFullBackupSize(long size) {
        if (DEBUG) Log.d(TAG, "check full backup size");
        int result = TRANSPORT_OK;
        // Decline zero-size "backups"
        if (size <= 0) {
            result = TRANSPORT_PACKAGE_REJECTED;
        } else if (size > FULL_BACKUP_SIZE_QUOTA) {
            result = TRANSPORT_QUOTA_EXCEEDED;
        }
        if (result != TRANSPORT_OK) {
            if (DEBUG) {
                Log.d(TAG, "Declining backup of size " + size + " Full backup size quota: "
                        + FULL_BACKUP_SIZE_QUOTA);
            }
        }
        return result;
    }

    @Override
    public int performFullBackup(PackageInfo targetPackage, ParcelFileDescriptor socket) {
        if (DEBUG) Log.d(TAG, "perform full backup for: " + targetPackage);
        if (mSocket != null) {
            Log.e(TAG, "Attempt to initiate full backup while one is in progress");
            return TRANSPORT_ERROR;
        }
        // We know a priori that we run in the system process, so we need to make
        // sure to dup() our own copy of the socket fd. Transports which run in
        // their own processes must not do this.
        try {
            mFullBackupSize = 0;
            mSocket = ParcelFileDescriptor.dup(socket.getFileDescriptor());
            mSocketInputStream = new FileInputStream(mSocket.getFileDescriptor());
        } catch (IOException e) {
            Log.e(TAG, "Unable to process socket for full backup:" + e);
            return TRANSPORT_ERROR;
        }

        mFullTargetPackage = targetPackage.packageName;
        mFullBackupBuffer = new byte[FULL_BACKUP_BUFFER_BYTE_SIZE];

        return TRANSPORT_OK;
    }

    @Override
    public int performFullBackup(PackageInfo targetPackage, ParcelFileDescriptor socket,
            int flags) {
        Log.v(TAG, "perform full backup, flags:" + flags + ", package:" + targetPackage);
        return super.performFullBackup(targetPackage, socket, flags);
    }

    // Reads data from socket file descriptor provided in performFullBackup() call
    @Override
    public int sendBackupData(final int numBytes) {
        if (DEBUG) Log.d(TAG, "send back data");
        if (mSocket == null) {
            Log.w(TAG, "Attempted sendBackupData before performFullBackup");
            return TRANSPORT_ERROR;
        }

        mFullBackupSize += numBytes;
        if (mFullBackupSize > FULL_BACKUP_SIZE_QUOTA) {
            return TRANSPORT_QUOTA_EXCEEDED;
        }

        if (numBytes > mFullBackupBuffer.length) {
            mFullBackupBuffer = new byte[numBytes];
        }
        // creates new full backup output stream at the target location
        if (mFullBackupOutputStream == null) {
            FileOutputStream outputStream;
            try {
                File tarball = new File(mCurrentSetFullDir, mFullTargetPackage);
                outputStream = new FileOutputStream(tarball);
            } catch (FileNotFoundException e) {
                return TRANSPORT_ERROR;
            }
            // later will close when finishBackup() and cancelFullBackup() are called
            mFullBackupOutputStream = new BufferedOutputStream(outputStream);
        }

        int bytesLeft = numBytes;
        while (bytesLeft > 0) {
            try {
                int nRead = mSocketInputStream.read(mFullBackupBuffer, 0, bytesLeft);
                Log.i(TAG, "read " + bytesLeft + " bytes of data");
                if (nRead < 0) {
                    // Something went wrong if we expect data but saw EOD
                    Log.w(TAG, "Unexpected EOD; failing backup");
                    return TRANSPORT_ERROR;
                }
                mFullBackupOutputStream.write(mFullBackupBuffer, 0, nRead);
                bytesLeft -= nRead;
            } catch (IOException e) {
                Log.e(TAG, "Error handling backup data for " + mFullTargetPackage);
                return TRANSPORT_ERROR;
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Stored " + numBytes + " of data");
        }
        return TRANSPORT_OK;
    }

    // Happens before finishBackup(), tear down any ongoing backup state
    @Override
    public void cancelFullBackup() {
        if (DEBUG) {
            Log.d(TAG, "Canceling full backup of " + mFullTargetPackage);
        }
        File archive = new File(mCurrentSetFullDir, mFullTargetPackage);
        closeFullBackup();
        if (archive.exists()) {
            archive.delete();
        }
    }

    // ------------------------------------------------------------------------------------
    // Restore handling

    @Override
    public RestoreSet[] getAvailableRestoreSets() {
        Log.v(TAG, "get available restore sets");
        long[] existing = new long[POSSIBLE_SETS.length + 1];
        // number of existing non-current sets
        int num = 0;
        // see which possible non-current sets exist...
        for (long token : POSSIBLE_SETS) {
            // if the file directory exists for the non-current set
            if ((new File(mDataDir, Long.toString(token))).exists()) {
                existing[num++] = token;
                Log.v(TAG, "number of available restore sets: " + num);
            }
        }
        // always adds the currently-active set at last
        existing[num++] = CURRENT_SET_TOKEN;

        RestoreSet[] available = new RestoreSet[num];
        String deviceName = DEFAULT_DEVICE_NAME_FOR_RESTORE_SET;
        for (int i = 0; i < available.length; i++) {
            available[i] = new RestoreSet("Local disk image", deviceName, existing[i]);
        }
        return available;
    }

    @Override
    public long getCurrentRestoreSet() {
        // The current restore set always has the same token, which is 1
        if (DEBUG) Log.d(TAG, "get current restore set");
        return CURRENT_SET_TOKEN;
    }

    @Override
    public int startRestore(long token, PackageInfo[] packages) {
        if (DEBUG) {
            Log.d(TAG, "start restore for token: " + token + " , num of packages: "
                    + packages.length);
        }
        mRestorePackages = packages;
        mRestorePackageIndex = -1;
        mRestoreSetDir = new File(mDataDir, Long.toString(token));
        mRestoreSetIncrementalDir = new File(mRestoreSetDir, INCREMENTAL_DIR);
        mRestoreSetFullDir = new File(mRestoreSetDir, FULL_DATA_DIR);
        return TRANSPORT_OK;
    }

    // Get the package name of the next application with data in the backup store, plus
    // a description of the structure of the restored type
    @Override
    public RestoreDescription nextRestorePackage() {
        if (DEBUG) {
            Log.d(TAG, "nextRestorePackage() : mRestorePackageIndex=" + mRestorePackageIndex
                    + " length=" + mRestorePackages.length);
        }
        if (mRestorePackages == null) throw new IllegalStateException("startRestore not called");

        boolean found;
        while (++mRestorePackageIndex < mRestorePackages.length) {
            // name of the current restore package
            String name = mRestorePackages[mRestorePackageIndex].packageName;

            // If we have key/value data for this package, deliver that
            // skip packages where we have a data dir but no actual contents
            found = hasRestoreDataForPackage(name);
            if (found) {
                mRestoreType = RestoreDescription.TYPE_KEY_VALUE;
            } else {
                // No key/value data; check for [non-empty] full data
                File maybeFullData = new File(mRestoreSetFullDir, name);
                if (maybeFullData.length() > 0) {
                    if (DEBUG) {
                        Log.d(TAG, "nextRestorePackage(TYPE_FULL_STREAM) @ "
                                + mRestorePackageIndex + " = " + name);
                    }
                    mRestoreType = RestoreDescription.TYPE_FULL_STREAM;
                    mCurFullRestoreStream = null;   // ensure starting from the ground state
                    found = true;
                }
            }

            if (found) {
                return new RestoreDescription(name, mRestoreType);
            }
            // if not found for either type
            if (DEBUG) {
                Log.d(TAG, "... package @ " + mRestorePackageIndex + " = " + name
                        + " has no data; skipping");
            }
        }

        if (DEBUG) Log.d(TAG, "no more packages to restore");
        return RestoreDescription.NO_MORE_PACKAGES;
    }

    // check if this package has key/value backup data
    private boolean hasRestoreDataForPackage(String packageName) {
        String[] contents = (new File(mRestoreSetIncrementalDir, packageName)).list();
        if (contents != null && contents.length > 0) {
            if (DEBUG) {
                Log.d(TAG, "nextRestorePackage(TYPE_KEY_VALUE) @ "
                        + mRestorePackageIndex + " = " + packageName);
            }
            return true;
        }
        return false;
    }

    // get the date for the application returned by nextRestorePackage(), only if key/value is
    // the delivery type
    @Override
    public int getRestoreData(ParcelFileDescriptor outFd) {
        if (DEBUG) Log.d(TAG, "get restore data");
        if (mRestorePackages == null) throw new IllegalStateException("startRestore not called");
        if (mRestorePackageIndex < 0) {
            throw new IllegalStateException("nextRestorePackage not called");
        }
        if (mRestoreType != RestoreDescription.TYPE_KEY_VALUE) {
            throw new IllegalStateException("getRestoreData(fd) for non-key/value dataset, "
                    + "restore type:" + mRestoreType);
        }
        File packageDir = new File(mRestoreSetIncrementalDir,
                mRestorePackages[mRestorePackageIndex].packageName);
        // the restore set is the concatenation of the individual record blobs,
        // each of which is a file in the package's directory.
        List<DecodedFilename> blobs = contentsByKey(packageDir);
        if (blobs == null) {  // nextRestorePackage() ensures the dir exists, so this is an error
            Log.e(TAG, "No keys for package: " + packageDir);
            return TRANSPORT_ERROR;
        }

        // We expect at least some data if the directory exists in the first place
        if (DEBUG) Log.d(TAG, "getRestoreData() found " + blobs.size() + " key files");
        BackupDataOutput out = new BackupDataOutput(outFd.getFileDescriptor());
        try {
            for (DecodedFilename keyEntry : blobs) {
                File f = keyEntry.mFile;
                try (FileInputStream in = new FileInputStream(f)) {
                    int size = (int) f.length();
                    byte[] buf = new byte[size];
                    in.read(buf);
                    if (DEBUG) Log.d(TAG, "... key=" + keyEntry.mKey + " size=" + size);
                    out.writeEntityHeader(keyEntry.mKey, size);
                    out.writeEntityData(buf, size);
                }
            }
            return TRANSPORT_OK;
        } catch (IOException e) {
            Log.e(TAG, "Unable to read backup records", e);
            return TRANSPORT_ERROR;
        }
    }

    private static final class DecodedFilename implements Comparable<DecodedFilename> {
        public File mFile;
        public String mKey;

        DecodedFilename(File f) {
            mFile = f;
            mKey = new String(Base64.decode(f.getName(), Base64.DEFAULT));
        }

        @Override
        public int compareTo(DecodedFilename other) {
            // sorts into ascending lexical order by decoded key
            return mKey.compareTo(other.mKey);
        }
    }

    // Return a list of the files in the given directory, sorted lexically by
    // the Base64-decoded file name, not by the on-disk filename
    private List<DecodedFilename> contentsByKey(File dir) {
        File[] allFiles = dir.listFiles();
        if (allFiles == null || allFiles.length == 0) {
            return Collections.emptyList();
        }

        // Decode the filenames into keys then sort lexically by key
        List<DecodedFilename> contents = new ArrayList<>();
        for (File f : allFiles) {
            contents.add(new DecodedFilename(f));
        }
        Collections.sort(contents);
        return contents;
    }

    @Override
    public void finishRestore() {
        if (DEBUG) Log.d(TAG, "finishRestore()");
        if (mRestoreType == RestoreDescription.TYPE_FULL_STREAM) {
            resetFullRestoreState();
        }
        // set the restore type back to 0
        mRestoreType = 0;
    }

    // Clears full restore stream and full restore buffer back to the ground state
    private void resetFullRestoreState() {
        IoUtils.closeQuietly(mCurFullRestoreStream);
        mCurFullRestoreStream = null;
        mFullRestoreBuffer = null;
    }

    // ------------------------------------------------------------------------------------
    // Full restore handling

    // Writes some data to the socket supplied to this call, and returns the number of bytes
    // written. The system will then read that many bytes and stream them to the
    // application's agent for restore.
    @Override
    public int getNextFullRestoreDataChunk(ParcelFileDescriptor socket) {
        if (DEBUG) Log.d(TAG, "get next full restore data chunk");
        if (mRestoreType != RestoreDescription.TYPE_FULL_STREAM) {
            throw new IllegalStateException("Asked for full restore data for non-stream package"
                    + ", restore type:" + mRestoreType);
        }

        // first chunk?
        if (mCurFullRestoreStream == null) {
            final String name = mRestorePackages[mRestorePackageIndex].packageName;
            if (DEBUG) Log.i(TAG, "Starting full restore of " + name);
            File dataset = new File(mRestoreSetFullDir, name);
            try {
                mCurFullRestoreStream = new FileInputStream(dataset);
            } catch (IOException e) {
                // If we can't open the target package's tarball, we return the single-package
                // error code and let the caller go on to the next package.
                Log.e(TAG, "Unable to read archive for " + name + e);
                return TRANSPORT_PACKAGE_REJECTED;
            }
            mFullRestoreBuffer = new byte[FULL_RESTORE_BUFFER_BYTE_SIZE];
        }

        FileOutputStream stream = new FileOutputStream(socket.getFileDescriptor());

        int nRead;
        try {
            nRead = mCurFullRestoreStream.read(mFullRestoreBuffer);
            if (nRead < 0) {
                // EOF: tell the caller we're done
                nRead = NO_MORE_DATA;
            } else if (nRead == 0) {
                // This shouldn't happen when reading a FileInputStream; we should always
                // get either a positive nonzero byte count or -1.  Log the situation and
                // treat it as EOF.
                Log.w(TAG, "read() of archive file returned 0; treating as EOF");
                nRead = NO_MORE_DATA;
            } else {
                if (DEBUG) {
                    Log.i(TAG, "delivering restore chunk: " + nRead);
                }
                stream.write(mFullRestoreBuffer, 0, nRead);
            }
        } catch (IOException e) {
            Log.e(TAG, "exception:" + e);
            return TRANSPORT_ERROR;  // Hard error accessing the file; shouldn't happen
        } finally {
            IoUtils.closeQuietly(socket);
        }

        return nRead;
    }

    // If the OS encounters an error while processing RestoreDescription.TYPE_FULL_STREAM
    // data for restore, it will invoke this method to tell the transport that it should
    // abandon the data download for the current package.
    @Override
    public int abortFullRestore() {
        Log.v(TAG, "abort full restore");
        if (mRestoreType != RestoreDescription.TYPE_FULL_STREAM) {
            throw new IllegalStateException("abortFullRestore() but not currently restoring"
                    + ", restore type: " + mRestoreType);
        }
        resetFullRestoreState();
        mRestoreType = 0;
        return TRANSPORT_OK;
    }

    @Override
    public long getBackupQuota(String packageName, boolean isFullBackup) {
        if (DEBUG) Log.d(TAG, "get backup quota");
        return isFullBackup ? FULL_BACKUP_SIZE_QUOTA : KEY_VALUE_BACKUP_SIZE_QUOTA;
    }

}
