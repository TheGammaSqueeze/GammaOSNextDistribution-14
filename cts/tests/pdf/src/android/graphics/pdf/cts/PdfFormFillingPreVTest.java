/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.graphics.pdf.cts;

import static android.graphics.pdf.cts.Utils.createPreVRenderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.models.ChoiceOption;
import android.graphics.pdf.models.FormEditRecord;
import android.graphics.pdf.models.FormWidgetInfo;

import androidx.annotation.RawRes;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class PdfFormFillingPreVTest {
    private static final int CLICK_FORM = R.raw.click_form;
    private static final int COMBOBOX_FORM = R.raw.combobox_form;
    private static final int LISTBOX_FORM = R.raw.listbox_form;
    private static final int TEXT_FORM = R.raw.text_form;
    private static final int NOT_FORM = R.raw.two_pages;
    private static final int XFA_FORM = R.raw.xfa_form;
    private static final int XFAF_FORM = R.raw.xfaf_form;

    private Context mContext;

    /**
     * Returns {@code true} if every {@code innerRect} is contained by at least one {@code
     * outerRect}
     */
    private static boolean fullyContain(List<Rect> innerRects, List<Rect> outerRects) {
        outerLoop:
        for (Rect inner : innerRects) {
            for (Rect outer : outerRects) {
                if (outer.contains(inner)) {
                    continue outerLoop;
                }
            }
            return false;
        }
        return true;
    }

    private static FormWidgetInfo makeCheckbox(
            int widgetIndex,
            Rect widgetRect,
            boolean readOnly,
            String textValue,
            String accessibilityLabel) {
        return new FormWidgetInfo.Builder(
                        FormWidgetInfo.WIDGET_TYPE_CHECKBOX,
                        widgetIndex,
                        widgetRect,
                        textValue,
                        accessibilityLabel)
                .setReadOnly(readOnly)
                .build();
    }

    private static FormWidgetInfo makeRadioButton(
            int widgetIndex,
            Rect widgetRect,
            boolean readOnly,
            String textValue,
            String accessibilityLabel) {
        return new FormWidgetInfo.Builder(
                        FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON,
                        widgetIndex,
                        widgetRect,
                        textValue,
                        accessibilityLabel)
                .setReadOnly(readOnly)
                .build();
    }

    private static FormWidgetInfo makeCombobox(
            int widgetIndex,
            Rect widgetRect,
            boolean readOnly,
            String textValue,
            String accessibilityLabel,
            boolean editableText,
            float fontSize,
            List<ChoiceOption> choiceOptions) {
        FormWidgetInfo.Builder builder =
                new FormWidgetInfo.Builder(
                                FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                                widgetIndex,
                                widgetRect,
                                textValue,
                                accessibilityLabel)
                        .setReadOnly(readOnly)
                        .setEditableText(editableText)
                        .setChoiceOptions(choiceOptions);
        if (fontSize > 0) {
            builder.setFontSize(fontSize);
        }
        return builder.build();
    }

    private static FormWidgetInfo makeListbox(
            int widgetIndex,
            Rect widgetRect,
            boolean readOnly,
            String textValue,
            String accessibilityLabel,
            boolean multiSelect,
            List<ChoiceOption> choiceOptions) {
        return new FormWidgetInfo.Builder(
                        FormWidgetInfo.WIDGET_TYPE_LISTBOX,
                        widgetIndex,
                        widgetRect,
                        textValue,
                        accessibilityLabel)
                .setReadOnly(readOnly)
                .setMultiSelect(multiSelect)
                .setChoiceOptions(choiceOptions)
                .build();
    }

    private static FormWidgetInfo makeTextField(
            int widgetIndex,
            Rect widgetRect,
            boolean readOnly,
            String textValue,
            String accessibilityLabel,
            boolean editableText,
            boolean multiLineText,
            int maxLength,
            float fontSize) {
        FormWidgetInfo.Builder builder =
                new FormWidgetInfo.Builder(
                                FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                                widgetIndex,
                                widgetRect,
                                textValue,
                                accessibilityLabel)
                        .setReadOnly(readOnly)
                        .setEditableText(editableText)
                        .setMultiLineText(multiLineText);
        if (fontSize > 0) {
            builder.setFontSize(fontSize);
        }
        if (maxLength > 0) {
            builder.setMaxLength(maxLength);
        }
        return builder.build();
    }

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void getFormType_none() throws Exception {
        verifyFormType(NOT_FORM, PdfRendererPreV.PDF_FORM_TYPE_NONE);
    }

    @Test
    public void getFormType_acro() throws Exception {
        verifyFormType(TEXT_FORM, PdfRendererPreV.PDF_FORM_TYPE_ACRO_FORM);
    }

    @Test
    public void getFormType_xfa() throws Exception {
        verifyFormType(XFA_FORM, PdfRendererPreV.PDF_FORM_TYPE_XFA_FULL);
    }

    @Test
    public void getFormType_xfaf() throws Exception {
        verifyFormType(XFAF_FORM, PdfRendererPreV.PDF_FORM_TYPE_XFA_FOREGROUND);
    }

    // getFormWidgetInfo
    @Test
    public void getFormWidgetInfo_checkbox() throws Exception {
        FormWidgetInfo expected =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ new Rect(135, 70, 155, 90),
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "checkbox");

        verifyFormWidgetInfo(CLICK_FORM, 0, new Point(145, 80), expected);
    }

    @Test
    public void getFormWidgetInfo_radioButton() throws Exception {
        FormWidgetInfo expected =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ new Rect(85, 230, 105, 250),
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");

        verifyFormWidgetInfo(CLICK_FORM, 0, new Point(95, 240), expected);
    }

    @Test
    public void getFormWidgetInfo_readOnlyCheckbox() throws Exception {
        FormWidgetInfo expected =
                makeCheckbox(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ new Rect(135, 30, 155, 50),
                        /* readOnly= */ true,
                        /* textValue= */ "true",
                        /* accessibilityLabel= */ "readOnlyCheckbox");

        verifyFormWidgetInfo(CLICK_FORM, 0, new Point(145, 40), expected);
    }

    @Test
    public void getFormWidgetInfo_readOnlyRadioButton() throws Exception {
        FormWidgetInfo expected =
                makeRadioButton(
                        /* widgetIndex= */ 2,
                        /* widgetRect= */ new Rect(85, 180, 105, 200),
                        /* readOnly= */ true,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");

        verifyFormWidgetInfo(CLICK_FORM, 0, new Point(95, 190), expected);
    }

    @Test
    public void getFormWidgetInfo_editableCombobox() throws Exception {
        List<ChoiceOption> expectedChoices =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Foo", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Bar", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Qux", /* selected= */ false));
        FormWidgetInfo expected =
                makeCombobox(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ new Rect(100, 220, 200, 250),
                        /* readOnly= */ false,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Combo_Editable",
                        /* editableText= */ true,
                        /* fontSize= */ 12.0f,
                        /* choiceOptions= */ expectedChoices);

        verifyFormWidgetInfo(COMBOBOX_FORM, 0, new Point(150, 235), expected);
    }

    @Test
    public void getFormWidgetInfo_unEditableCombobox() throws Exception {
        List<ChoiceOption> expectedChoices =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Apple", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Banana", /* selected= */ true),
                        new ChoiceOption(/* label= */ "Cherry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Date", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Elderberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Fig", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Guava", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Honeydew", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Indian Fig", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Jackfruit", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Kiwi", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Lemon", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Mango", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nectarine", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Orange", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Persimmon", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quince", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Raspberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Strawberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Tamarind", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ugli Fruit", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Voavanga", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Wolfberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Xigua", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Yangmei", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Zucchini", /* selected= */ false));
        FormWidgetInfo expected =
                makeCombobox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ new Rect(100, 170, 200, 200),
                        /* readOnly= */ false,
                        /* textValue= */ "Banana",
                        /* accessibilityLabel= */ "Combo1",
                        /* editableText= */ false,
                        /* fontSize= */ 0f,
                        /* choiceOptions= */ expectedChoices);

        verifyFormWidgetInfo(COMBOBOX_FORM, 0, new Point(150, 185), expected);
    }

    @Test
    public void getFormWidgetInfo_readOnlyCombobox() throws Exception {
        // Notably, choice options are not populated for read-only Comboboxes
        FormWidgetInfo expected =
                makeCombobox(
                        /* widgetIndex= */ 2,
                        /* widgetRect= */ new Rect(100, 70, 200, 100),
                        /* readOnly= */ true,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Combo_ReadOnly",
                        /* editableText= */ false,
                        /* fontSize= */ 0f,
                        /* choiceOptions= */ List.of());

        verifyFormWidgetInfo(COMBOBOX_FORM, 0, new Point(150, 85), expected);
    }

    @Test
    public void getFormWidgetInfo_listbox() throws Exception {
        List<ChoiceOption> expectedChoices =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Alberta", /* selected= */ false),
                        new ChoiceOption(/* label= */ "British Columbia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Manitoba", /* selected= */ false),
                        new ChoiceOption(/* label= */ "New Brunswick", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Newfoundland and Labrador", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nova Scotia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ontario", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Prince Edward Island", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quebec", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Saskatchewan", /* selected= */ true));
        FormWidgetInfo expected =
                makeListbox(
                        /* widgetIndex= */ 6,
                        /* widgetRect= */ new Rect(100, 470, 200, 500),
                        /* readOnly= */ false,
                        /* textValue= */ "Saskatchewan",
                        /* accessibilityLabel= */ "Listbox_SingleSelectLastSelected",
                        /* multiSelect= */ false,
                        /* choiceOptions= */ expectedChoices);

        verifyFormWidgetInfo(LISTBOX_FORM, 0, new Point(150, 485), expected);
    }

    @Test
    public void getFormWidgetInfo_multiselectListbox() throws Exception {
        List<ChoiceOption> expectedChoices =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Alberta", /* selected= */ false),
                        new ChoiceOption(/* label= */ "British Columbia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Manitoba", /* selected= */ false),
                        new ChoiceOption(/* label= */ "New Brunswick", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Newfoundland and Labrador", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nova Scotia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ontario", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Prince Edward Island", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quebec", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Saskatchewan", /* selected= */ true));
        FormWidgetInfo expected =
                makeListbox(
                        /* widgetIndex= */ 6,
                        /* widgetRect= */ new Rect(100, 470, 200, 500),
                        /* readOnly= */ false,
                        /* textValue= */ "Saskatchewan",
                        /* accessibilityLabel= */ "Listbox_SingleSelectLastSelected",
                        /* multiSelect= */ false,
                        /* choiceOptions= */ expectedChoices);

        verifyFormWidgetInfo(LISTBOX_FORM, 0, new Point(150, 485), expected);
    }

    @Test
    public void getFormWidgetInfo_readOnlyListbox() throws Exception {
        // Notably, choice options are not populated for read-only Listboxes
        FormWidgetInfo expected =
                makeListbox(
                        /* widgetIndex= */ 2,
                        /* widgetRect= */ new Rect(100, 70, 200, 100),
                        /* readOnly= */ true,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Listbox_ReadOnly",
                        /* multiSelect= */ false,
                        /* choiceOptions= */ List.of());

        verifyFormWidgetInfo(LISTBOX_FORM, 0, new Point(150, 85), expected);
    }

    @Test
    public void getFormWidgetInfo_textField() throws Exception {
        FormWidgetInfo expected =
                makeTextField(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ new Rect(100, 170, 200, 200),
                        /* readOnly= */ false,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Text Box",
                        /* editableText= */ true,
                        /* multiLineText= */ false,
                        /* maxLength= */ -1,
                        /* fontSize= */ 12.0f);

        verifyFormWidgetInfo(TEXT_FORM, 0, new Point(150, 185), expected);
    }

    @Test
    public void getFormWidgetInfo_charLimitTextField() throws Exception {
        FormWidgetInfo expected =
                makeTextField(
                        /* widgetIndex= */ 2,
                        /* widgetRect= */ new Rect(100, 225, 200, 250),
                        /* readOnly= */ false,
                        /* textValue= */ "Elephant",
                        /* accessibilityLabel= */ "CharLimit",
                        /* editableText= */ true,
                        /* multiLineText= */ false,
                        /* maxLength= */ 10,
                        /* fontSize= */ 12.0f);

        verifyFormWidgetInfo(TEXT_FORM, 0, new Point(150, 235), expected);
    }

    @Test
    public void getFormWidgetInfo_readOnlyTextField() throws Exception {
        FormWidgetInfo expected =
                makeTextField(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ new Rect(100, 70, 200, 100),
                        /* readOnly= */ true,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "ReadOnly",
                        /* editableText= */ false,
                        /* multiLineText= */ false,
                        /* maxLength= */ -1,
                        /* fontSize= */ 0f);

        verifyFormWidgetInfo(TEXT_FORM, 0, new Point(150, 85), expected);
    }

    @Test
    public void getFormWidgetInfo_filtering() throws Exception {
        // Notably, choice options are not populated for read-only Comboboxes
        FormWidgetInfo readOnly =
                makeCombobox(
                        /* widgetIndex= */ 2,
                        /* widgetRect= */ new Rect(100, 70, 200, 100),
                        /* readOnly= */ true,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Combo_ReadOnly",
                        /* editableText= */ false,
                        /* fontSize= */ 0f,
                        /* choiceOptions= */ List.of());
        List<ChoiceOption> combo1Choices =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Apple", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Banana", /* selected= */ true),
                        new ChoiceOption(/* label= */ "Cherry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Date", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Elderberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Fig", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Guava", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Honeydew", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Indian Fig", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Jackfruit", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Kiwi", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Lemon", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Mango", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nectarine", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Orange", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Persimmon", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quince", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Raspberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Strawberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Tamarind", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ugli Fruit", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Voavanga", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Wolfberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Xigua", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Yangmei", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Zucchini", /* selected= */ false));
        FormWidgetInfo combo1 =
                makeCombobox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ new Rect(100, 170, 200, 200),
                        /* readOnly= */ false,
                        /* textValue= */ "Banana",
                        /* accessibilityLabel= */ "Combo1",
                        /* editableText= */ false,
                        /* fontSize= */ 0f,
                        /* choiceOptions= */ combo1Choices);
        List<ChoiceOption> editableChoices =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Foo", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Bar", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Qux", /* selected= */ false));
        FormWidgetInfo editable =
                makeCombobox(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ new Rect(100, 220, 200, 250),
                        /* readOnly= */ false,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Combo_Editable",
                        /* editableText= */ true,
                        /* fontSize= */ 12.0f,
                        /* choiceOptions= */ editableChoices);

        verifyFormWidgetInfos(
                COMBOBOX_FORM,
                0,
                Set.of(FormWidgetInfo.WIDGET_TYPE_COMBOBOX),
                Arrays.asList(editable, combo1, readOnly));
    }

    @Test
    public void getFormWidgetInfo_invalidIndex() throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(CLICK_FORM, mContext, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(0)) {
                int emptyIndex = 999;
                assertThrows(
                        IllegalArgumentException.class,
                        () -> page.getFormWidgetInfoAtIndex(emptyIndex));
            }
        }
    }

    @Test
    public void getFormWidgetInfo_emptyPoint() throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(CLICK_FORM, mContext, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(0)) {
                Point emptyPos = new Point(0, 0);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> page.getFormWidgetInfoAtPosition(emptyPos.x, emptyPos.y));
            }
        }
    }

    // applyEdit - click type widgets
    @Test
    public void applyEdit_clickOnCheckbox() throws Exception {
        Rect widgetArea = new Rect(135, 70, 155, 90);
        FormWidgetInfo before =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "checkbox");
        Point clickPoint = new Point(145, 80);
        FormEditRecord click =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(clickPoint)
                        .build();
        FormWidgetInfo after =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "true",
                        /* accessibilityLabel= */ "checkbox");

        verifyApplyEdit(CLICK_FORM, 0, clickPoint, before, click, after, List.of(widgetArea));
    }

    @Test
    public void applyEdit_clickOnRadioButton() throws Exception {
        Rect widgetArea = new Rect(85, 230, 105, 250);
        FormWidgetInfo before =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");
        Point clickPoint = new Point(95, 240);
        FormEditRecord click =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 5)
                        .setClickPoint(clickPoint)
                        .build();
        FormWidgetInfo after =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "true",
                        /* accessibilityLabel= */ "");

        verifyApplyEdit(CLICK_FORM, 0, clickPoint, before, click, after, List.of(widgetArea));
    }

    // applyEdit - combobox
    @Test
    public void applyEdit_setChoiceSelectionOnCombobox() throws Exception {
        Rect comboboxArea = new Rect(100, 220, 200, 250);
        List<ChoiceOption> choicesBefore =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Foo", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Bar", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Qux", /* selected= */ false));
        FormWidgetInfo widgetBefore =
                makeCombobox(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ comboboxArea,
                        /* readOnly= */ false,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Combo_Editable",
                        /* editableText= */ true,
                        /* fontSize= */ 12.0f,
                        /* choiceOptions= */ choicesBefore);
        FormEditRecord selectBar =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 0)
                        .setSelectedIndices(Set.of(1))
                        .build();
        List<ChoiceOption> choicesAfter =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Foo", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Bar", /* selected= */ true),
                        new ChoiceOption(/* label= */ "Qux", /* selected= */ false));
        FormWidgetInfo widgetAfter =
                makeCombobox(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ comboboxArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Bar",
                        /* accessibilityLabel= */ "Combo_Editable",
                        /* editableText= */ true,
                        /* fontSize= */ 12.0f,
                        /* choiceOptions= */ choicesAfter);

        verifyApplyEdit(
                COMBOBOX_FORM,
                0,
                new Point(150, 235),
                widgetBefore,
                selectBar,
                widgetAfter,
                List.of(comboboxArea));
    }

    @Test
    public void applyEdit_setTextOnCombobox() throws Exception {
        Rect comboboxArea = new Rect(100, 220, 200, 250);
        List<ChoiceOption> choicesBefore =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Foo", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Bar", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Qux", /* selected= */ false));
        FormWidgetInfo widgetBefore =
                makeCombobox(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ comboboxArea,
                        /* readOnly= */ false,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Combo_Editable",
                        /* editableText= */ true,
                        /* fontSize= */ 12.0f,
                        /* choiceOptions= */ choicesBefore);
        FormEditRecord setText =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_TEXT,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 0)
                        .setText("Gecko tail")
                        .build();
        FormWidgetInfo widgetAfter =
                makeCombobox(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ comboboxArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Gecko tail",
                        /* accessibilityLabel= */ "Combo_Editable",
                        /* editableText= */ true,
                        /* fontSize= */ 12.0f,
                        /* choiceOptions= */ choicesBefore);

        verifyApplyEdit(
                COMBOBOX_FORM,
                0,
                new Point(150, 235),
                widgetBefore,
                setText,
                widgetAfter,
                List.of(comboboxArea));
    }

    // applyEdit - listbox
    @Test
    public void applyEdit_setChoiceSelectionOnListbox() throws Exception {
        Rect widgetArea = new Rect(100, 470, 200, 500);
        List<ChoiceOption> choicesBefore =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Alberta", /* selected= */ false),
                        new ChoiceOption(/* label= */ "British Columbia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Manitoba", /* selected= */ false),
                        new ChoiceOption(/* label= */ "New Brunswick", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Newfoundland and Labrador", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nova Scotia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ontario", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Prince Edward Island", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quebec", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Saskatchewan", /* selected= */ true));
        FormWidgetInfo widgetBefore =
                makeListbox(
                        /* widgetIndex= */ 6,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Saskatchewan",
                        /* accessibilityLabel= */ "Listbox_SingleSelectLastSelected",
                        /* multiSelect= */ false,
                        /* choiceOptions= */ choicesBefore);
        FormEditRecord clearSelection =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 6)
                        .setSelectedIndices(Set.of(0))
                        .build();
        List<ChoiceOption> choicesAfter =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Alberta", /* selected= */ true),
                        new ChoiceOption(/* label= */ "British Columbia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Manitoba", /* selected= */ false),
                        new ChoiceOption(/* label= */ "New Brunswick", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Newfoundland and Labrador", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nova Scotia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ontario", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Prince Edward Island", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quebec", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Saskatchewan", /* selected= */ false));
        FormWidgetInfo widgetAfter =
                makeListbox(
                        /* widgetIndex= */ 6,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Alberta",
                        /* accessibilityLabel= */ "Listbox_SingleSelectLastSelected",
                        /* multiSelect= */ false,
                        /* choiceOptions= */ choicesAfter);

        verifyApplyEdit(
                LISTBOX_FORM,
                0,
                new Point(150, 485),
                widgetBefore,
                clearSelection,
                widgetAfter,
                List.of(widgetArea));
    }

    @Test
    public void applyEdit_setMultipleChoiceSelectionOnListbox() throws Exception {
        Rect widgetArea = new Rect(100, 170, 200, 200);
        List<ChoiceOption> choicesBefore =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Apple", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Banana", /* selected= */ true),
                        new ChoiceOption(/* label= */ "Cherry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Date", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Elderberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Fig", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Guava", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Honeydew", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Indian Fig", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Jackfruit", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Kiwi", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Lemon", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Mango", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nectarine", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Orange", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Persimmon", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quince", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Raspberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Strawberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Tamarind", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ugli Fruit", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Voavanga", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Wolfberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Xigua", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Yangmei", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Zucchini", /* selected= */ false));
        FormWidgetInfo widgetBefore =
                makeListbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Banana",
                        /* accessibilityLabel= */ "Listbox_MultiSelect",
                        /* multiSelect= */ true,
                        /* choiceOptions= */ choicesBefore);
        FormEditRecord selectMultiple =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setSelectedIndices(Set.of(1, 2, 3))
                        .build();
        List<ChoiceOption> choicesAfter =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Apple", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Banana", /* selected= */ true),
                        new ChoiceOption(/* label= */ "Cherry", /* selected= */ true),
                        new ChoiceOption(/* label= */ "Date", /* selected= */ true),
                        new ChoiceOption(/* label= */ "Elderberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Fig", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Guava", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Honeydew", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Indian Fig", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Jackfruit", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Kiwi", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Lemon", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Mango", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nectarine", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Orange", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Persimmon", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quince", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Raspberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Strawberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Tamarind", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ugli Fruit", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Voavanga", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Wolfberry", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Xigua", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Yangmei", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Zucchini", /* selected= */ false));
        FormWidgetInfo widgetAfter =
                makeListbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Banana",
                        /* accessibilityLabel= */ "Listbox_MultiSelect",
                        /* multiSelect= */ true,
                        /* choiceOptions= */ choicesAfter);

        verifyApplyEdit(
                LISTBOX_FORM,
                0,
                new Point(150, 185),
                widgetBefore,
                selectMultiple,
                widgetAfter,
                List.of(widgetArea));
    }

    @Test
    public void applyEdit_clearSelectionOnListbox() throws Exception {
        Rect widgetArea = new Rect(100, 470, 200, 500);
        List<ChoiceOption> choicesBefore =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Alberta", /* selected= */ false),
                        new ChoiceOption(/* label= */ "British Columbia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Manitoba", /* selected= */ false),
                        new ChoiceOption(/* label= */ "New Brunswick", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Newfoundland and Labrador", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nova Scotia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ontario", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Prince Edward Island", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quebec", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Saskatchewan", /* selected= */ true));
        FormWidgetInfo widgetBefore =
                makeListbox(
                        /* widgetIndex= */ 6,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Saskatchewan",
                        /* accessibilityLabel= */ "Listbox_SingleSelectLastSelected",
                        /* multiSelect= */ false,
                        /* choiceOptions= */ choicesBefore);
        FormEditRecord clearSelection =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 6)
                        .setSelectedIndices(Set.of())
                        .build();
        List<ChoiceOption> choicesAfter =
                Arrays.asList(
                        new ChoiceOption(/* label= */ "Alberta", /* selected= */ false),
                        new ChoiceOption(/* label= */ "British Columbia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Manitoba", /* selected= */ false),
                        new ChoiceOption(/* label= */ "New Brunswick", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Newfoundland and Labrador", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Nova Scotia", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Ontario", /* selected= */ false),
                        new ChoiceOption(
                                /* label= */ "Prince Edward Island", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Quebec", /* selected= */ false),
                        new ChoiceOption(/* label= */ "Saskatchewan", /* selected= */ false));
        FormWidgetInfo widgetAfter =
                makeListbox(
                        /* widgetIndex= */ 6,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Listbox_SingleSelectLastSelected",
                        /* multiSelect= */ false,
                        /* choiceOptions= */ choicesAfter);

        verifyApplyEdit(
                LISTBOX_FORM,
                0,
                new Point(150, 485),
                widgetBefore,
                clearSelection,
                widgetAfter,
                List.of(widgetArea));
    }

    // applyEdit - text field
    @Test
    public void applyEdit_setTextOnTextField() throws Exception {
        Rect widgetArea = new Rect(100, 170, 200, 200);
        FormWidgetInfo widgetBefore =
                makeTextField(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "Text Box",
                        /* editableText= */ true,
                        /* multiLineText= */ false,
                        /* maxLength= */ -1,
                        /* fontSize= */ 12.0f);
        FormEditRecord setText =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_TEXT,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 0)
                        .setText("Gecko tail")
                        .build();
        FormWidgetInfo widgetAfter =
                makeTextField(
                        /* widgetIndex= */ 0,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Gecko tail",
                        /* accessibilityLabel= */ "Text Box",
                        /* editableText= */ true,
                        /* multiLineText= */ false,
                        /* maxLength= */ -1,
                        /* fontSize= */ 12.0f);

        verifyApplyEdit(
                TEXT_FORM,
                0,
                new Point(150, 185),
                widgetBefore,
                setText,
                widgetAfter,
                List.of(widgetArea));
    }

    @Test
    public void applyEdit_clearTextOnTextField() throws Exception {
        Rect widgetArea = new Rect(100, 225, 200, 250);
        FormWidgetInfo widgetBefore =
                makeTextField(
                        /* widgetIndex= */ 2,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "Elephant",
                        /* accessibilityLabel= */ "CharLimit",
                        /* editableText= */ true,
                        /* multiLineText= */ false,
                        /* maxLength= */ 10,
                        /* fontSize= */ 12.0f);
        FormEditRecord clearText =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_TEXT,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setText("")
                        .build();
        FormWidgetInfo widgetAfter =
                makeTextField(
                        /* widgetIndex= */ 2,
                        /* widgetRect= */ widgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "",
                        /* accessibilityLabel= */ "CharLimit",
                        /* editableText= */ true,
                        /* multiLineText= */ false,
                        /* maxLength= */ 10,
                        /* fontSize= */ 12.0f);

        verifyApplyEdit(
                TEXT_FORM,
                0,
                new Point(150, 238),
                widgetBefore,
                clearText,
                widgetAfter,
                List.of(widgetArea));
    }

    // applyEdit edge cases - click type widgets
    @Test
    public void applyEdit_clickOnReadOnlyCheckbox() throws Exception {
        FormEditRecord clickOnROCheckbox =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 0)
                        .setClickPoint(new Point(145, 40))
                        .build();

        verifyApplyEditThrowsException(CLICK_FORM, 0, clickOnROCheckbox);
    }

    @Test
    public void applyEdit_clickOnReadOnlyRadioButton() throws Exception {
        FormEditRecord clickOnRORadioButton =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setClickPoint(new Point(95, 190))
                        .build();

        verifyApplyEditThrowsException(CLICK_FORM, 0, clickOnRORadioButton);
    }

    @Test
    public void applyEdit_setTextOnClickTypeWidget() throws Exception {
        FormEditRecord setTextOnCheckbox =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_TEXT,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setText("New text")
                        .build();

        verifyApplyEditThrowsException(CLICK_FORM, 0, setTextOnCheckbox);
    }

    @Test
    public void applyEdit_setChoiceSelectionOnClickTypeWidget() throws Exception {
        FormEditRecord setChoiceOnCB =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setSelectedIndices(Set.of(1, 2, 3))
                        .build();

        verifyApplyEditThrowsException(CLICK_FORM, 0, setChoiceOnCB);
    }

    @Test
    public void applyEdit_clickOnInvalidPoint() throws Exception {
        FormEditRecord clickOnNothing =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 0)
                        .setClickPoint(new Point(0, 0))
                        .build();

        verifyApplyEditThrowsException(CLICK_FORM, 0, clickOnNothing);
    }

    // applyEdit edge cases - combobox
    @Test
    public void applyEdit_setChoiceSelectionOnReadOnlyCombobox() throws Exception {
        FormEditRecord setChoiceOnROCB =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setSelectedIndices(Set.of(1))
                        .build();

        verifyApplyEditThrowsException(COMBOBOX_FORM, 0, setChoiceOnROCB);
    }

    @Test
    public void applyEdit_setInvalidChoiceSelectionOnCombobox() throws Exception {
        FormEditRecord setBadChoice =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setSelectedIndices(Set.of(100, 365, 1436))
                        .build();

        verifyApplyEditThrowsException(COMBOBOX_FORM, 0, setBadChoice);
    }

    @Test
    public void applyEdit_setTextOnReadOnlyCombobox() throws Exception {
        FormEditRecord setTextOnROCB =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_TEXT,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setText("new text")
                        .build();

        verifyApplyEditThrowsException(COMBOBOX_FORM, 0, setTextOnROCB);
    }

    // applyEdit edge cases - text field

    @Test
    public void applyEdit_setTextOnUneditableCombobox() throws Exception {
        FormEditRecord setTextOnUneditableCB =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_TEXT,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setText("new text")
                        .build();

        verifyApplyEditThrowsException(COMBOBOX_FORM, 0, setTextOnUneditableCB);
    }

    @Test
    public void applyEdit_clickOnCombobox() throws Exception {
        FormEditRecord clickOnCB =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(new Point(150, 185))
                        .build();

        verifyApplyEditThrowsException(COMBOBOX_FORM, 0, clickOnCB);
    }

    // applyEdit edge cases - listbox
    @Test
    public void applyEdit_setMultipleChoiceSelectionOnSingleSelectionListbox() throws Exception {
        FormEditRecord pickMultipleOnSingleChoice =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 0)
                        .setSelectedIndices(Set.of(1, 2))
                        .build();

        verifyApplyEditThrowsException(LISTBOX_FORM, 0, pickMultipleOnSingleChoice);
    }

    @Test
    public void applyEdit_setChoiceSelectionOnReadOnlyListbox() throws Exception {
        FormEditRecord setChoiceOnROLB =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setSelectedIndices(Set.of(1))
                        .build();

        verifyApplyEditThrowsException(LISTBOX_FORM, 0, setChoiceOnROLB);
    }

    @Test
    public void applyEdit_clickOnListbox() throws Exception {
        FormEditRecord clickOnLB =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(new Point(150, 235))
                        .build();

        verifyApplyEditThrowsException(LISTBOX_FORM, 0, clickOnLB);
    }

    @Test
    public void applyEdit_setTextOnListbox() throws Exception {
        FormEditRecord setTextOnLB =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_TEXT,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setText("new text")
                        .build();

        verifyApplyEditThrowsException(COMBOBOX_FORM, 0, setTextOnLB);
    }

    @Test
    public void applyEdit_setTextOnReadOnlyTextField() throws Exception {
        FormEditRecord setTextOnROTF =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_TEXT,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setText("new text")
                        .build();

        verifyApplyEditThrowsException(TEXT_FORM, 0, setTextOnROTF);
    }

    @Test
    public void applyEdit_clickOnTextField() throws Exception {
        FormEditRecord clickOnTF =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(new Point(150, 185))
                        .build();

        verifyApplyEditThrowsException(TEXT_FORM, 0, clickOnTF);
    }

    @Test
    public void applyEdit_setChoiceSelectionOnTextField() throws Exception {
        FormEditRecord setChoiceOnTF =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_SET_INDICES,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setSelectedIndices(Set.of(1, 2, 3))
                        .build();

        verifyApplyEditThrowsException(TEXT_FORM, 0, setChoiceOnTF);
    }

    @Test
    public void applyEdits_multipleFormEditRecords_returnsEmptyList() throws IOException {
        Rect checkboxWidgetArea = new Rect(135, 70, 155, 90);
        FormWidgetInfo checkboxBefore =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "checkbox");
        Point checkBoxPoint = new Point(145, 80);
        FormEditRecord checkboxRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(checkBoxPoint)
                        .build();
        FormWidgetInfo checkboxAfter =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "true",
                        /* accessibilityLabel= */ "checkbox");

        Rect radioButtonWidgetArea = new Rect(85, 230, 105, 250);
        FormWidgetInfo radioButtonBefore =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");
        Point radioButtonPoint = new Point(95, 240);
        FormEditRecord radioButtonRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 5)
                        .setClickPoint(radioButtonPoint)
                        .build();
        FormWidgetInfo radioButtonAfter =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "true",
                        /* accessibilityLabel= */ "");

        List<Point> positions = List.of(new Point[] {checkBoxPoint, radioButtonPoint});
        List<FormWidgetInfo> beforeWidgetInfos =
                List.of(new FormWidgetInfo[] {checkboxBefore, radioButtonBefore});
        List<FormWidgetInfo> afterWidgetInfos =
                List.of(new FormWidgetInfo[] {checkboxAfter, radioButtonAfter});
        List<FormEditRecord> records =
                List.of(new FormEditRecord[] {checkboxRecord, radioButtonRecord});

        verifyApplyEditsPreV(
                CLICK_FORM, 0, positions, beforeWidgetInfos, records, afterWidgetInfos);
    }

    @Test
    public void applyEdits_singleInvalidFormEditRecord_returnsRecord() throws IOException {
        Rect checkboxWidgetArea = new Rect(135, 70, 155, 90);
        FormWidgetInfo checkboxBefore =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "checkbox");
        Point checkBoxPoint = new Point(145, 80);
        FormEditRecord checkboxRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(checkBoxPoint)
                        .build();
        FormWidgetInfo checkboxAfter =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "true",
                        /* accessibilityLabel= */ "checkbox");

        Rect radioButtonWidgetArea = new Rect(85, 230, 105, 250);
        FormWidgetInfo radioButtonBefore =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");
        Point radioButtonPoint = new Point(95, 240);
        FormEditRecord radioButtonRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setClickPoint(new Point(0, 0))
                        .build();
        FormWidgetInfo radioButtonAfter =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");

        List<Point> positions = List.of(new Point[] {checkBoxPoint, radioButtonPoint});
        List<FormWidgetInfo> beforeWidgetInfos =
                List.of(new FormWidgetInfo[] {checkboxBefore, radioButtonBefore});
        List<FormWidgetInfo> afterWidgetInfos =
                List.of(new FormWidgetInfo[] {checkboxAfter, radioButtonAfter});
        List<FormEditRecord> records =
                List.of(new FormEditRecord[] {checkboxRecord, radioButtonRecord});

        verifyApplyEditsPreVNotNull(
                CLICK_FORM,
                0,
                positions,
                beforeWidgetInfos,
                records,
                afterWidgetInfos,
                List.of(radioButtonRecord));
    }

    @Test
    public void applyEdits_multipleInvalidFormEditRecord_returnsFirstRecord() throws IOException {
        Rect checkboxWidgetArea = new Rect(135, 70, 155, 90);
        FormWidgetInfo checkboxBefore =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "checkbox");
        Point checkBoxPoint = new Point(145, 80);
        FormEditRecord checkboxRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(new Point(0, 0))
                        .build();
        FormWidgetInfo checkboxAfter =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "checkbox");

        Rect radioButtonWidgetArea = new Rect(85, 230, 105, 250);
        FormWidgetInfo radioButtonBefore =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");
        Point radioButtonPoint = new Point(95, 240);
        FormEditRecord radioButtonRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setClickPoint(new Point(0, 0))
                        .build();
        FormWidgetInfo radioButtonAfter =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");

        List<Point> positions = List.of(new Point[] {checkBoxPoint, radioButtonPoint});
        List<FormWidgetInfo> beforeWidgetInfos =
                List.of(new FormWidgetInfo[] {checkboxBefore, radioButtonBefore});
        List<FormWidgetInfo> afterWidgetInfos =
                List.of(new FormWidgetInfo[] {checkboxAfter, radioButtonAfter});
        List<FormEditRecord> records =
                List.of(new FormEditRecord[] {checkboxRecord, radioButtonRecord});

        verifyApplyEditsPreVNotNull(
                CLICK_FORM,
                0,
                positions,
                beforeWidgetInfos,
                records,
                afterWidgetInfos,
                List.of(checkboxRecord, radioButtonRecord));
    }

    @Test
    public void applyEdits_documentClosed_throwsIllegalStateException() throws IOException {
        Rect checkboxWidgetArea = new Rect(135, 70, 155, 90);
        FormWidgetInfo checkboxBefore =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "checkbox");
        Point checkBoxPoint = new Point(145, 80);
        FormEditRecord checkboxRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(checkBoxPoint)
                        .build();
        FormWidgetInfo checkboxAfter =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "true",
                        /* accessibilityLabel= */ "checkbox");

        Rect radioButtonWidgetArea = new Rect(85, 230, 105, 250);
        FormWidgetInfo radioButtonBefore =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");
        Point radioButtonPoint = new Point(95, 240);
        FormEditRecord radioButtonRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setClickPoint(new Point(0, 0))
                        .build();
        FormWidgetInfo radioButtonAfter =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");
        List<FormEditRecord> records =
                List.of(new FormEditRecord[] {checkboxRecord, radioButtonRecord});
        verifyApplyEditsPreVDocumentClosed(CLICK_FORM, 0, records);
    }

    @Test
    public void applyEdits_pageClosed_throwsIllegalStateException() throws IOException {
        Rect checkboxWidgetArea = new Rect(135, 70, 155, 90);
        FormWidgetInfo checkboxBefore =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "checkbox");
        Point checkBoxPoint = new Point(145, 80);
        FormEditRecord checkboxRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 1)
                        .setClickPoint(checkBoxPoint)
                        .build();
        FormWidgetInfo checkboxAfter =
                makeCheckbox(
                        /* widgetIndex= */ 1,
                        /* widgetRect= */ checkboxWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "true",
                        /* accessibilityLabel= */ "checkbox");

        Rect radioButtonWidgetArea = new Rect(85, 230, 105, 250);
        FormWidgetInfo radioButtonBefore =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");
        Point radioButtonPoint = new Point(95, 240);
        FormEditRecord radioButtonRecord =
                new FormEditRecord.Builder(
                                FormEditRecord.EDIT_TYPE_CLICK,
                                /* pageNumber= */ 0,
                                /* widgetIndex= */ 2)
                        .setClickPoint(new Point(0, 0))
                        .build();
        FormWidgetInfo radioButtonAfter =
                makeRadioButton(
                        /* widgetIndex= */ 5,
                        /* widgetRect= */ radioButtonWidgetArea,
                        /* readOnly= */ false,
                        /* textValue= */ "false",
                        /* accessibilityLabel= */ "");
        List<FormEditRecord> records =
                List.of(new FormEditRecord[] {checkboxRecord, radioButtonRecord});
        verifyApplyEditsPreVPageClosed(CLICK_FORM, 0, records);
    }

    private void verifyFormType(@RawRes int docRes, int expectedFormType) throws Exception {
        verifyFormTypePreV(docRes, expectedFormType);
    }

    private void verifyFormTypePreV(@RawRes int docRes, int expectedFormType) throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null)) {
            int formType = renderer.getPdfFormType();
            assertEquals(expectedFormType, formType);
        }
    }

    private void verifyFormWidgetInfo(
            @RawRes int docRes, int pageNum, Point position, FormWidgetInfo expectedInfo)
            throws Exception {
        verifyFormWidgetInfoPreV(docRes, pageNum, position, expectedInfo);
    }

    private void verifyFormWidgetInfoPreV(
            @RawRes int docRes, int pageNum, Point position, FormWidgetInfo expectedInfo)
            throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(pageNum)) {
                // Verify position API
                FormWidgetInfo foundInfo = page.getFormWidgetInfoAtPosition(position.x, position.y);

                assertEquals(expectedInfo, foundInfo);
                // Verify index API
                assertEquals(foundInfo, page.getFormWidgetInfoAtIndex(foundInfo.getWidgetIndex()));
            }
        }
    }

    private void verifyFormWidgetInfos(
            @RawRes int docRes,
            int pageNum,
            Set<Integer> widgetTypes,
            List<FormWidgetInfo> expectedInfos)
            throws Exception {
        verifyFormWidgetInfosPreV(docRes, pageNum, widgetTypes, expectedInfos);
    }

    private void verifyFormWidgetInfosPreV(
            @RawRes int docRes,
            int pageNum,
            Set<Integer> widgetTypes,
            List<FormWidgetInfo> expectedInfos)
            throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(pageNum)) {
                List<FormWidgetInfo> foundInfos = page.getFormWidgetInfos(widgetTypes);

                assertEquals(expectedInfos.size(), foundInfos.size());
                for (int i = 0; i < foundInfos.size(); i++) {
                    FormWidgetInfo expectedInfo = expectedInfos.get(i);
                    assertEquals(expectedInfo, foundInfos.get(i));
                }
            }
        }
    }

    private void verifyApplyEdit(
            @RawRes int docRes,
            int pageNum,
            Point position,
            FormWidgetInfo expectedInfoBefore,
            FormEditRecord editRecord,
            FormWidgetInfo expectedInfoAfter,
            List<Rect> expectedInvalidRects)
            throws Exception {
        verifyApplyEditPreV(
                docRes,
                pageNum,
                position,
                expectedInfoBefore,
                editRecord,
                expectedInfoAfter,
                expectedInvalidRects);
    }

    private void verifyApplyEditPreV(
            @RawRes int docRes,
            int pageNum,
            Point position,
            FormWidgetInfo expectedInfoBefore,
            FormEditRecord editRecord,
            FormWidgetInfo expectedInfoAfter,
            List<Rect> expectedInvalidRects)
            throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(pageNum)) {
                FormWidgetInfo beforeInfo =
                        page.getFormWidgetInfoAtPosition(position.x, position.y);
                assertEquals(expectedInfoBefore, beforeInfo);

                List<Rect> invalidatedRects = page.applyEdit(editRecord);

                FormWidgetInfo afterInfo = page.getFormWidgetInfoAtPosition(position.x, position.y);
                assertEquals(expectedInfoAfter, afterInfo);
                // A compatible implementation of this API may invalidate a larger area than we
                // expect, but it cannot invalidate a smaller area than we expect.
                assertTrue(
                        fullyContain(
                                /* innerRects= */ expectedInvalidRects,
                                /* outerRects= */ invalidatedRects));
            }
        }
    }

    private void verifyApplyEditsPreV(
            @RawRes int docRes,
            int pageNum,
            List<Point> positions,
            List<FormWidgetInfo> expectedInfosBefore,
            List<FormEditRecord> editRecords,
            List<FormWidgetInfo> expectedInfosAfter)
            throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(pageNum)) {
                for (int i = 0; i < positions.size(); i++) {
                    FormWidgetInfo beforeInfo =
                            page.getFormWidgetInfoAtPosition(
                                    positions.get(i).x, positions.get(i).y);
                    assertEquals(expectedInfosBefore.get(i), beforeInfo);
                }

                List<FormEditRecord> result = page.applyEdits(editRecords);
                assertEquals(0, result.size());

                for (int i = 0; i < positions.size(); i++) {
                    FormWidgetInfo afterInfo =
                            page.getFormWidgetInfoAtPosition(
                                    positions.get(i).x, positions.get(i).y);
                    assertEquals(expectedInfosAfter.get(i), afterInfo);
                }
            }
        }
    }

    private void verifyApplyEditsPreVNotNull(
            @RawRes int docRes,
            int pageNum,
            List<Point> positions,
            List<FormWidgetInfo> expectedInfosBefore,
            List<FormEditRecord> editRecords,
            List<FormWidgetInfo> expectedInfosAfter,
            List<FormEditRecord> invalidRecords)
            throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(pageNum)) {
                for (int i = 0; i < positions.size(); i++) {
                    FormWidgetInfo beforeInfo =
                            page.getFormWidgetInfoAtPosition(
                                    positions.get(i).x, positions.get(i).y);
                    assertEquals(expectedInfosBefore.get(i), beforeInfo);
                }

                List<FormEditRecord> result = page.applyEdits(editRecords);
                assertEquals(invalidRecords, result);

                for (int i = 0; i < positions.size(); i++) {
                    FormWidgetInfo afterInfo =
                            page.getFormWidgetInfoAtPosition(
                                    positions.get(i).x, positions.get(i).y);
                    assertEquals(expectedInfosAfter.get(i), afterInfo);
                }
            }
        }
    }

    private void verifyApplyEditsPreVDocumentClosed(
            @RawRes int docRes, int pageNum, List<FormEditRecord> editRecords) throws IOException {
        PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(pageNum);
        page.close();
        renderer.close();
        assertThrows(IllegalStateException.class, () -> page.applyEdits(editRecords));
    }

    private void verifyApplyEditsPreVPageClosed(
            @RawRes int docRes, int pageNum, List<FormEditRecord> editRecords) throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null)) {
            PdfRendererPreV.Page page = renderer.openPage(pageNum);
            page.close();
            assertThrows(IllegalStateException.class, () -> page.applyEdits(editRecords));
        }
    }

    private void verifyApplyEditThrowsException(
            @RawRes int docRes, int pageNum, FormEditRecord editRecord) throws Exception {
        verifyApplyEditThrowsExceptionPreV(docRes, pageNum, editRecord);
    }

    private void verifyApplyEditThrowsExceptionPreV(
            @RawRes int docRes, int pageNum, FormEditRecord editRecord) throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, mContext, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(pageNum)) {
                assertThrows(IllegalArgumentException.class, () -> page.applyEdit(editRecord));
            }
        }
    }
}
