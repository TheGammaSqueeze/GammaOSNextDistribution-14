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
import com.android.internal.gammaos.SyncthingClient.Device;
import com.android.internal.gammaos.SyncthingClient.Folder;
import com.android.internal.gammaos.SyncthingClient.FolderStatus;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One folder: its settings, and for a stored folder its status and actions.
 *
 * <p>A stored folder is edited the way the web GUI's per-field Save works: every change is sent
 * with {@code putFolder} at once. A new folder ({@link #ARG_NEW}) is a draft that only reaches the
 * daemon when the "Save Folder" row at the top is pressed.
 */
public class SyncthingFolderFragment extends SyncthingBaseFragment {

    static final String ARG_NEW = "new";
    static final String ARG_FOLDER_ID = "folder_id";
    /** Optional label for a new folder (from a pending offer). */
    static final String ARG_LABEL = "label";
    /** Optional device ID a new folder starts shared with (the device that offered it). */
    static final String ARG_SHARE_WITH = "share_with";

    private static final CharSequence[] VERSIONING_VALUES = { "", "trashcan", "simple", "staggered" };

    private boolean mNew;
    private String mFolderId;
    /** The folder shown: a fresh draft, or the daemon's copy from the last snapshot. */
    private Folder mDraft;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        mNew = args.getBoolean(ARG_NEW, false);
        mFolderId = args.getString(ARG_FOLDER_ID, "");
        if (mNew) {
            mDraft = new Folder();
            mDraft.id = mFolderId.isEmpty() ? SyncthingClient.newFolderId() : mFolderId;
            mDraft.label = args.getString(ARG_LABEL, "");
            mDraft.type = "sendreceive";
            mDraft.path = SyncthingClient.DEFAULT_ROOT + "/"
                    + (mDraft.label.isEmpty() ? mDraft.id : mDraft.label);
            mDraft.ignorePerms = true;
            String shareWith = args.getString(ARG_SHARE_WITH, "");
            if (!shareWith.isEmpty()) mDraft.devices.add(shareWith);
        }
    }

    @Override
    void onSnapshot(Snapshot s) {
        if (!mNew) {
            // Follow the daemon's copy: another client may have edited the folder meanwhile.
            if (s == null || !s.apiOk) {
                if (mDraft == null) showWaiting();
                return;
            }
            Folder f = s.folder(mFolderId);
            if (f == null) {
                // Removed, most likely by this screen.
                finish();
                return;
            }
            mDraft = f;
        }
        rebuild(s);
    }

    private void showWaiting() {
        List<Preference> rows = new ArrayList<>();
        rows.add(info("waiting", getString(R.string.syncthing_waiting), null));
        applyRows(null, rows);
    }

    /** Send the current draft of a stored folder; a new folder only changes on screen. */
    private void commit() {
        if (mNew) {
            rebuild(lastSnapshot());
            return;
        }
        final Folder f = mDraft;
        action(client -> client.putFolder(f));
    }

    private void rebuild(Snapshot s) {
        final Folder f = mDraft;
        final List<Preference> rows = new ArrayList<>();
        final String title = mNew ? getString(R.string.syncthing_folder_new)
                : (f.label.isEmpty() ? f.id : f.label);

        if (mNew) {
            rows.add(action("save", getString(R.string.syncthing_folder_save),
                    getString(R.string.syncthing_folder_save_summary), this::saveNew));
        }

        rows.add(text("label", getString(R.string.syncthing_folder_label), f.label,
                getString(R.string.syncthing_none_value) + "\n" + getString(R.string.syncthing_folder_label_summary),
                InputType.TYPE_CLASS_TEXT, v -> {
                    f.label = v;
                    commit();
                }));

        if (mNew) {
            rows.add(text("id", getString(R.string.syncthing_folder_id), f.id,
                    getString(R.string.syncthing_folder_id_summary), InputType.TYPE_CLASS_TEXT, v -> {
                        String id = v.replaceAll("[^A-Za-z0-9._-]", "");
                        if (!id.isEmpty()) f.id = id;
                        commit();
                    }));
            rows.add(text("path", getString(R.string.syncthing_folder_path), f.path,
                    getString(R.string.syncthing_folder_path_new_summary),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, v -> {
                        if (v.isEmpty()) return;
                        f.path = v;
                        if (f.label.isEmpty()) f.label = new File(v).getName();
                        commit();
                    }));
        } else {
            rows.add(info("id", getString(R.string.syncthing_folder_id), f.id));
            rows.add(info("path", getString(R.string.syncthing_folder_path),
                    f.path + "\n" + getString(R.string.syncthing_folder_path_fixed_summary)));
        }

        rows.add(list("type", getString(R.string.syncthing_folder_type),
                new CharSequence[] {
                        getString(R.string.syncthing_folder_type_sendreceive),
                        getString(R.string.syncthing_folder_type_sendonly),
                        getString(R.string.syncthing_folder_type_receiveonly) },
                new CharSequence[] { "sendreceive", "sendonly", "receiveonly" },
                f.type, SyncthingClient.folderTypeLabel(f.type), v -> {
                    f.type = v;
                    commit();
                }));

        // Shared with: the devices from the snapshot, ticked where the folder lists them.
        List<Device> devices = s != null ? s.devices : new ArrayList<>();
        if (devices.isEmpty()) {
            rows.add(info("share", getString(R.string.syncthing_folder_shared_with),
                    getString(R.string.syncthing_folder_no_devices)));
        } else {
            CharSequence[] names = new CharSequence[devices.size()];
            CharSequence[] ids = new CharSequence[devices.size()];
            List<String> sharedNames = new ArrayList<>();
            Set<String> selected = new HashSet<>();
            for (int i = 0; i < devices.size(); i++) {
                Device d = devices.get(i);
                names[i] = d.name.isEmpty() ? SyncthingClient.shortId(d.id) : d.name;
                ids[i] = d.id;
                if (f.devices.contains(d.id)) {
                    selected.add(d.id);
                    sharedNames.add(names[i].toString());
                }
            }
            rows.add(multi("share", getString(R.string.syncthing_folder_shared_with),
                    (sharedNames.isEmpty() ? getString(R.string.syncthing_folder_no_shared_devices) : join(sharedNames))
                            + "\n" + getString(R.string.syncthing_folder_shared_with_summary),
                    names, ids, selected, chosen -> {
                        // Keep the daemon's order for devices that stay, append new ones after.
                        List<String> keep = new ArrayList<>();
                        for (String id : f.devices) if (chosen.contains(id)) keep.add(id);
                        for (String id : chosen) if (!keep.contains(id)) keep.add(id);
                        f.devices = keep;
                        commit();
                    }));
        }

        // Versioning: the type, and its one parameter when a type is set.
        String verLabel = versioningLabel(f.versioningType);
        if (!f.versioningType.isEmpty() && !f.versioningParam.isEmpty()) {
            verLabel += " (" + f.versioningParam + ")";
        }
        rows.add(list("versioning", getString(R.string.syncthing_folder_versioning),
                new CharSequence[] {
                        getString(R.string.syncthing_versioning_none),
                        getString(R.string.syncthing_versioning_trashcan),
                        getString(R.string.syncthing_versioning_simple),
                        getString(R.string.syncthing_versioning_staggered) },
                VERSIONING_VALUES, f.versioningType,
                verLabel + "\n" + getString(R.string.syncthing_folder_versioning_summary), v -> {
                    f.versioningType = v;
                    f.versioningParam = "";
                    commit();
                }));
        if (!f.versioningType.isEmpty() && !"external".equals(f.versioningType)) {
            int what = "trashcan".equals(f.versioningType) ? R.string.syncthing_versioning_param_days
                    : "simple".equals(f.versioningType) ? R.string.syncthing_versioning_param_keep
                    : R.string.syncthing_versioning_param_maxage;
            rows.add(text("versioning_param", getString(what), f.versioningParam,
                    getString(R.string.syncthing_default_value), InputType.TYPE_CLASS_NUMBER, v -> {
                        f.versioningParam = v.replaceAll("[^0-9]", "");
                        commit();
                    }));
        }

        // Rescan interval: the fixed choices, with a value set elsewhere shown as custom.
        CharSequence[] rescanLabels = {
                getString(R.string.syncthing_rescan_1min), getString(R.string.syncthing_rescan_10min),
                getString(R.string.syncthing_rescan_1h), getString(R.string.syncthing_rescan_1d),
                getString(R.string.syncthing_rescan_never) };
        CharSequence[] rescanValues = new CharSequence[SyncthingClient.RESCAN_SECONDS.length];
        String rescanSummary = getString(R.string.syncthing_rescan_custom, f.rescanIntervalS);
        for (int i = 0; i < rescanValues.length; i++) {
            rescanValues[i] = Integer.toString(SyncthingClient.RESCAN_SECONDS[i]);
            if (SyncthingClient.RESCAN_SECONDS[i] == f.rescanIntervalS) rescanSummary = rescanLabels[i].toString();
        }
        rows.add(list("rescan", getString(R.string.syncthing_folder_rescan), rescanLabels, rescanValues,
                Integer.toString(f.rescanIntervalS), rescanSummary, v -> {
                    f.rescanIntervalS = parseCount(v);
                    commit();
                }));

        rows.add(toggle("watch", getString(R.string.syncthing_folder_watch),
                getString(R.string.syncthing_folder_watch_summary), f.fsWatcherEnabled, on -> {
                    f.fsWatcherEnabled = on;
                    commit();
                }));
        rows.add(toggle("ignore_perms", getString(R.string.syncthing_folder_ignore_perms),
                getString(R.string.syncthing_folder_ignore_perms_summary), f.ignorePerms, on -> {
                    f.ignorePerms = on;
                    commit();
                }));

        if (!mNew) {
            final String id = f.id;
            rows.add(toggle("paused", getString(R.string.syncthing_paused), null, f.paused,
                    on -> action(client -> client.setFolderPaused(id, on))));

            FolderStatus st = s != null ? s.folderStatus.get(f.id) : null;
            if (st != null) {
                String since = !st.error.isEmpty() ? st.error
                        : st.stateChanged.isEmpty() ? ""
                        : getString(R.string.syncthing_since, SyncthingClient.formatAgo(st.stateChanged));
                rows.add(info("state", getString(R.string.syncthing_state),
                        SyncthingClient.folderStateText(s, f) + (since.isEmpty() ? "" : "\n" + since)));
                rows.add(info("global", getString(R.string.syncthing_global_state),
                        getString(R.string.syncthing_bytes_files, SyncthingClient.formatBytes(st.globalBytes), st.globalFiles)));
                rows.add(info("local", getString(R.string.syncthing_local_state),
                        getString(R.string.syncthing_bytes_files, SyncthingClient.formatBytes(st.localBytes), st.localFiles)));
                if (st.needBytes > 0) {
                    rows.add(info("need", getString(R.string.syncthing_out_of_sync),
                            getString(R.string.syncthing_bytes_files, SyncthingClient.formatBytes(st.needBytes), st.needFiles)));
                }
                if (st.pullErrors > 0) {
                    rows.add(info("failed", getString(R.string.syncthing_failed_items),
                            st.pullErrors + "\n" + getString(R.string.syncthing_failed_items_summary)));
                }
            }

            rows.add(action("rescan_now", getString(R.string.syncthing_folder_rescan_now), null,
                    () -> action(client -> client.rescanFolder(id))));
            if ("sendonly".equals(f.type)) {
                rows.add(action("override", getString(R.string.syncthing_folder_override),
                        getString(R.string.syncthing_folder_override_summary),
                        () -> action(client -> client.overrideFolder(id))));
            }
            if ("receiveonly".equals(f.type)) {
                rows.add(action("revert", getString(R.string.syncthing_folder_revert),
                        getString(R.string.syncthing_folder_revert_summary),
                        () -> action(client -> client.revertFolder(id))));
            }
            rows.add(action("ignores", getString(R.string.syncthing_ignores),
                    getString(R.string.syncthing_ignores_summary), () -> {
                        Bundle args = new Bundle();
                        args.putString(SyncthingIgnoresFragment.ARG_FOLDER_ID, id);
                        open(SyncthingIgnoresFragment.class, args, getString(R.string.syncthing_ignores));
                    }));
            rows.add(action("remove", getString(R.string.syncthing_folder_remove),
                    getString(R.string.syncthing_folder_remove_summary),
                    () -> confirm(getString(R.string.syncthing_remove_title, title),
                            getString(R.string.syncthing_folder_remove_message),
                            getString(R.string.syncthing_remove),
                            () -> action(client -> client.removeFolder(id), this::finish))));
        }

        applyRows(title, rows);
    }

    private String versioningLabel(String type) {
        switch (type == null ? "" : type) {
            case "trashcan": return getString(R.string.syncthing_versioning_trashcan);
            case "simple": return getString(R.string.syncthing_versioning_simple);
            case "staggered": return getString(R.string.syncthing_versioning_staggered);
            case "": return getString(R.string.syncthing_versioning_none);
            default: return type;
        }
    }

    /** Create the directory, then the folder; the screen closes once the daemon has it. */
    private void saveNew() {
        final Folder f = mDraft;
        if (f.path.isEmpty()) {
            showError(getString(R.string.syncthing_folder_path_required));
            return;
        }
        action(client -> {
            ensureDirectory(f.path);
            client.putFolder(f);
        }, () -> {
            mNew = false;
            finish();
        });
    }

    /**
     * Make sure the folder's directory exists and the daemon can write to it. Settings runs as
     * system, as does the daemon, and internal storage is group writable for media_rw, so a
     * plain mkdirs with group access is enough; only the missing tail is created.
     */
    private static void ensureDirectory(String path) throws IOException {
        File dir = new File(path);
        if (dir.isDirectory()) return;
        // Remember which directories are missing so only those get their mode set; an existing
        // parent is left exactly as it is.
        List<File> created = new ArrayList<>();
        for (File d = dir; d != null && !d.exists(); d = d.getParentFile()) created.add(d);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Could not create " + path);
        }
        for (File d : created) {
            d.setReadable(true, false);
            d.setWritable(true, false);
            d.setExecutable(true, false);
        }
    }
}
