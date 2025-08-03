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
package com.android.tradefed.result.proto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.proto.TestRecordProtoUtil;

import com.google.common.truth.Truth;
import com.google.protobuf.Any;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Unit tests for {@link FileProtoResultReporter}. */
@RunWith(JUnit4.class)
public class FileProtoResultReporterTest {

    private FileProtoResultReporter mReporter;
    private File mOutput;
    private List<File> mToDelete = new ArrayList<>();
    @Mock ITestInvocationListener mMockListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mOutput = FileUtil.createTempFile("proto-file-reporter-test", ".pb");
        mReporter = new FileProtoResultReporter();
        mReporter.setFileOutput(mOutput);
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mOutput);
        for (File f : mToDelete) {
            FileUtil.deleteFile(f);
        }
    }

    @Test
    public void testWriteResults() throws Exception {
        assertEquals(0L, mOutput.length());
        IInvocationContext context = new InvocationContext();
        context.setConfigurationDescriptor(new ConfigurationDescriptor());
        context.addInvocationAttribute("test", "test");
        mReporter.invocationStarted(context);
        mReporter.invocationEnded(500L);

        // Something was outputted
        assertTrue(mOutput.length() != 0L);
        TestRecord record = TestRecordProtoUtil.readFromFile(mOutput);

        Any anyDescription = record.getDescription();
        assertTrue(anyDescription.is(Context.class));

        IInvocationContext endContext =
                InvocationContext.fromProto(anyDescription.unpack(Context.class));
        assertEquals("test", endContext.getAttributes().get("test").get(0));
    }

    @Test
    public void testWriteResults_periodic() throws Exception {
        OptionSetter setter = new OptionSetter(mReporter);
        setter.setOptionValue("periodic-proto-writing", "true");
        setter.setOptionValue("proto-output-file", mOutput.getAbsolutePath());
        TestDescription test1 = new TestDescription("class1", "test1");
        IInvocationContext context = new InvocationContext();
        context.setConfigurationDescriptor(new ConfigurationDescriptor());
        context.addInvocationAttribute("test", "test");
        mReporter.invocationStarted(context);
        IInvocationContext module1Context = createModuleContext("module1");
        mReporter.testModuleStarted(module1Context);
        mReporter.testRunStarted("run1", 1);
        mReporter.testStarted(test1);
        mReporter.testEnded(test1, new HashMap<String, Metric>());
        mReporter.testRunEnded(200L, new HashMap<String, Metric>());
        module1Context.addInvocationAttribute(ITestSuite.MODULE_END_TIME, "endTime");
        mReporter.testModuleEnded();
        IInvocationContext module2Context = createModuleContext("module2");
        mReporter.testModuleStarted(module2Context);
        module2Context.addInvocationAttribute(ITestSuite.MODULE_END_TIME, "endTime");
        mReporter.testModuleEnded();
        // Run without a module
        mReporter.testRunStarted("run2", 1);
        mReporter.testStarted(test1);
        mReporter.testEnded(test1, new HashMap<String, Metric>());
        mReporter.testRunEnded(200L, new HashMap<String, Metric>());
        mReporter.invocationEnded(500L);

        int index = 0;
        File currentOutput = new File(mOutput.getAbsolutePath() + index);
        if (!currentOutput.exists()) {
            fail("Should have output at least one file");
        }

        ProtoResultParser parser = new ProtoResultParser(mMockListener, context, true);
        while (currentOutput.exists()) {
            mToDelete.add(currentOutput);
            parser.processFileProto(currentOutput);
            index++;
            currentOutput = new File(mOutput.getAbsolutePath() + index);
        }

        ArgumentCaptor<IInvocationContext> moduleCaptor =
                ArgumentCaptor.forClass(IInvocationContext.class);
        verify(mMockListener).invocationStarted(Mockito.any());
        verify(mMockListener, times(2)).testModuleStarted(moduleCaptor.capture());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener, times(2)).testStarted(Mockito.eq(test1), Mockito.anyLong());
        verify(mMockListener, times(2))
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener, times(2)).testRunEnded(200L, new HashMap<String, Metric>());
        verify(mMockListener, times(2)).testModuleEnded();
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).invocationEnded(500L);

        IInvocationContext capturedModuleContext = moduleCaptor.getValue();
        Truth.assertThat(capturedModuleContext.getAttribute(ITestSuite.MODULE_END_TIME))
                .isEqualTo("endTime");
    }

    private IInvocationContext createModuleContext(String moduleId) {
        IInvocationContext context = new InvocationContext();
        context.addInvocationAttribute(ModuleDefinition.MODULE_ID, moduleId);
        context.setConfigurationDescriptor(new ConfigurationDescriptor());
        context.addInvocationAttribute(ITestSuite.MODULE_START_TIME, "startTime");
        return context;
    }

    @Test
    public void testWriteReadAtestResults() throws Exception {
        assertEquals(0L, mOutput.length());
        OptionSetter setter = new OptionSetter(mReporter);
        setter.setOptionValue("use-delimited-api", "false");
        setter.setOptionValue("proto-output-file", mOutput.getAbsolutePath());
        TestRecord.Builder testRecord = TestRecord.newBuilder();
        TestRecordProto.ChildReference.Builder child1 = TestRecordProto.ChildReference.newBuilder();
        child1.setTestRecordId("child record1");

        TestRecordProto.ChildReference.Builder child2 = TestRecordProto.ChildReference.newBuilder();
        child2.setTestRecordId("child record2");
        testRecord.setTestRecordId("record id").addChildren(child1).addChildren(child2);
        mReporter.processFinalProto(testRecord.build());

        TestRecord record = TestRecordProtoUtil.readFromFile(mOutput, false);
        assertEquals("record id", record.getTestRecordId());
        assertEquals("child record1", record.getChildren(0).getTestRecordId());
        assertEquals("child record2", record.getChildren(1).getTestRecordId());
    }
}
