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

package com.android.sts.common.util;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RunWith(DeviceJUnit4ClassRunner.class)
public class UnameVersionTest extends BaseHostJUnit4Test {

    // https://plx.corp.google.com/scripts2/script_62._8e8d32_0000_2455_b2ca_3c286d390792
    // manually removed csv header
    // manually removed because no year:
    //     "#1 repo:AndroidTV_11_AML_Genesis SMP PREEMPT Fri Sep 10 05:36:27"
    private static final String UNAME_VERSIONS_RESOURCE = "edi_uname_versions.txt";

    @Test
    public final void testParseBuildTimestamp() throws Exception {
        List<String> violations = new ArrayList<>();
        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                getClass()
                                        .getClassLoader()
                                        .getResourceAsStream(UNAME_VERSIONS_RESOURCE)));
        String version = null;
        while ((version = reader.readLine()) != null) {
            Optional<LocalDate> ts = UnameVersion.parseBuildTimestamp(version);
            if (!ts.isPresent()) {
                violations.add(version);
            }
        }
        assertTrue(violations.toString(), violations.isEmpty());
    }
}
