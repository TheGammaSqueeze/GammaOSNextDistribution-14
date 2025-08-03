package android.nfc.cts;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HostNfcFServiceTest {
    private CtsMyHostNfcFService service;

    @Before
    public void setUp() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        service = new CtsMyHostNfcFService();
    }

    @Test
    public void testOnBind() {
        Intent serviceIntent
            = new Intent(CtsMyHostNfcFService.SERVICE_INTERFACE);
        Assert.assertNotNull(service.onBind(serviceIntent));
    }

    @Test
    public void testSendResponsePacket() {
        try {
            byte[] responsePacket = new byte[0];
            service.sendResponsePacket(responsePacket);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testProcessNfcFPacket() {
        byte[] result = service.processNfcFPacket(new byte[0], new Bundle());
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length == 0);
    }

    @Test
    public void testOnDeactivated() {
        try {
            service.onDeactivated(CtsMyHostNfcFService.DEACTIVATION_LINK_LOSS);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }
}
