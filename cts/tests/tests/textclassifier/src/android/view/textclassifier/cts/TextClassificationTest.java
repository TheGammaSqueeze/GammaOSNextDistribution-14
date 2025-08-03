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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextClassificationTest {
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
    private static final String ID = "id123";
    private static final LocaleList LOCALES = LocaleList.forLanguageTags("fr,en,de,es");

    @Test
    public void testTextClassification() {
        final float addressScore = 0.1f;
        final float emailScore = 0.9f;
        final PendingIntent intent1 = PendingIntent.getActivity(
                InstrumentationRegistry.getTargetContext(),
                0,
                new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        final String label1 = "label1";
        final String description1 = "description1";
        final Icon icon1 = generateTestIcon(16, 16, Color.RED);
        final PendingIntent intent2 = PendingIntent.getActivity(
                InstrumentationRegistry.getTargetContext(),
                0,
                new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        final String label2 = "label2";
        final String description2 = "description2";
        final Icon icon2 = generateTestIcon(16, 16, Color.GREEN);

        final TextClassification classification = new TextClassification.Builder()
                .setText(TEXT)
                .setEntityType(TextClassifier.TYPE_ADDRESS, addressScore)
                .setEntityType(TextClassifier.TYPE_EMAIL, emailScore)
                .addAction(new RemoteAction(icon1, label1, description1, intent1))
                .addAction(new RemoteAction(icon2, label2, description2, intent2))
                .setId(ID)
                .setExtras(BUNDLE)
                .build();

        assertEquals(TEXT, classification.getText());
        assertEquals(2, classification.getEntityCount());
        assertEquals(TextClassifier.TYPE_EMAIL, classification.getEntity(0));
        assertEquals(TextClassifier.TYPE_ADDRESS, classification.getEntity(1));
        assertEquals(addressScore, classification.getConfidenceScore(TextClassifier.TYPE_ADDRESS),
                ACCEPTED_DELTA);
        assertEquals(emailScore, classification.getConfidenceScore(TextClassifier.TYPE_EMAIL),
                ACCEPTED_DELTA);
        assertEquals(0, classification.getConfidenceScore("random_type"), ACCEPTED_DELTA);

        // Legacy API
        assertNull(classification.getIntent());
        assertNull(classification.getLabel());
        assertNull(classification.getIcon());
        assertNull(classification.getOnClickListener());

        assertEquals(2, classification.getActions().size());
        assertEquals(label1, classification.getActions().get(0).getTitle());
        assertEquals(description1, classification.getActions().get(0).getContentDescription());
        assertEquals(intent1, classification.getActions().get(0).getActionIntent());
        assertNotNull(classification.getActions().get(0).getIcon());
        assertEquals(label2, classification.getActions().get(1).getTitle());
        assertEquals(description2, classification.getActions().get(1).getContentDescription());
        assertEquals(intent2, classification.getActions().get(1).getActionIntent());
        assertNotNull(classification.getActions().get(1).getIcon());
        assertEquals(ID, classification.getId());
        assertEquals(BUNDLE_VALUE, classification.getExtras().getString(BUNDLE_KEY));
    }

    @Test
    public void testTextClassificationLegacy() {
        final float addressScore = 0.1f;
        final float emailScore = 0.9f;
        final Intent intent = new Intent();
        final String label = "label";
        final Drawable icon = new ColorDrawable(Color.RED);
        final View.OnClickListener onClick = v -> {
        };

        final TextClassification classification = new TextClassification.Builder()
                .setText(TEXT)
                .setEntityType(TextClassifier.TYPE_ADDRESS, addressScore)
                .setEntityType(TextClassifier.TYPE_EMAIL, emailScore)
                .setIntent(intent)
                .setLabel(label)
                .setIcon(icon)
                .setOnClickListener(onClick)
                .setId(ID)
                .build();

        assertEquals(TEXT, classification.getText());
        assertEquals(2, classification.getEntityCount());
        assertEquals(TextClassifier.TYPE_EMAIL, classification.getEntity(0));
        assertEquals(TextClassifier.TYPE_ADDRESS, classification.getEntity(1));
        assertEquals(addressScore, classification.getConfidenceScore(TextClassifier.TYPE_ADDRESS),
                ACCEPTED_DELTA);
        assertEquals(emailScore, classification.getConfidenceScore(TextClassifier.TYPE_EMAIL),
                ACCEPTED_DELTA);
        assertEquals(0, classification.getConfidenceScore("random_type"), ACCEPTED_DELTA);

        assertEquals(intent, classification.getIntent());
        assertEquals(label, classification.getLabel());
        assertEquals(icon, classification.getIcon());
        assertEquals(onClick, classification.getOnClickListener());
        assertEquals(ID, classification.getId());
    }

    @Test
    public void testTextClassification_defaultValues() {
        final TextClassification classification = new TextClassification.Builder().build();

        assertEquals(null, classification.getText());
        assertEquals(0, classification.getEntityCount());
        assertEquals(null, classification.getIntent());
        assertEquals(null, classification.getLabel());
        assertEquals(null, classification.getIcon());
        assertEquals(null, classification.getOnClickListener());
        assertEquals(0, classification.getActions().size());
        assertNull(classification.getId());
        assertTrue(classification.getExtras().isEmpty());
    }

    @Test
    public void testTextClassificationRequest() {
        final ZonedDateTime referenceTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(1000L),
                ZoneId.of("UTC"));
        final TextClassification.Request request =
                new TextClassification.Request.Builder(TEXT, START, END)
                        .setDefaultLocales(LOCALES)
                        .setReferenceTime(referenceTime)
                        .setExtras(BUNDLE)
                        .build();

        assertEquals(LOCALES, request.getDefaultLocales());
        assertEquals(BUNDLE_VALUE, request.getExtras().getString(BUNDLE_KEY));
        assertEquals(referenceTime, request.getReferenceTime());
    }

    @Test
    public void testTextClassificationRequest_nullValues() {
        final TextClassification.Request request =
                new TextClassification.Request.Builder(TEXT, START, END)
                        .setDefaultLocales(null)
                        .build();

        assertNull(request.getDefaultLocales());
        assertTrue(request.getExtras().isEmpty());
    }

    @Test
    public void testTextClassificationRequest_defaultValues() {
        final TextClassification.Request request =
                new TextClassification.Request.Builder(TEXT, START, END).build();
        assertNull(request.getDefaultLocales());
        assertTrue(request.getExtras().isEmpty());
    }

    /** Helper to generate Icons for testing. */
    private static Icon generateTestIcon(int width, int height, int colorValue) {
        final int numPixels = width * height;
        final int[] colors = new int[numPixels];
        for (int i = 0; i < numPixels; ++i) {
            colors[i] = colorValue;
        }
        final Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
        return Icon.createWithBitmap(bitmap);
    }
}
