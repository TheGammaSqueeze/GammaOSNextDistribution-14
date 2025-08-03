/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.car.cts.utils;

import android.util.ArrayMap;

import com.android.compatibility.common.util.ShellUtils;

import java.util.Map;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Class contains utility methods to dumpsys for car services
 */
public final class DumpUtils {
    private DumpUtils() {}

    /**
     * Dumps a given CarService by shell command.
     * It will parse the dump output with ':' or '=' separator and put it into a map with the first
     * part as a key and the second part as a value.
     *
     * @param serviceName Name of service to be dumped.
     * @return map contains the dump output
     */
    public static Map<String, String> executeDumpShellCommand(String serviceName) {
        String output = ShellUtils.runShellCommand(
                "dumpsys car_service --services " + serviceName);
        return parseDumpResult(serviceName, output);
    }

    private static ArrayMap<String, String> parseDumpResult(String serviceName, String output) {
        Scanner scanner = new Scanner(output);
        ArrayMap<String, String> map = new ArrayMap<>();
        // Pattern to match the title, For example:
        //   *CarMediaService*
        Pattern patternHeader = Pattern.compile("\\*" + serviceName + "\\*");
        // Pattern to match key-value with ':' or '=' as separator. For example:
        //   mPlayOnBootConfig=2
        //   MediaConnectorService: com.android.car.media/.service.MediaConnectorService
        Pattern patternBody = Pattern.compile("\\s*(\\w+)\\s*[:=]\\s*(\\S+)");
        Pattern currentPattern = patternHeader;
        while (scanner.hasNextLine()) {
            scanner.nextLine();
            String matchedLine = scanner.findInLine(currentPattern);
            if (matchedLine == null) {
                continue;
            }
            if (currentPattern == patternHeader) {
                currentPattern = patternBody;
                continue;
            }
            MatchResult match = scanner.match();
            map.put(match.group(1), match.group(2));
        }
        return map;
    }
}
