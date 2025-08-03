package com.android.net.module.util.structs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.util.ArraySet;
import androidx.test.runner.AndroidJUnit4;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StructMif6ctlTest {
    private static final byte[] MSG_BYTES = new byte[] {
        1, 0,  /* mif6cMifi */
        0, /* mif6cFlags */
        1, /* vifcThreshold*/
        20, 0, /* mif6cPifi */
        0, 0, 0, 0, /* vifcRateLimit */
        0, 0 /* padding */
    };

    @Test
    public void testConstructor() {
        StructMif6ctl mif6ctl = new StructMif6ctl(10 /* mif6cMifi */,
                (short) 11 /* mif6cFlags */,
                (short) 12 /* vifcThreshold */,
                13 /* mif6cPifi */,
                14L /* vifcRateLimit */);

        assertEquals(10, mif6ctl.mif6cMifi);
        assertEquals(11, mif6ctl.mif6cFlags);
        assertEquals(12, mif6ctl.vifcThreshold);
        assertEquals(13, mif6ctl.mif6cPifi);
        assertEquals(14, mif6ctl.vifcRateLimit);
    }

    @Test
    public void testParseMif6ctl() {
        final ByteBuffer buf = ByteBuffer.wrap(MSG_BYTES);
        buf.order(ByteOrder.nativeOrder());
        StructMif6ctl mif6ctl = StructMif6ctl.parse(StructMif6ctl.class, buf);

        assertEquals(1, mif6ctl.mif6cMifi);
        assertEquals(0, mif6ctl.mif6cFlags);
        assertEquals(1, mif6ctl.vifcThreshold);
        assertEquals(20, mif6ctl.mif6cPifi);
        assertEquals(0, mif6ctl.vifcRateLimit);
    }

    @Test
    public void testWriteToBytes() {
        StructMif6ctl mif6ctl = new StructMif6ctl(1 /* mif6cMifi */,
                (short) 0 /* mif6cFlags */,
                (short) 1 /* vifcThreshold */,
                20 /* mif6cPifi */,
                (long) 0 /* vifcRateLimit */);

        byte[] bytes = mif6ctl.writeToBytes();

        assertArrayEquals("bytes = " + Arrays.toString(bytes), MSG_BYTES, bytes);
    }
}
