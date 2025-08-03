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

package com.google.android.tv.btservices.pairing;

import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_CANCELLED;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_CONNECTING;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_DONE;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_ERROR;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_PAIRING;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_TIMEOUT;
import static com.google.android.tv.btservices.pairing.BluetoothPairingService.STATUS_FOUND;
import static com.google.android.tv.btservices.pairing.BluetoothPairingService.STATUS_LOST;
import static com.google.android.tv.btservices.pairing.BluetoothPairingService.STATUS_UPDATED;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.VectorDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.google.android.tv.btservices.BluetoothUtils;
import com.google.android.tv.btservices.R;

public class BluetoothScannerFragment extends LeanbackPreferenceFragment {

    private static final String TAG = "Atv.BtScannerFragment";

    public static BluetoothScannerFragment newInstance() {
        return new BluetoothScannerFragment();
    }

    private static final String KEY_PAIR_DESCRIPTION = "pairing_description";
    private static final String KEY_AVAILABLE_DEVICES = "available_devices";
    private static final String KEY_EMPTY_TEXT = "empty_text";
    private static final String ICON_TOKEN_ONE = "ICON_TOKEN_ONE";
    private static final String ICON_TOKEN_TWO = "ICON_TOKEN_TWO";

    private static final int PAIR_DESCRIPTION_ORDER = 0;
    private static final int AVAILABLE_DEVICES_ORDER = 1;

    private PreferenceGroup mPrefGroup;
    private PreferenceCategory mAvailableCategory;
    private Preference mEmptyTextPreference;

    protected interface PairingHandler {
        void pairDevice(BluetoothDevice device);
    }

    class PairingClickListener implements OnPreferenceClickListener {
        private BluetoothDevice mDevice;

        PairingClickListener(BluetoothDevice device) {
            mDevice = device;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (getActivity() instanceof PairingHandler) {
                ((PairingHandler) getActivity()).pairDevice(mDevice);
            } else {
                throw new ClassCastException("Parent Activity must implement PairingHandler");
            }
            return true;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context preferenceContext = getPreferenceManager().getContext();
        mPrefGroup = getPreferenceManager().createPreferenceScreen(preferenceContext);
        mPrefGroup.setOrderingAsAdded(false);
        mPrefGroup.setTitle(R.string.settings_bt_pair_title);

        Preference pairRemotePref = mPrefGroup.findPreference(KEY_PAIR_DESCRIPTION);
        if (pairRemotePref == null) {
            pairRemotePref = new Preference(preferenceContext);
            pairRemotePref.setLayoutResource(R.layout.preference_description_layout);
            pairRemotePref.setTitle(getPairingDescription(preferenceContext));
            pairRemotePref.setKey(KEY_PAIR_DESCRIPTION);
            pairRemotePref.setOrder(PAIR_DESCRIPTION_ORDER);
            pairRemotePref.setSelectable(false);
            mPrefGroup.addPreference(pairRemotePref);
        }

        mAvailableCategory = mPrefGroup.findPreference(KEY_AVAILABLE_DEVICES);
        if (mAvailableCategory == null) {
            mAvailableCategory = new PreferenceCategory(preferenceContext);
            mAvailableCategory.setKey(KEY_AVAILABLE_DEVICES);
            mAvailableCategory.setTitle(R.string.settings_bt_available_devices);
            mAvailableCategory.setLayoutResource(
                    R.layout.preference_category_compact_progress_layout);
            mAvailableCategory.setOrder(AVAILABLE_DEVICES_ORDER);
            mPrefGroup.addPreference(mAvailableCategory);
        }
        setPreferenceScreen((PreferenceScreen) mPrefGroup);
        checkAddRemoveEmptyText();
    }

