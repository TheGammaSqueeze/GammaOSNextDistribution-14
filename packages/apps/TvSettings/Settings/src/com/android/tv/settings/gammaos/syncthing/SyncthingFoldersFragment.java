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
package com.android.tv.settings.gammaos.syncthing;

import androidx.annotation.Keep;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Folder;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

/** The folders this device syncs, one row each with its state, and a row to add another. */
@Keep
public class SyncthingFoldersFragment extends SyncthingBaseFragment {

    @Override
    protected void rebuild(Snapshot s) {
        getPreferenceScreen().setTitle(R.string.syncthing_folders);
        for (Folder f : s.folders) {
            final String id = f.id;
            String devices = getResources().getQuantityString(R.plurals.syncthing_device_count,
                    f.devices.size(), f.devices.size());
            String summary = SyncthingClient.folderStateText(s, f) + "  -  " + f.path
                    + " (" + SyncthingClient.folderTypeLabel(f.type) + ", " + devices + ")";
            row("folder_" + id, SyncthingUi.orDefault(f.label, f.id), summary,
                    () -> open(SyncthingFolderFragment.forFolder(id)));
        }
        row("add", getString(R.string.syncthing_folder_add),
                getString(R.string.syncthing_folder_add_summary),
                () -> open(SyncthingFolderFragment.forNew(null, "", null)));
    }
}
