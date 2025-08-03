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
import android.app.AlertDialog;
import android.app.backup.BackupManager;
import android.app.backup.RestoreObserver;
import android.app.backup.RestoreSession;
import android.app.backup.RestoreSet;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class BackupAndRestoreFragment extends Fragment {

    private static final String TAG = BackupAndRestoreFragment.class.getSimpleName();
    private static final long CURRENT_SET_TOKEN = 1;

    private final int mUserId = UserHandle.myUserId();

    private BackupManager mBackupManager;

    private Button mBackupButton;
    private Button mRestoreButton;
    private Button mShowTransportButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBackupManager = new BackupManager(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.backup_restore_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mShowTransportButton = view.findViewById(R.id.show_transport);
        mBackupButton = view.findViewById(R.id.backup);
        mRestoreButton = view.findViewById(R.id.restore);

        mShowTransportButton.setOnClickListener((v) -> showTransport());
        mBackupButton.setOnClickListener((v) -> backup());
        mRestoreButton.setOnClickListener((v) -> restore());
    }

    private void showTransport() {
        boolean isEnabled = mBackupManager.isBackupEnabled();
        Log.v(TAG, "backup is enabled: " + isEnabled);
        if (!isEnabled) {
            showMessage("Backup is not enabled yet.\nEnable backup first.");
            return;
        }
        String[] allTransports = mBackupManager.listAllTransports();
        Log.v(TAG, "All transports: " + Arrays.toString(allTransports));
        String currentTransport = mBackupManager.getCurrentTransport();
        Log.v(TAG, "Current Transport:" + currentTransport);

        StringBuilder sb = new StringBuilder().append("All transports: ");
        Arrays.stream(allTransports).forEach(t -> sb.append('\n').append(t));
        sb.append("\nCurrent Transport:\n").append(currentTransport);
        showMessage(sb.toString());
    }

    private void backup() {
        boolean isEnabled = mBackupManager.isBackupEnabled();
        Log.v(TAG, "backup is enabled: " + isEnabled);
        if (!isEnabled) {
            showMessage("Backup is not enabled yet.\nEnable backup first.");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            backupNow();
            requireActivity().runOnUiThread(() ->
                    showMessage("backup is queued, waiting for it to complete."));
        });
    }

    private void backupNow() {
        PackageManager packageManager = getActivity().getPackageManager();
        List<PackageInfo> installedPackages = null;
        try {
            installedPackages = packageManager.getInstalledPackagesAsUser(/* flags= */0, mUserId);
            Log.v(TAG, "installed packages: " + installedPackages);
        } catch (Exception e) {
            Log.e(TAG, "exception in backupNow()", e);
            return;
        }

        if (installedPackages != null) {
            String[] packages = installedPackages.stream().map(p -> p.packageName)
                    .toArray(String[]::new);

            List<String> filteredPackages = new ArrayList<>();

            for (String p : packages) {
                try {
                    boolean eligible = mBackupManager.isAppEligibleForBackup(p);
                    Log.v(TAG, "eligible: " + eligible + " package name: " + p);
                    if (eligible) {
                        filteredPackages.add(p);
                        Log.v(TAG, "adding package to filtered packages");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "isAppEligibleForBackup() cannot connect: ", e);
                }
            }
            // Currently, the observer is not waiting enough time for the backup to finish
            // Will implement it later for full functionality
            int res = mBackupManager.requestBackup(packages, /* observer= */ null);
            Log.v(TAG, "request backup returned code: " + res);
            if (res == 0) {
                Log.v(TAG, "request backup res successful!");
            }
        }
    }

    private void restore() {
        boolean isEnabled = mBackupManager.isBackupEnabled();
        Log.v(TAG, "backup is enabled: " + isEnabled);
        if (!isEnabled) {
            showMessage("Backup is not enabled yet.\nClick enable backup first.");
            return;
        }

        // TODO: use Handler / HandlerThread instead
        Executors.newSingleThreadExecutor().execute(() -> {
            restoreNow();
            requireActivity().runOnUiThread(() -> showMessage("restore is complete"));
        });
    }

    private void restoreNow() {
        RestoreObserverLocal observer = new RestoreObserverLocal();
        RestoreSession session = null;
        try {
            session = mBackupManager.beginRestoreSession();
            Log.v(TAG, "current restore session: " + session);
            if (session != null) {
                int err = session.getAvailableRestoreSets(observer);
                if (err == 0) {
                    observer.waitForCompletion();
                    int restoreResult = session.restoreAll(CURRENT_SET_TOKEN, observer);
                    Log.v(TAG, "restore all returned code: " + restoreResult);
                    if (restoreResult == 0) {
                        Log.i(TAG, "restore successful!!");
                    }
                } else {
                    Log.v(TAG, "Unable to contact server for restore" + err);
                }
            } else {
                Log.i(TAG, "No restore session");
            }
        } catch (Exception e) {
            Log.e(TAG, "exception in beginRestoreSession(): ", e);
        } finally {
            if (session != null) {
                try {
                    session.endRestoreSession();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to end the restore session!", e);
                }
            }
        }
    }

    private static final class RestoreObserverLocal extends RestoreObserver {
        final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void restoreSetsAvailable(RestoreSet[] result) {
            mLatch.countDown();
        }

        public void waitForCompletion() {
            boolean received = false;
            try {
                received = mLatch.await(120, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Log.e(TAG, "Current thread is stopped during restore: ", ex);
                Thread.currentThread().interrupt();
            }
            if (!received) {
                Log.w(TAG, "Restore operation is timed out after 120 seconds.");
            }
        }
    }

    private void showMessage(String pattern, Object... args) {
        String message = String.format(pattern, args);
        Log.v(TAG, "showMessage(): " + message);
        new AlertDialog.Builder(getContext()).setMessage(message).show();
    }
}

