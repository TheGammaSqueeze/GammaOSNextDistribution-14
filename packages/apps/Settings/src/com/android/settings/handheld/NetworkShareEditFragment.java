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
package com.android.settings.handheld;

import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.os.Bundle;
import android.text.InputType;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.gammaos.GammaShareConfig;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/**
 * Editor for one network share.
 *
 * The fields change with the protocol: NFS grants access by client address rather than by account,
 * so it has no username or password, and only SMB has a workgroup. Showing fields a protocol
 * ignores would be collecting input that silently does nothing, so the screen is rebuilt whenever
 * the type changes.
 */
public class NetworkShareEditFragment extends SettingsPreferenceFragment {

    static final String ARG_SLOT = "slot";

    private int mSlot;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSlot = getArguments() != null ? getArguments().getInt(ARG_SLOT, 0) : 0;
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getPrefContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();

        final GammaShareConfig.Share s = GammaShareConfig.load(mSlot);
        if (s == null) {
            // The share was removed (most likely by this screen). Nothing left to edit.
            finish();
            return;
        }
        screen.setTitle(s.name);

        final String problem = GammaShareConfig.problem(s);

        // Connect. Only offered once the share is complete; otherwise say what is missing rather
        // than letting the mount fail quietly in the background.
        SwitchPreference enabled = new SwitchPreference(getPrefContext());
        enabled.setKey("share_enabled");
        enabled.setTitle(R.string.network_shares_enabled);
        enabled.setChecked(s.enabled && problem == null);
        enabled.setEnabled(problem == null);
        enabled.setSummary(problem != null ? problem : statusSummary(s));
        enabled.setOnPreferenceChangeListener((p, v) -> {
            GammaShareConfig.setEnabled(mSlot, (Boolean) v);
            refresh();
            return true;
        });
        screen.addPreference(enabled);

        addText(screen, "share_name", R.string.network_shares_name, s.name,
                InputType.TYPE_CLASS_TEXT, v -> {
                    // The name is a directory under /mnt/shares, so it cannot contain a separator.
                    String name = v.replace('/', '_').trim();
                    if (name.isEmpty() || name.equals(".") || name.equals("..")) return;
                    if (name.equals(s.name)) return;
                    // The mount point comes from the name, so a rename has to take the old mount
                    // down; the user switches it back on and it returns under the new folder.
                    if (s.enabled) GammaShareConfig.setEnabled(mSlot, false);
                    s.name = name;
                    GammaShareConfig.save(s);
                });

        ListPreference type = new ListPreference(getPrefContext());
        type.setKey("share_type");
        type.setTitle(R.string.network_shares_type);
        type.setEntries(new CharSequence[] {"SMB", "NFS", "WebDAV", "FTP"});
        type.setEntryValues(new CharSequence[] {
                GammaShareConfig.TYPE_SMB, GammaShareConfig.TYPE_NFS,
                GammaShareConfig.TYPE_WEBDAV, GammaShareConfig.TYPE_FTP });
        type.setValue(s.type);
        type.setSummary(GammaShareConfig.typeLabel(s.type));
        type.setOnPreferenceChangeListener((p, v) -> {
            String want = (String) v;
            if (want.equals(s.type)) return true;
            s.type = want;
            // A port that was the old protocol's default means nothing under the new one.
            s.port = 0;
            GammaShareConfig.save(s);
            // Changing the protocol of a live mount would leave it serving the old one until
            // something restarted it, so take it down.
            if (s.enabled) GammaShareConfig.setEnabled(mSlot, false);
            refresh();
            return true;
        });
        screen.addPreference(type);

        addText(screen, "share_host", R.string.network_shares_server, s.host,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, v -> {
                    s.host = v.trim();
                    GammaShareConfig.save(s);
                });

        // No port field for NFS: the client asks the server's portmapper where nfsd and
        // mountd are listening, so a port typed here would be ignored.
        if (!GammaShareConfig.TYPE_NFS.equals(s.type)) {
            EditTextPreference port = addText(screen, "share_port", R.string.network_shares_port,
                    s.port > 0 ? Integer.toString(s.port) : "",
                    InputType.TYPE_CLASS_NUMBER, v -> {
                        int p;
                        try {
                            p = Integer.parseInt(v.trim());
                        } catch (NumberFormatException e) {
                            p = 0;
                        }
                        // Anything outside a real port number means "use the protocol default" rather
                        // than storing a value that could only ever fail to connect.
                        s.port = (p > 0 && p <= 65535) ? p : 0;
                        GammaShareConfig.save(s);
                    });
            if (s.port <= 0) {
                port.setSummary(getString(R.string.network_shares_port_default,
                        GammaShareConfig.defaultPort(s.type, s.useTls)));
            }
        }

        addText(screen, "share_path", pathTitle(s.type), s.path,
                InputType.TYPE_CLASS_TEXT, v -> {
                    s.path = v.trim();
                    GammaShareConfig.save(s);
                });

