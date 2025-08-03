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

package com.android.tv.twopanelsettings.slices;

import static android.app.slice.Slice.EXTRA_TOGGLE_STATE;
import static android.app.slice.Slice.HINT_PARTIAL;
import static com.android.tv.twopanelsettings.slices.InstrumentationUtils.logEntrySelected;
import static com.android.tv.twopanelsettings.slices.InstrumentationUtils.logToggleInteracted;
import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_PREFERENCE_INFO_STATUS;
import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_PREFERENCE_KEY;
import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_SLICE_FOLLOWUP;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.tvsettings.TvSettingsEnums;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.IntentSender;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.widget.ListContent;
import androidx.slice.widget.SliceContent;

import com.android.tv.twopanelsettings.R;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment.SliceFragmentCallback;
import com.android.tv.twopanelsettings.slices.PreferenceSliceLiveData.SliceLiveDataImpl;
import com.android.tv.twopanelsettings.slices.SlicePreferencesUtil.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A screen presenting a slice in TV settings.
 */
@Keep
public class SliceFragment extends SettingsPreferenceFragment implements Observer<Slice>,
        SliceFragmentCallback {
    private static final int SLICE_REQUEST_CODE = 10000;
    private static final String TAG = "SliceFragment";
    private static final String KEY_PREFERENCE_FOLLOWUP_INTENT = "key_preference_followup_intent";
    private static final String KEY_PREFERENCE_FOLLOWUP_RESULT_CODE =
            "key_preference_followup_result_code";
    private static final String KEY_SCREEN_TITLE = "key_screen_title";
    private static final String KEY_SCREEN_SUBTITLE = "key_screen_subtitle";
    private static final String KEY_SCREEN_ICON = "key_screen_icon";
    private static final String KEY_LAST_PREFERENCE = "key_last_preference";
    private static final String KEY_URI_STRING = "key_uri_string";
    private ListContent mListContent;
    private Slice mSlice;
    private ContextThemeWrapper mContextThemeWrapper;
    private String mUriString = null;
    private int mCurrentPageId;
    private CharSequence mScreenTitle;
    private CharSequence mScreenSubtitle;
    private Icon mScreenIcon;
    private PendingIntent mPreferenceFollowupIntent;
    private int mFollowupPendingIntentResultCode;
    private Intent mFollowupPendingIntentExtras;
    private Intent mFollowupPendingIntentExtrasCopy;
    private String mLastFocusedPreferenceKey;
    private boolean mIsMainPanelReady = true;

    private final Handler mHandler = new Handler();
    private final ActivityResultLauncher<IntentSenderRequest> mActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            mFollowupPendingIntentExtras = data;
                            mFollowupPendingIntentExtrasCopy = data == null ? null : new Intent(
                                    data);
                            mFollowupPendingIntentResultCode = result.getResultCode();
                        }
                    });
    private final ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            handleUri(uri);
            super.onChange(selfChange, uri);
        }
    };

    /** Callback for one panel settings fragment **/
    public interface OnePanelSliceFragmentContainer {
        void navigateBack();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mUriString = getArguments().getString(SlicesConstants.TAG_TARGET_URI);
        if (!TextUtils.isEmpty(mUriString)) {
            ContextSingleton.getInstance().grantFullAccess(getContext(), Uri.parse(mUriString));
        }
        if (TextUtils.isEmpty(mScreenTitle)) {
            mScreenTitle = getArguments().getCharSequence(SlicesConstants.TAG_SCREEN_TITLE, "");
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        this.setTitle(mScreenTitle);
        this.setSubtitle(mScreenSubtitle);
        this.setIcon(mScreenIcon);

        showProgressBar();
        if (!TextUtils.isEmpty(mUriString)) {
            getSliceLiveData().observeForever(this);
        }
        if (TextUtils.isEmpty(mScreenTitle)) {
            mScreenTitle = getArguments().getCharSequence(SlicesConstants.TAG_SCREEN_TITLE, "");
        }
        super.onResume();
        if (!TextUtils.isEmpty(mUriString)) {
            getContext().getContentResolver().registerContentObserver(
                    SlicePreferencesUtil.getStatusPath(mUriString), false, mContentObserver);
        }
        fireFollowupPendingIntent();
    }

    private SliceLiveDataImpl getSliceLiveData() {
        return ContextSingleton.getInstance()
                .getSliceLiveData(getActivity(), Uri.parse(mUriString));
    }

    private void fireFollowupPendingIntent() {
        if (mFollowupPendingIntentExtras == null) {
            return;
        }
        // If there is followup pendingIntent returned from initial activity, send it.
        // Otherwise send the followup pendingIntent provided by slice api.
        Parcelable followupPendingIntent;
        try {
            followupPendingIntent = mFollowupPendingIntentExtrasCopy.getParcelableExtra(
                    EXTRA_SLICE_FOLLOWUP);
        } catch (Throwable ex) {
            // unable to parse, the Intent has custom Parcelable, fallback
            followupPendingIntent = null;
        }
        if (followupPendingIntent instanceof PendingIntent) {
            try {
                ((PendingIntent) followupPendingIntent).send();
            } catch (CanceledException e) {
                Log.e(TAG, "Followup PendingIntent for slice cannot be sent", e);
            }
        } else {
            if (mPreferenceFollowupIntent == null) {
                return;
            }
            try {
                mPreferenceFollowupIntent.send(getContext(),
                        mFollowupPendingIntentResultCode, mFollowupPendingIntentExtras);
            } catch (CanceledException e) {
                Log.e(TAG, "Followup PendingIntent for slice cannot be sent", e);
            }
            mPreferenceFollowupIntent = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        hideProgressBar();
        getContext().getContentResolver().unregisterContentObserver(mContentObserver);
        getSliceLiveData().removeObserver(this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen preferenceScreen = getPreferenceManager()
                .createPreferenceScreen(getContext());
        setPreferenceScreen(preferenceScreen);

        TypedValue themeTypedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.preferenceTheme, themeTypedValue, true);
        mContextThemeWrapper = new ContextThemeWrapper(getActivity(), themeTypedValue.resourceId);

    }

    private boolean isUriValid(String uri) {
        if (uri == null) {
            return false;
        }
        ContentProviderClient client =
                getContext().getContentResolver().acquireContentProviderClient(Uri.parse(uri));
        if (client != null) {
            client.close();
            return true;
        } else {
            return false;
        }
    }

    private void update() {
        mListContent = new ListContent(mSlice);
        PreferenceScreen preferenceScreen =
                getPreferenceManager().getPreferenceScreen();

        if (preferenceScreen == null) {
            return;
        }

        List<SliceContent> items = mListContent.getRowItems();
        if (items == null || items.size() == 0) {
            return;
        }

        SliceItem redirectSliceItem = SlicePreferencesUtil.getRedirectSlice(items);
        String redirectSlice = null;
        if (redirectSliceItem != null) {
            Data data = SlicePreferencesUtil.extract(redirectSliceItem);
            CharSequence title = SlicePreferencesUtil.getText(data.mTitleItem);
            if (!TextUtils.isEmpty(title)) {
                redirectSlice = title.toString();
            }
        }
        if (isUriValid(redirectSlice)) {
            getSliceLiveData().removeObserver(this);
            getContext().getContentResolver().unregisterContentObserver(mContentObserver);
            mUriString = redirectSlice;
            getSliceLiveData().observeForever(this);
            getContext().getContentResolver().registerContentObserver(
                    SlicePreferencesUtil.getStatusPath(mUriString), false, mContentObserver);
        }

        SliceItem screenTitleItem = SlicePreferencesUtil.getScreenTitleItem(items);
        if (screenTitleItem == null) {
            setTitle(mScreenTitle);
        } else {
            Data data = SlicePreferencesUtil.extract(screenTitleItem);
            mCurrentPageId = SlicePreferencesUtil.getPageId(screenTitleItem);
            CharSequence title = SlicePreferencesUtil.getText(data.mTitleItem);
            if (!TextUtils.isEmpty(title)) {
                setTitle(title);
                mScreenTitle = title;
            } else {
                setTitle(mScreenTitle);
            }

            CharSequence subtitle = SlicePreferencesUtil.getText(data.mSubtitleItem);
            setSubtitle(subtitle);

            Icon icon = SlicePreferencesUtil.getIcon(data.mStartItem);
            setIcon(icon);
        }

        SliceItem focusedPrefItem = SlicePreferencesUtil.getFocusedPreferenceItem(items);
        CharSequence defaultFocusedKey = null;
        if (focusedPrefItem != null) {
            Data data = SlicePreferencesUtil.extract(focusedPrefItem);
            CharSequence title = SlicePreferencesUtil.getText(data.mTitleItem);
            if (!TextUtils.isEmpty(title)) {
                defaultFocusedKey = title;
            }
        }

        List<Preference> newPrefs = new ArrayList<>();
        for (SliceContent contentItem : items) {
            SliceItem item = contentItem.getSliceItem();
            if (SlicesConstants.TYPE_PREFERENCE.equals(item.getSubType())
                    || SlicesConstants.TYPE_PREFERENCE_CATEGORY.equals(item.getSubType())
                    || SlicesConstants.TYPE_PREFERENCE_EMBEDDED_PLACEHOLDER.equals(
                            item.getSubType())) {
                Preference preference =
                        SlicePreferencesUtil.getPreference(
                                item, mContextThemeWrapper, getClass().getCanonicalName(),
                                getParentFragment() instanceof TwoPanelSettingsFragment);
                if (preference != null) {
                    newPrefs.add(preference);
                }
            }
        }
        updatePreferenceScreen(preferenceScreen, newPrefs);
        if (defaultFocusedKey != null) {
            scrollToPreference(defaultFocusedKey.toString());
        } else if (mLastFocusedPreferenceKey != null) {
            scrollToPreference(mLastFocusedPreferenceKey);
        }

        if (getParentFragment() instanceof TwoPanelSettingsFragment) {
            ((TwoPanelSettingsFragment) getParentFragment()).refocusPreference(this);
        }
        mIsMainPanelReady = true;
    }

    private void back() {
        if (getCallbackFragment() instanceof TwoPanelSettingsFragment) {
            TwoPanelSettingsFragment parentFragment =
                    (TwoPanelSettingsFragment) getCallbackFragment();
            if (parentFragment.isFragmentInTheMainPanel(this)) {
                parentFragment.navigateBack();
            }
        } else if (getCallbackFragment() instanceof OnePanelSliceFragmentContainer) {
            ((OnePanelSliceFragmentContainer) getCallbackFragment()).navigateBack();
        }
    }

    private void forward() {
        if (mIsMainPanelReady) {
            if (getCallbackFragment() instanceof TwoPanelSettingsFragment) {
                TwoPanelSettingsFragment parentFragment =
                        (TwoPanelSettingsFragment) getCallbackFragment();
                Preference chosenPreference = TwoPanelSettingsFragment.getChosenPreference(this);
                if (chosenPreference == null && mLastFocusedPreferenceKey != null) {
                    chosenPreference = findPreference(mLastFocusedPreferenceKey);
                }
                if (chosenPreference != null && chosenPreference instanceof HasSliceUri
                        && ((HasSliceUri) chosenPreference).getUri() != null) {
                    chosenPreference.setFragment(SliceFragment.class.getCanonicalName());
                    parentFragment.refocusPreferenceForceRefresh(chosenPreference, this);
                }
                if (parentFragment.isFragmentInTheMainPanel(this)) {
                    parentFragment.navigateToPreviewFragment();
                }
            }
        } else {
            mHandler.post(() -> forward());
        }
    }

    private void updatePreferenceScreen(PreferenceScreen screen, List<Preference> newPrefs) {
        // Remove all the preferences in the screen that satisfy such three cases:
        // (a) Preference without key
        // (b) Preference with key which does not appear in the new list.
        // (c) Preference with key which does appear in the new list, but the preference has changed
        // ability to handle slices and needs to be replaced instead of re-used.
        int index = 0;
        IdentityHashMap<Preference, Preference> newToOld = new IdentityHashMap<>();
        while (index < screen.getPreferenceCount()) {
            boolean needToRemoveCurrentPref = true;
            Preference oldPref = screen.getPreference(index);
            for (Preference newPref : newPrefs) {
                if (isSamePreference(oldPref, newPref)) {
                    needToRemoveCurrentPref = false;
                    newToOld.put(newPref, oldPref);
                    break;
                }
            }

            if (needToRemoveCurrentPref) {
                screen.removePreference(oldPref);
            } else {
                index++;
            }
        }

        Map<Integer, Boolean> twoStatePreferenceIsCheckedByOrder = new HashMap<>();
        for (int i = 0; i < newPrefs.size(); i++) {
            if (newPrefs.get(i) instanceof TwoStatePreference) {
                twoStatePreferenceIsCheckedByOrder.put(
                        i, ((TwoStatePreference) newPrefs.get(i)).isChecked());
            }
        }

        //Iterate the new preferences list and give each preference a correct order
        for (int i = 0; i < newPrefs.size(); i++) {
            Preference newPref = newPrefs.get(i);
            // If the newPref has a key and has a corresponding old preference, update the old
            // preference and give it a new order.

            Preference oldPref = newToOld.get(newPref);
            if (oldPref == null) {
                newPref.setOrder(i);
                screen.addPreference(newPref);
                continue;
            }

            oldPref.setOrder(i);
            if (oldPref instanceof EmbeddedSlicePreference) {
                // EmbeddedSlicePreference has its own slice observer
                // (EmbeddedSlicePreferenceHelper). Should therefore not be updated by
                // slice observer in SliceFragment.
                // The order will however still need to be updated, as this can not be handled
                // by EmbeddedSlicePreferenceHelper.
                continue;
            }

            oldPref.setIcon(newPref.getIcon());
            oldPref.setTitle(newPref.getTitle());
            oldPref.setSummary(newPref.getSummary());
            oldPref.setEnabled(newPref.isEnabled());
            oldPref.setSelectable(newPref.isSelectable());
            oldPref.setFragment(newPref.getFragment());
            oldPref.getExtras().putAll(newPref.getExtras());
            if ((oldPref instanceof HasSliceAction)
                    && (newPref instanceof HasSliceAction)) {
                ((HasSliceAction) oldPref)
                        .setSliceAction(
                                ((HasSliceAction) newPref).getSliceAction());
            }
            if ((oldPref instanceof HasSliceUri)
                    && (newPref instanceof HasSliceUri)) {
                ((HasSliceUri) oldPref)
                        .setUri(((HasSliceUri) newPref).getUri());
            }
            if ((oldPref instanceof HasCustomContentDescription)
                    && (newPref instanceof HasCustomContentDescription)) {
                ((HasCustomContentDescription) oldPref).setContentDescription(
                        ((HasCustomContentDescription) newPref)
                                .getContentDescription());
            }
        }

        //addPreference will reset the checked status of TwoStatePreference.
        //So we need to add them back
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference screenPref = screen.getPreference(i);
            if (screenPref instanceof TwoStatePreference
                    && twoStatePreferenceIsCheckedByOrder.get(screenPref.getOrder()) != null) {
                ((TwoStatePreference) screenPref)
                        .setChecked(twoStatePreferenceIsCheckedByOrder.get(screenPref.getOrder()));
            }
        }
        removeAnimationClipping(getView());
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

    private static boolean isSamePreference(Preference oldPref, Preference newPref) {
        if (oldPref == null || newPref == null) {
            return false;
        }

        if (newPref instanceof HasSliceUri != oldPref instanceof HasSliceUri) {
            return false;
        }

        if (newPref instanceof EmbeddedSlicePreference) {
            return oldPref instanceof EmbeddedSlicePreference
                    && Objects.equals(((EmbeddedSlicePreference) newPref).getUri(),
                    ((EmbeddedSlicePreference) oldPref).getUri());
        }

        return newPref.getKey() != null && newPref.getKey().equals(oldPref.getKey());
    }

    @Override
    public void onPreferenceFocused(Preference preference) {
        setLastFocused(preference);
    }

    @Override
    public void onSeekbarPreferenceChanged(SliceSeekbarPreference preference, int addValue) {
        int curValue = preference.getValue();
        if((addValue > 0 && curValue < preference.getMax()) ||
           (addValue < 0 && curValue > preference.getMin())) {
            preference.setValue(curValue + addValue);

            try {
                Intent fillInIntent =
                        new Intent()
                                .putExtra(EXTRA_PREFERENCE_KEY, preference.getKey());
                firePendingIntent((HasSliceAction) preference, fillInIntent);
            } catch (Exception e) {
                Log.e(TAG, "PendingIntent for slice cannot be sent", e);
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof SliceRadioPreference) {
            SliceRadioPreference radioPref = (SliceRadioPreference) preference;
            if (!radioPref.isChecked()) {
                radioPref.setChecked(true);
                if (TextUtils.isEmpty(radioPref.getUri())) {
                    return true;
                }
            }

            logEntrySelected(getPreferenceActionId(preference));
            Intent fillInIntent = new Intent().putExtra(EXTRA_PREFERENCE_KEY, preference.getKey());

            boolean result = firePendingIntent(radioPref, fillInIntent);
            radioPref.clearOtherRadioPreferences(getPreferenceScreen());
            if (result) {
                return true;
            }
        } else if (preference instanceof TwoStatePreference
                && preference instanceof HasSliceAction) {
            boolean isChecked = ((TwoStatePreference) preference).isChecked();
            preference.getExtras().putBoolean(EXTRA_PREFERENCE_INFO_STATUS, isChecked);
            if (getParentFragment() instanceof TwoPanelSettingsFragment) {
                ((TwoPanelSettingsFragment) getParentFragment()).refocusPreference(this);
            }
            logToggleInteracted(getPreferenceActionId(preference), isChecked);
            Intent fillInIntent =
                    new Intent()
                            .putExtra(EXTRA_TOGGLE_STATE, isChecked)
                            .putExtra(EXTRA_PREFERENCE_KEY, preference.getKey());
            if (firePendingIntent((HasSliceAction) preference, fillInIntent)) {
                return true;
            }
            return true;
        } else if (preference instanceof SlicePreference) {
            // In this case, we may intentionally ignore this entry selection to avoid double
            // logging as the action should result in a PAGE_FOCUSED event being logged.
            if (getPreferenceActionId(preference) != TvSettingsEnums.ENTRY_DEFAULT) {
                logEntrySelected(getPreferenceActionId(preference));
            }
            Intent fillInIntent =
                    new Intent().putExtra(EXTRA_PREFERENCE_KEY, preference.getKey());
            if (firePendingIntent((HasSliceAction) preference, fillInIntent)) {
                return true;
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    private boolean firePendingIntent(@NonNull HasSliceAction preference, Intent fillInIntent) {
        if (preference.getSliceAction() == null) {
            return false;
        }
        IntentSender intentSender = preference.getSliceAction().getAction().getIntentSender();
        mActivityResultLauncher.launch(
                new IntentSenderRequest.Builder(intentSender).setFillInIntent(
                        fillInIntent).build());
        if (preference.getFollowupSliceAction() != null) {
            mPreferenceFollowupIntent = preference.getFollowupSliceAction().getAction();
        }

        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_PREFERENCE_FOLLOWUP_INTENT, mPreferenceFollowupIntent);
        outState.putInt(KEY_PREFERENCE_FOLLOWUP_RESULT_CODE, mFollowupPendingIntentResultCode);
        outState.putCharSequence(KEY_SCREEN_TITLE, mScreenTitle);
        outState.putCharSequence(KEY_SCREEN_SUBTITLE, mScreenSubtitle);
        outState.putParcelable(KEY_SCREEN_ICON, mScreenIcon);
        outState.putString(KEY_LAST_PREFERENCE, mLastFocusedPreferenceKey);
        outState.putString(KEY_URI_STRING, mUriString);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mPreferenceFollowupIntent =
                    savedInstanceState.getParcelable(KEY_PREFERENCE_FOLLOWUP_INTENT);
            mFollowupPendingIntentResultCode =
                    savedInstanceState.getInt(KEY_PREFERENCE_FOLLOWUP_RESULT_CODE);
            mScreenTitle = savedInstanceState.getCharSequence(KEY_SCREEN_TITLE);
            mScreenSubtitle = savedInstanceState.getCharSequence(KEY_SCREEN_SUBTITLE);
            mScreenIcon = savedInstanceState.getParcelable(KEY_SCREEN_ICON);
            mLastFocusedPreferenceKey = savedInstanceState.getString(KEY_LAST_PREFERENCE);
            mUriString = savedInstanceState.getString(KEY_URI_STRING);
        }
    }

    @Override
    public void onChanged(@NonNull Slice slice) {
        mSlice = slice;
        // Make TvSettings guard against the case that slice provider is not set up correctly
        if (slice == null || slice.getHints() == null) {
            return;
        }

        if (slice.getHints().contains(HINT_PARTIAL)) {
            showProgressBar();
        } else {
            hideProgressBar();
        }
        mIsMainPanelReady = false;
        update();
    }

    private void showProgressBar() {
        View view = this.getView();
        View progressBar = view == null ? null : getView().findViewById(R.id.progress_bar);
        if (progressBar != null) {
            progressBar.bringToFront();
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgressBar() {
        View view = this.getView();
        View progressBar = view == null ? null : getView().findViewById(R.id.progress_bar);
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void setSubtitle(CharSequence subtitle) {
        View view = this.getView();
        TextView decorSubtitle = view == null
                ? null
                : (TextView) view.findViewById(R.id.decor_subtitle);
        if (decorSubtitle != null) {
            // This is to remedy some complicated RTL scenario such as Hebrew RTL Account slice with
            // English account name subtitle.
            if (getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_RTL) {
                decorSubtitle.setGravity(Gravity.TOP | Gravity.RIGHT);
            }
            if (TextUtils.isEmpty(subtitle)) {
                decorSubtitle.setVisibility(View.GONE);
            } else {
                decorSubtitle.setVisibility(View.VISIBLE);
                decorSubtitle.setText(subtitle);
            }
        }
        mScreenSubtitle = subtitle;
    }

    private void setIcon(Icon icon) {
        View view = this.getView();
        ImageView decorIcon = view == null ? null : (ImageView) view.findViewById(R.id.decor_icon);
        if (decorIcon != null && icon != null) {
            TextView decorTitle = view.findViewById(R.id.decor_title);
            if (decorTitle != null) {
                decorTitle.setMaxWidth(
                        getResources().getDimensionPixelSize(R.dimen.decor_title_width));
            }
            decorIcon.setImageDrawable(icon.loadDrawable(mContextThemeWrapper));
            decorIcon.setVisibility(View.VISIBLE);
        } else if (decorIcon != null) {
            decorIcon.setVisibility(View.GONE);
        }
        mScreenIcon = icon;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup view =
                (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        LayoutInflater themedInflater = LayoutInflater.from(view.getContext());
        final View newTitleContainer = themedInflater.inflate(
                R.layout.slice_title_container, container, false);
        view.removeView(view.findViewById(R.id.decor_title_container));
        view.addView(newTitleContainer, 0);

        if (newTitleContainer != null) {
            newTitleContainer.setOutlineProvider(null);
            newTitleContainer.setBackgroundResource(R.color.tp_preference_panel_background_color);
        }

        final View newContainer =
                themedInflater.inflate(R.layout.slice_progress_bar, container, false);
        if (newContainer != null) {
            ((ViewGroup) newContainer).addView(view);
        }
        return newContainer;
    }

    public void setLastFocused(Preference preference) {
        mLastFocusedPreferenceKey = preference.getKey();
    }

    private void handleUri(Uri uri) {
        String uriString = uri.getQueryParameter(SlicesConstants.PARAMETER_URI);
        String errorMessage = uri.getQueryParameter(SlicesConstants.PARAMETER_ERROR);
        // Display the errorMessage based upon two different scenarios:
        // a) If the provided uri string matches with current page slice uri(usually happens
        // when the data fails to correctly load), show the errors in the current panel using
        // InfoFragment UI.
        // b) If the provided uri string does not match with current page slice uri(usually happens
        // when the data fails to save), show the error message as the toast.
        if (uriString != null && errorMessage != null) {
            if (!uriString.equals(mUriString)) {
                showErrorMessageAsToast(errorMessage);
            } else {
                showErrorMessage(errorMessage);
            }
        }
        // Provider should provide the correct slice uri in the parameter if it wants to do certain
        // action(includes go back, forward), otherwise TvSettings would ignore it.
        if (uriString == null || !uriString.equals(mUriString)) {
            return;
        }
        String direction = uri.getQueryParameter(SlicesConstants.PARAMETER_DIRECTION);
        if (direction != null) {
            if (direction.equals(SlicesConstants.FORWARD)) {
                forward();
            } else if (direction.equals(SlicesConstants.BACKWARD)) {
                back();
            } else if (direction.equals(SlicesConstants.EXIT)) {
                finish();
            }
        }
    }

    private void showErrorMessageAsToast(String errorMessage) {
        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
    }

    private void finish() {
        getActivity().setResult(Activity.RESULT_OK);
        getActivity().finish();
    }

    private void showErrorMessage(String errorMessage) {
        if (getCallbackFragment() instanceof TwoPanelSettingsFragment) {
            ((TwoPanelSettingsFragment) getCallbackFragment()).showErrorMessage(errorMessage, this);
        }
    }

    private int getPreferenceActionId(Preference preference) {
        if (preference instanceof HasSliceAction) {
            return ((HasSliceAction) preference).getActionId() != 0
                    ? ((HasSliceAction) preference).getActionId()
                    : TvSettingsEnums.ENTRY_DEFAULT;
        }
        return TvSettingsEnums.ENTRY_DEFAULT;
    }

    public CharSequence getScreenTitle() {
        return mScreenTitle;
    }

    @Override
    protected int getPageId() {
        return mCurrentPageId != 0 ? mCurrentPageId : TvSettingsEnums.PAGE_SLICE_DEFAULT;
    }

    @Deprecated
    public int getMetricsCategory() {
        return 0;
    }
}
