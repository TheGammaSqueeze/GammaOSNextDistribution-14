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

import android.os.Bundle;

import androidx.annotation.Keep;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Connection;
import com.android.internal.gammaos.SyncthingClient.Device;
import com.android.internal.gammaos.SyncthingClient.Folder;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One device: its settings, and for a device that is already added its connection state and
 * actions. Same draft rules as the folder editor: an existing device is refreshed from the daemon
 * and every change is sent at once; a new device is only sent by Save Device.
 */
@Keep
public class SyncthingDeviceFragment extends SyncthingBaseFragment {

    private static final String ARG_ID = "id";
    private static final String ARG_NEW = "new";
    private static final String ARG_NAME = "name";
    private static final String ARG_ADDRESS = "address";

    private Device mDraft;
    private boolean mNew;

    public static SyncthingDeviceFragment forDevice(String id) {
        SyncthingDeviceFragment f = new SyncthingDeviceFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ID, id);
        b.putBoolean(ARG_NEW, false);
        f.setArguments(b);
        return f;
    }

    /** A device not yet added; the ID must already be in canonical form. */
    public static SyncthingDeviceFragment forNew(String id, String presetName, String presetAddress) {
        SyncthingDeviceFragment f = new SyncthingDeviceFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ID, id);
        b.putBoolean(ARG_NEW, true);
        b.putString(ARG_NAME, presetName);
        b.putString(ARG_ADDRESS, presetAddress);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        Bundle a = getArguments() != null ? getArguments() : new Bundle();
        mNew = a.getBoolean(ARG_NEW, false);
        mDraft = new Device();
        String id = a.getString(ARG_ID, "");
        mDraft.id = id == null ? "" : id;
        if (mNew) {
            String name = a.getString(ARG_NAME, "");
            mDraft.name = name == null ? "" : name;
            String addr = a.getString(ARG_ADDRESS, "");
            if (addr != null && !addr.isEmpty() && !"dynamic".equals(addr)) mDraft.addresses.add(addr);
            mDraft.compression = SyncthingClient.COMPRESSION[0];
        }
    }

    @Override
    protected void rebuild(Snapshot s) {
        if (!mNew) {
            Device stored = s.device(mDraft.id);
            if (stored != null) {
                mDraft = stored;
            } else if (s.apiOk) {
                popBack();
                return;
            }
        }
        final Device d = mDraft;
        final String shortId = SyncthingClient.shortId(d.id);
        getPreferenceScreen().setTitle(mNew ? getString(R.string.syncthing_device_new)
                                            : SyncthingUi.orDefault(d.name, shortId));

        if (mNew) {
            row("save", getString(R.string.syncthing_device_save),
                    getString(R.string.syncthing_device_save_summary), () ->
                    SyncthingUi.act(this, c -> c.putDevice(d), () -> {
                        mNew = false;
                        refresh();
                        popBack();
                    }));
        }
        row("name", getString(R.string.syncthing_device_name),
                SyncthingUi.orDefault(d.name, getString(R.string.syncthing_device_name_from_device)), () ->
                SyncthingUi.askText(this, getString(R.string.syncthing_device_name), d.name, v -> {
                    d.name = v.trim();
                    commit();
                }));
        info("id", getString(R.string.syncthing_device_id), SyncthingClient.wrapId(d.id));
        row("addresses", getString(R.string.syncthing_device_addresses),
                d.addresses.isEmpty() ? "dynamic" : SyncthingUi.join(d.addresses), () ->
                SyncthingUi.askText(this, getString(R.string.syncthing_device_addresses_prompt),
                        SyncthingUi.join(d.addresses), v -> {
                            List<String> out = SyncthingUi.split(v);
                            if (out.isEmpty()) out.add("dynamic");
                            d.addresses = out;
                            commit();
                        }));
        final CharSequence[] compression = {
                getString(R.string.syncthing_compression_metadata),
                getString(R.string.syncthing_compression_always),
                getString(R.string.syncthing_compression_never) };
        int cIdx = SyncthingUi.indexOf(SyncthingClient.COMPRESSION, d.compression, 0);
        row("compression", getString(R.string.syncthing_compression), compression[cIdx], () ->
                SyncthingUi.choose(this, getString(R.string.syncthing_compression), compression, cIdx, which -> {
                    d.compression = SyncthingClient.COMPRESSION[which];
                    commit();
                }));
        toggle("introducer", getString(R.string.syncthing_device_introducer),
                getString(R.string.syncthing_device_introducer_summary), d.introducer, on -> {
                    d.introducer = on;
                    commit();
                });
        toggle("auto_accept", getString(R.string.syncthing_device_auto_accept),
                getString(R.string.syncthing_device_auto_accept_summary), d.autoAcceptFolders, on -> {
                    d.autoAcceptFolders = on;
                    commit();
                });
        List<String> shared = new ArrayList<>();
        for (Folder f : s.folders) if (f.devices.contains(d.id)) shared.add(SyncthingUi.orDefault(f.label, f.id));
        row("shared", getString(R.string.syncthing_device_shared_folders),
                shared.isEmpty() ? getString(R.string.syncthing_none_value) : SyncthingUi.join(shared),
                () -> chooseFolders(s, d));

        if (mNew) return;

        toggle("paused", getString(R.string.syncthing_paused), "", d.paused, on ->
                SyncthingUi.act(this, c -> c.setDevicePaused(d.id, on), this::refresh));
        Connection c = s.connections.get(d.id);
        if (c != null) {
            if (c.connected) {
                info("connection", getString(R.string.syncthing_connection), c.address + " (" + c.type + ")");
                info("version", getString(R.string.syncthing_version),
                        SyncthingUi.orDefault(c.clientVersion, getString(R.string.syncthing_unknown_value)));
                info("transferred", getString(R.string.syncthing_transferred),
                        getString(R.string.syncthing_down_up, SyncthingClient.formatBytes(c.inBytesTotal),
                                SyncthingClient.formatBytes(c.outBytesTotal)));
                info("completion", getString(R.string.syncthing_completion),
                        String.format(Locale.US, "%.1f%%", c.completion) + "\n"
                                + getString(R.string.syncthing_completion_summary));
            } else {
                info("connection", getString(R.string.syncthing_connection),
                        getString(R.string.syncthing_disconnected) + "\n"
                                + getString(R.string.syncthing_last_seen, SyncthingClient.formatAgo(c.lastSeen)));
            }
        }
        row("remove", getString(R.string.syncthing_device_remove),
                getString(R.string.syncthing_device_remove_summary), () ->
                SyncthingUi.confirm(this,
                        getString(R.string.syncthing_remove_title, SyncthingUi.orDefault(d.name, shortId)),
                        getString(R.string.syncthing_device_remove_message),
                        getString(R.string.syncthing_remove_action),
                        () -> SyncthingUi.act(this, cl -> cl.removeDevice(d.id), () -> {
                            refresh();
                            popBack();
                        })));
    }

    /**
     * Which folders are shared with this device. Sharing lives on the folder side of the config,
     * so each folder whose membership changed is sent on its own; that needs the device to exist
     * first.
     */
    private void chooseFolders(Snapshot s, Device d) {
        if (mNew) {
            SyncthingUi.info(this, getString(R.string.syncthing_title),
                    getString(R.string.syncthing_device_save_first));
            return;
        }
        if (s.folders.isEmpty()) {
            SyncthingUi.info(this, getString(R.string.syncthing_no_folders),
                    getString(R.string.syncthing_no_folders_summary));
            return;
        }
        final List<Folder> folders = new ArrayList<>(s.folders);
        CharSequence[] names = new CharSequence[folders.size()];
        boolean[] checked = new boolean[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            Folder f = folders.get(i);
            names[i] = SyncthingUi.orDefault(f.label, f.id);
            checked[i] = f.devices.contains(d.id);
        }
        SyncthingUi.chooseMany(this, getString(R.string.syncthing_device_shared_folders), names, checked, state -> {
            final List<Folder> changed = new ArrayList<>();
            for (int i = 0; i < folders.size(); i++) {
                if (state[i] == checked[i]) continue;
                Folder f = folders.get(i);
                if (state[i]) f.devices.add(d.id); else f.devices.remove(d.id);
                changed.add(f);
            }
            if (changed.isEmpty()) return;
            SyncthingUi.act(this, c -> { for (Folder f : changed) c.putFolder(f); }, this::refresh);
        });
    }

    private void commit() {
        if (mNew) {
            rerender();
            return;
        }
        final Device d = mDraft;
        SyncthingUi.act(this, c -> c.putDevice(d), this::refresh);
    }
}
