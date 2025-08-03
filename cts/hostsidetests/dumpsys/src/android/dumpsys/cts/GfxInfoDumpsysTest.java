/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License.
 */

package android.dumpsys.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;

/**
 * Test to check the format of the dumps of the gfxinfo.
 */
public class GfxInfoDumpsysTest extends BaseDumpsysTest {
    private static final String TEST_APK = "CtsFramestatsTestApp.apk";
    private static final String TEST_PKG = "com.android.cts.framestatstestapp";

    /**
     * Tests the output of "dumpsys gfxinfo framestats".
     *
     * @throws Exception
     */
    public void testGfxinfoFramestats() throws Exception {
        final String MARKER = "---PROFILEDATA---";

        try {
            // cleanup test apps that might be installed from previous partial test run
            getDevice().uninstallPackage(TEST_PKG);

            // install the test app
            CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
            File testAppFile = buildHelper.getTestFile(TEST_APK);
            String installResult = getDevice().installPackage(testAppFile, false);
            assertNull(
                    String.format("failed to install atrace test app. Reason: %s", installResult),
                    installResult);

            getDevice().executeShellCommand("am start -W " + TEST_PKG);
            // Currently "wait for launch" doesn't actually wait for the frame to be finished
            // So do a little sleep to let things stabilize
            Thread.sleep(500);

            String frameinfo = mDevice.executeShellCommand("dumpsys gfxinfo " +
                    TEST_PKG + " framestats");
            assertNotNull(frameinfo);
            assertTrue(frameinfo.length() > 0);
            int profileStart = frameinfo.indexOf(MARKER);
            int profileEnd = frameinfo.indexOf(MARKER, profileStart + 1);
            assertTrue(profileStart >= 0);
            assertTrue(profileEnd > profileStart);
            String profileData = frameinfo.substring(profileStart + MARKER.length(), profileEnd);
            assertTrue(profileData.length() > 0);
            validateProfileData(profileData);
        } finally {
            getDevice().uninstallPackage(TEST_PKG);
        }
    }

    private void validateProfileData(String profileData) throws IOException {
        final int TIMESTAMP_COUNT = 22;
        boolean foundAtLeastOneRow = false;
        try (BufferedReader reader = new BufferedReader(
                new StringReader(profileData))) {
            String line;
            // First line needs to be the headers
            while ((line = reader.readLine()) != null && line.isEmpty()) {}

            assertNotNull(line);
            assertTrue("First line was not the expected header: " + line,
                    line.startsWith("Flags,FrameTimelineVsyncId,IntendedVsync,Vsync"
                            + ",InputEventId,HandleInputStart,AnimationStart,PerformTraversalsStart"
                            + ",DrawStart,FrameDeadline,FrameInterval,FrameStartTime"
                            + ",SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers"
                            + ",FrameCompleted,DequeueBufferDuration,QueueBufferDuration"
                            + ",GpuCompleted,SwapBuffersCompleted,DisplayPresentTime"));

            long[] numparts = new long[TIMESTAMP_COUNT];
            while ((line = reader.readLine()) != null && !line.isEmpty()) {

                String[] parts = line.split(",");
                assertTrue(parts.length >= TIMESTAMP_COUNT);
                for (int i = 0; i < TIMESTAMP_COUNT; i++) {
                    numparts[i] = assertInteger(parts[i]);
                }

                final long flags = numparts[0];
                // Flags = 1 just means the first frame of the window
                if (flags != 0 && flags != 1) {
                    continue;
                }

                final long timestampIntendedVsync = numparts[2];
                final long timestampVsync = numparts[3];
                // skip InputEventId, since it's a randomly assigned id
                final long timestampHandleInputStart = numparts[5];
                final long timestampAnimationStart = numparts[6];
                final long timestampPerformTraversalsStart = numparts[7];
                final long timestampDrawStart = numparts[8];
                // skip FrameDeadline, FrameInterval,FrameStartTime
                final long timestampSyncQueued = numparts[12];
                final long timestampSyncStart = numparts[13];
                final long timestampIssueDrawCommandsStart = numparts[14];
                final long timestampSwapBuffers = numparts[15];
                final long timestampFrameCompleted = numparts[16];

                // assert time is flowing forwards. we need to check each entry explicitly
                // as some entries do not represent a flow of events.
                assertTrue("VSYNC happened before INTENDED_VSYNC",
                        timestampVsync >= timestampIntendedVsync);
                assertTrue("HandleInputStart happened before VSYNC",
                        timestampHandleInputStart >= timestampVsync);
                assertTrue("AnimationStart happened before HandleInputStart",
                        timestampAnimationStart >= timestampHandleInputStart);
                assertTrue("PerformTraversalsStart happened before AnimationStart",
                        timestampPerformTraversalsStart >= timestampAnimationStart);
                assertTrue("DrawStart happened before PerformTraversalsStart",
                        timestampDrawStart >= timestampPerformTraversalsStart);
                assertTrue("SyncQueued happened before DrawStart",
                        timestampSyncQueued >= timestampDrawStart);
                assertTrue("SyncStart happened before SyncQueued",
                        timestampSyncStart >= timestampSyncQueued);
                assertTrue("IssueDrawCommandsStart happened before SyncStart",
                        timestampIssueDrawCommandsStart >= timestampSyncStart);
                assertTrue("SwapBuffers happened before IssueDrawCommandsStart",
                        timestampSwapBuffers >= timestampIssueDrawCommandsStart);
                assertTrue("FrameCompleted happened before SwapBuffers",
                        timestampFrameCompleted >= timestampSwapBuffers);

                // total duration is from IntendedVsync to FrameCompleted
                long totalDuration = timestampFrameCompleted - timestampIntendedVsync;
                assertTrue("Frame did not take a positive amount of time to process",
                        totalDuration > 0);
                assertTrue("Bogus frame duration, exceeds 100 seconds",
                        totalDuration < TimeUnit.SECONDS.toNanos(100));
                foundAtLeastOneRow = true;
            }
        }
        assertTrue(foundAtLeastOneRow);
    }
}
