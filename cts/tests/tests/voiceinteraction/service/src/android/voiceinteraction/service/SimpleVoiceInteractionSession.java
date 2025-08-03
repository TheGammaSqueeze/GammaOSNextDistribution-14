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

package android.voiceinteraction.service;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.voiceinteraction.common.Utils;

/**
 * A simple {@link VoiceInteractionSession} that is used to test @link VoiceInteractionSession}
 * APIs, e.g. {@link VoiceInteractionSession#startAssistantActivity(Intent)} and
 * {@link VoiceInteractionSession#startAssistantActivity(Intent, ActivityOptions)}.
 */
public class SimpleVoiceInteractionSession extends VoiceInteractionSession {

    private static final String TAG = "SimpleVoiceInteractionSession";
    private final Context mContext;

    public SimpleVoiceInteractionSession(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        boolean useActivityOptions = args != null && args.getBoolean(
                Utils.VOICE_INTERACTION_KEY_USE_ACTIVITY_OPTIONS);
        Log.d(TAG, "onShow! useActivityOptions=" + useActivityOptions);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("android.voiceinteraction.cts",
                "android.voiceinteraction.cts.activities.EmptyActivity"));
        if (useActivityOptions) {
            ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext,
                    /* enterResId= */ android.R.anim.fade_in,
                    /* exitResId= */ android.R.anim.fade_out);
            startAssistantActivity(intent, options.toBundle());
        } else {
            startAssistantActivity(intent);
        }
    }
}
