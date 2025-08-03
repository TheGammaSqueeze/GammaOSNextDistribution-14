/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.AttributeSet;

import com.android.systemui.R;

/**
 * CarUiAssistantButton is an ui component that will trigger the Voice Interaction Service.
 *
 * TODO(b/255887799): Workaround until the issue is fixed.
 */
public class CarUiAssistantButton extends AssistantButton {

    private final Context mContext;
    private ComponentName mVoiceAssistantComponent;

    public CarUiAssistantButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mVoiceAssistantComponent = ComponentName.unflattenFromString(
                context.getResources().getString(R.string.config_VoiceAssistantActivity));
    }

    @Override
    void showAssistant() {
        Intent intent = new Intent();
        intent.setComponent(mVoiceAssistantComponent);
        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }
}
