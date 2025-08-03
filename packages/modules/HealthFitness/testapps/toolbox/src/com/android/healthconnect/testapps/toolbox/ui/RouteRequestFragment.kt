/**
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.testapps.toolbox.ui

import android.app.Activity
import android.content.Intent
import android.health.connect.HealthConnectManager
import android.health.connect.HealthConnectManager.EXTRA_EXERCISE_ROUTE
import android.health.connect.datatypes.ExerciseRoute
import android.health.connect.datatypes.ExerciseSessionRecord
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.testapps.toolbox.R
import com.android.healthconnect.testapps.toolbox.adapters.TextViewListAdapter
import com.android.healthconnect.testapps.toolbox.adapters.TextViewListViewHolder
import com.android.healthconnect.testapps.toolbox.viewmodels.RouteRequestViewModel

class RouteRequestFragment : Fragment() {

    private val TAG = RouteRequestFragment::class.java.simpleName
    private val ROUTE_REQUEST_CODE = 1
    private val routeRequestViewModel: RouteRequestViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_route_request, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        routeRequestViewModel.readExerciseSessionRecords(requireContext())

        routeRequestViewModel.exerciseSessionRecords.observe(viewLifecycleOwner) {
            result: Result<List<ExerciseSessionRecord>> ->
            result.onFailure {
                Log.e(TAG, "Read records error", it)
                Toast.makeText(
                        requireContext(),
                        "Read records error: ${it.javaClass.simpleName}",
                        Toast.LENGTH_SHORT)
                    .show()
            }

            result.onSuccess {
                val recyclerView: RecyclerView = view.findViewById(R.id.list_recycler_view)
                recyclerView.adapter =
                    TextViewListAdapter(it) { viewHolder: TextViewListViewHolder, position: Int ->
                        onBindViewHolderCallback(viewHolder, it[position])
                    }
            }
        }
    }

    private fun onBindViewHolderCallback(
        viewHolder: TextViewListViewHolder,
        record: ExerciseSessionRecord
    ) {
        val textView = viewHolder.textView
        textView.text =
            "ExerciseSession\n" +
                "Title: ${record.title}\n" +
                "Start time: ${record.startTime}\n" +
                "End time: ${record.endTime}\n" +
                "Package: ${record.metadata.dataOrigin.packageName}\n" +
                "Route: ${record.route != null}\n" +
                "Has route: ${record.hasRoute()}\n" +
                "Id: ${record.metadata.id}"

        textView.setOnClickListener {
            val intent =
                Intent(HealthConnectManager.ACTION_REQUEST_EXERCISE_ROUTE).apply {
                    putExtra(HealthConnectManager.EXTRA_SESSION_ID, record.metadata.id)
                }
            startActivityForResult(intent, ROUTE_REQUEST_CODE)
        }
    }

    // public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != ROUTE_REQUEST_CODE) {
            return
        }

        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(requireContext(), "Route request cancelled", Toast.LENGTH_SHORT).show()
            return
        }

        val route = data?.getParcelableExtra(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java)

        if (route == null || route.routeLocations.isEmpty()) {
            Toast.makeText(requireContext(), "Null or empty route", Toast.LENGTH_SHORT).show()
            return
        }

        val message =
            route!!
                .routeLocations
                .map {
                    "Time: ${it.time}\n" +
                        "Latitude: ${it.latitude}\n" +
                        "Longitude: ${it.longitude}\n" +
                        "Altitude: ${it.altitude}\n" +
                        "H accuracy: ${it.horizontalAccuracy}\n" +
                        "V accuracy: ${it.verticalAccuracy}\n"
                }
                .joinToString("\n")

        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Exercise route")
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok) { _, _ -> }

        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }
}
