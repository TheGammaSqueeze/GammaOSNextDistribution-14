/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.testapps.toolbox.fieldviews

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.android.healthconnect.testapps.toolbox.R

@SuppressLint("ViewConstructor")
class EditableTextView(context: Context, fieldName: String?, inputType: Int) :
    InputFieldView(context) {

    init {
        inflate(context, R.layout.fragment_editable_field, this)
        val textView = findViewById<TextView>(R.id.title)
        if (fieldName == null) {
            textView.visibility = View.GONE
        } else {
            textView.text = fieldName
        }

        findViewById<EditText>(R.id.input_field).inputType = inputType
    }

    override fun getFieldValue(): Any {
        return findViewById<EditText>(R.id.input_field).text.toString()
    }

    override fun isEmpty(): Boolean {
        return findViewById<EditText>(R.id.input_field).text.isEmpty()
    }
}
