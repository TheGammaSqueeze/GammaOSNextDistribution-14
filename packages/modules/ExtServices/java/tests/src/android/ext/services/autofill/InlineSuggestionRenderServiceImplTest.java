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

package android.ext.services.autofill;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.testng.Assert.assertThrows;

import android.app.PendingIntent;
import android.app.slice.Slice;
import android.app.slice.SliceSpec;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.service.autofill.InlinePresentation;
import android.util.Size;
import android.view.View;
import android.widget.inline.InlinePresentationSpec;

import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.UiVersions.StylesBuilder;
import androidx.autofill.inline.v1.InlineSuggestionUi;
import androidx.autofill.inline.v1.InlineSuggestionUi.Content;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.mockito.Mockito;

/**
 * Contains the base tests that does not rely on the specific algorithm implementation.
 */
public class InlineSuggestionRenderServiceImplTest {
    // TODO(b/187376476): improve naming and add more tests for Slice with correct version but
    //  no PendingIntent.
    // Different inline presentation use cases.
    private static final InlinePresentation INLINE_PRESENTATION_WITH_STYLE_AND_INTENT =
            new InlinePresentation(createAttributionIntentSlice(),
                    createSpecWithStyle(), true);
    private static final InlinePresentation INLINE_PRESENTATION_WITH_STYLE_NO_INTENT  =
            new InlinePresentation(createNoAttributionIntentSlice(),
                    createSpecWithStyle(), true);
    private static final InlinePresentation INLINE_PRESENTATION_NO_STYLE_NO_INTENT  =
            new InlinePresentation(createNoAttributionIntentSlice(),
                    createSpecWithoutStyle(), true);
    private static final InlinePresentation INLINE_PRESENTATION_NO_STYLE_WITH_INTENT  =
            new InlinePresentation(createAttributionIntentSlice(),
                    createSpecWithoutStyle(), true);

    private final InlineSuggestionRenderServiceImpl mService =
            new InlineSuggestionRenderServiceImpl();

    @Test
    public void testOnGetInlineSuggestionsRendererInfo() {
        assertThat(mService.onGetInlineSuggestionsRendererInfo()).isNotNull();
    }

    @Test
    public void testOnRenderSuggestion_nullPresentation() {
        assertThrows(NullPointerException.class,
                () -> mService.onRenderSuggestion(/* presentation */ null, 0, 0));
    }

    @Test
    public void testOnRenderSuggestion_hasStyleHasAttributionIntentInlinePresentation() {
        InlineSuggestionRenderServiceImpl spyService = Mockito.spy(mService);
        spyService.attachBaseContext(ApplicationProvider.getApplicationContext());

        View view = spyService.onRenderSuggestion(
                INLINE_PRESENTATION_WITH_STYLE_AND_INTENT, /* width= */ 0,  /* height= */ 0);
        assertThat(view).isNotNull();

        view.performLongClick();
        // TODO(b/187376476): verify startIntentSender() parameter value.
        Mockito.verify(spyService, Mockito.times(1)).startIntentSender(any(IntentSender.class));
    }

    @Test
    public void testOnRenderSuggestion_noStyleNoAttributionIntentInlinePresentation() {
        assertThat(mService.onRenderSuggestion(INLINE_PRESENTATION_NO_STYLE_NO_INTENT, /* width= */
                0,  /* height= */0)).isNull();
    }

    @Test
    public void testOnRenderSuggestion_noStyleHasAttributionIntentInlinePresentation() {
        assertThat(
                mService.onRenderSuggestion(INLINE_PRESENTATION_NO_STYLE_WITH_INTENT, /* width= */
                        0,  /* height= */0)).isNull();
    }

    @Test
    public void testOnRenderSuggestion_hasStyleNoAttributionIntentInlinePresentation() {
        assertThat(
                mService.onRenderSuggestion(INLINE_PRESENTATION_WITH_STYLE_NO_INTENT, /* width= */
                        0,  /* height= */ 0)).isNull();
    }

    private static InlinePresentationSpec createSpecWithoutStyle() {
        return createInlinePresentationSpec(null);
    }

    private static InlinePresentationSpec createSpecWithStyle() {
        StylesBuilder stylesBuilder = UiVersions.newStylesBuilder();
        stylesBuilder.addStyle(InlineSuggestionUi.newStyleBuilder().build());
        return createInlinePresentationSpec(stylesBuilder.build());
    }

    private static InlinePresentationSpec createInlinePresentationSpec(Bundle styles) {
        InlinePresentationSpec.Builder specBuilder = new InlinePresentationSpec.Builder(
                new Size(100, 100), new Size(400, 100));
        if (styles != null) {
            specBuilder.setStyle(styles);
        }
        return specBuilder.build();
    }

    private static Slice createNoAttributionIntentSlice() {
        return new Slice.Builder(Uri.parse("testuri"), new SliceSpec("type", 1)).build();
    }

    private static Slice createAttributionIntentSlice() {
        PendingIntent attribution = createAttribution();
        Content.Builder builder = InlineSuggestionUi.newContentBuilder(attribution);
        builder.setTitle("title").setSubtitle("subtitle");
        return builder.build().getSlice();
    }

    private static PendingIntent createAttribution() {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                ApplicationProvider.getApplicationContext(), 0,
                new Intent()
                        .setPackage(ApplicationProvider.getApplicationContext().getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        return pendingIntent;
    }
}
