/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.inputmethodservice.cts.devicetest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.compat.CompatChanges;
import android.os.LocaleList;
import android.util.Size;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.inline.InlinePresentationSpec;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Device side tests for the {@link android.app.compat.CompatChanges} API changes in
 * {@link android.view.inputmethod.InlineSuggestionsRequest}.
 *
 * <p>See also {@link android.inputmethodservice.cts.hostside.InlineSuggestionsRequestHostTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class InlineSuggestionsRequestDeviceTest {

    /**
     * This test case only concerns the {@link InlineSuggestionsRequest#getSupportedLocales()}
     * API's behavior change from change id 169273070L. The other tests for the class are in
     * the regular CTS tests.
     */
    @Test
    public void imeAutofillDefaultSupportedLocalesIsEmpty_changeEnabled() {
        assertTrue(CompatChanges.isChangeEnabled(169273070L));
        InlineSuggestionsRequest request = createInlineSuggestionsRequestWithoutLocale();
        assertEquals(LocaleList.getEmptyLocaleList(), request.getSupportedLocales());
    }

    @Test
    public void imeAutofillDefaultSupportedLocalesIsEmpty_changeDisabled() {
        assertFalse(CompatChanges.isChangeEnabled(169273070L));
        InlineSuggestionsRequest request = createInlineSuggestionsRequestWithoutLocale();
        assertEquals(LocaleList.getDefault(), request.getSupportedLocales());
    }

    private InlineSuggestionsRequest createInlineSuggestionsRequestWithoutLocale() {
        return new InlineSuggestionsRequest.Builder(new ArrayList<>())
                .addInlinePresentationSpecs(
                        new InlinePresentationSpec.Builder(new Size(100, 100),
                                new Size(400, 100)).build())
                .setMaxSuggestionCount(3).build();
    }
}
