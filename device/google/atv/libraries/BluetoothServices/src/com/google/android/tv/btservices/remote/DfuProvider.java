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

package com.google.android.tv.btservices.remote;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.tv.btservices.R;
import java.io.File;
import java.io.FileInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * For determining the current DFU binary.
 */
public abstract class DfuProvider {

    private static final String TAG = "Atv.RemoteDfuPrvdr";

    public interface Listener {
        void onDfuFileAdd();
    }

    private class DfuFileObserver extends FileObserver {

        public DfuFileObserver(String path) {
            super(path, FileObserver.CLOSE_WRITE | FileObserver.DELETE);
        }

        @Override
        public void onEvent(int event, String path) {
            if (mContext != null) {
                mHandler.post(DfuProvider.this::checkExternalStorage);
            }
        }
    }

    private class CheckOnDiskDfuFileTask extends AsyncTask<File[], Integer, File[]> {
        @Override
        protected File[] doInBackground(File[]... params) {
            if (params == null || params.length == 0 || params[0] == null) {
                return new File[]{};
            }
            return Arrays.stream(params[0])
                .filter((file) -> {
                      return file.isFile() && file.canRead() &&
                          isDfuFileName(file.getName()) &&
                          (bypassMd5() || MD5s.contains(md5(file.getAbsolutePath())));
                          })
                    .collect(Collectors.toList())
                    .toArray(new File[]{});
        }

        @Override
        protected void onPostExecute(File[] result) {
            boolean changed = false;
            TreeSet<DfuBinary> newDfus = new TreeSet<>();
            newDfus.addAll(getPackagedBinaries());
            for (File file: result) {
                try {
                    FileInputStream fin = new FileInputStream(file.getAbsolutePath());
                    // New, pushed binaries have priority over the system image binaries, so we set
                    // 'override' to true. Note that this is for QA testing and validation only.
                    DfuBinary dfu = mFactory.build(fin, true /* override */);
                    newDfus.add(dfu);
                    fin.close();
                    Log.i(TAG, "Found dfu with version: " + dfu.getVersion());
                } catch (Exception e) {
                    Log.e(TAG, "CheckOnDiskDfuFileTask: exception " + e);
                }
            }
            for (DfuBinary bin : newDfus) {
                if (!mDfus.contains(bin)) {
                    changed = true;
                    break;
                }
            }
            for (DfuBinary bin : mDfus) {
                if (!newDfus.contains(bin)) {
                    changed = true;
                    break;
                }
            }
            mDfus.clear();
            mDfus.addAll(newDfus);
            if (changed) {
                mListener.onDfuFileAdd();
            }
        }
    }

    private final TreeSet<DfuBinary> mDfus = new TreeSet<>();
    private final DfuBinary.Factory mFactory;
    private final Handler mHandler = new Handler();
    private final Set<String> MD5s;
    private final Set<Version> mManualReconnectionVersions;
    private final Context mContext;
    private MessageDigest mDigest;
    private FileObserver mObserver;
    private Listener mListener;

    // This method provides the DFU binaries that are packaged with the APK.
    protected abstract List<DfuBinary> getPackagedBinaries();

    /**
     * Returns the versions from which an upgrade will cause the connection information stored on
     * the remote control being erased. After an upgrade from one of these versions the connection
     * to the remote control would need to be forgotten on the host side and the user needs to
     * perform pairing again.
     *
     * @return A set containing all versions for which the above behavior is to be expected.
     */
    public Set<Version> getManualReconnectionVersions() {
        return mManualReconnectionVersions;
    }

    private static Version convertStrToVersion(String str) {
        String[] parts = str.split(" ");
        int major = Integer.parseInt(parts[0], 16);
        int minor = Integer.parseInt(parts[1], 16);
        byte vid = (byte) (Integer.parseInt(parts[2], 16) & 0xff);
        byte pid = (byte) (Integer.parseInt(parts[3], 16) & 0xff);
        return new Version(major, minor, vid, pid);
    }

