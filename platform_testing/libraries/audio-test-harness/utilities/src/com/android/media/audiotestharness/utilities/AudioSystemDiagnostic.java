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

package com.android.media.audiotestharness.utilities;

import java.util.Arrays;
import java.util.logging.Logger;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

/**
 * Helper binary that runs a diagnostic with the AudioSystem class to see if audio devices can be
 * seen. This class can also be used to determine the name that the Java Audio System has assigned
 * to the various devices connected to the system.
 */
public class AudioSystemDiagnostic {
    public static final Logger LOGGER = Logger.getLogger(AudioSystemDiagnostic.class.getName());

    public static void main(String args[]) {
        LOGGER.info("Audio System Diagnostic follows:\n" + buildAudioSystemDiagnosticString());
    }

    /**
     * Builds a string containing diagnostic information about the Java Sound API and the devices it
     * can recognize.
     */
    public static String buildAudioSystemDiagnosticString() {
        StringBuilder diagnosticStringBuilder = new StringBuilder();

        diagnosticStringBuilder.append("=====  Java Audio System Diagnostic  =====\n\n");

        diagnosticStringBuilder.append("-----  Java Version Information  -----\n");
        diagnosticStringBuilder.append(
                "Java Version = " + System.getProperty("java.runtime.version") + "\n\n");

        diagnosticStringBuilder.append("-----  Java Audio System Output  -----\n");

        Mixer.Info[] mixers = AudioSystem.getMixerInfo();

        diagnosticStringBuilder.append("Mixers Array = " + Arrays.toString(mixers) + "\n");
        diagnosticStringBuilder.append("Number of Mixers = " + mixers.length + "\n");

        for (int i = 0; i < mixers.length; i++) {
            Mixer.Info currentMixer = mixers[i];
            diagnosticStringBuilder.append("\n");
            diagnosticStringBuilder.append(
                    "----- Start Mixer (#" + Integer.toString(i) + ")  -----\n");
            diagnosticStringBuilder.append("Mixer Name = " + currentMixer.getName() + "\n");
            diagnosticStringBuilder.append("Mixer Vendor = " + currentMixer.getVendor() + "\n");
            diagnosticStringBuilder.append("Mixer Version = " + currentMixer.getVersion() + "\n");
            diagnosticStringBuilder.append(
                    "Mixer Description = " + currentMixer.getDescription() + "\n");
            diagnosticStringBuilder.append(
                    "----- End Mixer (#" + Integer.toString(i) + ")  -----\n");
        }

        return diagnosticStringBuilder.toString();
    }
}
