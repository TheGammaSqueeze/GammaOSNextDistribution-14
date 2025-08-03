/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.carlauncher.homescreen.assistive;

import android.content.Context;

import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.ui.CardContent;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextView;

/**
 * Displays static weather information to provide default content for the assistive card.
 */
public class FakeWeatherModel implements AssistiveModel {
    private CardHeader mCardHeader;
    private DescriptiveTextView mCardContent;
    private OnModelUpdateListener mOnModelUpdateListener;

    @Override
    public void onCreate(Context context) {
        mCardHeader = new CardHeader(context.getString(R.string.weather_app_name),
                context.getDrawable(R.drawable.ic_partly_cloudy));
        mCardContent = new DescriptiveTextView(
                /* image= */ context.getDrawable(R.drawable.ic_partly_cloudy),
                /* title= */ context.getString(R.string.fake_weather_main_text),
                /* subtitle= */ null,
                /* footer= */ context.getString(R.string.fake_weather_footer_text));
        mOnModelUpdateListener.onModelUpdate(this);
    }

    @Override
    public CardHeader getCardHeader() {
        return mCardHeader;
    }

    @Override
    public CardContent getCardContent() {
        return mCardContent;
    }

    @Override
    public void setOnModelUpdateListener(OnModelUpdateListener onModelUpdateListener) {
        mOnModelUpdateListener = onModelUpdateListener;
    }
}
