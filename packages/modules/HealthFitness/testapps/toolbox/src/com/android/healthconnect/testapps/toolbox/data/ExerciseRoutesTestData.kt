package com.android.healthconnect.testapps.toolbox.data

import android.health.connect.datatypes.ExerciseRoute
import android.health.connect.datatypes.ExerciseRoute.Location
import java.time.Instant

class ExerciseRoutesTestData {

    data class ExerciseRouteLocationData(
        val secondsToAdd: Int,
        val latitude: Double,
        val longitude: Double,
    )

    companion object {

        // https://screenshot.googleplex.com/9MRH639gSzFpgzY
        val WARSAW_ROUTE =
            listOf(
                ExerciseRouteLocationData(12, 52.26019, 21.02268),
                ExerciseRouteLocationData(40, 52.26000, 21.02360),
                ExerciseRouteLocationData(48, 52.25973, 21.02356),
                ExerciseRouteLocationData(60, 52.25966, 21.02313),
                ExerciseRouteLocationData(78, 52.25993, 21.02309),
                ExerciseRouteLocationData(79, 52.25972, 21.02271),
                ExerciseRouteLocationData(90, 52.25948, 21.02276),
                ExerciseRouteLocationData(93, 52.25945, 21.02335),
                ExerciseRouteLocationData(94, 52.25960, 21.02338),
                ExerciseRouteLocationData(100, 52.25961, 21.02382),
                ExerciseRouteLocationData(102, 52.25954, 21.02370),
                ExerciseRouteLocationData(105, 52.25945, 21.02362),
                ExerciseRouteLocationData(109, 52.25954, 21.02354),
            )

        // https://screenshot.googleplex.com/7yVxQfJaETZcFPS
        private val LONDON_ROUTE =
            listOf(
                ExerciseRouteLocationData(0, 51.53312, -0.15512),
                ExerciseRouteLocationData(12, 51.53301, -0.15566),
                ExerciseRouteLocationData(40, 51.53269, -0.15578),
                ExerciseRouteLocationData(48, 51.53279, -0.15529),
                ExerciseRouteLocationData(60, 51.53246, -0.15577),
                ExerciseRouteLocationData(78, 51.53236, -0.15500),
                ExerciseRouteLocationData(79, 51.53268, -0.15457),
                ExerciseRouteLocationData(93, 51.53244, -0.15410),
                ExerciseRouteLocationData(94, 51.53277, -0.15391),
                ExerciseRouteLocationData(100, 51.53297, -0.15242),
                ExerciseRouteLocationData(102, 51.53299, -0.15472),
                ExerciseRouteLocationData(105, 51.53319, -0.15467),
                ExerciseRouteLocationData(109, 51.53308, -0.15508),
            )

        // https://screenshot.googleplex.com/ARzR6WNvkNct4Pt
        private val KOSCIUSZKO_ROUTE =
            listOf(
                ExerciseRouteLocationData(0, -36.46687, 148.25588),
                ExerciseRouteLocationData(12, -36.46699, 148.25730),
                ExerciseRouteLocationData(40, -36.46643, 148.25841),
                ExerciseRouteLocationData(48, -36.46558, 148.25732),
                ExerciseRouteLocationData(60, -36.46552, 148.25474),
                ExerciseRouteLocationData(78, -36.46681, 148.25378),
                ExerciseRouteLocationData(79, -36.46670, 148.25221),
                ExerciseRouteLocationData(93, -36.46834, 148.25089),
                ExerciseRouteLocationData(94, -36.46964, 148.25051),
                ExerciseRouteLocationData(100, -36.47003, 148.25401),
                ExerciseRouteLocationData(102, -36.46979, 148.25579),
                ExerciseRouteLocationData(105, -36.46852, 148.25638),
                ExerciseRouteLocationData(109, -36.46864, 148.25299),
                ExerciseRouteLocationData(111, -36.46799, 148.25247),
                ExerciseRouteLocationData(112, -36.46708, 148.25461),
            )

        // https://screenshot.googleplex.com/9WLjATYv4z4YteD
        private val CHUKOTKA_ROUTE =
            listOf(
                ExerciseRouteLocationData(0, 67.13740, -179.9200),
                ExerciseRouteLocationData(12, 67.13695, -179.95765),
                ExerciseRouteLocationData(40, 67.15066, -179.94647),
                ExerciseRouteLocationData(48, 67.15236, 179.96876),
                ExerciseRouteLocationData(60, 67.13841, 179.95380),
                ExerciseRouteLocationData(78, 67.11923, -179.97705),
                ExerciseRouteLocationData(79, 67.11876, -179.87888),
                ExerciseRouteLocationData(93, 67.12671, -179.80387),
            )

        // https://screenshot.googleplex.com/7cNkdqkF2arM926
        private val ANTARCTICA_ROUTE =
            listOf(
                ExerciseRouteLocationData(0, -81.45264, 57.52812),
                ExerciseRouteLocationData(12, -81.46467, 57.65451),
                ExerciseRouteLocationData(40, -81.47302, 57.56526),
                ExerciseRouteLocationData(48, -81.48638, 57.61308),
                ExerciseRouteLocationData(60, -81.49360, 57.28638),
                ExerciseRouteLocationData(78, -81.51240, 57.11263),
                ExerciseRouteLocationData(79, -81.51401, 56.88966),
                ExerciseRouteLocationData(93, -81.50867, 56.70117),
                ExerciseRouteLocationData(94, -81.53209, 56.56434),
                ExerciseRouteLocationData(100, -81.54088, 56.80689),
                ExerciseRouteLocationData(102, -81.54123, 56.80921),
                ExerciseRouteLocationData(105, -81.56274, 56.90045),
                ExerciseRouteLocationData(109, -81.57542, 56.83663),
                ExerciseRouteLocationData(111, -81.61755, 55.72278),
                ExerciseRouteLocationData(112, -81.61605, 55.50406),
            )

        private val NOT_RECTANGLE_ROUTE =
            listOf(
                ExerciseRouteLocationData(0, 80.0, 0.0),
                ExerciseRouteLocationData(12, 88.0, 1.0),
                ExerciseRouteLocationData(40, 89.0, 1.0),
                ExerciseRouteLocationData(48, 89.0, 0.0),
            )

        val routeDataMap: Map<String, List<ExerciseRouteLocationData>> =
            hashMapOf(
                "Warsaw" to WARSAW_ROUTE,
                "London" to LONDON_ROUTE,
                "Kosciuszko" to KOSCIUSZKO_ROUTE,
                "Chukota" to CHUKOTKA_ROUTE,
                "Antarctica" to ANTARCTICA_ROUTE,
                "Not rectangle" to NOT_RECTANGLE_ROUTE)

        fun generateExerciseRouteFromLocations(
            locationsData: List<ExerciseRouteLocationData>,
            start: Long,
        ): ExerciseRoute {
            val locations: ArrayList<Location> = ArrayList()
            for (locationsDatum in locationsData) {
                locations.add(
                    Location.Builder(
                            Instant.ofEpochMilli(start + locationsDatum.secondsToAdd),
                            locationsDatum.latitude,
                            locationsDatum.longitude)
                        .build())
            }
            return ExerciseRoute(locations)
        }
    }
}
