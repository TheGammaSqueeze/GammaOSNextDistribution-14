/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.org.conscrypt.metrics;

import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @hide This class is not part of the Android public SDK API
 */
@RunWith(JUnit4.class)
public class ProtocolTest {
    @Test
    public void consistency() {
        for (Protocol protocol : Protocol.values()) {
            if (protocol == Protocol.TLS_PROTO_FAILED) {
                continue;
            }
            String name = protocol.name().replace('_', '.');
            assertSame(protocol, Protocol.forName(name));
        }
    }
}
