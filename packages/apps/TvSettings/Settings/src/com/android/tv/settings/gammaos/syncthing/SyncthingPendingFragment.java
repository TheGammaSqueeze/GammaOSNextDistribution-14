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
import com.android.internal.gammaos.SyncthingClient.PendingDevice;
import com.android.internal.gammaos.SyncthingClient.PendingFolder;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

/**
 * Requests from other devices: devices that want to connect and folders offered to this device.
 * Accepting opens the matching editor prefilled; ignoring dismisses the request on the daemon.
 */
@Keep
public class SyncthingPendingFragment extends SyncthingBaseFragment {

    @Override
    protected void rebuild(Snapshot s) {
        getPreferenceScreen().setTitle(R.string.syncthing_pending);
        for (PendingDevice p : s.pendingDevices) {
            final PendingDevice pd = p;
            String title = SyncthingUi.orDefault(p.name, SyncthingClient.shortId(p.id));
            String summary = getString(R.string.syncthing_pending_device_summary,
                    SyncthingClient.shortId(p.id), p.address, SyncthingClient.formatAgo(p.time));
            row("pending_device_" + p.id, title, summary, () -> {
                CharSequence[] items = {
                        getString(R.string.syncthing_device_add),
                        getString(R.string.syncthing_pending_ignore),
                        getString(android.R.string.cancel) };
                SyncthingUi.choose(this, title, items, 0, which -> {
                    if (which == 0) open(SyncthingDeviceFragment.forNew(pd.id, pd.name, pd.address));
                    else if (which == 1) SyncthingUi.act(this, c -> c.dismissPendingDevice(pd.id), this::refresh);
                });
            });
        }
        for (PendingFolder p : s.pendingFolders) {
            final PendingFolder pf = p;
            String title = SyncthingUi.orDefault(p.label, p.id);
            String summary = getString(R.string.syncthing_pending_folder_summary,
                    p.offeredByName, SyncthingClient.formatAgo(p.time));
            row("pending_folder_" + p.id + "_" + p.offeredBy, title, summary, () -> {
                CharSequence[] items = {
                        getString(R.string.syncthing_folder_add),
                        getString(R.string.syncthing_pending_ignore),
                        getString(android.R.string.cancel) };
                SyncthingUi.choose(this, title, items, 0, which -> {
                    if (which == 0) open(SyncthingFolderFragment.forNew(pf.id, pf.label, pf.offeredBy));
                    else if (which == 1) SyncthingUi.act(this, c -> c.dismissPendingFolder(pf.id, pf.offeredBy), this::refresh);
                });
            });
        }
        if (s.pendingDevices.isEmpty() && s.pendingFolders.isEmpty()) {
            info("none", getString(R.string.syncthing_pending_none),
                    getString(R.string.syncthing_pending_none_summary));
        }
    }
}
