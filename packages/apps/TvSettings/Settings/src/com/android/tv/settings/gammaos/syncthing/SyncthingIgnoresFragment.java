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

import androidx.annotation.Keep;

import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A folder's ignore patterns, one row each. The list is read once when the screen opens (it is
 * the user's own edit in progress, not daemon state that moves on its own) and written back after
 * every change.
 */
@Keep
public class SyncthingIgnoresFragment extends SyncthingBaseFragment {

    private static final String ARG_FOLDER = "folder";

    private String mFolderId = "";
    private List<String> mPatterns = new ArrayList<>();
    private boolean mLoaded;

    public static SyncthingIgnoresFragment forFolder(String folderId) {
        SyncthingIgnoresFragment f = new SyncthingIgnoresFragment();
        Bundle b = new Bundle();
        b.putString(ARG_FOLDER, folderId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        String id = getArguments() != null ? getArguments().getString(ARG_FOLDER, "") : "";
        mFolderId = id == null ? "" : id;
    }

    /** Reads the patterns once; later calls just redraw the list being edited. */
    @Override
    protected void refresh() {
        if (mLoaded) {
            rerender();
            return;
        }
        final String id = mFolderId;
        SyncthingUi.run(this, c -> c.getIgnores(id), lines -> {
            mPatterns = new ArrayList<>(lines);
            mLoaded = true;
            rerender();
        }, e -> {
            SyncthingUi.showError(this, getString(R.string.syncthing_ignores_read_failed, e.getMessage()));
            popBack();
        });
    }

    @Override
    protected void rebuild(Snapshot s) {
        getPreferenceScreen().setTitle(R.string.syncthing_ignores);
        if (!mLoaded) return;
        for (int i = 0; i < mPatterns.size(); i++) {
            final int idx = i;
            final String pattern = mPatterns.get(i);
            row("pattern_" + i, pattern, "", () -> {
                CharSequence[] items = {
                        getString(R.string.syncthing_ignores_edit),
                        getString(R.string.syncthing_remove_action),
                        getString(android.R.string.cancel) };
                SyncthingUi.choose(this, pattern, items, 0, which -> {
                    if (idx >= mPatterns.size()) return;
                    if (which == 0) {
                        SyncthingUi.askText(this, getString(R.string.syncthing_ignores_pattern), mPatterns.get(idx), v -> {
                            if (idx >= mPatterns.size()) return;
                            String t = v.trim();
                            if (t.isEmpty()) mPatterns.remove(idx); else mPatterns.set(idx, t);
                            save();
                        });
                    } else if (which == 1) {
                        mPatterns.remove(idx);
                        save();
                    }
                });
            });
        }
        row("add", getString(R.string.syncthing_ignores_add),
                getString(R.string.syncthing_ignores_add_summary), () ->
                SyncthingUi.askText(this, getString(R.string.syncthing_ignores_pattern), "", v -> {
                    String t = v.trim();
                    if (t.isEmpty()) return;
                    mPatterns.add(t);
                    save();
                }));
    }

    private void save() {
        rerender();
        final String id = mFolderId;
        final List<String> lines = new ArrayList<>(mPatterns);
        SyncthingUi.act(this, c -> c.setIgnores(id, lines), null);
    }
}
