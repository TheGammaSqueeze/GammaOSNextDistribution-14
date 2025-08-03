/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.settings.wifi.preferences;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.ui.preference.CarUiPreference;

/**
 * A Preference used to show the QR code for the Hotspot.
 */
public class WifiTetherQrCodePreference extends CarUiPreference {
    private static final Logger LOG = new Logger(WifiTetherQrCodePreference.class);

    private Bitmap mImage;
    private String mName;
    private String mPassword;

    public WifiTetherQrCodePreference(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public WifiTetherQrCodePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public WifiTetherQrCodePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WifiTetherQrCodePreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.wifi_tether_qr_code_preference);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ImageView qrCode = (ImageView) holder.findViewById(R.id.hotspot_qr_code);
        if (qrCode != null && mImage != null) {
            qrCode.setImageBitmap(mImage);
        } else {
            LOG.v("The QR code ImageView is null");
        }

        TextView nameView = (TextView) holder.findViewById(R.id.hotspot_qr_code_instruction);
        if (nameView != null) {
            nameView.setText(getContext().getString(R.string.wifi_hotspot_qr_code_text, mName));
        }

        TextView passwordView = (TextView) holder.findViewById(R.id.hotspot_qr_code_password);
        if (passwordView != null) {
            passwordView.setText(
                    getContext().getString(R.string.wifi_hotspot_qr_code_password_text, mPassword));
        }
    }

    /**
     * Sets information for this preference.
     */
    public void setPreferenceInfo(Bitmap bmp, String name, String password) {
        mImage = bmp;
        mName = name;
        mPassword = password;

        notifyChanged();
    }
}
