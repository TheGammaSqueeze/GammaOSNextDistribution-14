/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.webkit.cts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.webkit.DateSorter;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DateSorterTest extends SharedWebViewTest {
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = getTestEnvironment().getContext();
    }

    @Override
    protected SharedWebViewTestEnvironment createTestEnvironment() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return new SharedWebViewTestEnvironment.Builder()
            .setContext(context)
            .setHostAppInvoker(SharedWebViewTestEnvironment.createHostAppInvoker(context))
            .build();
    }

    @Test
    public void testConstructor() {
        new DateSorter(mContext);
    }

    @Test
    public void testConstants() {
        // according to DateSorter javadoc
        assertThat("DAY_COUNT should be >= 3",
                DateSorter.DAY_COUNT,
                greaterThanOrEqualTo(3));
    }

    @Test
    public void testGetLabel() {
        DateSorter dateSorter = new DateSorter(mContext);
        HashSet<String> set = new HashSet<String>();
        for (int i = 0; i < DateSorter.DAY_COUNT; i++) {
            String label = dateSorter.getLabel(i);
            assertNotNull(label);
            assertThat(label.length(), greaterThan(0));
            // label must be unique
            assertFalse("Set should not contain label", set.contains(label));
            set.add(label);
            // cannot assert actual label contents, since resources are not public
        }
    }

    @Test
    public void testGetIndex() {
        DateSorter dateSorter = new DateSorter(mContext);

        for (int i = 0; i < DateSorter.DAY_COUNT; i++) {
            long boundary = dateSorter.getBoundary(i);
            int nextIndex = i + 1;

            assertEquals(i, dateSorter.getIndex(boundary + 1));
            if (i == DateSorter.DAY_COUNT - 1) break;
            assertEquals(nextIndex, dateSorter.getIndex(boundary));
            assertEquals(nextIndex, dateSorter.getIndex(boundary-1));
        }
    }

    @Test
    public void testGetBoundary() {
        DateSorter dateSorter = new DateSorter(mContext);

        for (int i = 0; i < DateSorter.DAY_COUNT - 1; i++) {
            assertThat(dateSorter.getBoundary(i), greaterThan(dateSorter.getBoundary(i+1)));
        }
    }
}
