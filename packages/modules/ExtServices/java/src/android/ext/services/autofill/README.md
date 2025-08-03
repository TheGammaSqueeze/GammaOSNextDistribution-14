ExtServices module - Autofill
=============================

### AutofillFieldClassificationService
A service that calculates field classification scores. A field classification score is a float value
representing how well an AutofillValue filled matches an expected value predicted by an
AutofillService. The full match is 1.0 (representing 100%), while a full mismatch is 0.0.

### InlineSuggestionsRenderService
A service that renders an inline presentation view to be shown in the keyboard suggestion strip. The
service is called to render a View object holding the Inline Suggestion for the new Inline
Autofill flow. The default implementation for this renderer service calls into
androidx.autofill.inline.Renderer to render the suggestion.

### Test
- MTS
- CTS
  - run cts -m CtsAutoFillServiceTestCases -t android.autofillservice.cts
    .servicebehavior.FieldsClassificationTest
- Manual (for inline)
  1. Select the AutofillService from Settings (Password & Account -> AutofillService), you
can choose Google. Make sure you have passwords saved.
  2. Use Gboard as your keyboard
  3. Open the app that you have password saved in AutofillService, e.g. open Twitter and
   open login page
  4. Click the username field. The expected result is to see inline suggestion shown in
   the keyboard"
### Other resources
- [Field classification](https://developer.android.com/reference/android/service/autofill/AutofillService#metrics-and-field-classification)