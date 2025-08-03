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

package com.android.tv.mdnsoffloadmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import device.google.atv.mdns_offload.IMdnsOffload.MdnsProtocolData.MatchCriteria;

@SmallTest
public class MdnsPacketParserTest {

    @Test
    public void testExtractFullNameFromSingleLabel() {
        byte[] array = new byte[]{'x', 'x', 'x', 3, 'a', 't', 'v', 0};
        String fullName = MdnsPacketParser.extractFullName(array, 3);
        assertEquals("atv.", fullName);
    }

    @Test
    public void testExtractFullNameFromPointer() {
        byte[] array = new byte[]{3, 'i', 's', 'o', 0, (byte) 0xC0, 0x00};
        String fullName = MdnsPacketParser.extractFullName(array, 5);
        assertEquals("iso.", fullName);
    }

    @Test
    public void testExtractFullNameFromMultiLabel() {
        byte[] array = new byte[]{'x', 'x', 'x', 3, 'a', 't', 'v', 3, 'g', 't', 'v', 0};
        String fullName = MdnsPacketParser.extractFullName(array, 3);
        assertEquals("atv.gtv.", fullName);
    }

    @Test
    public void testExtractFullNameFromLabelPointer() {
        byte[] array = new byte[]{3, 'i', 's', 'o', 0,//
                3, 'a', 't', 'v', 3, 'g', 't', 'v', (byte) 0xC0, 0x00};
        String fullName = MdnsPacketParser.extractFullName(array, 9);
        assertEquals("gtv.iso.", fullName);
    }

    @Test
    public void testExtractFullNameFromLabelDualPointerLongOffset() {
        byte[] array = new byte[]{3, 'i', 's', 'o', 0,//
                3, 'a', 't', 'v', 3, 'g', 't', 'v', (byte) 0xC0, 0x64};

        //Add the string 4http at offset 0x64 and back to pointer 0x00
        byte[] longArray = Arrays.copyOf(array, 120);
        longArray[0x64] = 4;
        longArray[0x64 + 1] = 'h';
        longArray[0x64 + 2] = 't';
        longArray[0x64 + 3] = 't';
        longArray[0x64 + 4] = 'p';
        longArray[0x64 + 5] = (byte) 0xC0;
        longArray[0x64 + 6] = 0x00;

        String fullName = MdnsPacketParser.extractFullName(longArray, 9);
        assertEquals("gtv.http.iso.", fullName);
    }

    @Test
    public void testExtractFullNameBadPointer() {
        byte[] array = new byte[]{3, 'i', 's', 'o', 0, (byte) 0xC0, 0x20};
        assertThrows(
                "Setting cursor on negative offset is not allowed.",
                IllegalArgumentException.class,
                () -> MdnsPacketParser.extractFullName(array, -10)
        );
    }

    @Test
    public void testExtractFullNameLabelTooLong() {
        byte[] longArray = new byte[70];
        Arrays.fill(longArray, (byte) 'a');
        // Labels maximum allowed size is 64 so this fall in the unknown categorie of
        // pointer 01xxxxxx or 10xxxxxx
        longArray[0] = 65;
        longArray[66] = 0x00; // ther array is [65, 65 times 'a', 0x00]

        assertThrows(
                "mDNS response packet is badly formed. Not enough data.",
                IllegalArgumentException.class,
                () -> MdnsPacketParser.extractFullName(longArray, 0)
        );
    }

    @Test
    public void testExtractMatchCriteriaFailureQueryCount() {
        //1 query, 2 answers, 0 authority, 0 additional.
        byte[] array = new byte[]{0, 0, 0, 0, 0, 1, 0, 2, 0, 0, 0, 0};
        assertThrows(
                "mDNS response packet contains data that is not answers",
                IllegalArgumentException.class,
                () -> MdnsPacketParser.extractMatchCriteria(array)
        );
    }

    @Test
    public void testExtractMatchCriteriaFailureAuthorityCount() {
        //0 query, 2 answers, 2 authority, 0 additional.
        byte[] array = new byte[]{0, 0, 0, 0, 0, 0, 0, 2, 0, 2, 0, 0};
        assertThrows(
                "mDNS response packet contains data that is not answers",
                IllegalArgumentException.class,
                () -> MdnsPacketParser.extractMatchCriteria(array)
        );
    }

    @Test
    public void testExtractMatchCriteriaFailureAdditionalCount() {
        //0 query, 2 answers, 0 authority, 3 additional.
        byte[] array = new byte[]{0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 3};
        assertThrows(
                "mDNS response packet contains data that is not answers",
                IllegalArgumentException.class,
                () -> MdnsPacketParser.extractMatchCriteria(array)
        );
    }

    @Test
    public void testExtractMatchCriteriaSuccessOneAnswerLabel() {
        byte[] array = new byte[]{
                0, 0, 0, 0,//Id , Flags
                0, 0, 0, 1, 0, 0, 0, 0,// Header section. 1 answer.
                //Data 1
                3, 'a', 't', 'v', 0x00, //atv.
                0x00, 0x01, //type A
                (byte) 0x80, 0x01,//cache flush: True, class: in
                0, 0, 0, 5,// TTL 5sec
                0, 4, // Data with size 4
                100, 80, 40, 20 //ip: 100.80.40.20
        };

        List<MatchCriteria> criteria = MdnsPacketParser.extractMatchCriteria(array);

        assertEquals(1, criteria.size());

        MatchCriteria result = criteria.get(0);
        assertEquals(12, result.nameOffset);
        assertEquals(1, result.type);
    }

