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

package com.android.tv.settings.library.system;

import static com.android.tv.settings.library.ManagerUtil.STATE_SYSTEM_DATE_TIME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.users.RestrictedProfileModel;

import java.util.Calendar;
import java.util.Date;

/** State to provide data for rendering DateTimeFragmentCompat. */
public class DateTimeState implements State {
    private static final String KEY_AUTO_DATE_TIME = "auto_date_time";
    private static final String KEY_SET_DATE = "set_date";
    private static final String KEY_SET_TIME = "set_time";
    private static final String KEY_SET_TIME_ZONE = "set_time_zone";
    private static final String KEY_USE_24_HOUR = "use_24_hour";

    private static final String AUTO_DATE_TIME_NTP = "network";
    private static final String AUTO_DATE_TIME_TS = "transport_stream";
    private static final String AUTO_DATE_TIME_OFF = "off";

    private static final String HOURS_12 = "12";
    private static final String HOURS_24 = "24";

    private Calendar mNow = Calendar.getInstance();
    private final Calendar mDummyDate = Calendar.getInstance();

    private PreferenceCompat mDatePref;
    private PreferenceCompat mTimePref;
    private PreferenceCompat mTimeZonePref;
    private PreferenceCompat mTime24Pref;
    private PreferenceCompat mAutoDateTimePref;

    private final Context mContext;
    private final UIUpdateCallback mUIUpdateCallback;
    private PreferenceCompatManager mPreferenceCompatManager;

    public DateTimeState(Context context, UIUpdateCallback callback) {
        mUIUpdateCallback = callback;
        mContext = context;
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTimeAndDateDisplay();
        }
    };

    @Override
    public void onAttach() {
        // no-op
    }

    @Override
    public void onCreate(Bundle extras) {
        mPreferenceCompatManager = new PreferenceCompatManager();

        final boolean isRestricted = isRestrictedProfileInEffect();
        mDatePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_SET_DATE);
        mDatePref.setVisible(!isRestricted);
        mTimePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_SET_TIME);
        mTimePref.setVisible(!isRestricted);
        mAutoDateTimePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_AUTO_DATE_TIME);
        mAutoDateTimePref.setValue(getAutoDateTimeState());
        mAutoDateTimePref.setVisible(!isRestricted);
        mTimeZonePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_SET_TIME_ZONE);
        mTimeZonePref.setVisible(!isRestricted);
        mTime24Pref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_USE_24_HOUR);
    }

    @Override
    public void onStart() {
        // no-op
    }

    @Override
    public void onResume() {
        // Register for time ticks and other reasons for time change
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        mContext.registerReceiver(mIntentReceiver, filter, null, null);

        updateTimeAndDateDisplay();
        updateTimeDateEnable();
    }

    @Override
    public void onPause() {
        mContext.unregisterReceiver(mIntentReceiver);
    }

    @Override
    public void onStop() {
        // no-op
    }

    @Override
    public void onDestroy() {
        // no-op
    }

    @Override
    public void onDetach() {
        // no-op
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        // no-op
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        if (TextUtils.equals(key[0], KEY_AUTO_DATE_TIME)) {
            String value = (String) newValue;
            if (TextUtils.equals(value, AUTO_DATE_TIME_NTP)) {
                setAutoDateTime(true);
            } else if (TextUtils.equals(value, AUTO_DATE_TIME_TS)) {
                throw new IllegalStateException("TS date is not yet implemented");
//                mTvInputManager.syncTimefromBroadcast(true);
//                setAutoDateTime(false);
            } else if (TextUtils.equals(value, AUTO_DATE_TIME_OFF)) {
                setAutoDateTime(false);
            } else {
                throw new IllegalArgumentException("Unknown auto time value " + value);
            }
            updateTimeDateEnable();
            updateAutoDateTimeUI();
        } else if (TextUtils.equals(key[0], KEY_USE_24_HOUR)) {
            final boolean use24Hour = (Boolean) newValue;
            set24Hour(use24Hour);
            timeUpdated(use24Hour);
            updateTimeAndDateDisplayUI();
        }
        return true;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_SYSTEM_DATE_TIME;
    }

    private void updateTimeAndDateDisplay() {
        mTime24Pref.setChecked(is24Hour());

        mNow = Calendar.getInstance();
        mDummyDate.setTimeZone(mNow.getTimeZone());
        // We use December 31st because it's unambiguous when demonstrating the date format.
        // We use 13:00 so we can demonstrate the 12/24 hour options.
        mDummyDate.set(mNow.get(Calendar.YEAR), 11, 31, 13, 0, 0);
        Date dummyDate = mDummyDate.getTime();

        mDatePref.setSummary(DateFormat.getLongDateFormat(mContext).format(mNow.getTime()));
        mTimePref.setSummary(DateFormat.getTimeFormat(mContext).format(mNow.getTime()));
        mTime24Pref.setSummary(DateFormat.getTimeFormat(mContext).format(dummyDate));

        updateTimeAndDateDisplayUI();
    }

    private void updateTimeAndDateDisplayUI() {
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mDatePref);
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mTimePref);
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mTimeZonePref);
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mTime24Pref);
        }
    }

    private void updateAutoDateTimeUI() {
        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mAutoDateTimePref);
        }
    }

    private void updateTimeDateEnable() {
        final boolean enable = TextUtils.equals(getAutoDateTimeState(), AUTO_DATE_TIME_OFF);

        mDatePref.setEnabled(enable);
        mTimePref.setEnabled(enable);

        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mDatePref);
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mTimePref);
        }
    }

    private boolean is24Hour() {
        return DateFormat.is24HourFormat(mContext);
    }

    private void timeUpdated(boolean use24Hour) {
        Intent timeChanged = new Intent(Intent.ACTION_TIME_CHANGED);
        int timeFormatPreference =
                use24Hour ? Intent.EXTRA_TIME_PREF_VALUE_USE_24_HOUR
                        : Intent.EXTRA_TIME_PREF_VALUE_USE_12_HOUR;
        timeChanged.putExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT, timeFormatPreference);
        mContext.sendBroadcast(timeChanged);
    }

    private void set24Hour(boolean use24Hour) {
        Settings.System.putString(mContext.getContentResolver(),
                Settings.System.TIME_12_24,
                use24Hour ? HOURS_24 : HOURS_12);
    }

    private void setAutoDateTime(boolean on) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.AUTO_TIME, on ? 1 : 0);
    }

    private String getAutoDateTimeState() {
        int value = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AUTO_TIME, 0);
        if (value > 0) {
            return AUTO_DATE_TIME_NTP;
        }

        return AUTO_DATE_TIME_OFF;
    }

    /** Get current time for updating UI of date & time */
    public Calendar getNow() {
        return mNow;
    }

    private boolean isRestrictedProfileInEffect() {
        return new RestrictedProfileModel(mContext).isCurrentUser();
    }

    @Override
    public void onDisplayDialogPreference(String[] key) {

    }
}
