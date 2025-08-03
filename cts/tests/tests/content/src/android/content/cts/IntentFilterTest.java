/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.cts;

import static android.content.IntentFilter.MATCH_ADJUSTMENT_NORMAL;
import static android.content.IntentFilter.MATCH_CATEGORY_HOST;
import static android.content.IntentFilter.MATCH_CATEGORY_PATH;
import static android.content.IntentFilter.MATCH_CATEGORY_SCHEME_SPECIFIC_PART;
import static android.content.IntentFilter.MATCH_CATEGORY_TYPE;
import static android.content.IntentFilter.NO_MATCH_DATA;
import static android.content.UriRelativeFilter.PATH;
import static android.content.UriRelativeFilter.QUERY;
import static android.content.UriRelativeFilter.FRAGMENT;
import static android.content.UriRelativeFilterGroup.ACTION_ALLOW;
import static android.content.UriRelativeFilterGroup.ACTION_BLOCK;
import static android.os.PatternMatcher.PATTERN_ADVANCED_GLOB;
import static android.os.PatternMatcher.PATTERN_LITERAL;
import static android.os.PatternMatcher.PATTERN_PREFIX;
import static android.os.PatternMatcher.PATTERN_SIMPLE_GLOB;
import static android.os.PatternMatcher.PATTERN_SUFFIX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.AuthorityEntry;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.UriRelativeFilter;
import android.content.UriRelativeFilterGroup;
import android.content.pm.ActivityInfo;
import android.content.pm.Flags;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Parcel;
import android.os.PatternMatcher;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.annotations.PlatinumTest;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.RavenwoodFlagsValueProvider;
import android.platform.test.ravenwood.RavenwoodRule;
import android.test.mock.MockContext;
import android.util.Printer;
import android.util.StringBuilderPrinter;
import android.util.Xml;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@PlatinumTest(focusArea = "pm")
public class IntentFilterTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = RavenwoodRule.isOnRavenwood()
            ? RavenwoodFlagsValueProvider.createAllOnCheckFlagsRule()
            : DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private IntentFilter mIntentFilter;
    private static final String ACTION = "testAction";
    private static final String CATEGORY = "testCategory";
    private static final String DATA_DYNAMIC_TYPE = "type/dynamic";
    private static final String DATA_STATIC_TYPE = "vnd.android.cursor.dir/person";
    private static final String DATA_SCHEME = "testDataSchemes.";
    private static final String MIME_GROUP = "mime_group";
    private static final String SSP = "testSsp";
    private static final String HOST = "testHost";
    private static final int PORT = 80;
    private static final String DATA_PATH = "testDataPath";
    private static final Uri URI = Uri.parse("content://com.example/people");

    @Before
    public void setUp() throws Exception {
        if (mRavenwood.isUnderRavenwood()) {
            // TODO: replace with mockito when better supported
            mContext = new MockContext() {
                @Override
                public String getPackageName() {
                    return "android.content.cts";
                }
            };
        } else {
            mContext = InstrumentationRegistry.getTargetContext();
        }

        mIntentFilter = new IntentFilter();
    }

    @Test
    public void testConstructor() throws MalformedMimeTypeException {

        IntentFilter filter = new IntentFilter();
        verifyContent(filter, null, null);

        filter = new IntentFilter(ACTION);
        verifyContent(filter, ACTION, null);

        final IntentFilter actionTypeFilter = new IntentFilter(ACTION, DATA_STATIC_TYPE);
        verifyContent(actionTypeFilter, ACTION, DATA_STATIC_TYPE);

        filter = new IntentFilter(actionTypeFilter);
        verifyContent(filter, ACTION, DATA_STATIC_TYPE);

        final String dataType = "testdataType";
        try {
            new IntentFilter(ACTION, dataType);
            fail("Should throw MalformedMimeTypeException ");
        } catch (MalformedMimeTypeException e) {
            // expected
        }
    }

    /**
     * Assert that the given filter contains the given action and dataType. If
     * action or dataType are null, assert that the filter has no actions or
     * dataTypes registered.
     */
    private void verifyContent(IntentFilter filter, String action, String dataType) {
        if (action != null) {
            assertEquals(1, filter.countActions());
            assertEquals(action, filter.getAction(0));
        } else {
            assertEquals(0, filter.countActions());
        }
        if (dataType != null) {
            assertEquals(1, filter.countDataTypes());
            assertEquals(dataType, filter.getDataType(0));
            assertEquals(1, filter.countStaticDataTypes());
        } else {
            assertEquals(0, filter.countDataTypes());
            assertEquals(0, filter.countStaticDataTypes());
        }
    }

    @Test
    public void testCategories() {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addCategory(CATEGORY + i);
        }
        assertEquals(10, mIntentFilter.countCategories());
        Iterator<String> iter = mIntentFilter.categoriesIterator();
        String actual = null;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(CATEGORY + i, actual);
            assertEquals(CATEGORY + i, mIntentFilter.getCategory(i));
            assertTrue(mIntentFilter.hasCategory(CATEGORY + i));
            assertFalse(mIntentFilter.hasCategory(CATEGORY + i + 10));
            i++;
        }
        IntentFilter filter = new Match(null, new String[]{"category1"}, null, null, null, null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null,
                        new String[]{"category1"}, null, null),
                new MatchCondition(IntentFilter.NO_MATCH_CATEGORY, null,
                        new String[]{"category2"}, null, null),
                new MatchCondition(IntentFilter.NO_MATCH_CATEGORY, null, new String[]{
                        "category1", "category2"}, null, null));

        filter = new Match(null, new String[]{"category1", "category2"}, null, null, null, null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null,
                        new String[]{"category1"}, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null,
                        new String[]{"category2"}, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null, new String[]{
                        "category1", "category2"}, null, null),
                new MatchCondition(IntentFilter.NO_MATCH_CATEGORY, null,
                        new String[]{"category3"}, null, null),
                new MatchCondition(IntentFilter.NO_MATCH_CATEGORY, null, new String[]{
                        "category1", "category2", "category3"}, null, null));
    }

    @Test
    public void testMimeTypes() throws Exception {
        IntentFilter filter = new Match(null, null, new String[]{"which1/what1"}, null, null,
                null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which1/what2", null));

        filter = new Match(null, null, new String[]{"which1/what1", "which2/what2"}, null, null,
                null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/what2",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which1/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which3/what3", null));

        filter = new Match(null, null, new String[]{"which1/*"}, null, null, null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what2",
                        null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which3/what3", null));

        filter = new Match(null, null, new String[]{"*/*"}, null, null, null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/what2",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what2",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which3/what3",
                        null));
    }

    @Test
    public void testDynamicMimeTypes() {
        IntentFilter filter = new Match()
                .addDynamicMimeTypes(new String[] { "which1/what1" });
        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which1/what2", null));

        filter = new Match()
                .addDynamicMimeTypes(new String[] { "which1/what1", "which2/what2" });
        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/what2",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which1/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which3/what3", null));

        filter = new Match()
                .addDynamicMimeTypes(new String[] { "which1/*" });
        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what2",
                        null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which3/what3", null));

        filter = new Match()
                .addDynamicMimeTypes(new String[] { "*/*" });
        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/what2",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what2",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which3/what3",
                        null));
    }

    @Test
    public void testClearDynamicMimeTypesWithStaticType() {
        IntentFilter filter = new Match()
                .addMimeTypes(new String[] {"which1/what1"})
                .addDynamicMimeTypes(new String[] { "which2/what2" });

        checkMatches(filter, new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/what2",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which1/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which3/what3", null));

        filter.clearDynamicDataTypes();

        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which1/what2", null));
    }

    @Test
    public void testClearDynamicMimeTypesWithAction() {
        IntentFilter filter = new Match()
                .addActions(new String[] {"action1"})
                .addDynamicMimeTypes(new String[] { "which1/what1" });

        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/what1",
                        null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "which1/*", null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE, null, null, "*/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/what2", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which2/*", null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, null, null, "which1/what2", null));

        filter.clearDynamicDataTypes();

        checkMatches(filter,
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, "action1", null, null, null),
                new MatchCondition(IntentFilter.NO_MATCH_TYPE, "action1", null, "which2/what2",
                        null));
    }

    @Test
    public void testAccessPriority() {
        final int expected = 1;
        mIntentFilter.setPriority(expected);
        assertEquals(expected, mIntentFilter.getPriority());
    }

    @Test
    public void testDataSchemes() {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addDataScheme(DATA_SCHEME + i);
        }
        assertEquals(10, mIntentFilter.countDataSchemes());
        final Iterator<String> iter = mIntentFilter.schemesIterator();
        String actual = null;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_SCHEME + i, actual);
            assertEquals(DATA_SCHEME + i, mIntentFilter.getDataScheme(i));
            assertTrue(mIntentFilter.hasDataScheme(DATA_SCHEME + i));
            assertFalse(mIntentFilter.hasDataScheme(DATA_SCHEME + i + 10));
            i++;
        }
        IntentFilter filter = new Match(null, null, null, new String[]{"scheme1"}, null, null);
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_SCHEME, "scheme1:foo"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme2:foo"));

        filter = new Match(null, null, null, new String[]{"scheme1", "scheme2"}, null, null);
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_SCHEME, "scheme1:foo"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_SCHEME, "scheme2:foo"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme3:foo"));
    }

    @Test
    public void testCreate() {
        IntentFilter filter = IntentFilter.create(ACTION, DATA_STATIC_TYPE);
        assertNotNull(filter);
        verifyContent(filter, ACTION, DATA_STATIC_TYPE);
    }

    @Test
    public void testSchemeSpecificParts() throws Exception {
        IntentFilter filter = new Match(null, null, null, new String[]{"scheme"},
                null, null, null, null, new String[]{"ssp1", "2ssp"},
                new int[]{PATTERN_LITERAL, PATTERN_LITERAL});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp1"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:2ssp"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:ssp"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:ssp12"));
        filter = new Match(null, null, null, new String[]{"scheme"},
                null, null, null, null, new String[]{"ssp1", "2ssp"},
                new int[]{PATTERN_PREFIX, PATTERN_PREFIX});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp1"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:2ssp"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:ssp"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp12"));
        filter = new Match(null, null, null, new String[]{"scheme"},
                null, null, null, null, new String[]{"p1", "sp", ".file"},
                new int[]{PATTERN_SUFFIX, PATTERN_SUFFIX, PATTERN_SUFFIX});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp1"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:2ssp"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:something.file"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp"),
                MatchCondition.data(NO_MATCH_DATA, "scheme:ssp12"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", "ssp.*",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", "ssp.*",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp1"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:ss"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", ".*",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", ".*",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp1"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", "a1*b",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", "a1*b",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ab"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a1b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a11b"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a2b"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a1bc"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", "a1*",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", "a1*",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a1"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:ab"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a11"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a1b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a11"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a2"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", "a\\.*b",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", "a\\.*b",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ab"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a.b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a..b"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a2b"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a.bc"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", "a[.1-2]*b",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", "a.*b",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ab"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a.b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a.1b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a2b"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a.bc"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", "a.*",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", "a.*",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ab"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a.b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a.1b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a2b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a.bc"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", "a.\\*b",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", "a.\\*b",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:ab"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a.*b"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a1*b"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a2b"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a.bc"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:"));
        checkMatches(new IntentFilter[]{
                        filterForSchemeAndSchemeSpecificPart("scheme", "a.\\*",
                                PATTERN_ADVANCED_GLOB),
                        filterForSchemeAndSchemeSpecificPart("scheme", "a.\\*",
                                PATTERN_SIMPLE_GLOB)},
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:ab"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a.*"),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:a1*"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:a1b"));
    }

    private Match filterForSchemeAndSchemeSpecificPart(String scheme, String ssp, int matchType) {
        return new Match(null, null, null, new String[]{scheme},
                null, null, null, null, new String[]{ssp},
                new int[]{matchType});
    }

    @Test
    public void testSchemeSpecificPartsWithWildCards() throws Exception {
        IntentFilter filter = new Match(null, null, null, new String[]{"scheme"},
                null, null, null, null, new String[]{"ssp1"},
                new int[]{PATTERN_LITERAL, PATTERN_LITERAL});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null, true),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:ssp1", true),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "*:ssp1", true),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "scheme:*", true),
                MatchCondition.data(MATCH_CATEGORY_SCHEME_SPECIFIC_PART, "*:*", true),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:ssp12", true));

        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "*:ssp1", false),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme:*", false),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "*:*", false));
    }

    @Test
    public void testAuthorities() {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addDataAuthority(HOST + i, String.valueOf(PORT + i));
        }
        assertEquals(10, mIntentFilter.countDataAuthorities());

        final Iterator<AuthorityEntry> iter = mIntentFilter.authoritiesIterator();
        AuthorityEntry actual = null;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(HOST + i, actual.getHost());
            assertEquals(PORT + i, actual.getPort());
            AuthorityEntry ae = new AuthorityEntry(HOST + i, String.valueOf(PORT + i));
            assertEquals(ae.getHost(), mIntentFilter.getDataAuthority(i).getHost());
            assertEquals(ae.getPort(), mIntentFilter.getDataAuthority(i).getPort());
            Uri uri = Uri.parse("http://" + HOST + i + ":" + String.valueOf(PORT + i));
            assertTrue(mIntentFilter.hasDataAuthority(uri));
            Uri uri2 = Uri.parse("http://" + HOST + i + 10 + ":" + PORT + i + 10);
            assertFalse(mIntentFilter.hasDataAuthority(uri2));
            i++;
        }
        IntentFilter filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"authority1"}, new String[]{null});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1:foo"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://authority1/"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority2/"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://authority1:100/"));

        filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"authority1"}, new String[]{"100"});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1:foo"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority1/"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority2/"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_PORT, "scheme1://authority1:100/"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority1:200/"));

        filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"authority1", "authority2"}, new String[]{"100", null});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1:foo"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority1/"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://authority2/"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_PORT, "scheme1://authority1:100/"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority1:200/"));
    }

    @Test
    public void testAuthoritiesWithWildcards() throws Exception {
        IntentFilter filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"authority1"}, new String[]{null});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null, true),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1:*", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://*/", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://*:100/", true),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://*/", false),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://*:100/", false));

        filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"authority1"}, new String[]{"100"});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null, true),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA,       "scheme1:*", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://*/", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://*:100/", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://*:200/", true),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA,       "*:foo", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "*://authority1/", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "*://authority1:100/", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "*://authority1:200/", true),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA,       "*:*", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "*://*/", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "*://*:100/", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "*://*:200/", true));

        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://*/", false),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://*:100/", false),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "*://authority1:100/", false),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "*://*/", false),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "*://*:100/", false));

        filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"*"}, null);
        checkMatches(filter,
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://", true),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://", false),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://*", true));
    }

    @Test
    public void testDataTypes() throws MalformedMimeTypeException {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addDataType(DATA_STATIC_TYPE + i);
        }
        assertEquals(10, mIntentFilter.countDataTypes());
        assertEquals(10, mIntentFilter.countStaticDataTypes());
        final Iterator<String> iter = mIntentFilter.typesIterator();
        String actual;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_STATIC_TYPE + i, actual);
            assertEquals(DATA_STATIC_TYPE + i, mIntentFilter.getDataType(i));
            assertTrue(mIntentFilter.hasDataType(DATA_STATIC_TYPE + i));
            assertFalse(mIntentFilter.hasDataType(DATA_STATIC_TYPE + i + 10));
            i++;
        }
    }

    @Test
    public void testDynamicDataTypes() throws MalformedMimeTypeException {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addDynamicDataType(DATA_DYNAMIC_TYPE + i);
        }
        assertEquals(10, mIntentFilter.countDataTypes());
        assertEquals(0, mIntentFilter.countStaticDataTypes());

        final Iterator<String> iter = mIntentFilter.typesIterator();
        String actual;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_DYNAMIC_TYPE + i, actual);
            assertEquals(DATA_DYNAMIC_TYPE + i, mIntentFilter.getDataType(i));
            assertTrue(mIntentFilter.hasDataType(DATA_DYNAMIC_TYPE + i));
            assertFalse(mIntentFilter.hasDataType(DATA_DYNAMIC_TYPE + i + 10));
            i++;
        }
    }

    @Test
    public void testClearDynamicDataTypes() throws MalformedMimeTypeException {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addDataType(DATA_STATIC_TYPE + i);
            mIntentFilter.addDynamicDataType(DATA_DYNAMIC_TYPE + i);
        }
        assertEquals(20, mIntentFilter.countDataTypes());
        assertEquals(10, mIntentFilter.countStaticDataTypes());

        mIntentFilter.clearDynamicDataTypes();

        assertEquals(10, mIntentFilter.countDataTypes());
        assertEquals(10, mIntentFilter.countStaticDataTypes());

        final Iterator<String> iter = mIntentFilter.typesIterator();
        String actual;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_STATIC_TYPE + i, actual);
            assertEquals(DATA_STATIC_TYPE + i, mIntentFilter.getDataType(i));
            assertTrue(mIntentFilter.hasDataType(DATA_STATIC_TYPE + i));
            assertFalse(mIntentFilter.hasDataType(DATA_DYNAMIC_TYPE + i));
            i++;
        }
    }

    @Test
    public void testMimeGroups() {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addMimeGroup(MIME_GROUP + i);
        }
        assertEquals(10, mIntentFilter.countMimeGroups());
        final Iterator<String> iter = mIntentFilter.mimeGroupsIterator();
        String actual = null;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(MIME_GROUP + i, actual);
            assertEquals(MIME_GROUP + i, mIntentFilter.getMimeGroup(i));
            assertTrue(mIntentFilter.hasMimeGroup(MIME_GROUP + i));
            assertFalse(mIntentFilter.hasMimeGroup(MIME_GROUP + i + 10));
            i++;
        }
    }

    @Test
    public void testAppEnumerationMatchesMimeGroups() {
        IntentFilter filter = new Match(new String[]{ACTION}, null, null, new String[]{"scheme1"},
                new String[]{"authority1"}, null).addMimeGroups(new String[]{"test"});

        // assume any mime type or no mime type matches a filter with a mimegroup defined.
        checkMatches(filter,
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE,
                        ACTION, null, "img/jpeg", "scheme1://authority1", true),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE,
                        ACTION, null, null, "scheme1://authority1", true),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_TYPE,
                        ACTION, null, "*/*", "scheme1://authority1", true));
    }

    @Test
    public void testMatchData() throws MalformedMimeTypeException {
        int expected = IntentFilter.MATCH_CATEGORY_EMPTY + IntentFilter.MATCH_ADJUSTMENT_NORMAL;
        assertEquals(expected, mIntentFilter.matchData(null, null, null));
        assertEquals(expected, mIntentFilter.matchData(null, DATA_SCHEME, null));

        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, URI));
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.matchData(DATA_STATIC_TYPE,
                DATA_SCHEME, URI));

        mIntentFilter.addDataScheme(DATA_SCHEME);
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.matchData(DATA_STATIC_TYPE,
                "mDataSchemestest", URI));
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.matchData(DATA_STATIC_TYPE, "",
                URI));

        expected = IntentFilter.MATCH_CATEGORY_SCHEME + IntentFilter.MATCH_ADJUSTMENT_NORMAL;
        assertEquals(expected, mIntentFilter.matchData(null, DATA_SCHEME, URI));
        assertEquals(IntentFilter.NO_MATCH_TYPE, mIntentFilter.matchData(DATA_STATIC_TYPE,
                DATA_SCHEME, URI));

        mIntentFilter.addDataType(DATA_STATIC_TYPE);
        assertEquals(IntentFilter.MATCH_CATEGORY_TYPE + IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(DATA_STATIC_TYPE, DATA_SCHEME, URI));

        mIntentFilter.addDataAuthority(HOST, String.valueOf(PORT));
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, URI));

        final Uri uri = Uri.parse("http://" + HOST + ":" + PORT);
        mIntentFilter.addDataPath(DATA_PATH, PatternMatcher.PATTERN_LITERAL);
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, uri));
    }

    @Test
    @IgnoreUnderRavenwood
    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    public void testMatchDataWithRelRefGroups() {
        Uri uri = Uri.parse("https://" + HOST + "/path?query=string&cat=gizmo#fragment");
        Uri uri2 = Uri.parse("https://" + HOST + "/path?query=string;cat=gizmo#fragment");
        mIntentFilter.addDataScheme(DATA_SCHEME);
        mIntentFilter.addDataAuthority(HOST, null);
        assertEquals(MATCH_CATEGORY_HOST + MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(null, DATA_SCHEME, uri));

        // PATH matches so group match
        UriRelativeFilterGroup matchGroup = new UriRelativeFilterGroup(ACTION_ALLOW);
        matchGroup.addUriRelativeFilter(new UriRelativeFilter(PATH, PATTERN_LITERAL, "/path"));
        mIntentFilter.addUriRelativeFilterGroup(matchGroup);
        assertEquals(MATCH_CATEGORY_PATH + MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(null, DATA_SCHEME, uri));
        mIntentFilter.clearUriRelativeFilterGroups();

        // PATH does not match so group does not match
        UriRelativeFilterGroup noMatchGroup = new UriRelativeFilterGroup(ACTION_ALLOW);
        noMatchGroup.addUriRelativeFilter(new UriRelativeFilter(PATH, PATTERN_LITERAL, "/trail"));
        mIntentFilter.addUriRelativeFilterGroup(noMatchGroup);
        assertEquals(NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, uri));
        mIntentFilter.clearUriRelativeFilterGroups();

        // PATH and QUERY match so group match
        UriRelativeFilterGroup matchGroup1 = new UriRelativeFilterGroup(ACTION_ALLOW);
        matchGroup1.addUriRelativeFilter(new UriRelativeFilter(PATH, PATTERN_LITERAL, "/path"));
        matchGroup1.addUriRelativeFilter(new UriRelativeFilter(QUERY, PATTERN_SIMPLE_GLOB, ".*"));
        mIntentFilter.addUriRelativeFilterGroup(matchGroup1);
        assertEquals(MATCH_CATEGORY_PATH + MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(null, DATA_SCHEME, uri));
        assertEquals(MATCH_CATEGORY_PATH + MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(null, DATA_SCHEME, uri2));
        mIntentFilter.clearUriRelativeFilterGroups();

        // PATH match but QUERY does not match so group does not match
        UriRelativeFilterGroup noMatchGroup1 = new UriRelativeFilterGroup(ACTION_ALLOW);
        noMatchGroup1.addUriRelativeFilter(new UriRelativeFilter(PATH, PATTERN_LITERAL, "/path"));
        noMatchGroup1.addUriRelativeFilter(new UriRelativeFilter(QUERY, PATTERN_PREFIX, "query"));
        noMatchGroup1.addUriRelativeFilter(new UriRelativeFilter(QUERY, PATTERN_SUFFIX, "widget"));
        mIntentFilter.addUriRelativeFilterGroup(noMatchGroup1);
        assertEquals(NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, uri));
        assertEquals(NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, uri2));
        mIntentFilter.clearUriRelativeFilterGroups();

        // PATH, QUERY, and FRAGMENT all match so group matches
        UriRelativeFilterGroup matchGroup2 = new UriRelativeFilterGroup(ACTION_ALLOW);
        matchGroup2.addUriRelativeFilter(new UriRelativeFilter(PATH, PATTERN_LITERAL, "/path"));
        matchGroup2.addUriRelativeFilter(new UriRelativeFilter(QUERY, PATTERN_PREFIX, "query"));
        matchGroup2.addUriRelativeFilter(new UriRelativeFilter(FRAGMENT, PATTERN_SIMPLE_GLOB,
                "fr.*"));
        mIntentFilter.addUriRelativeFilterGroup(matchGroup2);
        assertEquals(MATCH_CATEGORY_PATH + MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(null, DATA_SCHEME, uri));
        assertEquals(MATCH_CATEGORY_PATH + MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(null, DATA_SCHEME, uri2));
        mIntentFilter.clearUriRelativeFilterGroups();

        // PATH, QUERY, and FRAGMENT all match but group is disallow
        // and intent filter will not match
        UriRelativeFilterGroup disallowGroup = new UriRelativeFilterGroup(ACTION_BLOCK);
        disallowGroup.addUriRelativeFilter(new UriRelativeFilter(PATH, PATTERN_LITERAL, "/path"));
        disallowGroup.addUriRelativeFilter(new UriRelativeFilter(QUERY, PATTERN_PREFIX, "query"));
        disallowGroup.addUriRelativeFilter(new UriRelativeFilter(QUERY, PATTERN_SUFFIX, "gizmo"));
        disallowGroup.addUriRelativeFilter(new UriRelativeFilter(FRAGMENT, PATTERN_SIMPLE_GLOB,
                "fr.*"));
        mIntentFilter.addUriRelativeFilterGroup(disallowGroup);
        assertEquals(NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, uri));
        assertEquals(NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, uri2));
        mIntentFilter.clearUriRelativeFilterGroups();

        // Since disallow group is defined first it will match but prevent the
        // intent filter from matching even though though a matching group is
        // defined after the disallow group.
        mIntentFilter.addUriRelativeFilterGroup(disallowGroup);
        mIntentFilter.addUriRelativeFilterGroup(matchGroup);
        assertEquals(NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, uri));
        assertEquals(NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, uri2));
        mIntentFilter.clearUriRelativeFilterGroups();

        // First group will not match so check the next group. The second group will
        // match so the disallowed group will not be checked and the intent filter
        // will match.
        mIntentFilter.addUriRelativeFilterGroup(noMatchGroup);
        mIntentFilter.addUriRelativeFilterGroup(matchGroup);
        mIntentFilter.addUriRelativeFilterGroup(disallowGroup);
        assertEquals(MATCH_CATEGORY_PATH + MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(null, DATA_SCHEME, uri));
        assertEquals(MATCH_CATEGORY_PATH + MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.matchData(null, DATA_SCHEME, uri2));
    }

    @Test
    public void testActions() {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addAction(ACTION + i);
        }
        assertEquals(10, mIntentFilter.countActions());
        final Iterator<String> iter = mIntentFilter.actionsIterator();
        String actual = null;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(ACTION + i, actual);
            assertEquals(ACTION + i, mIntentFilter.getAction(i));
            assertTrue(mIntentFilter.hasAction(ACTION + i));
            assertFalse(mIntentFilter.hasAction(ACTION + i + 10));
            assertTrue(mIntentFilter.matchAction(ACTION + i));
            assertFalse(mIntentFilter.matchAction(ACTION + i + 10));
            i++;
        }
        IntentFilter filter = new Match(new String[]{"action1"}, null, null, null, null, null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, "action1", null, null, null),
                new MatchCondition(IntentFilter.NO_MATCH_ACTION, "action2", null, null, null));

        filter = new Match(new String[]{"action1", "action2"}, null, null, null, null, null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null, null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, "action1", null, null, null),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, "action2", null, null, null),
                new MatchCondition(IntentFilter.NO_MATCH_ACTION, "action3", null, null, null),
                new MatchCondition(IntentFilter.NO_MATCH_ACTION, "action1", null, null, null, false,
                        Arrays.asList("action1", "action2")),
                new MatchCondition(IntentFilter.NO_MATCH_ACTION, "action2", null, null, null, false,
                        Arrays.asList("action1", "action2")),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, "action1", null, null, null,
                        false, Arrays.asList("action2")));
    }

    @Test
    public void testActionWildCards() throws Exception {
        IntentFilter filter =
                new Match(new String[]{"action1", "action2"}, null, null, null, null, null);
        checkMatches(filter,
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, null, null, null, null, true),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, "*", null, null, null, true),
                new MatchCondition(IntentFilter.MATCH_CATEGORY_EMPTY, "*", null, null, null, true,
                        Arrays.asList("action1")),
                new MatchCondition(IntentFilter.NO_MATCH_ACTION, "*", null, null, null, true,
                        Arrays.asList("action1", "action2")),
                new MatchCondition(
                        IntentFilter.NO_MATCH_ACTION, "action3", null, null, null, true));

        checkMatches(filter,
                new MatchCondition(IntentFilter.NO_MATCH_ACTION, "*", null, null, null, false));

    }

    @Test
    public void testAppEnumerationContactProviders() throws Exception {
        // sample contact source
        IntentFilter filter = new Match(new String[]{Intent.ACTION_VIEW},
                new String[]{Intent.CATEGORY_DEFAULT},
                new String[]{"vnd.android.cursor.item/vnd.com.someapp.profile"},
                new String[]{"content"},
                new String[]{"com.android.contacts"},
                null /*ports*/);

        // app that would like to match all contact sources
        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_TYPE,
                        Intent.ACTION_VIEW,
                        null /*categories*/,
                        "vnd.android.cursor.item/*",
                        "content://com.android.contacts",
                        true));
    }

    @Test
    public void testAppEnumerationDocumentEditor() throws Exception {
        // sample document editor
        IntentFilter filter = new Match(
                new String[]{
                        Intent.ACTION_VIEW,
                        Intent.ACTION_EDIT,
                        "com.app.android.intent.action.APP_EDIT",
                        "com.app.android.intent.action.APP_VIEW"},
                new String[]{Intent.CATEGORY_DEFAULT},
                new String[]{
                        "application/msword",
                        "application/vnd.oasis.opendocument.text",
                        "application/rtf",
                        "text/rtf",
                        "text/plain",
                        "application/pdf",
                        "application/x-pdf",
                        "application/docm"},
                null /*schemes*/,
                null /*authorities*/,
                null /*ports*/);

        // app that would like to match all doc editors
        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_TYPE,
                        Intent.ACTION_VIEW,
                        new String[]{Intent.CATEGORY_DEFAULT},
                        "*/*",
                        "content://com.example.fileprovider",
                        true));

    }

    @Test
    public void testAppEnumerationDeepLinks() throws Exception {
        // Sample app that supports deep-links
        IntentFilter filter = new Match(
                new String[]{Intent.ACTION_VIEW},
                new String[]{
                        Intent.CATEGORY_DEFAULT,
                        Intent.CATEGORY_BROWSABLE},
                null /*types*/,
                new String[]{"http", "https"},
                new String[]{"arbitrary-site.com"},
                null /*ports*/);

        // Browser that would like to see all deep-linkable http/s app, but not all apps
        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_HOST,
                        Intent.ACTION_VIEW,
                        new String[]{Intent.CATEGORY_BROWSABLE},
                        null,
                        "https://*",
                        true));
        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_HOST,
                        Intent.ACTION_VIEW,
                        new String[]{Intent.CATEGORY_BROWSABLE},
                        null,
                        "http://*",
                        true));
    }

    @Test
    public void testAppEnumerationCustomShareSheet() throws Exception {
        // Sample share target
        IntentFilter filter = new Match(
                new String[]{Intent.ACTION_SEND},
                new String[]{Intent.CATEGORY_DEFAULT},
                new String[]{"*/*"},
                null /*schemes*/,
                null /*authorities*/,
                null /*ports*/);

        // App with custom share sheet that would like to see all jpeg targets
        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_TYPE,
                        Intent.ACTION_SEND,
                        null /*categories*/,
                        "image/jpeg",
                        "content://com.example.fileprovider",
                        true));
        // App with custom share sheet that would like to see all jpeg targets that don't specify
        // a host
        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_TYPE,
                        Intent.ACTION_SEND,
                        null /*categories*/,
                        "image/jpeg",
                        "content:",
                        true));
    }

    @Test
    public void testAppEnumerationNoHostMatchesWildcardHost() throws Exception {
        IntentFilter filter = new Match(
                new String[]{Intent.ACTION_VIEW},
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                new String[]{"http", "https"},
                new String[]{"*"},
                null /*ports*/);
        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_HOST,
                        Intent.ACTION_VIEW,
                        new String[]{Intent.CATEGORY_BROWSABLE},
                        null,
                        "https://*",
                        true));

        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_HOST,
                        Intent.ACTION_VIEW,
                        new String[]{Intent.CATEGORY_BROWSABLE},
                        null,
                        "https://",
                        true));
    }

    @Test
    public void testAppEnumerationNoPortMatchesPortFilter() throws Exception {
        IntentFilter filter = new Match(
                new String[]{Intent.ACTION_VIEW},
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                new String[]{"http", "https"},
                new String[]{"*"},
                new String[]{"81"});
        checkMatches(filter,
                new MatchCondition(MATCH_CATEGORY_HOST,
                        Intent.ACTION_VIEW,
                        new String[]{Intent.CATEGORY_BROWSABLE},
                        null,
                        "https://something",
                        true));
    }

    @Test
    public void testAppEnumerationBrowser() throws Exception {
        IntentFilter appWithWebLink = new Match(
                new String[]{Intent.ACTION_VIEW},
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                new String[]{"http", "https"},
                new String[]{"some.app.domain"},
                null);

        IntentFilter appWithWildcardWebLink = new Match(
                new String[]{Intent.ACTION_VIEW},
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                new String[]{"http", "https"},
                new String[]{"*.app.domain"},
                null);

        IntentFilter browserFilterWithWildcard = new Match(
                new String[]{Intent.ACTION_VIEW},
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                new String[]{"http", "https"},
                new String[]{"*"},
                null);

        IntentFilter browserFilterWithoutWildcard = new Match(
                new String[]{Intent.ACTION_VIEW},
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                new String[]{"http", "https"},
                null,
                null);

        checkMatches(browserFilterWithWildcard,
                new MatchCondition(MATCH_CATEGORY_HOST,
                Intent.ACTION_VIEW,
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                "https://",
                true));
        checkMatches(browserFilterWithoutWildcard,
                new MatchCondition(IntentFilter.MATCH_CATEGORY_SCHEME | MATCH_ADJUSTMENT_NORMAL,
                Intent.ACTION_VIEW,
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                "https://",
                true));
        checkMatches(appWithWebLink,
                new MatchCondition(NO_MATCH_DATA,
                Intent.ACTION_VIEW,
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                "https://",
                true));
        checkMatches(appWithWildcardWebLink,
                new MatchCondition(NO_MATCH_DATA,
                Intent.ACTION_VIEW,
                new String[]{Intent.CATEGORY_BROWSABLE},
                null,
                "https://",
                true));
    }

    @Test
    public void testWriteToXml() throws IllegalArgumentException, IllegalStateException,
            IOException, MalformedMimeTypeException, XmlPullParserException {
        XmlSerializer xml;
        ByteArrayOutputStream out;

        xml = Xml.newSerializer();
        out = new ByteArrayOutputStream();
        xml.setOutput(out, "utf-8");
        mIntentFilter.addAction(ACTION);
        mIntentFilter.addCategory(CATEGORY);
        mIntentFilter.addDataAuthority(HOST, String.valueOf(PORT));
        mIntentFilter.addDataPath(DATA_PATH, 1);
        mIntentFilter.addDataScheme(DATA_SCHEME);
        mIntentFilter.addDataType(DATA_STATIC_TYPE);
        mIntentFilter.addDynamicDataType(DATA_DYNAMIC_TYPE);
        mIntentFilter.addMimeGroup(MIME_GROUP);
        mIntentFilter.writeToXml(xml);
        xml.flush();
        final XmlPullParser parser = Xml.newPullParser();
        final InputStream in = new ByteArrayInputStream(out.toByteArray());
        parser.setInput(in, "utf-8");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.readFromXml(parser);
        assertEquals(ACTION, intentFilter.getAction(0));
        assertEquals(CATEGORY, intentFilter.getCategory(0));
        assertTrue(intentFilter.hasExactStaticDataType(DATA_STATIC_TYPE));
        assertTrue(intentFilter.hasExactDynamicDataType(DATA_DYNAMIC_TYPE));
        assertEquals(MIME_GROUP, intentFilter.getMimeGroup(0));
        assertEquals(DATA_SCHEME, intentFilter.getDataScheme(0));
        assertEquals(DATA_PATH, intentFilter.getDataPath(0).getPath());
        assertEquals(HOST, intentFilter.getDataAuthority(0).getHost());
        assertEquals(PORT, intentFilter.getDataAuthority(0).getPort());
        out.close();
    }

    @Test
    @IgnoreUnderRavenwood
    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    public void testWriteToXmlWithRelRefGroup() throws IllegalArgumentException,
            IllegalStateException, IOException, MalformedMimeTypeException, XmlPullParserException {
        XmlSerializer xml;
        ByteArrayOutputStream out;
        UriRelativeFilterGroup relRefGroup = new UriRelativeFilterGroup(ACTION_ALLOW);
        relRefGroup.addUriRelativeFilter(new UriRelativeFilter(PATH, PATTERN_LITERAL, "/path"));
        relRefGroup.addUriRelativeFilter(new UriRelativeFilter(QUERY, PATTERN_SIMPLE_GLOB, "q*"));

        xml = Xml.newSerializer();
        out = new ByteArrayOutputStream();
        xml.setOutput(out, "utf-8");
        mIntentFilter.addAction(ACTION);
        mIntentFilter.addCategory(CATEGORY);
        mIntentFilter.addDataAuthority(HOST, String.valueOf(PORT));
        mIntentFilter.addDataPath(DATA_PATH, 1);
        mIntentFilter.addDataScheme(DATA_SCHEME);
        mIntentFilter.addDataType(DATA_STATIC_TYPE);
        mIntentFilter.addDynamicDataType(DATA_DYNAMIC_TYPE);
        mIntentFilter.addMimeGroup(MIME_GROUP);
        mIntentFilter.addUriRelativeFilterGroup(relRefGroup);
        mIntentFilter.writeToXml(xml);
        xml.flush();
        final XmlPullParser parser = Xml.newPullParser();
        final InputStream in = new ByteArrayInputStream(out.toByteArray());
        parser.setInput(in, "utf-8");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.readFromXml(parser);
        assertEquals(ACTION, intentFilter.getAction(0));
        assertEquals(CATEGORY, intentFilter.getCategory(0));
        assertTrue(intentFilter.hasExactStaticDataType(DATA_STATIC_TYPE));
        assertTrue(intentFilter.hasExactDynamicDataType(DATA_DYNAMIC_TYPE));
        assertEquals(MIME_GROUP, intentFilter.getMimeGroup(0));
        assertEquals(DATA_SCHEME, intentFilter.getDataScheme(0));
        assertEquals(DATA_PATH, intentFilter.getDataPath(0).getPath());
        assertEquals(HOST, intentFilter.getDataAuthority(0).getHost());
        assertEquals(PORT, intentFilter.getDataAuthority(0).getPort());
        assertEquals(mIntentFilter.countUriRelativeFilterGroups(),
                intentFilter.countUriRelativeFilterGroups());
        assertEquals(mIntentFilter.getUriRelativeFilterGroup(0).getUriRelativeFilters().size(),
                intentFilter.getUriRelativeFilterGroup(0).getUriRelativeFilters().size());
        Iterator<UriRelativeFilter> it =
                intentFilter.getUriRelativeFilterGroup(0).getUriRelativeFilters().iterator();
        assertTrue(relRefGroup.getUriRelativeFilters().contains(it.next()));
        assertTrue(relRefGroup.getUriRelativeFilters().contains(it.next()));
        assertFalse(it.hasNext());
        out.close();
    }

    @Test
    public void testMatchCategories() {
        assertNull(mIntentFilter.matchCategories(null));
        Set<String> cat = new HashSet<String>();
        assertNull(mIntentFilter.matchCategories(cat));

        final String expected = "mytest";
        cat.add(expected);
        assertEquals(expected, mIntentFilter.matchCategories(cat));

        cat = new HashSet<String>();
        cat.add(CATEGORY);
        mIntentFilter.addCategory(CATEGORY);
        assertNull(mIntentFilter.matchCategories(cat));
        cat.add(expected);
        assertEquals(expected, mIntentFilter.matchCategories(cat));
    }

    @Test
    public void testMatchDataAuthority() {
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.matchDataAuthority(null));
        mIntentFilter.addDataAuthority(HOST, String.valueOf(PORT));
        final Uri uri = Uri.parse("http://" + HOST + ":" + PORT);
        assertEquals(IntentFilter.MATCH_CATEGORY_PORT, mIntentFilter.matchDataAuthority(uri));
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, mIntentFilter.describeContents());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testReadFromXml()
            throws NameNotFoundException, XmlPullParserException, IOException {
        XmlPullParser parser = null;
        ActivityInfo ai = null;

        final ComponentName mComponentName = new ComponentName(
                "android.content.cts", "android.content.cts.MockActivity");
        final PackageManager pm = mContext.getPackageManager();
        ai = pm.getActivityInfo(mComponentName, PackageManager.GET_META_DATA);

        parser = ai.loadXmlMetaData(pm, "android.app.intent.filter");

        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && type != XmlPullParser.START_TAG) {
        }

        final String nodeName = parser.getName();

        if (!"intent-filter".equals(nodeName)) {
            throw new RuntimeException();
        }

        mIntentFilter.readFromXml(parser);

        assertEquals("testAction", mIntentFilter.getAction(0));
        assertEquals("testCategory", mIntentFilter.getCategory(0));

        assertTrue(mIntentFilter.hasExactDynamicDataType("vnd.android.cursor.dir/person"));
        assertTrue(mIntentFilter.hasExactStaticDataType("text/plain"));

        assertEquals("testMimeGroup", mIntentFilter.getMimeGroup(0));
        assertEquals("testScheme", mIntentFilter.getDataScheme(0));
        assertEquals("testHost", mIntentFilter.getDataAuthority(0).getHost());
        assertEquals(80, mIntentFilter.getDataAuthority(0).getPort());

        assertEquals("test", mIntentFilter.getDataPath(0).getPath());
        assertEquals("test", mIntentFilter.getDataPath(1).getPath());
        assertEquals("test", mIntentFilter.getDataPath(2).getPath());
    }

    @Test
    public void testDataPaths() {
        for (int i = 0; i < 10; i++) {
            mIntentFilter.addDataPath(DATA_PATH + i, PatternMatcher.PATTERN_PREFIX);
        }
        assertEquals(10, mIntentFilter.countDataPaths());
        Iterator<PatternMatcher> iter = mIntentFilter.pathsIterator();
        PatternMatcher actual = null;
        int i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_PATH + i, actual.getPath());
            assertEquals(PatternMatcher.PATTERN_PREFIX, actual.getType());
            PatternMatcher p = new PatternMatcher(DATA_PATH + i, PatternMatcher.PATTERN_PREFIX);
            assertEquals(p.getPath(), mIntentFilter.getDataPath(i).getPath());
            assertEquals(p.getType(), mIntentFilter.getDataPath(i).getType());
            assertTrue(mIntentFilter.hasDataPath(DATA_PATH + i));
            assertTrue(mIntentFilter.hasDataPath(DATA_PATH + i + 10));
            i++;
        }

        mIntentFilter = new IntentFilter();
        for (i = 0; i < 10; i++) {
            mIntentFilter.addDataPath(DATA_PATH + i, PatternMatcher.PATTERN_SUFFIX);
        }
        assertEquals(10, mIntentFilter.countDataPaths());
        iter = mIntentFilter.pathsIterator();
        i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_PATH + i, actual.getPath());
            assertEquals(PatternMatcher.PATTERN_SUFFIX, actual.getType());
            PatternMatcher p = new PatternMatcher(DATA_PATH + i, PatternMatcher.PATTERN_SUFFIX);
            assertEquals(p.getPath(), mIntentFilter.getDataPath(i).getPath());
            assertEquals(p.getType(), mIntentFilter.getDataPath(i).getType());
            assertTrue(mIntentFilter.hasDataPath(DATA_PATH + i));
            assertTrue(mIntentFilter.hasDataPath("a" + DATA_PATH + i));
            i++;
        }

        mIntentFilter = new IntentFilter();
        i = 0;
        for (i = 0; i < 10; i++) {
            mIntentFilter.addDataPath(DATA_PATH + i, PatternMatcher.PATTERN_LITERAL);
        }
        assertEquals(10, mIntentFilter.countDataPaths());
        iter = mIntentFilter.pathsIterator();
        i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_PATH + i, actual.getPath());
            assertEquals(PatternMatcher.PATTERN_LITERAL, actual.getType());
            PatternMatcher p = new PatternMatcher(DATA_PATH + i, PatternMatcher.PATTERN_LITERAL);
            assertEquals(p.getPath(), mIntentFilter.getDataPath(i).getPath());
            assertEquals(p.getType(), mIntentFilter.getDataPath(i).getType());
            assertTrue(mIntentFilter.hasDataPath(DATA_PATH + i));
            assertFalse(mIntentFilter.hasDataPath(DATA_PATH + i + 10));
            i++;
        }
        mIntentFilter = new IntentFilter();
        i = 0;
        for (i = 0; i < 10; i++) {
            mIntentFilter.addDataPath(DATA_PATH + i, PatternMatcher.PATTERN_SIMPLE_GLOB);
        }
        assertEquals(10, mIntentFilter.countDataPaths());
        iter = mIntentFilter.pathsIterator();
        i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_PATH + i, actual.getPath());
            assertEquals(PatternMatcher.PATTERN_SIMPLE_GLOB, actual.getType());
            PatternMatcher p = new PatternMatcher(DATA_PATH + i,
                    PatternMatcher.PATTERN_SIMPLE_GLOB);
            assertEquals(p.getPath(), mIntentFilter.getDataPath(i).getPath());
            assertEquals(p.getType(), mIntentFilter.getDataPath(i).getType());
            assertTrue(mIntentFilter.hasDataPath(DATA_PATH + i));
            assertFalse(mIntentFilter.hasDataPath(DATA_PATH + i + 10));
            i++;
        }

        mIntentFilter = new IntentFilter();
        for (i = 0; i < 10; i++) {
            mIntentFilter.addDataPath(DATA_PATH + i, PatternMatcher.PATTERN_ADVANCED_GLOB);
        }
        assertEquals(10, mIntentFilter.countDataPaths());
        iter = mIntentFilter.pathsIterator();
        i = 0;
        while (iter.hasNext()) {
            actual = iter.next();
            assertEquals(DATA_PATH + i, actual.getPath());
            assertEquals(PatternMatcher.PATTERN_ADVANCED_GLOB, actual.getType());
            PatternMatcher p = new PatternMatcher(DATA_PATH + i,
                    PatternMatcher.PATTERN_ADVANCED_GLOB);
            assertEquals(p.getPath(), mIntentFilter.getDataPath(i).getPath());
            assertEquals(p.getType(), mIntentFilter.getDataPath(i).getType());
            assertTrue(mIntentFilter.hasDataPath(DATA_PATH + i));
            assertFalse(mIntentFilter.hasDataPath(DATA_PATH + i + 10));
            i++;
        }

        IntentFilter filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"authority1"}, new String[]{null});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1:foo"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://authority1/"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority2/"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://authority1:100/"));

        filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"authority1"}, new String[]{"100"});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1:foo"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority1/"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority2/"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_PORT, "scheme1://authority1:100/"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority1:200/"));

        filter = new Match(null, null, null, new String[]{"scheme1"},
                new String[]{"authority1", "authority2"}, new String[]{"100", null});
        checkMatches(filter,
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1:foo"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority1/"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_HOST, "scheme1://authority2/"),
                MatchCondition.data(IntentFilter.MATCH_CATEGORY_PORT, "scheme1://authority1:100/"),
                MatchCondition.data(IntentFilter.NO_MATCH_DATA, "scheme1://authority1:200/"));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ContentResolver.class)
    public void testMatchWithIntent() throws MalformedMimeTypeException {
        final ContentResolver resolver = mContext.getContentResolver();

        Intent intent = new Intent(ACTION);
        assertEquals(IntentFilter.NO_MATCH_ACTION, mIntentFilter.match(resolver, intent, true,
                null));
        mIntentFilter.addAction(ACTION);
        assertEquals(IntentFilter.MATCH_CATEGORY_EMPTY + IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.match(resolver, intent, true, null));

        final Uri uri = Uri.parse(DATA_SCHEME + "://" + HOST + ":" + PORT);
        intent.setData(uri);
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(resolver, intent, true, null));
        mIntentFilter.addDataAuthority(HOST, String.valueOf(PORT));
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(resolver, intent, true, null));
        intent.setType(DATA_STATIC_TYPE);
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(resolver, intent, true, null));

        mIntentFilter.addDataType(DATA_STATIC_TYPE);

        assertEquals(IntentFilter.MATCH_CATEGORY_TYPE + IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.match(resolver, intent, true, null));
        assertEquals(IntentFilter.MATCH_CATEGORY_TYPE + IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.match(resolver, intent, false, null));
        intent.addCategory(CATEGORY);
        assertEquals(IntentFilter.NO_MATCH_CATEGORY, mIntentFilter.match(resolver, intent, true,
                null));
        mIntentFilter.addCategory(CATEGORY);
        assertEquals(IntentFilter.MATCH_CATEGORY_TYPE + IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.match(resolver, intent, true, null));

        intent.setDataAndType(uri, DATA_STATIC_TYPE);
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(resolver, intent, true, null));

    }

    @Test
    public void testMatchWithIntentData() throws MalformedMimeTypeException {
        Set<String> cat = new HashSet<String>();
        assertEquals(IntentFilter.NO_MATCH_ACTION, mIntentFilter.match(ACTION, null, null, null,
                null, null));
        mIntentFilter.addAction(ACTION);

        assertEquals(IntentFilter.MATCH_CATEGORY_EMPTY + IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.match(ACTION, null, null, null, null, null));
        assertEquals(IntentFilter.MATCH_CATEGORY_EMPTY + IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.match(ACTION, null, DATA_SCHEME, null, null, null));

        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.matchData(null, DATA_SCHEME, URI));

        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, URI, null, null));

        mIntentFilter.addDataScheme(DATA_SCHEME);
        assertEquals(IntentFilter.NO_MATCH_TYPE, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, URI, null, null));
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, DATA_STATIC_TYPE, "",
                URI, null, null));
        mIntentFilter.addDataType(DATA_STATIC_TYPE);

        assertEquals(IntentFilter.MATCH_CATEGORY_TYPE + IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                mIntentFilter.match(ACTION, DATA_STATIC_TYPE, DATA_SCHEME, URI, null, null));

        assertEquals(IntentFilter.NO_MATCH_TYPE, mIntentFilter.match(ACTION, null, DATA_SCHEME,
                URI, null, null));

        assertEquals(IntentFilter.NO_MATCH_TYPE, mIntentFilter.match(ACTION, null, DATA_SCHEME,
                URI, cat, null));

        cat.add(CATEGORY);
        assertEquals(IntentFilter.NO_MATCH_CATEGORY, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, URI, cat, null));
        cat = new HashSet<String>();
        mIntentFilter.addDataAuthority(HOST, String.valueOf(PORT));
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, null, DATA_SCHEME,
                URI, null, null));
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, URI, null, null));

        final Uri uri = Uri.parse(DATA_SCHEME + "://" + HOST + ":" + PORT);
        mIntentFilter.addDataPath(DATA_PATH, PatternMatcher.PATTERN_LITERAL);
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, uri, null, null));
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, URI, null, null));

        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, URI, cat, null));
        cat.add(CATEGORY);
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, URI, cat, null));
        mIntentFilter.addCategory(CATEGORY);
        assertEquals(IntentFilter.NO_MATCH_DATA, mIntentFilter.match(ACTION, DATA_STATIC_TYPE,
                DATA_SCHEME, URI, cat, null));
    }

    @Test
    public void testWriteToParcel() throws MalformedMimeTypeException {
        mIntentFilter.addAction(ACTION);
        mIntentFilter.addCategory(CATEGORY);
        mIntentFilter.addDataAuthority(HOST, String.valueOf(PORT));
        mIntentFilter.addDataPath(DATA_PATH, 1);
        mIntentFilter.addDataScheme(DATA_SCHEME);
        mIntentFilter.addDataType(DATA_STATIC_TYPE);
        mIntentFilter.addDynamicDataType(DATA_DYNAMIC_TYPE);
        mIntentFilter.addMimeGroup(MIME_GROUP);
        Parcel parcel = Parcel.obtain();
        mIntentFilter.writeToParcel(parcel, 1);
        parcel.setDataPosition(0);
        IntentFilter target = IntentFilter.CREATOR.createFromParcel(parcel);
        assertEquals(mIntentFilter.getAction(0), target.getAction(0));
        assertEquals(mIntentFilter.getCategory(0), target.getCategory(0));
        assertEquals(mIntentFilter.getDataAuthority(0).getHost(),
                target.getDataAuthority(0).getHost());
        assertEquals(mIntentFilter.getDataAuthority(0).getPort(),
                target.getDataAuthority(0).getPort());
        assertEquals(mIntentFilter.getDataPath(0).getPath(), target.getDataPath(0).getPath());
        assertEquals(mIntentFilter.getDataScheme(0), target.getDataScheme(0));
        assertEquals(mIntentFilter.getDataType(0), target.getDataType(0));
        assertEquals(mIntentFilter.getDataType(1), target.getDataType(1));
        assertEquals(mIntentFilter.countStaticDataTypes(), target.countStaticDataTypes());
        assertEquals(mIntentFilter.countDataTypes(), target.countDataTypes());
        assertEquals(mIntentFilter.getMimeGroup(0), target.getMimeGroup(0));
    }

    @Test
    @IgnoreUnderRavenwood
    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    public void testWriteToParcelWithRelRefGroup() throws MalformedMimeTypeException {
        UriRelativeFilterGroup relRefGroup = new UriRelativeFilterGroup(ACTION_ALLOW);
        relRefGroup.addUriRelativeFilter(new UriRelativeFilter(PATH, PATTERN_LITERAL, "/path"));
        relRefGroup.addUriRelativeFilter(new UriRelativeFilter(QUERY, PATTERN_SIMPLE_GLOB, "q*"));

        mIntentFilter.addAction(ACTION);
        mIntentFilter.addCategory(CATEGORY);
        mIntentFilter.addDataAuthority(HOST, String.valueOf(PORT));
        mIntentFilter.addDataPath(DATA_PATH, 1);
        mIntentFilter.addDataScheme(DATA_SCHEME);
        mIntentFilter.addDataType(DATA_STATIC_TYPE);
        mIntentFilter.addDynamicDataType(DATA_DYNAMIC_TYPE);
        mIntentFilter.addMimeGroup(MIME_GROUP);
        mIntentFilter.addUriRelativeFilterGroup(relRefGroup);
        Parcel parcel = Parcel.obtain();
        mIntentFilter.writeToParcel(parcel, 1);
        parcel.setDataPosition(0);
        IntentFilter target = IntentFilter.CREATOR.createFromParcel(parcel);
        assertEquals(mIntentFilter.getAction(0), target.getAction(0));
        assertEquals(mIntentFilter.getCategory(0), target.getCategory(0));
        assertEquals(mIntentFilter.getDataAuthority(0).getHost(),
                target.getDataAuthority(0).getHost());
        assertEquals(mIntentFilter.getDataAuthority(0).getPort(),
                target.getDataAuthority(0).getPort());
        assertEquals(mIntentFilter.getDataPath(0).getPath(), target.getDataPath(0).getPath());
        assertEquals(mIntentFilter.getDataScheme(0), target.getDataScheme(0));
        assertEquals(mIntentFilter.getDataType(0), target.getDataType(0));
        assertEquals(mIntentFilter.getDataType(1), target.getDataType(1));
        assertEquals(mIntentFilter.countStaticDataTypes(), target.countStaticDataTypes());
        assertEquals(mIntentFilter.countDataTypes(), target.countDataTypes());
        assertEquals(mIntentFilter.getMimeGroup(0), target.getMimeGroup(0));
        assertEquals(mIntentFilter.countUriRelativeFilterGroups(),
                target.countUriRelativeFilterGroups());
        assertEquals(mIntentFilter.getUriRelativeFilterGroup(0).getUriRelativeFilters().size(),
                target.getUriRelativeFilterGroup(0).getUriRelativeFilters().size());
        Iterator<UriRelativeFilter> it =
                target.getUriRelativeFilterGroup(0).getUriRelativeFilters().iterator();
        assertTrue(relRefGroup.getUriRelativeFilters().contains(it.next()));
        assertTrue(relRefGroup.getUriRelativeFilters().contains(it.next()));
        assertFalse(it.hasNext());
    }

    @Test
    public void testAddDataType() throws MalformedMimeTypeException {
        try {
            mIntentFilter.addDataType("test");
            fail("should throw MalformedMimeTypeException");
        } catch (MalformedMimeTypeException e) {
            // expected
        }

        mIntentFilter.addDataType(DATA_STATIC_TYPE);
        assertEquals(DATA_STATIC_TYPE, mIntentFilter.getDataType(0));
    }

    private static class Match extends IntentFilter {
        Match() {
        }

        Match(String[] actions, String[] categories, String[] mimeTypes, String[] schemes,
                String[] authorities, String[] ports) {
            if (actions != null) {
                addActions(actions);
            }
            if (categories != null) {
                for (int i = 0; i < categories.length; i++) {
                    addCategory(categories[i]);
                }
            }
            if (mimeTypes != null) {
                for (int i = 0; i < mimeTypes.length; i++) {
                    try {
                        addDataType(mimeTypes[i]);
                    } catch (IntentFilter.MalformedMimeTypeException e) {
                        throw new RuntimeException("Bad mime type", e);
                    }
                }
            }
            if (schemes != null) {
                for (int i = 0; i < schemes.length; i++) {
                    addDataScheme(schemes[i]);
                }
            }
            if (authorities != null) {
                for (int i = 0; i < authorities.length; i++) {
                    addDataAuthority(authorities[i], ports != null ? ports[i] : null);
                }
            }
        }

        Match(String[] actions, String[] categories, String[] mimeTypes, String[] schemes,
                String[] authorities, String[] ports, String[] paths, int[] pathTypes) {
            this(actions, categories, mimeTypes, schemes, authorities, ports);
            if (paths != null) {
                for (int i = 0; i < paths.length; i++) {
                    addDataPath(paths[i], pathTypes[i]);
                }
            }
        }

        Match(String[] actions, String[] categories, String[] mimeTypes, String[] schemes,
                String[] authorities, String[] ports, String[] paths, int[] pathTypes,
                String[] ssps, int[] sspTypes) {
            this(actions, categories, mimeTypes, schemes, authorities, ports, paths, pathTypes);
            if (ssps != null) {
                for (int i = 0; i < ssps.length; i++) {
                    addDataSchemeSpecificPart(ssps[i], sspTypes[i]);
                }
            }
        }

        Match addDynamicMimeTypes(String[] dynamicMimeTypes) {
            for (int i = 0; i < dynamicMimeTypes.length; i++) {
                try {
                    addDynamicDataType(dynamicMimeTypes[i]);
                } catch (IntentFilter.MalformedMimeTypeException e) {
                    throw new RuntimeException("Bad mime type", e);
                }
            }
            return this;
        }

        Match addMimeGroups(String[] mimeGroups) {
            for (int i = 0; i < mimeGroups.length; i++) {
                addMimeGroup(mimeGroups[i]);
            }
            return this;
        }

        Match addMimeTypes(String[] mimeTypes) {
            for (int i = 0; i < mimeTypes.length; i++) {
                try {
                    addDataType(mimeTypes[i]);
                } catch (IntentFilter.MalformedMimeTypeException e) {
                    throw new RuntimeException("Bad mime type", e);
                }
            }
            return this;
        }

        Match addActions(String[] actions) {
            for (int i = 0; i < actions.length; i++) {
                addAction(actions[i]);
            }
            return this;
        }
    }

    private static class MatchCondition {
        public final int result;
        public final String action;
        public final String mimeType;
        public final Uri data;
        public final String[] categories;
        public final boolean wildcardSupported;
        public final Collection<String> ignoredActions;

        public static MatchCondition data(int result, String data) {
            return new MatchCondition(result, null, null, null, data);
        }
        public static MatchCondition data(int result, String data, boolean wildcardSupported) {
            return new MatchCondition(result, null, null, null, data, wildcardSupported, null);
        }
        public static MatchCondition data(int result, String data, boolean wildcardSupported,
                Collection<String> ignoredActions) {
            return new MatchCondition(result, null, null, null, data, wildcardSupported,
                    ignoredActions);
        }
        MatchCondition(int result, String action, String[] categories, String mimeType,
                String data) {
            this(result, action, categories, mimeType, data, false, null);
        }
        MatchCondition(int result, String action, String[] categories, String mimeType,
                String data, boolean wildcardSupported) {
            this(result, action, categories, mimeType, data, wildcardSupported, null);
        }
        MatchCondition(int result, String action, String[] categories, String mimeType,
                String data, boolean wildcardSupported, Collection<String> ignoredActions) {
            this.result = result;
            this.action = action;
            this.mimeType = mimeType;
            this.data = data != null ? Uri.parse(data) : null;
            this.categories = categories;
            this.wildcardSupported = wildcardSupported;
            this.ignoredActions = ignoredActions;
        }
    }

    private static void checkMatches(IntentFilter[] filters, MatchCondition... results) {
        for (IntentFilter filter : filters) {
            checkMatches(filter, results);
        }
    }

    private static void checkMatches(IntentFilter filter, MatchCondition... results) {
        for (int i = 0; i < results.length; i++) {
            MatchCondition mc = results[i];
            HashSet<String> categories = null;
            if (mc.categories != null) {
                for (int j = 0; j < mc.categories.length; j++) {
                    if (categories == null) {
                        categories = new HashSet<String>();
                    }
                    categories.add(mc.categories[j]);
                }
            }
            int result = filter.match(mc.action, mc.mimeType, mc.data != null ? mc.data.getScheme()
                    : null, mc.data, categories, "test", mc.wildcardSupported, mc.ignoredActions);
            if ((result & IntentFilter.MATCH_CATEGORY_MASK) !=
                    (mc.result & IntentFilter.MATCH_CATEGORY_MASK)) {
                StringBuilder msg = new StringBuilder();
                msg.append("Error matching against IntentFilter:\n");
                filter.dump(new StringBuilderPrinter(msg), "    ");
                msg.append("Match action: ");
                msg.append(mc.action);
                msg.append("\nMatch mimeType: ");
                msg.append(mc.mimeType);
                msg.append("\nMatch data: ");
                msg.append(mc.data);
                msg.append("\nMatch categories: ");
                if (mc.categories != null) {
                    for (int j = 0; j < mc.categories.length; j++) {
                        if (j > 0) {
                            msg.append(", ");
                        }
                        msg.append(mc.categories[j]);
                    }
                }
                msg.append("\nExpected result: 0x");
                msg.append(Integer.toHexString(mc.result));
                msg.append(", got result: 0x");
                msg.append(Integer.toHexString(result));
                throw new RuntimeException(msg.toString());
            }
        }
    }

    @Test
    public void testPaths() throws Exception {
        IntentFilter filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null,
                new String[]{"/literal1", "/2literal"},
                new int[]{PATTERN_LITERAL, PATTERN_LITERAL});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/literal1"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/2literal"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/literal"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/literal12"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null,
                new String[]{"/literal1", "/2literal"}, new int[]{PATTERN_PREFIX, PATTERN_PREFIX});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/literal1"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/2literal"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/literal"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/literal12"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null,
                new String[]{"literal1", "2literal"}, new int[]{PATTERN_SUFFIX, PATTERN_SUFFIX});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/aliteral1"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/2literal"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/literal"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/literal1"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/2literal1"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/literal1a"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{"/.*"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/literal1"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{".*"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/literal1"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{"/a1*b"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/ab"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a1b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a11b"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a2b"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a1bc"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{"/a1*"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a1"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/ab"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a11"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a1b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a11"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a2"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{"/a\\.*b"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/ab"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a.b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a..b"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a2b"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a.bc"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{"/a.*b"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/ab"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a.b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a.1b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a2b"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a.bc"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{"/a.*"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/ab"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a.b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a.1b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a2b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a.bc"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{"/a.\\*b"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/ab"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a.*b"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a1*b"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a2b"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a.bc"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/"));
        filter = new Match(null, null, null,
                new String[]{"scheme"}, new String[]{"authority"}, null, new String[]{"/a.\\*"},
                new int[]{PATTERN_SIMPLE_GLOB});
        checkMatches(filter,
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, null),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/ab"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a.*"),
                MatchCondition.data(
                        IntentFilter.MATCH_CATEGORY_PATH, "scheme://authority/a1*"),
                MatchCondition.data(
                        IntentFilter.NO_MATCH_DATA, "scheme://authority/a1b"));
    }

    @Test
    public void testDump() throws MalformedMimeTypeException {
        TestPrinter printer = new TestPrinter();
        String prefix = "TestIntentFilter";
        IntentFilter filter = new IntentFilter(ACTION, DATA_STATIC_TYPE);
        filter.dump(printer, prefix);
        assertTrue(printer.isPrintlnCalled);
    }

    private class TestPrinter implements Printer {
        public boolean isPrintlnCalled;

        public void println(String x) {
            isPrintlnCalled = true;
        }
    }

    @Test
    public void testAsPredicate() throws Exception {
        final Predicate<Intent> pred = new IntentFilter(ACTION).asPredicate();

        assertTrue(pred.test(new Intent(ACTION)));
        assertFalse(pred.test(new Intent(CATEGORY)));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ContentResolver.class)
    public void testAsPredicateWithTypeResolution() throws Exception {
        final ContentResolver resolver = mContext.getContentResolver();
        final Predicate<Intent> pred = new IntentFilter(ACTION, DATA_STATIC_TYPE)
                .asPredicateWithTypeResolution(resolver);

        assertTrue(pred.test(new Intent(ACTION).setDataAndType(URI, DATA_STATIC_TYPE)));
        assertFalse(pred.test(new Intent(ACTION).setDataAndType(URI, DATA_DYNAMIC_TYPE)));
    }
}
