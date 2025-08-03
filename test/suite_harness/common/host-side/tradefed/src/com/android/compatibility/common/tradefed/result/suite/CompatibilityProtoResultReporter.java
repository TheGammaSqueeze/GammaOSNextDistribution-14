/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.proto.FileProtoResultReporter;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.util.IDisableable;

import java.io.File;
import java.io.FileNotFoundException;

/** Proto reporter that will drop a {@link TestRecord} protobuf in the result directory. */
@OptionClass(alias = "result-reporter")
public class CompatibilityProtoResultReporter extends FileProtoResultReporter
        implements IDisableable {

    public static final String PROTO_FILE_NAME = "test-record.pb";
    public static final String PROTO_DIR = "proto";

    @Option(name = "disable", description = "Whether or not to disable this reporter.")
    private boolean mDisable = false;

    private CompatibilityBuildHelper mBuildHelper;

    /** The directory containing the proto results */
    private File mResultDir = null;

    private File mBaseProtoFile = null;

    @Override
    public void processStartInvocation(
            TestRecord invocationStartRecord, IInvocationContext invocationContext) {
        if (mBuildHelper == null) {
            mBuildHelper = new CompatibilityBuildHelper(invocationContext.getBuildInfos().get(0));
            mResultDir = getProtoResultDirectory(mBuildHelper);
            mBaseProtoFile = new File(mResultDir, PROTO_FILE_NAME);
            setFileOutput(mBaseProtoFile);
        }
        super.processStartInvocation(invocationStartRecord, invocationContext);
    }

    public static File getProtoResultDirectory(CompatibilityBuildHelper buildHelper) {
        File protoDir = null;
        try {
            File resultDir = buildHelper.getResultDir();
            if (resultDir != null) {
                resultDir.mkdirs();
            }
            protoDir = new File(resultDir, PROTO_DIR);
            protoDir.mkdir();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (!protoDir.exists()) {
            throw new RuntimeException(
                    "Result Directory was not created: " + protoDir.getAbsolutePath());
        }
        CLog.d("Proto Results Directory: %s", protoDir.getAbsolutePath());
        return protoDir;
    }

    @Override
    public boolean isDisabled() {
        return mDisable;
    }
}