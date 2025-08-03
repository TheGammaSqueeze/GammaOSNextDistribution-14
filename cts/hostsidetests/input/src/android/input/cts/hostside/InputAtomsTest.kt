/*
 * Copyright 2023 The Android Open Source Project
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
package android.input.cts.hostside

import android.cts.statsdatom.lib.AtomTestUtils
import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.DeviceUtils
import android.cts.statsdatom.lib.ReportUtils
import android.input.InputDeviceBus
import android.input.InputDeviceUsageType
import android.input.KeyboardSystemEvent
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.PollingCheck
import com.android.os.AtomsProto.Atom
import com.android.os.StatsLog.EventMetricData
import com.android.os.input.InputDeviceUsageReported
import com.android.os.input.InputExtensionAtoms
import com.android.os.input.KeyboardConfigured
import com.android.os.input.KeyboardSystemsEventReported
import com.android.os.input.TouchpadUsage
import com.android.tradefed.testtype.DeviceTestCase
import com.android.tradefed.util.RunUtil
import com.google.protobuf.ExtensionRegistry
import java.util.concurrent.TimeUnit
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.hamcrest.core.IsEqual.equalTo

/**
 * Tests for input atom logging.
 *
 * Run via: atest CtsInputHostTestCases -c
 */
class InputAtomsTest : DeviceTestCase() {

    companion object {
        const val TEST_APP_PACKAGE = "android.input.cts.hostside.app"
        const val EMULATE_INPUT_DEVICE_CLASS = "$TEST_APP_PACKAGE.EmulateInputDevice"
    }

    private val registry: ExtensionRegistry = ExtensionRegistry.newInstance()

