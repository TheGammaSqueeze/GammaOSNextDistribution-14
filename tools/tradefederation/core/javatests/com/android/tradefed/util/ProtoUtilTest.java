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
package com.android.tradefed.util;

import static org.junit.Assert.assertArrayEquals;

import com.android.tradefed.util.test.ProtoUtilTestProto.TestMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link ProtoUtil} */
@RunWith(Parameterized.class)
public class ProtoUtilTest {
    @Parameter(0)
    public String mTestName; // Unused, for identifying tests only.

    @Parameter(1)
    public TestMessage mMessage;

    @Parameter(2)
    public List<String> mReferences;

    @Parameter(3)
    public List<String> mExpectedResults;

    @Parameters(name = "{0}#{index}")
    public static Iterable<Object[]> data() {
        List<Object[]> parameters = new ArrayList<>();
        parameters.add(
                new Object[] {
                    "returnsMessageAsStringForEmptyReference",
                    TestMessage.newBuilder().setIntField(7).build(),
                    new ArrayList<String>(),
                    Arrays.asList(TestMessage.newBuilder().setIntField(7).build().toString())
                });
        parameters.add(
                new Object[] {
                    "singleLevel",
                    TestMessage.newBuilder().setIntField(7).build(),
                    Arrays.asList("int_field"),
                    Arrays.asList("7")
                });
        parameters.add(
                new Object[] {
                    "singleLevel",
                    TestMessage.newBuilder().setStringField("string").build(),
                    Arrays.asList("string_field"),
                    Arrays.asList("string")
                });
        parameters.add(
                new Object[] {
                    "singleLevel",
                    TestMessage.newBuilder()
                            .setMessageField(TestMessage.SubMessage.newBuilder().setIntField(7))
                            .build(),
                    Arrays.asList("message_field"),
                    Arrays.asList(TestMessage.SubMessage.newBuilder().setIntField(7).toString())
                });
        parameters.add(
                new Object[] {
                    "singleLevelRepeated",
                    TestMessage.newBuilder()
                            .addAllRepeatedStringField(Arrays.asList("string1", "string2"))
                            .build(),
                    Arrays.asList("repeated_string_field"),
                    Arrays.asList("string1", "string2")
                });
        parameters.add(
                new Object[] {
                    "multiLevel",
                    TestMessage.newBuilder()
                            .setMessageField(TestMessage.SubMessage.newBuilder().setIntField(7))
                            .build(),
                    Arrays.asList("message_field", "int_field"),
                    Arrays.asList("7")
                });
        parameters.add(
                new Object[] {
                    "multiLevelRepeated",
                    TestMessage.newBuilder()
                            .setMessageField(
                                    TestMessage.SubMessage.newBuilder()
                                            .addAllRepeatedStringField(
                                                    Arrays.asList("string1", "string2")))
                            .build(),
                    Arrays.asList("message_field", "repeated_string_field"),
                    Arrays.asList("string1", "string2")
                });
        parameters.add(
                new Object[] {
                    "multiLevelRepeated",
                    TestMessage.newBuilder()
                            .addAllRepeatedMessageField(
                                    Arrays.asList(
                                            TestMessage.SubMessage.newBuilder()
                                                    .addAllRepeatedStringField(
                                                            Arrays.asList("string1", "string2"))
                                                    .build(),
                                            TestMessage.SubMessage.newBuilder()
                                                    .addAllRepeatedStringField(
                                                            Arrays.asList("string3", "string4"))
                                                    .build()))
                            .build(),
                    Arrays.asList("repeated_message_field", "repeated_string_field"),
                    Arrays.asList("string1", "string2", "string3", "string4")
                });
        parameters.add(
                new Object[] {
                    "oneofSingleLevel",
                    TestMessage.newBuilder().setOneofStringField("string").build(),
                    Arrays.asList("oneof_string_field"),
                    Arrays.asList("string")
                });
        parameters.add(
                new Object[] {
                    "oneofMultiLevel",
                    TestMessage.newBuilder()
                            .setOneofMessageField(
                                    TestMessage.SubMessage.newBuilder()
                                            .addAllRepeatedStringField(
                                                    Arrays.asList("string1", "string2")))
                            .build(),
                    Arrays.asList("oneof_message_field", "repeated_string_field"),
                    Arrays.asList("string1", "string2")
                });
        parameters.add(
                new Object[] {
                    "returnsEmptyForNonExistentFieldReference",
                    TestMessage.newBuilder().setStringField("string").build(),
                    Arrays.asList("not_a_field"),
                    new ArrayList<String>()
                });
        parameters.add(
                new Object[] {
                    "returnsEmptyForNonExistentFieldReference",
                    TestMessage.newBuilder()
                            .setMessageField(TestMessage.SubMessage.newBuilder().setIntField(7))
                            .build(),
                    Arrays.asList("message_field", "not_a_field"),
                    new ArrayList<String>()
                });
        parameters.add(
                new Object[] {
                    "returnsEmptyForNonExistentFieldReference",
                    TestMessage.newBuilder()
                            .setMessageField(TestMessage.SubMessage.newBuilder().setIntField(7))
                            .build(),
                    Arrays.asList("message_field", "int_field", "not_a_field"),
                    new ArrayList<String>()
                });
        return parameters;
    }

    @Test
    public void testParsing() {
        assertArrayEquals(
                mExpectedResults.toArray(),
                ProtoUtil.getNestedFieldFromMessageAsStrings(mMessage, mReferences).toArray());
    }
}
