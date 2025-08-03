/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.system;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.service.oemlock.OemLockManager;
import android.service.persistentdata.PersistentDataBlockManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.ui.toolbar.MenuItem;

import java.util.Collections;
import java.util.List;

/**
 * Presents the user with a final warning before issuing the request to reset the head unit to its
 * default "factory" state.
 */
public class FactoryResetConfirmFragment extends SettingsFragment {

    private MenuItem mClearConfirmButton;
    @VisibleForTesting
    AsyncTask<Void, Void, Void> mPersistentDataWipeTask;

    private MenuItem.OnClickListener mFinalClickListener = i -> {
        if (ActivityManager.isUserAMonkey()) {
            return;
        }

        PersistentDataBlockManager pdbManager = requireContext().getSystemService(
                PersistentDataBlockManager.class);
        OemLockManager oemLockManager = requireContext().getSystemService(OemLockManager.class);
        if (pdbManager != null && !oemLockManager.isOemUnlockAllowed()
                && isDeviceProvisioned()) {
            // If OEM unlock is allowed, the persistent data block will be wiped during the factory
            // reset process. If disabled, it will be wiped here, unless the device is still being
            // provisioned, in which case the persistent data block will be preserved.
            mPersistentDataWipeTask = new WipePersistentDataBlockAsyncTask(pdbManager);
            mPersistentDataWipeTask.execute();
        } else {
            resetEverything();
        }
    };

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.factory_reset_confirm_fragment;
    }

    @Override
    protected List<MenuItem> getToolbarMenuItems() {
        return Collections.singletonList(mClearConfirmButton);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mClearConfirmButton = new MenuItem.Builder(getContext())
                .setTitle(R.string.factory_reset_confirm_button_text)
                .setOnClickListener(mFinalClickListener)
                .build();
    }

    @VisibleForTesting
    ProgressDialog getProgressDialog() {
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(requireContext().getString(R.string.factory_reset_progress_title));
        progressDialog.setMessage(requireContext().getString(R.string.factory_reset_progress_text));
        return progressDialog;
    }

    private boolean isDeviceProvisioned() {
        return Settings.Global.getInt(requireContext().getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    private void resetEverything() {
        Intent intent = new Intent(Intent.ACTION_FACTORY_RESET);
        intent.setPackage("android");
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_REASON, "MasterClearConfirm");
        intent.putExtra(Intent.EXTRA_WIPE_ESIMS, shouldResetEsim());
        requireActivity().sendBroadcast(intent);
    }

    private boolean shouldResetEsim() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                requireContext());
        return sharedPreferences.getBoolean(
                requireContext().getString(R.string.pk_factory_reset_reset_esim), false);
    }

    private class WipePersistentDataBlockAsyncTask extends AsyncTask<Void, Void, Void> {
        private final PersistentDataBlockManager mPdbManager;
        private ProgressDialog mProgressDialog;

        WipePersistentDataBlockAsyncTask(PersistentDataBlockManager pdbManager) {
            mPdbManager = pdbManager;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mPdbManager.wipe();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mProgressDialog.hide();
            if (getActivity() != null) {
                resetEverything();
            }
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog = getProgressDialog();
            mProgressDialog.show();
        }
    }
}
