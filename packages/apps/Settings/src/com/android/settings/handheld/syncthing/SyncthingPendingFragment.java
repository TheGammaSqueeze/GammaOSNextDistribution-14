/*
 * Copyright (C) 2026 GammaOS
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
package com.android.settings.handheld.syncthing;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.preference.Preference;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.PendingDevice;
import com.android.internal.gammaos.SyncthingClient.PendingFolder;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Requests from other devices: devices that want to connect and folders offered to this one.
 * Each row offers to add (which opens the matching editor prefilled) or ignore the request.
 */
public class SyncthingPendingFragment extends SyncthingBaseFragment {

    @Override
    void onSnapshot(Snapshot s) {
        List<Preference> rows = new ArrayList<>();
        if (s != null) {
            for (PendingDevice p : s.pendingDevices) {
                final PendingDevice pd = p;
                String title = p.name.isEmpty() ? SyncthingClient.shortId(p.id) : p.name;
                String summary = getString(R.string.syncthing_pending_wants_to_connect) + "\n"
                        + getString(R.string.syncthing_pending_device_detail,
                                SyncthingClient.shortId(p.id), p.address, SyncthingClient.formatAgo(p.time));
                rows.add(action("pdev_" + p.id, title, summary, () -> choose(title,
                        getString(R.string.syncthing_device_add),
                        () -> {
                            Bundle args = new Bundle();
                            args.putBoolean(SyncthingDeviceFragment.ARG_NEW, true);
                            args.putString(SyncthingDeviceFragment.ARG_DEVICE_ID, pd.id);
                            args.putString(SyncthingDeviceFragment.ARG_NAME, pd.name);
                            args.putString(SyncthingDeviceFragment.ARG_ADDRESS, pd.address);
                            open(SyncthingDeviceFragment.class, args, getString(R.string.syncthing_device_new));
                        },
                        () -> action(client -> client.dismissPendingDevice(pd.id)))));
            }
            for (PendingFolder p : s.pendingFolders) {
                final PendingFolder pf = p;
                String title = p.label.isEmpty() ? p.id : p.label;
                String summary = getString(R.string.syncthing_pending_folder_offered) + "\n"
                        + getString(R.string.syncthing_pending_folder_detail,
                                p.offeredByName, SyncthingClient.formatAgo(p.time));
                rows.add(action("pfld_" + p.id + "_" + p.offeredBy, title, summary, () -> choose(title,
                        getString(R.string.syncthing_folder_add),
                        () -> {
                            Bundle args = new Bundle();
                            args.putBoolean(SyncthingFolderFragment.ARG_NEW, true);
                            args.putString(SyncthingFolderFragment.ARG_FOLDER_ID, pf.id);
                            args.putString(SyncthingFolderFragment.ARG_LABEL, pf.label);
                            args.putString(SyncthingFolderFragment.ARG_SHARE_WITH, pf.offeredBy);
                            open(SyncthingFolderFragment.class, args, getString(R.string.syncthing_folder_new));
                        },
                        () -> action(client -> client.dismissPendingFolder(pf.id, pf.offeredBy)))));
            }
        }
        if (rows.isEmpty()) {
            rows.add(info("none", getString(R.string.syncthing_pending_none),
                    getString(R.string.syncthing_pending_none_summary)));
        }
        applyRows(getString(R.string.syncthing_pending), rows);
    }

    /** Add / Ignore / Cancel for one request. */
    private void choose(CharSequence title, CharSequence addLabel, Runnable onAdd, Runnable onIgnore) {
        if (getContext() == null) return;
        CharSequence[] items = { addLabel, getString(R.string.syncthing_pending_ignore),
                getString(android.R.string.cancel) };
        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setItems(items, (d, which) -> {
                    if (which == 0) onAdd.run();
                    else if (which == 1) onIgnore.run();
                })
                .show();
    }
}
