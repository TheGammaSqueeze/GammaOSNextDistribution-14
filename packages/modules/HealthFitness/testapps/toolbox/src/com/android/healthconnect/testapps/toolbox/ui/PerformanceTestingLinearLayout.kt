package com.android.healthconnect.testapps.toolbox.ui

import android.content.Context
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.android.healthconnect.testapps.toolbox.R

class PerformanceTestingLinearLayout(context: Context) : LinearLayout(context) {

    private var spanOverTime: Boolean = false
    private var numberOfMinutes: EditText

    init {
        inflate(context, R.layout.fragment_performance_testing_dialog, this)
        val checkbox = findViewById<CheckBox>(R.id.checkbox_insert_without_spanning)
        numberOfMinutes = findViewById(R.id.number_of_minutes)
        checkbox.setOnClickListener {
            val isChecked = (it as CheckBox).isChecked
            spanOverTime = isChecked
            if (isChecked) {
                numberOfMinutes.visibility = VISIBLE
            } else {
                numberOfMinutes.visibility = GONE
            }
        }
    }

    fun getPerformanceData(): PerformanceData {
        val numberOfRecordsPerBatch = getNumberOfRecordsPerBatch()
        val numberOfMinutes = getNumberOfMinutes()
        val numberOfBatches = getNumberOfBatches()
        var isDataValid = true
        if (numberOfBatches == -1L || numberOfMinutes == -1L || numberOfRecordsPerBatch == -1L) {
            isDataValid = false
        }
        return PerformanceData(
            spanOverTime,
            getNumberOfMinutes(),
            getNumberOfBatches(),
            getNumberOfRecordsPerBatch(),
            isDataValid)
    }

    private fun getNumberOfMinutes(): Long {
        if (spanOverTime) {
            return returnIntIfNotEmpty(numberOfMinutes.text.toString())
        }
        return 0L
    }

    private fun getNumberOfBatches(): Long {
        return returnIntIfNotEmpty(findViewById<EditText>(R.id.number_of_batches).text.toString())
    }

    private fun getNumberOfRecordsPerBatch(): Long {
        return returnIntIfNotEmpty(findViewById<EditText>(R.id.batch_size).text.toString())
    }

    private fun returnIntIfNotEmpty(dataString: String): Long {
        if (dataString.isEmpty()) {
            return -1L
        }
        return dataString.toLong()
    }
}

data class PerformanceData(
    val spanOverTime: Boolean,
    val numberOfMinutes: Long,
    val numberOfBatches: Long,
    val numberOfRecordsPerBatch: Long,
    val isDataValid: Boolean,
)
