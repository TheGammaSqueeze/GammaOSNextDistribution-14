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
package com.android.healthconnect.testapps.toolbox.ui

import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.BasalBodyTemperatureRecord
import android.health.connect.datatypes.BloodGlucoseRecord
import android.health.connect.datatypes.BloodPressureRecord
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation
import android.health.connect.datatypes.BodyTemperatureRecord
import android.health.connect.datatypes.CervicalMucusRecord
import android.health.connect.datatypes.ExerciseRoute
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType
import android.health.connect.datatypes.FloorsClimbedRecord
import android.health.connect.datatypes.InstantRecord
import android.health.connect.datatypes.IntervalRecord
import android.health.connect.datatypes.MealType
import android.health.connect.datatypes.MenstruationFlowRecord
import android.health.connect.datatypes.OvulationTestRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SexualActivityRecord
import android.health.connect.datatypes.Vo2MaxRecord
import android.health.connect.datatypes.units.BloodGlucose
import android.health.connect.datatypes.units.Energy
import android.health.connect.datatypes.units.Length
import android.health.connect.datatypes.units.Mass
import android.health.connect.datatypes.units.Percentage
import android.health.connect.datatypes.units.Power
import android.health.connect.datatypes.units.Pressure
import android.health.connect.datatypes.units.Temperature
import android.health.connect.datatypes.units.Volume
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.testapps.toolbox.Constants.HealthPermissionType
import com.android.healthconnect.testapps.toolbox.Constants.INPUT_TYPE_DOUBLE
import com.android.healthconnect.testapps.toolbox.Constants.INPUT_TYPE_INT
import com.android.healthconnect.testapps.toolbox.Constants.INPUT_TYPE_LONG
import com.android.healthconnect.testapps.toolbox.Constants.INPUT_TYPE_TEXT
import com.android.healthconnect.testapps.toolbox.R
import com.android.healthconnect.testapps.toolbox.data.ExerciseRoutesTestData.Companion.routeDataMap
import com.android.healthconnect.testapps.toolbox.fieldviews.DateTimePicker
import com.android.healthconnect.testapps.toolbox.fieldviews.EditableTextView
import com.android.healthconnect.testapps.toolbox.fieldviews.EnumDropDown
import com.android.healthconnect.testapps.toolbox.fieldviews.InputFieldView
import com.android.healthconnect.testapps.toolbox.fieldviews.ListInputField
import com.android.healthconnect.testapps.toolbox.utils.EnumFieldsWithValues
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils
import com.android.healthconnect.testapps.toolbox.utils.InsertOrUpdateRecords.Companion.createRecordObject
import com.android.healthconnect.testapps.toolbox.viewmodels.InsertOrUpdateRecordsViewModel
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

class InsertRecordFragment : Fragment() {

    private lateinit var mRecordFields: Array<Field>
    private lateinit var mRecordClass: KClass<out Record>
    private lateinit var mNavigationController: NavController
    private lateinit var mFieldNameToFieldInput: HashMap<String, InputFieldView>
    private lateinit var mLinearLayout: LinearLayout
    private lateinit var mHealthConnectManager: HealthConnectManager
    private lateinit var mUpdateRecordUuid: InputFieldView

