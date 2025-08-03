package com.android.healthconnect.controller.tests.utils

import android.health.connect.datatypes.ExerciseRoute
import java.time.Instant

/** Test data for route rendering. */
object TestData {

    private val START = Instant.ofEpochMilli(1234567891011)

    // https://screenshot.googleplex.com/9MRH639gSzFpgzY
    public val WARSAW_ROUTE =
        listOf(
            ExerciseRoute.Location.Builder(START.plusSeconds(12), 52.26019, 21.02268).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(40), 52.26000, 21.02360).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(48), 52.25973, 21.02356).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(60), 52.25966, 21.02313).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(78), 52.25993, 21.02309).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(79), 52.25972, 21.02271).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(90), 52.25948, 21.02276).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(93), 52.25945, 21.02335).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(94), 52.25960, 21.02338).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(100), 52.25961, 21.02382).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(102), 52.25954, 21.02370).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(105), 52.25945, 21.02362).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(109), 52.25954, 21.02354).build(),
        )

    // https://screenshot.googleplex.com/7yVxQfJaETZcFPS
    public val LONDON_ROUTE =
        listOf(
            ExerciseRoute.Location.Builder(START, 51.53312, -0.15512).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(12), 51.53301, -0.15566).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(40), 51.53269, -0.15578).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(48), 51.53279, -0.15529).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(60), 51.53246, -0.15577).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(78), 51.53236, -0.15500).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(79), 51.53268, -0.15457).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(93), 51.53244, -0.15410).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(94), 51.53277, -0.15391).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(100), 51.53297, -0.15242).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(102), 51.53299, -0.15472).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(105), 51.53319, -0.15467).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(109), 51.53308, -0.15508).build(),
        )

    // https://screenshot.googleplex.com/ARzR6WNvkNct4Pt
    val KOSCIUSZKO_ROUTE =
        listOf(
            ExerciseRoute.Location.Builder(START, -36.46687, 148.25588).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(12), -36.46699, 148.25730).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(40), -36.46643, 148.25841).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(48), -36.46558, 148.25732).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(60), -36.46552, 148.25474).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(78), -36.46681, 148.25378).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(79), -36.46670, 148.25221).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(93), -36.46834, 148.25089).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(94), -36.46964, 148.25051).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(100), -36.47003, 148.25401).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(102), -36.46979, 148.25579).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(105), -36.46852, 148.25638).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(109), -36.46864, 148.25299).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(111), -36.46799, 148.25247).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(112), -36.46708, 148.25461).build(),
        )

    // https://screenshot.googleplex.com/9WLjATYv4z4YteD
    val CHUKOTKA_ROUTE =
        listOf(
            ExerciseRoute.Location.Builder(START, 67.13740, -179.9200).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(12), 67.13695, -179.95765).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(40), 67.15066, -179.94647).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(48), 67.15236, 179.96876).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(60), 67.13841, 179.95380).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(78), 67.11923, -179.97705).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(79), 67.11876, -179.87888).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(93), 67.12671, -179.80387).build(),
        )

    // https://screenshot.googleplex.com/7cNkdqkF2arM926
    val ANTARCTICA_ROUTE =
        listOf(
            ExerciseRoute.Location.Builder(START, -81.45264, 57.52812).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(12), -81.46467, 57.65451).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(40), -81.47302, 57.56526).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(48), -81.48638, 57.61308).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(60), -81.49360, 57.28638).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(78), -81.51240, 57.11263).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(79), -81.51401, 56.88966).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(93), -81.50867, 56.70117).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(94), -81.53209, 56.56434).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(100), -81.54088, 56.80689).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(102), -81.54123, 56.80921).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(105), -81.56274, 56.90045).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(109), -81.57542, 56.83663).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(111), -81.61755, 55.72278).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(112), -81.61605, 55.50406).build(),
        )

    val NOT_RECTANGLE_ROUTE =
        listOf(
            ExerciseRoute.Location.Builder(START, 80.0, 0.0).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(12), 88.0, 1.0).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(40), 89.0, 1.0).build(),
            ExerciseRoute.Location.Builder(START.plusSeconds(48), 89.0, 0.0).build(),
        )
}
