/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class MainInteractionSessionService extends VoiceInteractionSessionService {

    private static final String TAG = "MainInteractionSessionService";
    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        final String className = (args != null)
                ? args.getString(Utils.VOICE_INTERACTION_KEY_CLASS) : null;
        String command = args != null ? args.getString(Utils.VOICE_INTERACTION_KEY_COMMAND) : null;
        final RemoteCallback callback = (args != null) ? args.getParcelable(
                Utils.VOICE_INTERACTION_KEY_REMOTE_CALLBACK_FOR_NEW_SESSION, RemoteCallback.class)
                : null;
        if (callback != null) {
            // Notify the session is started. The result value is not important
            callback.sendResult(Bundle.EMPTY);
        }
        boolean testStartAssistantActivity =
                command != null && command.equals("startAssistantActivity");
        Log.d(TAG, "testStartAssistantActivity = " + testStartAssistantActivity);
        if (testStartAssistantActivity) {
            return new SimpleVoiceInteractionSession(this);
        }
        if (className == null) {
            return new MainInteractionSession(this);
        } else {
            try {
                final Constructor<?> constructor = Class.forName(className)
                        .getConstructor(Context.class);
                return (VoiceInteractionSession) constructor.newInstance(this);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                    | InstantiationException | InvocationTargetException e) {
                throw new RuntimeException("Cannot instantiate class: " + className, e);
            }
        }
    }
}
