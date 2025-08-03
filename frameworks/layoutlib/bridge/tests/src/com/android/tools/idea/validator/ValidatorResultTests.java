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

package com.android.tools.idea.validator;

import com.android.tools.idea.validator.ValidatorData.Issue.IssueBuilder;
import com.android.tools.idea.validator.ValidatorData.Level;
import com.android.tools.idea.validator.ValidatorData.Type;
import com.android.tools.idea.validator.ValidatorResult.Builder;
import com.android.tools.idea.validator.ValidatorResult.Metric;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ValidatorResultTests {
    private static final long EPSILON = 10L;

    @Test
    public void testBuildingEmptyResult() {
        ValidatorResult result = new ValidatorResult.Builder().build();
        assertNotNull(result);
        assertTrue(result.getIssues().isEmpty());
        assertTrue(result.getSrcMap().isEmpty());
        assertTrue(result.getNodeInfoMap().isEmpty());
        assertNotNull(result.getMetric());
        assertEquals("Result containing 0 issues:\n", result.toString());
    }

    @Test
    public void testBuildingResult() {
        ValidatorResult.Builder builder = new ValidatorResult.Builder();
        for (int i = 0; i < 3; i++) {
            builder.mIssues.add(createIssueBuilder().setMsg("issue " + i).build());
        }
        assertEquals(
                "Result containing 3 issues:\n" +
                        " - [ERROR] issue 0\n" +
                        " - [ERROR] issue 1\n" +
                        " - [ERROR] issue 2\n",
                builder.build().toString());
    }

    private static IssueBuilder createIssueBuilder() {
        return new IssueBuilder()
                .setCategory("category")
                .setType(Type.ACCESSIBILITY)
                .setMsg("msg")
                .setLevel(Level.ERROR)
                .setSourceClass("Source class");
    }

    @Ignore("b/172205439")
    @Test
    public void testMetricRecordHierarchyCreationTime() {
        long expectedElapsed = 100;
        Metric metric = new Builder().mMetric;

        metric.startHierarchyCreationTimer();
        try {
            Thread.sleep(expectedElapsed);
        } catch (Exception e) {
            String msg = "Unexpected exception. ";
            if (e.getMessage() == null) {
                msg += e.getMessage();
            }
            fail(msg);
        }
        metric.recordHierarchyCreationTime();

        long diff = Math.abs(expectedElapsed - metric.mHierarchyCreationMs);
        System.out.println(diff);
        assertTrue(diff < EPSILON);
    }

    @Test
    public void testMetricToString() {
        Metric metric = new Builder().mMetric;

        metric.mErrorMessage = "TestError";
        metric.mImageMemoryBytes = Long.MAX_VALUE;

        assertEquals(
                "Validation result metric: { hierarchy creation=0ms, image memory=9223372036gb }",
                metric.toString());
    }

}
