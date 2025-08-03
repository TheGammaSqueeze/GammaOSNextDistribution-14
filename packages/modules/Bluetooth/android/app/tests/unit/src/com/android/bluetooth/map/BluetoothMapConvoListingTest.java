/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.SignedLongLong;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapConvoListingTest {
    private static final long TEST_LAST_ACTIVITY_EARLIEST = 0;
    private static final long TEST_LAST_ACTIVITY_MIDDLE = 1;
    private static final long TEST_LAST_ACTIVITY_LATEST = 2;
    private static final boolean TEST_READ = true;
    private static final boolean TEST_REPORT_READ = true;

    private BluetoothMapConvoListingElement mListingElementEarliestWithReadFalse;
    private BluetoothMapConvoListingElement mListingElementMiddleWithReadFalse;
    private BluetoothMapConvoListingElement mListingElementLatestWithReadTrue;
    private BluetoothMapConvoListing mListing;

    @Before
    public void setUp() {
        mListingElementEarliestWithReadFalse = new BluetoothMapConvoListingElement();
        mListingElementEarliestWithReadFalse.setLastActivity(TEST_LAST_ACTIVITY_EARLIEST);

        mListingElementMiddleWithReadFalse = new BluetoothMapConvoListingElement();
        mListingElementMiddleWithReadFalse.setLastActivity(TEST_LAST_ACTIVITY_MIDDLE);

        mListingElementLatestWithReadTrue = new BluetoothMapConvoListingElement();
        mListingElementLatestWithReadTrue.setLastActivity(TEST_LAST_ACTIVITY_LATEST);
        mListingElementLatestWithReadTrue.setRead(TEST_READ, TEST_REPORT_READ);

        mListing = new BluetoothMapConvoListing();
        mListing.add(mListingElementEarliestWithReadFalse);
        mListing.add(mListingElementMiddleWithReadFalse);
        mListing.add(mListingElementLatestWithReadTrue);
    }

    @Test
    public void addElement() {
        final BluetoothMapConvoListing listing = new BluetoothMapConvoListing();
        assertThat(listing.getCount()).isEqualTo(0);
        listing.add(mListingElementLatestWithReadTrue);
        assertThat(listing.getCount()).isEqualTo(1);
        assertThat(listing.hasUnread()).isEqualTo(true);
    }

    @Test
    public void segment_whenCountIsLessThanOne_returnsOffsetToEnd() {
        mListing.segment(0, 1);
        assertThat(mListing.getList().size()).isEqualTo(2);
    }

    @Test
    public void segment_whenOffsetIsBiggerThanSize_returnsEmptyList() {
        mListing.segment(1, 4);
        assertThat(mListing.getList().size()).isEqualTo(0);
    }

    @Test
    public void segment_whenOffsetCountCombinationIsValid_returnsCorrectly() {
        mListing.segment(1, 1);
        assertThat(mListing.getList().size()).isEqualTo(1);
    }

    @Test
    public void sort() {
        // BluetoothMapConvoListingElements are sorted according to their mLastActivity values
        mListing.sort();
        assertThat(mListing.getList().get(0).getLastActivity()).isEqualTo(
                TEST_LAST_ACTIVITY_LATEST);
    }

    @Test
    public void equals_withSameObject_returnsTrue() {
        assertThat(mListing.equals(mListing)).isEqualTo(true);
    }

    @Test
    public void equals_withNull_returnsFalse() {
        assertThat(mListing.equals(null)).isEqualTo(false);
    }

    @Test
    public void equals_withDifferentClass_returnsFalse() {
        assertThat(mListing.equals(mListingElementEarliestWithReadFalse)).isEqualTo(false);
    }

    @Test
    public void equals_withDifferentRead_returnsFalse() {
        final BluetoothMapConvoListing listingWithDifferentRead = new BluetoothMapConvoListing();
        assertThat(mListing.equals(listingWithDifferentRead)).isEqualTo(false);
    }

    @Test
    public void equals_whenNullComparedWithNonNullList_returnsFalse() {
        final BluetoothMapConvoListing listingWithNullList = new BluetoothMapConvoListing();
        final BluetoothMapConvoListing listingWithNonNullList = new BluetoothMapConvoListing();
        listingWithNonNullList.add(mListingElementEarliestWithReadFalse);

        assertThat(listingWithNullList.equals(listingWithNonNullList)).isEqualTo(false);
    }

    @Test
    public void equals_whenNonNullListsAreDifferent_returnsFalse() {
        final BluetoothMapConvoListing listingWithListSizeOne = new BluetoothMapConvoListing();
        listingWithListSizeOne.add(mListingElementEarliestWithReadFalse);

        final BluetoothMapConvoListing listingWithListSizeTwo = new BluetoothMapConvoListing();
        listingWithListSizeTwo.add(mListingElementEarliestWithReadFalse);
        listingWithListSizeTwo.add(mListingElementMiddleWithReadFalse);

        assertThat(listingWithListSizeOne.equals(listingWithListSizeTwo)).isEqualTo(false);
    }

    @Test
    public void equals_whenNonNullListsAreTheSame_returnsTrue() {
        final BluetoothMapConvoListing listing = new BluetoothMapConvoListing();
        final BluetoothMapConvoListing listingEqual = new BluetoothMapConvoListing();
        listing.add(mListingElementEarliestWithReadFalse);
        listingEqual.add(mListingElementEarliestWithReadFalse);
        assertThat(listing.equals(listingEqual)).isEqualTo(true);
    }

    @Test
    public void encodeToXml_thenAppendFromXml() throws Exception {
        final BluetoothMapConvoListing listingToAppend = new BluetoothMapConvoListing();
        final BluetoothMapConvoListingElement listingElementToAppendOne =
                new BluetoothMapConvoListingElement();
        final BluetoothMapConvoListingElement listingElementToAppendTwo =
                new BluetoothMapConvoListingElement();

        final long testIdOne = 1111;
        final long testIdTwo = 1112;

        final SignedLongLong signedLongLongIdOne = new SignedLongLong(testIdOne, 0);
        final SignedLongLong signedLongLongIdTwo = new SignedLongLong(testIdTwo, 0);

        listingElementToAppendOne.setConvoId(0, testIdOne);
        listingElementToAppendTwo.setConvoId(0, testIdTwo);

        listingToAppend.add(listingElementToAppendOne);
        listingToAppend.add(listingElementToAppendTwo);

        final InputStream listingStream = new ByteArrayInputStream(listingToAppend.encode());

        BluetoothMapConvoListing listing = new BluetoothMapConvoListing();
        listing.appendFromXml(listingStream);
        assertThat(listing.getList().size()).isEqualTo(2);
        assertThat(listing.getList().get(0).getConvoId()).isEqualTo(
                signedLongLongIdOne.toHexString());
        assertThat(listing.getList().get(1).getConvoId()).isEqualTo(
                signedLongLongIdTwo.toHexString());
    }
}