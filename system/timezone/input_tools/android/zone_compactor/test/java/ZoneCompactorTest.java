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

import com.android.libcore.timezone.testing.TestUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.US_ASCII;

public final class ZoneCompactorTest {

    private static final String TZDB_VERSION = "tzdata2030a";
    private static final byte[] ARBITRARY_TZIF_CONTENT = "TZif3contentgoeshere".getBytes(US_ASCII);

    private Path inputFilesDir;
    private Path outputFilesDir;

    @Before
    public void setup() throws Exception {
        inputFilesDir = Files.createTempDirectory("tzdata");
        outputFilesDir = Files.createTempDirectory("output");
    }

    @Test
    public void shouldFollowLinkChains_definedInForwardOrder() throws Exception {
        String zoneAndLinks =
                "Link     America/Toronto     America/Rainy_Inlet\n" +
                "Link     America/Rainy_Inlet     America/Nipigon\n" +
                "Zone     America/Toronto";

        String setupFile = TestUtils.createFile(inputFilesDir, zoneAndLinks);
        Files.createDirectories(inputFilesDir.resolve("America"));
        Path americaToronto = Files.createFile(inputFilesDir.resolve("America").resolve("Toronto"));

        Files.write(americaToronto, ARBITRARY_TZIF_CONTENT);

        new ZoneCompactor(
                setupFile,
                inputFilesDir.toString(),
                outputFilesDir.toString(),
                TZDB_VERSION);

        Path resultTzdata = outputFilesDir.resolve("tzdata");
        assertTrue(Files.exists(resultTzdata));

        try (FileInputStream fis = new FileInputStream(resultTzdata.toFile())) {
            byte[] allData = TestUtils.readFully(fis);

            assertEquals(tzdataLength(3, List.of(ARBITRARY_TZIF_CONTENT)), allData.length);
        }
    }

    @Test
    public void shouldFollowLinkChains_definedInBackwardsOrder() throws Exception {
        String zoneAndLinks =
                "Link     America/Rainy_Inlet     America/Nipigon\n" +
                "Link     America/Toronto     America/Rainy_Inlet\n" +
                "Zone     America/Toronto";

        String setupFile = TestUtils.createFile(inputFilesDir, zoneAndLinks);
        Files.createDirectories(inputFilesDir.resolve("America"));
        Path americaToronto = Files.createFile(inputFilesDir.resolve("America").resolve("Toronto"));

        Files.write(americaToronto, ARBITRARY_TZIF_CONTENT);

        new ZoneCompactor(
                setupFile,
                inputFilesDir.toString(),
                outputFilesDir.toString(),
                TZDB_VERSION);

        Path resultTzdata = outputFilesDir.resolve("tzdata");
        assertTrue(Files.exists(resultTzdata));

        try (FileInputStream fis = new FileInputStream(resultTzdata.toFile())) {
            byte[] allData = TestUtils.readFully(fis);

            assertEquals(tzdataLength(3, List.of(ARBITRARY_TZIF_CONTENT)), allData.length);
        }
    }

    @Test
    public void shouldFail_ifSetupFileHasMultipleZoneEntriesOfTheSameTimeZoneId() throws Exception {
        String zoneAndLinks =
                "Zone     America/Toronto\n" +
                "Zone     America/Toronto";

        String setupFile = TestUtils.createFile(inputFilesDir, zoneAndLinks);
        Files.createDirectories(inputFilesDir.resolve("America"));
        Path americaToronto = Files.createFile(inputFilesDir.resolve("America").resolve("Toronto"));

        Files.write(americaToronto, ARBITRARY_TZIF_CONTENT);

        assertThrows(
                IllegalStateException.class,
                () -> new ZoneCompactor(
                        setupFile,
                        inputFilesDir.toString(),
                        outputFilesDir.toString(),
                        TZDB_VERSION));
    }

    private static int tzdataLength(int timeZonesCount, List<byte[]> tzifContents) {
        int allDataLength = tzifContents.stream().mapToInt(array -> array.length).sum();

        // 12 bytes is version - tzdataYYYYa\0
        // 4 * 3 - version is followed by 3 ints: index, data, and final offsets.
        // 40 + 4 * 3 - 40 is for time zone name, followed by offset, length, and unused int.
        // The rest is all data concatenated as-it-is.
        return 12 + 3 * 4 + (40 + 4 * 3) * timeZonesCount + allDataLength;
    }
}
