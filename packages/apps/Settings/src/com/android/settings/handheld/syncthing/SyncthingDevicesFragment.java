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
import android.text.InputType;

import androidx.preference.Preference;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Connection;
import com.android.internal.gammaos.SyncthingClient.Device;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

/** The devices this one syncs with, one row each with its connection state, and a row to add one. */
public class SyncthingDevicesFragment extends SyncthingBaseFragment {

    @Override
    void onSnapshot(Snapshot s) {
        List<Preference> rows = new ArrayList<>();
        if (s != null) {
            for (Device d : s.devices) {
                final String id = d.id;
                final String title = d.name.isEmpty() ? SyncthingClient.shortId(d.id) : d.name;
                String desc = SyncthingClient.shortId(d.id);
                Connection c = s.connections.get(d.id);
                if (c != null && c.connected) {
                    desc += "  " + c.address + " (" + c.type + ")  "
                            + getString(R.string.syncthing_transfer_totals,
                                    SyncthingClient.formatBytes(c.inBytesTotal),
                                    SyncthingClient.formatBytes(c.outBytesTotal));
                }
                rows.add(action("device_" + id, title,
                        SyncthingClient.deviceStateText(s, d) + "\n" + desc, () -> {
                            Bundle args = new Bundle();
                            args.putString(SyncthingDeviceFragment.ARG_DEVICE_ID, id);
                            open(SyncthingDeviceFragment.class, args, title);
                        }));
            }
        }
        rows.add(action("add", getString(R.string.syncthing_device_add),
                getString(R.string.syncthing_device_add_summary), this::askDeviceId));
        applyRows(getString(R.string.syncthing_devices), rows);
    }

    /**
     * Adding starts with the ID, since nothing else makes sense without it; the editor opens
     * once a well-formed ID that is not already known has been typed.
     */
    private void askDeviceId() {
        promptText(getString(R.string.syncthing_device_add), "",
                getString(R.string.syncthing_device_id_hint),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, typed -> {
                    String id = SyncthingClient.normalizeDeviceId(typed);
                    if (id == null) {
                        showError(getString(R.string.syncthing_device_id_invalid));
                        return;
                    }
                    Snapshot s = lastSnapshot();
                    if (s != null) {
                        if (s.device(id) != null) {
                            showError(getString(R.string.syncthing_device_id_exists));
                            return;
                        }
                        if (id.equals(s.myID)) {
                            showError(getString(R.string.syncthing_device_id_own));
                            return;
                        }
                    }
                    Bundle args = new Bundle();
                    args.putBoolean(SyncthingDeviceFragment.ARG_NEW, true);
                    args.putString(SyncthingDeviceFragment.ARG_DEVICE_ID, id);
                    open(SyncthingDeviceFragment.class, args, getString(R.string.syncthing_device_new));
                });
    }
}
