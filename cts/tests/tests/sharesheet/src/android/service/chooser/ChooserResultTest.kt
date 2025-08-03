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

package android.service.chooser

import android.content.ComponentName
import android.os.Parcel
import android.service.chooser.ChooserResult.CHOOSER_RESULT_COPY
import android.service.chooser.ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compatibility.common.util.ApiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@ApiTest(
    apis = [
        "android.service.chooser.ChooserResult#getType",
        "android.service.chooser.ChooserResult#getSelectedComponent",
        "android.service.chooser.ChooserResult#isShortcut",
        "android.service.chooser.ChooserResult#writeToParcel",
        "android.service.chooser.ChooserResult#CREATOR"])
class ChooserResultTest {
    @Test
    fun testShortcut() {
        val testSubject = ChooserResult(
            /* type = */ CHOOSER_RESULT_SELECTED_COMPONENT,
            /* componentName = */ ComponentName("a","b"),
            /* isShortcut = */ true)

        val parcel = Parcel.obtain()

        testSubject.writeToParcel(parcel, testSubject.describeContents())
        parcel.setDataPosition(0)
        val result = ChooserResult.CREATOR.createFromParcel(parcel)

        assertEquals(CHOOSER_RESULT_SELECTED_COMPONENT, result.type)
        assertEquals(ComponentName("a","b"), result.selectedComponent)
        assertTrue(result.isShortcut)
    }

    @Test
    fun testAction() {
        val testSubject = ChooserResult(
            /* type = */ CHOOSER_RESULT_COPY,
            /* componentName = */ null,
            /* isShortcut = */ false)

        val parcel = Parcel.obtain()

        testSubject.writeToParcel(parcel, testSubject.describeContents())
        parcel.setDataPosition(0)
        val result = ChooserResult.CREATOR.createFromParcel(parcel)

        assertEquals(CHOOSER_RESULT_COPY, result.type)
        assertNull(result.selectedComponent)
        assertFalse(result.isShortcut)
    }

}