    @Test
    public void testExtractMatchCriteriaSuccessTwoAnswersPointers() {
        byte[] array = new byte[]{
                0, 0, 0, 0,//Id , Flags
                0, 0, 0, 2, 0, 0, 0, 0,// Header section. 2 answers.
                //Data 1
                3, 'a', 't', 'v', 0x00, //atv.
                0x00, 0x01, //type A
                (byte) 0x80, 0x01,//cache flush: True, class: in
                0, 0, 0, 5,// TTL 5sec
                0, 4, // Data with size 4
                100, 80, 40, 20, //ip: 100.80.40.20
                //Data 2
                3, 'g', 't', 'v', (byte) 0b11000000, 12, //gtv.[ptr->]atv.
                0x00, 16, //type TXT
                (byte) 0x80, 0x01,//cache flush: True, class: in
                0, 0, 0, 5,// TTL 5sec
                0, 3, // Data with size 3
                'i', 's', 'o' // "iso"
        };

        List<MatchCriteria> criteria = MdnsPacketParser.extractMatchCriteria(array);

        assertEquals(2, criteria.size());

        MatchCriteria result0 = criteria.get(0);
        assertEquals(12, result0.nameOffset);
        assertEquals(1, result0.type);

        MatchCriteria result1 = criteria.get(1);
        assertEquals(31, result1.nameOffset);
        assertEquals(16, result1.type);
    }

    @Test
    public void testExtractFullName() {
        byte[] array = new byte[]{
            0, 0, 0, 0,//Id , Flags
            0, 0, 0, 2, 0, 0, 0, 0,// Header section. 2 answers.
            //Data 1
            3, 'a', 't', 'v', 0x00, //atv.
            0x00, 0x01, //type A
            (byte) 0x80, 0x01,//cache flush: True, class: in
            0, 0, 0, 5,// TTL 5sec
            0, 4, // Data with size 4
            100, 80, 40, 20, //ip: 100.80.40.20
            //Data 2
            3, 'g', 't', 'v', (byte) 0b11000000, 12, //gtv.[ptr->]atv.
            0x00, 16, //type TXT
            (byte) 0x80, 0x01,//cache flush: True, class: in
            0, 0, 0, 5,// TTL 5sec
            0, 3, // Data with size 3
            'i', 's', 'o' // "iso"
        };

        List<MatchCriteria> criteria = MdnsPacketParser.extractMatchCriteria(array);
        assertEquals(2, criteria.size());
        String name0 = MdnsPacketParser.extractFullName(array, criteria.get(0).nameOffset);
        assertEquals("atv.", name0);
        String name1 = MdnsPacketParser.extractFullName(array, criteria.get(1).nameOffset);
        assertEquals("gtv.atv.", name1);
    }

    @Test
    public void testNegativeByteToUint8() {
        byte[] array = new byte[]{
                0, 0, 0, 0,//Id , Flags
                0, 0, 0, 3, 0, 0, 0, 0,// Header section. 2 answers.
                //Data 1
                3, 'a', 't', 'v', 0x00, //atv.
                0x00, 0x01, //type A
                (byte) 0x80, 0x01,//cache flush: True, class: in
                0, 0, 0, 5,// TTL 5sec
                0, 4, // Data with size 4
                100, 80, 40, 20, //ip: 100.80.40.20
                //Data 2
                3, 'g', 't', 'v', (byte) 0b11000000, 12, //gtv.[ptr->]atv.
                0x00, 16, //type TXT
                (byte) 0x80, 0x01,//cache flush: True, class: in
                0, 0, 0, 5,// TTL 5sec
                0, (byte) 130, // Data with size 130 > 127
                1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
                //Data 3
                4, 'f', 'a', 'i', 'l', 0x00, //fail.
                0x00, 0x01, //type A
                (byte) 0x80, 0x01,//cache flush: True, class: in
                0, 0, 0, 5,// TTL 5sec
                0, 4, // Data with size 4
                100, 80, 40, 20, //ip: 100.80.40.20
        };

        List<MatchCriteria> criteria = MdnsPacketParser.extractMatchCriteria(array);
        assertEquals(3, criteria.size());
        String name2 = MdnsPacketParser.extractFullName(array, criteria.get(2).nameOffset);
        assertEquals("fail.", name2);
    }

    @Test
    public void testExtractMatchCriteriaFailureTooMuchData() {
        byte[] array = new byte[]{
                0, 0, 0, 0,//Id , Flags
                0, 0, 0, 1, 0, 0, 0, 0,// Header section. 2 answers.
                //Data 1
                3, 'a', 't', 'v', 0x00, //atv.
                0x00, 0x01, //type A
                (byte) 0x80, 0x01,//cache flush: True, class: in
                0, 0, 0, 5,// TTL 5sec
                0, 4, // Data with size 4
                100, 80, 40, 20, //ip: 100.80.40.20
                //extra data.
                'e','x','t','r','a'
        };

        assertThrows(
                "mDNS response packet is badly formed. Too much data.",
                IllegalArgumentException.class,
                () -> MdnsPacketParser.extractMatchCriteria(array)
        );
    }

}