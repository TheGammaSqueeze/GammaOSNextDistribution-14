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

package com.android.tv.settings.library;

import android.annotation.SystemApi;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 *  @hide
 *  Hold the data of a Settings Preference.
 */
@SystemApi
public class PreferenceCompat {
    public static final byte TYPE_PREFERENCE = 0;
    public static final byte TYPE_PREFERENCE_CATEGORY = 1;
    public static final byte TYPE_PREFERENCE_ACCESS_POINT = 2;
    public static final byte TYPE_PREFERENCE_COLLAPSE_CATEGORY = 3;
    public static final byte TYPE_LIST = 4;
    public static final byte TYPE_SWITCH = 5;
    public static final byte TYPE_RADIO = 6;
    public static final byte TYPE_DIALOG = 7;

    public static final byte STATUS_UNASSIGNED = 0;
    public static final byte STATUS_OFF = 1;
    public static final byte STATUS_ON = 2;

    private final String[] mKey;
    private String mTitle;
    private String mSummary;
    private String mContentDescription;
    private Bundle mExtras;
    private Intent mIntent;
    private Drawable mIcon;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private int mValueIndex;
    private String mValue;
    private boolean mHasSlice;
    private String mSliceUri;
    private String mMessage;
    private String mNeutralButtonText;
    private String mNegativeButtonText;
    private String mPositiveButtonText;

    // 0 : preference, 1 : preferenceCategory, 2 : AccessPointPreference
    private byte mType;

    // 0 : not updated, 1 : unchecked, 2 : checked
    private byte mChecked;

    // 0 : not updated, 1 : invisible, 2: visible
    private byte mVisible;

    // 0: not updated, 1 :not selectable, 2: selectable
    private byte mSelectable;

    // 0: not updated, 1 :not selectable, 2: selectable
    private byte mEnabled;

    // 0: not updated, 1 :not focused, 2: focused
    private boolean mIsFocused;

    private boolean mShouldRemove;

    // Indicate whether there is on preference change listener
    private boolean mHasOnPreferenceChangeListener;

    // Indicates whether the preference is a restricted preference.
    private boolean mIsRestricted;

    // Indicates whether the preference is disabled by admin.
    private boolean mIsDisabledByAdmin;

    private byte mPersistent;

    private String mRadioGroup;

    // Next state of the current state, -1 to indicate there is no next state.
    private Integer mNextState;

    private List<PreferenceCompat> mChildPrefCompats;

    /** @hide */
    @SystemApi
    public void setChildPrefCompats(
            List<PreferenceCompat> childPrefCompats) {
        this.mChildPrefCompats = childPrefCompats;
    }

    /** @hide */
    @SystemApi
    public List<PreferenceCompat> getChildPrefCompats() {
        if (mChildPrefCompats == null) {
            mChildPrefCompats = new ArrayList<>();
        }
        return mChildPrefCompats;
    }

    /** @hide */
    @SystemApi
    public int getChildPrefsCount() {
        return mChildPrefCompats == null ? 0 : mChildPrefCompats.size();
    }

    /** @hide */
    @SystemApi
    public void clearChildPrefCompats() {
        mChildPrefCompats = new ArrayList<>();
    }

