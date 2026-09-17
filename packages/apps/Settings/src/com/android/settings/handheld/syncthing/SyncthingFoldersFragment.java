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

import android.os.Bundle;

import androidx.preference.Preference;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Folder;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

/** The folders this device syncs, one row each with its state, and a row to add another. */
public class SyncthingFoldersFragment extends SyncthingBaseFragment {

    @Override
    void onSnapshot(Snapshot s) {
        List<Preference> rows = new ArrayList<>();
        if (s != null) {
            for (Folder f : s.folders) {
                final String id = f.id;
                final String title = f.label.isEmpty() ? f.id : f.label;
                String devices = getResources().getQuantityString(
                        R.plurals.syncthing_device_count, f.devices.size(), f.devices.size());
                String summary = SyncthingClient.folderStateText(s, f) + "\n" + f.path
                        + "  (" + SyncthingClient.folderTypeLabel(f.type) + ", " + devices + ")";
                rows.add(action("folder_" + id, title, summary, () -> {
                    Bundle args = new Bundle();
                    args.putString(SyncthingFolderFragment.ARG_FOLDER_ID, id);
                    open(SyncthingFolderFragment.class, args, title);
                }));
            }
        }
        rows.add(action("add", getString(R.string.syncthing_folder_add),
                getString(R.string.syncthing_folder_add_summary), () -> {
                    Bundle args = new Bundle();
                    args.putBoolean(SyncthingFolderFragment.ARG_NEW, true);
                    open(SyncthingFolderFragment.class, args, getString(R.string.syncthing_folder_new));
                }));
        applyRows(getString(R.string.syncthing_folders), rows);
    }
}
