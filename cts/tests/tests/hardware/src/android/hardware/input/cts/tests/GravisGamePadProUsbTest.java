package android.hardware.input.cts.tests;

import static org.junit.Assume.assumeTrue;

import android.hardware.cts.R;
import android.view.InputDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.cts.kernelinfo.KernelInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GravisGamePadProUsbTest extends InputHidTestCase {

    public GravisGamePadProUsbTest() {
        super(R.raw.gravis_gamepadprousb_register);
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.gravis_gamepadprousb_keyeventtests);
    }

    @Test
    public void testAllMotions() {
        testInputEvents(R.raw.gravis_gamepadprousb_motioneventtests);
    }
}
