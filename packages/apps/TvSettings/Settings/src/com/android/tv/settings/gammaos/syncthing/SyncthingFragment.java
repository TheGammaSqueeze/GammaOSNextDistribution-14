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

import android.text.InputType;

import androidx.annotation.Keep;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Connection;
import com.android.internal.gammaos.SyncthingClient.Device;
import com.android.internal.gammaos.SyncthingClient.Folder;
import com.android.internal.gammaos.SyncthingClient.Gui;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

/**
 * Syncthing (Settings > Syncthing): the entry screen for the Syncthing daemon shipped with
 * GammaOS. Mirrors the nano menu and the Syncthing web GUI: the on/off switch, the daemon's
 * status, this device's ID for pairing, and the folder, device, pending request, option and log
 * screens beneath it.
 */
@Keep
public class SyncthingFragment extends SyncthingBaseFragment {

    private static final String LOOPBACK_GUI = "127.0.0.1:8384";
    private static final String LAN_GUI = "0.0.0.0:8384";
    private static final int MIN_PASSWORD = 4;

    @Override
    protected void rebuild(Snapshot s) {
        getPreferenceScreen().setTitle(R.string.syncthing_title);

        if (!SyncthingClient.isInstalled()) {
            info("not_installed", getString(R.string.syncthing_not_installed),
                    getString(R.string.syncthing_not_installed_summary));
            return;
        }

        final boolean enabled = SyncthingClient.isEnabled();
        toggle("enabled", getString(R.string.syncthing_title),
                getString(R.string.syncthing_enabled_summary), enabled, on -> {
                    SyncthingClient.setEnabled(on);
                    refresh();
                });

        String status;
        String statusSummary = "";
        if (!enabled) {
            status = getString(R.string.syncthing_status_stopped);
        } else if (!s.apiOk) {
            status = getString(R.string.syncthing_status_starting);
            if (!"off".equals(s.error)) statusSummary = s.error;
        } else {
            long h = s.uptimeS / 3600, m = (s.uptimeS % 3600) / 60;
            String up = h > 0 ? getString(R.string.syncthing_uptime_hours, h, m)
                              : getString(R.string.syncthing_uptime_minutes, m);
            status = getString(R.string.syncthing_status_running, s.version, up);
            int bad = 0;
            for (String l : s.listeners) if (!l.endsWith(": ok")) bad++;
            StringBuilder sb = new StringBuilder();
            if (bad > 0) sb.append(getString(R.string.syncthing_listeners_failed, bad));
            if (!s.discoveryErrors.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(getString(R.string.syncthing_discovery_error, s.discoveryErrors.get(0)));
            }
            statusSummary = sb.toString();
        }
        info("status", getString(R.string.syncthing_status), status + (statusSummary.isEmpty()
                ? "" : "\n" + statusSummary));

        if (!enabled || !s.apiOk) return;

        final String name = SyncthingUi.orDefault(s.options.deviceName,
                getString(R.string.syncthing_unnamed));
        row("this_device", getString(R.string.syncthing_this_device),
                getString(R.string.syncthing_this_device_summary, name, SyncthingClient.shortId(s.myID)),
                () -> SyncthingUi.info(this,
                        s.options.deviceName.isEmpty() ? getString(R.string.syncthing_this_device) : s.options.deviceName,
                        getString(R.string.syncthing_this_device_dialog, SyncthingClient.wrapId(s.myID))));

        int active = 0;
        for (Folder f : s.folders) {
            String t = SyncthingClient.folderStateText(s, f);
            if (t.startsWith("Syncing") || t.startsWith("Scanning")) active++;
        }
        int connected = 0;
        for (Device d : s.devices) {
            Connection c = s.connections.get(d.id);
            if (c != null && c.connected) connected++;
        }
        row("folders", getString(R.string.syncthing_folders),
                active > 0 ? getString(R.string.syncthing_folders_count_active, s.folders.size(), active)
                           : getString(R.string.syncthing_folders_count, s.folders.size()),
                () -> open(new SyncthingFoldersFragment()));
        row("devices", getString(R.string.syncthing_devices),
                getString(R.string.syncthing_devices_count, s.devices.size(), connected),
                () -> open(new SyncthingDevicesFragment()));
        int pending = s.pendingDevices.size() + s.pendingFolders.size();
        row("pending", getString(R.string.syncthing_pending),
                pending > 0 ? Integer.toString(pending) : getString(R.string.syncthing_none),
                () -> open(new SyncthingPendingFragment()));
        row("options", getString(R.string.syncthing_options),
                getString(R.string.syncthing_options_summary),
                () -> open(new SyncthingOptionsFragment()));

        final boolean lanGui = s.lanGui();
        row("webgui", getString(R.string.syncthing_webgui),
                lanGui ? getString(R.string.syncthing_webgui_on_summary, SyncthingUi.deviceIp())
                       : getString(R.string.syncthing_webgui_off_summary),
                () -> chooseWebGui(s));
        row("restart", getString(R.string.syncthing_restart), "", () ->
                SyncthingUi.confirm(this, getString(R.string.syncthing_restart),
                        getString(R.string.syncthing_restart_message),
                        getString(R.string.syncthing_restart_action),
                        () -> SyncthingUi.act(this, SyncthingClient::restart, this::refresh)));
        row("log", getString(R.string.syncthing_log), "", () -> open(new SyncthingLogFragment()));
    }

    /**
     * Off keeps the web GUI on loopback; On opens it to the network, which needs a username and
     * a password first. The GUI listener only rebinds on a restart, so one is requested.
     */
    private void chooseWebGui(Snapshot s) {
        CharSequence[] items = {
                getString(R.string.syncthing_webgui_off),
                getString(R.string.syncthing_webgui_on) };
        SyncthingUi.choose(this, getString(R.string.syncthing_webgui), items, s.lanGui() ? 1 : 0, which -> {
            if (which == 0) {
                Gui g = copy(s.gui);
                g.address = LOOPBACK_GUI;
                SyncthingUi.act(this, c -> c.setGui(g, null), this::refresh);
                return;
            }
            SyncthingUi.askText(this, getString(R.string.syncthing_webgui_user),
                    SyncthingUi.orDefault(s.gui.user, "gammaos"), user -> {
                        if (user.trim().isEmpty()) return;
                        SyncthingUi.askText(this, getString(R.string.syncthing_webgui_password), "",
                                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, pw -> {
                                    if (pw.length() < MIN_PASSWORD) {
                                        SyncthingUi.showError(this, getString(R.string.syncthing_webgui_password_short, MIN_PASSWORD));
                                        return;
                                    }
                                    Gui g = copy(s.gui);
                                    g.address = LAN_GUI;
                                    g.user = user.trim();
                                    SyncthingUi.act(this, c -> {
                                        c.setGui(g, pw);
                                        SyncthingClient.requestRestart();
                                    }, this::refresh);
                                });
                    });
        });
    }

    private static Gui copy(Gui in) {
        Gui g = new Gui();
        g.address = in.address;
        g.user = in.user;
        g.passwordSet = in.passwordSet;
        g.useTLS = in.useTLS;
        return g;
    }
}
