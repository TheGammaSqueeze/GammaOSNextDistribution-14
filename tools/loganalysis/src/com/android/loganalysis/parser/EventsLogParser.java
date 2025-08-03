/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.loganalysis.parser;

import com.android.loganalysis.item.IItem;
import com.android.loganalysis.item.BootEventItem;
import com.android.loganalysis.item.LatencyItem;
import com.android.loganalysis.item.TransitionDelayItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse the events logs. </p>
 */
public class EventsLogParser implements IParser {

    private static final String DATE = "date";
    private static final String TIME = "time";
    private static final String TRANSITION_INFO = "transitioninfo";
    private static final String PACKAGE_KEY = "806";
    private static final String ACTIVITY_KEY = "871";
    private static final String TRANSITION_DELAY_KEY = "319";
    private static final String STARTING_WINDOW_DELAY_KEY = "321";
    private static final String COLD_LAUNCH_KEY = "945";
    private static final String WINDOWS_DRAWN_DELAY_KEY = "322";
    private static final String BOOT_PROGRESS = "boot_progress_";
    private static final String STOP_ANIM = "sf_stop_bootanim";

    // 09-18 23:56:19.376 1140 1221 I sysui_multi_action:
    // [319,51,321,50,322,190,325,670,757,761,758,7,759,1,806,com.google.android.calculator,871,
    // com.android.calculator2.Calculator,905,0,945,41]
    private static final Pattern SYSUI_TRANSITION_INFO_PATTERN = Pattern.compile(
            "^(?<date>[0-9-]*)\\s+(?<time>[0-9:.]*)\\s+\\d+\\s+\\d+ I sysui_multi_action:"
                    + " \\[(?<transitioninfo>.*)\\]$");

    // 08-21 17:53:53.876 1053 2135 I sysui_latency: [1,50]
    private static final Pattern ACTION_LATENCY = Pattern.compile("^(?<date>[0-9-]*)\\s+"
            + "(?<time>[0-9:.]*)\\s+\\d+\\s+\\d+ I sysui_latency: \\[(?<action>.*),"
            + "(?<delay>.*)\\]$");

    // 05-24 09:14:59.999   720   720 I boot_progress_start: 40429
    private static final Pattern BOOT_DURATION =
            Pattern.compile(
                    String.format(
                            "^\\d+-\\d+\\s(?<time>[0-9:.]*)\\s+\\S+\\s+\\S+\\sI (?<event>%s\\S+): "
                                    + "(?<duration>\\d+)$",
                            BOOT_PROGRESS));

    // 05-24 09:15:20.737   516   542 I sf_stop_bootanim: 61167
    private static final Pattern STOPANIM_DURATION =
            Pattern.compile(
                    String.format(
                            "^\\d+-\\d+\\s(?<time>[0-9:.]*)\\s+\\S+\\s+\\S+\\sI (?<event>%s): "
                                    + "(?<duration>\\d+)$",
                            STOP_ANIM));

    @Override
    public IItem parse(List<String> lines) {
        throw new UnsupportedOperationException("Method has not been implemented in lieu"
                + " of others");
    }

