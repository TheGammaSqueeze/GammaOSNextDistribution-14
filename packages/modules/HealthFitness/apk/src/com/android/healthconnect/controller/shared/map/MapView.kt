/**
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.shared.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.health.connect.datatypes.ExerciseRoute
import android.health.connect.datatypes.ExerciseRoute.Location
import android.util.AttributeSet
import android.view.View
import com.android.settingslib.widget.theme.R
import java.lang.Math.toDegrees
import java.lang.Math.toRadians
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A view displaying a path given an exercise route. */
class MapView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private val mapBounds: RectF = RectF()
    private val routeBounds: RectF =
        RectF(Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MAX_VALUE)
    private val route: MutableList<Location> = mutableListOf()
    private val paint: Paint
    private val startPaint: Paint

    init {
        val baseColor = context.getColor(R.color.settingslib_text_color_primary)
        paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = baseColor
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }

        startPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = baseColor
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = 4f
                style = Paint.Style.FILL_AND_STROKE
            }

        setWillNotDraw(false)
    }

    fun setRoute(route: ExerciseRoute) {
        routeBounds.set(Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MAX_VALUE)
        this.route.clear()
        this.route.addAll(route.routeLocations)
        this.route.sortBy { location -> location.time }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        mapBounds.set(
            width * PADDING, height * PADDING, width * (1 - PADDING), height * (1 - PADDING))
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawRoute(canvas)
        setBackgroundColor(context.getColor(R.color.settingslib_colorSurfaceVariant))
    }

    private fun drawRoute(canvas: Canvas) {
        if (route.isEmpty()) {
            return
        }
        val average = average()
        val adjustedRoute =
            route
                .map { location ->
                    var latitude = (location.latitude - average.first + 180) % 180
                    if (latitude > 90) latitude -= 180
                    var longitude = (location.longitude - average.second + 360) % 360
                    if (longitude > 180) longitude -= 360
                    Pair(latitude, longitude)
                }
                .toList()

        adjustedRoute.forEach { point ->
            val lat = point.first.toFloat()
            val lon = point.second.toFloat()
            routeBounds.set(
                min(routeBounds.left, lon),
                max(routeBounds.top, lat),
                max(routeBounds.right, lon),
                min(routeBounds.bottom, lat))
        }
        var previous = translate(adjustedRoute[0])

        adjustedRoute.forEach { point ->
            val current = translate(point)
            canvas.drawLine(previous.first, previous.second, current.first, current.second, paint)
            previous = current
        }
        val start = translate(adjustedRoute[0])
        val end = translate(adjustedRoute[adjustedRoute.size - 1])
        canvas.drawCircle(start.first, start.second, 4f, startPaint)
        if (!start.equals(end)) {
            canvas.drawCircle(end.first, end.second, 4f, startPaint)
        }
    }

    private fun translate(point: Pair<Double, Double>): Pair<Float, Float> {
        val yRatio = (point.first - routeBounds.top) / (routeBounds.bottom - routeBounds.top)
        val xRatio = (point.second - routeBounds.left) / (routeBounds.right - routeBounds.left)
        val mapX = xRatio * (mapBounds.right - mapBounds.left) + mapBounds.left
        val mapY = yRatio * (mapBounds.bottom - mapBounds.top) + mapBounds.top
        return Pair(mapX.toFloat(), mapY.toFloat())
    }

    private fun average(): Pair<Double, Double> {
        var x = 0.0
        var y = 0.0
        var z = 0.0

        route.forEach { location ->
            x += cos(toRadians(location.latitude)) * cos(toRadians(location.longitude))
            y += cos(toRadians(location.latitude)) * sin(toRadians(location.longitude))
            z += sin(toRadians(location.latitude))
        }
        val r = sqrt(x * x + y * y + z * z)
        if (r == 0.0) {
            return Pair(0.0, 0.0)
        }
        return Pair(toDegrees(asin(z / r)), toDegrees(atan2(y, x)))
    }

    companion object {
        private const val PADDING = 0.2f
    }
}
