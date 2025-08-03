package android.cts.statsd.restricted;

import android.cts.statsd.atom.DeviceAtomTestCase;

/**
 * Tests Suite for restricted stats permissions.
 */
public class ReadRestrictedStatsPermissionTest extends DeviceAtomTestCase {

    public void testReadRestrictedStatsPermission() throws Exception {
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE,
                ".RestrictedPermissionTests", "testReadRestrictedStatsPermission");
    }
}
