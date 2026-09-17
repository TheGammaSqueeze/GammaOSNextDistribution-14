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

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.preference.Preference;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Connection;
import com.android.internal.gammaos.SyncthingClient.Device;
import com.android.internal.gammaos.SyncthingClient.Folder;
import com.android.internal.gammaos.SyncthingClient.Gui;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Syncthing root screen: the on/off switch, the daemon's status, this device's identity, and the
 * doors to folders, devices, pending requests, options, the web interface, restart and the log.
 * Mirrors the nano Syncthing root row for row.
 */
public class SyncthingFragment extends SyncthingBaseFragment {

    @Override
    public void onResume() {
        super.onResume();
        // Before the first snapshot arrives the switch and status are still worth showing.
        if (lastSnapshot() == null) onSnapshot(null);
    }

    @Override
    void onSnapshot(Snapshot s) {
        final List<Preference> rows = new ArrayList<>();
        final boolean enabled = SyncthingClient.isEnabled();
        final boolean live = s != null && s.apiOk;

        if (!SyncthingClient.isInstalled()) {
            rows.add(info("not_installed", getString(R.string.syncthing_not_installed),
                    getString(R.string.syncthing_not_installed_summary)));
            applyRows(getString(R.string.syncthing_title), rows);
            return;
        }

        rows.add(toggle("enabled", getString(R.string.syncthing_title),
                getString(R.string.syncthing_enabled_summary), enabled,
                on -> action(client -> SyncthingClient.setEnabled(on))));

        String status;
        String statusDesc = "";
        if (!enabled) {
            status = getString(R.string.syncthing_status_stopped);
        } else if (!live) {
            status = getString(R.string.syncthing_status_starting);
            if (s != null && !s.error.isEmpty() && !"off".equals(s.error)) statusDesc = s.error;
        } else {
            String up = s.uptimeS >= 3600
                    ? getString(R.string.syncthing_uptime_hours, s.uptimeS / 3600, (s.uptimeS % 3600) / 60)
                    : getString(R.string.syncthing_uptime_minutes, s.uptimeS / 60);
            status = getString(R.string.syncthing_status_running, s.version, up);
            int bad = 0;
            for (String l : s.listeners) if (!l.contains(": ok")) bad++;
            if (bad > 0) statusDesc = getString(R.string.syncthing_listeners_failed, bad);
            if (!s.discoveryErrors.isEmpty()) {
                statusDesc += (statusDesc.isEmpty() ? "" : " ")
                        + getString(R.string.syncthing_discovery_error, s.discoveryErrors.get(0));
            }
        }
        rows.add(info("status", getString(R.string.syncthing_status),
                statusDesc.isEmpty() ? status : status + "\n" + statusDesc));

        if (enabled && live) {
            final String name = s.options.deviceName.isEmpty()
                    ? getString(R.string.syncthing_unnamed) : s.options.deviceName;
            final String myId = s.myID;
            rows.add(action("this_device", getString(R.string.syncthing_this_device),
                    name + "\n" + getString(R.string.syncthing_this_device_summary, SyncthingClient.shortId(myId)),
                    () -> showInfo(name, getString(R.string.syncthing_this_device_dialog,
                            SyncthingClient.wrapId(myId)))));

            int active = 0;
            for (Folder f : s.folders) {
                String t = SyncthingClient.folderStateText(s, f);
                if (t.startsWith("Syncing") || t.startsWith("Scanning")) active++;
            }
            int connected = 0;
            for (Device d : s.devices) {
                Connection cn = s.connections.get(d.id);
                if (cn != null && cn.connected) connected++;
            }
            String folders = Integer.toString(s.folders.size());
            if (active > 0) folders += " " + getString(R.string.syncthing_folders_active, active);
            rows.add(action("folders", getString(R.string.syncthing_folders),
                    folders + "\n" + getString(R.string.syncthing_folders_summary),
                    () -> open(SyncthingFoldersFragment.class, null, getString(R.string.syncthing_folders))));
            rows.add(action("devices", getString(R.string.syncthing_devices),
                    getString(R.string.syncthing_devices_count, s.devices.size(), connected)
                            + "\n" + getString(R.string.syncthing_devices_summary),
                    () -> open(SyncthingDevicesFragment.class, null, getString(R.string.syncthing_devices))));
            int pending = s.pendingDevices.size() + s.pendingFolders.size();
            rows.add(action("pending", getString(R.string.syncthing_pending),
                    (pending > 0 ? Integer.toString(pending) : getString(R.string.syncthing_none))
                            + "\n" + getString(R.string.syncthing_pending_summary),
                    () -> open(SyncthingPendingFragment.class, null, getString(R.string.syncthing_pending))));
            rows.add(action("options", getString(R.string.syncthing_options),
                    getString(R.string.syncthing_options_summary),
                    () -> open(SyncthingOptionsFragment.class, null, getString(R.string.syncthing_options))));

            final boolean lan = s.lanGui();
            final Gui gui = s.gui;
            rows.add(action("webgui", getString(R.string.syncthing_webgui),
                    (lan ? getString(R.string.syncthing_on) : getString(R.string.syncthing_off)) + "\n"
                            + (lan ? getString(R.string.syncthing_webgui_on_summary, deviceIp())
                                   : getString(R.string.syncthing_webgui_off_summary)),
                    () -> chooseWebGui(gui, lan)));
            rows.add(action("restart", getString(R.string.syncthing_restart), null,
                    () -> confirm(getString(R.string.syncthing_restart),
                            getString(R.string.syncthing_restart_message),
                            getString(R.string.syncthing_restart_button),
                            () -> action(SyncthingClient::restart))));
            rows.add(action("log", getString(R.string.syncthing_log), null,
                    () -> open(SyncthingLogFragment.class, null, getString(R.string.syncthing_log))));
        }
        applyRows(getString(R.string.syncthing_title), rows);
    }

