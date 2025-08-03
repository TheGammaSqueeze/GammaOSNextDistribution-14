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

public interface IAutoDialHelper extends IAppHelper, Scrollable {

    /** enum class for contact list order type. */
    enum OrderType {
        FIRST_NAME,
        LAST_NAME
    }
    /** enum class for phone call audio channel. */
    enum AudioSource {
        PHONE,
        CAR_SPEAKERS,
    }

    /**
     * Setup expectations: The app is open and the dialpad is open
     *
     * <p>This method is used to dial the phonenumber on dialpad
     *
     * @param phoneNumber phone number to dial.
     */
    void dialANumber(String phoneNumber);

    /**
     * Setup expectations: A prompt allowing the user to select "Device" is open.
     *
     * <p>This method is meant to be used when contacts are being uploaded onto a device (the
     * confirmation dialogue allows either the device to be selected for upload, or for
     * cancellation.
     */
    void pressDeviceOnPrompt();

    /**
     * Press the contact info element on screen containing the given name.
     *
     * @param expectedName - The contact name on the contact info element
     */
    void pressContactResult(String expectedName);

    /** Assumes contact page is open. Press the mobile call button on a contact page. */
    void pressMobileCallOnContact();

    /** Assumes Dialer settings page is open. Press the active call toggle. */
    void pressActiveCallToggle();

    /**
     * Assumes home screen is currently open, and the phone card is the bottommost card on the left
     * side of the screen.
     *
     * <p>Press the dialer button on the phone card.
     */
    void pressDialerButtonOnPhoneCard();

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * <p>This method is used to end call using softkey.
     */
    void endCall();

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to open call history details.
     */
    void openCallHistory();

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method dials a contact from the contact list.
     *
     * @param contactName to dial.
     */
    void callContact(String contactName);

    /**
     * Setup expectations: The app is open and in Dialpad.
     *
     * <p>This method is used to delete the number entered on dialpad using backspace
     */
    void deleteDialedNumber();

    /**
     * Setup expectations: The app is open and in Dialpad
     *
     * <p>This method is used to get the number entered on dialing screen.
     */
    String getDialedNumber();

    /**
     * Setup expectations: A call is underway, and the call screen is in focus on the device.
     *
     * <p>This method is used to get the number that is currently being dialed, or has been dialed
     * on a currently-displayed ongoing call
     */
    String getDialingNumber();

    /**
     * Setup expectations: The app is open and in Dialpad
     *
     * <p>This method is used to get the number entered on dialpad
     */
    String getNumberInDialPad();

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * <p>This method is used to get the name of the contact for the ongoing call
     */
    String getDialedContactName();

    /**
     * Setup expectations: The app is open and Call History is open.
     *
     * <p>This method is used to get the most recent call history.
     */
    String getRecentCallHistory();

    /**
     * Setup expectations: The app is open and phonenumber is entered on the dialpad.
     *
     * <p>This method is used to make/receive a call using softkey.
     */
    void makeCall();

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to dial a number from a list (Favorites, Call History, Contact).
     *
     * @param contact (number or name) dial.
     */
    void dialFromList(String contact);

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * <p>This method is used to enter number on the in-call dialpad.
     *
     * @param phoneNumber to dial.
     */
    void inCallDialPad(String phoneNumber);

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * <p>This method is used to mute the ongoing call.
     */
    void muteCall();

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * <p>This method is used to unmute the ongoing call.
     */
    void unmuteCall();

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * <p>This method is used to change voice channel to handset/bluetooth.
     *
     * @param source to switch to.
     */
    void changeAudioSource(AudioSource source);

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to make a call to the first history from Call History.
     */
    void callMostRecentHistory();

    /**
     * Setup expectations: The Phone Dialer Settings page is open.
     *
     * <p>This method returns the phone name recorded under the "Connected Phone" settings field.
     */
    String getConnectedPhoneName();

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * <p>This method is used to get the contact name being called.
     */
    String getContactName();

