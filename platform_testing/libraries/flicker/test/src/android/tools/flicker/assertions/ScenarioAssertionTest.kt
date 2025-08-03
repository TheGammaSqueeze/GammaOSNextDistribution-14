package android.tools.flicker.assertions

import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.subject.exceptions.SimpleFlickerAssertionError
import android.tools.io.Reader
import com.google.common.truth.Truth
import org.junit.Test
import org.mockito.Mockito

class ScenarioAssertionTest {
    @Test
    fun addsExtraDataToFlickerAssertionMessage() {
        val mockReader = Mockito.mock(Reader::class.java)
        val mockAssertionData = Mockito.mock(AssertionData::class.java)
        val mockAssertionRunner = Mockito.mock(AssertionRunner::class.java)

        val scenarioAssertion =
            ScenarioAssertionImpl(
                name = "My Assertion",
                reader = mockReader,
                assertionData = listOf(mockAssertionData),
                stabilityGroup = AssertionInvocationGroup.BLOCKING,
                assertionExtraData = mapOf("extraKey" to "extraValue"),
                assertionRunner = mockAssertionRunner
            )

        Mockito.`when`(mockAssertionRunner.runAssertion(mockAssertionData))
            .thenReturn(SimpleFlickerAssertionError("My assertion"))

        val assertionResult = scenarioAssertion.execute()

        Truth.assertThat(assertionResult.assertionErrors).hasSize(1)
        val assertionMessage = assertionResult.assertionErrors.first().message
        Truth.assertThat(assertionMessage).contains("My assertion")
        Truth.assertThat(assertionMessage).contains("extraKey")
        Truth.assertThat(assertionMessage).contains("extraValue")
    }
}
