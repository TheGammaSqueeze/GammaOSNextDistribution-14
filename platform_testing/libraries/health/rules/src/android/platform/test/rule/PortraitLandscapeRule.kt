package android.platform.test.rule

import android.platform.test.rule.Orientation.LANDSCAPE
import android.platform.test.rule.Orientation.PORTRAIT
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Makes each test of the class that uses this rule execute twice, in [Orientation.LANDSCAPE] and
 * [Orientation.PORTRAIT] orientation.
 */
class PortraitLandscapeRule(
    private val portraitDeviceFilter: List<DeviceTypeFilter> = listOf(DeviceTypeFilter.ANY),
    private val landscapeDeviceFilter: List<DeviceTypeFilter> = listOf(DeviceTypeFilter.ANY)
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                try {
                    if (portraitDeviceFilter.any { it.match() }) {
                        base.runInOrientation(PORTRAIT)
                    }
                    if (landscapeDeviceFilter.any { it.match() }) {
                        base.runInOrientation(LANDSCAPE)
                    }
                } finally {
                    RotationUtils.clearOrientationOverride()
                }
            }
        }

    private fun Statement.runInOrientation(orientation: Orientation) {
        RotationUtils.setOrientationOverride(orientation)
        try {
            evaluate()
        } catch (e: Throwable) {
            throw Exception("Test failed while in $orientation", e)
        }
    }
}
