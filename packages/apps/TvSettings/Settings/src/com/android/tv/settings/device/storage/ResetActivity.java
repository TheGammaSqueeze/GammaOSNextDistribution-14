/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.device.storage;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.oemlock.OemLockManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import com.android.tv.settings.R;
import com.android.tv.settings.util.GuidedActionsAlignUtil;

import java.util.List;

public class ResetActivity extends FragmentActivity {

    private static final String TAG = "ResetActivity";

    /**
     * Support for shutdown-after-reset. If our launch intent has a true value for
     * the boolean extra under the following key, then include it in the intent we
     * use to trigger a factory reset. This will cause us to shut down instead of
     * restart after the reset.
     */
    private static final String SHUTDOWN_INTENT_EXTRA = "shutdown";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepSupportFragment
                    .addAsRoot(this, ResetFragment.newInstance(), android.R.id.content);
        }
    }

    public static class ResetFragment extends GuidedStepSupportFragment {

        public static ResetFragment newInstance() {

            Bundle args = new Bundle();

            ResetFragment fragment = new ResetFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.device_reset),
                    getString(R.string.factory_reset_description),
                    null,
                    getContext().getDrawable(R.drawable.ic_settings_backup_restore_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_CANCEL)
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_OK)
                    .title(getString(R.string.device_reset))
                    .multilineDescription(true)
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == GuidedAction.ACTION_ID_OK) {
                add(getFragmentManager(), ResetConfirmFragment.newInstance());
            } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
                getActivity().finish();
            } else {
                Log.wtf(TAG, "Unknown action clicked");
            }
        }

        @Override
        public GuidanceStylist onCreateGuidanceStylist() {
            return GuidedActionsAlignUtil.createGuidanceStylist();
        }
    }

    public static class ResetConfirmFragment extends GuidedStepSupportFragment {

        public static ResetConfirmFragment newInstance() {

            Bundle args = new Bundle();

            ResetConfirmFragment fragment = new ResetConfirmFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.device_reset),
                    getString(R.string.confirm_factory_reset_description),
                    null,
                    getContext().getDrawable(R.drawable.ic_settings_backup_restore_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_CANCEL)
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_OK)
                    .title(getString(R.string.confirm_factory_reset_device))
                    .build());
        }

        @Override
        public GuidanceStylist onCreateGuidanceStylist() {
            return GuidedActionsAlignUtil.createGuidanceStylist();
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == GuidedAction.ACTION_ID_OK) {
                if (ActivityManager.isUserAMonkey()) {
                    Log.v(TAG, "Monkey tried to erase the device. Bad monkey, bad!");
                    getActivity().finish();
                } else {
                    performFactoryReset();
                }
            } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
                getActivity().finish();
            } else {
                Log.wtf(TAG, "Unknown action clicked");
            }
        }

        private void performFactoryReset() {
            final PersistentDataBlockManager pdbManager = (PersistentDataBlockManager)
                    getContext().getSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE);

            // Disable actions in the fragment as the wipe of the persistent data block
            // and ACTION_FACTORY_RESET broadcast can take some time on some devices.
            int position = 0;
            for (GuidedAction action: getActions()) {
                action.setFocusable(false);
                action.setEnabled(false);
                notifyActionChanged(position);
                ++position;
            }

            if (shouldWipePersistentDataBlock(pdbManager)) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        pdbManager.wipe();
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        doMainClear();
                    }
                }.execute();
            } else {
                doMainClear();
            }
        }

        private boolean shouldWipePersistentDataBlock(PersistentDataBlockManager pdbManager) {
            if (pdbManager == null) {
                return false;
            }
            // If OEM unlock is allowed, the persistent data block will be wiped during FR.
            // If disabled, it will be wiped here instead.
            if (((OemLockManager) getActivity().getSystemService(Context.OEM_LOCK_SERVICE))
                    .isOemUnlockAllowed()) {
                return false;
            }
            return true;
        }

        private void doMainClear() {
            if (getActivity() == null) {
                return;
            }
            Intent resetIntent = new Intent(Intent.ACTION_FACTORY_RESET);
            resetIntent.setPackage("android");
            resetIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            resetIntent.putExtra(Intent.EXTRA_REASON, "ResetConfirmFragment");
            if (getActivity().getIntent().getBooleanExtra(SHUTDOWN_INTENT_EXTRA, false)) {
                resetIntent.putExtra(SHUTDOWN_INTENT_EXTRA, true);
            }
            getActivity().sendBroadcastAsUser(resetIntent, UserHandle.SYSTEM);
        }
    }
}
