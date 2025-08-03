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

package android.car.media;

import android.annotation.SdkConstant;
import android.annotation.SuppressLint;

/**
 * Defines media related intents (and their extras) for the car.
 */
public final class CarMediaIntents {

    /**
      * Activity Action: Provide media playing through a media template app.
      * <p> Input: these optional extras
      * <li> {@link #EXTRA_MEDIA_COMPONENT} </li>
      * <li> {@link #EXTRA_SEARCH_QUERY} </li>
      * <p> Output: nothing.<p/>
      * If no extra is specified, the current media source is opened.
      */
    @SuppressLint("ActionValue") // Moved from android.car.Car.java
    @SdkConstant(SdkConstant.SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MEDIA_TEMPLATE = "android.car.intent.action.MEDIA_TEMPLATE";

    /**
     * Used as a string extra field with {@link #ACTION_MEDIA_TEMPLATE} to specify the
     * MediaBrowserService that user wants to start the media on.
     */
    @SuppressLint("ActionValue") // Moved from android.car.Car.java
    public static final String EXTRA_MEDIA_COMPONENT = "android.car.intent.extra.MEDIA_COMPONENT";

    /**
     * Used as a string extra field with {@link #ACTION_MEDIA_TEMPLATE} to specify the search query
     * to send either to the current MediaBrowserService or the one specified
     * with {@link #EXTRA_MEDIA_COMPONENT}.
     */
    public static final String EXTRA_SEARCH_QUERY = "android.car.media.extra.SEARCH_QUERY";

    /** @hide */
    private CarMediaIntents() {
    }
}
