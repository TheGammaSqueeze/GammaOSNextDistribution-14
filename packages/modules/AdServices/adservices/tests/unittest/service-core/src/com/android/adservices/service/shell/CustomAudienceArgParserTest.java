/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.shell;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class CustomAudienceArgParserTest extends AdServicesUnitTestCase {

    private static final String CMD = "not-relevant-command not-relevant-sub-command";
    private static final String SUB_COMMAND = "not-relevant-sub-command";

    @Test
    public void testParse_happyPath_success() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        customAudienceArgParser.parse(CMD, SUB_COMMAND, "--hello", "world");

        expect.withMessage("`--hello world` argument parses")
                .that(customAudienceArgParser.getValue("hello"))
                .isEqualTo("world");
    }

    @Test
    public void testParse_multipleArguments_success() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        customAudienceArgParser.parse(
                CMD, SUB_COMMAND, "--hello", "world", "--another", "argument");

        expect.withMessage("`--hello world` argument parses")
                .that(customAudienceArgParser.getValue("hello"))
                .isEqualTo("world");
        expect.withMessage("`--another argument` argument parses")
                .that(customAudienceArgParser.getValue("another"))
                .isEqualTo("argument");
    }

    @Test
    public void testParse_multipleArgumentsOutOfOrder_success() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        // Same as above test, but with order swapped.
        customAudienceArgParser.parse(
                CMD, SUB_COMMAND, "--another", "argument", "--hello", "world");

        expect.withMessage("`--hello world` parses")
                .that(customAudienceArgParser.getValue("hello"))
                .isEqualTo("world");
        expect.withMessage("`--another argument` parses")
                .that(customAudienceArgParser.getValue("another"))
                .isEqualTo("argument");
    }

    @Test
    public void testParse_withRequired_success() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser("hello");

        customAudienceArgParser.parse(CMD, SUB_COMMAND, "--hello", "world");

        expect.withMessage("`--hello world` parses")
                .that(customAudienceArgParser.getValue("hello"))
                .isEqualTo("world");
    }

    @Test
    public void testParse_withUriArgument_success() {
        String uri = "http://google.com/about#abc123";

        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser("uri");

        customAudienceArgParser.parse(CMD, SUB_COMMAND, "--uri", uri);
        expect.withMessage("`--uri` parses")
                .that(customAudienceArgParser.getValue("uri"))
                .isEqualTo(uri);
    }

    @Test
    public void testParse_noExtraArguments_success() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        customAudienceArgParser.parse(CMD, SUB_COMMAND);
    }

    @Test
    public void testParse_duplicateArguments_throwsException() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        customAudienceArgParser.parse(
                                new String[] {
                                    CMD, SUB_COMMAND, "--hello", "world", "--hello", "world"
                                }));
    }

    @Test
    public void testParse_noArguments_throwsException() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        assertThrows(
                IllegalArgumentException.class,
                () -> customAudienceArgParser.parse(new String[] {}));
    }

    @Test
    public void testParse_emptyArgument_throwsException() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        assertThrows(
                IllegalArgumentException.class,
                () -> customAudienceArgParser.parse(new String[] {CMD, SUB_COMMAND, ""}));
    }

    @Test
    public void testParse_emptyKey_throwsException() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        assertThrows(
                IllegalArgumentException.class,
                () -> customAudienceArgParser.parse(new String[] {CMD, SUB_COMMAND, "--world"}));
    }

    @Test
    public void testParse_emptyValue_throwsException() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser();

        assertThrows(
                IllegalArgumentException.class,
                () -> customAudienceArgParser.parse(new String[] {CMD, SUB_COMMAND, "--hello"}));
    }

    @Test
    public void testParse_missingRequired_throwsException() {
        CustomAudienceArgParser customAudienceArgParser = new CustomAudienceArgParser("hello");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        customAudienceArgParser.parse(
                                new String[] {CMD, SUB_COMMAND, "--something", "else"}));
    }
}
