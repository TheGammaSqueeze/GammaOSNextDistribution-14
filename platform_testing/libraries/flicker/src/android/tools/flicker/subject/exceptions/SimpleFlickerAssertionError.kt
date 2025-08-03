package android.tools.flicker.subject.exceptions

class SimpleFlickerAssertionError(message: String) : FlickerAssertionError() {
    override val messageBuilder = ExceptionMessageBuilder().setMessage(message)
}
