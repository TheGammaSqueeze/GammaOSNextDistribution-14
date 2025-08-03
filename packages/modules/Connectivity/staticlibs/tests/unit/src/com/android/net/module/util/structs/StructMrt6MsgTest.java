package com.android.net.module.util.structs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.InetAddresses;
import androidx.test.runner.AndroidJUnit4;
import com.android.net.module.util.Struct;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StructMrt6MsgTest {

    private static final byte[] MSG_BYTES = new byte[] {
        0, /* mbz = 0 */
        1, /* message type = MRT6MSG_NOCACHE */
        1, 0, /* mif u16 = 1 */
        0, 0, 0, 0, /* padding */
        32, 1, 13, -72, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, /* source=2001:db8::1 */
        -1, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 18, 52, /* destination=ff05::1234 */
    };

    private static final Inet6Address SOURCE =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8::1");
    private static final Inet6Address GROUP =
            (Inet6Address) InetAddresses.parseNumericAddress("ff05::1234");

    @Test
    public void testParseMrt6Msg() {
        final ByteBuffer buf = ByteBuffer.wrap(MSG_BYTES);
        StructMrt6Msg mrt6Msg = StructMrt6Msg.parse(buf);

        assertEquals(1, mrt6Msg.mif);
        assertEquals(StructMrt6Msg.MRT6MSG_NOCACHE, mrt6Msg.msgType);
        assertEquals(SOURCE, mrt6Msg.src);
        assertEquals(GROUP, mrt6Msg.dst);
    }

    @Test
    public void testWriteToBytes() {
        StructMrt6Msg msg = new StructMrt6Msg((byte) 0 /* mbz must be 0 */,
                StructMrt6Msg.MRT6MSG_NOCACHE,
                1 /* mif */,
                SOURCE,
                GROUP);
        byte[] bytes = msg.writeToBytes();

        assertArrayEquals(MSG_BYTES, bytes);
    }
}
