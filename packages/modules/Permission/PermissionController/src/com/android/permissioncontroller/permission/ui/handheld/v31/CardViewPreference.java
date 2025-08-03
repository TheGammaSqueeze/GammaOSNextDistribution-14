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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.Button;

import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;


/**
 * A Preference representing a banner message represented as a CardView
 */
@RequiresApi(Build.VERSION_CODES.S)
public class CardViewPreference extends Preference {

    private String mAction;

    public CardViewPreference(Context context, String action) {
        super(context);
        this.setLayoutResource(R.layout.warning_banner_preference_card);
        mAction = action;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Button button = (Button) holder.findViewById(R.id.button_id);
        button.setText(R.string.blocked_sensor_button_label);
        button.setContentDescription(getContext().getString(R.string.blocked_sensor_button_label));
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> {
            getContext().startActivity(new Intent(mAction));
        });
    }

}
