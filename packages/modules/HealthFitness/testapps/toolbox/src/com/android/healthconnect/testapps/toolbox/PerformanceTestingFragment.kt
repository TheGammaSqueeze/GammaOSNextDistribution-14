package com.android.healthconnect.testapps.toolbox

import android.health.connect.HealthConnectManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.android.healthconnect.testapps.toolbox.seed.SeedData
import com.android.healthconnect.testapps.toolbox.ui.PerformanceTestingLinearLayout
import com.android.healthconnect.testapps.toolbox.utils.LoadingDialog
import com.android.healthconnect.testapps.toolbox.viewmodels.PerformanceTestingViewModel
import com.android.healthconnect.testapps.toolbox.viewmodels.PerformanceTestingViewModel.PerformanceInsertedRecordsState
import com.android.healthconnect.testapps.toolbox.viewmodels.PerformanceTestingViewModel.PerformanceReadRecordsState

class PerformanceTestingFragment : Fragment() {

    private lateinit var manager: HealthConnectManager
    private lateinit var seedDataClass: SeedData
    private lateinit var loadingDialog: LoadingDialog

    private val performanceTestingViewModel: PerformanceTestingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        manager = requireContext().getSystemService(HealthConnectManager::class.java)!!
        seedDataClass = SeedData(requireContext(), manager)
        loadingDialog = LoadingDialog(requireContext())

        performanceTestingViewModel.performanceInsertedRecordsState.observe(this) { state ->
            loadingDialog.dismissDialog()
            when (state) {
                is PerformanceInsertedRecordsState.Error -> {
                    Toast.makeText(
                            context,
                            "Unable to seed data: ${state.errorMessage}",
                            Toast.LENGTH_SHORT)
                        .show()
                }
                is PerformanceInsertedRecordsState.Success -> {
                    Toast.makeText(
                            context, "Successfully inserted Heart Rate Records", Toast.LENGTH_SHORT)
                        .show()
                }
                is PerformanceInsertedRecordsState.BeginInserting -> {
                    insertRecordsForPerformanceTesting(state.seedDataInParallel)
                }
                is PerformanceInsertedRecordsState.InsertingOverSpanOfTime -> {
                    Toast.makeText(
                            context,
                            "Each batch will be inserted after ${state.timeDifferenceBetweenEachBatchInsert / 1000} seconds",
                            Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        performanceTestingViewModel.performanceReadRecordsState.observe(this) { state ->
            loadingDialog.dismissDialog()
            when (state) {
                is PerformanceReadRecordsState.Error -> {
                    Toast.makeText(
                            context,
                            "Unable to read data: ${state.errorMessage}",
                            Toast.LENGTH_SHORT)
                        .show()
                }
                is PerformanceReadRecordsState.Success -> {
                    Toast.makeText(
                            context, "Successfully read Heart Rate Records", Toast.LENGTH_SHORT)
                        .show()
                }
                is PerformanceReadRecordsState.BeginReading -> {
                    readRecordsForPerformanceTesting()
                }
                is PerformanceReadRecordsState.ReadingOverSpanOfTime -> {
                    Toast.makeText(
                            context,
                            "Each batch will be read after ${state.timeDifferenceBetweenEachBatchInsert / 1000} seconds",
                            Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private fun insertRecordsForPerformanceTesting(ignoreForNow: Boolean) {

        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        val performanceTestingLinearLayout = PerformanceTestingLinearLayout(requireContext())

        builder.setTitle("Enter performance testing values")
        builder.setView(performanceTestingLinearLayout)
        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            val performanceData = performanceTestingLinearLayout.getPerformanceData()

            if (!performanceData.isDataValid) {
                Toast.makeText(requireContext(), "Please enter valid data", Toast.LENGTH_SHORT)
                    .show()
                return@setPositiveButton
            }

            if (performanceData.spanOverTime) {
                performanceTestingViewModel.insertRecordsForPerformanceTestingOverASpanOfTime(
                    performanceData.numberOfBatches,
                    performanceData.numberOfRecordsPerBatch,
                    performanceData.numberOfMinutes,
                    seedDataClass)
            } else {
                performanceTestingViewModel.insertRecordsForPerformanceTesting(
                    performanceData.numberOfBatches,
                    performanceData.numberOfRecordsPerBatch,
                    seedDataClass)
                (dialog as AlertDialog).setOnDismissListener { loadingDialog.showDialog() }
            }
            dialog.dismiss()
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    private fun readRecordsForPerformanceTesting() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        val performanceTestingLinearLayout = PerformanceTestingLinearLayout(requireContext())
        builder.setTitle("Enter performance testing values")
        builder.setView(performanceTestingLinearLayout)
        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            val performanceData = performanceTestingLinearLayout.getPerformanceData()

            if (!performanceData.isDataValid) {
                Toast.makeText(requireContext(), "Please enter valid data", Toast.LENGTH_SHORT)
                    .show()
                return@setPositiveButton
            }

            if (performanceData.spanOverTime) {
                performanceTestingViewModel.readRecordsForPerformanceTestingOverASpanOfTime(
                    performanceData.numberOfBatches,
                    performanceData.numberOfRecordsPerBatch,
                    performanceData.numberOfMinutes,
                    manager)
            } else {
                performanceTestingViewModel.readRecordsForPerformanceTesting(
                    performanceData.numberOfBatches,
                    performanceData.numberOfRecordsPerBatch,
                    manager)
                (dialog as AlertDialog).setOnDismissListener { loadingDialog.showDialog() }
            }
            dialog.dismiss()
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }
}
