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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_ATTRIBUTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.common.AppManifestConfigCall.API_PROTECTED_SIGNALS;
import static com.android.adservices.service.common.AppManifestConfigCall.API_TOPICS;
import static com.android.adservices.service.common.AppManifestConfigCall.API_UNSPECIFIED;
import static com.android.adservices.service.common.AppManifestConfigCall.INVALID_API_TEMPLATE;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_ALL;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_DOES_NOT_EXIST;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_BY_APP;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_GENERIC_ERROR;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_UNSPECIFIED;
import static com.android.adservices.service.common.AppManifestConfigCall.isAllowed;
import static com.android.adservices.service.common.AppManifestConfigCall.resultToString;
import static com.android.adservices.service.common.AppManifestConfigCall.apiToString;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class AppManifestConfigCallTest extends AdServicesUnitTestCase {

    private static final String PKG_NAME = "pkg.I.am";
    private static final String PKG_NAME2 = "or.not";

    @Test
    public void testInvalidConstructor() {
        assertThrows(
                NullPointerException.class,
                () -> new AppManifestConfigCall(/* packageName= */ null, API_TOPICS));

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AppManifestConfigCall(PKG_NAME, API_UNSPECIFIED));
        expect.withMessage("e.getMessage()")
                .that(e)
                .hasMessageThat()
                .isEqualTo(String.format(INVALID_API_TEMPLATE, API_UNSPECIFIED));

        e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AppManifestConfigCall(PKG_NAME, -42));
        expect.withMessage("e.getMessage()")
                .that(e)
                .hasMessageThat()
                .isEqualTo(String.format(INVALID_API_TEMPLATE, -42));
    }

    @Test
    public void testValidConstructors() {
        AppManifestConfigCall topics = new AppManifestConfigCall(PKG_NAME, API_TOPICS);
        expect.withMessage("pkg on %s", topics).that(topics.packageName).isEqualTo(PKG_NAME);
        expect.withMessage("api on %s", topics).that(topics.api).isEqualTo(API_TOPICS);

        AppManifestConfigCall customAudience =
                new AppManifestConfigCall(PKG_NAME, API_CUSTOM_AUDIENCES);
        expect.withMessage("pkg on %s", customAudience)
                .that(customAudience.packageName)
                .isEqualTo(PKG_NAME);
        expect.withMessage("api on %s", customAudience)
                .that(customAudience.api)
                .isEqualTo(API_CUSTOM_AUDIENCES);

        AppManifestConfigCall attribution = new AppManifestConfigCall(PKG_NAME, API_ATTRIBUTION);
        expect.withMessage("pkg on %s", attribution)
                .that(attribution.packageName)
                .isEqualTo(PKG_NAME);
        expect.withMessage("api on %s", attribution)
                .that(attribution.api)
                .isEqualTo(API_ATTRIBUTION);
    }

    @Test
    public void testEqualsHashCode() {
        AppManifestConfigCall pkg1api1 = new AppManifestConfigCall(PKG_NAME, API_TOPICS);
        AppManifestConfigCall pkg1api2 = new AppManifestConfigCall(PKG_NAME, API_ATTRIBUTION);
        AppManifestConfigCall pkg2api1 = new AppManifestConfigCall(PKG_NAME2, API_TOPICS);
        AppManifestConfigCall pkg2api2 = new AppManifestConfigCall(PKG_NAME2, API_ATTRIBUTION);

        AppManifestConfigCall otherPkg1api1 = new AppManifestConfigCall(PKG_NAME, API_TOPICS);
        AppManifestConfigCall otherPkg1api2 = new AppManifestConfigCall(PKG_NAME, API_ATTRIBUTION);
        AppManifestConfigCall otherPkg2api1 = new AppManifestConfigCall(PKG_NAME2, API_TOPICS);
        AppManifestConfigCall otherPkg2api2 = new AppManifestConfigCall(PKG_NAME2, API_ATTRIBUTION);

        expectEquals(pkg1api1, pkg1api1);
        expectEquals(pkg1api1, otherPkg1api1);
        expectEquals(pkg1api2, pkg1api2);
        expectEquals(pkg1api2, otherPkg1api2);
        expectEquals(pkg2api1, pkg2api1);
        expectEquals(pkg2api1, otherPkg2api1);
        expectEquals(pkg2api2, pkg2api2);
        expectEquals(pkg2api2, otherPkg2api2);

        expectNotEquals(pkg1api1, pkg1api2);
        expectNotEquals(pkg1api1, pkg2api1);
        expectNotEquals(pkg1api1, pkg2api2);

        // Adds result
        otherPkg1api1.result = RESULT_ALLOWED_APP_ALLOWS_ALL;
        expectNotEquals(pkg1api1, otherPkg1api1);
        pkg1api1.result = RESULT_ALLOWED_APP_ALLOWS_ALL;
        expectEquals(pkg1api1, otherPkg1api1);
    }

    private void expectEquals(AppManifestConfigCall call1, AppManifestConfigCall call2) {
        expect.withMessage("equals()").that(call1).isEqualTo(call2);
        expect.withMessage("equals()").that(call2).isEqualTo(call1);
        expect.withMessage("hashcode(%s, %s)", call1, call2)
                .that(call1.hashCode())
                .isEqualTo(call2.hashCode());
    }

    private void expectNotEquals(AppManifestConfigCall call1, AppManifestConfigCall call2) {
        expect.withMessage("equals()").that(call1).isNotEqualTo(call2);
        expect.withMessage("equals()").that(call2).isNotEqualTo(call1);
        expect.withMessage("hashcode(%s, %s)", call1, call2)
                .that(call1.hashCode())
                .isNotEqualTo(call2.hashCode());
    }

    @Test
    public void testResultToString() {
        expect.withMessage("resultToString(%s)", RESULT_UNSPECIFIED)
                .that(resultToString(RESULT_UNSPECIFIED))
                .isEqualTo("UNSPECIFIED");
        expect.withMessage("resultToString(%s)", RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG)
                .that(resultToString(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG))
                .isEqualTo("ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG");
        expect.withMessage(
                        "resultToString(%s)",
                        RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION)
                .that(resultToString(RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .isEqualTo("ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION");
        expect.withMessage("resultToString(%s)", RESULT_ALLOWED_APP_ALLOWS_ALL)
                .that(resultToString(RESULT_ALLOWED_APP_ALLOWS_ALL))
                .isEqualTo("ALLOWED_APP_ALLOWS_ALL");
        expect.withMessage("resultToString(%s)", RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID)
                .that(resultToString(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID))
                .isEqualTo("ALLOWED_APP_ALLOWS_SPECIFIC_ID");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_APP_DOES_NOT_EXIST)
                .that(resultToString(RESULT_DISALLOWED_APP_DOES_NOT_EXIST))
                .isEqualTo("DISALLOWED_APP_DOES_NOT_EXIST");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR)
                .that(resultToString(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR))
                .isEqualTo("DISALLOWED_APP_CONFIG_PARSING_ERROR");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG)
                .that(resultToString(RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG))
                .isEqualTo("DISALLOWED_APP_DOES_NOT_HAVE_CONFIG");
        expect.withMessage(
                        "resultToString(%s)", RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION)
                .that(resultToString(RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .isEqualTo("DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_BY_APP)
                .that(resultToString(RESULT_DISALLOWED_BY_APP))
                .isEqualTo("DISALLOWED_BY_APP");
        expect.withMessage("resultToString(%s)", RESULT_DISALLOWED_GENERIC_ERROR)
                .that(resultToString(RESULT_DISALLOWED_GENERIC_ERROR))
                .isEqualTo("DISALLOWED_GENERIC_ERROR");
        expect.withMessage("resultToString(42)").that(resultToString(42)).isEqualTo("INVALID-42");
    }

    @Test
    public void testApiToString() {
        expect.withMessage("apiToString(%s)", API_UNSPECIFIED)
                .that(apiToString(API_UNSPECIFIED))
                .isEqualTo("UNSPECIFIED");
        expect.withMessage("apiToString(%s)", API_TOPICS)
                .that(apiToString(API_TOPICS))
                .isEqualTo("TOPICS");
        expect.withMessage("apiToString(%s)", API_CUSTOM_AUDIENCES)
                .that(apiToString(API_CUSTOM_AUDIENCES))
                .isEqualTo("CUSTOM_AUDIENCES");
        expect.withMessage("apiToString(%s)", API_ATTRIBUTION)
                .that(apiToString(API_ATTRIBUTION))
                .isEqualTo("ATTRIBUTION");
        expect.withMessage("apiToString(%s)", API_PROTECTED_SIGNALS)
                .that(apiToString(API_PROTECTED_SIGNALS))
                .isEqualTo("PROTECTED_SIGNALS");
        expect.withMessage("apiToString(%s)", API_AD_SELECTION)
                .that(apiToString(API_AD_SELECTION))
                .isEqualTo("AD_SELECTION");
        expect.withMessage("apiToString(42)").that(apiToString(42)).isEqualTo("INVALID-42");
    }

    @Test
    public void testIsAllowed() {
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_UNSPECIFIED))
                .that(isAllowed(RESULT_UNSPECIFIED))
                .isFalse();
        expect.withMessage(
                        "isAllowed(%s)",
                        resultToString(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG))
                .that(isAllowed(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG))
                .isTrue();
        expect.withMessage(
                        "isAllowed(%s)",
                        resultToString(
                                RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .that(isAllowed(RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .isTrue();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_ALLOWED_APP_ALLOWS_ALL))
                .that(isAllowed(RESULT_ALLOWED_APP_ALLOWS_ALL))
                .isTrue();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID))
                .that(isAllowed(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID))
                .isTrue();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_DISALLOWED_APP_DOES_NOT_EXIST))
                .that(isAllowed(RESULT_DISALLOWED_APP_DOES_NOT_EXIST))
                .isFalse();
        expect.withMessage(
                        "isAllowed(%s)", resultToString(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR))
                .that(isAllowed(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR))
                .isFalse();
        expect.withMessage(
                        "isAllowed(%s)", resultToString(RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG))
                .that(isAllowed(RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG))
                .isFalse();
        expect.withMessage(
                        "isAllowed(%s)",
                        resultToString(RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .that(isAllowed(RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION))
                .isFalse();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_DISALLOWED_BY_APP))
                .that(isAllowed(RESULT_DISALLOWED_BY_APP))
                .isFalse();
        expect.withMessage("isAllowed(%s)", resultToString(RESULT_DISALLOWED_GENERIC_ERROR))
                .that(isAllowed(RESULT_DISALLOWED_GENERIC_ERROR))
                .isFalse();
    }
}