    /**
     * Parse the transition delay information from the events log.
     * @param input
     * @return list of transition delay items.
     * @throws IOException
     */
    public List<TransitionDelayItem> parseTransitionDelayInfo(BufferedReader input)
            throws IOException {
        List<TransitionDelayItem> transitionDelayItems = new ArrayList<TransitionDelayItem>();
        String line;
        Matcher match = null;
        while ((line = input.readLine()) != null) {
            if ((match = matches(SYSUI_TRANSITION_INFO_PATTERN, line)) != null) {
                Map<String, String> transitionInfoMap = getTransitionInfoMap(
                        match.group(TRANSITION_INFO));
                if (transitionInfoMap.containsKey(TRANSITION_DELAY_KEY)) {
                    TransitionDelayItem delayItem = new TransitionDelayItem();
                    if (null != transitionInfoMap.get(PACKAGE_KEY)
                            && null != transitionInfoMap.get(ACTIVITY_KEY)
                            && null != transitionInfoMap.get(TRANSITION_DELAY_KEY)
                            && null != transitionInfoMap.get(WINDOWS_DRAWN_DELAY_KEY)) {
                        delayItem.setComponentName(transitionInfoMap.get(PACKAGE_KEY) + "/"
                                + transitionInfoMap.get(ACTIVITY_KEY));
                        delayItem.setTransitionDelay(Long.parseLong(transitionInfoMap
                                .get(TRANSITION_DELAY_KEY)));
                        delayItem.setDateTime(String.format("%s %s", match.group(DATE),
                                match.group(TIME)));
                        delayItem.setWindowDrawnDelay(
                                Long.parseLong(transitionInfoMap.get(WINDOWS_DRAWN_DELAY_KEY)));
                    }
                    if (transitionInfoMap.containsKey(COLD_LAUNCH_KEY)) {
                        if (null != transitionInfoMap.get(STARTING_WINDOW_DELAY_KEY)) {
                            delayItem.setStartingWindowDelay(Long.parseLong(transitionInfoMap
                                    .get(STARTING_WINDOW_DELAY_KEY)));
                        }
                    }
                    transitionDelayItems.add(delayItem);
                }
            }
        }
        return transitionDelayItems;
    }

    /**
     * Split the transition info string in to key, values and return a map.
     * @param transitionInfo transition info map in hey value format.
     * @return
     */
    public Map<String, String> getTransitionInfoMap(String transitionInfo) {
        String[] transitionSplit = transitionInfo.split(",");
        Map<String, String> transitionInfoMap = new HashMap<>();
        if (transitionSplit.length % 2 == 0) {
            for (int i = 0; i < transitionSplit.length; i = i + 2) {
                transitionInfoMap.put(transitionSplit[i], transitionSplit[i + 1]);
            }
        }
        return transitionInfoMap;
    }

    /**
     * Method to parse the latency information from the events log
     * @param input
     * @return
     * @throws IOException
     */
    public List<LatencyItem> parseLatencyInfo(BufferedReader input) throws IOException {
        List<LatencyItem> latencyItems = new ArrayList<LatencyItem>();
        String line;
        while ((line = input.readLine()) != null) {
            Matcher match = null;
            if ((match = matches(ACTION_LATENCY, line)) != null) {
                LatencyItem latencyItem = new LatencyItem();
                latencyItem.setActionId(Integer.parseInt(match.group("action")));
                latencyItem.setDelay(Long.parseLong(match.group("delay")));
                latencyItems.add(latencyItem);
            }
        }
        return latencyItems;
    }

    /**
     * Method to parse the boot information from the events log
     *
     * @param input
     * @return
     * @throws IOException
     */
    public List<BootEventItem> parseBootEventInfo(BufferedReader input) throws IOException {
        List<BootEventItem> eventItems = new ArrayList<BootEventItem>();
        String line;
        while ((line = input.readLine()) != null) {
            Matcher match = null;
            if ((match = matches(BOOT_DURATION, line)) != null
                    || (match = matches(STOPANIM_DURATION, line)) != null) {
                BootEventItem eventItem = new BootEventItem();
                eventItem.setEventName(match.group("event"));
                eventItem.setDuration(Double.parseDouble(match.group("duration")));
                eventItems.add(eventItem);
            }
        }
        return eventItems;
    }

    /**
     * Checks whether {@code line} matches the given {@link Pattern}.
     *
     * @return The resulting {@link Matcher} obtained by matching the {@code line} against
     *         {@code pattern}, or null if the {@code line} does not match.
     */
    private static Matcher matches(Pattern pattern, String line) {
        Matcher ret = pattern.matcher(line);
        return ret.matches() ? ret : null;
    }

}