    public DfuProvider(Context context, Listener listener, DfuBinary.Factory factory) {
        mContext = context;
        mListener = listener;
        mFactory = factory;
        mDfus.addAll(getPackagedBinaries());
        MD5s = Collections.unmodifiableSet(new HashSet<>(
                Arrays.asList(mContext.getResources().getStringArray(R.array.dfu_binary_md5s))));

        String[] versionStrs =
                mContext.getResources().getStringArray(R.array.manual_reconnection_remote_versions);
        mManualReconnectionVersions =
                  Collections.unmodifiableSet(Arrays.stream(versionStrs)
                        .map(DfuProvider::convertStrToVersion)
                        .collect(Collectors.<Version>toSet()));

        try {
            mDigest = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            Log.e(TAG, "error in opening md5 digest: " + e);
        }
        checkExternalStorage();
        File extDir = Environment.getExternalStorageDirectory();
        mObserver = new DfuFileObserver(extDir.getAbsolutePath());
        mObserver.startWatching();
    }

    public void destroy() {
        mObserver.stopWatching();
        mObserver = null;
    }

    private static boolean bypassMd5() {
        return !TextUtils.isEmpty(SystemProperties.get("btservices.dfu_bypass_md5", ""));
    }

    private static boolean bypassVendorIdCheck() {
        return !TextUtils.isEmpty(SystemProperties.get("btservices.dfu_bypass_vendor_id", ""));
    }

    private static boolean bypassProductIdCheck() {
        return !TextUtils.isEmpty(SystemProperties.get("btservices.dfu_bypass_product_id", ""));
    }

    public boolean bypassVersionCheck() {
        return bypassMd5();
    }

    private String md5(String absPath) {
        if (mDigest == null) {
            return null;
        }

        File file = new File(absPath);
        if (!file.isFile()) {
            return null;
        }

        FileInputStream fin;
        try {
            fin = new FileInputStream(absPath);
        } catch (Exception e) {
            Log.e(TAG, "failed to open file: " + absPath);
            return null;
        }
        mDigest.reset();

        DigestInputStream dis = new DigestInputStream(fin, mDigest);
        try {
            while (dis.available() > 0) {
                dis.read();
            }
        } catch(Exception e) {
            Log.e(TAG, "failed to read file: " + absPath);
            return null;
        }
        try {
            fin.close();
        } catch (Exception e) {
            Log.e(TAG, "failed to close file " + absPath);
            return null;
        }

        byte[] digest = mDigest.digest();
        StringBuilder sb = new StringBuilder();
        for(byte b: digest){
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString().toLowerCase();
    }

    // TODO: Should be replaced with vendor implementation.
    private static boolean isDfuFileName(String fname) {
        if (fname == null)
            return false;
        fname = fname.toLowerCase();
        return fname.endsWith(".bin") && fname.contains("ota");
    }

    private void checkExternalStorage() {
        if (mContext == null) {
            return;
        }
        File extDir = Environment.getExternalStorageDirectory();
        if (!extDir.isDirectory()) {
            return;
        }
        new CheckOnDiskDfuFileTask().execute(extDir.listFiles());
    }

    /**
     * Given the device name and the firmware version of the current remote, this method determines
     * the best possible DFU for this remote or null if there are no suitable DFU binaries.
     *
     * @param deviceName The name of the device.
     * @param version The current version of the device.
     * @return The best matching DfuBinary or null if there is no suitable one available.
     */
    @Nullable
    public DfuBinary getDfu(String deviceName, Version version) {
        DfuBinary best = null;
        for (DfuBinary bin : mDfus) {
            Version binVersion = bin.getVersion();
            if (!bypassVendorIdCheck() && binVersion.vid() != version.vid()) {
                continue;
            }

            if (!bypassProductIdCheck() && binVersion.pid() != version.pid()) {
                continue;
            }

            if (binVersion.compareTo(version) <= 0) {
                continue;
            }

            if (best == null || (best.getVersion().compareTo(binVersion) < 0)) {
                best = bin;
            }
        }
        return best;
    }
}
