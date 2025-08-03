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
package android.app.cts.shortfgstest;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.FileUtils;
import com.android.server.am.nano.ActiveServicesProto.ServicesByUser;
import com.android.server.am.nano.ActivityManagerServiceDumpProcessesProto;
import com.android.server.am.nano.ActivityManagerServiceDumpServicesProto;
import com.android.server.am.nano.ProcessOomProto;
import com.android.server.am.nano.ServiceRecordProto;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import org.junit.Assert;

import java.io.FileInputStream;
import java.io.IOException;

public class DumpProtoUtils {
    private DumpProtoUtils() {
    }

    /**
     * Returns the proto from `dumpsys activity --proto processes`
     */
    public static ActivityManagerServiceDumpProcessesProto dumpProcesses() {
        try {
            return ActivityManagerServiceDumpProcessesProto.parseFrom(
                    getDump("dumpsys activity --proto processes"));
        } catch (InvalidProtocolBufferNanoException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the proto from `dumpsys activity --proto services`
     */
    public static ActivityManagerServiceDumpServicesProto dumpServices() {
        try {
            return ActivityManagerServiceDumpServicesProto.parseFrom(
                    getDump("dumpsys activity --proto service"));
        } catch (InvalidProtocolBufferNanoException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] getDump(String command) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            return FileUtils.readInputStreamFully(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class ProcStateInfo {
        public int mProcState;
        public int mOomAdjustment;

        @Override
        public String toString() {
            return "ProcState=" + mProcState + ", OOM-adj=" + mOomAdjustment;
        }
    }

    /**
     * Returns true if a given process is running.
     */
    public static boolean processExists(String processName) {
        ActivityManagerServiceDumpProcessesProto processes = dumpProcesses();

        for (ProcessOomProto pop : processes.lruProcs.list) {
            if (pop.proc == null || !processName.equals(pop.proc.processName)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns {@link ProcStateInfo} of a given process.
     */
    @NonNull
    public static ProcStateInfo getProcessProcState(String processName) {
        ActivityManagerServiceDumpProcessesProto processes = dumpProcesses();

        for (ProcessOomProto pop : processes.lruProcs.list) {
            if (pop.proc == null || !processName.equals(pop.proc.processName)) {
                continue;
            }

            ProcStateInfo ret = new ProcStateInfo();
            ret.mProcState = pop.state;
            ret.mOomAdjustment = pop.detail.setAdj;

            return ret;
        }
        Assert.fail("Process " + processName + " not found");
        return null; // never reaches
    }

    /**
     * Returns {@link ServiceRecordProto} of a given service.
     */
    @Nullable
    public static ServiceRecordProto findServiceRecord(ComponentName cn) {
        final int userId = UserHandle.getUserId(android.os.Process.myUid());

        ActivityManagerServiceDumpServicesProto services = dumpServices();

        for (ServicesByUser sbu : services.activeServices.servicesByUsers) {
            if (sbu.userId != userId) {
                continue;
            }
            for (ServiceRecordProto srp : sbu.serviceRecords) {
                if (ComponentName.unflattenFromString(srp.shortName).equals(cn)) {
                    return srp;
                }
            }
        }
        return null;
    }
}

