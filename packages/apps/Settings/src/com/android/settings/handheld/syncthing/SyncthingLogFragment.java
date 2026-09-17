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

import androidx.preference.Preference;

import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The tail of the daemon's log, oldest first, refreshed with every snapshot. The lines are
 * fetched right after each snapshot on the same executor, so the two never race.
 */
public class SyncthingLogFragment extends SyncthingBaseFragment {

    private static final int LINES = 60;

    @Override
    void onSnapshot(Snapshot s) {
        if (s == null || !s.apiOk) {
            List<Preference> rows = new ArrayList<>();
            rows.add(info("unavailable", getString(R.string.syncthing_log_unavailable),
                    s == null || s.error.isEmpty() || "off".equals(s.error) ? null : s.error));
            applyRows(getString(R.string.syncthing_log), rows);
            return;
        }
        background(client -> client.fetchLog(LINES), this::show);
    }

    private void show(List<String> lines) {
        List<Preference> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            rows.add(info("line_" + i, null, lines.get(i)));
        }
        if (rows.isEmpty()) rows.add(info("empty", getString(R.string.syncthing_log_empty), null));
        applyRows(getString(R.string.syncthing_log), rows);
    }
}
