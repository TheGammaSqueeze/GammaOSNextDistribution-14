/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.media.codec.cts;

import static android.media.codec.cts.MediaCodecResourceTestHighPriorityActivity.ACTION_HIGH_PRIORITY_ACTIVITY_READY;
import static android.media.codec.cts.MediaCodecResourceTestLowPriorityService.ACTION_LOW_PRIORITY_SERVICE_READY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

// This class verifies the resource management aspects of MediaCodecs.
@SmallTest
@RequiresDevice
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
public class MediaCodecResourceTest {
    private static final String TAG = "MediaCodecResourceTest";

    // Codec information that is pertinent to creating codecs for resource management testing.
    private static class CodecInfo {
        CodecInfo(String name, int maxSupportedInstances, String mime, MediaFormat mediaFormat) {
            this.name = name;
            this.maxSupportedInstances = maxSupportedInstances;
            this.mime = mime;
            this.mediaFormat = mediaFormat;
        }
        public final String name;
        public final int maxSupportedInstances;
        public final String mime;
        public final MediaFormat mediaFormat;
    }

    private static class ProcessInfo {
        ProcessInfo(int pid, int uid) {
            this.pid = pid;
            this.uid = uid;
        }
        public final int pid;
        public final int uid;
    }

    @ApiTest(apis = "MediaCodec#createByCodecNameForClient")
    @Test
    public void testCreateCodecForAnotherProcessWithoutPermissionsThrows() throws Exception {
        CodecInfo codecInfo = getFirstVideoHardwareDecoder();
        assumeTrue("No video hardware codec found.", codecInfo != null);
        try {
            ProcessInfo processInfo = createLowPriorityProcess();
            assertTrue("Unable to retrieve low priority process info.", processInfo != null);

            MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                    processInfo.pid, processInfo.uid);
            fail("No SecurityException thrown when creating a codec for another process");
        } catch (SecurityException ex) {
            // expected
        } finally {
            destroyLowPriorityProcess();
            // Allow time for resources to be released
            Thread.sleep(500);
        }
    }

    // A process with lower priority (e.g. background app) should not be able to reclaim
    // MediaCodec resources from a process with higher priority (e.g. foreground app).
    @ApiTest(apis = "MediaCodec#createByCodecNameForClient")
    @Test
    public void testLowerPriorityProcessFailsToReclaimResources() throws Exception {
        CodecInfo codecInfo = getFirstVideoHardwareDecoder();
        assumeTrue("No video hardware codec found.", codecInfo != null);
        assertTrue("Expected at least one max supported codec instance.",
                codecInfo.maxSupportedInstances > 0);

        List<MediaCodec> mediaCodecList = new ArrayList<>();
        try {
            ProcessInfo lowPriorityProcess = createLowPriorityProcess();
            assertTrue("Unable to retrieve low priority process info.", lowPriorityProcess != null);
            ProcessInfo highPriorityProcess = createHighPriorityProcess();
            assertTrue("Unable to retrieve high priority process info.",
                    highPriorityProcess != null);

            // This permission is required to create MediaCodecs on behalf of other processes.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.MEDIA_RESOURCE_OVERRIDE_PID);

            Log.i(TAG, "Creating MediaCodecs on behalf of pid " + highPriorityProcess.pid);
            // Create more codecs than are supported by the device on behalf of a high-priority
            // process.
            boolean wasInitialInsufficientResourcesExceptionThrown = false;
            for (int i = 0; i <= codecInfo.maxSupportedInstances; ++i) {
                try {
                    MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                            highPriorityProcess.pid, highPriorityProcess.uid);
                    mediaCodecList.add(mediaCodec);
                    mediaCodec.configure(codecInfo.mediaFormat, /* surface= */ null,
                            /* crypto= */ null, /* flags= */ 0);
                    mediaCodec.start();
                } catch (MediaCodec.CodecException ex) {
                    if (ex.getErrorCode() == CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                        Log.i(TAG, "Exception received on MediaCodec #" +  i + ".");
                        wasInitialInsufficientResourcesExceptionThrown = true;
                    } else {
                        Log.e(TAG, "Unexpected exception thrown", ex);
                        throw ex;
                    }
                }
            }
            // For the same process, insufficient resources should be thrown.
            assertTrue(String.format("No MediaCodec.Exception thrown with insufficient"
                    + " resources after creating too many %d codecs for %s on behalf of the"
                    + " same process", codecInfo.maxSupportedInstances, codecInfo.name),
                    wasInitialInsufficientResourcesExceptionThrown);

            Log.i(TAG, "Creating MediaCodecs on behalf of pid " + lowPriorityProcess.pid);
            // Attempt to create the codec again, but this time, on behalf of a low priority
            // process.
            boolean wasLowPriorityInsufficientResourcesExceptionThrown = false;
            try {
                MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                        lowPriorityProcess.pid, lowPriorityProcess.uid);
                mediaCodecList.add(mediaCodec);
                mediaCodec.configure(codecInfo.mediaFormat, /* surface= */ null, /* crypto= */ null,
                        /* flags= */ 0);
                mediaCodec.start();
            } catch (MediaCodec.CodecException ex) {
                if (ex.getErrorCode() == CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                    wasLowPriorityInsufficientResourcesExceptionThrown = true;
                } else {
                    Log.e(TAG, "Unexpected exception thrown", ex);
                    throw ex;
                }
            }
            assertTrue(String.format("No MediaCodec.Exception thrown with insufficient"
                    + " resources after creating a follow-up codec for %s on behalf of a lower"
                    + " priority process", codecInfo.mime),
                    wasLowPriorityInsufficientResourcesExceptionThrown);
        } finally {
            Log.i(TAG, "Cleaning up MediaCodecs");
            for (MediaCodec mediaCodec : mediaCodecList) {
                mediaCodec.release();
            }
            destroyHighPriorityProcess();
            destroyLowPriorityProcess();
            // Allow time for the codecs and other resources to be released
            Thread.sleep(500);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    // A process with higher priority (e.g. foreground app) should be able to reclaim
    // MediaCodec resources from a process with lower priority (e.g. background app).
    @ApiTest(apis = "MediaCodec#createByCodecNameForClient")
    @Test
    public void testHigherPriorityProcessReclaimsResources() throws Exception {
        CodecInfo codecInfo = getFirstVideoHardwareDecoder();
        assumeTrue("No video hardware codec found.", codecInfo != null);
        assertTrue("Expected at least one max supported codec instance.",
                codecInfo.maxSupportedInstances > 0);

        List<MediaCodec> mediaCodecList = new ArrayList<>();
        try {
            ProcessInfo lowPriorityProcess = createLowPriorityProcess();
            assertTrue("Unable to retrieve low priority process info.", lowPriorityProcess != null);
            ProcessInfo highPriorityProcess = createHighPriorityProcess();
            assertTrue("Unable to retrieve high priority process info.",
                    highPriorityProcess != null);

            // This permission is required to create MediaCodecs on behalf of other processes.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.MEDIA_RESOURCE_OVERRIDE_PID);

            Log.i(TAG, "Creating MediaCodecs on behalf of pid " + lowPriorityProcess.pid);
            // Create more codecs than are supported by the device on behalf of a low-priority
            // process.
            boolean wasInitialInsufficientResourcesExceptionThrown = false;
            for (int i = 0; i <= codecInfo.maxSupportedInstances; ++i) {
                try {
                    MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                            lowPriorityProcess.pid, lowPriorityProcess.uid);
                    mediaCodecList.add(mediaCodec);
                    mediaCodec.configure(codecInfo.mediaFormat, /* surface= */ null,
                            /* crypto= */ null, /* flags= */ 0);
                    mediaCodec.start();
                } catch (MediaCodec.CodecException ex) {
                    if (ex.getErrorCode() == CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                        Log.i(TAG, "Exception received on MediaCodec #" +  i + ".");
                        wasInitialInsufficientResourcesExceptionThrown = true;
                    } else {
                        Log.e(TAG, "Unexpected exception thrown", ex);
                        throw ex;
                    }
                }
            }
            // For the same process, insufficient resources should be thrown.
            assertTrue(String.format("No MediaCodec.Exception thrown with insufficient"
                    + " resources after creating too many %d codecs for %s on behalf of the"
                    + " same process", codecInfo.maxSupportedInstances, codecInfo.mime),
                    wasInitialInsufficientResourcesExceptionThrown);

            Log.i(TAG, "Creating final MediaCodec on behalf of pid " + highPriorityProcess.pid);
            // Attempt to create the codec again, but this time, on behalf of a high-priority
            // process.
            boolean wasHighPriorityInsufficientResourcesExceptionThrown = false;
            try {
                MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                        highPriorityProcess.pid, highPriorityProcess.uid);
                mediaCodecList.add(mediaCodec);
                mediaCodec.configure(codecInfo.mediaFormat, /* surface= */ null, /* crypto= */ null,
                        /* flags= */ 0);
                mediaCodec.start();
            } catch (MediaCodec.CodecException ex) {
                if (ex.getErrorCode() == CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                    wasHighPriorityInsufficientResourcesExceptionThrown = true;
                } else {
                    Log.e(TAG, "Unexpected exception thrown", ex);
                    throw ex;
                }
            }
            assertFalse(String.format("Resource reclaiming should occur when creating a"
                    + " follow-up codec for %s on behalf of a higher priority process, but"
                    + " received an insufficient resource CodecException instead",
                    codecInfo.mime), wasHighPriorityInsufficientResourcesExceptionThrown);
        } finally {
            Log.i(TAG, "Cleaning up MediaCodecs");
            for (MediaCodec mediaCodec : mediaCodecList) {
                mediaCodec.release();
            }
            destroyHighPriorityProcess();
            destroyLowPriorityProcess();
            // Allow time for the codecs and other resources to be released
            Thread.sleep(500);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    // Find the first hardware video decoder and create a media format for it.
    @Nullable
    private CodecInfo getFirstVideoHardwareDecoder() {
        MediaCodecList allMediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo mediaCodecInfo : allMediaCodecList.getCodecInfos()) {
            if (mediaCodecInfo.isSoftwareOnly()) {
                continue;
            }
            if (mediaCodecInfo.isEncoder()) {
                continue;
            }
            if (mediaCodecInfo.getSupportedTypes().length == 0) {
                continue;
            }
            String mime = mediaCodecInfo.getSupportedTypes()[0];
            CodecCapabilities codecCapabilities = mediaCodecInfo.getCapabilitiesForType(mime);
            VideoCapabilities videoCapabilities = codecCapabilities.getVideoCapabilities();
            if (videoCapabilities != null) {
                int height = videoCapabilities.getSupportedHeights().getLower();
                int width = videoCapabilities.getSupportedWidthsFor(height).getLower();
                MediaFormat mediaFormat = new MediaFormat();
                mediaFormat.setString(MediaFormat.KEY_MIME, mime);
                mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, height);
                mediaFormat.setInteger(MediaFormat.KEY_WIDTH, width);
                return new CodecInfo(mediaCodecInfo.getName(),
                        codecCapabilities.getMaxSupportedInstances(), mime, mediaFormat);
            }
        }
        return null;
    }

    private ProcessInfo createLowPriorityProcess() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProcessInfoBroadcastReceiver processInfoBroadcastReceiver =
                new ProcessInfoBroadcastReceiver();
        context.registerReceiver(processInfoBroadcastReceiver,
                new IntentFilter(ACTION_LOW_PRIORITY_SERVICE_READY),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        Intent intent = new Intent(context, MediaCodecResourceTestLowPriorityService.class);
        context.startForegroundService(intent);
        // Starting the service and receiving the broadcast should take less than 5 seconds
        ProcessInfo processInfo = processInfoBroadcastReceiver.waitForProcessInfoMs(5000);
        context.unregisterReceiver(processInfoBroadcastReceiver);
        return processInfo;
    }

    private ProcessInfo createHighPriorityProcess() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProcessInfoBroadcastReceiver processInfoBroadcastReceiver =
                new ProcessInfoBroadcastReceiver();
        context.registerReceiver(processInfoBroadcastReceiver,
                new IntentFilter(ACTION_HIGH_PRIORITY_ACTIVITY_READY),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        Intent intent = new Intent(context, MediaCodecResourceTestHighPriorityActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        // Starting the activity and receiving the broadcast should take less than 5 seconds
        ProcessInfo processInfo = processInfoBroadcastReceiver.waitForProcessInfoMs(5000);
        context.unregisterReceiver(processInfoBroadcastReceiver);
        return processInfo;
    }

    private void destroyLowPriorityProcess() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MediaCodecResourceTestLowPriorityService.class);
        context.stopService(intent);
    }

    private void destroyHighPriorityProcess() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent().setAction(
                MediaCodecResourceTestHighPriorityActivity.ACTION_HIGH_PRIORITY_ACTIVITY_FINISH);
        context.sendBroadcast(intent);
    }

    private static class ProcessInfoBroadcastReceiver extends BroadcastReceiver {
        private int mPid = -1;
        private int mUid = -1;

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (this) {
                mPid = intent.getIntExtra("pid", -1);
                mUid = intent.getIntExtra("uid", -1);
                this.notify();
            }
        }

        public ProcessInfo waitForProcessInfoMs(int milliseconds) {
            synchronized (this) {
                try {
                    this.wait(milliseconds);
                } catch (InterruptedException ex) {
                    return null;
                }
            }
            if (mPid == -1 || mUid == -1) {
                return null;
            }
            return new ProcessInfo(mPid, mUid);
        }
    }
}
