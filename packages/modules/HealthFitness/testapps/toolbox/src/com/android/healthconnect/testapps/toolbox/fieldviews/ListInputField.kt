/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import android.health.connect.datatypes.CyclingPedalingCadenceRecord.CyclingPedalingCadenceRecordSample
import android.health.connect.datatypes.ExerciseLap
import android.health.connect.datatypes.ExerciseSegment
import android.health.connect.datatypes.ExerciseSegmentType
import android.health.connect.datatypes.HeartRateRecord.HeartRateSample
import android.health.connect.datatypes.PowerRecord.PowerRecordSample
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.SpeedRecord.SpeedRecordSample
import android.health.connect.datatypes.StepsCadenceRecord.StepsCadenceRecordSample
import android.health.connect.datatypes.units.Length
import android.health.connect.datatypes.units.Power
import android.health.connect.datatypes.units.Velocity
import android.widget.LinearLayout
import android.widget.TextView
import com.android.healthconnect.testapps.toolbox.Constants.INPUT_TYPE_DOUBLE
import com.android.healthconnect.testapps.toolbox.Constants.INPUT_TYPE_LONG
import com.android.healthconnect.testapps.toolbox.R
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.getStaticFieldNamesAndValues
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

@SuppressLint("ViewConstructor")
class ListInputField(context: Context, fieldName: String, inputFieldType: ParameterizedType) :
    InputFieldView(context) {

    data class Row(val context: Context) {
        val startTime = DateTimePicker(context, "Start Time", true)
        val endTime = DateTimePicker(context, "End Time")
        lateinit var dataPointField: InputFieldView
    }

    private var mLinearLayout: LinearLayout
    private var mDataTypeClass: Type
    private var mRowsData: ArrayList<Row>

    init {
        inflate(context, R.layout.fragment_list_input_view, this)
        findViewById<TextView>(R.id.field_name).text = fieldName
        mLinearLayout = findViewById(R.id.list_input_linear_layout)
        mDataTypeClass = inputFieldType.actualTypeArguments[0]
        mRowsData = ArrayList()
        setupAddRowButtonListener()
    }

    private fun setupAddRowButtonListener() {
        val buttonView = findViewById<FloatingActionButton>(R.id.add_row)

        buttonView.setOnClickListener { addRow() }
    }

    private fun addRow() {
        val rowLayout = LinearLayout(context)
        rowLayout.orientation = VERTICAL

        val row = Row(context)

        rowLayout.addView(row.startTime)
        val dataPointField: InputFieldView =
            when (mDataTypeClass) {
                SpeedRecordSample::class.java -> {
                    EditableTextView(context, "Velocity", INPUT_TYPE_DOUBLE)
                }
                HeartRateSample::class.java -> {
                    EditableTextView(context, "Beats per minute", INPUT_TYPE_LONG)
                }
                PowerRecordSample::class.java -> {
                    EditableTextView(context, "Power", INPUT_TYPE_DOUBLE)
                }
                CyclingPedalingCadenceRecordSample::class.java -> {
                    EditableTextView(context, "Revolutions Per Minute", INPUT_TYPE_DOUBLE)
                }
                SleepSessionRecord.Stage::class.java -> {
                    rowLayout.addView(row.endTime)
                    EnumDropDown(
                        context,
                        "Sleep Stage",
                        getStaticFieldNamesAndValues(SleepSessionRecord.StageType::class))
                }
                StepsCadenceRecordSample::class.java -> {
                    EditableTextView(context, "Steps Cadence", INPUT_TYPE_DOUBLE)
                }
                ExerciseSegment::class.java -> {
                    rowLayout.addView(row.endTime)
                    EnumDropDown(
                        context,
                        "Segment Type",
                        getStaticFieldNamesAndValues(ExerciseSegmentType::class))
                }
                ExerciseLap::class.java -> {
                    rowLayout.addView(row.endTime)
                    EditableTextView(context, "Length", INPUT_TYPE_DOUBLE)
                }
                else -> {
                    return
                }
            }
        row.dataPointField = dataPointField
        rowLayout.addView(dataPointField)
        mRowsData.add(row)
        mLinearLayout.addView(rowLayout, 0)
    }

    override fun getFieldValue(): List<Any> {
        val samples: ArrayList<Any> = ArrayList()
        for (row in mRowsData) {
            val dataPoint = row.dataPointField
            val instant = row.startTime
            val dataPointString = dataPoint.getFieldValue().toString()
            when (mDataTypeClass) {
                SpeedRecordSample::class.java -> {
                    samples.add(
                        SpeedRecordSample(
                            Velocity.fromMetersPerSecond(dataPointString.toDouble()),
                            instant.getFieldValue()))
                }
                HeartRateSample::class.java -> {
                    samples.add(HeartRateSample(dataPointString.toLong(), instant.getFieldValue()))
                }
                PowerRecordSample::class.java -> {
                    samples.add(
                        PowerRecordSample(
                            Power.fromWatts(dataPointString.toDouble()), instant.getFieldValue()))
                }
                CyclingPedalingCadenceRecordSample::class.java -> {
                    samples.add(
                        CyclingPedalingCadenceRecordSample(
                            dataPointString.toDouble(), instant.getFieldValue()))
                }
                StepsCadenceRecordSample::class.java -> {
                    samples.add(
                        StepsCadenceRecordSample(
                            dataPointString.toDouble(), instant.getFieldValue()))
                }
                SleepSessionRecord.Stage::class.java -> {
                    samples.add(
                        SleepSessionRecord.Stage(
                            instant.getFieldValue(),
                            row.endTime.getFieldValue(),
                            dataPointString.toInt()))
                }
                ExerciseSegment::class.java -> {
                    samples.add(
                        ExerciseSegment.Builder(
                                instant.getFieldValue(),
                                row.endTime.getFieldValue(),
                                dataPointString.toInt())
                            .build())
                }
                ExerciseLap::class.java -> {
                    samples.add(
                        ExerciseLap.Builder(instant.getFieldValue(), row.endTime.getFieldValue())
                            .apply {
                                if (dataPointString.isNotEmpty()) {
                                    setLength(Length.fromMeters(dataPointString.toDouble()))
                                }
                            }
                            .build())
                }
            }
        }
        return samples
    }

    override fun isEmpty(): Boolean {
        return getFieldValue().isEmpty()
    }
}
