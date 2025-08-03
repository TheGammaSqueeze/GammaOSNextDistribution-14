package com.android.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;

/** Unit tests for {@link AllTestAppsInstallSetup} */
@RunWith(JUnit4.class)
public class AllTestAppsInstallSetupTest {

    private static final String SERIAL = "SERIAL";
    private AllTestAppsInstallSetup mPrep;
    @Mock IDeviceBuildInfo mMockBuildInfo;
    @Mock ITestDevice mMockTestDevice;

    private TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mPrep = new AllTestAppsInstallSetup();

        when(mMockTestDevice.getSerialNumber()).thenReturn(SERIAL);
        when(mMockTestDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(false);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockTestDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @Test
    public void testNotIDeviceBuildInfo() throws Exception {
        IBuildInfo mockBuildInfo = mock(IBuildInfo.class);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockTestDevice);
        context.addDeviceBuildInfo("device", mockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();

        try {
            mPrep.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError");
        } catch (TargetSetupError e) {
            // expected
            assertEquals("Invalid buildInfo, expecting an IDeviceBuildInfo", e.getMessage());
        }
    }

    @Test
    public void testNoTestDir() throws Exception {
        when(mMockBuildInfo.getTestsDir()).thenReturn(new File(""));

        try {
            mPrep.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError");
        } catch (TargetSetupError e) {
            assertEquals("Failed to find a valid test zip directory.", e.getMessage());
        }
    }

    @Test
    public void testNullTestDir() throws DeviceNotAvailableException {

        try {
            mPrep.installApksRecursively(null, mMockTestDevice);
            fail("Should have thrown a TargetSetupError");
        } catch (TargetSetupError e) {
            assertEquals("Invalid test zip directory!", e.getMessage());
        }
    }

    @Test
    public void testSetup() throws Exception {
        File testDir = FileUtil.createTempDir("TestAppSetupTest");
        // fake hierarchy of directory and files
        FileUtil.createTempFile("fakeApk", ".apk", testDir);
        FileUtil.createTempFile("fakeApk2", ".apk", testDir);
        FileUtil.createTempFile("notAnApk", ".txt", testDir);
        File subTestDir = FileUtil.createTempDir("SubTestAppSetupTest", testDir);
        FileUtil.createTempFile("subfakeApk", ".apk", subTestDir);
        try {
            when(mMockBuildInfo.getTestsDir()).thenReturn(testDir);
            when(mMockTestDevice.installPackage((File) Mockito.any(), Mockito.eq(true)))
                    .thenReturn(null);

            mPrep.setUp(mTestInfo);
            verify(mMockTestDevice, times(3))
                    .installPackage((File) Mockito.any(), Mockito.eq(true));
        } finally {
            FileUtil.recursiveDelete(testDir);
        }
    }

    @Test
    public void testSetupForceQueryable() throws Exception {
        when(mMockTestDevice.checkApiLevelAgainstNextRelease(Mockito.eq(34))).thenReturn(false);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(true);
        File testDir = FileUtil.createTempDir("TestAppSetupForceQueryableTest");
        // fake hierarchy of directory and files
        FileUtil.createTempFile("fakeApk", ".apk", testDir);
        try {
            when(mMockBuildInfo.getTestsDir()).thenReturn(testDir);
            when(mMockTestDevice.installPackage(
                            (File) Mockito.any(),
                            Mockito.eq(true),
                            Mockito.eq("--force-queryable")))
                    .thenReturn(null);

            mPrep.setUp(mTestInfo);
            verify(mMockTestDevice, atLeastOnce())
                    .installPackage(
                            (File) Mockito.any(),
                            Mockito.eq(true),
                            Mockito.eq("--force-queryable"));
        } finally {
            FileUtil.recursiveDelete(testDir);
        }
    }

    @Test
    public void testSetupDeviceApi34ForceQueryableIsFalse() throws Exception {
        when(mMockTestDevice.checkApiLevelAgainstNextRelease(Mockito.eq(34))).thenReturn(true);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(true);
        File testDir = FileUtil.createTempDir("TestAppSetupForceQueryableTest");
        // fake hierarchy of directory and files
        FileUtil.createTempFile("fakeApk", ".apk", testDir);
        try {
            when(mMockBuildInfo.getTestsDir()).thenReturn(testDir);
            when(mMockTestDevice.installPackage((File) Mockito.any(), Mockito.eq(true)))
                    .thenReturn(null);

            mPrep.setUp(mTestInfo);
            verify(mMockTestDevice, atLeastOnce())
                    .installPackage((File) Mockito.any(), Mockito.eq(true));
        } finally {
            FileUtil.recursiveDelete(testDir);
        }
    }

    @Test
    public void testInstallFailure() throws DeviceNotAvailableException {
        final String failure = "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED";
        final String file = "TEST";
        when(mMockTestDevice.installPackage((File) Mockito.any(), Mockito.eq(true)))
                .thenReturn(failure);

        try {
            mPrep.installApk(new File("TEST"), mMockTestDevice);
            fail("Should have thrown an exception");
        } catch (TargetSetupError e) {
            String expected =
                    String.format(
                            "Failed to install %s on %s. Reason: '%s'", file, SERIAL, failure);
            assertEquals(expected, e.getMessage());
        }
    }
}
