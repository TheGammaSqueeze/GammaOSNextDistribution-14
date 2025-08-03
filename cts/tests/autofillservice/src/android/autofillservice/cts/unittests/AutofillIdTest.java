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

package android.autofillservice.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.view.View;
import android.view.autofill.AutofillId;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(MockitoJUnitRunner.class)
public class AutofillIdTest {

    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private View mHost;

    @Before
    public void setup() {
        mHost = new View(mContext);
        mHost.setAutofillId(new AutofillId(1));
    }

    @Test
    public void testCreateAutofillId() {
        AutofillId vid = AutofillId.create(mHost, /* virtualId= */ 2);

        assertThat(vid).isEqualTo(new AutofillId(/* hostId= */ 1, /* virtualId= */ 2));
    }

    @Test
    public void testCreateAutofillId_invalidHost() {
        assertThrows(NullPointerException.class,
                () -> AutofillId.create(/* hostId= */ null, /* virtualId= */ 2));
    }
}
