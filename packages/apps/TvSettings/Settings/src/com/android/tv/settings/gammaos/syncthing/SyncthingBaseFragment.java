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

import android.app.tvsettings.TvSettingsEnums;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What every Syncthing screen shares: a snapshot fetched on the background thread when the screen
 * resumes and every few seconds after that, and rows rebuilt in place from it.
 *
 * <p>Rows are matched by key across rebuilds, so a refresh updates the text of the row the user
 * is on rather than replacing the list under the focus. A subclass builds its screen in
 * {@link #rebuild(Snapshot)} with {@link #row}, {@link #info} and {@link #toggle}; rows it does
 * not mention that time are removed.
 */
abstract class SyncthingBaseFragment extends SettingsPreferenceFragment {

    /** How often a screen refreshes while it is in front. */
    protected static final long REFRESH_MS = 3000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = this::refresh;
    private boolean mResumed;

    /** The snapshot the rows were last built from, or null before the first one. */
    private Snapshot mSnapshot;

    private final Set<String> mKept = new HashSet<>();
    private int mOrder;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        mResumed = true;
        // Show the last known state at once; the fetch that follows corrects it.
        Snapshot s = SyncthingUi.lastSnapshot();
        if (s != null) render(s);
        refresh();
    }

    @Override
    public void onPause() {
        mResumed = false;
        mHandler.removeCallbacks(mTick);
        super.onPause();
    }

    protected boolean isResumedHere() { return mResumed; }

    /** Queues the next periodic refresh; called once a refresh has delivered. */
    protected void scheduleTick() {
        mHandler.removeCallbacks(mTick);
        if (mResumed) mHandler.postDelayed(mTick, REFRESH_MS);
    }

    /**
     * Fetches a snapshot now (after a change, or on the timer) and rebuilds the rows from it.
     * Screens that show something other than the snapshot override this.
     */
    protected void refresh() {
        mHandler.removeCallbacks(mTick);
        SyncthingUi.run(this, SyncthingUi::loadSnapshot, s -> {
            SyncthingUi.setLastSnapshot(s);
            if (!mResumed) return;
            render(s);
            scheduleTick();
        }, e -> scheduleTick());
    }

    /** Rebuilds from the snapshot already shown, for edits that change nothing on the daemon. */
    protected void rerender() {
        render(mSnapshot != null ? mSnapshot : new Snapshot());
    }

    protected Snapshot snapshot() { return mSnapshot; }

    private void render(Snapshot s) {
        mSnapshot = s;
        beginRows();
        rebuild(s);
        endRows();
    }

    /** Builds (or updates) the rows for this snapshot. */
    protected abstract void rebuild(Snapshot s);

    // ---- rows ----------------------------------------------------------------------------------

    protected void beginRows() {
        mKept.clear();
        mOrder = 0;
    }

    protected void endRows() {
        PreferenceScreen screen = getPreferenceScreen();
        for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
            Preference p = screen.getPreference(i);
            if (!mKept.contains(p.getKey())) screen.removePreference(p);
        }
    }

    /** The preference with this key if it is of the wanted class, else a fresh one in its place. */
    @SuppressWarnings("unchecked")
    private <T extends Preference> T reuse(String key, Class<T> cls) {
        PreferenceScreen screen = getPreferenceScreen();
        Preference existing = screen.findPreference(key);
        T p;
        if (existing != null && existing.getClass() == cls) {
            p = (T) existing;
        } else {
            if (existing != null) screen.removePreference(existing);
            if (cls == SwitchPreference.class) p = (T) new SwitchPreference(getContext());
            else p = (T) new Preference(getContext());
            p.setKey(key);
            p.setPersistent(false);
            screen.addPreference(p);
        }
        p.setOrder(mOrder++);
        mKept.add(key);
        return p;
    }

    /** A selectable row. */
    protected Preference row(String key, CharSequence title, CharSequence summary,
            Runnable onClick) {
        Preference p = reuse(key, Preference.class);
        p.setTitle(title);
        p.setSummary(summary);
        p.setSelectable(true);
        p.setOnPreferenceClickListener(pref -> {
            onClick.run();
            return true;
        });
        return p;
    }

    /** A row that only shows something. */
    protected Preference info(String key, CharSequence title, CharSequence summary) {
        Preference p = reuse(key, Preference.class);
        p.setTitle(title);
        p.setSummary(summary);
        p.setSelectable(false);
        p.setOnPreferenceClickListener(null);
        return p;
    }

    /** An on/off row; onChange receives the new value. */
    protected SwitchPreference toggle(String key, CharSequence title, CharSequence summary,
            boolean on, Consumer<Boolean> onChange) {
        SwitchPreference p = reuse(key, SwitchPreference.class);
        p.setTitle(title);
        p.setSummary(summary);
        p.setChecked(on);
        p.setOnPreferenceChangeListener((pref, v) -> {
            onChange.accept((Boolean) v);
            return true;
        });
        return p;
    }

    // ---- navigation ----------------------------------------------------------------------------

    protected void open(Fragment f) {
        if (getFragmentManager() == null) return;
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, f)
                .addToBackStack(null)
                .commit();
    }

    protected void popBack() {
        if (getFragmentManager() != null) getFragmentManager().popBackStack();
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.PAGE_CLASSIC_DEFAULT;
    }
}
