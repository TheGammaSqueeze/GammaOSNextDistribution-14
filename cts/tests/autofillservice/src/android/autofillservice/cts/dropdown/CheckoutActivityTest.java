/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.autofillservice.cts.dropdown;

import static android.autofillservice.cts.activities.CheckoutActivity.ID_ADDRESS;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_CC_EXPIRATION;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_CC_NUMBER;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_DATE_PICKER;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_HOME_ADDRESS;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_SAVE_CC;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_TIME_PICKER;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_WORK_ADDRESS;
import static android.autofillservice.cts.activities.CheckoutActivity.INDEX_ADDRESS_WORK;
import static android.autofillservice.cts.activities.CheckoutActivity.INDEX_CC_EXPIRATION_NEVER;
import static android.autofillservice.cts.activities.CheckoutActivity.INDEX_CC_EXPIRATION_TODAY;
import static android.autofillservice.cts.activities.CheckoutActivity.INDEX_CC_EXPIRATION_TOMORROW;
import static android.autofillservice.cts.testcore.Helper.assertListValue;
import static android.autofillservice.cts.testcore.Helper.assertTextAndValue;
import static android.autofillservice.cts.testcore.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.testcore.Helper.assertToggleIsSanitized;
import static android.autofillservice.cts.testcore.Helper.assertToggleValue;
import static android.autofillservice.cts.testcore.Helper.findAutofillIdByResourceId;
import static android.autofillservice.cts.testcore.Helper.findNodeByResourceId;
import static android.autofillservice.cts.testcore.Helper.getContext;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.view.View.AUTOFILL_TYPE_LIST;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.R;
import android.autofillservice.cts.activities.CheckoutActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.SaveRequest;
import android.autofillservice.cts.testcore.MultipleTimesRadioGroupListener;
import android.autofillservice.cts.testcore.MultipleTimesTimeListener;
import android.autofillservice.cts.testcore.OneTimeCompoundButtonListener;
import android.autofillservice.cts.testcore.OneTimeDateListener;
import android.autofillservice.cts.testcore.OneTimeSpinnerListener;
import android.autofillservice.cts.testcore.OneTimeTextWatcher;
import android.icu.util.Calendar;
import android.platform.test.annotations.AppModeFull;
import android.service.autofill.CharSequenceTransformation;
import android.service.autofill.CustomDescription;
import android.service.autofill.FillContext;
import android.service.autofill.ImageTransformation;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.TimePicker;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Test case for an activity containing non-TextField views.
 */
public class CheckoutActivityTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<CheckoutActivity> {

    private CheckoutActivity mActivity;

    @Override
    protected AutofillActivityTestRule<CheckoutActivity> getActivityRule() {
        return new AutofillActivityTestRule<CheckoutActivity>(CheckoutActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Test
    public void testAutofill() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setPresentation(createPresentation("ACME CC"))
                .setField(ID_CC_NUMBER, "4815162342")
                .setField(ID_CC_EXPIRATION, INDEX_CC_EXPIRATION_NEVER)
                .setField(ID_ADDRESS, 1)
                .setField(ID_SAVE_CC, true)
                .build());
        mActivity.expectAutoFill("4815162342", INDEX_CC_EXPIRATION_NEVER, R.id.work_address,
                true);

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert properties of Spinner field.
        final ViewNode ccExpirationNode =
                assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertThat(ccExpirationNode.getClassName()).isEqualTo(Spinner.class.getName());
        assertThat(ccExpirationNode.getAutofillType()).isEqualTo(AUTOFILL_TYPE_LIST);
        final CharSequence[] options = ccExpirationNode.getAutofillOptions();
        assertWithMessage("ccExpirationNode.getAutoFillOptions()").that(options).isNotNull();
        assertWithMessage("Wrong auto-fill options for spinner").that(options).asList()
                .containsExactly((Object [])
                        getContext().getResources().getStringArray(R.array.cc_expiration_values))
                .inOrder();

        // Auto-fill it.
        mUiBot.selectDataset("ACME CC");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    @AppModeFull(reason = "testAutofill() is enough")
    public void testAutofillDynamicAdapter() throws Exception {
        // Set activity.
        mActivity.onCcExpiration((v) -> v.setAdapter(new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_spinner_item,
                Arrays.asList("YESTERDAY", "TODAY", "TOMORROW", "NEVER"))));

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setPresentation(createPresentation("ACME CC"))
                .setField(ID_CC_NUMBER, "4815162342")
                .setField(ID_CC_EXPIRATION, INDEX_CC_EXPIRATION_NEVER)
                .setField(ID_ADDRESS, 1)
                .setField(ID_SAVE_CC, true)
                .build());
        mActivity.expectAutoFill("4815162342", INDEX_CC_EXPIRATION_NEVER, R.id.work_address,
                true);

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert properties of Spinner field.
        final ViewNode ccExpirationNode =
                assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertThat(ccExpirationNode.getClassName()).isEqualTo(Spinner.class.getName());
        assertThat(ccExpirationNode.getAutofillType()).isEqualTo(AUTOFILL_TYPE_LIST);
        final CharSequence[] options = ccExpirationNode.getAutofillOptions();
        assertWithMessage("ccExpirationNode.getAutoFillOptions()").that(options).isNull();

