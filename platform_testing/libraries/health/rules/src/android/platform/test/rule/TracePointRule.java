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
package android.platform.test.rule;

import android.os.Trace;

import androidx.annotation.VisibleForTesting;

import com.google.common.base.Strings;

import org.junit.runner.Description;

/**
 * This rule will create a trace section that captures the start and end of each test method.
 */
public class TracePointRule extends TestWatcher {
    // Matches android.os.Trace#MAX_SECTION_NAME_LEN
    private static final int MAX_SECTION_NAME_LEN = 127;

    private final String mSectionTag;

    public TracePointRule() {
        mSectionTag = "";
    }

    public TracePointRule(String sectionTag) {
        mSectionTag = sectionTag;
    }

    @Override
    protected void starting(Description description) {
        beginSection(getSectionName(description));
    }

    @Override
    protected void finished(Description description) {
        endSection();
    }

    /**
     * Begins a {@link Trace} section with the tag, {@code sectionTag}.
     */
    protected void beginSection(String sectionTag) {
        Trace.beginSection(sectionTag);
    }

    /**
     * Ends the last started {@link Trace} section, regardless of tag.
     */
    protected void endSection() {
        Trace.endSection();
    }

    /**
     * Returns the section name used for the applied {@code TestWatcher}.
     */
    protected String getSectionName(Description description) {
        String name = mSectionTag;
        if (Strings.isNullOrEmpty(name)) {
            name = description.getDisplayName();
        }
        return name.substring(Math.max(0, name.length() - getMaxSectionLength()));
    }

    @VisibleForTesting
    int getMaxSectionLength() {
        return MAX_SECTION_NAME_LEN;
    }
}
