/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.ConfigurationException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/** Unit test for {@link LocalEmulatorLaunch} */
@RunWith(JUnit4.class)
public class LocalEmulatorLaunchTest {

    /** Simple test for args building */
    @Test
    public void buildEmulatorLaunchArgs() throws ConfigurationException {
        LocalEmulatorLaunch l = new LocalEmulatorLaunch();
        ArgsOptionParser parser = new ArgsOptionParser(l);
        parser.parse(
                "--emulator-path", "emupath", "--gpu", "mygpu", "--feature", "NoDraw,MyFeature");

        List<String> emulatorArgs = l.buildEmulatorLaunchArgs();
        assertThat(emulatorArgs.get(0)).isEqualTo("emupath");
        assertThat(emulatorArgs).containsAtLeast("-gpu", "mygpu").inOrder();
        assertThat(emulatorArgs).containsAtLeast("-feature", "NoDraw,MyFeature").inOrder();
    }
}
