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
 * limitations under the License
 */

package com.android.server.connectivity.mdns;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.android.net.module.util.HexDump;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Locale;

@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MdnsPacketReaderTests {

    @Test
    public void testLimits() throws IOException {
        byte[] data = new byte[25];
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length);

        // After creating a new reader, confirm that the remaining is equal to the packet length
        // (or that there is no temporary limit).
        MdnsPacketReader packetReader = new MdnsPacketReader(datagramPacket);
        assertEquals(data.length, packetReader.getRemaining());

        // Confirm that we can set the temporary limit to 0.
        packetReader.setLimit(0);
        assertEquals(0, packetReader.getRemaining());

        // Confirm that we can clear the temporary limit, and restore to the length of the packet.
        packetReader.clearLimit();
        assertEquals(data.length, packetReader.getRemaining());

        // Confirm that we can set the temporary limit to the actual length of the packet.
        // While parsing packets, it is common to set the limit to the length of the packet.
        packetReader.setLimit(data.length);
        assertEquals(data.length, packetReader.getRemaining());

        // Confirm that we ignore negative limits.
        packetReader.setLimit(-10);
        assertEquals(data.length, packetReader.getRemaining());

        // Confirm that we can set the temporary limit to something less than the packet length.
        packetReader.setLimit(data.length / 2);
        assertEquals(data.length / 2, packetReader.getRemaining());

        // Confirm that we throw an exception if trying to set the temporary limit beyond the
        // packet length.
        packetReader.clearLimit();
        try {
            packetReader.setLimit(data.length * 2 + 1);
            fail("Should have thrown an IOException when trying to set the temporary limit beyond "
                    + "the packet length");
        } catch (IOException e) {
            // Expected
        } catch (RuntimeException e) {
            fail(String.format(
                    Locale.ROOT,
                    "Should not have thrown any other exception except " + "for IOException: %s",
                    e.getMessage()));
        }
        assertEquals(data.length, packetReader.getRemaining());
    }

    @Test
    public void testInfinitePtrLoop() {
        // Fake mdns response packet label portion which has infinite ptr loop.
        final byte[] infinitePtrLoopData = HexDump.hexStringToByteArray(
                "054C4142454C" // label "LABEL"
                        + "0454455354" // label "TEST"
                        + "C006"); // PTR to second label.
        MdnsPacketReader packetReader = new MdnsPacketReader(
                infinitePtrLoopData, infinitePtrLoopData.length,
                MdnsFeatureFlags.newBuilder().setIsLabelCountLimitEnabled(true).build());
        assertThrows(IOException.class, packetReader::readLabels);
    }
}
