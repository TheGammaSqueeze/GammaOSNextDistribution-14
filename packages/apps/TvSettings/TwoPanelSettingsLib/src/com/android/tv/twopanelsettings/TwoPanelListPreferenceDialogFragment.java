/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.animation.AnimatorInflater;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.leanback.widget.VerticalGridView;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.recyclerview.widget.RecyclerView;


/** A workaround for pi-tv-dev to fix the issue that ListPreference is not correctly handled by two
 * panel lib. When moving to Q, we should fix this problem in androidx(b/139085296).
 */
public class TwoPanelListPreferenceDialogFragment extends
        LeanbackListPreferenceDialogFragmentCompat {
    private static final String SAVE_STATE_IS_MULTI =
            "LeanbackListPreferenceDialogFragment.isMulti";
    private static final String SAVE_STATE_ENTRIES = "LeanbackListPreferenceDialogFragment.entries";
    private static final String SAVE_STATE_ENTRY_VALUES =
            "LeanbackListPreferenceDialogFragment.entryValues";
    private static final String SAVE_STATE_SUMMARIES =
            "LeanbackListPreferenceDialogFragment.summaries";
    private static final String SAVE_STATE_INITIAL_SELECTION =
            "LeanbackListPreferenceDialogFragment.initialSelection";
    private boolean mMultiCopy;
    private CharSequence[] mEntriesCopy;
    private CharSequence[] mEntryValuesCopy;
    private CharSequence[] mSummariesCopy;
    private String mInitialSelectionCopy;

    /** Provide a ListPreferenceDialogFragment which satisfy the use of two panel lib **/
    public static TwoPanelListPreferenceDialogFragment newInstanceSingle(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final TwoPanelListPreferenceDialogFragment
                fragment = new TwoPanelListPreferenceDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            final DialogPreference preference = getPreference();
            if (preference instanceof ListPreference) {
                mMultiCopy = false;
                mEntriesCopy = ((ListPreference) preference).getEntries();
                mEntryValuesCopy = ((ListPreference) preference).getEntryValues();
                if (preference instanceof SummaryListPreference) {
                    mSummariesCopy = ((SummaryListPreference) preference).getSummaries();
                }
                mInitialSelectionCopy = ((ListPreference) preference).getValue();
            } else if (preference instanceof MultiSelectListPreference) {
                mMultiCopy = true;
            } else {
                throw new IllegalArgumentException("Preference must be a ListPreference or "
                    + "MultiSelectListPreference");
            }
        } else {
            mMultiCopy = savedInstanceState.getBoolean(SAVE_STATE_IS_MULTI);
            mEntriesCopy = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRIES);
            mEntryValuesCopy = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRY_VALUES);
            mSummariesCopy = savedInstanceState.getCharSequenceArray(SAVE_STATE_SUMMARIES);
            if (!mMultiCopy) {
                mInitialSelectionCopy = savedInstanceState.getString(SAVE_STATE_INITIAL_SELECTION);
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (view != null) {
            removeAnimationClipping(view);
            ViewGroup mainFrame = view.findViewById(R.id.main_frame);
            if (mainFrame != null) {
                mainFrame.setOutlineProvider(null);
            }
        }

        if (!mMultiCopy) {
            VerticalGridView verticalGridView =
                    (VerticalGridView) getView().findViewById(android.R.id.list);
            // focus to initial value
            if (verticalGridView != null) {
                verticalGridView.setSelectedPosition(getSelectionPositionInSingleSelectionMode());
            }
        }
    }

    /**
     * Return initial selection position in single selection mode. If the list fragment is not in
     * single selection mode or we can not find selected position, the function will return
     * {@link RecyclerView.NO_POSITION}.
     * @return initial selection position.
     */
    private int getSelectionPositionInSingleSelectionMode() {
        // This function is intended for single selection.
        if (mMultiCopy) {
            return RecyclerView.NO_POSITION;
        }
        for (int i = 0; i < mEntryValuesCopy.length; i++) {
            if (mEntryValuesCopy[i].equals(mInitialSelectionCopy)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    protected void removeAnimationClipping(View v) {
        if (v instanceof ViewGroup) {
            ((ViewGroup) v).setClipChildren(false);
            ((ViewGroup) v).setClipToPadding(false);
            for (int index = 0; index < ((ViewGroup) v).getChildCount(); index++) {
                View child = ((ViewGroup) v).getChildAt(index);
                removeAnimationClipping(child);
            }
        }
    }

    @Override
    public RecyclerView.Adapter onCreateAdapter() {
        if (!mMultiCopy) {
            return new TwoPanelAdapterSingle(mEntriesCopy, mEntryValuesCopy, mSummariesCopy,
                    mInitialSelectionCopy);
        }
        return super.onCreateAdapter();
    }

    private class TwoPanelAdapterSingle extends RecyclerView.Adapter<ViewHolder>
            implements OnItemClickListener  {
        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        private final CharSequence[] mSummaries;
        private CharSequence mSelectedValue;

        TwoPanelAdapterSingle(CharSequence[] entries, CharSequence[] entryValues,
                CharSequence[] summaries, CharSequence selectedValue) {
            mEntries = entries;
            mEntryValues = entryValues;
            mSummaries = summaries;
            mSelectedValue = selectedValue;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            final View view = inflater.inflate(R.layout.leanback_list_preference_item_single,
                    parent, false);
            return new ViewHolder(view, this);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.getWidgetView().setChecked(mEntryValues[position].equals(mSelectedValue));
            holder.getTitleView().setText(mEntries[position]);
            holder.itemView.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                    getContext(), R.animator.preference));
            TextView summaryView = (TextView) holder.getContainer()
                    .findViewById(android.R.id.summary);
            if (summaryView != null) {
                if (mSummaries != null && !TextUtils.isEmpty(mSummaries[position])) {
                    summaryView.setText(mSummaries[position]);
                    summaryView.setVisibility(View.VISIBLE);
                } else {
                    summaryView.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return mEntries.length;
        }

        @Override
        public void onItemClick(ViewHolder viewHolder) {
            final int index = viewHolder.getAdapterPosition();
            if (index == RecyclerView.NO_POSITION) {
                return;
            }
            final CharSequence entry = mEntryValues[index];
            final ListPreference preference = (ListPreference) getPreference();
            if (index >= 0) {
                String value = mEntryValues[index].toString();
                if (preference.callChangeListener(value)) {
                    preference.setValue(value);
                    mSelectedValue = entry;
                }
            }

            if (getParentFragment() instanceof TwoPanelSettingsFragment) {
                TwoPanelSettingsFragment parent = (TwoPanelSettingsFragment) getParentFragment();
                parent.navigateBack();
            }
            notifyDataSetChanged();
        }
    }
}
