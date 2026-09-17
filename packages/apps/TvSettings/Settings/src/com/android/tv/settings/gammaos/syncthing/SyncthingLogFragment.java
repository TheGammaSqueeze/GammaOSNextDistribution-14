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

import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;

/** The tail of the daemon's log, oldest first, refreshed while the screen is in front. */
@Keep
public class SyncthingLogFragment extends SyncthingBaseFragment {

    private static final int LINES = 60;

    /** What was last shown, so a redraw between fetches does not blank the list. */
    private List<String> mLines = new ArrayList<>();
    private String mError;

    /** Fetches the log rather than a snapshot. */
    @Override
    protected void refresh() {
        SyncthingUi.run(this, c -> c.fetchLog(LINES), lines -> {
            mLines = lines;
            mError = null;
            if (!isResumedHere()) return;
            rerender();
            scheduleTick();
        }, e -> {
            mError = e.getMessage();
            if (!isResumedHere()) return;
            rerender();
            scheduleTick();
        });
    }

    @Override
    protected void rebuild(Snapshot s) {
        getPreferenceScreen().setTitle(R.string.syncthing_log_title);
        if (mError != null && mLines.isEmpty()) {
            info("unavailable", getString(R.string.syncthing_log_unavailable), mError);
            return;
        }
        if (mLines.isEmpty()) {
            info("empty", getString(R.string.syncthing_log_empty), "");
            return;
        }
        for (int i = 0; i < mLines.size(); i++) {
            // "HH:MM:SS  message": the time as the title, the message in the summary so a long
            // line wraps instead of being cut off.
            String line = mLines.get(i);
            int sep = line.indexOf("  ");
            String when = sep > 0 ? line.substring(0, sep) : "";
            String msg = sep > 0 ? line.substring(sep + 2) : line;
            info("line_" + i, when, msg);
        }
    }
}
