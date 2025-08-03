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

package android.platform.tests;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoDialContactDetailsHelper;
import android.platform.helpers.IAutoDialContactDetailsHelper.ContactType;
import android.platform.helpers.IAutoDialHelper;
import android.platform.helpers.IAutoDialHelper.AudioSource;
import android.platform.helpers.IAutoDialHelper.OrderType;
import android.platform.helpers.IAutoVehicleHardKeysHelper;
import android.platform.test.option.StringOption;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DialTest {
    public static final String DIAL_A_NUMBER = "Dial a number";
    public static final String DIALED_CONTACT = "Aaron";
    public static final String DETAILED_CONTACT = "Aaron";
    public static final String CONTACT_TYPE = "Work";

    private static final String SMALL_NUMBER_PARAM = "small-phone-number";
    private static final String LARGE_NUMBER_PARAM = "large-phone-number";
    private static final String SEARCH_CONTACT_NUMBER_PARAM = "search-contact-number";
    private static final String SEARCH_CONTACT_NAME_PARAM = "search-contact-name";

    @ClassRule
    public static StringOption mSmallPhoneNumber =
            new StringOption(SMALL_NUMBER_PARAM).setRequired(true);

    @ClassRule
    public static StringOption mLargePhoneNumber =
            new StringOption(LARGE_NUMBER_PARAM).setRequired(true);

    @ClassRule
    public static StringOption mSearchContactNumber =
            new StringOption(SEARCH_CONTACT_NUMBER_PARAM).setRequired(true);

    @ClassRule
    public static StringOption mSearchContactName =
            new StringOption(SEARCH_CONTACT_NAME_PARAM).setRequired(true);

    private HelperAccessor<IAutoDialHelper> mDialerHelper;
    private HelperAccessor<IAutoVehicleHardKeysHelper> mVehicleHardKeysHelper;
    private HelperAccessor<IAutoDialContactDetailsHelper> mContactDetailsHelper;

    public DialTest() throws Exception {
        mDialerHelper = new HelperAccessor<>(IAutoDialHelper.class);
        mContactDetailsHelper = new HelperAccessor<>(IAutoDialContactDetailsHelper.class);
        mVehicleHardKeysHelper = new HelperAccessor<>(IAutoVehicleHardKeysHelper.class);
    }

    @After
    public void endCall() {
        mVehicleHardKeysHelper.get().pressEndCallKey();
    }

    @Test
    public void testDialSmallNumber() {
        mDialerHelper.get().dialANumber(mSmallPhoneNumber.get());
        mDialerHelper.get().makeCall();
        String actualDialedNumber = mDialerHelper.get().getDialedNumber();
        assertEquals(mSmallPhoneNumber.get(), actualDialedNumber.replaceAll("[-()\\s]", ""));
        mDialerHelper.get().endCall();
    }

    @Test
    public void testDialLargeNumber() {
        mDialerHelper.get().dialANumber(mLargePhoneNumber.get());
        mDialerHelper.get().makeCall();
        String actualDialedNumber = mDialerHelper.get().getDialedNumber();
        assertEquals(mLargePhoneNumber.get(), actualDialedNumber.replaceAll("[-()\\s]", ""));
        mDialerHelper.get().endCall();
    }

    @Test
    public void testHistoryUpdatesCalledNumber() {
        mDialerHelper.get().dialANumber(mSmallPhoneNumber.get());
        mDialerHelper.get().makeCall();
        mDialerHelper.get().endCall();
        mDialerHelper.get().openCallHistory();
        assertTrue(
                "Call History did not update",
                mDialerHelper.get().getRecentCallHistory().equals(mSmallPhoneNumber.get()));
    }

    @Test
    public void testHistoryUpdatesCalledContactName() {
        mDialerHelper.get().open();
        String dialContactByName = mSearchContactName.get();
        mDialerHelper.get().callContact(dialContactByName);
        mDialerHelper.get().endCall();
        mDialerHelper.get().openCallHistory();
        assertTrue(
                "Call History did not update",
                mDialerHelper.get().getRecentCallHistory().equals(dialContactByName));
    }

    @Test
    public void testDeleteDialedNumber() {
        mDialerHelper.get().dialANumber(mSmallPhoneNumber.get());
        mDialerHelper.get().deleteDialedNumber();
        String numberAfterDelete = mDialerHelper.get().getNumberInDialPad();
        assertTrue(DIAL_A_NUMBER.equals(numberAfterDelete));
    }

    @Test
    public void testMuteUnmuteCall() {
        mDialerHelper.get().dialANumber(mSmallPhoneNumber.get());
        mDialerHelper.get().makeCall();
        try {
            mDialerHelper.get().muteCall();
            mDialerHelper.get().unmuteCall();
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } finally {
            mDialerHelper.get().endCall();
        }
    }

    @Test
    public void testEndCallHardkey() {
        mDialerHelper.get().dialANumber(mLargePhoneNumber.get());
        mDialerHelper.get().makeCall();
        String actualDialedNumber = mDialerHelper.get().getDialedNumber();
        assertEquals(mLargePhoneNumber.get(), actualDialedNumber.replaceAll("[-()\\s]", ""));
        mVehicleHardKeysHelper.get().pressEndCallKey();
    }

    @Test
    public void testCallAudioSourceTransfer() {
        mDialerHelper.get().dialANumber(mSmallPhoneNumber.get());
        mDialerHelper.get().makeCall();
        mDialerHelper.get().changeAudioSource(AudioSource.PHONE);
        mDialerHelper.get().changeAudioSource(AudioSource.CAR_SPEAKERS);
        mDialerHelper.get().endCall();
    }

    @Test
    public void testCallFromHistory() {
        mDialerHelper.get().dialANumber(mSmallPhoneNumber.get());
        mDialerHelper.get().makeCall();
        mDialerHelper.get().endCall();
        mDialerHelper.get().openCallHistory();
        mDialerHelper.get().callMostRecentHistory();
        assertTrue(
                "History is not same as dialed number.",
                mDialerHelper.get().getDialedContactName().equals(mSmallPhoneNumber.get()));
        mDialerHelper.get().endCall();
    }

    @Test
    public void testDisplayedNameMatchesCalledContactName() {
        mDialerHelper.get().open();
        String dialContactByName = mSearchContactName.get();
        mDialerHelper.get().callContact(dialContactByName);
        assertTrue(
                "Contact name is not the same",
                mDialerHelper.get().getContactName().contains(dialContactByName));
        mDialerHelper.get().endCall();
    }

    @Test
    public void testDisplayedContactTypeMatchesCalledContactType() {
        mDialerHelper.get().open();
        mDialerHelper.get().callContact(mSearchContactName.get());
        assertTrue(
                "Contact detail is not the same",
                mDialerHelper.get().getContactType().equalsIgnoreCase(CONTACT_TYPE));
        mDialerHelper.get().endCall();
    }

    @Test
    public void testSearchContactByName() {
        mDialerHelper.get().open();
        String dialContactByName = mSearchContactName.get();
        mDialerHelper.get().searchContactsByName(dialContactByName);
        assertEquals(
                "Cannot find contact",
                dialContactByName,
                mDialerHelper.get().getFirstSearchResult());
    }

    @Test
    public void testSearchContactByNumber() {
        mDialerHelper.get().open();
        mDialerHelper.get().searchContactsByNumber(mSearchContactNumber.get());
        assertEquals(
                "Cannot find contact",
                mSearchContactName.get(),
                mDialerHelper.get().getFirstSearchResult());
    }

    @Test
    public void testSortContacts() {
        mDialerHelper.get().open();
        mDialerHelper.get().sortContactListBy(OrderType.LAST_NAME);
        assertEquals(
                "Order by last name is not correct.",
                mDialerHelper.get().getFirstContactFromContactList(),
                DIALED_CONTACT);
        mDialerHelper.get().sortContactListBy(OrderType.FIRST_NAME);
        assertEquals(
                "Order is not correct.",
                mDialerHelper.get().getFirstContactFromContactList(),
                DIALED_CONTACT);
    }

    @Test
    public void testAddRemoveFavoriteContact() {
        mDialerHelper.get().open();
        String favoritesContact = mSearchContactName.get();
        mDialerHelper.get().openDetailsPage(favoritesContact);
        mContactDetailsHelper.get().addRemoveFavoriteContact();
        mContactDetailsHelper.get().closeDetailsPage();
        assertTrue(
                "Contact is not added to favorites.",
                mDialerHelper.get().isContactInFavorites(favoritesContact));
        mDialerHelper.get().openDetailsPage(favoritesContact);
        mContactDetailsHelper.get().addRemoveFavoriteContact();
        mContactDetailsHelper.get().closeDetailsPage();
        assertFalse(
                "Contact is not removed from favorites.",
                mDialerHelper.get().isContactInFavorites(favoritesContact));
    }

    @Test
    public void testMakeCallFromContactDetailsPage() {
        mDialerHelper.get().open();
        mDialerHelper.get().openDetailsPage(DETAILED_CONTACT);
        mContactDetailsHelper.get().makeCallFromDetailsPageByType(ContactType.MOBILE);
        assertTrue(
                "Contact name is not the same",
                mDialerHelper.get().getContactName().contains(DETAILED_CONTACT));
        mDialerHelper.get().endCall();
        mContactDetailsHelper.get().closeDetailsPage();
    }
}
