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

import android.text.InputType;

import androidx.preference.Preference;

import com.android.internal.gammaos.SyncthingClient.Options;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Daemon options: device name, discovery, relays, bandwidth limits, scan concurrency, free space
 * and usage reporting. Each change is sent at once with {@code setOptions}.
 */
public class SyncthingOptionsFragment extends SyncthingBaseFragment {

    /** A change to a copy of the current options, applied before it is sent. */
    private interface Change {
        void apply(Options o);
    }

    private Options mOptions;
    private String mMyId = "";

    @Override
    void onSnapshot(Snapshot s) {
        if (s == null || !s.apiOk) {
            if (mOptions == null) {
                List<Preference> rows = new ArrayList<>();
                rows.add(info("waiting", getString(R.string.syncthing_waiting), null));
                applyRows(getString(R.string.syncthing_options), rows);
            }
            return;
        }
        mOptions = s.options;
        mMyId = s.myID;
        rebuild();
    }

    private void set(Change change) {
        final Options o = copyOptions(mOptions);
        change.apply(o);
        final String myId = mMyId;
        action(client -> client.setOptions(o, myId));
    }

    private void rebuild() {
        final Options o = mOptions;
        final List<Preference> rows = new ArrayList<>();

        rows.add(text("name", getString(R.string.syncthing_options_device_name), o.deviceName,
                getString(R.string.syncthing_unnamed), InputType.TYPE_CLASS_TEXT,
                v -> set(x -> x.deviceName = v)));
        rows.get(0).setSummary(rows.get(0).getSummary() + "\n"
                + getString(R.string.syncthing_options_device_name_summary));

        rows.add(text("listen", getString(R.string.syncthing_options_listen), join(o.listenAddresses),
                "default", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, v -> {
                    List<String> out = splitList(v);
                    if (out.isEmpty()) out.add("default");
                    set(x -> x.listenAddresses = out);
                }));
        rows.get(1).setSummary(rows.get(1).getSummary() + "\n"
                + getString(R.string.syncthing_options_listen_summary));

        rows.add(toggle("global", getString(R.string.syncthing_options_global_discovery),
                getString(R.string.syncthing_options_global_discovery_summary), o.globalAnnounceEnabled,
                on -> set(x -> x.globalAnnounceEnabled = on)));
        rows.add(toggle("local", getString(R.string.syncthing_options_local_discovery),
                getString(R.string.syncthing_options_local_discovery_summary), o.localAnnounceEnabled,
                on -> set(x -> x.localAnnounceEnabled = on)));
        rows.add(toggle("relays", getString(R.string.syncthing_options_relaying),
                getString(R.string.syncthing_options_relaying_summary), o.relaysEnabled,
                on -> set(x -> x.relaysEnabled = on)));
        rows.add(toggle("nat", getString(R.string.syncthing_options_nat),
                getString(R.string.syncthing_options_nat_summary), o.natEnabled,
                on -> set(x -> x.natEnabled = on)));

        rows.add(text("recv", getString(R.string.syncthing_options_download_limit),
                o.maxRecvKbps > 0 ? Integer.toString(o.maxRecvKbps) : "",
                getString(R.string.syncthing_unlimited), InputType.TYPE_CLASS_NUMBER,
                v -> set(x -> x.maxRecvKbps = parseCount(v))));
        if (o.maxRecvKbps > 0) {
            rows.get(rows.size() - 1).setSummary(getString(R.string.syncthing_kibps, o.maxRecvKbps));
        }
        rows.add(text("send", getString(R.string.syncthing_options_upload_limit),
                o.maxSendKbps > 0 ? Integer.toString(o.maxSendKbps) : "",
                getString(R.string.syncthing_unlimited), InputType.TYPE_CLASS_NUMBER,
                v -> set(x -> x.maxSendKbps = parseCount(v))));
        if (o.maxSendKbps > 0) {
            rows.get(rows.size() - 1).setSummary(getString(R.string.syncthing_kibps, o.maxSendKbps));
        }
        rows.add(toggle("lan_limit", getString(R.string.syncthing_options_lan_limit), null,
                o.limitBandwidthInLan, on -> set(x -> x.limitBandwidthInLan = on)));

        rows.add(text("concurrency", getString(R.string.syncthing_options_concurrency),
                o.maxFolderConcurrency > 0 ? Integer.toString(o.maxFolderConcurrency) : "",
                getString(R.string.syncthing_automatic), InputType.TYPE_CLASS_NUMBER,
                v -> set(x -> x.maxFolderConcurrency = parseCount(v))));
        rows.get(rows.size() - 1).setSummary(rows.get(rows.size() - 1).getSummary() + "\n"
                + getString(R.string.syncthing_options_concurrency_summary));

        rows.add(text("min_free", getString(R.string.syncthing_options_min_free),
                Integer.toString(o.minHomeDiskFreePct), "", InputType.TYPE_CLASS_NUMBER,
                v -> set(x -> x.minHomeDiskFreePct = Math.min(100, parseCount(v)))));
        rows.get(rows.size() - 1).setSummary(o.minHomeDiskFreePct + "%\n"
                + getString(R.string.syncthing_options_min_free_summary));

        rows.add(toggle("ur", getString(R.string.syncthing_options_usage_reporting),
                getString(R.string.syncthing_options_usage_reporting_summary), o.urAccepted,
                on -> set(x -> x.urAccepted = on)));

        applyRows(getString(R.string.syncthing_options), rows);
    }
}
