/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.graphics.pdf.PdfRendererPreV.DOCUMENT_LINEARIZED_TYPE_LINEARIZED;
import static android.graphics.pdf.PdfRendererPreV.DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED;
import static android.graphics.pdf.cts.Utils.A4_HEIGHT_PTS;
import static android.graphics.pdf.cts.Utils.A4_PORTRAIT;
import static android.graphics.pdf.cts.Utils.A4_WIDTH_PTS;
import static android.graphics.pdf.cts.Utils.A5_PORTRAIT;
import static android.graphics.pdf.cts.Utils.INCORRECT_LOAD_PARAMS;
import static android.graphics.pdf.cts.Utils.LOAD_PARAMS;
import static android.graphics.pdf.cts.Utils.PROTECTED_PDF;
import static android.graphics.pdf.cts.Utils.SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR;
import static android.graphics.pdf.cts.Utils.SAMPLE_PDF;
import static android.graphics.pdf.cts.Utils.calculateArea;
import static android.graphics.pdf.cts.Utils.createRenderer;
import static android.graphics.pdf.cts.Utils.createRendererUsingNewConstructor;
import static android.graphics.pdf.cts.Utils.getFile;
import static android.graphics.pdf.cts.Utils.getParcelFileDescriptorFromResourceId;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRenderer.Page;
import android.graphics.pdf.RenderParams;
import android.graphics.pdf.content.PdfPageGotoLinkContent;
import android.graphics.pdf.models.PageMatchBounds;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.os.ParcelFileDescriptor;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * All test for {@link PdfRenderer} beside the valid transformation parameter tests of {@link
 * PdfRenderer.Page#render}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PdfRendererTest {
    private static final int A5_PORTRAIT_PRINTSCALING_DEFAULT =
            R.raw.a5_portrait_rgbb_1_6_printscaling_default;
    private static final int A5_PORTRAIT_PRINTSCALING_NONE =
            R.raw.a5_portrait_rgbb_1_6_printscaling_none;
    private static final int TWO_PAGES = R.raw.two_pages;

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void getDocumentType_withNonLinearizedPdf() throws Exception {
        PdfRenderer renderer = createRenderer(SAMPLE_PDF, mContext);
        assertThat(renderer.getDocumentLinearizationType()).isEqualTo(
                DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED);
        renderer.close();
    }

    @Test
    public void getDocumentType_withLinearizedPdf() throws Exception {
        PdfRenderer renderer = createRendererUsingNewConstructor(PROTECTED_PDF, mContext,
                LOAD_PARAMS);
        assertThat(renderer.getDocumentLinearizationType()).isEqualTo(
                DOCUMENT_LINEARIZED_TYPE_LINEARIZED);
        renderer.close();
    }

    @Test
    public void constructRendererNull() throws Exception {
        assertThrows(NullPointerException.class, () -> new PdfRenderer(null));

        assertThrows(NullPointerException.class, () -> new PdfRenderer(null, null));

    }

    @Test
    public void constructRendererWithNullLoadParams() throws Exception {
        assertThrows(NullPointerException.class,
                () -> new PdfRenderer(getParcelFileDescriptorFromResourceId(SAMPLE_PDF, mContext),
                        null));
    }

    @Test
    public void constructRendererFromNonPDF() throws Exception {
        // Open jpg as if it was a PDF
        assertThrows(IOException.class, () -> createRenderer(R.raw.testimage, mContext));

        // assert using new constructor
        assertThrows(IOException.class,
                () -> createRendererUsingNewConstructor(R.raw.testimage, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    @Test
    public void constructRendererFromProtectedPDF() throws Exception {
        assertThrows(SecurityException.class, () -> createRenderer(PROTECTED_PDF, mContext));

        // assert using new constructor
        assertThrows(SecurityException.class,
                () -> createRendererUsingNewConstructor(PROTECTED_PDF, mContext,
                        INCORRECT_LOAD_PARAMS));
    }

    @Test
    public void rendererRecoversAfterFailure() throws Exception {
        // Create rendered to prevent lib from being unloaded
        PdfRenderer firstRenderer = createRenderer(A4_PORTRAIT, mContext);

        assertThrows(SecurityException.class, () -> createRenderer(PROTECTED_PDF, mContext));

        // We can create new renderers after we failed to create one
        PdfRenderer renderer = createRenderer(TWO_PAGES, mContext);
        renderer.close();

        // assert using new constructor
        PdfRenderer newRenderer = createRendererUsingNewConstructor(TWO_PAGES, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR);
        newRenderer.close();

        firstRenderer.close();
    }

    @Test
    public void useRendererAfterClose() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
        renderer.close();

        assertRendererAfterClose(renderer);

        PdfRenderer newRenderer = createRendererUsingNewConstructor(A4_PORTRAIT, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR);
        newRenderer.close();

        assertRendererAfterClose(newRenderer);
    }

    @Test
    public void usePageAfterClose() throws Exception {
        assertPageAfterClose(createRenderer(A4_PORTRAIT, mContext), false);

        assertPageAfterClose(createRendererUsingNewConstructor(SAMPLE_PDF, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR), true);

    }

    private void assertPageAfterClose(PdfRenderer renderer, boolean usesNewConstructor)
            throws Exception {
        PdfRenderer.Page page = renderer.openPage(0);

        Point leftPoint = new Point(275, 163);
        Point rightPoint = new Point(65, 125);
        page.close();

        // Legacy behavior: The properties are cached, hence they are still available after the page
        //                  is closed
        page.getHeight();
        page.getWidth();
        page.getIndex();

        assertThrows(IllegalStateException.class, page::close);
        assertThrows(IllegalStateException.class, page::getLinkContents);
        assertThrows(IllegalStateException.class, page::getGotoLinks);
        assertThrows(IllegalStateException.class, page::getTextContents);
        assertThrows(IllegalStateException.class, page::getImageContents);
        assertThrows(IllegalStateException.class, () -> page.searchText("more"));
        assertThrows(IllegalStateException.class,
                () -> page.selectContent(new SelectionBoundary(leftPoint),
                        new SelectionBoundary(rightPoint), false));

        if (usesNewConstructor) {
            assertThrows(IllegalStateException.class, () -> page.render(
                    Bitmap.createBitmap(A4_WIDTH_PTS, A4_HEIGHT_PTS, Bitmap.Config.ARGB_8888), null,
                    null, new RenderParams.Builder(1).build()));
        } else {
            // Legacy support. An IllegalStateException would be nice by unfortunately the legacy
            // implementation returned NullPointerException
            assertThrows(NullPointerException.class,
                    () -> page.render(null, null, null, Page.RENDER_MODE_FOR_DISPLAY));
        }
        renderer.close();
    }

    @Test
    public void closeWithOpenPage() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
        Page page = renderer.openPage(0);

        assertThrows(IllegalStateException.class, renderer::close);

        page.close();
        renderer.close();
    }

    @Test
    public void closeNewRendererWithOpenPage() throws Exception {
        PdfRenderer renderer = createRendererUsingNewConstructor(A4_PORTRAIT, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR);
        Page page = renderer.openPage(0);

        assertThrows(IllegalStateException.class, renderer::close);

        page.close();
        renderer.close();
    }

    @Test
    public void openTwoPages() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES, mContext)) {
            // Cannot open two pages at once
            Page page = renderer.openPage(0);
            assertThrows(IllegalStateException.class, () -> renderer.openPage(1));

            page.close();
        }
    }

    @Test
    public void testPageCount() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES, mContext)) {
            assertEquals(2, renderer.getPageCount());
        }

        // assert using new constructor
        try (PdfRenderer renderer = createRendererUsingNewConstructor(TWO_PAGES, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR)) {
            assertEquals(2, renderer.getPageCount());
        }
    }

    @Test
    public void testOpenPage() throws Exception {
        assertOpenPage(createRenderer(TWO_PAGES, mContext));

        assertOpenPage(createRendererUsingNewConstructor(TWO_PAGES, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    @Test
    public void testPageSize() throws Exception {
        assertPageSize(createRenderer(A4_PORTRAIT, mContext));

        assertPageSize(createRendererUsingNewConstructor(A4_PORTRAIT, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    @Test
    public void testPrintScaleDefault() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT, mContext)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }

        try (PdfRenderer renderer = createRendererUsingNewConstructor(A5_PORTRAIT, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void testPrintScalePDF16Default() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT_PRINTSCALING_DEFAULT, mContext)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }

        try (PdfRenderer renderer = createRendererUsingNewConstructor(
                A5_PORTRAIT_PRINTSCALING_DEFAULT, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void testPrintScalePDF16None() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT_PRINTSCALING_NONE, mContext)) {
            assertFalse(renderer.shouldScaleForPrinting());
        }

        try (PdfRenderer renderer = createRendererUsingNewConstructor(A5_PORTRAIT_PRINTSCALING_NONE,
                mContext, SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR)) {
            assertFalse(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void getTextPdfContents_pdfWithText() throws Exception {
        assertTextPdfContents_pdfWithText(createRenderer(R.raw.sample_test, mContext));

        assertTextPdfContents_pdfWithText(
                createRendererUsingNewConstructor(R.raw.sample_test, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertTextPdfContents_pdfWithText(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getTextContents().size()).isEqualTo(1);
        assertThat(firstPage.getTextContents().get(0).getText()).isEqualTo(
                "A Simple PDF File, which will be used for testing.");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getTextPdfContents_pdfWithTextAndImages() throws Exception {
        assertTextPdfContents_pdfWithTextAndImages(createRenderer(R.raw.alt_text, mContext));

        assertTextPdfContents_pdfWithTextAndImages(
                createRendererUsingNewConstructor(R.raw.alt_text, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertTextPdfContents_pdfWithTextAndImages(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getTextContents().size()).isEqualTo(1);
        assertThat(firstPage.getTextContents().get(0).getText().lines().toList().size()).isEqualTo(
                2);
        assertThat(firstPage.getTextContents().get(0).getText().lines().toList().get(0)).isEqualTo(
                "Social Security Administration Guide:");
        assertThat(firstPage.getTextContents().get(0).getText().lines().toList().get(1)).isEqualTo(
                "Alternate text for images");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getImagePdfContents_pdfWithText() throws Exception {
        assertImagePdfContents_pdfWithText(createRenderer(SAMPLE_PDF, mContext));

        assertImagePdfContents_pdfWithText(createRendererUsingNewConstructor(SAMPLE_PDF, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertImagePdfContents_pdfWithText(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getImageContents().size()).isEqualTo(0);

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getImagePdfContents_pdfWithAltText() throws Exception {
        assertImagePdfContents_pdfWithAltText(createRenderer(R.raw.alt_text, mContext));

        assertImagePdfContents_pdfWithAltText(
                createRendererUsingNewConstructor(R.raw.alt_text, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertImagePdfContents_pdfWithAltText(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getImageContents().size()).isEqualTo(1);
        assertThat(firstPage.getImageContents().get(0).getAltText()).isEqualTo(
                "Social Security Administration Logo");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPageLinks_pdfWithoutLink() throws Exception {
        assertPageLinks_pdfWithoutLink(createRenderer(R.raw.sample_links, mContext));

        assertPageLinks_pdfWithoutLink(
                createRendererUsingNewConstructor(R.raw.sample_links, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertPageLinks_pdfWithoutLink(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(1);

        assertThat(firstPage.getLinkContents().size()).isEqualTo(0);

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPageLinks_pdfWithLink() throws Exception {
        assertPageLinks_pdfWithLink(createRenderer(R.raw.sample_links, mContext));

        assertPageLinks_pdfWithLink(createRendererUsingNewConstructor(R.raw.sample_links, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertPageLinks_pdfWithLink(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getLinkContents().size()).isEqualTo(1);
        assertThat(firstPage.getLinkContents().get(0).getBounds().size()).isEqualTo(1);
        assertThat(firstPage.getLinkContents().get(0).getUri().getScheme()).isEqualTo("http");
        assertThat(firstPage.getLinkContents().get(0).getUri().getSchemeSpecificPart()).isEqualTo(
                "//www.antennahouse.com/purchase.htm");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText() throws Exception {
        assertSearchPageText(createRenderer(SAMPLE_PDF, mContext));

        assertSearchPageText(createRendererUsingNewConstructor(SAMPLE_PDF, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertSearchPageText(PdfRenderer renderer) {

        PdfRenderer.Page firstPage = renderer.openPage(0);
        int firstPageWidth = firstPage.getWidth();
        int firstPageHeight = firstPage.getHeight();

        // First query, First page only contains single "simple"
        List<PageMatchBounds> firstPageRects = firstPage.searchText("simple");

        assertThat(firstPageRects.size()).isEqualTo(1);
        assertThat(firstPageRects.get(0).getBounds().size()).isEqualTo(1);
        // the rects area should be less than the page area
        assertThat(calculateArea(firstPageRects)).isLessThan(firstPageHeight * firstPageWidth);
        firstPage.close();

        // Second page
        PdfRenderer.Page secondPage = renderer.openPage(1);
        int secondPageWidth = secondPage.getWidth();
        int secondPageHeight = secondPage.getHeight();

        // second query
        List<PageMatchBounds> secondPageRects = secondPage.searchText("more");

        assertThat(secondPageRects.size()).isEqualTo(28);
        // assert that size of all rects are 1 as more does not extend to other lines
        for (PageMatchBounds rect : secondPageRects) {
            assertThat(rect.getBounds().size()).isEqualTo(1);
        }
        // the rects area should be less than the page area
        assertThat(calculateArea(secondPageRects)).isLessThan(secondPageHeight * secondPageWidth);
        secondPage.close();

        // Third page,
        PdfRenderer.Page thirdPage = renderer.openPage(2);

        //third query assert Heading, the area should be less than the page area
        List<PageMatchBounds> thirdPageRects = thirdPage.searchText("Simple PDF File 2");

        assertThat(thirdPageRects.size()).isEqualTo(1);
        assertThat(thirdPageRects.get(0).getBounds().size()).isEqualTo(1);
        int thirdPageWidth = thirdPage.getWidth();
        int thirdPageHeight = thirdPage.getHeight();
        assertThat(calculateArea(thirdPageRects)).isLessThan(thirdPageHeight * thirdPageWidth);

        thirdPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText_queryTextInMultipleLines() throws Exception {
        assertSearchPageText_queryTextInMultipleLines(createRenderer(SAMPLE_PDF, mContext));

        assertSearchPageText_queryTextInMultipleLines(
                createRendererUsingNewConstructor(SAMPLE_PDF, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertSearchPageText_queryTextInMultipleLines(PdfRenderer renderer) {

        PdfRenderer.Page openPage = renderer.openPage(1);
        int secondPageWidth = openPage.getWidth();
        int secondPageHeight = openPage.getHeight();

        List<PageMatchBounds> secondPageRects = openPage.searchText("more text");

        assertThat(secondPageRects.size()).isEqualTo(27);
        // assert that size of all rects are less than 3
        int count = 0;
        for (PageMatchBounds rect : secondPageRects) {
            count += rect.getBounds().size();
            assertThat(rect.getBounds().size()).isLessThan(3);
        }
        // 3 of the "More Text" are split via lines so overall count for rects should be 30
        assertThat(count).isEqualTo(30);
        // the rects area should be less than the page area
        assertThat(calculateArea(secondPageRects)).isLessThan(secondPageHeight * secondPageWidth);

        openPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText_returnsEmptySearchResult() throws Exception {
        assertSearchPageText_returnsEmptySearchResult(createRenderer(SAMPLE_PDF, mContext));

        assertSearchPageText_returnsEmptySearchResult(
                createRendererUsingNewConstructor(SAMPLE_PDF, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertSearchPageText_returnsEmptySearchResult(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.searchText("z").isEmpty()).isTrue();

        firstPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText_withNullQuery_throwsException() throws Exception {
        assertSearchPageText_withNullQuery_throwsException(createRenderer(SAMPLE_PDF, mContext));

        assertSearchPageText_withNullQuery_throwsException(
                createRendererUsingNewConstructor(SAMPLE_PDF, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertSearchPageText_withNullQuery_throwsException(PdfRenderer renderer) {
        PdfRenderer.Page openPage = renderer.openPage(1);

        assertThrows(NullPointerException.class, () -> openPage.searchText(null));
    }

    @Test
    public void write_withNullDest_throwsException() throws Exception {
        assertWrite_withNullDest(createRenderer(SAMPLE_PDF, mContext));

        assertWrite_withNullDest(
                createRendererUsingNewConstructor(PROTECTED_PDF, mContext, LOAD_PARAMS));
    }

    private void assertWrite_withNullDest(PdfRenderer renderer) throws Exception {
        assertThrows(NullPointerException.class, () -> renderer.write(null, true));
    }

    //TODO: Update the test to assert pdfs.
    @Test
    public void write_protectedPdf_withSecurity() throws Exception {
        PdfRenderer expectedRenderer = createRendererUsingNewConstructor(PROTECTED_PDF, mContext,
                LOAD_PARAMS);
        File filePath = getFile(mContext, "protectedPdf1.pdf");
        String absolutePath = filePath.getAbsolutePath();
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(filePath,
                ParcelFileDescriptor.MODE_READ_WRITE);

        assert descriptor != null;
        expectedRenderer.write(descriptor, false);

        // get the file descriptor for the saved as file
        File saveAsFile = new File(absolutePath);
        ParcelFileDescriptor saveAsDescp = ParcelFileDescriptor.open(saveAsFile,
                ParcelFileDescriptor.MODE_READ_ONLY);

        // create a renderer with password for this file descriptor
        assertThat(saveAsDescp).isNotNull();
        PdfRenderer renderer = new PdfRenderer(saveAsDescp, LOAD_PARAMS);
        assertThat(renderer).isNotNull();

        renderer.close();
    }

    @Test
    public void write_withUnprotected() throws Exception {
        assertWriteWithUnprotectedPdf(createRenderer(SAMPLE_PDF, mContext), "newPdf1.pdf");

        assertWriteWithUnprotectedPdf(createRendererUsingNewConstructor(SAMPLE_PDF, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR), "newPdf.pdf");
    }

    private void assertWriteWithUnprotectedPdf(PdfRenderer expectedRenderer, String fileName)
            throws Exception {

        File filePath = getFile(mContext, fileName);
        String absolutePath = filePath.getAbsolutePath();
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(filePath,
                ParcelFileDescriptor.MODE_READ_WRITE);

        assert descriptor != null;
        expectedRenderer.write(descriptor, true);

        // get the file descriptor for the saved as file
        File saveAsFile = new File(absolutePath);
        ParcelFileDescriptor saveAsDescp = ParcelFileDescriptor.open(saveAsFile,
                ParcelFileDescriptor.MODE_READ_ONLY);

        // create a renderer without password for this file descriptor
        assertThat(saveAsDescp).isNotNull();
        PdfRenderer renderer = new PdfRenderer(saveAsDescp);
        assertSamplePdf(renderer, expectedRenderer);
    }

    @Test
    public void selectPageText() throws Exception {
        assertSelectPageText(createRenderer(SAMPLE_PDF, mContext));

        assertSelectPageText(
                createRendererUsingNewConstructor(PROTECTED_PDF, mContext, LOAD_PARAMS));
    }

    private void assertSelectPageText(PdfRenderer renderer) {

        PdfRenderer.Page firstPage = renderer.openPage(1);

        Point point = new Point(77, 104);
        SelectionBoundary start = new SelectionBoundary(point);
        SelectionBoundary stop = new SelectionBoundary(point);
        // first query
        PageSelection firstTextSelection = firstPage.selectContent(start, stop, true);
        // second query
        Point leftPoint = new Point(93, 139);
        Point rightPoint = new Point(147, 139);
        PageSelection secondTextSelection = firstPage.selectContent(
                new SelectionBoundary(leftPoint), new SelectionBoundary(rightPoint), true);

        // first selected text is: "this"
        assertPageSelection(firstTextSelection, 1, 1);
        assertThat(firstTextSelection.getTextSelections().get(
                0).getSelectedTextContents().getText()).isEqualTo("This");
        // assert second selected content
        assertPageSelection(secondTextSelection, 1, 1);
        assertThat(secondTextSelection.getTextSelections().get(
                0).getSelectedTextContents().getText()).isEqualTo("And more te");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void selectPageText_textSpreadAcrossMultipleLines() throws Exception {
        assertSelectPageText_textSpreadAcrossMultipleLines(createRenderer(SAMPLE_PDF, mContext));

        assertSelectPageText_textSpreadAcrossMultipleLines(
                createRendererUsingNewConstructor(PROTECTED_PDF, mContext, LOAD_PARAMS));

    }

    private void assertSelectPageText_textSpreadAcrossMultipleLines(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(1);

        Point leftPoint = new Point(93, 139);
        Point rightPoint = new Point(135, 168);
        PageSelection textSelection = firstPage.selectContent(new SelectionBoundary(leftPoint),
                new SelectionBoundary(rightPoint), true);

        assertPageSelection(textSelection, 2, 1);
        assertThat(textSelection.getTextSelections().get(
                0).getSelectedTextContents().getText().lines().toList().size()).isEqualTo(2);
        assertThat(textSelection.getTextSelections().get(
                0).getSelectedTextContents().getText().lines().toList().get(0)).isEqualTo(
                "And more text. And more text. And more text. ");
        assertThat(textSelection.getTextSelections().get(
                0).getSelectedTextContents().getText().lines().toList().get(1)).isEqualTo(
                " And more text");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void selectPageText_leftToRight() throws Exception {
        assertSelectPageText_leftToRight(createRenderer(SAMPLE_PDF, mContext));

        assertSelectPageText_leftToRight(
                createRendererUsingNewConstructor(PROTECTED_PDF, mContext, LOAD_PARAMS));
    }

    private void assertSelectPageText_leftToRight(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(1);

        Point leftPoint = new Point(275, 163);
        Point rightPoint = new Point(65, 125);
        PageSelection fourthTextSelection = firstPage.selectContent(
                new SelectionBoundary(leftPoint), new SelectionBoundary(rightPoint), false);

        assertPageSelection(fourthTextSelection, 3, 1);
        assertThat(fourthTextSelection.getTextSelections().get(
                0).getSelectedTextContents().getText().lines().toList().get(0)).isEqualTo(
                "just for use in the Virtual Mechanics tutorials. More text. And more ");
        assertThat(fourthTextSelection.getTextSelections().get(
                0).getSelectedTextContents().getText().lines().toList().get(1)).isEqualTo(
                " text. And more text. And more text. And more text. ");
        assertThat(fourthTextSelection.getTextSelections().get(
                0).getSelectedTextContents().getText().lines().toList().get(2)).isEqualTo(
                " And more text. And more text. And more text. ");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void renderPage_withNullParams_throwsException() throws Exception {
        try (PdfRenderer renderer = createRendererUsingNewConstructor(A4_PORTRAIT, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR);
             PdfRenderer.Page page = renderer.openPage(0)) {
            assertThrows(NullPointerException.class, () -> page.render(
                    Bitmap.createBitmap(A4_WIDTH_PTS, A4_HEIGHT_PTS, Bitmap.Config.ARGB_8888), null,
                    null, null));
        }
    }

    @Test
    public void selectPageText_emptySpace() throws Exception {
        assertSelectPageText_emptySpace(createRenderer(SAMPLE_PDF, mContext));

        assertSelectPageText_emptySpace(
                createRendererUsingNewConstructor(PROTECTED_PDF, mContext, LOAD_PARAMS));
    }

    private void assertSelectPageText_emptySpace(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(1);

        Point leftPoint = new Point(157, 330);
        Point rightPoint = new Point(157, 330);
        PageSelection pageSelection = firstPage.selectContent(new SelectionBoundary(leftPoint),
                new SelectionBoundary(rightPoint), true);

        assertThat(pageSelection).isNull();

        firstPage.close();
        renderer.close();
    }


    @Test
    public void selectPageText_withOutRightBoundary_throwsException() throws Exception {
        assertSelectPageText_withoutRighttBoundary(createRenderer(SAMPLE_PDF, mContext));

        assertSelectPageText_withoutRighttBoundary(
                createRendererUsingNewConstructor(PROTECTED_PDF, mContext, LOAD_PARAMS));
    }

    private void assertSelectPageText_withoutRighttBoundary(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(1);

        Point point = new Point(157, 330);
        assertThrows(NullPointerException.class,
                () -> firstPage.selectContent(new SelectionBoundary(point), null, true));
    }

    @Test
    public void selectPageText_withOutStopBoundary_throwsException() throws Exception {
        assertSelectPageText_withoutLeftBoundary(createRenderer(SAMPLE_PDF, mContext));

        assertSelectPageText_withoutLeftBoundary(
                createRendererUsingNewConstructor(PROTECTED_PDF, mContext, LOAD_PARAMS));
    }

    private void assertSelectPageText_withoutLeftBoundary(PdfRenderer renderer) {
        PdfRenderer.Page firstPage = renderer.openPage(1);


        Point rightPoint = new Point(157, 330);
        assertThrows(NullPointerException.class,
                () -> firstPage.selectContent(null, new SelectionBoundary(rightPoint), true));
    }

    @Test
    public void getPageGotoLinks_pageWithoutGotoLink() throws Exception {
        assertPageGotoLinks_pageWithoutGotoLink(createRenderer(SAMPLE_PDF, mContext));

        assertPageGotoLinks_pageWithoutGotoLink(
                createRendererUsingNewConstructor(SAMPLE_PDF, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertPageGotoLinks_pageWithoutGotoLink(PdfRenderer renderer) {
        PdfRenderer.Page page = renderer.openPage(0);

        assertThat(page.getGotoLinks()).isEmpty();

        page.close();
        renderer.close();
    }

    @Test
    public void getPageGotoLinks_pageWithGotoLink() throws Exception {
        assertPageGotoLinks_pageWithGotoLink(createRenderer(R.raw.sample_links, mContext));

        assertPageGotoLinks_pageWithGotoLink(
                createRendererUsingNewConstructor(R.raw.sample_links, mContext,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR));
    }

    private void assertPageGotoLinks_pageWithGotoLink(PdfRenderer renderer) {
        PdfRenderer.Page page = renderer.openPage(0);

        assertThat(page.getGotoLinks().size()).isEqualTo(1);
        //assert destination
        PdfPageGotoLinkContent.Destination destination = page.getGotoLinks().get(
                0).getDestination();
        assertThat(destination.getPageNumber()).isEqualTo(1);
        assertThat(destination.getXCoordinate()).isEqualTo((float) 0.0);
        assertThat(destination.getYCoordinate()).isEqualTo((float) 85.0);
        assertThat(destination.getZoom()).isEqualTo((float) 0.0);

        //assert coordinates
        assertThat(page.getGotoLinks().get(0).getBounds()).hasSize(1);
        Rect rect = page.getGotoLinks().get(0).getBounds().get(0);
        assertThat(rect.left).isEqualTo(91);
        assertThat(rect.top).isEqualTo(246);
        assertThat(rect.right).isEqualTo(235);
        assertThat(rect.bottom).isEqualTo(262);

        page.close();
        renderer.close();
    }

    private void assertSamplePdf(PdfRenderer renderer, PdfRenderer expectedRenderer) {
        assertThat(renderer.getPageCount()).isEqualTo(expectedRenderer.getPageCount());

        PdfRenderer.Page firstPage = renderer.openPage(0);
        PdfRenderer.Page expectedFirstPage = expectedRenderer.openPage(0);
        assertSamplePdfPage(firstPage, expectedFirstPage);
        assertThat(firstPage.searchText("A Simple PDF file").size()).isEqualTo(
                firstPage.searchText("A Simple PDF file").size());
        assertThat(firstPage.searchText("A Simple PDF file").size()).isEqualTo(1);

        firstPage.close();
        expectedFirstPage.close();

        PdfRenderer.Page secondPage = renderer.openPage(1);
        PdfRenderer.Page expectedSecondPage = expectedRenderer.openPage(1);
        assertSamplePdfPage(secondPage, expectedSecondPage);
        assertThat(secondPage.searchText("Simple PDF file 2").size()).isEqualTo(
                secondPage.searchText("Simple PDF file 2").size());
        assertThat(secondPage.searchText("more").size()).isEqualTo(28);

        secondPage.close();
        expectedSecondPage.close();

        PdfRenderer.Page thirdPage = renderer.openPage(2);
        PdfRenderer.Page expectedThirdPage = expectedRenderer.openPage(2);
        assertSamplePdfPage(thirdPage, expectedThirdPage);
        assertThat(thirdPage.searchText("Simple PDF file 2").size()).isEqualTo(
                thirdPage.searchText("Simple PDF file 2").size());

        thirdPage.close();
        expectedThirdPage.close();
    }

    private void assertSamplePdfPage(PdfRenderer.Page page, PdfRenderer.Page expectedPage) {
        assertThat(page.getHeight()).isEqualTo(expectedPage.getHeight());
        assertThat(page.getWidth()).isEqualTo(expectedPage.getWidth());
    }

    private void assertPageSelection(PageSelection pageSelection, int expectedRectsSize,
            int expectedPageNumber) {
        assertThat(pageSelection.getPage()).isEqualTo(expectedPageNumber);
        assertThat(pageSelection.getTextSelections()).isNotEmpty();
        assertThat(pageSelection.getTextSelections().get(0).getSelectionBounds().size()).isEqualTo(
                expectedRectsSize);
    }

    private void assertRendererAfterClose(PdfRenderer renderer) {
        assertThrows(IllegalStateException.class, renderer::close);
        assertThrows(IllegalStateException.class, renderer::getPageCount);
        assertThrows(IllegalStateException.class, renderer::shouldScaleForPrinting);
        assertThrows(IllegalStateException.class, () -> renderer.write(null, true));
        assertThrows(IllegalStateException.class, () -> renderer.openPage(0));
    }

    private void assertOpenPage(PdfRenderer renderer) {
        assertThrows(IllegalArgumentException.class, () -> renderer.openPage(-1));
        Page page0 = renderer.openPage(0);
        page0.close();
        Page page1 = renderer.openPage(1);
        page1.close();
        assertThrows(IllegalArgumentException.class, () -> renderer.openPage(2));
    }

    private void assertPageSize(PdfRenderer renderer) {
        Page page = renderer.openPage(0);
        assertEquals(A4_HEIGHT_PTS, page.getHeight());
        assertEquals(A4_WIDTH_PTS, page.getWidth());
    }
}
