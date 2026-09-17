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
import android.os.Bundle;
import android.text.InputType;

import androidx.preference.Preference;

import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A folder's ignore patterns (its .stignore), one row per pattern. The list is read once when the
 * screen opens and written back with {@code setIgnores} after every edit; the periodic snapshot
 * only keeps the daemon state warm for the screens underneath.
 */
public class SyncthingIgnoresFragment extends SyncthingBaseFragment {

    static final String ARG_FOLDER_ID = "folder_id";

    private String mFolderId;
    private List<String> mPatterns;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFolderId = getArguments() != null ? getArguments().getString(ARG_FOLDER_ID, "") : "";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPatterns == null) {
            final String id = mFolderId;
            background(client -> client.getIgnores(id), lines -> {
                mPatterns = new ArrayList<>(lines);
                rebuild();
            });
        }
    }

    @Override
    void onSnapshot(Snapshot s) {
        if (mPatterns == null) {
            List<Preference> rows = new ArrayList<>();
            rows.add(info("waiting", getString(R.string.syncthing_waiting), null));
            applyRows(getString(R.string.syncthing_ignores), rows);
        }
    }

    private void rebuild() {
        List<Preference> rows = new ArrayList<>();
        for (int i = 0; i < mPatterns.size(); i++) {
            final int index = i;
            final String pattern = mPatterns.get(i);
            rows.add(action("pattern_" + i, pattern, null, () -> editRow(index, pattern)));
        }
        rows.add(action("add", getString(R.string.syncthing_ignores_add),
                getString(R.string.syncthing_ignores_add_summary),
                () -> promptText(getString(R.string.syncthing_ignores_pattern), "",
                        getString(R.string.syncthing_ignores_pattern_hint), InputType.TYPE_CLASS_TEXT, v -> {
                            if (v.isEmpty()) return;
                            mPatterns.add(v);
                            save();
                        })));
        applyRows(getString(R.string.syncthing_ignores), rows);
    }

    /** Edit / Remove / Cancel for one pattern. */
    private void editRow(int index, String pattern) {
        if (getContext() == null) return;
        CharSequence[] items = { getString(R.string.syncthing_edit), getString(R.string.syncthing_remove),
                getString(android.R.string.cancel) };
        new AlertDialog.Builder(getContext())
                .setTitle(pattern)
                .setItems(items, (d, which) -> {
                    if (index >= mPatterns.size()) return;
                    if (which == 0) {
                        promptText(getString(R.string.syncthing_ignores_pattern), pattern,
                                getString(R.string.syncthing_ignores_pattern_hint), InputType.TYPE_CLASS_TEXT, v -> {
                                    if (index >= mPatterns.size()) return;
                                    if (v.isEmpty()) mPatterns.remove(index); else mPatterns.set(index, v);
                                    save();
                                });
                    } else if (which == 1) {
                        mPatterns.remove(index);
                        save();
                    }
                })
                .show();
    }

    private void save() {
        rebuild();
        final String id = mFolderId;
        final List<String> lines = new ArrayList<>(mPatterns);
        action(client -> client.setIgnores(id, lines));
    }
}
