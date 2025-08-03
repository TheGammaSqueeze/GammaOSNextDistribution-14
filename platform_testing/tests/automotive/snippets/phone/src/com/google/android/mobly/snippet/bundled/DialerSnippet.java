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

package com.google.android.mobly.snippet.bundled;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoDialHelper;
import android.platform.helpers.IAutoVehicleHardKeysHelper;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

import java.util.List;

/** Snippet class for exposing Phone/Dial App APIs. */
public class DialerSnippet implements Snippet {
    private final HelperAccessor<IAutoDialHelper> mDialerHelper;
    private final HelperAccessor<IAutoVehicleHardKeysHelper> mHardKeysHelper;

    public DialerSnippet() {
        mDialerHelper = new HelperAccessor<>(IAutoDialHelper.class);
        mHardKeysHelper = new HelperAccessor<>(IAutoVehicleHardKeysHelper.class);
    }

    @Rpc(description = "Open Phone Application.")
    public void openPhoneApp() {
        mDialerHelper.get().open();
    }

    /** Opens the dial pad from the dialer main screen. */
    @Rpc(description = "Open Dial Pad.")
    public void openDialPad() {
        mDialerHelper.get().openDialPad();
    }

    @Rpc(description = "Open Dial Pad and dial in a number using keypad.")
    public void dialANumber(String phoneNumber) {
        mDialerHelper.get().dialANumber(phoneNumber);
    }

    @Rpc(description = "Make a call.")
    public void makeCall() {
        mDialerHelper.get().makeCall();
    }

    @Rpc(description = "End the call.")
    public void endCall() {
        mDialerHelper.get().endCall();
    }

    @Rpc(description = "Press the hardkey for ending the call.")
    public void endCallWithHardkey() {
        mHardKeysHelper.get().pressEndCallKey();
    }

    @Rpc(description = "Open Call History.")
    public void openCallHistory() {
        mDialerHelper.get().openCallHistory();
    }

    /** Open Dialer settings from the Dialer app */
    @Rpc(description = "Open Dialer Settings from the Dialer app.")
    public void openDialerSettings() {
        mDialerHelper.get().openDialerSettings();
    }

    @Rpc(description = "Call Contact From Contact List.")
    public void callContact(String contactName) {
        mDialerHelper.get().callContact(contactName);
    }

    @Rpc(description = "Return whether the \"Connected Phone\" field holds the given name.")
    public String getConnectedPhoneName() {
        return mDialerHelper.get().getConnectedPhoneName();
    }

    @Rpc(description = "Delete the dialed number on Dial Pad.")
    public void deleteDialedNumber() {
        mDialerHelper.get().deleteDialedNumber();
    }

    @Rpc(description = "Get the dialed number while the call is in progress.")
    public String getDialingNumber() {
        return mDialerHelper.get().getDialingNumber();
    }

    @Rpc(description = "Get the entered on dial pad.")
    public String getNumberInDialPad() {
        return mDialerHelper.get().getNumberInDialPad();
    }

    @Rpc(description = "Get the home address from an open contacts page.")
    public String getHomeAddress() {
        return mDialerHelper.get().getHomeAddress();
    }

    @Rpc(description = "Get the contact name for dialed number when the call is in progress.")
    public String getDialedContactName() {
        return mDialerHelper.get().getDialedContactName();
    }

    @Rpc(description = "Get the recent entry from Call History.")
    public String getRecentCallHistory() {
        return mDialerHelper.get().getRecentCallHistory();
    }

    /** RPC to get the number of call history entries */
    @Rpc(description = "Get the number of entries in the display call history.")
    public int getNumCallHistoryEntries() {
        return mDialerHelper.get().getNumberOfCallHistoryEntries();
    }

    @Rpc(
            description =
                    "Call contact from list open in foreground e.g. Favorites, Recents, Contacts.")
    public void dialFromList(String contact) {
        mDialerHelper.get().dialFromList(contact);
    }

    @Rpc(description = "Returns whether a call is currently ongoing and in full-screen mode.")
    public boolean isOngoingCallInFullScreen() {
        return mDialerHelper.get().isOngoingCallInFullScreen();
    }

    @Rpc(description = "Return whether the Active Call toggle is chechked")
    public boolean isActiveCallEnabled() {
        return mDialerHelper.get().isActiveCallEnabled();
    }

    @Rpc(description = "Dial a number in call dial pad when call is in progress.")
    public void inCallDialPad(String phoneNumber) {
        mDialerHelper.get().inCallDialPad(phoneNumber);
    }

    @Rpc(description = "Mute Call.")
    public void muteCall() {
        mDialerHelper.get().muteCall();
    }

    @Rpc(description = "Unmute Call.")
    public void unmuteCall() {
        mDialerHelper.get().unmuteCall();
    }

    @Rpc(description = "Ongoing Call on homescreen.")
    public boolean isOngoingCallDisplayedOnHome() {
        return mDialerHelper.get().isOngoingCallDisplayedOnHome();
    }

    @Rpc(description = "Open Phone from Home Screen card.")
    public void openPhoneAppFromHome() {
        mDialerHelper.get().openPhoneAppFromHome();
    }