    override fun setUp() {
        super.setUp()
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())
        InputExtensionAtoms.registerAllExtensions(registry)
    }

    override fun tearDown() {
        super.tearDown()
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
    }

    @CddTest(requirements = ["6.1/C-0-10"])
    fun testInputDeviceUsageAtom() {
        val builder = ConfigUtils.createConfigBuilder("AID_NOBODY")
        ConfigUtils.addEventMetric(builder,
                InputExtensionAtoms.INPUTDEVICE_USAGE_REPORTED_FIELD_NUMBER)
        ConfigUtils.uploadConfig(device, builder)

        // Connect a touchscreen, use it for at least five seconds, and disconnect it.
        // This should result in an InputDeviceUsageReported atom being logged upon disconnection
        // that documents the device being used.
        DeviceUtils.runDeviceTests(
                device,
                TEST_APP_PACKAGE,
                EMULATE_INPUT_DEVICE_CLASS,
                "useTouchscreenForFiveSeconds")
        val minUsageDuration = 5_000
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())

        val data: List<EventMetricData> = ReportUtils.getEventMetricDataList(device, registry)

        assertThat("No InputDeviceUsageReported atoms logged!",
                data.size, greaterThanOrEqualTo(1))

        val matchesAtom = Matchers.allOf<InputDeviceUsageReported>(
                member("vendorId", { vendorId },
                        equalTo(0x18d1)),
                member("productId", { productId },
                        equalTo(0xabcd)),
                member("hasVersionId", { hasVersionId() },
                        equalTo(true)),
                member("deviceBus", { deviceBus },
                        equalTo(InputDeviceBus.USB)),
                member("usageDuration", { usageDurationMillis },
                        greaterThanOrEqualTo(minUsageDuration)),
                member("usageSourcesCount", { usageSourcesCount },
                        equalTo(1)),
                member("usageSources", { usageSourcesList },
                        hasItem(equalTo(InputDeviceUsageType.TOUCHSCREEN))),
                member("usageDurationsPerSourceCount", { usageDurationsPerSourceCount },
                        equalTo(1)),
                member("usageDurationsPerSource", { usageDurationsPerSourceList },
                        hasItem(greaterThanOrEqualTo(minUsageDuration))),
                member("uidsCount", { uidsCount },
                        greaterThanOrEqualTo(1)),
                member("usageDurationsPerUidCount", { usageDurationsPerUidCount },
                        greaterThanOrEqualTo(1)),
        )

        assertThat(data, hasItem<EventMetricData>(
                member("atom", { atom.getExtension(InputExtensionAtoms.inputdeviceUsageReported) },
                        matchesAtom)))
    }

    private fun getTouchpadUsageAtom(): TouchpadUsage {
        // Trigger atom pull.
        AtomTestUtils.sendAppBreadcrumbReportedAtom(device)
        var atoms: List<Atom> = listOf()
        PollingCheck.waitFor(TimeUnit.SECONDS.toMillis(5), {
            atoms = ReportUtils.getGaugeMetricAtoms(device, registry, false)
            atoms.size > 0
        })
        assertThat(atoms.size, equalTo(1))
        return atoms[0].getExtension(InputExtensionAtoms.touchpadUsage)
    }

    @CddTest(requirements = ["6.1/C-0-10"])
    fun testTouchpadUsageAtom_FingerAndPalmCounts() {
        setupTouchpadUsageConfig()

        DeviceUtils.runDeviceTests(
                device,
                TEST_APP_PACKAGE,
                EMULATE_INPUT_DEVICE_CLASS,
                "useTouchpadWithFingersAndPalms")

        val touchpadUsage = getTouchpadUsageAtom()
        assertThat(touchpadUsage, Matchers.allOf<TouchpadUsage>(
                member("vendorId", { vendorId }, equalTo(0x18d1)),
                member("productId", { productId }, equalTo(0xabcd)),
                member("deviceBus", { deviceBus },
                        equalTo(InputDeviceBus.USB)),
                member("fingerCount", { fingerCount }, equalTo(3)),
                member("palmCount", { palmCount }, equalTo(2)),
        ))
    }

    @CddTest(requirements = ["6.1/C-0-10"])
    fun testTouchpadUsageAtom_TwoFingerSwipeCount() {
        setupTouchpadUsageConfig()

        DeviceUtils.runDeviceTests(
            device,
            TEST_APP_PACKAGE,
            EMULATE_INPUT_DEVICE_CLASS,
            "twoFingerSwipeOnTouchpad"
        )

        val touchpadUsage = getTouchpadUsageAtom()
        assertThat(touchpadUsage, Matchers.allOf<TouchpadUsage>(
            member("twoFingerSwipeGestureCount", { twoFingerSwipeGestureCount }, equalTo(1)),
            member("threeFingerSwipeGestureCount", { threeFingerSwipeGestureCount }, equalTo(0)),
            member("fourFingerSwipeGestureCount", { fourFingerSwipeGestureCount }, equalTo(0)),
            member("pinchGestureCount", { pinchGestureCount }, equalTo(0)),
        ))
    }

    @CddTest(requirements = ["6.1/C-0-10"])
    fun testTouchpadUsageAtom_ThreeFingerSwipeCount() {
        setupTouchpadUsageConfig()

        DeviceUtils.runDeviceTests(
            device,
            TEST_APP_PACKAGE,
            EMULATE_INPUT_DEVICE_CLASS,
            "threeFingerSwipeOnTouchpad"
        )

        val touchpadUsage = getTouchpadUsageAtom()
        assertThat(touchpadUsage, Matchers.allOf<TouchpadUsage>(
            member("twoFingerSwipeGestureCount", { twoFingerSwipeGestureCount }, equalTo(0)),
            member("threeFingerSwipeGestureCount", { threeFingerSwipeGestureCount }, equalTo(1)),
            member("fourFingerSwipeGestureCount", { fourFingerSwipeGestureCount }, equalTo(0)),
            member("pinchGestureCount", { pinchGestureCount }, equalTo(0)),
        ))
    }

    @CddTest(requirements = ["6.1/C-0-10"])
    fun testTouchpadUsageAtom_FourFingerSwipeCount() {
        setupTouchpadUsageConfig()

        DeviceUtils.runDeviceTests(
            device,
            TEST_APP_PACKAGE,
            EMULATE_INPUT_DEVICE_CLASS,
            "fourFingerSwipeOnTouchpad"
        )

        val touchpadUsage = getTouchpadUsageAtom()
        assertThat(touchpadUsage, Matchers.allOf<TouchpadUsage>(
            member("twoFingerSwipeGestureCount", { twoFingerSwipeGestureCount }, equalTo(0)),
            member("threeFingerSwipeGestureCount", { threeFingerSwipeGestureCount }, equalTo(0)),
            member("fourFingerSwipeGestureCount", { fourFingerSwipeGestureCount }, equalTo(1)),
            member("pinchGestureCount", { pinchGestureCount }, equalTo(0)),
        ))
    }

    @CddTest(requirements = ["6.1/C-0-10"])
    fun testTouchpadUsageAtom_PinchCount() {
        setupTouchpadUsageConfig()

        DeviceUtils.runDeviceTests(
            device,
            TEST_APP_PACKAGE,
            EMULATE_INPUT_DEVICE_CLASS,
            "pinchOnTouchpad"
        )

        val touchpadUsage = getTouchpadUsageAtom()
        assertThat(touchpadUsage, Matchers.allOf<TouchpadUsage>(
            member("twoFingerSwipeGestureCount", { twoFingerSwipeGestureCount }, equalTo(0)),
            member("threeFingerSwipeGestureCount", { threeFingerSwipeGestureCount }, equalTo(0)),
            member("fourFingerSwipeGestureCount", { fourFingerSwipeGestureCount }, equalTo(0)),
            member("pinchGestureCount", { pinchGestureCount }, equalTo(1)),
        ))
    }

    private fun setupTouchpadUsageConfig() {
        val builder = ConfigUtils.createConfigBuilder("AID_NOBODY")
        ConfigUtils.addGaugeMetric(builder, InputExtensionAtoms.TOUCHPAD_USAGE_FIELD_NUMBER)
        ConfigUtils.uploadConfig(device, builder)

        // Trigger an atom pull and clear any returned atoms before running the rest of the test.
        // This avoids flakes caused by the daily touchpad usage atom causing multiple atoms to be
        // returned by getGaugeMetricAtoms later in the test.
        AtomTestUtils.sendAppBreadcrumbReportedAtom(device)
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())
        ReportUtils.getGaugeMetricAtoms(device, registry, false)
    }

    @CddTest(requirements = ["6.1/C-0-10"])
    fun testKeyboardConfiguredAtom() {
        val builder = ConfigUtils.createConfigBuilder("AID_NOBODY")
        ConfigUtils.addEventMetric(builder, InputExtensionAtoms.KEYBOARD_CONFIGURED_FIELD_NUMBER)
        ConfigUtils.uploadConfig(device, builder)

        DeviceUtils.runDeviceTests(
            device,
            TEST_APP_PACKAGE,
            EMULATE_INPUT_DEVICE_CLASS,
            "createKeyboardDevice"
        )
        val metricData = waitForMetricData()
        val matchesAtom = Matchers.allOf<KeyboardConfigured>(
            member(
                "vendorId",
                { vendorId },
                equalTo(0x18d1)
            ),
            member(
                "productId",
                { productId },
                equalTo(0xabcd)
            ),
        )

        assertThat(metricData, hasItem<EventMetricData>(
            member(
                "atom",
                { atom.getExtension(InputExtensionAtoms.keyboardConfigured) },
                matchesAtom
            )
        ))
    }

    @CddTest(requirements = ["6.1/C-0-10"])
    fun testKeyboardSystemEventsAtom() {
        val builder = ConfigUtils.createConfigBuilder("AID_NOBODY")
        ConfigUtils.addEventMetric(
            builder,
            InputExtensionAtoms.KEYBOARD_SYSTEMS_EVENT_REPORTED_FIELD_NUMBER
        )
        ConfigUtils.uploadConfig(device, builder)

        DeviceUtils.runDeviceTests(
            device,
            TEST_APP_PACKAGE,
            EMULATE_INPUT_DEVICE_CLASS,
            // We are using CAPS_LOCK as the "SystemsEvent" since, we feel it is very unlikely to be
            // changed by the OEMs or in future QPR releases. Other "SystemsEvents" especially
            // shortcuts are not enforced by other CTS tests, so using those here might cause CTS
            // compatibility issues.
            "createKeyboardDeviceAndSendCapsLockKey"
        )

        val metricData = waitForMetricData()
        val matchesAtom = Matchers.allOf<KeyboardSystemsEventReported>(
            member(
                "vendorId",
                { vendorId },
                equalTo(0x18d1)
            ),
            member(
                "productId",
                { productId },
                equalTo(0xabcd)
            ),
            member(
                "keyboardSystemsEvent",
                { keyboardSystemEvent },
                equalTo(KeyboardSystemEvent.TOGGLE_CAPS_LOCK)
            )
        )

        assertThat(metricData, hasItem<EventMetricData>(
            member(
                "atom",
                { atom.getExtension(InputExtensionAtoms.keyboardSystemsEventReported) },
                matchesAtom
            )
        ))
    }

    private fun waitForMetricData(): List<EventMetricData> {
        var metricData: List<EventMetricData> = listOf()
        PollingCheck.waitFor(TimeUnit.SECONDS.toMillis(5)) {
            metricData = ReportUtils.getEventMetricDataList(device, registry)
            metricData.isNotEmpty()
        }
        return metricData
    }

}

// Returns a matcher that helps match member variables of a class.
private fun <T, U> member(
        member: String,
        getMember: T.() -> U,
        matcher: Matcher<U>
): TypeSafeMatcher<T> =
        object : TypeSafeMatcher<T>() {

            override fun matchesSafely(item: T?): Boolean {
                return matcher.matches(item!!.getMember())
            }

            override fun describeMismatchSafely(item: T?, mismatchDescription: Description?) {
                matcher.describeMismatch(item, mismatchDescription)
            }

            override fun describeTo(description: Description?) {
                matcher.describeTo(description?.appendText("matches member $member"))
            }
        }
