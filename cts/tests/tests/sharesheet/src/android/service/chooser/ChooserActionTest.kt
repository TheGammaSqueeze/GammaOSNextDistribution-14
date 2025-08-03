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

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.ApiTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@ApiTest(
        apis = [
            "android.service.chooser.ChooserAction#writeToParcel",
            "android.service.chooser.ChooserAction#CREATOR#createFromParcel"])
class ChooserActionTest {
    @Test
    fun testParcel() {
        val icon = Icon.createWithContentUri("content://org.package.app/image")
        val label = "Custom Action"
        val pendingIntent = PendingIntent.getBroadcast(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                0,
                Intent("TESTACTION"),
                PendingIntent.FLAG_IMMUTABLE
        )
        val testSubject = ChooserAction.Builder(icon, label, pendingIntent).build()
        val parcel = Parcel.obtain()

        testSubject.writeToParcel(parcel, testSubject.describeContents())
        parcel.setDataPosition(0)
        val result = ChooserAction.CREATOR.createFromParcel(parcel)

        assertEquals(icon.uri, result.icon.uri)
        assertEquals(label, result.label)
        assertEquals(pendingIntent.creatorPackage, result.action.creatorPackage)
    }
}