    @Rpc(description = "Change audio source to Phone when the call is in progress.")
    public void changeAudioSourceToPhone() {
        mDialerHelper.get().changeAudioSource(IAutoDialHelper.AudioSource.PHONE);
    }

    @Rpc(description = "Change audio source to Car Speakers when the call is in progress.")
    public void changeAudioSourceToCarSpeakers() {
        mDialerHelper.get().changeAudioSource(IAutoDialHelper.AudioSource.CAR_SPEAKERS);
    }

    @Rpc(description = "Call Most Recent History.")
    public void callMostRecentHistory() {
        mDialerHelper.get().callMostRecentHistory();
    }

    @Rpc(description = "Get contact name while the call is in progress.")
    public String getContactName() {
        return mDialerHelper.get().getContactName();
    }

    @Rpc(description = "Get contact type (Work, Mobile, Home) while the call is in progress.")
    public String getContactType() {
        return mDialerHelper.get().getContactType();
    }

    @Rpc(description = "Search contact by name.")
    public void searchContactsByName(String contact) {
        mDialerHelper.get().searchContactsByName(contact);
    }

    @Rpc(description = "Search contact by number.")
    public void searchContactsByNumber(String number) {
        mDialerHelper.get().searchContactsByNumber(number);
    }

    @Rpc(description = "Get first contact search result.")
    public String getFirstSearchResult() {
        return mDialerHelper.get().getFirstSearchResult();
    }

    @Rpc(description = "Sort contact list by First Name.")
    public void sortContactListByFirstName() {
        mDialerHelper.get().sortContactListBy(IAutoDialHelper.OrderType.FIRST_NAME);
    }

    @Rpc(description = "Sort contact list by Last Name.")
    public void sortContactListByLastName() {
        mDialerHelper.get().sortContactListBy(IAutoDialHelper.OrderType.LAST_NAME);
    }

    @Rpc(description = "Get first contact from contacts list.")
    public String getFirstContactFromContactList() {
        return mDialerHelper.get().getFirstContactFromContactList();
    }

    @Rpc(description = "Check if given contact is in Favorites.")
    public boolean isContactInFavorites(String contact) {
        return mDialerHelper.get().isContactInFavorites(contact);
    }

    @Rpc(description = "Bluetooth HFP Error")
    public boolean isBluetoothHfpErrorDisplayed() {
        return mDialerHelper.get().isBluetoothHfpErrorDisplayed();
    }

    @Rpc(description = "Open details page for given contact.")
    public void openDetailsPage(String contact) {
        mDialerHelper.get().openDetailsPage(contact);
    }

    @Rpc(description = "Open details page for the first contact.")
    public void openFirstContactDetails() {
        mDialerHelper.get().openFirstContactDetails();
    }

    @Rpc(description = "Open Contacts List.")
    public void openContacts() {
        mDialerHelper.get().openContacts();
    }

    /** Press 'Device' prompt which comes up when trying to transfer files to a device. */
    @Rpc(description = "Press 'Device' on a prompt, if present.")
    public void pressDevice() {
        mDialerHelper.get().pressDeviceOnPrompt();
    }

    /** Press Active Call toggle */
    @Rpc(description = "Press Active Call toggle")
    public void pressActiveCallToggle() {
        mDialerHelper.get().pressActiveCallToggle();
    }

    /** Press Active Call toggle */
    @Rpc(description = "Press the dialer button on the phone card on the home screen.")
    public void pressDialerButtonOnPhoneCard() {
        mDialerHelper.get().pressDialerButtonOnPhoneCard();
    }

    /** Rpc to press the Mobile call action button on a contact page */
    @Rpc(description = "Press the Mobile call button on a contact page")
    public void pressMobileCallOnContact() {
        mDialerHelper.get().pressMobileCallOnContact();
    }

    /** Rpc to press a search result with a given name */
    @Rpc(description = "Press search result with a given name")
    public void pressContactResult(String expectedName) {
        mDialerHelper.get().pressContactResult(expectedName);
    }

    @Rpc(description = "Get list of visible contacts")
    public List<String> getListOfAllContacts() {
        return mDialerHelper.get().getListOfAllVisibleContacts();
    }

    @Rpc(description = "Add Favorites from favorites tab")
    public void addFavoritesFromFavoritesTab(String contact) {
        mDialerHelper.get().addFavoritesFromFavoritesTab(contact);
    }

    @Rpc(description = "Open Phone Button in Bluetooth Palette")
    public void clickPhoneButton() {
        mDialerHelper.get().clickPhoneButton();
    }

    @Rpc(description = "is Recents displayed in Dialer page ")
    public boolean verifyDialerRecentsTab() {
        return mDialerHelper.get().verifyDialerRecentsTab();
    }

    @Rpc(description = "is Contacts displayed in Dialer page ")
    public boolean verifyDialerContactsTab() {
        return mDialerHelper.get().verifyDialerContactsTab();
    }

    @Rpc(description = "is Favorites displayed in Dialer page ")
    public boolean verifyDialerFavoritesTab() {
        return mDialerHelper.get().verifyDialerFavoritesTab();
    }

    @Rpc(description = "is Dialpad displayed in Dialer page ")
    public boolean verifyDialerDialpadTab() {
        return mDialerHelper.get().verifyDialerDialpadTab();
    }

    @Override
    public void shutdown() {}
}