        if (s.usesCredentials()) {
            addText(screen, "share_user", R.string.network_shares_user, s.user,
                    InputType.TYPE_CLASS_TEXT, v -> {
                        s.user = v;
                        GammaShareConfig.save(s);
                    });

            // Never show the stored password back, not even masked: a stray edit of a masked field
            // can commit a truncated version of it. Blank clears it.
            EditTextPreference pass = new EditTextPreference(getPrefContext());
            pass.setKey("share_pass");
            pass.setTitle(R.string.network_shares_password);
            pass.setDialogTitle(R.string.network_shares_password);
            pass.setText("");
            pass.setSummary(s.password.isEmpty()
                    ? getString(R.string.network_shares_password_none)
                    : getString(R.string.network_shares_password_set));
            pass.setOnBindEditTextListener(e -> e.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
            pass.setOnPreferenceChangeListener((p, v) -> {
                s.password = (String) v;
                GammaShareConfig.save(s);
                refresh();
                return true;
            });
            screen.addPreference(pass);

            if (GammaShareConfig.TYPE_SMB.equals(s.type)) {
                addText(screen, "share_domain", R.string.network_shares_workgroup, s.domain,
                        InputType.TYPE_CLASS_TEXT, v -> {
                            s.domain = v.trim();
                            GammaShareConfig.save(s);
                        });
            }
        } else {
            Preference note = new Preference(getPrefContext());
            note.setKey("share_nfs_note");
            note.setSelectable(false);
            note.setTitle(R.string.network_shares_access);
            note.setSummary(R.string.network_shares_access_nfs);
            screen.addPreference(note);
        }

        if (s.supportsTls()) {
            // Encryption is its own switch rather than being inferred from the port, because a NAS
            // commonly serves WebDAV over TLS on a port of its own.
            SwitchPreference tls = new SwitchPreference(getPrefContext());
            tls.setKey("share_tls");
            tls.setTitle(GammaShareConfig.TYPE_WEBDAV.equals(s.type)
                    ? R.string.network_shares_https : R.string.network_shares_ftps);
            tls.setSummary(GammaShareConfig.TYPE_WEBDAV.equals(s.type)
                    ? R.string.network_shares_https_summary
                    : R.string.network_shares_ftps_summary);
            tls.setChecked(s.useTls);
            tls.setOnPreferenceChangeListener((p, v) -> {
                s.useTls = (Boolean) v;
                GammaShareConfig.save(s);
                // The scheme is chosen when the connection is made, so a live mount has to be
                // restarted to pick this up.
                if (s.enabled && GammaShareConfig.isMounted(s.name)) {
                    GammaShareConfig.setEnabled(mSlot, false);
                    GammaShareConfig.setEnabled(mSlot, true);
                }
                refresh();
                return true;
            });
            screen.addPreference(tls);
        }

        SwitchPreference ro = new SwitchPreference(getPrefContext());
        ro.setKey("share_ro");
        ro.setTitle(R.string.network_shares_readonly);
        ro.setSummary(R.string.network_shares_readonly_summary);
        ro.setChecked(s.readOnly);
        ro.setOnPreferenceChangeListener((p, v) -> {
            s.readOnly = (Boolean) v;
            GammaShareConfig.save(s);
            // The flag is applied at mount time, so a running share has to be restarted to pick it
            // up. Do that only when it is actually up.
            if (s.enabled && GammaShareConfig.isMounted(s.name)) {
                GammaShareConfig.setEnabled(mSlot, false);
                GammaShareConfig.setEnabled(mSlot, true);
            }
            return true;
        });
        screen.addPreference(ro);

        Preference remove = new Preference(getPrefContext());
        remove.setKey("share_remove");
        remove.setTitle(R.string.network_shares_remove);
        remove.setOnPreferenceClickListener(p -> {
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.network_shares_remove_title, s.name))
                    .setMessage(R.string.network_shares_remove_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.network_shares_remove, (d, w) -> {
                        GammaShareConfig.delete(mSlot);
                        finish();
                    })
                    .show();
            return true;
        });
        screen.addPreference(remove);
    }

    /** What each protocol calls the thing that goes in the path field. */
    private int pathTitle(String type) {
        if (GammaShareConfig.TYPE_SMB.equals(type)) return R.string.network_shares_share_name;
        if (GammaShareConfig.TYPE_NFS.equals(type)) return R.string.network_shares_export_path;
        return R.string.network_shares_remote_folder;
    }

    private String statusSummary(GammaShareConfig.Share s) {
        if (!s.enabled) return getString(R.string.network_shares_state_off);
        if (GammaShareConfig.isMounted(s.name)) {
            return getString(R.string.network_shares_state_at, s.mountPoint());
        }
        return getString(R.string.network_shares_state_connecting_hint);
    }

    private interface Commit {
        void apply(String value);
    }

    private EditTextPreference addText(PreferenceScreen screen, String key, int titleRes,
            String value, int inputType, Commit commit) {
        EditTextPreference p = new EditTextPreference(getPrefContext());
        p.setKey(key);
        p.setTitle(titleRes);
        p.setDialogTitle(titleRes);
        p.setText(value);
        p.setSummary(value == null || value.isEmpty()
                ? getString(R.string.network_shares_unset) : value);
        p.setOnBindEditTextListener(e -> e.setInputType(inputType));
        p.setOnPreferenceChangeListener((pref, v) -> {
            commit.apply((String) v);
            refresh();
            return true;
        });
        screen.addPreference(p);
        return p;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }
}
