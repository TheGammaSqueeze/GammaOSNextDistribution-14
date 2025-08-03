/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.tools.traces.parsers.perfetto

import android.tools.Logger
import android.tools.Timestamp
import android.tools.datatypes.ActiveBuffer
import android.tools.datatypes.Color
import android.tools.datatypes.Matrix33
import android.tools.datatypes.Rect
import android.tools.datatypes.RectF
import android.tools.datatypes.Region
import android.tools.datatypes.Size
import android.tools.parsers.AbstractTraceParser
import android.tools.traces.surfaceflinger.Display
import android.tools.traces.surfaceflinger.HwcCompositionType
import android.tools.traces.surfaceflinger.Layer
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.surfaceflinger.LayerTraceEntryBuilder
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.surfaceflinger.Transform
import android.tools.traces.surfaceflinger.Transform.Companion.isFlagClear
import android.tools.traces.surfaceflinger.Transform.Companion.isFlagSet

/** Parser for [LayersTrace] */
class LayersTraceParser(
    private val ignoreLayersStackMatchNoDisplay: Boolean = true,
    private val ignoreLayersInVirtualDisplay: Boolean = true,
    private val orphanLayerCallback: ((Layer) -> Boolean)? = null,
) : AbstractTraceParser<TraceProcessorSession, LayerTraceEntry, LayerTraceEntry, LayersTrace>() {

    override val traceName = "Layers trace (SF)"

    override fun createTrace(entries: Collection<LayerTraceEntry>): LayersTrace {
        return LayersTrace(entries)
    }

    override fun doDecodeByteArray(bytes: ByteArray): TraceProcessorSession {
        error("This parser can only read from perfetto trace processor")
    }

    override fun shouldParseEntry(entry: LayerTraceEntry) = true

    override fun getEntries(input: TraceProcessorSession): List<LayerTraceEntry> {
        val realToMonotonicTimeOffsetNs =
            queryRealToMonotonicTimeOffsetNs(input, "surfaceflinger_layers_snapshot")

        return input.query(getSqlQuerySnapshots()) { snapshotsRows ->
            val traceEntries = mutableListOf<LayerTraceEntry>()
            val snapshotGroups = snapshotsRows.groupBy { it["snapshot_id"] }

            for (snapshotId in 0L until snapshotGroups.size) {
                Logger.withTracing("query + build entry") {
                    val layerRows =
                        Logger.withTracing("query layer rows") {
                            input.query(getSqlQueryLayers(snapshotId)) { it }
                        }
                    Logger.withTracing("build entry") {
                        val snapshotRows = snapshotGroups[snapshotId]!!
                        val entry =
                            buildTraceEntry(snapshotRows, layerRows, realToMonotonicTimeOffsetNs)
                        traceEntries.add(entry)
                    }
                }
            }

            traceEntries
        }
    }

    override fun getTimestamp(entry: LayerTraceEntry): Timestamp = entry.timestamp

    override fun onBeforeParse(input: TraceProcessorSession) {}

    override fun doParseEntry(entry: LayerTraceEntry) = entry

    private fun buildTraceEntry(
        snapshotRows: List<Row>,
        layersRows: List<Row>,
        realToMonotonicTimeOffsetNs: Long
    ): LayerTraceEntry {
        val snapshotArgs = Args.build(snapshotRows)
        val displays = snapshotArgs.getChildren("displays")?.map { newDisplay(it) } ?: emptyList()

        val idAndLayers =
            layersRows
                .groupBy { it["layer_id"].toString() }
                .map { (layerId, layerRows) -> Pair(layerId, newLayer(Args.build(layerRows))) }
                .toMutableList()
        idAndLayers.sortBy { it.first.toLong() }

        val layers = idAndLayers.map { it.second }

        return LayerTraceEntryBuilder()
            .setElapsedTimestamp(snapshotArgs.getChild("elapsed_realtime_nanos")?.getLong() ?: 0L)
            .setRealToElapsedTimeOffsetNs(realToMonotonicTimeOffsetNs)
            .setLayers(layers)
            .setDisplays(displays)
            .setVSyncId(snapshotArgs.getChild("vsync_id")?.getLong() ?: 0L)
            .setHwcBlob(snapshotArgs.getChild("hwc_blob")?.getString() ?: "")
            .setWhere(snapshotArgs.getChild("where")?.getString() ?: "")
            .setOrphanLayerCallback(orphanLayerCallback)
            .ignoreLayersStackMatchNoDisplay(ignoreLayersStackMatchNoDisplay)
            .ignoreVirtualDisplay(ignoreLayersInVirtualDisplay)
            .build()
    }

    companion object {
        private fun getSqlQuerySnapshots(): String {
            return """
                SELECT
                    sfs.id AS snapshot_id,
                    sfs.ts as ts,
                    args.key as key,
                    args.display_value as value,
                    args.value_type as value_type
                FROM surfaceflinger_layers_snapshot AS sfs
                INNER JOIN args ON sfs.arg_set_id = args.arg_set_id;
            """
                .trimIndent()
        }

        private fun getSqlQueryLayers(snapshotId: Long): String {
            return """
                SELECT
                    sfl.snapshot_id,
                    sfl.id as layer_id,
                    args.key as key,
                    args.display_value as value,
                    args.value_type
                FROM
                    surfaceflinger_layer as sfl
                INNER JOIN args ON sfl.arg_set_id = args.arg_set_id
                WHERE snapshot_id = $snapshotId;
            """
                .trimIndent()
        }

        private fun newLayer(layer: Args, excludeCompositionState: Boolean = false): Layer {
            // Differentiate between the cases when there's no HWC data on
            // the trace, and when the visible region is actually empty
            val activeBuffer = newActiveBuffer(layer.getChild("active_buffer"))
            val visibleRegion = newRegion(layer.getChild("visible_region")) ?: Region.EMPTY
            val crop = newCropRect(layer.getChild("crop"))
            return Layer.from(
                layer.getChild("name")?.getString() ?: "",
                layer.getChild("id")?.getInt() ?: 0,
                layer.getChild("parent")?.getInt() ?: 0,
                layer.getChild("z")?.getInt() ?: 0,
                visibleRegion,
                activeBuffer,
                layer.getChild("flags")?.getInt() ?: 0,
                newRectF(layer.getChild("bounds")),
                newColor(layer.getChild("color")),
                layer.getChild("is_opaque")?.getBoolean() ?: false,
                layer.getChild("shadow_radius")?.getFloat() ?: 0f,
                layer.getChild("corner_radius")?.getFloat() ?: 0f,
                newRectF(layer.getChild("screen_bounds")),
                newTransform(layer.getChild("transform"), position = layer.getChild("position")),
                layer.getChild("curr_frame")?.getLong() ?: -1,
                layer.getChild("effective_scaling_mode")?.getInt() ?: 0,
                newTransform(layer.getChild("buffer_transform"), position = null),
                newHwcCompositionType(layer.getChild("hwc_composition_type")),
                layer.getChild("background_blur_radius")?.getInt() ?: 0,
                crop,
                layer.getChild("is_relative_of")?.getBoolean() ?: false,
                layer.getChild("z_order_relative_of")?.getInt() ?: 0,
                layer.getChild("layer_stack")?.getInt() ?: 0,
                excludeCompositionState
            )
        }

        private fun newDisplay(display: Args): Display {
            return Display.from(
                display.getChild("id")?.getLong() ?: 0L,
                display.getChild("name")?.getString() ?: "",
                display.getChild("layer_stack")?.getInt() ?: 0,
                newSize(display.getChild("size")),
                newRect(display.getChild("layer_stack_space_rect")),
                newTransform(display.getChild("transform"), position = null),
                display.getChild("is_virtual")?.getBoolean() ?: false,
                display.getChild("dpi_x")?.getFloat()?.toDouble() ?: 0.0,
                display.getChild("dpi_y")?.getFloat()?.toDouble() ?: 0.0,
            )
        }

        private fun newRectF(rectf: Args?): RectF {
            if (rectf == null) {
                return RectF.EMPTY
            }
            return RectF.from(
                left = rectf.getChild("left")?.getFloat() ?: 0f,
                top = rectf.getChild("top")?.getFloat() ?: 0f,
                right = rectf.getChild("right")?.getFloat() ?: 0f,
                bottom = rectf.getChild("bottom")?.getFloat() ?: 0f
            )
        }

        private fun newSize(sizeArgs: Args?): Size {
            if (sizeArgs == null) {
                return Size.EMPTY
            }
            return Size.from(
                sizeArgs.getChild("w")?.getInt() ?: 0,
                sizeArgs.getChild("h")?.getInt() ?: 0
            )
        }

        private fun newColor(color: Args?): Color {
            if (color == null) {
                return Color.EMPTY
            }
            return Color.from(
                color.getChild("r")?.getFloat() ?: 0f,
                color.getChild("g")?.getFloat() ?: 0f,
                color.getChild("b")?.getFloat() ?: 0f,
                color.getChild("a")?.getFloat() ?: 0f
            )
        }

        private fun newActiveBuffer(buffer: Args?): ActiveBuffer {
            if (buffer == null) {
                return ActiveBuffer.EMPTY
            }
            return ActiveBuffer.from(
                buffer.getChild("width")?.getInt() ?: 0,
                buffer.getChild("height")?.getInt() ?: 0,
                buffer.getChild("stride")?.getInt() ?: 0,
                buffer.getChild("format")?.getInt() ?: 0
            )
        }

        private fun newHwcCompositionType(value: Args?): HwcCompositionType {
            if (value == null || !value.isString()) {
                return HwcCompositionType.HWC_TYPE_UNRECOGNIZED
            }
            return HwcCompositionType.valueOf(value.getString())
        }

        private fun newCropRect(crop: Args?): Rect? {
            if (crop == null) {
                return Rect.EMPTY
            }

            val right = crop.getChild("right")?.getInt() ?: 0
            val left = crop.getChild("left")?.getInt() ?: 0
            val bottom = crop.getChild("bottom")?.getInt() ?: 0
            val top = crop.getChild("top")?.getInt() ?: 0

            // crop (0,0) (-1,-1) means no crop
            if (right == -1 && left == 0 && bottom == -1 && top == 0) {
                return null
            }

            return Rect.from(left, top, right, bottom)
        }

        private fun newRegion(region: Args?): Region? {
            if (region == null) {
                return null
            }
            val rects = region.getChildren("rect")?.map { newRect(it) } ?: emptyList()
            return Region(rects.toTypedArray())
        }

        private fun newRect(rect: Args?): Rect =
            Rect.from(
                rect?.getChild("left")?.getInt() ?: 0,
                rect?.getChild("top")?.getInt() ?: 0,
                rect?.getChild("right")?.getInt() ?: 0,
                rect?.getChild("bottom")?.getInt() ?: 0
            )

        private fun newTransform(transform: Args?, position: Args?) =
            Transform.from(transform?.getChild("type")?.getInt(), getMatrix(transform, position))

        private fun getMatrix(transform: Args?, position: Args?): Matrix33 {
            val x = position?.getChild("x")?.getFloat() ?: 0f
            val y = position?.getChild("y")?.getFloat() ?: 0f

            if (
                transform == null ||
                    Transform.isSimpleTransform(transform.getChild("type")?.getInt())
            ) {
                return transform?.getChild("type")?.getInt().getDefaultTransform(x, y)
            }

            return Matrix33.from(
                transform.getChild("dsdx")?.getFloat() ?: 0f,
                transform.getChild("dtdx")?.getFloat() ?: 0f,
                x,
                transform.getChild("dsdy")?.getFloat() ?: 0f,
                transform.getChild("dtdy")?.getFloat() ?: 0f,
                y
            )
        }

        private fun Int?.getDefaultTransform(x: Float, y: Float): Matrix33 {
            return when {
                // IDENTITY
                this == null -> Matrix33.identity(x, y)
                // // ROT_270 = ROT_90|FLIP_H|FLIP_V
                isFlagSet(Transform.ROT_90_VAL or Transform.FLIP_V_VAL or Transform.FLIP_H_VAL) ->
                    Matrix33.rot270(x, y)
                // ROT_180 = FLIP_H|FLIP_V
                isFlagSet(Transform.FLIP_V_VAL or Transform.FLIP_H_VAL) -> Matrix33.rot180(x, y)
                // ROT_90
                isFlagSet(Transform.ROT_90_VAL) -> Matrix33.rot90(x, y)
                // IDENTITY
                isFlagClear(Transform.SCALE_VAL or Transform.ROTATE_VAL) -> Matrix33.identity(x, y)
                else -> throw IllegalStateException("Unknown transform type $this")
            }
        }
    }
}
