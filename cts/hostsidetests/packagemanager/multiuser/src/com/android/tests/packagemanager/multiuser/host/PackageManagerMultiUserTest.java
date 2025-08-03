package com.android.tests.packagemanager.multiuser.host;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class PackageManagerMultiUserTest extends PackageManagerMultiUserTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private String getInstalledModules(int userId) throws Exception {
        runDeviceTestAsUser("testGetInstalledModules", userId, null);
        Map<String, String> results = getLastDeviceRunResults().getRunMetrics();
        return results.get("installedModules");
    }

    /**
     * Tests that all users see the same set of installed modules.
     */
    @Test
    @AppModeFull
    public void testGetInstalledModules() throws Exception {
        int newUserId = createUser();
        getDevice().startUser(newUserId);
        String list2 = getInstalledModules(newUserId);
        String list1 = getInstalledModules(mUserId);
        assertThat(list2).isEqualTo(list1);
    }
}
