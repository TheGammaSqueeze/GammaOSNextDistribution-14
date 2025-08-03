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
package com.android.wallpaper.effects;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/**
 * Utility class to provide methods to generate effects for the wallpaper.
 */
public abstract class EffectsController {
    public static final int ERROR_ORIGINAL_DESTROY_CONTROLLER = -16;
    public static final int ERROR_ORIGINAL_FINISH_ONGOING_SERVICE = -8;
    public static final int ERROR_ORIGINAL_SERVICE_DISCONNECT = -4;
    public static final int ERROR_ORIGINAL_TIME_OUT = -2;

    public static final int RESULT_ORIGINAL_UNKNOWN = -1;
    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_ERROR_TRY_ANOTHER_PHOTO = 1;
    public static final int RESULT_ERROR_TRY_AGAIN_LATER = 2;
    public static final int RESULT_ERROR_CONTINUE = 4;
    public static final int RESULT_ERROR_DEFAULT =
            RESULT_ERROR_TRY_ANOTHER_PHOTO + RESULT_ERROR_CONTINUE;
    public static final int RESULT_ERROR_DISCONNECT_NO_BUTTON = 8;
    public static final int RESULT_PROBE_SUCCESS = 16;
    public static final int RESULT_PROBE_ERROR = 32;
    public static final int RESULT_SUCCESS_REUSED = 64;
    public static final int RESULT_SUCCESS_WITH_GENERATION_ERROR = 128;
    public static final int RESULT_ERROR_PROBE_SUPPORT_FOREGROUND = 256;
    public static final int RESULT_FOREGROUND_DOWNLOAD_SUCCEEDED = 512;
    public static final int RESULT_FOREGROUND_DOWNLOAD_FAILED = 1024;
    public static final int RESULT_PROBE_FOREGROUND_DOWNLOADING = 2048;
    /**
     * Interface of the Effect enum.
     */
    public interface EffectEnumInterface {
    }
    public enum Effect implements EffectEnumInterface {
        NONE,
    }

    protected boolean mBound = false;

    /**
     * Call to generate an effect.
     *
     * @param effect the effect type we want to generate.
     * @param image  the image that will have the effect applied.
     */
    public void generateEffect(EffectEnumInterface effect, Uri image) {
    }

    /**
     * Binds the Effects Service.
     */
    public void bindEffectsService() {
    }

    /**
     * Destroys the controller
     */
    public void destroy() {
    }

    /**
     * If the Effects Service is bound.
     *
     * @return if the Effects Service is bound.
     */
    public boolean isBound() {
        return mBound;
    }

    /**
     * Triggers the effect.
     *
     * @param context the context
     */
    public void triggerEffect(Context context) {
    }

    /** Sets the {@link EffectsServiceListener} to receive updates. */
    public void setListener(EffectsServiceListener listener) {}

    /** Removes the listener set via {@link #setListener(EffectsServiceListener)}. */
    public void removeListener() {}

    /** Returns true if the effect is expected by this controller. */
    public boolean isTargetEffect(EffectEnumInterface effect) {
        return effect == getTargetEffect();
    }

    /**
     * Interface to listen to different key moments of the connection with the Effects Service.
     */
    public interface EffectsServiceListener {
        /**
         * Called when an effect has finished being processed.
         *
         * @param effect The effect that was generated.
         * @param bundle The data that the Service might have sent to the picker.
         * @param error The error code. if there's an error, value is greater than zero.
         * @param originalStatusCode The original status code used for metrics logging.
         * @param errorMessage The error message.
         */
        void onEffectFinished(EffectEnumInterface effect, Bundle bundle, int error,
                int originalStatusCode, String errorMessage);
    }

    public EffectEnumInterface getTargetEffect() {
        return Effect.NONE;
    }

    /**
     * Gets whether the effect triggering is successful or not.
     *
     * @return whether the effect triggering is successful or not.
     */
    public boolean isEffectTriggered() {
        return false;
    }

    public String getEffectTitle() {
        return "";
    }

    public String getEffectFailedTitle() {
        return "";
    }

    public String getEffectSubTitle() {
        return "";
    }

    public String getRetryInstruction() {
        return "";
    }

    public String getNoEffectInstruction() {
        return "";
    }

    public Uri getContentUri() {
        return Uri.EMPTY;
    }
}
