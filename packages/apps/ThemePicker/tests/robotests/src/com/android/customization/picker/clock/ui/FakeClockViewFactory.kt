package com.android.customization.picker.clock.ui

import android.content.res.Resources
import android.view.View
import androidx.lifecycle.LifecycleOwner
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository
import com.android.customization.picker.clock.ui.FakeClockViewFactory.Companion.fakeClocks
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.systemui.plugins.clocks.ClockConfig
import com.android.systemui.plugins.clocks.ClockController
import com.android.systemui.plugins.clocks.ClockEvents
import com.android.systemui.plugins.clocks.ClockFaceController
import java.io.PrintWriter

/**
 * This is a fake [ClockViewFactory]. Only implement the function if it's actually called in a test.
 */
class FakeClockViewFactory(
    val clockControllers: MutableMap<String, ClockController> = fakeClocks.toMutableMap(),
) : ClockViewFactory {

    class FakeClockController(
        override var config: ClockConfig,
    ) : ClockController {
        override val smallClock: ClockFaceController
            get() = TODO("Not yet implemented")

        override val largeClock: ClockFaceController
            get() = TODO("Not yet implemented")

        override val events: ClockEvents
            get() = TODO("Not yet implemented")

        override fun initialize(resources: Resources, dozeFraction: Float, foldFraction: Float) =
            TODO("Not yet implemented")

        override fun dump(pw: PrintWriter) = TODO("Not yet implemented")
    }

    override fun getController(clockId: String): ClockController = clockControllers.get(clockId)!!

    override fun getLargeView(clockId: String): View {
        TODO("Not yet implemented")
    }

    override fun getSmallView(clockId: String): View {
        TODO("Not yet implemented")
    }

    override fun updateColorForAllClocks(seedColor: Int?) {
        TODO("Not yet implemented")
    }

    override fun updateColor(clockId: String, seedColor: Int?) {
        TODO("Not yet implemented")
    }

    override fun updateRegionDarkness() {
        TODO("Not yet implemented")
    }

    override fun updateTimeFormat(clockId: String) {
        TODO("Not yet implemented")
    }

    override fun registerTimeTicker(owner: LifecycleOwner) {
        TODO("Not yet implemented")
    }

    override fun onDestroy() {
        TODO("Not yet implemented")
    }

    override fun unregisterTimeTicker(owner: LifecycleOwner) {
        TODO("Not yet implemented")
    }

    companion object {
        val fakeClocks =
            FakeClockPickerRepository.fakeClocks
                .map { clock ->
                    clock.clockId to
                        FakeClockController(
                            ClockConfig(
                                id = clock.clockId,
                                name = "Name: ${clock.clockId}",
                                description = "Desc: ${clock.clockId}"
                            )
                        )
                }
                .toMap()
    }
}
