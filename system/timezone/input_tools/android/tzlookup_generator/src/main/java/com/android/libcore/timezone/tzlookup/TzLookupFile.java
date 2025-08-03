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

import com.android.timezone.tzids.proto.TzIdsProto;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * A class that knows about the structure of the tzlookup.xml file.
 */
final class TzLookupFile {

    // <timezones ianaversion="2017b">
    private static final String TIMEZONES_ELEMENT = "timezones";
    private static final String IANA_VERSION_ATTRIBUTE = "ianaversion";

    // <countryzones>
    private static final String COUNTRY_ZONES_ELEMENT = "countryzones";

    // <country code="iso_code" default="olson_id" [defaultBoost="n|y"] everutc="n|y">
    private static final String COUNTRY_ELEMENT = "country";
    private static final String COUNTRY_CODE_ATTRIBUTE = "code";
    private static final String DEFAULT_ATTRIBUTE = "default";
    private static final String DEFAULT_BOOST_ATTRIBUTE = "defaultBoost";
    private static final String EVER_USES_UTC_ATTRIBUTE = "everutc";

    // <id [picker="n|y"]>
    private static final String ZONE_ID_ELEMENT = "id";
    // Default when unspecified is "y" / true.
    private static final String ZONE_SHOW_IN_PICKER_ATTRIBUTE = "picker";
    // The time when the zone stops being distinct from another of the country's zones (inclusive).
    private static final String ZONE_NOT_USED_AFTER_ATTRIBUTE = "notafter";
    // The zone ID used in place of this one starting from the "notafter" time (when present).
    private static final String ZONE_NOT_USED_REPLACEMENT_ID_ATTRIBUTE = "repl";
    // Other IDs associated with this ID, e.g. legacy or more modern alternatives.
    private static final String ZONE_ALTERNATIVE_IDS_ATTRIBUTE = "alts";


    // Short encodings for boolean attributes.
    private static final String ATTRIBUTE_FALSE = "n";
    private static final String ATTRIBUTE_TRUE = "y";

