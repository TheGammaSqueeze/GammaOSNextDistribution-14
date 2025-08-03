/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tests.init;

import static com.google.common.truth.Truth.assertThat;

import com.android.compatibility.common.util.ApiTest;
import com.android.server.os.TombstoneProtos.CrashDetail;
import com.android.server.os.TombstoneProtos.Tombstone;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;

import com.google.protobuf.ByteString;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@RunWith(DeviceJUnit4ClassRunner.class)
@ApiTest(apis = {"android_register_crash_detail", "android_unregister_crash_detail"})
public class TestCrashDetail extends BaseHostJUnit4Test {
    String mUUID;

    @Before
    public void setUp() throws Exception {
        mUUID = java.util.UUID.randomUUID().toString();
    }

    Tombstone parseTombstone(String tombstonePath) throws Exception {
        File tombstoneFile = getDevice().pullFile(tombstonePath);
        InputStream is = new FileInputStream(tombstoneFile);
        Tombstone tombstoneProto;
        try {
            tombstoneProto = Tombstone.parseFrom(is);
        } finally {
            is.close();
        }
        return tombstoneProto;
    }

    @After
    public void tearDown() throws Exception {
        String[] tombstones = getDevice().getChildren("/data/tombstones");
        for (String tombstone : tombstones) {
            if (!tombstone.endsWith(".pb")) {
                continue;
            }
            String tombstonePath = "/data/tombstones/" + tombstone;
            Tombstone tombstoneProto = parseTombstone(tombstonePath);
            if (!tombstoneProto.getCommandLineList().stream().anyMatch(x -> x.contains(mUUID))) {
                continue;
            }
            getDevice().deleteFile(tombstonePath);
            // remove the non .pb file as well.
            getDevice().deleteFile(tombstonePath.substring(0, tombstonePath.length() - 3));
        }
    }

    private Tombstone findTombstone() throws Exception {
        String[] tombstones = getDevice().getChildren("/data/tombstones");
        for (String tombstone : tombstones) {
            if (!tombstone.endsWith(".pb")) {
                continue;
            }
            String tombstonePath = "/data/tombstones/" + tombstone;
            Tombstone tombstoneProto = parseTombstone(tombstonePath);
            if (!tombstoneProto.getCommandLineList().stream().anyMatch(x -> x.contains(mUUID))) {
                continue;
            }
            return tombstoneProto;
        }
        return null;
    }

    private Tombstone runCrasher(String cmd) throws Exception {
        // See cts/tests/tests/debuggerd/debuggerd_cts_crasher.cpp
        CommandResult result =
                getDevice()
                        .executeShellV2Command(
                                "/data/local/tmp/debuggerd_cts_crasher " + cmd + " " + mUUID);
        assertThat(result.getExitCode()).isNotEqualTo(0);
        Tombstone tombstoneProto = findTombstone();
        assertThat(tombstoneProto).isNotNull();
        return tombstoneProto;
    }

    private CrashDetail crashDetail(String name, String data) {
        return CrashDetail.newBuilder()
                .setName(ByteString.copyFromUtf8(name))
                .setData(ByteString.copyFromUtf8(data))
                .build();
    }

    @Test
    public void testCrashWithoutCrashDetail() throws Exception {
        Tombstone tombstoneProto = runCrasher("crash_without_crash_detail");

        assertThat(tombstoneProto.getCrashDetailsList()).isEmpty();
    }

    @Test
    public void testCrashWithSingleCrashDetail() throws Exception {
        Tombstone tombstoneProto = runCrasher("crash_with_single_crash_detail");

        assertThat(tombstoneProto.getCrashDetailsList())
                .containsExactly(crashDetail("crash_detail_name", "crash_detail_data"));
    }

    @Test
    public void testCrashWithMultipleCrashDetails() throws Exception {
        Tombstone tombstoneProto = runCrasher("crash_with_multiple_crash_details");

        assertThat(tombstoneProto.getCrashDetailsList())
                .containsExactly(
                        crashDetail("crash_detail_name1", "crash_detail_data1"),
                        crashDetail("crash_detail_name2", "crash_detail_data2"));
    }

    @Test
    public void testCrashWithUnregisteredCrashDetails() throws Exception {
        Tombstone tombstoneProto = runCrasher("crash_with_unregistered_crash_details");

        assertThat(tombstoneProto.getCrashDetailsList())
                .containsExactly(crashDetail("crash_detail_name1", "crash_detail_data1"));
    }

    @Test
    public void testCrashWithBinaryCrashDetail() throws Exception {
        Tombstone tombstoneProto = runCrasher("crash_with_binary_crash_detail");

        CrashDetail detail =
                CrashDetail.newBuilder()
                        .setName(ByteString.copyFrom(new byte[] {(byte) 0254, 0}))
                        .setData(ByteString.copyFrom(new byte[] {(byte) 0255, 0}))
                        .build();
        assertThat(tombstoneProto.getCrashDetailsList()).containsExactly(detail);
    }

    @Test
    public void testCrashWithSingleCrashDetailManyUsed() throws Exception {
        Tombstone tombstoneProto = runCrasher("crash_with_single_crash_detail_many_used");

        assertThat(tombstoneProto.getCrashDetailsList())
                .containsExactly(crashDetail("crash_detail_name", "crash_detail_data"));
    }

    @Test
    public void testCrashWithChangingCrashDetail() throws Exception {
        Tombstone tombstoneProto = runCrasher("crash_with_changing_crash_detail");

        assertThat(tombstoneProto.getCrashDetailsList())
                .containsExactly(crashDetail("Crash_detail_name", "Crash_detail_data"));
    }
}
