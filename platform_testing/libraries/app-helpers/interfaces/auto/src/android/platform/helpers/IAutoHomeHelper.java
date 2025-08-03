/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.platform.helpers;

import java.util.List;

public interface IAutoHomeHelper extends IAppHelper {

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if exists a media widget.
     */
    boolean hasMediaWidget();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if exists a assistant widget.
     */
    boolean hasAssistantWidget();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if exists a bluetooth widget.
     */
    boolean hasBluetoothButton();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if exists a network widget.
     */
    boolean hasNetworkButton();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if exists a Brightness widget.
     */
    boolean hasDisplayBrightness();

    /**
     * Setup expectations: Verifying the Guest label displayed in profile icon in status bar
     *
     * <p>Get the text of the Guest profile
     */
    String getUserProfileName();

    /** Opens Media Widget from home screen. */
    void openMediaWidget();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if exists a brightness palette.
     */
    boolean hasDisplayBrightessPalette();

    /**
     * Setup expectations: Should be on home screen.
     *
     * <p>Checks if exists a adaptive brightness.
     */
    boolean hasAdaptiveBrightness();

    /** Click on brightness button. */
    void openBrightnessPalette();

    /** Checks if Temperature widget is present on home screen */
    boolean hasTemperatureWidget();

    /**
     * Setup expectations: To Open Profile Icon button.
     *
     * <p>Opens Profile Icon button on status bar.
     */
    void openStatusBarProfiles();

    /**
     * Setup expectations: To get Profiles in a List
     *
     * <p>Getting Profiles in a list and comparing based on position
     */
    List<String> getUserProfileNames();

    /**
     * Setup expectations: To run system Ui adb command.
     *
     * <p>Opens Profile Icon button on status bar.
     */
    void openSystemUi();

    /**
     * Setup expectations: To run car Ui adb command.
     *
     * <p>Opens Profile Icon button on status bar.
     */
    void openCarUi();
    /**
     * Setup expectations: SMaps widget should be present on Home screen
     *
     * <p>Checks if exists a maps Widget on home screen
     */
    boolean hasMapsWidget();
    /**
     * Setup expectations: To Open Assistant Widget button.
     *
     * <p>Opens Assistant Widget button on Home screen
     */
    void clickAssistantWidget();
}
