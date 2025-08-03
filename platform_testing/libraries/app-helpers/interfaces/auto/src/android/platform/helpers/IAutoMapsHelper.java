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

package android.platform.helpers;

public interface IAutoMapsHelper extends IAppHelper {

    /**
     * Setup expectations: Maps app is open
     *
     * <p>This method is used to verify whether search bar is present.
     */
    boolean isSearchBarPresent();

    /**
     * Setup expectations: Maps app is open
     *
     * This method is used to search an address.
     *
     */
    void search(String address);

    /**
     * Setup expectations: Maps app is open
     *
     * This method is used to start navigation.
     *
     */
    void startNavigation();

    /**
     * Setup expectations: Maps app is open
     *
     * This method is used to stop navigation.
     *
     */
    void stopNavigation();

    /**
     * Setup expectations: To get maps app address
     *
     * <p>This method is used to get searched address
     */
    String getAddress();

    /**
     * Setup expectations: Click on Mic button
     *
     * <p>This method is used to click on Mic button on maps
     */
    void openMapMicButton();

    /**
     * Setup expectations: verifies unblock message
     *
     * <p>Verify if unblock message is displayed
     */
    boolean isMicrophoneUnblockMessageDisplayed();

    /**
     * Setup expectations: click Cancel
     *
     * <p>Clicks on cancel button
     */
    void cancelUnblockMessage();

    /**
     * Setup expectations: Maps app is open
     *
     * <p>This method verifies the if searchBar is restricted when the drive mode is enabled.
     */
    boolean isSearchBarRestricted();

    /**
     * Setup expectations: Maps app is open
     *
     * <p>This method will launch the keyboard.
     */
    void launchKeyboard();

    /**
     * Setup expectations: Maps app is open
     *
     * <p>This method verifies the if keyboard is restricted when the drive mode is enabled.
     */
    boolean isKeyboardRestricted();

    /**
     * Setup expectations: Maps app is open
     *
     * <p>Swipe upwards by <code>percent</code> percent of maps region in <code>durationMs</code>
     * milliseconds.
     *
     * @param percent The percentage of the scrollable region by which to swipe up.
     * @param durationMs The duration it takes to perform this gesture in milliseconds.
     */
    void swipeUp(float percent, long durationMs);

    /**
     * Setup expectations: Maps app is open
     *
     * <p>Swipe downwards by <code>percent</code> percent of maps region in <code>durationMs</code>
     * milliseconds.
     *
     * @param percent The percentage of the scrollable region by which to swipe down.
     * @param durationMs The duration it takes to perform this gesture in milliseconds.
     */
    void swipeDown(float percent, long durationMs);

    /**
     * Setup expectations: Maps app is open
     *
     * <p>Swipe leftwards by <code>percent</code> percent of maps region in <code>durationMs</code>
     * milliseconds.
     *
     * @param percent The percentage of the scrollable region by which to swipe left.
     * @param durationMs The duration it takes to perform this gesture in milliseconds.
     */
    void swipeLeft(float percent, long durationMs);

    /**
     * Setup expectations: Maps app is open
     *
     * <p>Swipe rightwards by <code>percent</code> percent of maps region in <code>durationMs</code>
     * milliseconds.
     *
     * @param percent The percentage of the scrollable region by which to swipe right.
     * @param durationMs The duration it takes to perform this gesture in milliseconds.
     */
    void swipeRight(float percent, long durationMs);

    /**
     * Setup expectations: Maps app is open
     *
     * <p>Scale by <code>percent</code> percent in <code>durationMs</code> milliseconds. When <code>
     * percent</code> is less 100, it scales down, and vice versa. For example, when percent is 50,
     * it will scale down so that objects look half the size as before, and when percent is 200, it
     * will scale up and objects look twice as large.
     *
     * @param percent The percentage by which to scale up/down.
     * @param durationMs The duration it takes to perform this gesture in milliseconds.
     */
    void scale(float percent, long durationMs);

    /**
     * Setup expectations: Maps app is open
     *
     * <p>Rotate by <code>angle</code> degrees in <code>durationMs</code> milliseconds, rotation is
     * around the center of the maps region. Positive angle for clockwise rotation, and negative
     * angle for counter-clockwise rotation.
     *
     * @param angle The angle in degree by which to rotate to screen.
     * @param durationMs The duration it takes to perform this gesture in milliseconds.
     */
    void rotate(float angle, long durationMs);

    /**
     * Setup expectations: Maps app is open
     *
     * <p>Tilt up by <code>angle</code> degrees in <code>durationMs</code> milliseconds.
     *
     * @param angle The angle in degree by which to tilt up the screen's view angle.
     * @param durationMs The duration it takes to perform this gesture in milliseconds.
     */
    void tiltUp(float angle, long durationMs);

    /**
     * Setup expectations: Maps app is open
     *
     * <p>Tilt down by <code>angle</code> degrees in <code>durationMs</code> milliseconds.
     *
     * @param angle The angle in degree by which to tilt down the screen's view angle.
     * @param durationMs The duration it takes to perform this gesture in milliseconds.
     */
    void tiltDown(float angle, long durationMs);

    /**
     * Setup expectations: Maps widget is displayed
     *
     * <p>This method is used to verify whether Maps widget is displayed.
     */
    boolean hasMapsWidget();

    /**
     * Setup expectations: Gas station widget is displayed in maps full screen
     *
     * <p>This method is used to verify whether Gas Station Widget is displayed.
     */
    boolean hasGasStationWidget();

    /**
     * Setup expectations: Restaurant widget is displayed in maps full screen
     *
     * <p>This method is used to verify whether Restaurant Widget is displayed.
     */
    boolean hasRestaurantWidget();

    /**
     * Setup expectations: Grocery Store widget is displayed in maps full screen
     *
     * <p>This method is used to verify whether Grocery Store Widget is displayed.
     */
    boolean hasGroceryStoreWidget();

    /**
     * Setup expectations: Coffee Shop widget is displayed in maps full screen
     *
     * <p>This method is used to verify whether Coffee Shop Widget is displayed.
     */
    boolean hasCoffeeShopsWidget();

    /**
     * Setup expectations: Maps Keyboard is Open
     *
     * <p>click on handwriting and speech to text on keyboard
     */
    void clickKeyboardSpeechToTextButton();
    /**
     * Setup expectations: Maps is Open
     *
     * <p>Returns true if Alert Message is displayed
     */
    boolean isAlertMessageDisplayed();

    /**
     * SetUp expectation : Maps is Open
     *
     * <p>Open settings in Maps UI
     *
     * <p>
     */
    void openMapsSettings();

    /**
     * SetUp expectation : Maps is Open
     *
     * <p>Click on Edit home Work address button in Maps Settings
     *
     * <p>
     */
    void clickOnEditHomeAndWorkAddress();

    /**
     * SetUp expectation : Maps is Open
     *
     * <p>Set home or Work address in Maps Settings
     *
     * <p>
     *
     * @param address -Address of the location
     * @param target - Location of home target or work target
     */
    void setHomeOrWorkAddress(String address, String target);

    /**
     * SetUp expectation : Maps is Open
     *
     * <p>Delete home or Work address added for accounts in Maps
     *
     * <p>
     */
    void deleteAddressAdded(String target);

    /**
     * SetUp expectation : Maps is Open
     *
     * <p>Close the address diaglog opened
     *
     * <p>
     */
    void closeDialog();
}
