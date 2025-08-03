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

package android.uirendering.cts.testclasses;

import android.graphics.Color;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DrawingOrderTest extends ActivityTestBase {

    @Test
    public void testDefaultDrawOrder() {
        createTest().addLayout(R.layout.draw_order, null)
                .runWithVerifier(new ColorVerifier(Color.GREEN));
    }

    @Test
    public void testTranslationZOrder() {
        createTest().addLayout(R.layout.draw_order, (ViewInitializer) view -> {
            view.findViewById(R.id.blueview).setTranslationZ(4);
            view.findViewById(R.id.greenview).setTranslationZ(0);
        }).runWithVerifier(new ColorVerifier(Color.BLUE));
    }
}
