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

package android.tools.integration

import android.tools.flicker.datastore.CachedResultReader
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.io.RunStatus
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TEST_SCENARIO
import com.google.common.truth.Truth
import java.io.File
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.mockito.junit.MockitoJUnitRunner

/**
 * Contains an integration test
 *
 * To run this test: `atest FlickerLibTest:IntegrationTests`
 */
@RunWith(MockitoJUnitRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NoErrorTest {
    private var assertionExecuted = false
    private val testParam = LegacyFlickerTest().also { it.initialize(TEST_SCENARIO.testClass) }

    @Before
    fun setup() {
        assertionExecuted = false
    }

    @Test
    fun executesTransition() {
        Truth.assertWithMessage("Transition executed").that(transitionExecuted).isTrue()
    }

    @Test
    fun assertSetupAndTearDownTestAppNeverExists() {
        assertPredicatePasses {
            testParam.assertWm {
                assertionExecuted = true
                require(
                    this.subjects.none {
                        it.wmState
                            .getActivitiesForWindow(Utils.setupAndTearDownTestApp.componentMatcher)
                            .isNotEmpty()
                    }
                ) {
                    "${Utils.setupAndTearDownTestApp.appName} window existed at some point " +
                        "but shouldn't have."
                }
            }
        }
    }

    @Test
    fun assertTransitionTestAppExistsAtSomePoint() {
        assertPredicatePasses {
            testParam.assertWm {
                assertionExecuted = true
                require(
                    this.subjects.any {
                        it.wmState
                            .getActivitiesForWindow(Utils.transitionTestApp.componentMatcher)
                            .isNotEmpty()
                    }
                ) {
                    "${Utils.transitionTestApp.appName} window didn't exist at any point " +
                        "but should have."
                }
            }
        }
    }

    @Test
    fun assertSetupAndTearDownTestLayerNeverExists() {
        assertPredicatePasses {
            testParam.assertLayers {
                assertionExecuted = true
                require(
                    this.subjects.none {
                        Utils.setupAndTearDownTestApp.componentMatcher.layerMatchesAnyOf(
                            it.entry.flattenedLayers.filter { layer -> layer.isVisible }
                        )
                    }
                ) {
                    "${Utils.setupAndTearDownTestApp.appName} layer was visible at some point " +
                        "but shouldn't have."
                }

                require(
                    this.subjects.none {
                        Utils.setupAndTearDownTestApp.componentMatcher.layerMatchesAnyOf(
                            it.entry.flattenedLayers
                        )
                    }
                ) {
                    "${Utils.setupAndTearDownTestApp.appName} layer existed at some point " +
                        "but shouldn't have."
                }
            }
        }
    }

    @Test
    fun assertTransitionTestLayerExistsAtSomePoint() {
        assertPredicatePasses {
            testParam.assertLayers {
                assertionExecuted = true
                require(
                    this.subjects.any {
                        Utils.transitionTestApp.componentMatcher.layerMatchesAnyOf(
                            it.entry.flattenedLayers
                        )
                    }
                ) {
                    "${Utils.transitionTestApp.appName} layer didn't exist at any point " +
                        "but should have."
                }
            }
        }
    }

    @Test
    fun assertStartStateLayersIsNotEmpty() {
        assertPredicatePasses {
            testParam.assertLayersStart {
                assertionExecuted = true
                isNotEmpty()
            }
        }
    }

    @Test
    fun assertEndStateLayersIsNotEmpty() {
        assertPredicatePasses {
            testParam.assertLayersEnd {
                assertionExecuted = true
                isNotEmpty()
            }
        }
    }

    @Test
    fun assertStartStateWmIsNotEmpty() {
        assertPredicatePasses {
            testParam.assertWmStart {
                assertionExecuted = true
                isNotEmpty()
            }
        }
    }

    @Test
    fun assertEndStateWmIsNotEmpty() {
        assertPredicatePasses {
            testParam.assertWmEnd {
                assertionExecuted = true
                isNotEmpty()
            }
        }
    }

    @Test
    fun assertTagStateLayersIsNotEmpty() {
        assertPredicatePasses {
            testParam.assertLayersTag(Utils.TAG) {
                assertionExecuted = true
                isNotEmpty()
            }
        }
    }

    @Test
    fun assertTagStateWmIsNotEmpty() {
        assertPredicatePasses {
            testParam.assertWmTag(Utils.TAG) {
                assertionExecuted = true
                isNotEmpty()
            }
        }
    }

    @Test
    fun assertEventLog() {
        assertPredicatePasses { testParam.assertEventLog { assertionExecuted = true } }
    }

    private fun assertPredicatePasses(predicate: () -> Unit) {
        predicate.invoke()
        Truth.assertWithMessage("Executed").that(assertionExecuted).isTrue()
        val reader =
            android.tools.flicker.datastore.CachedResultReader(
                TEST_SCENARIO,
                TRACE_CONFIG_REQUIRE_CHANGES
            )
        Truth.assertWithMessage("Run status")
            .that(reader.runStatus)
            .isEqualTo(RunStatus.ASSERTION_SUCCESS)
        assertArtifactExists()
    }

    private fun assertArtifactExists() {
        val reader =
            android.tools.flicker.datastore.CachedResultReader(
                TEST_SCENARIO,
                TRACE_CONFIG_REQUIRE_CHANGES
            )
        val file = File(reader.artifactPath)
        Truth.assertWithMessage("Files exist").that(file.exists()).isTrue()
    }

    companion object {
        private var transitionExecuted = false
        @BeforeClass
        @JvmStatic
        fun runTransition() = Utils.runTransition { transitionExecuted = true }

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
