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
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Options;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Common ground for the Syncthing screens.
 *
 * <p>Every screen is a {@link PreferenceScreen} built in code from a {@link Snapshot} of the
 * daemon. While a screen is resumed it fetches a new snapshot every {@link #REFRESH_MS} on the one
 * background executor all screens share, and rebuilds its rows on the main thread. Rows are updated
 * in place when the screen still has the same rows, so the list does not jump or lose its scroll
 * position on every refresh; only when rows appear or disappear is the screen rebuilt.
 *
 * <p>Writes go through {@link #action}: they run on the same executor, report an {@link IOException}
 * in a dialog, and always trigger a fresh fetch afterwards so the screen shows what the daemon
 * really stored. A snapshot fetched before a write was submitted is discarded, so a screen never
 * shows the state from before the user's last change.
 */
abstract class SyncthingBaseFragment extends SettingsPreferenceFragment {

    /** How often a resumed screen refreshes from the daemon. */
    static final long REFRESH_MS = 3000;

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final Handler sMain = new Handler(Looper.getMainLooper());
    /** One client for all screens: it caches the API key. Only ever used on the executor. */
    private static final SyncthingClient sClient = new SyncthingClient();
    /** The most recent snapshot any screen fetched, so a screen opened from another starts filled. */
    private static Snapshot sLastSnapshot;

    private boolean mResumed;
    private boolean mFetching;
    /** Bumped when a write is submitted; a fetch started before that is thrown away. */
    private int mWrites;
    private final Runnable mPoll = this::pollNow;

    /** A blocking call returning a value. */
    interface Io<T> {
        T run(SyncthingClient client) throws IOException;
    }

    /** A blocking call with no result. */
    interface IoAction {
        void run(SyncthingClient client) throws IOException;
    }

    /** Main-thread receiver of a background result. */
    interface Done<T> {
        void done(T value);
    }

    /** Main-thread receiver of a changed row value. */
    interface OnValue<T> {
        void changed(T value);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getPrefContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        mResumed = true;
        // Show what we already know at once; the real refresh follows right behind it.
        if (sLastSnapshot != null) onSnapshot(sLastSnapshot);
        pollNow();
    }

    @Override
    public void onPause() {
        mResumed = false;
        sMain.removeCallbacks(mPoll);
        super.onPause();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    /** Called on the main thread with every snapshot, in order. Build the rows here. */
    abstract void onSnapshot(Snapshot s);

    /** The last snapshot any screen fetched, or null before the first fetch. */
    static Snapshot lastSnapshot() {
        return sLastSnapshot;
    }

    // ---- refresh -----------------------------------------------------------------------------

    /** Fetch a snapshot now (cancelling the scheduled one) and schedule the next. */
    void pollNow() {
        sMain.removeCallbacks(mPoll);
        if (!mResumed || mFetching) return;
        mFetching = true;
        final int writes = mWrites;
        sExecutor.execute(() -> {
            final Snapshot s = loadSnapshot();
            sMain.post(() -> {
                mFetching = false;
                if (!isAdded()) return;
                if (writes != mWrites) {
                    // A write was submitted while this ran; its data predates the change.
                    pollNow();
                    return;
                }
                sLastSnapshot = s;
                if (!mResumed) return;
                onSnapshot(s);
                sMain.postDelayed(mPoll, REFRESH_MS);
            });
        });
    }

    /** Runs on the executor. Never throws: a daemon that does not answer gives apiOk false. */
    private static Snapshot loadSnapshot() {
        if (!SyncthingClient.isEnabled()) {
            Snapshot s = new Snapshot();
            s.error = "off";
            return s;
        }
        Snapshot s;
        try {
            s = sClient.fetchSnapshot();
        } catch (IOException | RuntimeException e) {
            // A RuntimeException here would otherwise die silently inside the executor and leave
            // the screen on "Starting..." for good; report it like a daemon error.
            android.util.Log.w("SyncthingSettings", "snapshot failed", e);
            s = new Snapshot();
            s.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return s;
        }
        // First run: Syncthing names a new device after the hostname, which on Android is
        // "localhost". Give it the product model instead (once; the user can rename it).
        if ("localhost".equals(s.options.deviceName) && !s.myID.isEmpty()
                && !TextUtils.isEmpty(Build.MODEL)) {
            Options o = copyOptions(s.options);
            o.deviceName = Build.MODEL;
            try {
                sClient.setOptions(o, s.myID);
                s.options.deviceName = Build.MODEL;
            } catch (IOException ignored) {
                // The name stays "localhost" until the user sets one.
            }
        }
        return s;
    }

    // ---- background calls --------------------------------------------------------------------

    /** Run a read on the executor and hand its result to the main thread. */
    <T> void background(Io<T> io, Done<T> ok) {
        sExecutor.execute(() -> {
            try {
                final T v = io.run(sClient);
                sMain.post(() -> {
                    if (isAdded()) ok.done(v);
                });
            } catch (IOException e) {
                sMain.post(() -> {
                    if (isAdded()) showError(e);
                });
            }
        });
    }

    /** Run a write on the executor, report failure, then refresh so the rows show the truth. */
    void action(IoAction io, Runnable ok) {
        mWrites++;
        sExecutor.execute(() -> {
            try {
                io.run(sClient);
                sMain.post(() -> {
                    if (!isAdded()) return;
                    if (ok != null) ok.run();
                    pollNow();
                });
            } catch (IOException e) {
                sMain.post(() -> {
                    if (!isAdded()) return;
                    showError(e);
                    pollNow();
                });
            }
        });
    }

    void action(IoAction io) {
        action(io, null);
    }

    // ---- dialogs -----------------------------------------------------------------------------

    void showError(IOException e) {
        showError(e.getMessage() == null ? e.toString() : e.getMessage());
    }

    void showError(CharSequence message) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.syncthing_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    void showInfo(CharSequence title, CharSequence message) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    void confirm(CharSequence title, CharSequence message, CharSequence positive, Runnable onYes) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .show();
    }

    /** A dialog with one text field. The value is handed over trimmed on OK. */
    void promptText(CharSequence title, String prefill, CharSequence hint, int inputType,
            OnValue<String> onOk) {
        if (getContext() == null) return;
        final EditText edit = makeEditText(getContext(), prefill, hint, inputType);
        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setView(pad(getContext(), edit))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> onOk.changed(edit.getText().toString().trim()))
                .show();
    }

    static EditText makeEditText(Context c, String prefill, CharSequence hint, int inputType) {
        EditText edit = new EditText(c);
        edit.setInputType(inputType == 0 ? InputType.TYPE_CLASS_TEXT : inputType);
        edit.setSingleLine(true);
        if (hint != null) edit.setHint(hint);
        if (prefill != null) {
            edit.setText(prefill);
            edit.setSelection(prefill.length());
        }
        return edit;
    }

    /** Dialog padding around a bare view, matching the platform's dialog content inset. */
    static FrameLayout pad(Context c, android.view.View v) {
        FrameLayout box = new FrameLayout(c);
        int side = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24,
                c.getResources().getDisplayMetrics());
        int top = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                c.getResources().getDisplayMetrics());
        box.setPadding(side, top, side, 0);
        box.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    // ---- row builders --------------------------------------------------------------------------
    //
    // Each builder returns a row that is not yet on the screen; applyRows puts a whole list up.
    // Nothing is persisted to SharedPreferences: the daemon is the store.

    Preference info(String key, CharSequence title, CharSequence summary) {
        Preference p = new Preference(getPrefContext());
        p.setKey(key);
        p.setPersistent(false);
        p.setSelectable(false);
        p.setTitle(title);
        p.setSummary(summary);
        return p;
    }

    Preference action(String key, CharSequence title, CharSequence summary, Runnable onClick) {
        Preference p = new Preference(getPrefContext());
        p.setKey(key);
        p.setPersistent(false);
        p.setTitle(title);
        p.setSummary(summary);
        p.setOnPreferenceClickListener(pref -> {
            onClick.run();
            return true;
        });
        return p;
    }

    SwitchPreference toggle(String key, CharSequence title, CharSequence summary, boolean on,
            OnValue<Boolean> onChange) {
        SwitchPreference p = new SwitchPreference(getPrefContext());
        p.setKey(key);
        p.setPersistent(false);
        p.setTitle(title);
        p.setSummary(summary);
        p.setChecked(on);
        p.setOnPreferenceChangeListener((pref, v) -> {
            onChange.changed((Boolean) v);
            return true;
        });
        return p;
    }

    /** A text row. The summary shows the value, or emptySummary when there is none. */
    EditTextPreference text(String key, CharSequence title, String value, CharSequence emptySummary,
            int inputType, OnValue<String> onChange) {
        EditTextPreference p = new EditTextPreference(getPrefContext());
        p.setKey(key);
        p.setPersistent(false);
        p.setTitle(title);
        p.setDialogTitle(title);
        p.setText(value == null ? "" : value);
        p.setSummary(TextUtils.isEmpty(value) ? emptySummary : value);
        p.setOnBindEditTextListener(e -> {
            e.setInputType(inputType == 0 ? InputType.TYPE_CLASS_TEXT : inputType);
            e.setSingleLine(true);
            e.setSelection(e.getText().length());
        });
        p.setOnPreferenceChangeListener((pref, v) -> {
            onChange.changed(((String) v).trim());
            return true;
        });
        return p;
    }

    ListPreference list(String key, CharSequence title, CharSequence[] entries,
            CharSequence[] values, String value, CharSequence summary, OnValue<String> onChange) {
        ListPreference p = new ListPreference(getPrefContext());
        p.setKey(key);
        p.setPersistent(false);
        p.setTitle(title);
        p.setDialogTitle(title);
        p.setEntries(entries);
        p.setEntryValues(values);
        p.setValue(value);
        p.setSummary(summary);
        p.setOnPreferenceChangeListener((pref, v) -> {
            onChange.changed((String) v);
            return true;
        });
        return p;
    }

    MultiSelectListPreference multi(String key, CharSequence title, CharSequence summary,
            CharSequence[] entries, CharSequence[] values, Set<String> selected,
            OnValue<Set<String>> onChange) {
        MultiSelectListPreference p = new MultiSelectListPreference(getPrefContext());
        p.setKey(key);
        p.setPersistent(false);
        p.setTitle(title);
        p.setDialogTitle(title);
        p.setEntries(entries);
        p.setEntryValues(values);
        p.setValues(selected);
        p.setSummary(summary);
        p.setOnPreferenceChangeListener((pref, v) -> {
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) v;
            onChange.changed(set);
            return true;
        });
        return p;
    }

    /**
     * Put the rows on the screen. When the screen already holds rows with the same keys and
     * classes in the same order, they are updated in place (title, summary, state, listeners)
     * so the list keeps its scroll position and open dialogs keep working; otherwise the screen
     * is rebuilt.
     */
    void applyRows(CharSequence title, List<Preference> rows) {
        PreferenceScreen screen = getPreferenceScreen();
        if (title != null) screen.setTitle(title);
        boolean same = screen.getPreferenceCount() == rows.size();
        for (int i = 0; same && i < rows.size(); i++) {
            Preference cur = screen.getPreference(i);
            Preference fresh = rows.get(i);
            same = Objects.equals(cur.getKey(), fresh.getKey()) && cur.getClass() == fresh.getClass();
        }
        if (!same) {
            screen.removeAll();
            for (Preference p : rows) screen.addPreference(p);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            update(screen.getPreference(i), rows.get(i));
        }
    }

    private static void update(Preference cur, Preference fresh) {
        cur.setTitle(fresh.getTitle());
        cur.setSummary(fresh.getSummary());
        cur.setEnabled(fresh.isEnabled());
        cur.setSelectable(fresh.isSelectable());
        cur.setOnPreferenceClickListener(fresh.getOnPreferenceClickListener());
        cur.setOnPreferenceChangeListener(fresh.getOnPreferenceChangeListener());
        if (cur instanceof TwoStatePreference) {
            ((TwoStatePreference) cur).setChecked(((TwoStatePreference) fresh).isChecked());
        } else if (cur instanceof ListPreference) {
            ListPreference a = (ListPreference) cur, b = (ListPreference) fresh;
            a.setEntries(b.getEntries());
            a.setEntryValues(b.getEntryValues());
            a.setValue(b.getValue());
        } else if (cur instanceof MultiSelectListPreference) {
            MultiSelectListPreference a = (MultiSelectListPreference) cur;
            MultiSelectListPreference b = (MultiSelectListPreference) fresh;
            a.setEntries(b.getEntries());
            a.setEntryValues(b.getEntryValues());
            a.setValues(b.getValues());
        } else if (cur instanceof EditTextPreference) {
            ((EditTextPreference) cur).setText(((EditTextPreference) fresh).getText());
        }
    }

    // ---- small helpers shared by the screens --------------------------------------------------

    static Options copyOptions(Options o) {
        Options c = new Options();
        c.deviceName = o.deviceName;
        c.listenAddresses = new ArrayList<>(o.listenAddresses);
        c.globalAnnounceEnabled = o.globalAnnounceEnabled;
        c.localAnnounceEnabled = o.localAnnounceEnabled;
        c.relaysEnabled = o.relaysEnabled;
        c.natEnabled = o.natEnabled;
        c.maxSendKbps = o.maxSendKbps;
        c.maxRecvKbps = o.maxRecvKbps;
        c.limitBandwidthInLan = o.limitBandwidthInLan;
        c.maxFolderConcurrency = o.maxFolderConcurrency;
        c.minHomeDiskFreePct = o.minHomeDiskFreePct;
        c.urAccepted = o.urAccepted;
        c.crashReportingEnabled = o.crashReportingEnabled;
        return c;
    }

    /** Split a comma or space separated list, dropping empty entries. */
    static List<String> splitList(String v) {
        List<String> out = new ArrayList<>();
        if (v == null) return out;
        for (String s : v.split("[,\\s]+")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static String join(List<String> items) {
        return TextUtils.join(", ", items);
    }

    /** A non-negative integer from typed text; anything unreadable is 0. */
    static int parseCount(String v) {
        try {
            return Math.max(0, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Open another Syncthing screen on top of this one. */
    void open(Class<? extends SyncthingBaseFragment> screen, Bundle args, CharSequence title) {
        new com.android.settings.core.SubSettingLauncher(getContext())
                .setDestination(screen.getName())
                .setArguments(args)
                .setTitleText(title)
                .setSourceMetricsCategory(getMetricsCategory())
                .launch();
    }
}
