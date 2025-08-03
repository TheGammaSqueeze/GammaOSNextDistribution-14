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

package com.android.bluetooth.pbap;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.ContactsContract;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.pbap.BluetoothPbapVcardManager.ContactCursorFilter;
import com.android.bluetooth.pbap.BluetoothPbapVcardManager.PropertySelector;
import com.android.bluetooth.pbap.BluetoothPbapVcardManager.VCardFilter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicInteger;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapVcardManagerNestedClassesTest {

    @Mock
    Context mContext;

    @Mock
    Resources mResources;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getResources()).thenReturn(mResources);
    }

    @Test
    public void VCardFilter_isPhotoEnabled_whenFilterIncludesPhoto_returnsTrue() {
        final byte photoEnableBit = 1 << 3;
        byte[] filter = new byte[] {photoEnableBit};
        VCardFilter vCardFilter = new VCardFilter(filter);

        assertThat(vCardFilter.isPhotoEnabled()).isTrue();
    }

    @Test
    public void VCardFilter_isPhotoEnabled_whenFilterExcludesPhoto_returnsFalse() {
        byte[] filter = new byte[] {(byte) 0x00};
        VCardFilter vCardFilter = new VCardFilter(filter);

        assertThat(vCardFilter.isPhotoEnabled()).isFalse();
    }

    @Test
    public void VCardFilter_apply_whenFilterIsNull_returnsSameVcard() {
        VCardFilter vCardFilter = new VCardFilter(/*filter=*/null);

        String vCard = "FN:Full Name";
        assertThat(vCardFilter.apply(vCard, /*vCardType21=*/ true)).isEqualTo(vCard);
    }

    @Test
    public void VCardFilter_apply_returnsSameVcard() {
        final String separator = System.getProperty("line.separator");
        String vCard = "FN:Test Full Name" + separator
                + "EMAIL:android@android.com:" + separator
                + "X-IRMC-CALL-DATETIME:20170314T173942" + separator;

        byte[] emailExcludeFilter = new byte[] {(byte) 0xFE, (byte) 0xFF};
        VCardFilter vCardFilter = new VCardFilter(/*filter=*/ emailExcludeFilter);
        String expectedVCard = "FN:Test Full Name" + separator
                + "X-IRMC-CALL-DATETIME:20170314T173942" + separator;

        assertThat(vCardFilter.apply(vCard, /*vCardType21=*/ true))
                .isEqualTo(expectedVCard);
    }

    @Test
    public void PropertySelector_checkVCardSelector_atLeastOnePropertyExists_returnsTrue() {
        final String separator = System.getProperty("line.separator");
        String vCard = "FN:Test Full Name" + separator
                + "EMAIL:android@android.com:" + separator
                + "TEL:0123456789" + separator;

        byte[] emailSelector = new byte[] {0x01, 0x00};
        PropertySelector selector = new PropertySelector(emailSelector);

        assertThat(selector.checkVCardSelector(vCard, "0")).isTrue();
    }

    @Test
    public void PropertySelector_checkVCardSelector_atLeastOnePropertyExists_returnsFalse() {
        final String separator = System.getProperty("line.separator");
        String vCard = "FN:Test Full Name" + separator
                + "EMAIL:android@android.com:" + separator
                + "TEL:0123456789" + separator;

        byte[] organizationSelector = new byte[] {0x01, 0x00, 0x00};
        PropertySelector selector = new PropertySelector(organizationSelector);

        assertThat(selector.checkVCardSelector(vCard, "0")).isFalse();
    }

    @Test
    public void PropertySelector_checkVCardSelector_allPropertiesExist_returnsTrue() {
        final String separator = System.getProperty("line.separator");
        String vCard = "FN:Test Full Name" + separator
                + "EMAIL:android@android.com:" + separator
                + "TEL:0123456789" + separator;

        byte[] fullNameAndEmailSelector = new byte[] {0x01, 0x02};
        PropertySelector selector = new PropertySelector(fullNameAndEmailSelector);

        assertThat(selector.checkVCardSelector(vCard, "1")).isTrue();
    }

    @Test
    public void PropertySelector_checkVCardSelector_allPropertiesExist_returnsFalse() {
        final String separator = System.getProperty("line.separator");
        String vCard = "FN:Test Full Name" + separator
                + "EMAIL:android@android.com:" + separator
                + "TEL:0123456789" + separator;

        byte[] fullNameAndOrganizationSelector = new byte[] {0x01, 0x00, 0x02};
        PropertySelector selector = new PropertySelector(fullNameAndOrganizationSelector);

        assertThat(selector.checkVCardSelector(vCard, "1")).isFalse();
    }

    @Test
    public void ContactCursorFilter_filterByOffset() {
        Cursor contactCursor = mock(Cursor.class);
        int contactIdColumn = 5;
        when(contactCursor.getColumnIndex(ContactsContract.Data.CONTACT_ID))
                .thenReturn(contactIdColumn);

        long[] contactIds = new long[] {1001, 1001, 1002, 1002, 1003, 1003, 1004};
        AtomicInteger currentPos = new AtomicInteger(-1);
        when(contactCursor.moveToNext()).thenAnswer(invocation -> {
            if (currentPos.get() < contactIds.length - 1) {
                currentPos.incrementAndGet();
                return true;
            }
            return false;
        });
        when(contactCursor.getLong(contactIdColumn))
                .thenAnswer(invocation -> contactIds[currentPos.get()]);

        int offset = 3;
        Cursor resultCursor = ContactCursorFilter.filterByOffset(contactCursor, offset);

        // Should return cursor containing [1003]
        assertThat(resultCursor.getCount()).isEqualTo(1);
        assertThat(getContactsIdFromCursor(resultCursor, 0)).isEqualTo(1003);
    }

    @Test
    public void ContactCursorFilter_filterByRange() {
        Cursor contactCursor = mock(Cursor.class);
        int contactIdColumn = 5;
        when(contactCursor.getColumnIndex(ContactsContract.Data.CONTACT_ID))
                .thenReturn(contactIdColumn);

        long[] contactIds = new long[] {1001, 1001, 1002, 1002, 1003, 1003, 1004};
        AtomicInteger currentPos = new AtomicInteger(-1);
        when(contactCursor.moveToNext()).thenAnswer(invocation -> {
            if (currentPos.get() < contactIds.length - 1) {
                currentPos.incrementAndGet();
                return true;
            }
            return false;
        });
        when(contactCursor.getLong(contactIdColumn))
                .thenAnswer(invocation -> contactIds[currentPos.get()]);

        int startPoint = 2;
        int endPoint = 4;
        Cursor resultCursor = ContactCursorFilter.filterByRange(
                contactCursor, startPoint, endPoint);

        // Should return cursor containing [1002, 1003, 1004]
        assertThat(resultCursor.getCount()).isEqualTo(3);
        assertThat(getContactsIdFromCursor(resultCursor, 0)).isEqualTo(1002);
        assertThat(getContactsIdFromCursor(resultCursor, 1)).isEqualTo(1003);
        assertThat(getContactsIdFromCursor(resultCursor, 2)).isEqualTo(1004);
    }

    private long getContactsIdFromCursor(Cursor cursor, int position) {
        int index = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
        cursor.moveToPosition(position);
        return cursor.getLong(index);
    }
}