        // Auto-fill it.
        mUiBot.selectDataset("ACME CC");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    // TODO: this should be a pure unit test exercising onProvideAutofillStructure(),
    // but that would require creating a custom ViewStructure.
    @Test
    @AppModeFull(reason = "Unit test")
    public void testGetAutofillOptionsSorted() throws Exception {
        // Set service.
        enableService();

        // Set activity.
        mActivity.onCcExpirationAdapter((adapter) -> adapter.sort((a, b) -> {
            return ((String) a).compareTo((String) b);
        }));

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setPresentation(createPresentation("ACME CC"))
                .setField(ID_CC_NUMBER, "4815162342")
                .setField(ID_CC_EXPIRATION, INDEX_CC_EXPIRATION_NEVER)
                .setField(ID_ADDRESS, 1)
                .setField(ID_SAVE_CC, true)
                .build());
        mActivity.expectAutoFill("4815162342", INDEX_CC_EXPIRATION_NEVER, R.id.work_address,
                true);

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert properties of Spinner field.
        final ViewNode ccExpirationNode =
                assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertThat(ccExpirationNode.getClassName()).isEqualTo(Spinner.class.getName());
        assertThat(ccExpirationNode.getAutofillType()).isEqualTo(AUTOFILL_TYPE_LIST);
        final CharSequence[] options = ccExpirationNode.getAutofillOptions();
        assertWithMessage("Wrong auto-fill options for spinner").that(options).asList()
                .containsExactly("never", "today", "tomorrow", "yesterday").inOrder();

