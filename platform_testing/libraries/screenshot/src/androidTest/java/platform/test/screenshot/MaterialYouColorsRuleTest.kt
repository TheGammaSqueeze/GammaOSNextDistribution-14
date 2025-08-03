package platform.test.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialYouColorsRuleTest {
    private val colors = MaterialYouColors.GreenBlue

    @get:Rule val colorsRule = MaterialYouColorsRule(colors)

    @Test
    fun testApplyMaterialYouColors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Assert that all colors are the same.
        val firstResourceId = android.R.color.system_neutral1_0
        val lastResourceId = android.R.color.system_accent3_1000
        val nColors = lastResourceId - firstResourceId + 1
        val resourceIds = List(nColors) { firstResourceId + it }

        val resources = context.resources
        val expectedColors = resourceIds.map { colors.colors[it] }.toTypedArray()
        val actualColors = resourceIds.map { resources.getColor(it) }.toTypedArray()

        Assert.assertArrayEquals(expectedColors, actualColors)
    }
}