    /** Off keeps the GUI on loopback; On opens it to the network behind a username and password. */
    private void chooseWebGui(Gui current, boolean lan) {
        if (getContext() == null) return;
        CharSequence[] items = {
                getString(R.string.syncthing_webgui_choice_off),
                getString(R.string.syncthing_webgui_choice_on) };
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.syncthing_webgui)
                .setSingleChoiceItems(items, lan ? 1 : 0, (d, which) -> {
                    d.dismiss();
                    if (which == 0) {
                        action(client -> {
                            Gui g = copyGui(current);
                            g.address = "127.0.0.1:8384";
                            client.setGui(g, null);
                        });
                    } else {
                        askWebGuiCredentials(current);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void askWebGuiCredentials(Gui current) {
        final Context c = getContext();
        if (c == null) return;
        final EditText user = makeEditText(c, current.user.isEmpty() ? "gammaos" : current.user,
                getString(R.string.syncthing_webgui_user), InputType.TYPE_CLASS_TEXT);
        final EditText pass = makeEditText(c, "", getString(R.string.syncthing_webgui_password),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(user);
        box.addView(pass);
        new AlertDialog.Builder(c)
                .setTitle(R.string.syncthing_webgui_choice_on)
                .setMessage(R.string.syncthing_webgui_credentials_message)
                .setView(pad(c, box))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final String u = user.getText().toString().trim();
                    final String p = pass.getText().toString();
                    if (u.isEmpty()) {
                        showError(getString(R.string.syncthing_webgui_user_required));
                        return;
                    }
                    if (p.length() < 4) {
                        showError(getString(R.string.syncthing_webgui_password_short));
                        return;
                    }
                    action(client -> {
                        Gui g = copyGui(current);
                        g.address = "0.0.0.0:8384";
                        g.user = u;
                        client.setGui(g, p);
                        // The GUI listener only rebinds when the daemon restarts.
                        SyncthingClient.requestRestart();
                    });
                })
                .show();
    }

    private static Gui copyGui(Gui g) {
        Gui c = new Gui();
        c.address = g.address;
        c.user = g.user;
        c.passwordSet = g.passwordSet;
        c.useTLS = g.useTLS;
        return c;
    }

    /**
     * The device's first non-loopback IPv4 address, read from the kernel's routing trie the way
     * nano does it, so the web interface row can show a URL the user can type.
     */
    private String deviceIp() {
        String prev = "", found = "";
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/net/fib_trie"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("/32 host LOCAL") && !prev.isEmpty()) {
                    String ip = prev.trim();
                    int sp = ip.lastIndexOf(' ');
                    if (sp >= 0) ip = ip.substring(sp + 1);
                    if (!ip.startsWith("127.") && found.isEmpty()) found = ip;
                }
                prev = line;
            }
        } catch (IOException ignored) {
            // Fall through to the placeholder.
        }
        return found.isEmpty() ? getString(R.string.syncthing_device_ip_placeholder) : found;
    }
}
