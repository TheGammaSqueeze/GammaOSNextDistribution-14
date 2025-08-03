package android.nfc.cts;

import android.content.Intent;
import android.os.Looper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OffHostApduServiceTest {
    private CtsMyOffHostApduService service;

    @Before
    public void setUp() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        service = new CtsMyOffHostApduService();
    }

    @Test
    public void testOnBind() {
        Intent serviceIntent
            = new Intent(CtsMyOffHostApduService.SERVICE_INTERFACE);
        Assert.assertNull(service.onBind(serviceIntent));
    }
}
