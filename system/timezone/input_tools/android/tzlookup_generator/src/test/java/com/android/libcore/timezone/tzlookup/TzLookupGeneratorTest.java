/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.libcore.timezone.tzlookup;

import static com.android.libcore.timezone.countryzones.proto.CountryZonesFile.Country;
import static com.android.libcore.timezone.testing.TestUtils.assertAbsent;
import static com.android.libcore.timezone.testing.TestUtils.assertContains;
import static com.android.libcore.timezone.testing.TestUtils.createFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.libcore.timezone.countryzones.proto.CountryZonesFile;
import com.android.libcore.timezone.testing.TestUtils;
import com.android.timezone.tzids.proto.TzIdsProto;
import com.google.protobuf.TextFormat;
import com.ibm.icu.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TzLookupGeneratorTest {

    private static final String INVALID_TIME_ZONE_ID = "NOT_A_VALID_ID";
    private static final String TZDB_VERSION = TimeZone.getTZDataVersion();

    private Path tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("TzLookupGeneratorTest");
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteDir(tempDir);
    }

    @Test
    public void invalidCountryZonesFile() throws Exception {
        String countryZonesFile = createFile(tempDir, "THIS IS NOT A VALID FILE");
        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);
        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    @Test
    public void invalidRulesVersion() throws Exception {
        CountryZonesFile.Country validGb = createValidCountryGb();

        // The IANA version won't match ICU's IANA version so we should see a failure.
        CountryZonesFile.CountryZones badIanaVersionCountryZones =
                createValidCountryZones(validGb).toBuilder().setIanaVersion("2001a").build();
        String countryZonesFile = createCountryZonesFile(badIanaVersionCountryZones);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void countryWithNoTimeZoneMappings() throws Exception {
        // No zones found!
        CountryZonesFile.Country gbWithoutZones =
                createValidCountryGb().toBuilder().clearTimeZoneMappings().build();
        CountryZonesFile.CountryZones countryZones = createValidCountryZones(gbWithoutZones);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(createValidZoneTabEntriesGb());

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void countryWithDuplicateTimeZoneMappings() throws Exception {
        // Duplicate zones found!
        CountryZonesFile.Country validCountryGb = createValidCountryGb();
        CountryZonesFile.Country gbWithDuplicateZones =
                validCountryGb.toBuilder()
                        .setDefaultTimeZoneId(validCountryGb.getTimeZoneMappings(0).getId())
                        .addAllTimeZoneMappings(validCountryGb.getTimeZoneMappingsList())
                        .build();
        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(gbWithDuplicateZones);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(createValidZoneTabEntriesGb());

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void badDefaultId() throws Exception {
        // Set an invalid default.
        CountryZonesFile.Country validGb =
                createValidCountryGb().toBuilder()
                        .setDefaultTimeZoneId("NOT_A_TIMEZONE_ID")
                        .build();
        CountryZonesFile.CountryZones gbCountryZones = createValidCountryZones(validGb);
        String countryZonesFile = createCountryZonesFile(gbCountryZones);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void shouldFail_whenExplicitDefaultIdBelongsToOtherCountry() throws Exception {
        // Set a valid default, but to one that isn't referenced by "gb".
        CountryZonesFile.Country validGb = createValidCountryGb().toBuilder()
                .setDefaultTimeZoneId(createValidCountryFr().getTimeZoneMappings(0).getId())
                .build();
        CountryZonesFile.CountryZones gbCountryZones = createValidCountryZones(validGb);
        String countryZonesFile = createCountryZonesFile(gbCountryZones);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void calculatedDefaultZone() throws Exception {
        // Ensure there's no explicit default for "gb" and there's one zone.
        CountryZonesFile.Country validCountryGb = createValidCountryGb();
        assertEquals(1, validCountryGb.getTimeZoneMappingsCount());

        String gbTimeZoneId = validCountryGb.getTimeZoneMappings(0).getId();
        CountryZonesFile.Country gbWithoutDefault = validCountryGb.toBuilder()
                .clearDefaultTimeZoneId().build();
        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();

        OutputData outputData =
                generateOutputData(gbWithoutDefault, gbZoneTabEntries);

        // Check gb's time zone was defaulted.
        assertContains(outputData.tzLookupXml, "code=\"gb\" default=\"" + gbTimeZoneId + "\"");
    }

    @Test
    public void explicitDefaultZone() throws Exception {
        // Ensure there's an explicit default for "gb" and there's one zone.
        CountryZonesFile.Country validCountryGb = createValidCountryGb();
        String gbTimeZoneId = validCountryGb.getTimeZoneMappings(0).getId();
        CountryZonesFile.Country gbWithExplicitDefaultTimeZone =
                validCountryGb.toBuilder()
                        .setDefaultTimeZoneId(gbTimeZoneId)
                        .build();
        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();

        OutputData outputData = generateOutputData(
                gbWithExplicitDefaultTimeZone, gbZoneTabEntries);

        // Check gb's time zone was defaulted.
        assertContains(outputData.tzLookupXml, "code=\"gb\" default=\"" + gbTimeZoneId + "\"");
    }

    @Test
    public void countryZonesContainsNonLowercaseIsoCode() throws Exception {
        CountryZonesFile.Country validCountry = createValidCountryGb();
        CountryZonesFile.Country invalidCountry =
                createValidCountryGb().toBuilder().setIsoCode("Gb").build();

        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(validCountry, invalidCountry);
        String countryZonesFile = createCountryZonesFile(countryZones);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void countryZonesContainsDuplicate() throws Exception {
        CountryZonesFile.Country validGb = createValidCountryGb();

        // The file contains "gb" twice.
        CountryZonesFile.CountryZones duplicateGbData =
                createValidCountryZones(validGb, validGb);
        String countryZonesFile = createCountryZonesFile(duplicateGbData);

        List<ZoneTabFile.CountryEntry> gbZoneTabEntries = createValidZoneTabEntriesGb();
        String zoneTabFile = createZoneTabFile(gbZoneTabEntries);

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void shouldNotFail_whenCountryZonesAndZoneTabCountryMismatch() throws Exception {
        // The two input files contain non-identical country ISO codes.
        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(createValidCountryGb(), createValidCountryFr());
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile =
                createZoneTabFile(createValidZoneTabEntriesFr(), createValidZoneTabEntriesUs());
        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertTrue(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    @Test
    public void shouldNotFail_whenCountryZonesAndZoneTabDisagreeOnZones() throws Exception {
        CountryZonesFile.Country gbWithWrongZones = createValidCountryGb();
        CountryZonesFile.CountryZones countryZones = createValidCountryZones(gbWithWrongZones);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(
                List.of(new ZoneTabFile.CountryEntry("GB", "Europe/Paris")));
        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);

        assertTrue(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    @Test
    public void duplicateEntriesInZoneTab() throws Exception {
        CountryZonesFile.Country validGbCountry = createValidCountryGb();
        CountryZonesFile.CountryZones countryZones = createValidCountryZones(validGbCountry);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFileWithDupes = createZoneTabFile(
                createValidZoneTabEntriesGb(), createValidZoneTabEntriesGb());

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFileWithDupes, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void incorrectOffset() throws Exception {
        CountryZonesFile.Country validGbCountry = createValidCountryGb();
        CountryZonesFile.Country.Builder gbWithWrongOffsetBuilder = validGbCountry.toBuilder();
        gbWithWrongOffsetBuilder.getTimeZoneMappingsBuilder(0).setUtcOffset("20:00");
        CountryZonesFile.Country gbWithWrongOffset = gbWithWrongOffsetBuilder.build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(gbWithWrongOffset);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(createValidZoneTabEntriesGb());

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void badTimeZoneMappingId() throws Exception {
        CountryZonesFile.Country validGbCountry = createValidCountryGb();
        CountryZonesFile.Country.Builder gbWithBadIdBuilder = validGbCountry.toBuilder();
        gbWithBadIdBuilder.setDefaultTimeZoneId(validGbCountry.getTimeZoneMappings(0).getId())
                .addTimeZoneMappingsBuilder().setId(INVALID_TIME_ZONE_ID).setUtcOffset("00:00");
        CountryZonesFile.Country gbWithBadId = gbWithBadIdBuilder.build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(gbWithBadId);
        String countryZonesFile = createCountryZonesFile(countryZones);

        List<ZoneTabFile.CountryEntry> zoneTabEntriesWithBadId =
                new ArrayList<>(createValidZoneTabEntriesGb());
        zoneTabEntriesWithBadId.add(new ZoneTabFile.CountryEntry("GB", INVALID_TIME_ZONE_ID));
        String zoneTabFile = createZoneTabFile(zoneTabEntriesWithBadId);

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void usingOldIdsInCountryTextIsValid() throws Exception {
        // This simulates a case where America/Godthab has been superseded by America/Nuuk in IANA
        // data, but Android wants to continue using America/Godthab. This is signaled as deliberate
        // through the use of the alternativeIds in countryzones.txt.
        String countryZonesWithOldIdText =
                "isoCode:\"gl\"\n"
                + "defaultTimeZoneId:\"America/Godthab\"\n"
                + "timeZoneMappings:<\n"
                + "  utcOffset:\"0:00\"\n"
                + "  id:\"America/Danmarkshavn\"\n"
                + "  priority:1\n"
                + ">\n"
                + "\n"
                + "timeZoneMappings:<\n"
                + "  utcOffset:\"-2:00\"\n"
                + "  id:\"America/Godthab\"\n"
                + "  alternativeIds: \"America/Nuuk\"\n"
                + "  priority: 17\n"
                + ">\n"
                + "\n"
                + "timeZoneMappings:<\n"
                + "  utcOffset:\"-2:00\"\n"
                + "  id:\"America/Scoresbysund\"\n"
                + "  priority:1\n"
                + ">\n"
                + "\n"
                + "timeZoneMappings:<\n"
                + "  utcOffset:\"-4:00\"\n"
                + "  id:\"America/Thule\"\n"
                + "  priority:1\n"
                + ">\n";
        Country country = parseCountry(countryZonesWithOldIdText);
        List<ZoneTabFile.CountryEntry> zoneTabWithNewIds = Arrays.asList(
                new ZoneTabFile.CountryEntry("GL", "America/Nuuk"),
                new ZoneTabFile.CountryEntry("GL", "America/Danmarkshavn"),
                new ZoneTabFile.CountryEntry("GL", "America/Scoresbysund"),
                new ZoneTabFile.CountryEntry("GL", "America/Thule")
        );

        OutputData outputData = generateOutputData(country, zoneTabWithNewIds);

        String expectedTzLookupOutput = "<id>America/Danmarkshavn</id>\n"
                + "<id notafter=\"1711846800000\" repl=\"America/Godthab\">"
                + "America/Scoresbysund</id>\n"
                + "<id alts=\"America/Nuuk\">America/Godthab</id>\n"
                + "<id>America/Thule</id>\n";
        String[] expectedTzLookupXmlLines = expectedTzLookupOutput.split("\\n");
        for (String expectedTzLookupXmlLine : expectedTzLookupXmlLines) {
            assertContains(outputData.tzLookupXml, expectedTzLookupXmlLine);
        }

        TzIdsProto.TimeZoneIds.Builder tzIdsBuilder = TzIdsProto.TimeZoneIds
                .newBuilder()
                .setIanaVersion(TZDB_VERSION);

        TzIdsProto.CountryMapping.Builder b = TzIdsProto.CountryMapping.newBuilder()
                .setIsoCode("gl")
                .addTimeZoneIds("America/Danmarkshavn")
                .addTimeZoneIds("America/Godthab")
                .addTimeZoneIds("America/Thule");

        // Because Android lists America/Nuuk in alternativeIds in countryzones.txt, the link will
        // be reversed from the usual.
        addLink(b, "America/Nuuk" /* alternativeId */, "America/Godthab" /* preferredId */);
        addReplacement(b, 1711846800000L, "America/Godthab", "America/Scoresbysund");

        tzIdsBuilder.addCountryMappings(b);
        assertEquals(tzIdsBuilder.build(), outputData.timeZoneIds);
    }

    @Test
    public void alternativeIds_shouldBeUsedToFillAltsSection() throws Exception {
        Country country = Country.newBuilder()
                .setIsoCode("gb")
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setId("Europe/London")
                        .setUtcOffset("0:00")
                        .addAlternativeIds("Europe/Belfast")
                        .addAlternativeIds("GB")
                        .addAlternativeIds("GB-Eire"))
                .build();

        List<ZoneTabFile.CountryEntry> zoneTabEntries = List.of(
                new ZoneTabFile.CountryEntry("GB", "Europe/London"));

        OutputData outputData = generateOutputData(country, zoneTabEntries);

        String expectedTzlookupOutput = "<country code=\"gb\" default=\"Europe/London\" "
                + "everutc=\"y\">\n"
                + "<id alts=\"Europe/Belfast,GB,GB-Eire\">Europe/London</id>\n"
                + "</country>";

        assertContains(outputData.tzLookupXml, expectedTzlookupOutput.split("\n"));

        TzIdsProto.TimeZoneIds.Builder tzIdsBuilder = TzIdsProto.TimeZoneIds
                .newBuilder()
                .setIanaVersion(TZDB_VERSION);

        TzIdsProto.CountryMapping.Builder b = TzIdsProto.CountryMapping.newBuilder()
                .setIsoCode("gb")
                .addTimeZoneIds("Europe/London");

        addLink(b, "Europe/Belfast" /* alternativeId */, "Europe/London" /* preferredId */);
        addLink(b, "GB" /* alternativeId */, "Europe/London" /* preferredId */);
        addLink(b, "GB-Eire" /* alternativeId */, "Europe/London" /* preferredId */);

        tzIdsBuilder.addCountryMappings(b);
        assertEquals(tzIdsBuilder.build(), outputData.timeZoneIds);
    }

    private static void addLink(TzIdsProto.CountryMapping.Builder builder, String alternativeId,
            String preferredId) {
        TzIdsProto.TimeZoneLink link =
                TzIdsProto.TimeZoneLink.newBuilder()
                        .setAlternativeId(alternativeId)
                        .setPreferredId(preferredId)
                        .build();
        builder.addTimeZoneLinks(link);
    }

    @Test
    public void everUtc_true() throws Exception {
        CountryZonesFile.Country validCountryGb = createValidCountryGb();
        OutputData outputData = generateOutputData(
                validCountryGb, createValidZoneTabEntriesGb());

        // Check gb's entry contains everutc="y".
        assertContains(outputData.tzLookupXml, "everutc=\"y\"");
    }

    @Test
    public void everUtc_false() throws Exception {
        CountryZonesFile.Country validCountryFr = createValidCountryFr();
        OutputData outputData = generateOutputData(
                validCountryFr, createValidZoneTabEntriesFr());

        // Check fr's entry contains everutc="n".
        assertContains(outputData.tzLookupXml, "everutc=\"n\"");
    }

    @Test
    public void shownInPicker_false() throws Exception {
        CountryZonesFile.Country countryPrototype = createValidCountryFr();

        CountryZonesFile.TimeZoneMapping.Builder timeZoneMappingBuilder =
                countryPrototype.getTimeZoneMappings(0).toBuilder();
        timeZoneMappingBuilder.setShownInPicker(false);

        CountryZonesFile.Country.Builder countryBuilder = countryPrototype.toBuilder();
        countryBuilder.setTimeZoneMappings(0, timeZoneMappingBuilder);
        CountryZonesFile.Country country = countryBuilder.build();

        OutputData outputData = generateOutputData(
                country, createValidZoneTabEntriesFr());

        assertContains(outputData.tzLookupXml, "picker=\"n\"");
    }

    @Test
    public void shownInPicker_true() throws Exception {
        CountryZonesFile.Country countryPrototype = createValidCountryFr();

        CountryZonesFile.TimeZoneMapping.Builder timeZoneMappingBuilder =
                countryPrototype.getTimeZoneMappings(0).toBuilder();
        timeZoneMappingBuilder.setShownInPicker(true);

        CountryZonesFile.Country.Builder countryBuilder = countryPrototype.toBuilder();
        countryBuilder.setTimeZoneMappings(0, timeZoneMappingBuilder);
        CountryZonesFile.Country country = countryBuilder.build();

        OutputData outputData = generateOutputData(
                country, createValidZoneTabEntriesFr());

        // We should not see anything "picker="y" is the implicit default.
        assertAbsent(outputData.tzLookupXml, "picker=");
    }

    @Test
    public void notAfter() throws Exception {
        CountryZonesFile.Country country = createValidCountryUs();
        List<ZoneTabFile.CountryEntry> zoneTabEntries = createValidZoneTabEntriesUs();
        OutputData outputData = generateOutputData(country, zoneTabEntries);
        String expectedTzLookupOutput =
                "<id>America/New_York</id>\n"
                + "<id notafter=\"167814000000\" repl=\"America/New_York\">America/Detroit</id>\n"
                + "<id notafter=\"152089200000\" repl=\"America/New_York\">America/Kentucky/Louisville</id>\n"
                + "<id notafter=\"972802800000\" repl=\"America/New_York\">America/Kentucky/Monticello</id>\n"
                + "<id notafter=\"1130652000000\" repl=\"America/New_York\">America/Indiana/Indianapolis</id>\n"
                + "<id notafter=\"1194159600000\" repl=\"America/New_York\">America/Indiana/Vincennes</id>\n"
                + "<id notafter=\"1173600000000\" repl=\"America/New_York\">America/Indiana/Winamac</id>\n"
                + "<id notafter=\"183535200000\" repl=\"America/Indiana/Indianapolis\">America/Indiana/Marengo</id>\n"
                + "<id notafter=\"247042800000\" repl=\"America/Indiana/Vincennes\">America/Indiana/Petersburg</id>\n"
                + "<id notafter=\"89186400000\" repl=\"America/Indiana/Indianapolis\">America/Indiana/Vevay</id>\n"
                + "<id>America/Chicago</id>\n"
                + "<id notafter=\"1143964800000\" repl=\"America/Chicago\">America/Indiana/Tell_City</id>\n"
                + "<id notafter=\"688546800000\" repl=\"America/Indiana/Tell_City\">America/Indiana/Knox</id>\n"
                + "<id notafter=\"104918400000\" repl=\"America/Chicago\">America/Menominee</id>\n"
                + "<id notafter=\"720000000000\" repl=\"America/Chicago\">America/North_Dakota/Center</id>\n"
                + "<id notafter=\"1067155200000\" repl=\"America/Chicago\">America/North_Dakota/New_Salem</id>\n"
                + "<id notafter=\"1289116800000\" repl=\"America/Chicago\">America/North_Dakota/Beulah</id>\n"
                + "<id>America/Denver</id>\n"
                + "<id notafter=\"129114000000\" repl=\"America/Denver\">America/Boise</id>\n"
                + "<id>America/Phoenix</id>\n"
                + "<id>America/Los_Angeles</id>\n"
                + "<id>America/Anchorage</id>\n"
                + "<id notafter=\"436359600000\" repl=\"America/Anchorage\">America/Juneau</id>\n"
                + "<id notafter=\"436356000000\" repl=\"America/Juneau\">America/Yakutat</id>\n"
                + "<id notafter=\"436363200000\" repl=\"America/Anchorage\">America/Nome</id>\n"
                + "<id notafter=\"1547978400000\" repl=\"America/Anchorage\">America/Metlakatla</id>\n"
                + "<id notafter=\"341402400000\" repl=\"America/Juneau\">America/Sitka</id>\n"
                + "<id>Pacific/Honolulu</id>\n"
                + "<id>America/Adak</id>\n";
        String[] expectedTzLookupXmlLines = expectedTzLookupOutput.split("\\n");
        for (String expectedTzLookupXmlLine : expectedTzLookupXmlLines) {
            assertContains(outputData.tzLookupXml, expectedTzLookupXmlLine);
        }
        
        TzIdsProto.TimeZoneIds.Builder tzIdsBuilder = TzIdsProto.TimeZoneIds
                .newBuilder()
                .setIanaVersion(TZDB_VERSION);

        TzIdsProto.CountryMapping.Builder b = TzIdsProto.CountryMapping.newBuilder()
                .setIsoCode("us")
                .addTimeZoneIds("America/New_York")
                .addTimeZoneIds("America/Chicago")
                .addTimeZoneIds("America/Denver")
                .addTimeZoneIds("America/Phoenix")
                .addTimeZoneIds("America/Los_Angeles")
                .addTimeZoneIds("America/Anchorage")
                .addTimeZoneIds("Pacific/Honolulu")
                .addTimeZoneIds("America/Adak");

        addReplacement(b, 167814000000L, "America/New_York", "America/Detroit");
        addReplacement(b, 152089200000L, "America/New_York", "America/Kentucky/Louisville");
        addReplacement(b, 972802800000L, "America/New_York", "America/Kentucky/Monticello");
        addReplacement(b, 1130652000000L, "America/New_York", "America/Indiana/Indianapolis");
        addReplacement(b, 1194159600000L, "America/New_York", "America/Indiana/Vincennes");
        addReplacement(b, 1173600000000L, "America/New_York", "America/Indiana/Winamac");
        addReplacement(b, 183535200000L, "America/Indiana/Indianapolis", "America/Indiana/Marengo");
        addReplacement(b, 247042800000L, "America/Indiana/Vincennes", "America/Indiana/Petersburg");
        addReplacement(b, 89186400000L, "America/Indiana/Indianapolis", "America/Indiana/Vevay");
        addReplacement(b, 1143964800000L, "America/Chicago", "America/Indiana/Tell_City");
        addReplacement(b, 688546800000L, "America/Indiana/Tell_City", "America/Indiana/Knox");
        addReplacement(b, 104918400000L, "America/Chicago", "America/Menominee");
        addReplacement(b, 720000000000L, "America/Chicago", "America/North_Dakota/Center");
        addReplacement(b, 1067155200000L, "America/Chicago", "America/North_Dakota/New_Salem");
        addReplacement(b, 1289116800000L, "America/Chicago", "America/North_Dakota/Beulah");
        addReplacement(b, 129114000000L, "America/Denver", "America/Boise");
        addReplacement(b, 436359600000L, "America/Anchorage", "America/Juneau");
        addReplacement(b, 436356000000L, "America/Juneau", "America/Yakutat");
        addReplacement(b, 436363200000L, "America/Anchorage", "America/Nome");
        addReplacement(b, 1547978400000L, "America/Anchorage", "America/Metlakatla");
        addReplacement(b, 341402400000L, "America/Juneau", "America/Sitka");

        tzIdsBuilder.addCountryMappings(b);
        assertEquals(tzIdsBuilder.build(), outputData.timeZoneIds);
    }

    @Test
    public void eachAlternativeTimeZoneShouldBeAttributedToSingleCountryOnly() throws Exception {
        CountryZonesFile.Country gbCountry =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("gb")
                        .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("00:00")
                                .setId("Europe/London")
                                .addAlternativeIds("Europe/Belfast"))
                        .build();

        CountryZonesFile.Country franceCountry =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("fr")
                        .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("1:00")
                                .setId("Europe/Paris")
                                .addAlternativeIds("Europe/Belfast"))
                        .build();


        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(gbCountry, franceCountry);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile();

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void idInMappingsShouldBeAttributedToSingleCountryOnly() throws Exception {
        CountryZonesFile.Country gbCountry =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("gb")
                        .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("00:00")
                                .setId("Europe/London"))
                        .build();

        CountryZonesFile.Country franceCountry =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("fr")
                        .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("1:00")
                                .setId("Europe/London"))
                        .build();


        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(gbCountry, franceCountry);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile();
        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);

        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void idInMappingsShouldNotAppearInAlternativeNameIds() throws Exception {
        CountryZonesFile.Country gbCountry =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("gb")
                        .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("00:00")
                                .setId("Europe/London"))
                        .build();

        CountryZonesFile.Country franceCountry =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("fr")
                        .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("1:00")
                                .setId("Europe/Paris")
                                .addAlternativeIds("Europe/London"))
                        .build();


        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(gbCountry, franceCountry);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile();
        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);

        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        assertFileMissing(tzLookupFile);
        assertFileMissing(tzIdsFile);
    }

    @Test
    public void shouldFail_whenIdAppearsInMultipleTimeZoneMappingWithinACountry() throws Exception {
        CountryZonesFile.Country country =
                CountryZonesFile.Country.newBuilder()
                .setIsoCode("ar")
                .addTimeZoneMappings(
                        CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("-3:00")
                                .setId("America/Argentina/Buenos_Aires")
                                .addAlternativeIds("America/Buenos_Aires"))
                .addTimeZoneMappings(
                        CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("-3:00")
                                .setId("America/Argentina/Cordoba")
                                .addAlternativeIds("America/Cordoba")
                                .addAlternativeIds("America/Buenos_Aires"))
                .build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(country);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile();
        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);

        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    @Test
    public void shouldFail_whenIdIsMentionedAmongAlternativeIds() throws Exception {
        CountryZonesFile.Country country =
                CountryZonesFile.Country.newBuilder()
                .setIsoCode("gb")
                .addTimeZoneMappings(
                        CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("00:00")
                                .setId("Europe/London")
                                .addAlternativeIds("Europe/London"))
                .build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(country);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile();
        String tzlookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzlookupFile, tzIdsFile);

        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    @Test
    public void shouldFail_whenAlternativeIdsListContainsDuplicates() throws Exception {
        CountryZonesFile.Country country =
                CountryZonesFile.Country.newBuilder()
                .setIsoCode("gb")
                .addTimeZoneMappings(
                        CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("00:00")
                                .setId("Europe/London")
                                .addAlternativeIds("Europe/Belfast")
                                .addAlternativeIds("GB")
                                .addAlternativeIds("Europe/Belfast"))
                .build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(country);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile();
        String tzlookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzlookupFile, tzIdsFile);

        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    @Test
    public void shouldFail_whenAlternativeIdsListContainsNonEquivalentTimeZoneId()
            throws Exception {
        CountryZonesFile.Country country =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("gb")
                        .addTimeZoneMappings(
                                CountryZonesFile.TimeZoneMapping.newBuilder()
                                        .setUtcOffset("00:00")
                                        .setId("Europe/London")
                                        .addAlternativeIds("Europe/Belfast")
                                        .addAlternativeIds("GB")
                                        .addAlternativeIds("Europe/Paris"))
                        .build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(country);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile();
        String tzlookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzlookupFile, tzIdsFile);

        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    @Test
    public void shouldFail_whenAnAliasComesBeforeOriginalTimeZone() throws Exception {
        // These time zones relationship is unlikely to change judging by the recent
        // TZDB updates.
        CountryZonesFile.Country country =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("de")
                        .setDefaultTimeZoneId("Europe/Berlin")
                        .addTimeZoneMappings(
                                CountryZonesFile.TimeZoneMapping.newBuilder()
                                        .setUtcOffset("01:00")
                                        .setId("Europe/Busingen"))
                        .addTimeZoneMappings(
                                CountryZonesFile.TimeZoneMapping.newBuilder()
                                        .setUtcOffset("01:00")
                                        .setId("Europe/Berlin"))
                        .build();

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(country);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(
                List.of(
                        new ZoneTabFile.CountryEntry("DE", "Europe/Berlin"),
                        new ZoneTabFile.CountryEntry("DE", "Europe/Busingen")));
        String tzlookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzlookupFile, tzIdsFile);

        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    @Test
    public void shouldFail_whenCountryzonesMissesTimeZone() throws Exception {
        CountryZonesFile.Country gbCountry =
                CountryZonesFile.Country.newBuilder()
                        .setIsoCode("gb")
                        .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                                .setUtcOffset("00:00")
                                .setId("Europe/London")
                                .addAlternativeIds("Europe/Belfast"))
                        .build();

        CountryZonesFile.CountryZones countryZones =
                createValidCountryZones(gbCountry);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile();

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(true /* validateAllIanaIdsAreMapped */));
    }

    private static void addReplacement(TzIdsProto.CountryMapping.Builder builder,
            long fromMillis, String replacementId, String replacedId) {
        TzIdsProto.TimeZoneReplacement replacement =
                TzIdsProto.TimeZoneReplacement.newBuilder()
                        .setReplacedId(replacedId)
                        .setReplacementId(replacementId)
                        .setFromMillis(fromMillis)
                        .build();
        builder.addTimeZoneReplacements(replacement);
    }

    static class OutputData {
        final String tzLookupXml;
        final TzIdsProto.TimeZoneIds timeZoneIds;

        OutputData(String tzLookupXml, TzIdsProto.TimeZoneIds timeZoneIds) {
            this.tzLookupXml = tzLookupXml;
            this.timeZoneIds = timeZoneIds;
        }
    }

    private OutputData generateOutputData(CountryZonesFile.Country country,
            List<ZoneTabFile.CountryEntry> zoneTabEntries) throws Exception {

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(country);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(zoneTabEntries);

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertTrue(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));

        Path tzLookupFilePath = checkFileExists(tzLookupFile);
        String tzLookupXml = readFileToString(tzLookupFilePath);

        Path tzIdsFilePath = checkFileExists(tzIdsFile);
        String timeZoneIdsText = readFileToString(tzIdsFilePath);
        TzIdsProto.TimeZoneIds.Builder timeZoneIdsBuilder =
                TzIdsProto.TimeZoneIds.newBuilder();
        TextFormat.merge(timeZoneIdsText, timeZoneIdsBuilder);

        return new OutputData(tzLookupXml, timeZoneIdsBuilder.build());
    }

    private void generateTzLookupXmlExpectFailure(CountryZonesFile.Country country,
            List<ZoneTabFile.CountryEntry> zoneTabEntries) throws Exception {

        CountryZonesFile.CountryZones countryZones = createValidCountryZones(country);
        String countryZonesFile = createCountryZonesFile(countryZones);

        String zoneTabFile = createZoneTabFile(zoneTabEntries);

        String tzLookupFile = createTempFileName("tzlookup");
        String tzIdsFile = createTempFileName("tzids");

        TzLookupGenerator tzLookupGenerator = new TzLookupGenerator(
                countryZonesFile, zoneTabFile, tzLookupFile, tzIdsFile);
        assertFalse(tzLookupGenerator.execute(false /* validateAllIanaIdsAreMapped */));
    }

    private static String readFileToString(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private String createZoneTabFile(List<ZoneTabFile.CountryEntry>... zoneTabEntriesLists)
            throws Exception {
        List<List<ZoneTabFile.CountryEntry>> entries = Arrays.asList(zoneTabEntriesLists);
        List<String> lines = entries.stream()
                .flatMap(List::stream)
                .map(country -> country.isoCode + "\tIgnored\t" + country.olsonId)
                .collect(Collectors.toList());
        return TestUtils.createFile(tempDir, lines.toArray(new String[0]));
    }

    private String createCountryZonesFile(CountryZonesFile.CountryZones countryZones) throws Exception {
        return TestUtils.createFile(tempDir, TextFormat.printToString(countryZones));
    }

    private static CountryZonesFile.CountryZones createValidCountryZones(
            CountryZonesFile.Country... countries) {
        CountryZonesFile.CountryZones.Builder builder =
                CountryZonesFile.CountryZones.newBuilder()
                        .setIanaVersion(TZDB_VERSION);
        for (CountryZonesFile.Country country : countries) {
            builder.addCountries(country);
        }
        return builder.build();
    }

    private static CountryZonesFile.Country createValidCountryGb() {
        return CountryZonesFile.Country.newBuilder()
                .setIsoCode("gb")
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("00:00")
                        .setId("Europe/London"))
                .build();
    }

    private static CountryZonesFile.Country createValidCountryUs() throws Exception {
        // This country demonstrates most interesting algorithm behavior. This is copied verbatim
        // from countryzones.txt.
        String usText =
                "  isoCode:\"us\"\n"
                + "  defaultTimeZoneId:\"America/New_York\"\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/New_York\"\n"
                + "    priority:10\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Detroit\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Kentucky/Louisville\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Kentucky/Monticello\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Indiana/Indianapolis\"\n"
                + "    priority:9\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Indiana/Vincennes\"\n"
                + "    priority:9\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Indiana/Winamac\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Indiana/Marengo\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Indiana/Petersburg\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-5:00\"\n"
                + "    id:\"America/Indiana/Vevay\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-6:00\"\n"
                + "    id:\"America/Chicago\"\n"
                + "    priority:10\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-6:00\"\n"
                + "    id:\"America/Indiana/Tell_City\"\n"
                + "    priority:9\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-6:00\"\n"
                + "    id:\"America/Indiana/Knox\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-6:00\"\n"
                + "    id:\"America/Menominee\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-6:00\"\n"
                + "    id:\"America/North_Dakota/Center\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-6:00\"\n"
                + "    id:\"America/North_Dakota/New_Salem\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-6:00\"\n"
                + "    id:\"America/North_Dakota/Beulah\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-7:00\"\n"
                + "    id:\"America/Denver\"\n"
                + "    priority:9\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-7:00\"\n"
                + "    id:\"America/Boise\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-7:00\"\n"
                + "    id:\"America/Phoenix\"\n"
                + "    priority:10\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-8:00\"\n"
                + "    id:\"America/Los_Angeles\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-9:00\"\n"
                + "    id:\"America/Anchorage\"\n"
                + "    priority:10\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-9:00\"\n"
                + "    id:\"America/Juneau\"\n"
                + "    priority:9\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-9:00\"\n"
                + "    id:\"America/Yakutat\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-9:00\"\n"
                + "    id:\"America/Nome\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-9:00\"\n"
                + "    id:\"America/Metlakatla\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-9:00\"\n"
                + "    id:\"America/Sitka\"\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-10:00\"\n"
                + "    id:\"Pacific/Honolulu\"\n"
                + "    priority:10\n"
                + "  >\n"
                + "  timeZoneMappings:<\n"
                + "    utcOffset:\"-10:00\"\n"
                + "    id:\"America/Adak\"\n"
                + "  >\n";
        return parseCountry(usText);
    }

    private static CountryZonesFile.Country createValidCountryFr() {
        return CountryZonesFile.Country.newBuilder()
                .setIsoCode("fr")
                .addTimeZoneMappings(CountryZonesFile.TimeZoneMapping.newBuilder()
                        .setUtcOffset("01:00")
                        .setId("Europe/Paris"))
                .build();
    }

    private static List<ZoneTabFile.CountryEntry> createValidZoneTabEntriesGb() {
        return Arrays.asList(new ZoneTabFile.CountryEntry("GB", "Europe/London"));
    }

    private static List<ZoneTabFile.CountryEntry> createValidZoneTabEntriesUs() {
        return Arrays.asList(
                new ZoneTabFile.CountryEntry("US", "America/New_York"),
                new ZoneTabFile.CountryEntry("US", "America/Detroit"),
                new ZoneTabFile.CountryEntry("US", "America/Kentucky/Louisville"),
                new ZoneTabFile.CountryEntry("US", "America/Kentucky/Monticello"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Indianapolis"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Vincennes"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Winamac"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Marengo"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Petersburg"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Vevay"),
                new ZoneTabFile.CountryEntry("US", "America/Chicago"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Tell_City"),
                new ZoneTabFile.CountryEntry("US", "America/Indiana/Knox"),
                new ZoneTabFile.CountryEntry("US", "America/Menominee"),
                new ZoneTabFile.CountryEntry("US", "America/North_Dakota/Center"),
                new ZoneTabFile.CountryEntry("US", "America/North_Dakota/New_Salem"),
                new ZoneTabFile.CountryEntry("US", "America/North_Dakota/Beulah"),
                new ZoneTabFile.CountryEntry("US", "America/Denver"),
                new ZoneTabFile.CountryEntry("US", "America/Boise"),
                new ZoneTabFile.CountryEntry("US", "America/Phoenix"),
                new ZoneTabFile.CountryEntry("US", "America/Los_Angeles"),
                new ZoneTabFile.CountryEntry("US", "America/Anchorage"),
                new ZoneTabFile.CountryEntry("US", "America/Juneau"),
                new ZoneTabFile.CountryEntry("US", "America/Sitka"),
                new ZoneTabFile.CountryEntry("US", "America/Metlakatla"),
                new ZoneTabFile.CountryEntry("US", "America/Yakutat"),
                new ZoneTabFile.CountryEntry("US", "America/Nome"),
                new ZoneTabFile.CountryEntry("US", "America/Adak"),
                new ZoneTabFile.CountryEntry("US", "Pacific/Honolulu"));
    }

    private static List<ZoneTabFile.CountryEntry> createValidZoneTabEntriesFr() {
        return Arrays.asList(new ZoneTabFile.CountryEntry("FR", "Europe/Paris"));
    }

    /** Returns a file name for a file that does not exist. */
    private String createTempFileName(String fileNamePrefix) throws IOException {
        Path tempFile = Files.createTempFile(tempDir, fileNamePrefix, null /* suffix */);
        Files.delete(tempFile);
        return tempFile.toString();
    }

    private static Country parseCountry(String text) throws Exception {
        Country.Builder builder = Country.newBuilder();
        TextFormat.getParser().merge(text, builder);
        return builder.build();
    }

    private static Path checkFileExists(String fileName) {
        Path filePath = Paths.get(fileName);
        assertTrue("File " + filePath + " unexpectedly missing", Files.exists(filePath));
        return filePath;
    }

    private static void assertFileMissing(String fileName) throws IOException {
        Path filePath = Paths.get(fileName);
        assertFalse("File " + filePath + " unexpectedly exists", Files.exists(filePath));
    }
}
