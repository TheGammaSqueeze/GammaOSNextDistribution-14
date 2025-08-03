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

package com.android.loganalysis.item;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** An {@link IItem} used to store Module info logged in dmesg. */
public class DmesgModuleInfoItem extends GenericItem {

    /** Constant for JSON output */
    public static final String MODULE_NAME = "MODULE_NAME";

    public static final String MODULE_START_TIME = "MODULE_START_TIME";

    public static final String MODULE_END_TIME = "MODULE_END_TIME";

    public static final String MODULE_COUNT = "MODULE_COUNT";

    public static final String MODULE_DURATION = "MODULE_DURATION";

    private static final Set<String> ATTRIBUTES =
            new HashSet<String>(
                    Arrays.asList(
                            MODULE_NAME,
                            MODULE_START_TIME,
                            MODULE_END_TIME,
                            MODULE_COUNT,
                            MODULE_DURATION));

    /** The constructor for {@link DmesgModuleInfoItem}. */
    public DmesgModuleInfoItem() {
        super(ATTRIBUTES);
    }

    /** Set the name of the Module */
    public void setModuleName(String moduleName) {
        setAttribute(MODULE_NAME, moduleName);
    }

    /** Get the name of the Module */
    public String getModuleName() {
        return (String) getAttribute(MODULE_NAME);
    }

    /** Get the count of modules */
    public String getModuleCount() {
        return (String) getAttribute(MODULE_COUNT);
    }

    /** Set the count of modules */
    public void setModuleCount(String moduleName) {
        setAttribute(MODULE_COUNT, moduleName);
    }

    /** Get the start time in msecs */
    public Long getStartTime() {
        return (Long) getAttribute(MODULE_START_TIME);
    }

    /** Set the start time in msecs */
    public void setStartTime(Long startTime) {
        setAttribute(MODULE_START_TIME, startTime);
    }

    /** Get the end time in msecs */
    public Long getEndTime() {
        return (Long) getAttribute(MODULE_END_TIME);
    }

    /** Set the end time in msecs */
    public void setEndTime(Long endTime) {
        setAttribute(MODULE_END_TIME, endTime);
    }

    /**
     * Get the module loading time in msecs If the start or end time is not present then return -1
     */
    public Long getModuleDuration() {
        if (null != getAttribute(MODULE_DURATION)) {
            return (Long) getAttribute(MODULE_DURATION);
        }
        if (null != getAttribute(MODULE_END_TIME) && null != getAttribute(MODULE_START_TIME)) {
            long duration = getEndTime() - getStartTime();
            setModuleDuration(duration);
            return duration;
        }
        return -1L;
    }

    /** Get the duration in msec */
    public void setModuleDuration(Long duration) {
        setAttribute(MODULE_DURATION, duration);
    }

    @Override
    public String toString() {
        return "ModuleInfoItem ["
                + "getModuleName()="
                + getModuleName()
                + ", getStartTime()="
                + getStartTime()
                + ", getDuration()="
                + getModuleDuration()
                + "]";
    }
}
