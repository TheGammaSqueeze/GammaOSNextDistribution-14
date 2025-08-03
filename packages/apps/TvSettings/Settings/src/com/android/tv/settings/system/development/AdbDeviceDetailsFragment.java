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

package com.android.tv.settings.system.development;

import android.content.Context;
import android.debug.IAdbManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import com.android.tv.settings.R;

import java.util.List;

/**
 * Fragment shown when clicking on a paired device in the Wireless
 * Debugging fragment.
 */
public class AdbDeviceDetailsFragment extends GuidedStepSupportFragment {
    private static final String TAG = "AdbDeviceDetailsFragment";

    private static final String ARG_DEVICE_NAME = "device_name";
    private static final String ARG_FINGERPRINT = "fingerprint";

    private IAdbManager mAdbManager;

    /**
     * Function for transferring device name and fingerprint.
     */
    public static void prepareArgs(Bundle args, String deviceName, String fingerprint) {
        args.putString(ARG_DEVICE_NAME, deviceName);
        args.putString(ARG_FINGERPRINT, fingerprint);
    }

    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        String fingerprint = String.format(getString(R.string.adb_device_fingerprint_title_format,
                getArguments().getString(ARG_FINGERPRINT)));
        return new GuidanceStylist.Guidance(
                getString(R.string.adb_device_forget),
                fingerprint,
                getArguments().getString(ARG_DEVICE_NAME),
                getContext().getDrawable(R.drawable.ic_laptop_132dp)
        );
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        mAdbManager = IAdbManager.Stub.asInterface(ServiceManager.getService(Context.ADB_SERVICE));
        final Context context = getContext();
        actions.add(new GuidedAction.Builder(context)
                .title(getString(R.string.settings_ok))
                .clickAction(GuidedAction.ACTION_ID_OK)
                .build());
        actions.add(new GuidedAction.Builder(context)
                .title(getString(R.string.settings_cancel))
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == GuidedAction.ACTION_ID_OK) {
            try {
                Log.d(TAG, "Unpairing device");
                mAdbManager.unpairDevice(getArguments().getString(ARG_FINGERPRINT));
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to forget the device");
            }
            getFragmentManager().popBackStack();
        } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
            getFragmentManager().popBackStack();
        } else {
            super.onGuidedActionClicked(action);
        }
    }
}