    private val mInsertOrUpdateViewModel: InsertOrUpdateRecordsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        mInsertOrUpdateViewModel.insertedRecordsState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is InsertOrUpdateRecordsViewModel.InsertedRecordsState.WithData -> {
                    showInsertSuccessDialog(state.entries)
                }
                is InsertOrUpdateRecordsViewModel.InsertedRecordsState.Error -> {
                    Toast.makeText(
                            context,
                            "Unable to insert record(s)! ${state.errorMessage}",
                            Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        mInsertOrUpdateViewModel.updatedRecordsState.observe(viewLifecycleOwner) { state ->
            if (state is InsertOrUpdateRecordsViewModel.UpdatedRecordsState.Error) {
                Toast.makeText(
                        context,
                        "Unable to update record(s)! ${state.errorMessage}",
                        Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(context, "Successfully updated record(s)!", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        return inflater.inflate(R.layout.fragment_insert_record, container, false)
    }

    private fun showInsertSuccessDialog(records: List<Record>) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Record UUID(s)")
        builder.setMessage(records.joinToString { it.metadata.id })
        builder.setPositiveButton(android.R.string.ok) { _, _ -> }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
        alertDialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mNavigationController = findNavController()
        mHealthConnectManager =
            requireContext().getSystemService(HealthConnectManager::class.java)!!

        val permissionType =
            arguments?.getSerializable("permissionType", HealthPermissionType::class.java)
                ?: throw java.lang.IllegalArgumentException("Please pass the permissionType.")

        mFieldNameToFieldInput = HashMap()
        mRecordFields = permissionType.recordClass?.java?.declaredFields as Array<Field>
        mRecordClass = permissionType.recordClass
        view.findViewById<TextView>(R.id.title).setText(permissionType.title)
        mLinearLayout = view.findViewById(R.id.record_input_linear_layout)

        when (mRecordClass.java.superclass) {
            IntervalRecord::class.java -> {
                setupStartAndEndTimeFields()
            }
            InstantRecord::class.java -> {
                setupTimeField("Time", "time")
            }
            else -> {
                Toast.makeText(context, R.string.not_implemented, Toast.LENGTH_SHORT).show()
                mNavigationController.popBackStack()
            }
        }
        setupRecordFields()
        setupEnumFields()
        handleSpecialCases()
        setupListFields()
        setupInsertDataButton(view)
        setupUpdateDataButton(view)
    }

    private fun setupTimeField(title: String, key: String, setPreviousDay: Boolean = false) {
        val timeField = DateTimePicker(this.requireContext(), title, setPreviousDay)
        mLinearLayout.addView(timeField)

        mFieldNameToFieldInput[key] = timeField
    }

    private fun setupStartAndEndTimeFields() {
        setupTimeField("Start Time", "startTime", true)
        setupTimeField("End Time", "endTime")
    }

    private fun setupRecordFields() {
        var field: InputFieldView
        for (mRecordsField in mRecordFields) {
            when (mRecordsField.type) {
                Long::class.java -> {
                    field =
                        EditableTextView(this.requireContext(), mRecordsField.name, INPUT_TYPE_LONG)
                }
                ExerciseRoute::class.java, // Edge case
                Int::class.java, // Most of int fields are enums and are handled separately
                List::class
                    .java, // Handled later so that list fields are always added towards the end
                -> {
                    continue
                }
                Double::class.java,
                Pressure::class.java,
                BloodGlucose::class.java,
                Temperature::class.java,
                Volume::class.java,
                Percentage::class.java,
                Mass::class.java,
                Length::class.java,
                Energy::class.java,
                Power::class.java, -> {
                    field =
                        EditableTextView(
                            this.requireContext(), mRecordsField.name, INPUT_TYPE_DOUBLE)
                }
                CharSequence::class.java -> {
                    field =
                        EditableTextView(this.requireContext(), mRecordsField.name, INPUT_TYPE_TEXT)
                }
                else -> {
                    continue
                }
            }
            mLinearLayout.addView(field)
            mFieldNameToFieldInput[mRecordsField.name] = field
        }
    }

    private fun setupEnumFields() {
        val enumFieldNameToClass: HashMap<String, KClass<*>> = HashMap()
        var field: InputFieldView
        when (mRecordClass) {
            MenstruationFlowRecord::class -> {
                enumFieldNameToClass["mFlow"] =
                    MenstruationFlowRecord.MenstruationFlowType::class as KClass<*>
            }
            OvulationTestRecord::class -> {
                enumFieldNameToClass["mResult"] =
                    OvulationTestRecord.OvulationTestResult::class as KClass<*>
            }
            SexualActivityRecord::class -> {
                enumFieldNameToClass["mProtectionUsed"] =
                    SexualActivityRecord.SexualActivityProtectionUsed::class as KClass<*>
            }
            CervicalMucusRecord::class -> {
                enumFieldNameToClass["mSensation"] =
                    CervicalMucusRecord.CervicalMucusSensation::class as KClass<*>
                enumFieldNameToClass["mAppearance"] =
                    CervicalMucusRecord.CervicalMucusAppearance::class as KClass<*>
            }
            Vo2MaxRecord::class -> {
                enumFieldNameToClass["mMeasurementMethod"] =
                    Vo2MaxRecord.Vo2MaxMeasurementMethod::class as KClass<*>
            }
            BasalBodyTemperatureRecord::class -> {
                enumFieldNameToClass["mBodyTemperatureMeasurementLocation"] =
                    BodyTemperatureMeasurementLocation::class as KClass<*>
            }
            BloodGlucoseRecord::class -> {
                enumFieldNameToClass["mSpecimenSource"] =
                    BloodGlucoseRecord.SpecimenSource::class as KClass<*>
                enumFieldNameToClass["mRelationToMeal"] =
                    BloodGlucoseRecord.RelationToMealType::class as KClass<*>
                enumFieldNameToClass["mMealType"] = MealType::class as KClass<*>
            }
            BloodPressureRecord::class -> {
                enumFieldNameToClass["mMeasurementLocation"] =
                    BodyTemperatureMeasurementLocation::class as KClass<*>
                enumFieldNameToClass["mBodyPosition"] =
                    BloodPressureRecord.BodyPosition::class as KClass<*>
            }
            BodyTemperatureRecord::class -> {
                enumFieldNameToClass["mMeasurementLocation"] =
                    BodyTemperatureMeasurementLocation::class as KClass<*>
            }
            ExerciseSessionRecord::class -> {
                enumFieldNameToClass["mExerciseType"] = ExerciseSessionType::class as KClass<*>
            }
        }
        if (enumFieldNameToClass.size > 0) {
            for (entry in enumFieldNameToClass.entries) {
                val fieldName = entry.key
                val enumClass = entry.value
                val enumFieldsWithValues: EnumFieldsWithValues =
                    GeneralUtils.getStaticFieldNamesAndValues(enumClass)
                field = EnumDropDown(this.requireContext(), fieldName, enumFieldsWithValues)
                mLinearLayout.addView(field)
                mFieldNameToFieldInput[fieldName] = field
            }
        }
    }

    private fun setupListFields() {
        var field: InputFieldView
        for (mRecordsField in mRecordFields) {
            when (mRecordsField.type) {
                List::class.java -> {
                    field =
                        ListInputField(
                            this.requireContext(),
                            mRecordsField.name,
                            mRecordsField.genericType as ParameterizedType)
                }
                else -> {
                    continue
                }
            }
            mLinearLayout.addView(field)
            mFieldNameToFieldInput[mRecordsField.name] = field
        }
    }

    private fun handleSpecialCases() {
        var field: InputFieldView? = null
        var fieldName: String? = null

        when (mRecordClass) {
            FloorsClimbedRecord::class -> {
                fieldName = "mFloors"
                field = EditableTextView(this.requireContext(), fieldName, INPUT_TYPE_INT)
            }
            ExerciseSessionRecord::class -> {
                fieldName = "mExerciseRoute"
                field =
                    EnumDropDown(
                        this.requireContext(),
                        fieldName,
                        EnumFieldsWithValues(routeDataMap as Map<String, Any>))
            }
        }
        if (field != null && fieldName != null) {
            mLinearLayout.addView(field)
            mFieldNameToFieldInput[fieldName] = field
        }
    }

    private fun setupInsertDataButton(view: View) {
        val buttonView = view.findViewById<Button>(R.id.insert_record)

        buttonView.setOnClickListener {
            try {
                val record =
                    createRecordObject(mRecordClass, mFieldNameToFieldInput, requireContext())
                mInsertOrUpdateViewModel.insertRecordsViaViewModel(
                    listOf(record), mHealthConnectManager)
            } catch (ex: Exception) {
                Log.d("InsertOrUpdateRecordsViewModel", ex.localizedMessage!!)
                Toast.makeText(
                        context,
                        "Unable to insert record: ${ex.localizedMessage}",
                        Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun setupUpdateRecordUuidInputDialog() {
        mUpdateRecordUuid = EditableTextView(requireContext(), null, INPUT_TYPE_TEXT)
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Enter UUID")
        builder.setView(mUpdateRecordUuid)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            try {
                if (mUpdateRecordUuid.getFieldValue().toString().isEmpty()) {
                    throw IllegalArgumentException("Please enter UUID")
                }
                val record =
                    createRecordObject(
                        mRecordClass,
                        mFieldNameToFieldInput,
                        requireContext(),
                        mUpdateRecordUuid.getFieldValue().toString())
                mInsertOrUpdateViewModel.updateRecordsViaViewModel(
                    listOf(record), mHealthConnectManager)
            } catch (ex: Exception) {
                Toast.makeText(
                        context, "Unable to update: ${ex.localizedMessage}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    private fun setupUpdateDataButton(view: View) {
        val buttonView = view.findViewById<Button>(R.id.update_record)

        buttonView.setOnClickListener { setupUpdateRecordUuidInputDialog() }
    }
}
