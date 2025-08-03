/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.dltdoa.DlTDoAMeasurement;
import com.google.uwb.support.dltdoa.DlTDoARangingRoundsUpdate;
import com.google.uwb.support.dltdoa.DlTDoARangingRoundsUpdateStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DlTDoATests {

    @Test
    public void dlTDoAMeasurementTest() {
        int messageType = 0x02;
        int messageControl = 0x513;
        int blockIndex = 4;
        int roundIndex = 6;
        int nLoS = 40;
        long txTimestamp = 40_000L;
        long rxTimestamp = 50_000L;
        float anchorCfo = 433.33f;
        float cfo = 56.33f;
        long initiatorReplyTime = 100;
        long responderReplyTime = 200;
        int initiatorResponderTof = 400;
        byte[] anchorLocation = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        byte[] activeRangingRounds = new byte[]{0x01, 0x02};

        DlTDoAMeasurement dlTDoAMeasurement = new DlTDoAMeasurement.Builder()
                .setMessageType(messageType)
                .setMessageControl(messageControl)
                .setBlockIndex(blockIndex)
                .setRoundIndex(roundIndex)
                .setNLoS(nLoS)
                .setTxTimestamp(txTimestamp)
                .setRxTimestamp(rxTimestamp)
                .setAnchorCfo(anchorCfo)
                .setCfo(cfo)
                .setInitiatorReplyTime(initiatorReplyTime)
                .setResponderReplyTime(responderReplyTime)
                .setInitiatorResponderTof(initiatorResponderTof)
                .setAnchorLocation(anchorLocation)
                .setActiveRangingRounds(activeRangingRounds)
                .build();

        DlTDoAMeasurement fromBundle = DlTDoAMeasurement.fromBundle(dlTDoAMeasurement.toBundle());

        assertEquals(fromBundle.getMessageType(), messageType);
        assertEquals(fromBundle.getMessageControl(), messageControl);
        assertEquals(fromBundle.getBlockIndex(), blockIndex);
        assertEquals(fromBundle.getRoundIndex(), roundIndex);
        assertEquals(fromBundle.getNLoS(), nLoS);
        assertEquals(fromBundle.getTxTimestamp(), txTimestamp);
        assertEquals(fromBundle.getRxTimestamp(), rxTimestamp);
        assertEquals(fromBundle.getAnchorCfo(), anchorCfo, 0.0);
        assertEquals(fromBundle.getCfo(), cfo, 0.0);
        assertEquals(fromBundle.getInitiatorReplyTime(), initiatorReplyTime);
        assertEquals(fromBundle.getResponderReplyTime(), responderReplyTime);
        assertEquals(fromBundle.getInitiatorResponderTof(), initiatorResponderTof);
        assertArrayEquals(fromBundle.getAnchorLocation(), anchorLocation);
        assertArrayEquals(fromBundle.getActiveRangingRounds(), activeRangingRounds);
    }

    @Test
    public void dlTDoARangingRoundsUpdateTest() {
        int sessionId = 1234;
        int noOfActiveRangingRounds = 3;
        byte[] rangingRoundIndexes = new byte[]{0x01, 0x02, 0x03};

        DlTDoARangingRoundsUpdate dlTDoARangingRoundsUpdate = new DlTDoARangingRoundsUpdate
                .Builder()
                .setSessionId(sessionId)
                .setNoOfRangingRounds(noOfActiveRangingRounds)
                .setRangingRoundIndexes(rangingRoundIndexes)
                .build();

        DlTDoARangingRoundsUpdate fromBundle = DlTDoARangingRoundsUpdate.fromBundle(
                dlTDoARangingRoundsUpdate.toBundle());

        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getNoOfRangingRounds(), noOfActiveRangingRounds);
        assertArrayEquals(fromBundle.getRangingRoundIndexes(), rangingRoundIndexes);
    }

    @Test
    public void dlTDoARangingRoundsUpdateStatusTest() {
        int status = 1;
        int noOfActiveRangingRounds = 2;
        byte[] rangingRoundIndexes = new byte[]{0x02, 0x03};

        DlTDoARangingRoundsUpdateStatus dlTDoARangingRoundsUpdateStatus =
                new DlTDoARangingRoundsUpdateStatus.Builder()
                        .setStatus(status)
                        .setNoOfRangingRounds(noOfActiveRangingRounds)
                        .setRangingRoundIndexes(rangingRoundIndexes)
                        .build();

        DlTDoARangingRoundsUpdateStatus fromBundle = DlTDoARangingRoundsUpdateStatus.fromBundle(
                dlTDoARangingRoundsUpdateStatus.toBundle());

        assertEquals(fromBundle.getStatus(), status);
        assertEquals(fromBundle.getNoOfRangingRounds(), noOfActiveRangingRounds);
        assertArrayEquals(fromBundle.getRangingRoundIndexes(), rangingRoundIndexes);
    }
}
