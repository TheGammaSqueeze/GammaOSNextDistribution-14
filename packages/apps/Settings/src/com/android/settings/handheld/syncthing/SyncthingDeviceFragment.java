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
import com.android.internal.gammaos.SyncthingClient.Folder;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One device: its settings, and for a stored device its connection details and actions.
 *
 * <p>A stored device is updated with {@code putDevice} after every change. A new device
 * ({@link #ARG_NEW}, with the ID already validated by the caller) is a draft that only reaches
 * the daemon when "Save Device" is pressed. Which folders are shared with a device lives in each
 * folder's device list, so that row edits folders, and for a new device it has to wait until the
 * device exists.
 */
public class SyncthingDeviceFragment extends SyncthingBaseFragment {

    static final String ARG_NEW = "new";
    static final String ARG_DEVICE_ID = "device_id";
    /** Optional name for a new device (from a pending request). */
    static final String ARG_NAME = "name";
    /** Optional address for a new device (where the pending request came from). */
    static final String ARG_ADDRESS = "address";

    private boolean mNew;
    private String mDeviceId;
    private Device mDraft;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        mNew = args.getBoolean(ARG_NEW, false);
        mDeviceId = args.getString(ARG_DEVICE_ID, "");
        if (mNew) {
            mDraft = new Device();
            mDraft.id = mDeviceId;
            mDraft.name = args.getString(ARG_NAME, "");
            mDraft.addresses = new ArrayList<>();
            mDraft.addresses.add("dynamic");
            String addr = args.getString(ARG_ADDRESS, "");
            if (!addr.isEmpty() && !"dynamic".equals(addr)) mDraft.addresses.add(addr);
            mDraft.compression = "metadata";
        }
    }

    @Override
    void onSnapshot(Snapshot s) {
        if (!mNew) {
            if (s == null || !s.apiOk) {
                if (mDraft == null) {
                    List<Preference> rows = new ArrayList<>();
                    rows.add(info("waiting", getString(R.string.syncthing_waiting), null));
                    applyRows(null, rows);
                }
                return;
            }
            Device d = s.device(mDeviceId);
            if (d == null) {
                finish();
                return;
            }
            mDraft = d;
        }
        rebuild(s);
    }

    private void commit() {
        if (mNew) {
            rebuild(lastSnapshot());
            return;
        }
        final Device d = mDraft;
        action(client -> client.putDevice(d));
    }

    private void rebuild(Snapshot s) {
        final Device d = mDraft;
        final List<Preference> rows = new ArrayList<>();
        final String title = mNew ? getString(R.string.syncthing_device_new)
                : (d.name.isEmpty() ? SyncthingClient.shortId(d.id) : d.name);

        if (mNew) {
            rows.add(action("save", getString(R.string.syncthing_device_save),
                    getString(R.string.syncthing_device_save_summary), () -> {
                        final Device draft = mDraft;
                        action(client -> client.putDevice(draft), () -> {
                            mNew = false;
                            finish();
                        });
                    }));
        }

        rows.add(text("name", getString(R.string.syncthing_device_name), d.name,
                getString(R.string.syncthing_device_name_from_device) + "\n"
                        + getString(R.string.syncthing_device_name_summary),
                InputType.TYPE_CLASS_TEXT, v -> {
                    d.name = v;
                    commit();
                }));
        rows.add(info("id", getString(R.string.syncthing_device_id), SyncthingClient.wrapId(d.id)));
        rows.add(text("addresses", getString(R.string.syncthing_device_addresses), join(d.addresses),
                "dynamic", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, v -> {
                    List<String> out = splitList(v);
                    if (out.isEmpty()) out.add("dynamic");
                    d.addresses = out;
                    commit();
                }));
        // The row's summary is the value; the explanation goes under it.
        Preference addresses = rows.get(rows.size() - 1);
        addresses.setSummary(addresses.getSummary() + "\n"
                + getString(R.string.syncthing_device_addresses_summary));

        CharSequence[] compLabels = {
                getString(R.string.syncthing_compression_metadata),
                getString(R.string.syncthing_compression_always),
                getString(R.string.syncthing_compression_never) };
        String compSummary = compLabels[0].toString();
        for (int i = 0; i < SyncthingClient.COMPRESSION.length; i++) {
            if (SyncthingClient.COMPRESSION[i].equals(d.compression)) compSummary = compLabels[i].toString();
        }
        rows.add(list("compression", getString(R.string.syncthing_device_compression), compLabels,
                SyncthingClient.COMPRESSION, d.compression, compSummary, v -> {
                    d.compression = v;
                    commit();
                }));
        rows.add(toggle("introducer", getString(R.string.syncthing_device_introducer),
                getString(R.string.syncthing_device_introducer_summary), d.introducer, on -> {
                    d.introducer = on;
                    commit();
                }));
        rows.add(toggle("auto_accept", getString(R.string.syncthing_device_auto_accept),
                getString(R.string.syncthing_device_auto_accept_summary), d.autoAcceptFolders, on -> {
                    d.autoAcceptFolders = on;
                    commit();
                }));

        // Shared folders: read from every folder's device list.
        List<Folder> folders = s != null ? s.folders : new ArrayList<>();
        List<String> sharedNames = new ArrayList<>();
        Set<String> selected = new HashSet<>();
        CharSequence[] names = new CharSequence[folders.size()];
        CharSequence[] ids = new CharSequence[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            Folder f = folders.get(i);
            names[i] = f.label.isEmpty() ? f.id : f.label;
            ids[i] = f.id;
            if (f.devices.contains(d.id)) {
                selected.add(f.id);
                sharedNames.add(names[i].toString());
            }
        }
        String sharedSummary = (sharedNames.isEmpty() ? getString(R.string.syncthing_none_value) : join(sharedNames))
                + "\n" + getString(R.string.syncthing_device_shared_folders_summary);
        if (mNew) {
            rows.add(action("share", getString(R.string.syncthing_device_shared_folders), sharedSummary,
                    () -> showInfo(getString(R.string.syncthing_title),
                            getString(R.string.syncthing_device_save_first))));
        } else if (folders.isEmpty()) {
            rows.add(info("share", getString(R.string.syncthing_device_shared_folders),
                    getString(R.string.syncthing_device_no_folders)));
        } else {
            final List<Folder> all = folders;
            final String id = d.id;
            rows.add(multi("share", getString(R.string.syncthing_device_shared_folders), sharedSummary,
                    names, ids, selected, chosen -> shareFolders(all, id, chosen)));
        }

        if (!mNew) {
            final String id = d.id;
            rows.add(toggle("paused", getString(R.string.syncthing_paused), null, d.paused,
                    on -> action(client -> client.setDevicePaused(id, on))));
            Connection c = s != null ? s.connections.get(d.id) : null;
            if (c != null) {
                rows.add(info("connection", getString(R.string.syncthing_device_connection),
                        c.connected ? c.address + " (" + c.type + ")"
                                : getString(R.string.syncthing_disconnected) + "\n"
                                        + getString(R.string.syncthing_last_seen, SyncthingClient.formatAgo(c.lastSeen))));
                if (c.connected) {
                    rows.add(info("version", getString(R.string.syncthing_device_version),
                            c.clientVersion.isEmpty() ? getString(R.string.syncthing_unknown) : c.clientVersion));
                    rows.add(info("transferred", getString(R.string.syncthing_device_transferred),
                            getString(R.string.syncthing_transfer_totals,
                                    SyncthingClient.formatBytes(c.inBytesTotal),
                                    SyncthingClient.formatBytes(c.outBytesTotal))));
                    rows.add(info("completion", getString(R.string.syncthing_device_completion),
                            String.format(Locale.US, "%.1f%%", c.completion) + "\n"
                                    + getString(R.string.syncthing_device_completion_summary)));
                }
            }
            rows.add(action("remove", getString(R.string.syncthing_device_remove),
                    getString(R.string.syncthing_device_remove_summary),
                    () -> confirm(getString(R.string.syncthing_remove_title, title),
                            getString(R.string.syncthing_device_remove_message),
                            getString(R.string.syncthing_remove),
                            () -> action(client -> client.removeDevice(id), this::finish))));
        }

        applyRows(title, rows);
    }

    /** Add or drop this device on every folder whose membership changed, in one background run. */
    private void shareFolders(List<Folder> folders, String deviceId, Set<String> chosen) {
        final List<Folder> changed = new ArrayList<>();
        for (Folder f : folders) {
            boolean has = f.devices.contains(deviceId);
            boolean want = chosen.contains(f.id);
            if (has == want) continue;
            if (want) f.devices.add(deviceId); else f.devices.remove(deviceId);
            changed.add(f);
        }
        if (changed.isEmpty()) return;
        action(client -> {
            for (Folder f : changed) client.putFolder(f);
        });
    }
}
