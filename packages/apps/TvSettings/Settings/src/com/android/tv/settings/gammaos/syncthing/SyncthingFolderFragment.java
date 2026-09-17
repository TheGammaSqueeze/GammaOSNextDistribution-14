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
import android.text.InputType;

import androidx.annotation.Keep;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Device;
import com.android.internal.gammaos.SyncthingClient.Folder;
import com.android.internal.gammaos.SyncthingClient.FolderStatus;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * One folder: its settings, and for a folder that already exists its status and actions.
 *
 * <p>The screen works on a draft. For an existing folder the draft is replaced by the daemon's
 * copy on every refresh (another client may have edited it) and every change is sent at once,
 * the way the web GUI saves each field. A new folder lives only in the draft until Save Folder
 * creates it.
 */
@Keep
public class SyncthingFolderFragment extends SyncthingBaseFragment {

    private static final String ARG_ID = "id";
    private static final String ARG_NEW = "new";
    private static final String ARG_LABEL = "label";
    private static final String ARG_SHARED_WITH = "shared_with";

    private Folder mDraft;
    private boolean mNew;

    /** Opens an existing folder. */
    public static SyncthingFolderFragment forFolder(String id) {
        SyncthingFolderFragment f = new SyncthingFolderFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ID, id);
        b.putBoolean(ARG_NEW, false);
        f.setArguments(b);
        return f;
    }

    /**
     * Opens the editor for a folder that does not exist yet. All three presets may be empty; a
     * pending offer supplies the ID, label and the device that offered it.
     */
    public static SyncthingFolderFragment forNew(String presetId, String presetLabel, String sharedWith) {
        SyncthingFolderFragment f = new SyncthingFolderFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ID, presetId);
        b.putBoolean(ARG_NEW, true);
        b.putString(ARG_LABEL, presetLabel);
        b.putString(ARG_SHARED_WITH, sharedWith);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        Bundle a = getArguments() != null ? getArguments() : new Bundle();
        mNew = a.getBoolean(ARG_NEW, false);
        mDraft = new Folder();
        String id = a.getString(ARG_ID, "");
        if (mNew) {
            mDraft.id = id == null || id.isEmpty() ? SyncthingClient.newFolderId() : id;
            String label = a.getString(ARG_LABEL, "");
            mDraft.label = label == null ? "" : label;
            mDraft.path = SyncthingClient.DEFAULT_ROOT + "/" + (mDraft.label.isEmpty() ? mDraft.id : mDraft.label);
            mDraft.type = SyncthingClient.FOLDER_TYPES[0];
            mDraft.ignorePerms = true;
            String shared = a.getString(ARG_SHARED_WITH, "");
            if (shared != null && !shared.isEmpty()) mDraft.devices.add(shared);
        } else {
            mDraft.id = id == null ? "" : id;
        }
    }

    @Override
    protected void rebuild(Snapshot s) {
        if (!mNew) {
            Folder stored = s.folder(mDraft.id);
            if (stored != null) {
                mDraft = stored;
            } else if (s.apiOk) {
                // Removed (most likely by this screen). Nothing left to edit.
                popBack();
                return;
            }
        }
        final Folder f = mDraft;
        getPreferenceScreen().setTitle(mNew ? getString(R.string.syncthing_folder_new)
                                            : SyncthingUi.orDefault(f.label, f.id));

        if (mNew) {
            row("save", getString(R.string.syncthing_folder_save),
                    getString(R.string.syncthing_folder_save_summary), this::save);
        }
        row("label", getString(R.string.syncthing_folder_label),
                SyncthingUi.orDefault(f.label, getString(R.string.syncthing_none_value)), () ->
                SyncthingUi.askText(this, getString(R.string.syncthing_folder_label), f.label, v -> {
                    f.label = v.trim();
                    commit();
                }));
        if (mNew) {
            row("id", getString(R.string.syncthing_folder_id), f.id, () ->
                    SyncthingUi.askText(this, getString(R.string.syncthing_folder_id_prompt), f.id, v -> {
                        StringBuilder sb = new StringBuilder();
                        for (char ch : v.toCharArray()) {
                            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == '.') sb.append(ch);
                        }
                        if (sb.length() > 0) f.id = sb.toString();
                        rerender();
                    }));
            row("path", getString(R.string.syncthing_folder_path), f.path, () ->
                    SyncthingUi.askText(this, getString(R.string.syncthing_folder_path_prompt), f.path,
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, v -> {
                                String p = v.trim();
                                if (p.isEmpty()) return;
                                p = SyncthingClient.canonicalFolderPath(p);
                                f.path = p;
                                // A label makes the folder recognisable in the list; borrow the
                                // last path segment when none was typed.
                                if (f.label.isEmpty()) {
                                    int sl = p.lastIndexOf('/');
                                    f.label = sl >= 0 && sl < p.length() - 1 ? p.substring(sl + 1) : p;
                                }
                                rerender();
                            }));
        } else {
            info("id", getString(R.string.syncthing_folder_id), f.id);
            info("path", getString(R.string.syncthing_folder_path),
                    f.path + "\n" + getString(R.string.syncthing_folder_path_fixed));
        }

        final CharSequence[] types = {
                getString(R.string.syncthing_folder_type_sendreceive),
                getString(R.string.syncthing_folder_type_sendonly),
                getString(R.string.syncthing_folder_type_receiveonly) };
        row("type", getString(R.string.syncthing_folder_type), SyncthingClient.folderTypeLabel(f.type), () ->
                SyncthingUi.choose(this, getString(R.string.syncthing_folder_type), types,
                        SyncthingUi.indexOf(SyncthingClient.FOLDER_TYPES, f.type, 0), which -> {
                            f.type = SyncthingClient.FOLDER_TYPES[which];
                            commit();
                        }));

        List<String> names = new ArrayList<>();
        for (String id : f.devices) names.add(s.deviceName(id));
        row("shared", getString(R.string.syncthing_folder_shared_with),
                names.isEmpty() ? getString(R.string.syncthing_folder_no_devices) : SyncthingUi.join(names),
                () -> chooseDevices(s, f));

        final CharSequence[] versioning = {
                getString(R.string.syncthing_versioning_none),
                getString(R.string.syncthing_versioning_trashcan),
                getString(R.string.syncthing_versioning_simple),
                getString(R.string.syncthing_versioning_staggered) };
        int vIdx = SyncthingUi.indexOf(SyncthingClient.VERSIONING_TYPES, f.versioningType, -1);
        String vText = vIdx >= 0 ? versioning[vIdx].toString() : f.versioningType;
        if (!f.versioningType.isEmpty() && !f.versioningParam.isEmpty()) vText += " (" + f.versioningParam + ")";
        row("versioning", getString(R.string.syncthing_versioning), vText, () ->
                SyncthingUi.choose(this, getString(R.string.syncthing_versioning), versioning,
                        Math.max(0, vIdx), which -> {
                            f.versioningType = SyncthingClient.VERSIONING_TYPES[which];
                            f.versioningParam = "";
                            commit();
                        }));
        if (!f.versioningType.isEmpty() && !"external".equals(f.versioningType)) {
            final String what = "trashcan".equals(f.versioningType)
                    ? getString(R.string.syncthing_versioning_param_trashcan)
                    : "simple".equals(f.versioningType)
                    ? getString(R.string.syncthing_versioning_param_simple)
                    : getString(R.string.syncthing_versioning_param_staggered);
            row("versioning_param", what,
                    SyncthingUi.orDefault(f.versioningParam, getString(R.string.syncthing_default_value)), () ->
                    SyncthingUi.askText(this, what, f.versioningParam, InputType.TYPE_CLASS_NUMBER, v -> {
                        StringBuilder digits = new StringBuilder();
                        for (char ch : v.toCharArray()) if (Character.isDigit(ch)) digits.append(ch);
                        f.versioningParam = digits.toString();
                        commit();
                    }));
        }

        final CharSequence[] rescan = {
                getString(R.string.syncthing_rescan_1m),
                getString(R.string.syncthing_rescan_10m),
                getString(R.string.syncthing_rescan_1h),
                getString(R.string.syncthing_rescan_1d),
                getString(R.string.syncthing_rescan_never) };
        int rIdx = SyncthingUi.indexOf(SyncthingClient.RESCAN_SECONDS, f.rescanIntervalS, -1);
        row("rescan", getString(R.string.syncthing_rescan_interval),
                rIdx >= 0 ? rescan[rIdx] : getString(R.string.syncthing_rescan_custom, f.rescanIntervalS), () ->
                SyncthingUi.choose(this, getString(R.string.syncthing_rescan_interval), rescan,
                        rIdx >= 0 ? rIdx : 2, which -> {
                            f.rescanIntervalS = SyncthingClient.RESCAN_SECONDS[which];
                            commit();
                        }));
        toggle("watch", getString(R.string.syncthing_folder_watch),
                getString(R.string.syncthing_folder_watch_summary), f.fsWatcherEnabled, on -> {
                    f.fsWatcherEnabled = on;
                    commit();
                });
        toggle("ignore_perms", getString(R.string.syncthing_folder_ignore_perms),
                getString(R.string.syncthing_folder_ignore_perms_summary), f.ignorePerms, on -> {
                    f.ignorePerms = on;
                    commit();
                });

        if (mNew) return;

        toggle("paused", getString(R.string.syncthing_paused), "", f.paused, on ->
                SyncthingUi.act(this, c -> c.setFolderPaused(f.id, on), this::refresh));
        FolderStatus st = s.folderStatus.get(f.id);
        if (st != null) {
            String since = st.stateChanged.isEmpty() ? ""
                    : getString(R.string.syncthing_since, SyncthingClient.formatAgo(st.stateChanged));
            info("state", getString(R.string.syncthing_state),
                    SyncthingClient.folderStateText(s, f) + "\n" + (st.error.isEmpty() ? since : st.error));
            info("global", getString(R.string.syncthing_global_state),
                    getString(R.string.syncthing_bytes_files, SyncthingClient.formatBytes(st.globalBytes), st.globalFiles));
            info("local", getString(R.string.syncthing_local_state),
                    getString(R.string.syncthing_bytes_files, SyncthingClient.formatBytes(st.localBytes), st.localFiles));
            if (st.needBytes > 0) {
                info("need", getString(R.string.syncthing_out_of_sync),
                        getString(R.string.syncthing_bytes_files, SyncthingClient.formatBytes(st.needBytes), st.needFiles));
            }
            if (st.pullErrors > 0) {
                info("errors", getString(R.string.syncthing_failed_items),
                        st.pullErrors + "\n" + getString(R.string.syncthing_failed_items_summary));
            }
        }
        row("rescan_now", getString(R.string.syncthing_rescan_now), "", () ->
                SyncthingUi.act(this, c -> c.rescanFolder(f.id), this::refresh));
        if ("sendonly".equals(f.type)) {
            row("override", getString(R.string.syncthing_override),
                    getString(R.string.syncthing_override_summary), () ->
                    SyncthingUi.act(this, c -> c.overrideFolder(f.id), this::refresh));
        }
        if ("receiveonly".equals(f.type)) {
            row("revert", getString(R.string.syncthing_revert),
                    getString(R.string.syncthing_revert_summary), () ->
                    SyncthingUi.act(this, c -> c.revertFolder(f.id), this::refresh));
        }
        row("ignores", getString(R.string.syncthing_ignores),
                getString(R.string.syncthing_ignores_summary),
                () -> open(SyncthingIgnoresFragment.forFolder(f.id)));
        row("remove", getString(R.string.syncthing_folder_remove),
                getString(R.string.syncthing_folder_remove_summary), () ->
                SyncthingUi.confirm(this,
                        getString(R.string.syncthing_remove_title, SyncthingUi.orDefault(f.label, f.id)),
                        getString(R.string.syncthing_folder_remove_message),
                        getString(R.string.syncthing_remove_action),
                        () -> SyncthingUi.act(this, c -> c.removeFolder(f.id), () -> {
                            refresh();
                            popBack();
                        })));
    }

    /** Which devices the folder is shared with, as check boxes over the known devices. */
    private void chooseDevices(Snapshot s, Folder f) {
        if (s.devices.isEmpty()) {
            SyncthingUi.info(this, getString(R.string.syncthing_no_devices),
                    getString(R.string.syncthing_no_devices_summary));
            return;
        }
        final List<Device> devices = new ArrayList<>(s.devices);
        CharSequence[] names = new CharSequence[devices.size()];
        boolean[] checked = new boolean[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            Device d = devices.get(i);
            names[i] = SyncthingUi.orDefault(d.name, SyncthingClient.shortId(d.id));
            checked[i] = f.devices.contains(d.id);
        }
        SyncthingUi.chooseMany(this, getString(R.string.syncthing_folder_shared_with), names, checked, state -> {
            List<String> chosen = new ArrayList<>();
            for (int i = 0; i < devices.size(); i++) if (state[i]) chosen.add(devices.get(i).id);
            f.devices = chosen;
            commit();
        });
    }

    /** A new folder is only sent by Save; an existing one goes to the daemon after each change. */
    private void commit() {
        if (mNew) {
            rerender();
            return;
        }
        final Folder f = mDraft;
        SyncthingUi.act(this, c -> c.putFolder(f), this::refresh);
    }

    /** Creates the directory and the folder, then returns to the list. */
    private void save() {
        final Folder f = mDraft;
        if (f.path.isEmpty()) {
            SyncthingUi.showError(this, getString(R.string.syncthing_folder_no_path));
            return;
        }
        f.path = SyncthingClient.canonicalFolderPath(f.path);
        if (!SyncthingClient.isSupportedFolderPath(f.path)) {
            SyncthingUi.showError(this, getString(R.string.syncthing_folder_path_unsupported));
            return;
        }
        // FAT cards store no permission bits; syncing them would flag every file as changed. A
        // pulled card leaves the folder in "path missing" until the next full rescan, so keep that
        // rescan at most ten minutes away.
        if (SyncthingClient.isRemovableFolderPath(f.path)) {
            f.ignorePerms = true;
            if (f.rescanIntervalS <= 0 || f.rescanIntervalS > 600) f.rescanIntervalS = 600;
        }
        SyncthingUi.act(this, c -> {
            // The daemon refuses a path that does not exist. A failure here is not fatal: the
            // daemon reports a missing or unwritable path as the folder's error.
            File dir = new File(f.path);
            if (!dir.isDirectory()) dir.mkdirs();
            c.putFolder(f);
        }, () -> {
            mNew = false;
            refresh();
            popBack();
        });
    }
}
