package android.platform.helpers.foldable

import android.graphics.Point
import android.platform.uiautomator_helpers.DeviceHelpers.uiDevice
import android.platform.uiautomator_helpers.WaitUtils.waitFor
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration

/**
 * Utilities to write tests for moving icons towards the center, as happens during the fold
 * animation.
 */
object UnfoldAnimationTestingUtils {

    /**
     * Returns icon positions after any one of them is moved.
     *
     * [currentProvider] returned icons are compared with [initialIcons]. As soon as any moved, the
     * new set is returned.
     */
    fun getIconPositionsAfterAnyIconMove(
        initialIcons: Set<Icon>,
        currentProvider: () -> Set<Icon>
    ): Set<Icon> {
        return waitFor("Icons moving", Duration.ofSeconds(20)) {
            val newIcons = currentProvider()
            val moved =
                commonIcons(initialIcons, newIcons).any { (initial, new) ->
                    initial.position != new.position
                }
            return@waitFor if (moved) newIcons else null
        }
    }

    /**
     * Waits until the positions returned by [currentProvider] match [expected].
     *
     * Only common icons between the 2 sets are considered. Thows if there is no intersection.
     */
    fun waitForPositionsToMatch(expected: Set<Icon>, currentProvider: () -> Set<Icon>) {
        waitFor("Icons matching expected positions") {
            val allMoved =
                commonIcons(expected, currentProvider()).all { (expected, current) ->
                    expected.position == current.position
                }
            return@waitFor if (allMoved) true else null
        }
    }

    /** Returns icons in both [setA] and [setB], comparing only icon name. */
    fun commonIcons(setA: Set<Icon>, setB: Set<Icon>): Set<Pair<Icon, Icon>> {
        return commonNames(setA, setB)
            .also {
                assertWithMessage("Empty intersection between $setA and $setB")
                    .that(it)
                    .isNotEmpty()
            }
            .map { name ->
                val aIcon = setA.findWithName(name)
                val bIcon = setB.findWithName(name)
                aIcon to bIcon
            }
            .toSet()
    }

    private fun commonNames(setA: Set<Icon>, setB: Set<Icon>): Set<String> {
        return setA.map { it.name }.intersect(setB.map { it.name }.toSet())
    }

    private fun Set<Icon>.findWithName(iconName: String): Icon {
        return find { it.name == iconName } ?: error("Icon with name $iconName not found in $this")
    }

    /**
     * Asserts [coordinate] (x or y)coordinate of [new] icon has moved towards the center compared
     * to [old].
     */
    fun assertIconMovedTowardsTheCenter(old: Icon, new: Icon, expect: Expect, axis: Axis) {
        assertWithMessage("Comparing icons with different names").that(old.name).isEqualTo(new.name)
        val oldPosition = if (axis == Axis.X) old.position.x else old.position.y
        val newPosition = if (axis == Axis.X) new.position.x else new.position.y
        val expectThatOld =
            expect.withMessage("Icon: ${old.name} didn't move towards the center").that(oldPosition)
        if (oldPosition < uiDevice.displayWidth / 2) {
            expectThatOld.isLessThan(newPosition)
        } else {
            expectThatOld.isGreaterThan(newPosition)
        }
    }

    /** Describes axis (x or y) of a View */
    enum class Axis {
        X,
        Y
    }

    /**
     * Represent a UI element with a position, used in [UnfoldAnimationTestingUtils] for foldable
     * animation testing.
     */
    data class Icon(val name: String, val position: Point)
}
