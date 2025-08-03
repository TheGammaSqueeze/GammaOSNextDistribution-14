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

package com.example.testapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

@ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // To debug resources not destroyed
        // "A resource failed to call destroy."
        try {
            Class.forName("dalvik.system.CloseGuard")
                .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                .invoke(null, true)
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }

        val validate = true
        val tester = Tester(this, validate)
        val numberOfIterations = if (validate) 1 else 28
        val t = TimingTracker(numberOfIterations, 0)
        for (i in 1..numberOfIterations) {
            println("*** Iteration $i of $numberOfIterations ****")
            //startMethodTracing("myTracing")
            //startMethodTracingSampling("myTracing_sample", 8000000, 10)
            val r = tester.testAll(t)
            //stopMethodTracing()
            findViewById<TextView>(R.id.sample_text).text = "$r\n\n${t.report()}"
            t.nextIteration()
        }
        tester.destroy()
    }
}
