/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.view.textclassifier.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.LocaleList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLinks;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextLinksTest {

    private static final float EPSILON = 0.000001f;
    private static final String BUNDLE_KEY = "key";
    private static final String BUNDLE_VALUE = "value";
    private static final Bundle BUNDLE = new Bundle();
    static {
        BUNDLE.putString(BUNDLE_KEY, BUNDLE_VALUE);
    }

    private static final double ACCEPTED_DELTA = 0.0000001;
    private static final String TEXT = "abcdefghijklmnopqrstuvwxyz";
    private static final int START = 5;
    private static final int END = 20;
    private static final int ANOTHER_START = 22;
    private static final int ANOTHER_END = 24;
    private static final LocaleList LOCALES = LocaleList.forLanguageTags("fr,en,de,es");

    @Test
    public void testTextLinks_defaultValues() {
        final TextLinks textLinks = new TextLinks.Builder(TEXT).build();

        assertEquals(TEXT, textLinks.getText());
        assertTrue(textLinks.getExtras().isEmpty());
        assertTrue(textLinks.getLinks().isEmpty());
    }

    @Test
    public void testTextLinks_full() {
        final TextLinks textLinks = new TextLinks.Builder(TEXT)
                .setExtras(BUNDLE)
                .addLink(START, END, Collections.singletonMap(TextClassifier.TYPE_ADDRESS, 1.0f))
                .addLink(START, END, Collections.singletonMap(TextClassifier.TYPE_PHONE, 1.0f),
                        BUNDLE)
                .build();

        assertEquals(TEXT, textLinks.getText());
        assertEquals(BUNDLE_VALUE, textLinks.getExtras().getString(BUNDLE_KEY));
        assertEquals(2, textLinks.getLinks().size());

        final List<TextLinks.TextLink> resultList = new ArrayList<>(textLinks.getLinks());
        final TextLinks.TextLink textLinkNoExtra = resultList.get(0);
        assertEquals(TextClassifier.TYPE_ADDRESS, textLinkNoExtra.getEntity(0));
        assertEquals(1.0f, textLinkNoExtra.getConfidenceScore(TextClassifier.TYPE_ADDRESS),
                EPSILON);
        assertEquals(Bundle.EMPTY, textLinkNoExtra.getExtras());

        final TextLinks.TextLink textLinkHasExtra = resultList.get(1);
        assertEquals(TextClassifier.TYPE_PHONE, textLinkHasExtra.getEntity(0));
        assertEquals(1.0f, textLinkHasExtra.getConfidenceScore(TextClassifier.TYPE_PHONE),
                EPSILON);
        assertEquals(BUNDLE_VALUE, textLinkHasExtra.getExtras().getString(BUNDLE_KEY));
    }

    @Test
    public void testTextLinks_clearTextLinks() {
        final TextLinks textLinks = new TextLinks.Builder(TEXT)
                .setExtras(BUNDLE)
                .addLink(START, END, Collections.singletonMap(TextClassifier.TYPE_ADDRESS, 1.0f))
                .clearTextLinks()
                .build();
        assertEquals(0, textLinks.getLinks().size());
    }

    @Test
    public void testTextLinks_apply() {
        final SpannableString spannableString = SpannableString.valueOf(TEXT);
        final TextLinks textLinks = new TextLinks.Builder(TEXT)
                .addLink(START, END, Collections.singletonMap(TextClassifier.TYPE_ADDRESS, 1.0f))
                .addLink(ANOTHER_START, ANOTHER_END,
                        ImmutableMap.of(TextClassifier.TYPE_PHONE, 1.0f,
                                TextClassifier.TYPE_ADDRESS, 0.5f))
                .build();

        final int status = textLinks.apply(
                spannableString, TextLinks.APPLY_STRATEGY_IGNORE, null);
        final TextLinks.TextLinkSpan[] textLinkSpans = spannableString.getSpans(0,
                spannableString.length() - 1,
                TextLinks.TextLinkSpan.class);

        assertEquals(TextLinks.STATUS_LINKS_APPLIED, status);
        assertEquals(2, textLinkSpans.length);

        final TextLinks.TextLink textLink = textLinkSpans[0].getTextLink();
        final TextLinks.TextLink anotherTextLink = textLinkSpans[1].getTextLink();

        assertEquals(START, textLink.getStart());
        assertEquals(END, textLink.getEnd());
        assertEquals(1, textLink.getEntityCount());
        assertEquals(TextClassifier.TYPE_ADDRESS, textLink.getEntity(0));
        assertEquals(1.0f, textLink.getConfidenceScore(TextClassifier.TYPE_ADDRESS),
                ACCEPTED_DELTA);
        assertEquals(ANOTHER_START, anotherTextLink.getStart());
        assertEquals(ANOTHER_END, anotherTextLink.getEnd());
        assertEquals(2, anotherTextLink.getEntityCount());
        assertEquals(TextClassifier.TYPE_PHONE, anotherTextLink.getEntity(0));
        assertEquals(1.0f, anotherTextLink.getConfidenceScore(TextClassifier.TYPE_PHONE),
                ACCEPTED_DELTA);
        assertEquals(TextClassifier.TYPE_ADDRESS, anotherTextLink.getEntity(1));
        assertEquals(0.5f, anotherTextLink.getConfidenceScore(TextClassifier.TYPE_ADDRESS),
                ACCEPTED_DELTA);
    }

    @Test
    public void testTextLinks_applyStrategyReplace() {
        final SpannableString spannableString = SpannableString.valueOf(TEXT);
        final URLSpan urlSpan = new URLSpan("http://www.google.com");
        spannableString.setSpan(urlSpan, START, END, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final TextLinks textLinks = new TextLinks.Builder(TEXT)
                .addLink(START, END, Collections.singletonMap(TextClassifier.TYPE_ADDRESS, 1.0f))
                .build();

        final int status = textLinks.apply(
                spannableString, TextLinks.APPLY_STRATEGY_REPLACE, null);
        final TextLinks.TextLinkSpan[] textLinkSpans = spannableString.getSpans(0,
                spannableString.length() - 1,
                TextLinks.TextLinkSpan.class);
        final URLSpan[] urlSpans = spannableString.getSpans(0, spannableString.length() - 1,
                URLSpan.class);

        assertEquals(TextLinks.STATUS_LINKS_APPLIED, status);
        assertEquals(1, textLinkSpans.length);
        assertEquals(0, urlSpans.length);
    }

    @Test
    public void testTextLinks_applyStrategyIgnore() {
        final SpannableString spannableString = SpannableString.valueOf(TEXT);
        final URLSpan urlSpan = new URLSpan("http://www.google.com");
        spannableString.setSpan(urlSpan, START, END, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final TextLinks textLinks = new TextLinks.Builder(TEXT)
                .addLink(START, END, Collections.singletonMap(TextClassifier.TYPE_ADDRESS, 1.0f))
                .build();

        final int status = textLinks.apply(
                spannableString, TextLinks.APPLY_STRATEGY_IGNORE, null);
        final TextLinks.TextLinkSpan[] textLinkSpans = spannableString.getSpans(0,
                spannableString.length() - 1,
                TextLinks.TextLinkSpan.class);
        final URLSpan[] urlSpans = spannableString.getSpans(0, spannableString.length() - 1,
                URLSpan.class);

        assertEquals(TextLinks.STATUS_NO_LINKS_APPLIED, status);
        assertEquals(0, textLinkSpans.length);
        assertEquals(1, urlSpans.length);
    }

    @Test
    public void testTextLinks_applyWithCustomSpanFactory() {
        final class CustomTextLinkSpan extends TextLinks.TextLinkSpan {
            private CustomTextLinkSpan(TextLinks.TextLink textLink) {
                super(textLink);
            }
        }
        final SpannableString spannableString = SpannableString.valueOf(TEXT);
        final TextLinks textLinks = new TextLinks.Builder(TEXT)
                .addLink(START, END, Collections.singletonMap(TextClassifier.TYPE_ADDRESS, 1.0f))
                .build();

        final int status = textLinks.apply(
                spannableString, TextLinks.APPLY_STRATEGY_IGNORE, CustomTextLinkSpan::new);
        final CustomTextLinkSpan[] customTextLinkSpans = spannableString.getSpans(0,
                spannableString.length() - 1,
                CustomTextLinkSpan.class);

        assertEquals(TextLinks.STATUS_LINKS_APPLIED, status);
        assertEquals(1, customTextLinkSpans.length);

        final TextLinks.TextLink textLink = customTextLinkSpans[0].getTextLink();

        assertEquals(START, textLink.getStart());
        assertEquals(END, textLink.getEnd());
        assertEquals(1, textLink.getEntityCount());
        assertEquals(TextClassifier.TYPE_ADDRESS, textLink.getEntity(0));
        assertEquals(1.0f, textLink.getConfidenceScore(TextClassifier.TYPE_ADDRESS),
                ACCEPTED_DELTA);
    }

    @Test
    public void testTextLinksRequest_defaultValues() {
        final TextLinks.Request request = new TextLinks.Request.Builder(TEXT).build();

        assertEquals(TEXT, request.getText());
        assertNull(request.getDefaultLocales());
        assertTrue(request.getExtras().isEmpty());
        assertNull(request.getEntityConfig());
        assertNull(request.getCallingPackageName());
    }

    @Test
    public void testTextLinksRequest_full() {
        final ZonedDateTime referenceTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(1000L),
                ZoneId.of("UTC"));
        final TextLinks.Request request = new TextLinks.Request.Builder(TEXT)
                .setDefaultLocales(LOCALES)
                .setExtras(BUNDLE)
                .setEntityConfig(TextClassifier.EntityConfig.createWithHints(
                        Collections.singletonList(TextClassifier.HINT_TEXT_IS_EDITABLE)))
                .setReferenceTime(referenceTime)
                .build();

        assertEquals(TEXT, request.getText());
        assertEquals(LOCALES, request.getDefaultLocales());
        assertEquals(BUNDLE_VALUE, request.getExtras().getString(BUNDLE_KEY));
        assertEquals(1, request.getEntityConfig().getHints().size());
        assertEquals(
                TextClassifier.HINT_TEXT_IS_EDITABLE,
                request.getEntityConfig().getHints().iterator().next());
        assertEquals(referenceTime, request.getReferenceTime());
        assertNull(request.getCallingPackageName());
    }

}
