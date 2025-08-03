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

package android.car.hardware.power;


/**
 * Copy from android.frameworks.automotive.powerpolicy-java-source/gen/android/frameworks/automotive
 * /powerpolicy/PowerComponent.java. Must be updated when power components are added to
 * {@code android.frameworks.automotive.powerpolicy.PowerComponent}.
 */
public @interface PowerComponent {
    /**
     * This is used to turn on/off audio through power policy.
     */
    int AUDIO = 1;
    /**
     * This is used to turn on/off media playing/recording through power policy.
     */
    int MEDIA = 2;
    /**
     * This is used to turn on/off display through power policy.
     */
    int DISPLAY = 3;
    /**
     * This is used to turn on/off bluetooth through power policy.
     */
    int BLUETOOTH = 4;
    /**
     * This is used to turn on/off WiFi network through power policy.
     */
    int WIFI = 5;
    /**
     * This is used to turn on/off cellular network through power policy.
     */
    int CELLULAR = 6;
    /**
     * This is used to turn on/off ethernet through power policy.
     */
    int ETHERNET = 7;
    /**
     * This is used to turn on/off projection from other devices through power policy.
     */
    int PROJECTION = 8;
    /**
     * This is used to turn on/off NFC through power policy.
     */
    int NFC = 9;
    /**
     * This is used to turn on/off all inputs from users through power policy.
     */
    int INPUT = 10;
    /**
     * This is used to turn on/off voice interaction through power policy.
     */
    int VOICE_INTERACTION = 11;
    /**
     * This is used to turn on/off visual interaction through power policy.
     */
    int VISUAL_INTERACTION = 12;
    /**
     * This is used to turn on/off trusted device detection through power policy.
     */
    int TRUSTED_DEVICE_DETECTION = 13;
    /**
     * This is used to turn on/off location through power policy.
     */
    int LOCATION = 14;
    /**
     * This is used to turn on/off microphone through power policy.
     */
    int MICROPHONE = 15;
    /**
     * This is used to turn on/off CPU through power policy. It will turn into off state when system
     * goes into sleep state. It will be restored to on state when system gets out of sleep state.
     */
    int CPU = 16;
    /**
     * This is minimal allowed value for custom defined power components
     */
    int MINIMUM_CUSTOM_COMPONENT_VALUE = 1000;

}
