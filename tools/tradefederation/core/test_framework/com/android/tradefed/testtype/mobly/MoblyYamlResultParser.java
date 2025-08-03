/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tradefed.testtype.mobly;

import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto;
import com.android.tradefed.testtype.mobly.IMoblyYamlResultHandler.ITestResult;
import com.android.tradefed.testtype.mobly.MoblyYamlResultHandlerFactory.InvalidResultTypeException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mobly yaml test results parser. */
public class MoblyYamlResultParser {
    private static final String TYPE = "Type";
    private ImmutableList.Builder<ITestInvocationListener> mListenersBuilder =
            new ImmutableList.Builder<>();
    private ImmutableList.Builder<ITestResult> mResultCacheBuilder = new ImmutableList.Builder<>();
    private long mRunStartTime;
    private long mRunEndTime;
    private boolean mEnded;
    private boolean mRunFailed;
    private StringBuilder mYamlString = new StringBuilder();

    public MoblyYamlResultParser(ITestInvocationListener listener) {
        mListenersBuilder.add(listener);
    }

    public boolean parse(InputStream inputStream)
            throws InvalidResultTypeException,
                    IllegalAccessException,
                    InstantiationException,
                    IOException {
        if (mEnded)
            return mEnded;
        int available = inputStream.available();
        if (available == 0)
            return false;
        byte[] bytes = new byte[available];
        inputStream.read(bytes);
        mYamlString.append(new String(bytes, 0, available, StandardCharsets.UTF_8));
        int yamlEnd = mYamlString.lastIndexOf("\n...");
        if (yamlEnd == -1)
            return false;
        ArrayList<ITestResult> resultCache = new ArrayList<>();
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try {
            for (Object doc : yaml.loadAll(mYamlString.substring(0, yamlEnd))) {
                Map<String, Object> docMap = (Map<String, Object>) doc;
                resultCache.add(parseDocumentMap(docMap));
            }
        } finally {
            mYamlString.delete(0, yamlEnd + 4);
            reportToListeners(mListenersBuilder.build(), resultCache);
        }
        return mEnded;
    }

    public boolean getRunFailed() {
        return mRunFailed;
    }

    @VisibleForTesting
    protected ITestResult parseDocumentMap(Map<String, Object> docMap)
            throws InvalidResultTypeException, IllegalAccessException, InstantiationException {
        LogUtil.CLog.d("Parsed object: %s", docMap.toString());
        String docType = String.valueOf(docMap.get(TYPE));
        LogUtil.CLog.v("Parsing result type: %s", docType);
        IMoblyYamlResultHandler resultHandler =
                new MoblyYamlResultHandlerFactory().getHandler(docType);
        return resultHandler.handle(docMap);
    }

    @VisibleForTesting
    protected void reportToListeners(
            List<ITestInvocationListener> listeners,
            List<IMoblyYamlResultHandler.ITestResult> resultCache) {
        for (IMoblyYamlResultHandler.ITestResult result : resultCache) {
            switch (result.getType()) {
                case RECORD:
                    MoblyYamlResultRecordHandler.Record record =
                            (MoblyYamlResultRecordHandler.Record) result;
                    TestDescription testDescription =
                            new TestDescription(record.getTestClass(), record.getTestName());
                    FailureDescription failureDescription =
                            FailureDescription.create(
                                    record.getStackTrace(),
                                    TestRecordProto.FailureStatus.TEST_FAILURE);
                    if (MoblyYamlResultRecordHandler.RecordResult.ERROR.equals(
                            record.getResult())) {
                        // Non-test failure reports indicates some early failure so we fail the run
                        if (!testDescription.getTestName().startsWith("test_")) {
                            for (ITestInvocationListener listener : listeners) {
                                listener.testRunFailed(failureDescription);
                            }
                            mRunFailed = true;
                            continue;
                        }
                    }
                    mRunStartTime =
                            mRunStartTime == 0L
                                    ? record.getBeginTime()
                                    : Math.min(mRunStartTime, record.getBeginTime());
                    mRunEndTime = Math.max(mRunEndTime, record.getEndTime());
                    for (ITestInvocationListener listener : listeners) {
                        listener.testStarted(testDescription, record.getBeginTime());
                        if (MoblyYamlResultRecordHandler.RecordResult.SKIP.equals(
                                record.getResult())) {
                            listener.testIgnored(testDescription);
                        } else if (!MoblyYamlResultRecordHandler.RecordResult.PASS.equals(
                                record.getResult())) {
                            listener.testFailed(testDescription, failureDescription);
                        }
                        listener.testEnded(
                                testDescription,
                                record.getEndTime(),
                                new HashMap<String, String>());
                    }
                    break;
                case USER_DATA:
                    long timestamp =
                            ((MoblyYamlResultUserDataHandler.UserData) result).getTimeStamp();
                    mRunStartTime =
                            mRunStartTime == 0L ? timestamp : Math.min(mRunStartTime, timestamp);
                    break;
                case CONTROLLER_INFO:
                    mRunEndTime =
                            Math.max(
                                    mRunEndTime,
                                    ((MoblyYamlResultControllerInfoHandler.ControllerInfo) result)
                                            .getTimeStamp());
                    break;
                case SUMMARY:
                    mEnded = true;
                    break;
                default:
                    // Do nothing
            }
        }
    }
}