    private static SpannableStringBuilder getPairingDescription(Context context) {
        Paint textPaint = new Paint();
        textPaint.setTextSize(context.getResources().getDimension(
                androidx.leanback.preference.R.dimen.lb_preference_item_secondary_text_size));
        Paint.FontMetricsInt fontMetrics = textPaint.getFontMetricsInt();
        int width = fontMetrics.descent - fontMetrics.ascent;

        String deviceName = context.getString(R.string.pair_device_device_name);
        String pairingDescription = context.getString(
                R.string.pair_device_description,
                deviceName,
                ICON_TOKEN_ONE,
                ICON_TOKEN_TWO);
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(pairingDescription);

        int indexOfTokenOne = pairingDescription.indexOf(ICON_TOKEN_ONE);
        VectorDrawable drawableOne = (VectorDrawable) ContextCompat.getDrawable(
                context,
                R.drawable.ic_rcbtn_icon_one);
        drawableOne.setBounds(0 ,fontMetrics.ascent, width, fontMetrics.descent);
        ImageSpan spanOne = new ImageSpan(drawableOne);
        if (indexOfTokenOne != -1) {
            stringBuilder.setSpan(
                    spanOne,
                    indexOfTokenOne,
                    indexOfTokenOne + ICON_TOKEN_ONE.length(),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        int indexOfTokenTwo = pairingDescription.indexOf(ICON_TOKEN_TWO);
        VectorDrawable drawableTwo = (VectorDrawable) ContextCompat.getDrawable(
                context,
                R.drawable.ic_rcbtn_icon_two);
        drawableTwo.setBounds(0 ,fontMetrics.ascent, width, fontMetrics.descent);
        ImageSpan spanTwo = new ImageSpan(drawableTwo);
        if (indexOfTokenTwo != -1) {
            stringBuilder.setSpan(
                    spanTwo,
                    indexOfTokenTwo,
                    indexOfTokenTwo + ICON_TOKEN_TWO.length(),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        return stringBuilder;
    }

    void updateDevice(BluetoothDevice device, int status) {
        if (!isFragmentActive()) {
            return;
        }
        switch (status) {
            case STATUS_FOUND:
            case STATUS_UPDATED:
                addUpdateDevice(device);
                break;
            case STATUS_LOST:
                removeDevice(device);
                break;
        }
        checkAddRemoveEmptyText();
    }

    void updatePairingStatus(BluetoothDevice device, int status) {
        if (!isFragmentActive()) {
            return;
        }
        Preference pref = mAvailableCategory.findPreference(device.getAddress());
        String resStr;
        String text;
        if (pref != null) {
            switch (status) {
                case STATUS_TIMEOUT:
                case STATUS_ERROR:
                    pref.setSummary(R.string.settings_bt_pair_status_error);
                    resStr = getResources().getString(R.string.settings_bt_pair_toast_fail);
                    text = String.format(resStr, device.getName());
                    Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                    break;
                case STATUS_PAIRING:
                case STATUS_CONNECTING:
                    pref.setSummary(R.string.settings_bt_pair_status_connecting);
                    break;
                case STATUS_DONE:
                    pref.setSummary(R.string.settings_bt_pair_status_done);
                    resStr = getResources().getString(R.string.settings_bt_pair_toast_connected);
                    text = String.format(resStr, device.getName());
                    Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                    break;
                case STATUS_CANCELLED:
                    pref.setSummary(R.string.settings_bt_pair_status_cancelled);
                    break;
            }
        }
    }

    private boolean isFragmentActive() {
        return getContext() != null;
    }

    void showProgress(boolean show) {
        if (!isFragmentActive()) {
            return;
        }
        ProgressBar progress = getView().findViewById(R.id.category_progress_spin);
        if (progress == null) {
            return;
        }
        if (show) {
            progress.setVisibility(View.VISIBLE);
        } else {
            progress.setVisibility(View.INVISIBLE);
        }
    }

    private void addUpdateDevice(BluetoothDevice device) {
        Preference pref = mAvailableCategory.findPreference(device.getAddress());
        if (pref == null) {
            pref = createBluetoothDevicePreference(device);
            pref.setOnPreferenceClickListener(new PairingClickListener(device));
            mAvailableCategory.addPreference(pref);
        } else {
            pref.setTitle(device.getName());
            pref.setIcon(BluetoothUtils.getIcon(getContext(), device));
        }
    }

    private void removeDevice(BluetoothDevice device) {
        Preference pref = mAvailableCategory.findPreference(device.getAddress());
        if (pref != null) {
            mAvailableCategory.removePreference(pref);
        }
    }

    private Preference createBluetoothDevicePreference(BluetoothDevice device) {
        Preference pref = new Preference(getContext());
        pref.setKey(device.getAddress());
        pref.setLayoutResource(R.layout.preference_item_layout);
        pref.setTitle(device.getName());
        pref.setIcon(BluetoothUtils.getIcon(getContext(), device));
        pref.setSummary(null);
        pref.setVisible(true);
        pref.setSelectable(true);
        return pref;
    }

    private void checkAddRemoveEmptyText() {
        switch (mAvailableCategory.getPreferenceCount()) {
            case 0:
                mAvailableCategory.addPreference(getEmptyTextPreference());
                break;
            case 1:
                // do nothing
                break;
            default:
                Preference pref = mAvailableCategory.findPreference(KEY_EMPTY_TEXT);
                if (pref != null) {
                    mAvailableCategory.removePreference(pref);
                }
                break;
        }
    }

    private Preference getEmptyTextPreference() {
        if (mEmptyTextPreference == null) {
            mEmptyTextPreference = new Preference(getContext());
            mEmptyTextPreference.setKey(KEY_EMPTY_TEXT);
            mEmptyTextPreference.setLayoutResource(R.layout.preference_empty_text_layout);
            mEmptyTextPreference.setTitle(R.string.settings_bt_empty_text);
            mEmptyTextPreference.setSelectable(false);
        }
        return mEmptyTextPreference;
    }
}
