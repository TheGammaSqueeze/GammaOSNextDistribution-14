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

import com.android.internal.gammaos.SyncthingClient.Options;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The daemon's options: this device's name, listeners, discovery, relays, NAT traversal,
 * bandwidth limits, scan concurrency, free space floor and usage reporting. Every change is sent
 * at once.
 */
@Keep
public class SyncthingOptionsFragment extends SyncthingBaseFragment {

    @Override
    protected void rebuild(Snapshot s) {
        getPreferenceScreen().setTitle(R.string.syncthing_options);
        final Options o = s.options;

        row("name", getString(R.string.syncthing_opt_device_name),
                SyncthingUi.orDefault(o.deviceName, getString(R.string.syncthing_unnamed)) + "\n"
                        + getString(R.string.syncthing_opt_device_name_summary), () ->
                SyncthingUi.askText(this, getString(R.string.syncthing_opt_device_name), o.deviceName,
                        v -> change(s, x -> x.deviceName = v.trim())));
        row("listen", getString(R.string.syncthing_opt_listen),
                (o.listenAddresses.isEmpty() ? "default" : SyncthingUi.join(o.listenAddresses)) + "\n"
                        + getString(R.string.syncthing_opt_listen_summary), () ->
                SyncthingUi.askText(this, getString(R.string.syncthing_opt_listen_prompt),
                        SyncthingUi.join(o.listenAddresses), v -> change(s, x -> {
                            List<String> out = SyncthingUi.split(v);
                            if (out.isEmpty()) out.add("default");
                            x.listenAddresses = out;
                        })));
        toggle("global", getString(R.string.syncthing_opt_global_discovery),
                getString(R.string.syncthing_opt_global_discovery_summary), o.globalAnnounceEnabled,
                on -> change(s, x -> x.globalAnnounceEnabled = on));
        toggle("local", getString(R.string.syncthing_opt_local_discovery),
                getString(R.string.syncthing_opt_local_discovery_summary), o.localAnnounceEnabled,
                on -> change(s, x -> x.localAnnounceEnabled = on));
        toggle("relays", getString(R.string.syncthing_opt_relaying),
                getString(R.string.syncthing_opt_relaying_summary), o.relaysEnabled,
                on -> change(s, x -> x.relaysEnabled = on));
        toggle("nat", getString(R.string.syncthing_opt_nat),
                getString(R.string.syncthing_opt_nat_summary), o.natEnabled,
                on -> change(s, x -> x.natEnabled = on));
        row("recv", getString(R.string.syncthing_opt_download_limit), limitText(o.maxRecvKbps), () ->
                SyncthingUi.askNumber(this, getString(R.string.syncthing_opt_download_limit_prompt), o.maxRecvKbps,
                        n -> change(s, x -> x.maxRecvKbps = n)));
        row("send", getString(R.string.syncthing_opt_upload_limit), limitText(o.maxSendKbps), () ->
                SyncthingUi.askNumber(this, getString(R.string.syncthing_opt_upload_limit_prompt), o.maxSendKbps,
                        n -> change(s, x -> x.maxSendKbps = n)));
        toggle("lan_limit", getString(R.string.syncthing_opt_lan_limit), "", o.limitBandwidthInLan,
                on -> change(s, x -> x.limitBandwidthInLan = on));
        row("concurrency", getString(R.string.syncthing_opt_concurrency),
                (o.maxFolderConcurrency > 0 ? Integer.toString(o.maxFolderConcurrency)
                        : getString(R.string.syncthing_automatic)) + "\n"
                        + getString(R.string.syncthing_opt_concurrency_summary), () ->
                SyncthingUi.askNumber(this, getString(R.string.syncthing_opt_concurrency_prompt), o.maxFolderConcurrency,
                        n -> change(s, x -> x.maxFolderConcurrency = n)));
        row("min_free", getString(R.string.syncthing_opt_min_free),
                o.minHomeDiskFreePct + "%\n" + getString(R.string.syncthing_opt_min_free_summary), () ->
                SyncthingUi.askNumber(this, getString(R.string.syncthing_opt_min_free_prompt), o.minHomeDiskFreePct,
                        n -> change(s, x -> x.minHomeDiskFreePct = Math.min(100, n))));
        toggle("ur", getString(R.string.syncthing_opt_usage_reporting),
                getString(R.string.syncthing_opt_usage_reporting_summary), o.urAccepted,
                on -> change(s, x -> x.urAccepted = on));
    }

    private String limitText(int kbps) {
        return kbps > 0 ? getString(R.string.syncthing_kibs, kbps) : getString(R.string.syncthing_unlimited);
    }

    /** Applies one edit to a copy of the options and sends the whole set. */
    private void change(Snapshot s, Consumer<Options> edit) {
        final Options o = copy(s.options);
        edit.accept(o);
        final String myId = s.myID;
        SyncthingUi.act(this, c -> c.setOptions(o, myId), this::refresh);
    }

    private static Options copy(Options in) {
        Options o = new Options();
        o.deviceName = in.deviceName;
        o.listenAddresses = new ArrayList<>(in.listenAddresses);
        o.globalAnnounceEnabled = in.globalAnnounceEnabled;
        o.localAnnounceEnabled = in.localAnnounceEnabled;
        o.relaysEnabled = in.relaysEnabled;
        o.natEnabled = in.natEnabled;
        o.maxSendKbps = in.maxSendKbps;
        o.maxRecvKbps = in.maxRecvKbps;
        o.limitBandwidthInLan = in.limitBandwidthInLan;
        o.maxFolderConcurrency = in.maxFolderConcurrency;
        o.minHomeDiskFreePct = in.minHomeDiskFreePct;
        o.urAccepted = in.urAccepted;
        o.crashReportingEnabled = in.crashReportingEnabled;
        return o;
    }
}
