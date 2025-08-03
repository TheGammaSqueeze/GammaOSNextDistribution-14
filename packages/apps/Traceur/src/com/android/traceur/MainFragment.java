/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.traceur;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.icu.text.MessageFormat;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.android.settingslib.HelpUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class MainFragment extends PreferenceFragment {

    static final String TAG = TraceUtils.TAG;

    public static final String ACTION_REFRESH_TAGS = "com.android.traceur.REFRESH_TAGS";

    private static final String BETTERBUG_PACKAGE_NAME =
            "com.google.android.apps.internal.betterbug";

    private static final String ROOT_MIME_TYPE = "vnd.android.document/root";
    private static final String STORAGE_URI = "content://com.android.traceur.documents/root";

    private SwitchPreference mTracingOn;
    private SwitchPreference mStackSamplingOn;
    private SwitchPreference mHeapDumpOn;

    private AlertDialog mAlertDialog;
    private SharedPreferences mPrefs;

    private MultiSelectListPreference mTags;
    private MultiSelectListPreference mHeapDumpProcesses;

    private boolean mRefreshing;

    private BroadcastReceiver mRefreshReceiver;

    OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
        new OnSharedPreferenceChangeListener () {
              public void onSharedPreferenceChanged(
                      SharedPreferences sharedPreferences, String key) {
                  refreshUi();
              }
        };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Receiver.updateDeveloperOptionsWatcher(getContext(), /* fromBootIntent */ false);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(
                getActivity().getApplicationContext());

        mTracingOn = (SwitchPreference) findPreference(
                getActivity().getString(R.string.pref_key_tracing_on));
        mStackSamplingOn = (SwitchPreference) findPreference(
                getActivity().getString(R.string.pref_key_stack_sampling_on));
        mHeapDumpOn = (SwitchPreference) findPreference(
                getActivity().getString(R.string.pref_key_heap_dump_on));

        mTracingOn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Receiver.updateTracing(getContext());
                // Disable the stack sampling and heap dump toggles if the trace toggle is enabled.
                mStackSamplingOn.setEnabled(!((SwitchPreference) preference).isChecked());
                mHeapDumpOn.setEnabled(!((SwitchPreference) preference).isChecked());
                return true;
            }
        });

        mStackSamplingOn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Receiver.updateTracing(getContext());
                // Disable the trace and heap dump toggles if the stack sampling toggle is enabled.
                mTracingOn.setEnabled(!((SwitchPreference) preference).isChecked());
                mHeapDumpOn.setEnabled(!((SwitchPreference) preference).isChecked());
                return true;
            }
        });

        mHeapDumpOn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Receiver.updateTracing(getContext());
                // Disable the trace and stack sampling toggles if the heap dump toggle is enabled.
                mTracingOn.setEnabled(!((SwitchPreference) preference).isChecked());
                mStackSamplingOn.setEnabled(!((SwitchPreference) preference).isChecked());
                return true;
            }
        });

        mHeapDumpProcesses = (MultiSelectListPreference) findPreference(
                getContext().getString(R.string.pref_key_heap_dump_processes));

        mTags = (MultiSelectListPreference) findPreference(getContext().getString(R.string.pref_key_tags));
        mTags.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (mRefreshing) {
                    return true;
                }
                Set<String> set = (Set<String>) newValue;
                TreeMap<String, String> available = TraceUtils.listCategories();
                ArrayList<String> clean = new ArrayList<>(set.size());

                for (String s : set) {
                    if (available.containsKey(s)) {
                        clean.add(s);
                    }
                }
                set.clear();
                set.addAll(clean);
                return true;
            }
        });

        findPreference("restore_default_tags").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        refreshUi(/* restoreDefaultTags =*/ true,
                                /* clearHeapDumpProcesses =*/ false);
                        Toast.makeText(getContext(),
                            getContext().getString(R.string.default_categories_restored),
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });

        findPreference("clear_heap_dump_processes").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        refreshUi(/* restoreDefaultTags =*/ false,
                                /* clearHeapDumpProcesses =*/ true);
                        Toast.makeText(getContext(),
                            getContext().getString(R.string.clear_heap_dump_processes_toast),
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });

        findPreference(getString(R.string.pref_key_quick_setting))
            .setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Receiver.updateQuickSettings(getContext());
                        return true;
                    }
                });

        findPreference("clear_saved_files").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        new AlertDialog.Builder(getContext())
                            .setTitle(R.string.clear_saved_files_question)
                            .setMessage(R.string.all_recordings_will_be_deleted)
                            .setPositiveButton(R.string.clear,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        TraceUtils.clearSavedTraces();
                                    }
                                })
                            .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                            .create()
                            .show();
                        return true;
                    }
                });

        findPreference("trace_link_button")
            .setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = buildTraceFileViewIntent();
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            return false;
                        }
                        return true;
                    }
                });

        // This disables "Attach to bugreports" when long traces are enabled. This cannot be done in
        // main.xml because there are some other settings there that are enabled with long traces.
        SwitchPreference attachToBugreport = findPreference(
            getString(R.string.pref_key_attach_to_bugreport));
        findPreference(getString(R.string.pref_key_long_traces))
            .setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (((SwitchPreference) preference).isChecked()) {
                            attachToBugreport.setEnabled(false);
                        } else {
                            attachToBugreport.setEnabled(true);
                        }
                        return true;
                    }
                });

        refreshUi();

        mRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshUi();
            }
        };

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen().getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
        getActivity().registerReceiver(mRefreshReceiver, new IntentFilter(ACTION_REFRESH_TAGS),
                Context.RECEIVER_NOT_EXPORTED);
        Receiver.updateTracing(getContext());
    }

    @Override
    public void onStop() {
        getPreferenceScreen().getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
        getActivity().unregisterReceiver(mRefreshReceiver);

        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }

        super.onStop();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.main);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_url,
            this.getClass().getName());
    }

    private Intent buildTraceFileViewIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(STORAGE_URI), ROOT_MIME_TYPE);
        return intent;
    }

    private void refreshUi() {
        refreshUi(/* restoreDefaultTags =*/ false, /* clearHeapDumpProcesses =*/ false);
    }

    /*
     * Refresh the preferences UI to make sure it reflects the current state of the preferences and
     * system.
     */
    private void refreshUi(boolean restoreDefaultTags, boolean clearHeapDumpProcesses) {
        Context context = getContext();

        // Make sure the Record trace, Record CPU profile, and Record heap dump toggles match their
        // preference values.
        mTracingOn.setChecked(mPrefs.getBoolean(mTracingOn.getKey(), false));
        mStackSamplingOn.setChecked(mPrefs.getBoolean(mStackSamplingOn.getKey(), false));
        mHeapDumpOn.setChecked(mPrefs.getBoolean(mHeapDumpOn.getKey(), false));

        SwitchPreference stopOnReport =
                (SwitchPreference) findPreference(getString(R.string.pref_key_stop_on_bugreport));
        stopOnReport.setChecked(mPrefs.getBoolean(stopOnReport.getKey(), false));

        SwitchPreference continuousHeapDump = (SwitchPreference) findPreference(
                getString(R.string.pref_key_continuous_heap_dump));
        continuousHeapDump.setChecked(mPrefs.getBoolean(continuousHeapDump.getKey(), false));

        // Update category list to match the categories available on the system.
        Set<Entry<String, String>> availableTags = TraceUtils.listCategories().entrySet();
        ArrayList<String> entries = new ArrayList<String>(availableTags.size());
        ArrayList<String> values = new ArrayList<String>(availableTags.size());
        for (Entry<String, String> entry : availableTags) {
            entries.add(entry.getKey() + ": " + entry.getValue());
            values.add(entry.getKey());
        }

        // We keep selected processes in the list in case a user is interested in a process that AM
        // is not yet aware of (e.g. an app that hasn't started up).
        Set<String> runningProcesses = TraceUtils.getRunningAppProcesses(context);
        Set<String> selectedProcesses = mHeapDumpProcesses.getValues();
        runningProcesses.addAll(selectedProcesses);

        List<String> sortedProcesses = new ArrayList<>(runningProcesses);
        Collections.sort(sortedProcesses);

        mRefreshing = true;
        try {
            mTags.setEntries(entries.toArray(new String[0]));
            mTags.setEntryValues(values.toArray(new String[0]));
            if (restoreDefaultTags || !mPrefs.contains(context.getString(R.string.pref_key_tags))) {
                mTags.setValues(Receiver.getDefaultTagList());
            }
            mHeapDumpProcesses.setEntries(sortedProcesses.toArray(new String[0]));
            mHeapDumpProcesses.setEntryValues(sortedProcesses.toArray(new String[0]));
            if (clearHeapDumpProcesses ||
                    !mPrefs.contains(context.getString(R.string.pref_key_heap_dump_processes))) {
                mHeapDumpProcesses.setValues(new HashSet<String>());
            }
        } finally {
            mRefreshing = false;
        }

        // Enable or disable each toggle based on the state of the others. This path exists in case
        // the tracing state was updated with the QS tile or the ongoing-trace notification, which
        // would not call the toggles' OnClickListeners.
        mTracingOn.setEnabled(!(mStackSamplingOn.isChecked() || mHeapDumpOn.isChecked()));
        mStackSamplingOn.setEnabled(!(mTracingOn.isChecked() || mHeapDumpOn.isChecked()));

        // Disallow heap dumps if no process is selected, or if tracing/stack sampling is active.
        boolean heapDumpProcessSelected = mHeapDumpProcesses.getValues().size() > 0;
        mHeapDumpOn.setEnabled(heapDumpProcessSelected &&
                !(mTracingOn.isChecked() || mStackSamplingOn.isChecked()));
        mHeapDumpOn.setSummary(heapDumpProcessSelected
                ? context.getString(R.string.record_heap_dump_summary_enabled)
                : context.getString(R.string.record_heap_dump_summary_disabled));

        // Update subtitles on this screen.
        Set<String> categories = mTags.getValues();
        MessageFormat msgFormat = new MessageFormat(
                getResources().getString(R.string.num_categories_selected),
                Locale.getDefault());
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", categories.size());
        mTags.setSummary(Receiver.getDefaultTagList().equals(categories)
                         ? context.getString(R.string.default_categories)
                         : msgFormat.format(arguments));

        ListPreference bufferSize = (ListPreference)findPreference(
                context.getString(R.string.pref_key_buffer_size));
        bufferSize.setSummary(bufferSize.getEntry());

        ListPreference maxLongTraceSize = (ListPreference)findPreference(
                context.getString(R.string.pref_key_max_long_trace_size));
        maxLongTraceSize.setSummary(maxLongTraceSize.getEntry());

        ListPreference maxLongTraceDuration = (ListPreference)findPreference(
                context.getString(R.string.pref_key_max_long_trace_duration));
        maxLongTraceDuration.setSummary(maxLongTraceDuration.getEntry());

        ListPreference continuousHeapDumpInterval = (ListPreference)findPreference(
                context.getString(R.string.pref_key_continuous_heap_dump_interval));
        continuousHeapDumpInterval.setSummary(continuousHeapDumpInterval.getEntry());

        // Check if BetterBug is installed to see if Traceur should display either the toggle for
        // 'attach_to_bugreport' or 'stop_on_bugreport'.
        try {
            context.getPackageManager().getPackageInfo(BETTERBUG_PACKAGE_NAME,
                    PackageManager.MATCH_SYSTEM_ONLY);
            findPreference(getString(R.string.pref_key_attach_to_bugreport)).setVisible(true);
            findPreference(getString(R.string.pref_key_stop_on_bugreport)).setVisible(false);
            // Changes the long traces summary to add that they cannot be attached to bugreports.
            findPreference(getString(R.string.pref_key_long_traces))
                    .setSummary(getString(R.string.long_traces_summary_betterbug));
        } catch (PackageManager.NameNotFoundException e) {
            // attach_to_bugreport must be disabled here because it's true by default.
            mPrefs.edit().putBoolean(
                    getString(R.string.pref_key_attach_to_bugreport), false).commit();
            findPreference(getString(R.string.pref_key_attach_to_bugreport)).setVisible(false);
            findPreference(getString(R.string.pref_key_stop_on_bugreport)).setVisible(true);
            // Sets long traces summary to the default in case Betterbug was removed.
            findPreference(getString(R.string.pref_key_long_traces))
                    .setSummary(getString(R.string.long_traces_summary));
        }

        // Check if an activity exists to handle the trace_link_button intent. If not, hide the UI
        // element
        PackageManager packageManager = context.getPackageManager();
        Intent intent = buildTraceFileViewIntent();
        if (intent.resolveActivity(packageManager) == null) {
            findPreference("trace_link_button").setVisible(false);
        }
    }
}