    static void write(TimeZones timeZones, String outputFile)
            throws XMLStreamException, IOException {
        /*
         * The required XML structure is:
         * <timezones ianaversion="2017b">
         *  <countryzones>
         *   <country code="us" default="America/New_York" everutc="n">
         *    <id>America/New_York"</id>
         *    <id notafter="1234" repl="America/New_York" alts="US/Michigan">America/Detroit"</id>
         *    ...
         *    <id picker="n">America/Los_Angeles</id>
         *   </country>
         *   <country code="gb" default="Europe/London" defaultBoost="y" everutc="y">
         *    <id alts="Europe/Belfast,GB,GB-Eire">Europe/London</id>
         *   </country>
         *  </countryzones>
         * </timezones>
         */

        StringWriter writer = new StringWriter();
        writeRaw(timeZones, writer);
        String rawXml = writer.getBuffer().toString();

        TransformerFactory factory = TransformerFactory.newInstance();
        try (Writer fileWriter = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {

            // Transform the XML with the identity transform but with indenting
            // so it's more human-readable.
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
            transformer.transform(
                    new StreamSource(new StringReader(rawXml)), new StreamResult(fileWriter));
        } catch (TransformerException e) {
            throw new XMLStreamException(e);
        }
    }

    private static void writeRaw(TimeZones timeZones, Writer fileWriter)
            throws XMLStreamException {
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(fileWriter);
        xmlWriter.writeStartDocument();
        xmlWriter.writeComment("\n\n **** Autogenerated file - DO NOT EDIT ****\n\n");
        TimeZones.writeXml(timeZones, xmlWriter);
        xmlWriter.writeEndDocument();
    }

    static class TimeZones {

        private final String ianaVersion;
        private CountryZones countryZones;

        TimeZones(String ianaVersion) {
            this.ianaVersion = ianaVersion;
        }

        void setCountryZones(CountryZones countryZones) {
            this.countryZones = countryZones;
        }

        static void writeXml(TimeZones timeZones, XMLStreamWriter writer)
                throws XMLStreamException {
            writer.writeStartElement(TIMEZONES_ELEMENT);
            writer.writeAttribute(IANA_VERSION_ATTRIBUTE, timeZones.ianaVersion);
            CountryZones.writeXml(timeZones.countryZones, writer);
            writer.writeEndElement();
        }
    }

    static class CountryZones {

        private final List<Country> countries = new ArrayList<>();

        CountryZones() {
        }

        static void writeXml(CountryZones countryZones, XMLStreamWriter writer)
                throws XMLStreamException {
            writer.writeStartElement(COUNTRY_ZONES_ELEMENT);
            for (Country country : countryZones.countries) {
                Country.writeXml(country, writer);
            }
            writer.writeEndElement();
        }

        void addCountry(Country country) {
            countries.add(country);
        }
    }

    static class Country {

        private final String isoCode;
        private final String defaultTimeZoneId;
        private final boolean defaultTimeZoneBoost;
        private final boolean everUsesUtc;
        private final List<TimeZoneMapping> timeZoneMappings = new ArrayList<>();
        private final Set<String> alreadyDefinedTimeZoneIds = new HashSet<>();

        Country(String isoCode, String defaultTimeZoneId, boolean defaultTimeZoneBoost,
                boolean everUsesUtc) {
            this.defaultTimeZoneId = defaultTimeZoneId;
            this.isoCode = isoCode;
            this.defaultTimeZoneBoost = defaultTimeZoneBoost;
            this.everUsesUtc = everUsesUtc;
        }

        void addTimeZoneMapping(TimeZoneMapping timeZoneMapping) {
            String notUsedReplacementId = timeZoneMapping.notAfterReplacementId;

            // It makes sense to have the highest-priority zones first for time zone detection,
            // which uses the first valid time zone that matches local offset information.
            // "Replaced by" is a strong signal that one zone is subordinate to another.
            // Therefore, zones should usually come after the zones that replaced them.
            if (notUsedReplacementId != null
                    && !alreadyDefinedTimeZoneIds.contains(notUsedReplacementId)) {
                String errorMessage = ("Wrong mapping order for %s: %s is replaced by %s,"
                        + " but comes before it.")
                        .formatted(
                                isoCode,
                                timeZoneMapping.olsonId,
                                notUsedReplacementId);
                throw new IllegalArgumentException(errorMessage);
            }

            alreadyDefinedTimeZoneIds.add(timeZoneMapping.olsonId);
            alreadyDefinedTimeZoneIds.addAll(timeZoneMapping.alternativeZoneIds);
            timeZoneMappings.add(timeZoneMapping);
        }

        static TzIdsProto.CountryMapping createCountryMappingProto(Country country) {
            TzIdsProto.CountryMapping.Builder countryMappingBuilder =
                    TzIdsProto.CountryMapping.newBuilder()
                            .setIsoCode(country.isoCode);
            for (TzLookupFile.TimeZoneMapping timeZoneMapping : country.timeZoneMappings) {
                String mappingTimeZoneId = timeZoneMapping.olsonId;
                String notUsedReplacementId = timeZoneMapping.notAfterReplacementId;
                Instant notUsedAfterInstant = timeZoneMapping.notUsedAfterInclusive;
                if (notUsedReplacementId == null && notUsedAfterInstant == null) {
                    countryMappingBuilder.addTimeZoneIds(mappingTimeZoneId);
                } else if (notUsedReplacementId != null && notUsedAfterInstant != null) {
                    String replacedTimeZoneId = mappingTimeZoneId;
                    TzIdsProto.TimeZoneReplacement timeZoneReplacement =
                            TzIdsProto.TimeZoneReplacement.newBuilder()
                                    .setReplacedId(replacedTimeZoneId)
                                    .setReplacementId(notUsedReplacementId)
                                    .setFromMillis(notUsedAfterInstant.toEpochMilli())
                                    .build();
                    countryMappingBuilder.addTimeZoneReplacements(timeZoneReplacement);
                } else {
                    throw new IllegalArgumentException(
                            "Malformed TimeZoneMapping:" + timeZoneMapping);
                }

                for (String alternativeZoneId : timeZoneMapping.alternativeZoneIds) {
                    // We could collapse links when notUsedReplacementId != null by using it instead
                    // of mappingTimeZoneId below, but that would potentially lose information.
                    // Leave it to the downstream components to collapse links if they want to.
                    TzIdsProto.TimeZoneLink timeZoneLink =
                            TzIdsProto.TimeZoneLink.newBuilder()
                                    .setPreferredId(mappingTimeZoneId)
                                    .setAlternativeId(alternativeZoneId)
                                    .build();
                    countryMappingBuilder.addTimeZoneLinks(timeZoneLink);
                }
            }
            return countryMappingBuilder.build();
        }

        static void writeXml(Country country, XMLStreamWriter writer)
                throws XMLStreamException {
            writer.writeStartElement(COUNTRY_ELEMENT);
            writer.writeAttribute(COUNTRY_CODE_ATTRIBUTE, country.isoCode);
            writer.writeAttribute(DEFAULT_ATTRIBUTE, country.defaultTimeZoneId);
            if (country.defaultTimeZoneBoost) {
                writer.writeAttribute(DEFAULT_BOOST_ATTRIBUTE,
                        encodeBooleanAttribute(country.defaultTimeZoneBoost));
            }
            writer.writeAttribute(EVER_USES_UTC_ATTRIBUTE, encodeBooleanAttribute(
                    country.everUsesUtc));
            for (TimeZoneMapping timeZoneMapping : country.timeZoneMappings) {
                TimeZoneMapping.writeXml(timeZoneMapping, writer);
            }
            writer.writeEndElement();
        }
    }

    private static String encodeBooleanAttribute(boolean value) {
        return value ? ATTRIBUTE_TRUE : ATTRIBUTE_FALSE;
    }

    private static String encodeLongAttribute(long epochMillis) {
        return Long.toString(epochMillis);
    }

    static class TimeZoneMapping {

        private final String olsonId;
        private final boolean showInPicker;
        private final Instant notUsedAfterInclusive;
        private final String notAfterReplacementId;
        private final List<String> alternativeZoneIds;

        TimeZoneMapping(String olsonId, boolean showInPicker, Instant notUsedAfterInclusive,
                String notAfterReplacementId, List<String> alternativeZoneIds) {
            this.olsonId = Objects.requireNonNull(olsonId);
            this.showInPicker = showInPicker;
            if ((notUsedAfterInclusive == null) != (notAfterReplacementId == null)) {
                throw new IllegalArgumentException(
                        "Supply both notUsedAfterInclusive and notAfterReplacementId or neither");
            }
            this.notUsedAfterInclusive = notUsedAfterInclusive;
            this.notAfterReplacementId = notAfterReplacementId;
            this.alternativeZoneIds = Objects.requireNonNull(alternativeZoneIds);
        }

        static void writeXml(TimeZoneMapping timeZoneMapping, XMLStreamWriter writer)
                throws XMLStreamException {
            writer.writeStartElement(ZONE_ID_ELEMENT);
            if (!timeZoneMapping.showInPicker) {
                writer.writeAttribute(ZONE_SHOW_IN_PICKER_ATTRIBUTE, encodeBooleanAttribute(false));
            }
            if (timeZoneMapping.notUsedAfterInclusive != null) {
                writer.writeAttribute(ZONE_NOT_USED_AFTER_ATTRIBUTE,
                        encodeLongAttribute(timeZoneMapping.notUsedAfterInclusive.toEpochMilli()));
                writer.writeAttribute(ZONE_NOT_USED_REPLACEMENT_ID_ATTRIBUTE,
                        timeZoneMapping.notAfterReplacementId);
            }
            if (!timeZoneMapping.alternativeZoneIds.isEmpty()) {
                String alternativeZoneIdsString =
                        String.join(",", timeZoneMapping.alternativeZoneIds);
                writer.writeAttribute(ZONE_ALTERNATIVE_IDS_ATTRIBUTE, alternativeZoneIdsString);
            }
            writer.writeCharacters(timeZoneMapping.olsonId);
            writer.writeEndElement();
        }
    }
}
