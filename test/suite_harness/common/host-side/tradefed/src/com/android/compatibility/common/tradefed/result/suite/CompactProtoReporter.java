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
package com.android.compatibility.common.tradefed.result.suite;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.proto.FileProtoResultReporter;
import com.android.tradefed.result.proto.ProtoResultParser;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IDisableable;

import java.io.File;
import java.io.IOException;

/**
 * This reporter compacts together all the partial proto to save disk space. This is a memory heavy
 * operation so it's executed last and can optionally fail and be inop (resulting in no
 * compression).
 */
@OptionClass(alias = "result-reporter")
public class CompactProtoReporter
        implements ITestInvocationListener, ITestSummaryListener, IDisableable {

    @Option(
            name = "skip-proto-compacting",
            description = "Option to disable compacting the protos at the end")
    private boolean mSkipProtoCompacting = false;

    @Option(name = "disable", description = "Whether or not to disable this reporter.")
    private boolean mDisable = false;

    private CompatibilityBuildHelper mBuildHelper;

    /** The directory containing the proto results */
    private File mResultDir = null;

    private File mBaseProtoFile = null;

    @Override
    public void invocationStarted(IInvocationContext context) {
        if (mBuildHelper == null) {
            mBuildHelper = new CompatibilityBuildHelper(context.getBuildInfos().get(0));
            mResultDir = CompatibilityProtoResultReporter.getProtoResultDirectory(mBuildHelper);
            mBaseProtoFile = new File(mResultDir, CompatibilityProtoResultReporter.PROTO_FILE_NAME);
        }
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        if (mSkipProtoCompacting) {
            return;
        }
        if (mBuildHelper == null) {
            CLog.w("Something went wrong and no build helper are configured.");
            return;
        }
        // Compact all the protos
        try (CloseableTraceScope ignored = new CloseableTraceScope("compact_protos")) {
            CLog.d("Compacting protos to reduce disk size");
            compactAllProtos();
            CLog.d("Done compacting protos");
        } catch (RuntimeException e) {
            CLog.e("Failed to compact the protos");
            CLog.e(e);
            FileUtil.deleteFile(mBaseProtoFile);
            return;
        }
        // Delete all the protos we compacted
        int index = 0;
        while (new File(mBaseProtoFile.getAbsolutePath() + index).exists()) {
            FileUtil.deleteFile(new File(mBaseProtoFile.getAbsolutePath() + index));
            index++;
        }
    }

    private void compactAllProtos() {
        FileProtoResultReporter fprr = new FileProtoResultReporter();
        fprr.setFileOutput(mBaseProtoFile);
        ProtoResultParser parser = new ProtoResultParser(fprr, new InvocationContext(), true);
        int index = 0;
        while (new File(mBaseProtoFile.getAbsolutePath() + index).exists()) {
            try {
                parser.processFileProto(new File(mBaseProtoFile.getAbsolutePath() + index));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            index++;
        }
    }

    @Override
    public boolean isDisabled() {
        return mDisable;
    }
}