    /** @hide */
    @SystemApi
    public PreferenceCompat findChildPreferenceCompat(String[] prefKey) {
        if (prefKey == null || prefKey.length != this.mKey.length + 1) {
            return null;
        }
        if (IntStream.range(0, mKey.length).anyMatch(i -> !(mKey[i].equals(prefKey[i])))) {
            return null;
        }
        if (mChildPrefCompats != null) {
            return mChildPrefCompats.stream()
                    .filter(preferenceParcelable ->
                            preferenceParcelable.getKey()[preferenceParcelable.getKey().length - 1]
                                    .equals(prefKey[prefKey.length - 1]))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /** @hide */
    @SystemApi
    public PreferenceCompat(String key) {
        this.mKey = new String[]{key};
    }

    /** @hide */
    @SystemApi
    public PreferenceCompat(String[] key) {
        this.mKey = key;
    }

    /** @hide */
    @SystemApi
    public PreferenceCompat(String[] key, String title) {
        this.mKey = key;
        this.mTitle = title;
    }

    /** @hide */
    @SystemApi
    public PreferenceCompat(String[] key, String title, String summary) {
        this(key, title);
        this.mSummary = summary;
    }

    /** @hide */
    @SystemApi
    public String[] getKey() {
        return mKey;
    }

    /** @hide */
    @SystemApi
    public String getTitle() {
        return mTitle;
    }

    /** @hide */
    @SystemApi
    public void setTitle(String title) {
        this.mTitle = title;
    }

    /** @hide */
    @SystemApi
    public String getSummary() {
        return mSummary;
    }

    /** @hide */
    @SystemApi
    public void setSummary(String summary) {
        this.mSummary = summary;
    }

    /** @hide */
    @SystemApi
    public Drawable getIcon() {
        return mIcon;
    }

    /** @hide */
    @SystemApi
    public void setIcon(Drawable icon) {
        this.mIcon = icon;
    }

    /** @hide */
    @SystemApi
    public String getContentDescription() {
        return mContentDescription;
    }

    /** @hide */
    @SystemApi
    public String getMessage() {
        return mMessage;
    }

    /** @hide */
    @SystemApi
    public void setMessage(String message) {
        this.mMessage = message;
    }

    /** @hide */
    @SystemApi
    public String getNeutralButtonText() {
        return mNeutralButtonText;
    }

    /** @hide */
    @SystemApi
    public void setNeutralButtonText(String neutralButtonText) {
        this.mNeutralButtonText = neutralButtonText;
    }

    /** @hide */
    @SystemApi
    public String getNegativeButtonText() {
        return mNegativeButtonText;
    }

    /** @hide */
    @SystemApi
    public void setNegativeButtonText(String negativeButtonText) {
        this.mNegativeButtonText = negativeButtonText;
    }

    /** @hide */
    @SystemApi
    public String getPositiveButtonText() {
        return mPositiveButtonText;
    }

    /** @hide */
    @SystemApi
    public void setPositiveButtonText(String positiveButtonText) {
        this.mPositiveButtonText = positiveButtonText;
    }

    /** @hide */
    @SystemApi
    public int getType() {
        return mType;
    }

    /** @hide */
    @SystemApi
    public void setType(byte type) {
        this.mType = type;
    }

    /** @hide */
    @SystemApi
    public CharSequence[] getEntries() {
        return mEntries;
    }

    /** @hide */
    @SystemApi
    public void setEntries(CharSequence[] entries) {
        mEntries = entries;
    }

    /** @hide */
    @SystemApi
    public CharSequence[] getEntryValues() {
        return mEntryValues;
    }

    /** @hide */
    @SystemApi
    public void setEntryValues(CharSequence[] entryValues) {
        mEntryValues = entryValues;
    }

    /** @hide */
    @SystemApi
    public int getValueIndex() {
        return mValueIndex;
    }

    /** @hide */
    @SystemApi
    public void setValueIndex(int valueIndex) {
        mValueIndex = valueIndex;
    }

    /** @hide */
    @SystemApi
    public String getValue() {
        return mValue;
    }

    /** @hide */
    @SystemApi
    public void setValue(String value) {
        mValue = value;
    }

    /** @hide */
    @SystemApi
    public boolean hasSlice() {
        return mHasSlice;
    }

    /** @hide */
    @SystemApi
    public void setHasSlice(boolean hasSlice) {
        mHasSlice = hasSlice;
    }

    /** @hide */
    @SystemApi
    public String getSliceUri() {
        return mSliceUri;
    }

    /** @hide */
    @SystemApi
    public void setSliceUri(String sliceUri) {
        mSliceUri = sliceUri;
    }

    /** @hide */
    @SystemApi
    public boolean shouldRemove() {
        return mShouldRemove;
    }

    /** @hide */
    @SystemApi
    public void setShouldRemove(boolean shouldRemove) {
        mShouldRemove = shouldRemove;
    }

    /** @hide */
    @SystemApi
    public boolean hasOnPreferenceChangeListener() {
        return mHasOnPreferenceChangeListener;
    }

    /** @hide */
    @SystemApi
    public void setHasOnPreferenceChangeListener(boolean hasOnPreferenceChangeListener) {
        mHasOnPreferenceChangeListener = hasOnPreferenceChangeListener;
    }

    /** @hide */
    @SystemApi
    public Integer getNextState() {
        return mNextState;
    }

    /** @hide */
    @SystemApi
    public void setNextState(Integer nextState) {
        mNextState = nextState;
    }

    /** @hide */
    @SystemApi
    public void setContentDescription(String contentDescription) {
        this.mContentDescription = contentDescription;
    }

    /** @hide */
    @SystemApi
    public byte getChecked() {
        return mChecked;
    }

    /** @hide */
    @SystemApi
    public void setChecked(byte checked) {
        this.mChecked = checked;
    }

    /** @hide */
    @SystemApi
    public void setChecked(boolean checked) {
        setChecked(ManagerUtil.getChecked(checked));
    }

    /** @hide */
    @SystemApi
    public void setVisible(boolean visible) {
        setVisible(ManagerUtil.getVisible(visible));
    }

    /** @hide */
    @SystemApi
    public byte getVisible() {
        return mVisible;
    }

    /** @hide */
    @SystemApi
    public void setVisible(byte visible) {
        this.mVisible = visible;
    }

    /** @hide */
    @SystemApi
    public byte getSelectable() {
        return mSelectable;
    }

    /** @hide */
    @SystemApi
    public void setSelectable(byte selectable) {
        this.mSelectable = selectable;
    }

    /** @hide */
    @SystemApi
    public void setSelectable(boolean selectable) {
        this.mSelectable = ManagerUtil.getSelectable(selectable);
    }

    /** @hide */
    @SystemApi
    public byte getEnabled() {
        return mEnabled;
    }

    /** @hide */
    @SystemApi
    public void setEnabled(byte enabled) {
        this.mEnabled = enabled;
    }

    /** @hide */
    @SystemApi
    public boolean isRestricted() {
        return mIsRestricted;
    }

    /** @hide */
    @SystemApi
    public void setRestricted(boolean restricted) {
        mIsRestricted = restricted;
    }

    /** @hide */
    @SystemApi
    public boolean isDisabledByAdmin() {
        return mIsDisabledByAdmin;
    }

    /** @hide */
    @SystemApi
    public void setDisabledByAdmin(boolean disabledByAdmin) {
        mIsDisabledByAdmin = disabledByAdmin;
    }

    /** @hide */
    @SystemApi
    public byte getPersistent() {
        return mPersistent;
    }

    /** @hide */
    @SystemApi
    public void setPersistent(byte persistent) {
        mPersistent = persistent;
    }

    /** @hide */
    @SystemApi
    public void setPersistent(boolean persistent) {
        setPersistent(ManagerUtil.getPersistent(persistent));
    }

    /** @hide */
    @SystemApi
    public String getRadioGroup() {
        return mRadioGroup;
    }

    /** @hide */
    @SystemApi
    public void setRadioGroup(String radioGroup) {
        mRadioGroup = radioGroup;
    }

    /** @hide */
    @SystemApi
    public void setEnabled(boolean enabled) {
        setEnabled(ManagerUtil.getEnabled(enabled));
    }

    /** @hide */
    @SystemApi
    public boolean isFocused() {
        return mIsFocused;
    }

    /** @hide */
    @SystemApi
    public void setFocused(boolean focused) {
        mIsFocused = focused;
    }

    /** @hide */
    @SystemApi
    public Bundle getExtras() {
        return mExtras;
    }

    /** @hide */
    @SystemApi
    public void setExtras(Bundle extras) {
        this.mExtras = extras;
    }

    /** @hide */
    @SystemApi
    public void addInfo(String key, Object value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putObject(key, value);
    }

    /** @hide */
    @SystemApi
    public Object getInfo(String key) {
        if (mExtras != null) {
            return mExtras.get(key);
        }
        return null;
    }

    /** @hide */
    @SystemApi
    public void getInfo(String key, int value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putInt(key, value);
    }

    /** @hide */
    @SystemApi
    public Intent getIntent() {
        return mIntent;
    }

    /** @hide */
    @SystemApi
    public void setIntent(Intent intent) {
        this.mIntent = intent;
    }

    /** @hide */
    @SystemApi
    public void initChildPreferences() {
        mChildPrefCompats = new ArrayList<>();
    }

    /** @hide */
    @SystemApi
    public void addChildPrefCompat(PreferenceCompat childPrefCompat) {
        if (mChildPrefCompats == null) {
            mChildPrefCompats = new ArrayList<>();
        }
        mChildPrefCompats.add(childPrefCompat);
    }

    @Override
    public String toString() {
        return "PreferenceCompat{"
                + "mKey=" + Arrays.toString(mKey)
                + ", mTitle='" + mTitle + '\''
                + ", mSummary='" + mSummary + '\''
                + ", mContentDescription='" + mContentDescription
                + '\'' + ", mExtras=" + mExtras
                + ", mIntent=" + mIntent
                + ", mIcon=" + mIcon
                + ", mValue='" + mValue + '\''
                + ", mType=" + mType
                + ", mChecked=" + mChecked
                + ", mVisible=" + mVisible
                + ", mSelectable=" + mSelectable
                + ", mEnabled=" + mEnabled
                + ", mShouldRemove=" + mShouldRemove
                + ", mHasOnPreferenceChangeListener=" + mHasOnPreferenceChangeListener
                + ", mNextState=" + mNextState
                + ", mChildPrefCompats=" + mChildPrefCompats
                + '}';
    }
}
