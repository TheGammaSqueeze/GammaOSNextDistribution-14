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
import com.android.internal.gammaos.SyncthingClient.Connection;
import com.android.internal.gammaos.SyncthingClient.Device;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

/** The other devices this one syncs with, one row each with its connection, plus Add Device. */
@Keep
public class SyncthingDevicesFragment extends SyncthingBaseFragment {

    @Override
    protected void rebuild(Snapshot s) {
        getPreferenceScreen().setTitle(R.string.syncthing_devices);
        for (Device d : s.devices) {
            final String id = d.id;
            StringBuilder summary = new StringBuilder(SyncthingClient.deviceStateText(s, d));
            summary.append("  -  ").append(SyncthingClient.shortId(d.id));
            Connection c = s.connections.get(d.id);
            if (c != null && c.connected) {
                summary.append("  ").append(c.address).append(" (").append(c.type).append(")  ")
                       .append(getString(R.string.syncthing_down_up,
                               SyncthingClient.formatBytes(c.inBytesTotal),
                               SyncthingClient.formatBytes(c.outBytesTotal)));
            }
            row("device_" + id, SyncthingUi.orDefault(d.name, SyncthingClient.shortId(d.id)),
                    summary.toString(), () -> open(SyncthingDeviceFragment.forDevice(id)));
        }
        row("add", getString(R.string.syncthing_device_add),
                getString(R.string.syncthing_device_add_summary), () -> addDevice(s));
    }

    /**
     * Adding starts with the ID, since nothing else makes sense without it; the editor opens once
     * a well-formed ID that is not already known has been typed.
     */
    private void addDevice(Snapshot s) {
        SyncthingUi.askText(this, getString(R.string.syncthing_device_id_prompt), "", typed -> {
            String id = SyncthingClient.normalizeDeviceId(typed);
            if (id == null) {
                SyncthingUi.showError(this, getString(R.string.syncthing_device_id_invalid));
                return;
            }
            if (s.device(id) != null) {
                SyncthingUi.showError(this, getString(R.string.syncthing_device_id_known));
                return;
            }
            if (id.equals(s.myID)) {
                SyncthingUi.showError(this, getString(R.string.syncthing_device_id_own));
                return;
            }
            open(SyncthingDeviceFragment.forNew(id, "", ""));
        });
    }
}