    /**
     * Setup expectations: The app is open and there is an ongoing call
     *
     * <p>This method is used to get the contact type (Mobile, Work, Home and etc.) being called.
     */
    String getContactType();

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to search a contact in the contact list.
     *
     * @param contact to search.
     */
    void searchContactsByName(String contact);

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to search a number in the contact list.
     *
     * @param number to search.
     */
    void searchContactsByNumber(String number);

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to get the first search result for contact.
     */
    String getFirstSearchResult();

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to order the contact list based on first/last name.
     *
     * @param orderType to use.
     */
    void sortContactListBy(OrderType orderType);

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to get the first contact name from contact list.
     */
    String getFirstContactFromContactList();

    /**
     * Setup expectations: A Contact details page is open.
     *
     * <p>This method is used to get the home address from the currently-open contact.
     */
    String getHomeAddress();

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to verify if a contact is added to Favorites.
     *
     * @param contact to check.
     */
    boolean isContactInFavorites(String contact);

    /**
     * Setup expectations: The contact's details page is open.
     *
     * <p>This method is used to close the details page.
     *
     * @param contact Contact's details page to be opened.
     */
    void openDetailsPage(String contact);

    /** Setup expectations: The dialer main page is open. Opens the dial pad screen. */
    void openDialPad();

    /**
     * Setup expectations: The dialer app page is open.
     *
     * <p>This method opens the dialer settings page (via the Settings icon button on the dialer
     * page.
     */
    void openDialerSettings();

    boolean isPhonePaired();
    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to open contact list
     */
    void openContacts();

    /**
     * Setup expectations: The contacts page is open.
     *
     * <p>This method is used to open the contact details page of the first visible contact. Throws
     * an error if no contacts are visible.
     */
    void openFirstContactDetails();

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * <p>This method is used check if ongoing call is displayed on home.
     */
    boolean isOngoingCallDisplayedOnHome();

    /**
     * Setup expectations: None
     *
     * @return Whether the screen currently shows an ongoing call (in full-screen mode).
     */
    boolean isOngoingCallInFullScreen();

    /**
     * Setup expectations: The home screen is open
     *
     * <p>This method is used for opening phone app from homescreen
     */
    void openPhoneAppFromHome();

    /**
     * Setup expectations: The app is open.
     *
     * <p>This method is used to get visible contacts list
     */
    List<String> getListOfAllVisibleContacts();

    /**
     * Setup expectations: The call history view is open.
     *
     * @return - The number of call histrory entries currently on screen.
     */
    int getNumberOfCallHistoryEntries();

    /**
     * Setup expectations: the Dialer Settings page is open.
     *
     * <p>Returns true if the Active Call feature is enabled, false otherwise.
     */
    boolean isActiveCallEnabled();

    /**
     * Setup expectations: bluetooth is off
     *
     * <p>This method is used for checking if error message is displaye when bluetooth is off
     */
    boolean isBluetoothHfpErrorDisplayed();

    /**
     * Setup expectations: The app is open
     *
     * <p>This method is adding favorites from the Favorites Tab
     */
    void addFavoritesFromFavoritesTab(String contact);
    /**
     * Setup expectations: The bluetooth palette is open
     *
     * <p>This method is used for clicking phone button from bluetooth palette
     */
    void clickPhoneButton();
    /**
     * Setup expectations: The dialer page is open
     *
     * <p>This method is used to check if Recents tab is present in dialer page
     */
    boolean verifyDialerRecentsTab();
    /**
     * Setup expectations: The dialer page is open
     *
     * <p>This method is used to check if Contacts tab is present in dialer page
     */
    boolean verifyDialerContactsTab();
    /**
     * Setup expectations: The dialer page is open
     *
     * <p>This method is used to check if Favorites tab is present in dialer page
     */
    boolean verifyDialerFavoritesTab();
    /**
     * Setup expectations: The dialer page is open
     *
     * <p>This method is used to check if Dialpad tab is present in dialer page
     */
    boolean verifyDialerDialpadTab();
}
