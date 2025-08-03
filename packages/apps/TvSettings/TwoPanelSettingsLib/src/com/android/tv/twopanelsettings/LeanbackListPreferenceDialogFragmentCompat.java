/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.twopanelsettings;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;
import androidx.leanback.preference.LeanbackPreferenceDialogFragmentCompat;
import androidx.leanback.widget.VerticalGridView;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.recyclerview.widget.RecyclerView.Adapter;

import com.android.tv.twopanelsettings.R;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A copy-paste from androidx leanback library. Make particular methods to be public so that
 * they can be overrode.
 */
public class LeanbackListPreferenceDialogFragmentCompat extends
        LeanbackPreferenceDialogFragmentCompat {
    private static final String SAVE_STATE_IS_MULTI =
            "LeanbackListPreferenceDialogFragment.isMulti";
    private static final String SAVE_STATE_ENTRIES = "LeanbackListPreferenceDialogFragment.entries";
    private static final String SAVE_STATE_ENTRY_VALUES =
            "LeanbackListPreferenceDialogFragment.entryValues";
    private static final String SAVE_STATE_TITLE = "LeanbackListPreferenceDialogFragment.title";
    private static final String SAVE_STATE_MESSAGE = "LeanbackListPreferenceDialogFragment.message";
    private static final String SAVE_STATE_INITIAL_SELECTIONS =
            "LeanbackListPreferenceDialogFragment.initialSelections";
    private static final String SAVE_STATE_INITIAL_SELECTION =
            "LeanbackListPreferenceDialogFragment.initialSelection";
    private boolean mMulti;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private CharSequence mDialogTitle;
    private CharSequence mDialogMessage;
    Set<String> mInitialSelections;
    private String mInitialSelection;

    public LeanbackListPreferenceDialogFragmentCompat() {
    }

    public static LeanbackListPreferenceDialogFragmentCompat newInstanceSingle(String key) {
        Bundle args = new Bundle(1);
        args.putString("key", key);
        LeanbackListPreferenceDialogFragmentCompat fragment =
                new LeanbackListPreferenceDialogFragmentCompat();
        fragment.setArguments(args);
        return fragment;
    }

    public static LeanbackListPreferenceDialogFragmentCompat newInstanceMulti(String key) {
        Bundle args = new Bundle(1);
        args.putString("key", key);
        LeanbackListPreferenceDialogFragmentCompat fragment =
                new LeanbackListPreferenceDialogFragmentCompat();
        fragment.setArguments(args);
        return fragment;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            DialogPreference preference = this.getPreference();
            this.mDialogTitle = preference.getDialogTitle();
            this.mDialogMessage = preference.getDialogMessage();
            if (preference instanceof ListPreference) {
                this.mMulti = false;
                this.mEntries = ((ListPreference) preference).getEntries();
                this.mEntryValues = ((ListPreference) preference).getEntryValues();
                this.mInitialSelection = ((ListPreference) preference).getValue();
            } else {
                if (!(preference instanceof MultiSelectListPreference)) {
                    throw new IllegalArgumentException(
                            "Preference must be a ListPreference or MultiSelectListPreference");
                }

                this.mMulti = true;
                this.mEntries = ((MultiSelectListPreference) preference).getEntries();
                this.mEntryValues = ((MultiSelectListPreference) preference).getEntryValues();
                this.mInitialSelections = ((MultiSelectListPreference) preference).getValues();
            }
        } else {
            this.mDialogTitle = savedInstanceState.getCharSequence(
                    "LeanbackListPreferenceDialogFragment.title");
            this.mDialogMessage = savedInstanceState.getCharSequence(
                    "LeanbackListPreferenceDialogFragment.message");
            this.mMulti = savedInstanceState.getBoolean(
                    "LeanbackListPreferenceDialogFragment.isMulti");
            this.mEntries = savedInstanceState.getCharSequenceArray(
                    "LeanbackListPreferenceDialogFragment.entries");
            this.mEntryValues = savedInstanceState.getCharSequenceArray(
                    "LeanbackListPreferenceDialogFragment.entryValues");
            if (this.mMulti) {
                String[] initialSelections = savedInstanceState.getStringArray(
                        "LeanbackListPreferenceDialogFragment.initialSelections");
                this.mInitialSelections = new ArraySet(
                        initialSelections != null ? initialSelections.length : 0);
                if (initialSelections != null) {
                    Collections.addAll(this.mInitialSelections, initialSelections);
                }
            } else {
                this.mInitialSelection = savedInstanceState.getString(
                        "LeanbackListPreferenceDialogFragment.initialSelection");
            }
        }

    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence("LeanbackListPreferenceDialogFragment.title", this.mDialogTitle);
        outState.putCharSequence("LeanbackListPreferenceDialogFragment.message",
                this.mDialogMessage);
        outState.putBoolean("LeanbackListPreferenceDialogFragment.isMulti", this.mMulti);
        outState.putCharSequenceArray("LeanbackListPreferenceDialogFragment.entries",
                this.mEntries);
        outState.putCharSequenceArray("LeanbackListPreferenceDialogFragment.entryValues",
                this.mEntryValues);
        if (this.mMulti) {
            outState.putStringArray("LeanbackListPreferenceDialogFragment.initialSelections",
                    (String[]) this.mInitialSelections.toArray(
                            new String[this.mInitialSelections.size()]));
        } else {
            outState.putString("LeanbackListPreferenceDialogFragment.initialSelection",
                    this.mInitialSelection);
        }

    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        TypedValue tv = new TypedValue();
        this.getActivity().getTheme().resolveAttribute(R.attr.preferenceTheme, tv, true);
        int theme = tv.resourceId;
        if (theme == 0) {
            theme = R.style.PreferenceThemeOverlayLeanback;
        }

        Context styledContext = new ContextThemeWrapper(this.getActivity(), theme);
        LayoutInflater styledInflater = inflater.cloneInContext(styledContext);
        View view = styledInflater.inflate(R.layout.leanback_list_preference_fragment, container,
                false);
        VerticalGridView verticalGridView = (VerticalGridView) view.findViewById(16908298);
        verticalGridView.setWindowAlignment(3);
        verticalGridView.setFocusScrollStrategy(0);
        verticalGridView.setAdapter(this.onCreateAdapter());
        CharSequence title = this.mDialogTitle;
        if (!TextUtils.isEmpty(title)) {
            TextView titleView = (TextView) view.findViewById(R.id.decor_title);
            titleView.setText(title);
        }

        CharSequence message = this.mDialogMessage;
        if (!TextUtils.isEmpty(message)) {
            TextView messageView = (TextView) view.findViewById(16908299);
            messageView.setVisibility(0);
            messageView.setText(message);
        }

        return view;
    }

    public Adapter onCreateAdapter() {
        return (Adapter) (this.mMulti ? new LeanbackListPreferenceDialogFragmentCompat.AdapterMulti(
                this.mEntries, this.mEntryValues, this.mInitialSelections)
                : new LeanbackListPreferenceDialogFragmentCompat.AdapterSingle(this.mEntries,
                        this.mEntryValues, this.mInitialSelection));
    }

    public static final class ViewHolder extends
            androidx.recyclerview.widget.RecyclerView.ViewHolder implements OnClickListener {
        private final Checkable mWidgetView;
        private final TextView mTitleView;
        private final ViewGroup mContainer;
        private final LeanbackListPreferenceDialogFragmentCompat.OnItemClickListener mListener;

        public ViewHolder(@NonNull View view,
                @NonNull LeanbackListPreferenceDialogFragmentCompat.OnItemClickListener listener) {
            super(view);
            this.mWidgetView = (Checkable) view.findViewById(R.id.button);
            this.mContainer = (ViewGroup) view.findViewById(R.id.container);
            this.mTitleView = (TextView) view.findViewById(16908310);
            this.mContainer.setOnClickListener(this);
            this.mListener = listener;
        }

        public Checkable getWidgetView() {
            return this.mWidgetView;
        }

        public TextView getTitleView() {
            return this.mTitleView;
        }

        public ViewGroup getContainer() {
            return this.mContainer;
        }

        public void onClick(View v) {
            this.mListener.onItemClick(this);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(LeanbackListPreferenceDialogFragmentCompat.ViewHolder var1);
    }

    final class AdapterMulti extends
            Adapter<LeanbackListPreferenceDialogFragmentCompat.ViewHolder> implements
            LeanbackListPreferenceDialogFragmentCompat.OnItemClickListener {
        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        private final Set<String> mSelections;

        AdapterMulti(CharSequence[] entries, CharSequence[] entryValues,
                Set<String> initialSelections) {
            this.mEntries = entries;
            this.mEntryValues = entryValues;
            this.mSelections = new HashSet(initialSelections);
        }

        public LeanbackListPreferenceDialogFragmentCompat.ViewHolder onCreateViewHolder(
                ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.leanback_list_preference_item_multi, parent, false);
            return new LeanbackListPreferenceDialogFragmentCompat.ViewHolder(view, this);
        }

        public void onBindViewHolder(LeanbackListPreferenceDialogFragmentCompat.ViewHolder holder,
                int position) {
            holder.getWidgetView().setChecked(
                    this.mSelections.contains(this.mEntryValues[position].toString()));
            holder.getTitleView().setText(this.mEntries[position]);
        }

        public int getItemCount() {
            return this.mEntries.length;
        }

        public void onItemClick(LeanbackListPreferenceDialogFragmentCompat.ViewHolder viewHolder) {
            int index = viewHolder.getAdapterPosition();
            if (index != -1) {
                String entry = this.mEntryValues[index].toString();
                if (this.mSelections.contains(entry)) {
                    this.mSelections.remove(entry);
                } else {
                    this.mSelections.add(entry);
                }

                MultiSelectListPreference multiSelectListPreference =
                        (MultiSelectListPreference) LeanbackListPreferenceDialogFragmentCompat.this.getPreference();
                if (multiSelectListPreference.callChangeListener(new HashSet(this.mSelections))) {
                    multiSelectListPreference.setValues(new HashSet(this.mSelections));
                    LeanbackListPreferenceDialogFragmentCompat.this.mInitialSelections =
                            this.mSelections;
                } else if (this.mSelections.contains(entry)) {
                    this.mSelections.remove(entry);
                } else {
                    this.mSelections.add(entry);
                }

                this.notifyDataSetChanged();
            }
        }
    }

    final class AdapterSingle extends
            Adapter<LeanbackListPreferenceDialogFragmentCompat.ViewHolder> implements
            LeanbackListPreferenceDialogFragmentCompat.OnItemClickListener {
        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        private CharSequence mSelectedValue;

        AdapterSingle(CharSequence[] entries, CharSequence[] entryValues,
                CharSequence selectedValue) {
            this.mEntries = entries;
            this.mEntryValues = entryValues;
            this.mSelectedValue = selectedValue;
        }

        public LeanbackListPreferenceDialogFragmentCompat.ViewHolder onCreateViewHolder(
                ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.leanback_list_preference_item_single, parent,
                    false);
            return new LeanbackListPreferenceDialogFragmentCompat.ViewHolder(view, this);
        }

        public void onBindViewHolder(LeanbackListPreferenceDialogFragmentCompat.ViewHolder holder,
                int position) {
            holder.getWidgetView().setChecked(
                    this.mEntryValues[position].equals(this.mSelectedValue));
            holder.getTitleView().setText(this.mEntries[position]);
        }

        public int getItemCount() {
            return this.mEntries.length;
        }

        public void onItemClick(LeanbackListPreferenceDialogFragmentCompat.ViewHolder viewHolder) {
            int index = viewHolder.getAdapterPosition();
            if (index != -1) {
                CharSequence entry = this.mEntryValues[index];
                ListPreference preference =
                        (ListPreference) LeanbackListPreferenceDialogFragmentCompat.this.getPreference();
                if (index >= 0) {
                    String value = this.mEntryValues[index].toString();
                    if (preference.callChangeListener(value)) {
                        preference.setValue(value);
                        this.mSelectedValue = entry;
                    }
                }

                LeanbackListPreferenceDialogFragmentCompat.this.getFragmentManager().popBackStack();
                this.notifyDataSetChanged();
            }
        }
    }
}
