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

package com.android.media.audiotestharness.server.javasound;

import javax.sound.sampled.Control;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Mixer.Info;
import javax.sound.sampled.TargetDataLine;

/**
 * Test implementation of the {@link Mixer} interface that scaffolds out enough functionality to be
 * able to test the {@link JavaAudioSystemService}.
 */
public class TestMixer implements Mixer {

    private final String mName;
    private final int mTargetLineCount;
    private final TargetDataLine mTargetDataLine;
    private final TestInfo mTestInfo;

    /**
     * Constructor.
     *
     * @param name the name that should be provided in the {@link Mixer.Info} returned by the {@link
     *     #getMixerInfo()} method.
     * @param targetLineCount the number of target lines this Mixer should expose, in general, this
     *     means that the returned arrays for any target line method are of this size.
     */
    public TestMixer(String name, int targetLineCount, TargetDataLine targetDataLine) {
        mName = name;
        mTargetLineCount = targetLineCount;
        mTargetDataLine = targetDataLine;
        mTestInfo = new TestInfo(mName, null, null, null);
    }

    @Override
    public Info getMixerInfo() {
        return mTestInfo;
    }

    @Override
    public Line.Info[] getSourceLineInfo() {
        return new Line.Info[0];
    }

    @Override
    public Line.Info[] getTargetLineInfo() {
        return new Line.Info[mTargetLineCount];
    }

    @Override
    public Line.Info[] getSourceLineInfo(Line.Info info) {
        return new Line.Info[0];
    }

    @Override
    public Line.Info[] getTargetLineInfo(Line.Info info) {
        return new Line.Info[mTargetLineCount];
    }

    @Override
    public boolean isLineSupported(Line.Info info) {
        return false;
    }

    @Override
    public Line getLine(Line.Info info) throws LineUnavailableException {
        if (mTargetDataLine == null) {
            throw new LineUnavailableException("Unavailable");
        } else {
            return mTargetDataLine;
        }
    }

    @Override
    public int getMaxLines(Line.Info info) {
        return 0;
    }

    @Override
    public Line[] getSourceLines() {
        return new Line[0];
    }

    @Override
    public Line[] getTargetLines() {
        return new Line[mTargetLineCount];
    }

    @Override
    public void synchronize(Line[] lines, boolean maintainSync) {}

    @Override
    public void unsynchronize(Line[] lines) {}

    @Override
    public boolean isSynchronizationSupported(Line[] lines, boolean maintainSync) {
        return false;
    }

    @Override
    public Line.Info getLineInfo() {
        return null;
    }

    @Override
    public void open() throws LineUnavailableException {}

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public Control[] getControls() {
        return new Control[0];
    }

    @Override
    public boolean isControlSupported(Control.Type control) {
        return false;
    }

    @Override
    public Control getControl(Control.Type control) {
        return null;
    }

    @Override
    public void addLineListener(LineListener listener) {}

    @Override
    public void removeLineListener(LineListener listener) {}

    /** Test implementation of the Mixer.Info class for visibility here. */
    public static class TestInfo extends Info {

        /**
         * Constructs a mixer's info object, passing it the given textual information.
         *
         * @param name the name of the mixer
         * @param vendor the company who manufactures or creates the hardware or software mixer
         * @param description descriptive text about the mixer
         * @param version version information for the mixer
         */
        protected TestInfo(String name, String vendor, String description, String version) {
            super(name, vendor, description, version);
        }
    }
}
