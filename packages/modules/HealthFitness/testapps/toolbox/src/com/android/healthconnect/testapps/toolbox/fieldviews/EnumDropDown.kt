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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import com.android.healthconnect.testapps.toolbox.R
import com.android.healthconnect.testapps.toolbox.utils.EnumFieldsWithValues

@SuppressLint("ViewConstructor")
class EnumDropDown(
    context: Context,
    title: String?,
    enumFieldsWithValues: EnumFieldsWithValues,
) : InputFieldView(context) {

    private var mSelectedPosition = -1
    private var mDropdownValues: List<String>
    private var mEnumFieldsWithValues: EnumFieldsWithValues

    init {
        inflate(context, R.layout.fragment_dropdown, this)
        mDropdownValues = enumFieldsWithValues.getAllFieldNames()
        mEnumFieldsWithValues = enumFieldsWithValues
        val spinnerTitle = findViewById<TextView>(R.id.title)
        spinnerTitle.text = title
        setupSpinner()
    }

    private fun setupSpinner() {
        val autoCompleteTextView =
            findViewById<AutoCompleteTextView>(R.id.enum_auto_complete_textview)
        val dataAdapter: ArrayAdapter<Any> =
            ArrayAdapter<Any>(context, R.layout.simple_spinner_item, mDropdownValues)
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        autoCompleteTextView.setAdapter(dataAdapter)

        autoCompleteTextView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ -> mSelectedPosition = position }
    }

    override fun getFieldValue(): Any {
        return mEnumFieldsWithValues.getFieldValue(mDropdownValues[mSelectedPosition])
    }

    override fun isEmpty(): Boolean {
        return mSelectedPosition == -1
    }
}