        // Auto-fill it.
        mUiBot.selectDataset("ACME CC");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testSanitization() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_CREDIT_CARD,
                        ID_CC_NUMBER, ID_CC_EXPIRATION, ID_ADDRESS, ID_SAVE_CC)
                .build());

        // Dynamically change view contents
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TOMORROW, true));
        mActivity.onHomeAddress((v) -> v.setChecked(true));
        mActivity.onSaveCc((v) -> v.setChecked(true));

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());

        // Assert sanitization on fill request: everything should be sanitized!
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        assertTextIsSanitized(fillRequest.structure, ID_CC_NUMBER);
        assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertToggleIsSanitized(fillRequest.structure, ID_HOME_ADDRESS);
        assertToggleIsSanitized(fillRequest.structure, ID_SAVE_CC);

        // Trigger save.
        mActivity.onCcNumber((v) -> v.setText("4815162342"));
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TODAY));
        mActivity.onAddress((v) -> v.check(R.id.work_address));
        mActivity.onSaveCc((v) -> v.setChecked(false));
        mActivity.tapBuy();
        mUiBot.saveForAutofill(true, SAVE_DATA_TYPE_CREDIT_CARD);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert sanitization on save: everything should be available!
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_CC_NUMBER), "4815162342");
        assertListValue(findNodeByResourceId(saveRequest.structure, ID_CC_EXPIRATION),
                INDEX_CC_EXPIRATION_TODAY);
        assertListValue(findNodeByResourceId(saveRequest.structure, ID_ADDRESS),
                INDEX_ADDRESS_WORK);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_HOME_ADDRESS), false);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_WORK_ADDRESS), true);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_SAVE_CC), false);
    }

    @Test
    @AppModeFull(reason = "Service-specific test")
    public void testCustomizedSaveUi() throws Exception {
        customizedSaveUi(false);
    }

    @Test
    @AppModeFull(reason = "Service-specific test")
    public void testCustomizedSaveUiWithContentDescription() throws Exception {
        customizedSaveUi(true);
    }

    /**
     * Tests that a spinner can be used on custom save descriptions.
     */
    private void customizedSaveUi(boolean withContentDescription) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final String packageName = getContext().getPackageName();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_CREDIT_CARD, ID_CC_NUMBER, ID_CC_EXPIRATION)
                .setSaveInfoVisitor((contexts, builder) -> {
                    final RemoteViews presentation = new RemoteViews(packageName,
                            R.layout.two_horizontal_text_fields);
                    final FillContext context = contexts.get(0);
                    final AutofillId ccNumberId = findAutofillIdByResourceId(context,
                            ID_CC_NUMBER);
                    final AutofillId ccExpirationId = findAutofillIdByResourceId(context,
                            ID_CC_EXPIRATION);
                    final CharSequenceTransformation trans1 = new CharSequenceTransformation
                            .Builder(ccNumberId, Pattern.compile("(.*)"), "$1")
                            .build();
                    final CharSequenceTransformation trans2 = new CharSequenceTransformation
                            .Builder(ccExpirationId, Pattern.compile("(.*)"), "$1")
                            .build();
                    final ImageTransformation trans3 = (withContentDescription
                            ? new ImageTransformation.Builder(ccNumberId,
                                    Pattern.compile("(.*)"), R.drawable.android,
                                    "One image is worth thousand words")
                            : new ImageTransformation.Builder(ccNumberId,
                                    Pattern.compile("(.*)"), R.drawable.android))
                            .build();

                    final CustomDescription customDescription =
                            new CustomDescription.Builder(presentation)
                            .addChild(R.id.first, trans1)
                            .addChild(R.id.second, trans2)
                            .addChild(R.id.img, trans3)
                            .build();
                    builder.setCustomDescription(customDescription);
                })
                .build());

        // Dynamically change view contents
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TOMORROW, true));

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onCcNumber((v) -> v.setText("4815162342"));
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TODAY));
        mActivity.tapBuy();

        // First make sure the UI is shown...
        final UiObject2 saveUi = mUiBot.assertSaveShowing(SAVE_DATA_TYPE_CREDIT_CARD);

        // Then make sure it does have the custom views on it...
        final UiObject2 staticText = saveUi.findObject(By.res(packageName, Helper.ID_STATIC_TEXT));
        assertThat(staticText).isNotNull();
        assertThat(staticText.getText()).isEqualTo("YO:");

        final UiObject2 number = saveUi.findObject(By.res(packageName, "first"));
        assertThat(number).isNotNull();
        assertThat(number.getText()).isEqualTo("4815162342");

        final UiObject2 expiration = saveUi.findObject(By.res(packageName, "second"));
        assertThat(expiration).isNotNull();
        assertThat(expiration.getText()).isEqualTo("today");

        final UiObject2 image = saveUi.findObject(By.res(packageName, "img"));
        assertThat(image).isNotNull();
        final String contentDescription = image.getContentDescription();
        if (withContentDescription) {
            assertThat(contentDescription).isEqualTo("One image is worth thousand words");
        } else {
            assertThat(contentDescription).isNull();
        }
    }

    /**
     * Tests that a custom save description is ignored when the selected spinner element is not
     * available in the autofill options.
     */
    @Test
    public void testCustomizedSaveUiWhenListResolutionFails() throws Exception {
        // Set service.
        enableService();

        // Change spinner to return just one item so the transformation throws an exception when
        // fetching it.
        mActivity.getCcExpirationAdapter().setAutofillOptions("D'OH!");

        // Set expectations.
        final String packageName = getContext().getPackageName();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_CREDIT_CARD, ID_CC_NUMBER, ID_CC_EXPIRATION)
                .setSaveInfoVisitor((contexts, builder) -> {
                    final FillContext context = contexts.get(0);
                    final AutofillId ccNumberId = findAutofillIdByResourceId(context,
                            ID_CC_NUMBER);
                    final AutofillId ccExpirationId = findAutofillIdByResourceId(context,
                            ID_CC_EXPIRATION);
                    final RemoteViews presentation = new RemoteViews(packageName,
                            R.layout.two_horizontal_text_fields);
                    final CharSequenceTransformation trans1 = new CharSequenceTransformation
                            .Builder(ccNumberId, Pattern.compile("(.*)"), "$1")
                            .build();
                    final CharSequenceTransformation trans2 = new CharSequenceTransformation
                            .Builder(ccExpirationId, Pattern.compile("(.*)"), "$1")
                            .build();
                    final CustomDescription customDescription =
                            new CustomDescription.Builder(presentation)
                            .addChild(R.id.first, trans1)
                            .addChild(R.id.second, trans2)
                            .build();
                    builder.setCustomDescription(customDescription);
                })
                .build());

        // Dynamically change view contents
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TOMORROW, true));

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onCcNumber((v) -> v.setText("4815162342"));
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TODAY));
        mActivity.tapBuy();

        // First make sure the UI is shown...
        final UiObject2 saveUi = mUiBot.assertSaveShowing(SAVE_DATA_TYPE_CREDIT_CARD);

        // Then make sure it does not have the custom views on it...
        assertThat(saveUi.findObject(By.res(packageName, Helper.ID_STATIC_TEXT))).isNull();
    }

    // ============================================================================================
    // Tests to verify EditText by setting with AutofillValue.
    // ============================================================================================
    @Test
    public void autofillValidTextValue() throws Exception {
        autofillEditText(AutofillValue.forText("filled"), "filled", true);
    }

    @Test
    public void autofillEmptyTextValue() throws Exception {
        autofillEditText(AutofillValue.forText(""), "", true);
    }

    @Test
    public void autofillTextWithListValue() throws Exception {
        autofillEditText(AutofillValue.forList(0), "", false);
    }

    private void autofillEditText(AutofillValue value, String expectedText,
            boolean expectAutoFill) throws Exception {
        // Enable service.
        enableService();

        // Set expectations and trigger Autofill.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_CC_NUMBER, value)
                .setPresentation(createPresentation("dataset"))
                .build());
        mActivity.onCcNumber((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Autofill it and check the result.
        EditText editText = mActivity.getCcNumber();
        OneTimeTextWatcher textWatcher = new OneTimeTextWatcher(ID_CC_NUMBER, editText,
                expectedText);
        editText.addTextChangedListener(textWatcher);
        mUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            textWatcher.assertAutoFilled();
        } else {
            assertThat(editText.getText().toString()).isEqualTo(expectedText);
        }
    }

    @Test
    public void getEditTextAutoFillValue() throws Exception {
        EditText editText = mActivity.getCcNumber();

        mActivity.syncRunOnUiThread(() -> editText.setText("test"));

        assertThat(editText.getAutofillValue()).isEqualTo(AutofillValue.forText("test"));
    }

    @Test
    public void getEditTextAutoFillValue_worksOnDisabledField() throws Exception {
        EditText editText = mActivity.getCcNumber();
        mActivity.syncRunOnUiThread(() -> editText.setEnabled(false));

        mActivity.syncRunOnUiThread(() -> editText.setText("test"));

        assertThat(editText.getAutofillValue()).isEqualTo(AutofillValue.forText("test"));
    }

    // ============================================================================================
    // Tests to verify CheckBox by setting with AutofillValue.
    // ============================================================================================
    @Test
    @Ignore("b/232198065 Fix touch mode problem in ActivityTestRule")
    public void autofillToggleValueWithTrue() throws Exception {
        autofillCompoundButton(AutofillValue.forToggle(true), true, true);
    }

    @Test
    public void autofillToggleValueWithFalse() throws Exception {
        autofillCompoundButton(AutofillValue.forToggle(false), false, false);
    }

    @Test
    public void autofillCompoundButtonWithTextValue() throws Exception {
        autofillCompoundButton(AutofillValue.forText(""), false, false);
    }

    private void autofillCompoundButton(AutofillValue value, boolean expectedValue,
            boolean expectAutoFill) throws Exception {
        // Enable service.
        enableService();

        // Set expectations and trigger Autofill.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_SAVE_CC, value)
                .setPresentation(createPresentation("dataset"))
                .build());
        mActivity.onSaveCc((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Autofill it and check the result.
        CheckBox compoundButton = mActivity.getSaveCc();
        OneTimeCompoundButtonListener checkedWatcher = new OneTimeCompoundButtonListener(
                ID_SAVE_CC, compoundButton, expectedValue);
        compoundButton.setOnCheckedChangeListener(checkedWatcher);
        mUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            checkedWatcher.assertAutoFilled();
        } else {
            assertThat(compoundButton.isChecked()).isEqualTo(expectedValue);
        }
    }

    @Test
    public void getCompoundButtonAutoFillValue() throws Exception {
        CheckBox compoundButton = mActivity.getSaveCc();
        mActivity.syncRunOnUiThread(() -> compoundButton.setChecked(true));

        assertThat(compoundButton.getAutofillValue()).isEqualTo(AutofillValue.forToggle(true));

        mActivity.syncRunOnUiThread(() -> compoundButton.setEnabled(false));

        assertThat(compoundButton.getAutofillValue()).isNull();
    }

    // ============================================================================================
    // Tests to verify Spinner by setting with AutofillValue
    // ============================================================================================
    private void autofillListValue(AutofillValue value, int expectedValue,
            boolean expectAutoFill) throws Exception {
        // Enable service.
        enableService();

        // Set expectations and trigger Autofill.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_CC_EXPIRATION, value)
                .setPresentation(createPresentation("dataset"))
                .build());
        mActivity.onCcExpiration((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Autofill it and check the result.
        Spinner spinner = mActivity.getCcExpiration();
        OneTimeSpinnerListener spinnerWatcher = new OneTimeSpinnerListener(
                ID_CC_EXPIRATION, spinner, expectedValue);
        spinner.setOnItemSelectedListener(spinnerWatcher);
        mUiBot.selectDatasetSync("dataset");

        if (expectAutoFill) {
            spinnerWatcher.assertAutoFilled();
        } else {
            assertThat(spinner.getSelectedItemPosition()).isEqualTo(expectedValue);
        }
    }

    @Test
    public void autofillZeroListValueToSpinner() throws Exception {
        autofillListValue(AutofillValue.forList(0), 0, false);
    }

    @Test
    @Ignore("b/232198065 Fix touch mode problem in ActivityTestRule")
    public void autofillOneListValueToSpinner() throws Exception {
        autofillListValue(AutofillValue.forList(1), 1, true);
    }

    @Test
    public void autofillInvalidListValueToSpinner() throws Exception {
        autofillListValue(AutofillValue.forList(-1), 0, false);
    }

    @Test
    public void autofillSpinnerWithTextValue() throws Exception {
        autofillListValue(AutofillValue.forText(""), 0, false);
    }

    @Test
    public void getSpinnerAutoFillValue() throws Exception {
        Spinner spinner = mActivity.getCcExpiration();
        mActivity.syncRunOnUiThread(() -> spinner.setSelection(1));

        assertThat(spinner.getAutofillValue()).isEqualTo(AutofillValue.forList(1));

        mActivity.syncRunOnUiThread(() -> spinner.setEnabled(false));

        assertThat(spinner.getAutofillValue()).isNull();
    }

    // ============================================================================================
    // Tests to verify DatePicker by setting with AutofillValue
    // ============================================================================================
    @Test
    public void autofillValidDateValueToDatePicker() throws Exception {
        autofillDateValueToDatePicker(AutofillValue.forDate(getDateAsMillis(2017, 3, 7, 12, 32)),
                true);
    }

    @Test
    public void autofillDatePickerWithTextValue() throws Exception {
        autofillDateValueToDatePicker(AutofillValue.forText(""), false);
    }

    private void autofillDateValueToDatePicker(AutofillValue value,
            boolean expectAutoFill) throws Exception {
        // Enable service.
        enableService();

        // Set expectations and trigger Autofill.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_DATE_PICKER, value)
                .setField(ID_CC_NUMBER, "filled")
                .setPresentation(createPresentation("dataset"))
                .build());
        DatePicker datePicker = mActivity.getDatePicker();
        int nonAutofilledYear = datePicker.getYear();
        int nonAutofilledMonth = datePicker.getMonth();
        int nonAutofilledDay = datePicker.getDayOfMonth();
        mActivity.onCcNumber((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Autofill it and check the result.
        OneTimeDateListener dateWatcher = new OneTimeDateListener(ID_DATE_PICKER, datePicker,
                2017, 3, 7);
        datePicker.setOnDateChangedListener(dateWatcher);
        mUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            dateWatcher.assertAutoFilled();
        } else {
            Helper.assertDateValue(datePicker, nonAutofilledYear, nonAutofilledMonth,
                    nonAutofilledDay);
        }
    }

    private long getDateAsMillis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(
                mActivity.getResources().getConfiguration().getLocales().get(0));

        calendar.set(year, month, day, hour, minute);

        return calendar.getTimeInMillis();
    }

    @Test
    public void getDatePickerAutoFillValue() throws Exception {
        DatePicker datePicker = mActivity.getDatePicker();
        mActivity.syncRunOnUiThread(() -> datePicker.updateDate(2017, 3, 7));

        Helper.assertDateValue(datePicker, 2017, 3, 7);

        mActivity.syncRunOnUiThread(() -> datePicker.setEnabled(false));

        assertThat(datePicker.getAutofillValue()).isNull();
    }

    // ============================================================================================
    // Tests to verify TimePicker by setting with AutofillValue
    // ============================================================================================
    @Test
    public void autofillValidDateValueToTimePicker() throws Exception {
        autofillDateValueToTimePicker(AutofillValue.forDate(getDateAsMillis(2017, 3, 7, 12, 32)),
                true);
    }

    @Test
    public void autofillTimePickerWithTextValue() throws Exception {
        autofillDateValueToTimePicker(AutofillValue.forText(""), false);
    }

    private void autofillDateValueToTimePicker(AutofillValue value,
            boolean expectAutoFill) throws Exception {
        // Set service.
        enableService();

        // Set expectations and trigger Autofill.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_TIME_PICKER, value)
                .setField(ID_CC_NUMBER, "filled")
                .setPresentation(createPresentation("dataset"))
                .build());
        TimePicker timePicker = mActivity.getTimePicker();
        mActivity.syncRunOnUiThread(() -> {
            timePicker.setIs24HourView(true);
        });
        int nonAutofilledHour = timePicker.getHour();
        int nonAutofilledMinute = timePicker.getMinute();
        mActivity.onCcNumber((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Autofill it and check the result.
        MultipleTimesTimeListener timeWatcher = new MultipleTimesTimeListener(ID_TIME_PICKER, 1,
                timePicker, 12, 32);
        timePicker.setOnTimeChangedListener(timeWatcher);
        mUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            timeWatcher.assertAutoFilled();
        } else {
            Helper.assertTimeValue(timePicker, nonAutofilledHour, nonAutofilledMinute);
        }
    }

    @Test
    public void getTimePickerAutoFillValue() throws Exception {
        TimePicker timePicker = mActivity.getTimePicker();
        mActivity.syncRunOnUiThread(() -> {
            timePicker.setHour(12);
            timePicker.setMinute(32);
        });

        Helper.assertTimeValue(timePicker, 12, 32);

        mActivity.syncRunOnUiThread(() -> timePicker.setEnabled(false));

        assertThat(timePicker.getAutofillValue()).isNull();
    }

    // ============================================================================================
    // Tests to verify RadioGroup by setting with AutofillValue
    // ============================================================================================
    @Test
    public void autofillZeroListValueToRadioGroup() throws Exception {
        autofillRadioGroup(AutofillValue.forList(0), 0, false);
    }

    @Test
    public void autofillOneListValueToRadioGroup() throws Exception {
        autofillRadioGroup(AutofillValue.forList(1), 1, true);
    }

    @Test
    public void autofillInvalidListValueToRadioGroup() throws Exception {
        autofillRadioGroup(AutofillValue.forList(-1), 0, false);
    }

    @Test
    public void autofillRadioGroupWithTextValue() throws Exception {
        autofillRadioGroup(AutofillValue.forText(""), 0, false);
    }

    private void autofillRadioGroup(AutofillValue value, int expectedValue,
            boolean expectAutoFill) throws Exception {
        // Set service.
        enableService();

        // Set expectations and trigger Autofill.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_ADDRESS, value)
                .setField(ID_CC_NUMBER, "filled")
                .setPresentation(createPresentation("dataset"))
                .build());
        mActivity.onHomeAddress((v) -> v.setChecked(true));
        mActivity.onCcNumber((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        RadioGroup radioGroup = mActivity.getAddress();
        MultipleTimesRadioGroupListener radioGroupWatcher = new MultipleTimesRadioGroupListener(
                ID_ADDRESS, 2, radioGroup, expectedValue);
        radioGroup.setOnCheckedChangeListener(radioGroupWatcher);

        // Autofill it and check the result.
        mUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            radioGroupWatcher.assertAutoFilled();
        } else {
            if (expectedValue == 0) {
                mActivity.assertRadioButtonValue(/* homeAddrValue= */
                        true, /* workAddrValue= */ false);
            } else {
                mActivity.assertRadioButtonValue(/* homeAddrValue= */
                        false, /* workAddrValue= */true);
            }
        }
    }

    @Test
    public void getRadioGroupAutoFillValue() throws Exception {
        RadioGroup radioGroup = mActivity.getAddress();
        mActivity.onWorkAddress((v) -> v.setChecked(true));

        assertThat(radioGroup.getAutofillValue()).isEqualTo(AutofillValue.forList(1));

        mActivity.syncRunOnUiThread(() -> radioGroup.setEnabled(false));

        assertThat(radioGroup.getAutofillValue()).isNull();
    }
}
