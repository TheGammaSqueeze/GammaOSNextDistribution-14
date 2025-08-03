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

import static android.graphics.pdf.PdfRendererPreV.DOCUMENT_LINEARIZED_TYPE_LINEARIZED;
import static android.graphics.pdf.PdfRendererPreV.DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED;
import static android.graphics.pdf.cts.Utils.A4_HEIGHT_PTS;
import static android.graphics.pdf.cts.Utils.A4_PORTRAIT;
import static android.graphics.pdf.cts.Utils.A4_WIDTH_PTS;
import static android.graphics.pdf.cts.Utils.INCORRECT_LOAD_PARAMS;
import static android.graphics.pdf.cts.Utils.LOAD_PARAMS;
import static android.graphics.pdf.cts.Utils.PROTECTED_PDF;
import static android.graphics.pdf.cts.Utils.SAMPLE_PDF;
import static android.graphics.pdf.cts.Utils.calculateArea;
import static android.graphics.pdf.cts.Utils.createPreVRenderer;
import static android.graphics.pdf.cts.Utils.getFile;
import static android.graphics.pdf.cts.Utils.getParcelFileDescriptorFromResourceId;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.graphics.pdf.content.PdfPageGotoLinkContent;
import android.graphics.pdf.models.PageMatchBounds;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PdfRendererPreVTest {

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void constructRenderer_fromNull_throwsException() throws Exception {
        assertThrows(NullPointerException.class, () -> new PdfRendererPreV(null, LOAD_PARAMS));
    }

    @Test
    public void constructRenderer_fromNullWithoutLoadParams_throwsException() throws Exception {
        assertThrows(NullPointerException.class, () -> new PdfRendererPreV(null));
    }

    @Test
    public void constructRenderer_withoutLoadParams_throwsException() throws Exception {
        assertThrows(NullPointerException.class, () -> new PdfRendererPreV(
                getParcelFileDescriptorFromResourceId(SAMPLE_PDF, mContext), null));
    }

    @Test
    public void constructRenderer_fromNonPDF_throwsException() throws Exception {
        assertThrows(IOException.class,
                () -> createPreVRenderer(R.raw.testimage, mContext, LOAD_PARAMS));
    }

    @Test
    public void constructRenderer_protectedPdfWithWrongPassword_throwsException() throws Exception {
        assertThrows(SecurityException.class,
                () -> createPreVRenderer(PROTECTED_PDF, mContext, INCORRECT_LOAD_PARAMS));
    }

    @Test
    public void rendererRecoversAfterFailure() throws Exception {
        assertThrows(SecurityException.class,
                () -> createPreVRenderer(PROTECTED_PDF, mContext, INCORRECT_LOAD_PARAMS));
        // We can create new renderers after we failed to create one
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        renderer.close();
    }

    @Test
    public void useRenderer_afterRendererClose_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        renderer.close();

        assertThrows(IllegalStateException.class, renderer::close);
        assertThrows(IllegalStateException.class, renderer::getPageCount);
        assertThrows(IllegalStateException.class, renderer::getDocumentLinearizationType);
        assertThrows(IllegalStateException.class, () -> renderer.write(null, true));
        assertThrows(IllegalStateException.class, () -> renderer.openPage(0));
    }

    @Test
    public void usePage_afterRendererClose_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page page = renderer.openPage(0);
        renderer.close();

        assertPageException(page);
    }

    @Test
    public void usePage_afterPageClose_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page page = renderer.openPage(0);
        page.close();

        assertPageException(page);
        renderer.close();
    }

    @Test
    public void useRenderer_afterPageClose() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page page = renderer.openPage(0);
        page.close();

        assertThat(renderer.openPage(0)).isNotNull();
        renderer.close();
    }

    @Test
    public void getPdfPageCount() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        assertThat(renderer.getPageCount()).isEqualTo(3);
        renderer.close();
    }

    @Test
    public void getDocumentType_withNonLinearizedPdf() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        assertThat(renderer.getDocumentLinearizationType()).isEqualTo(
                DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED);
        renderer.close();
    }

    @Test
    public void getDocumentType_withLinearizedPdf() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        assertThat(renderer.getDocumentLinearizationType()).isEqualTo(
                DOCUMENT_LINEARIZED_TYPE_LINEARIZED);
        renderer.close();
    }

    @Test
    public void getPageDimension() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(A4_PORTRAIT, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(0);

        assertThat(page.getWidth()).isEqualTo(A4_WIDTH_PTS);
        assertThat(page.getHeight()).isEqualTo(A4_HEIGHT_PTS);

        page.close();
        renderer.close();
    }

    @Test
    public void getPdfPageTextContents_pdfWithText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getTextContents().size()).isEqualTo(1);
        assertThat(firstPage.getTextContents().get(0).getText()).isEqualTo(
                "A Simple PDF File, which will be used for testing.");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getOpenPage_pageOutOfBounds_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);

        assertThrows(IllegalArgumentException.class, () -> renderer.openPage(-1));
        PdfRendererPreV.Page page0 = renderer.openPage(0);
        page0.close();
        PdfRendererPreV.Page page1 = renderer.openPage(1);
        page1.close();
        assertThrows(IllegalArgumentException.class, () -> renderer.openPage(3));
    }

    @Test
    public void getPageIndex() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(2);

        assertThat(page.getIndex()).isEqualTo(2);

        page.close();
        renderer.close();

    }

    @Test
    public void getPdfPageTextContents_pdfWithTextAndImages() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.alt_text, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

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
    public void getPdfPageImageContents_pdfWithText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getImageContents().size()).isEqualTo(0);

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPdfPageImageContents_pdfWithAltText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.alt_text, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getImageContents().size()).isEqualTo(1);
        assertThat(firstPage.getImageContents().get(0).getAltText()).isEqualTo(
                "Social Security Administration Logo");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPageLinks_pdfWithoutLink() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.sample_links, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        assertThat(firstPage.getLinkContents().size()).isEqualTo(0);

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPageLinks_pdfWithLink() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.sample_links, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

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
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);
        int firstPageWidth = firstPage.getWidth();
        int firstPageHeight = firstPage.getHeight();
        // Second page
        PdfRendererPreV.Page secondPage = renderer.openPage(1);
        int secondPageWidth = secondPage.getWidth();
        int secondPageHeight = secondPage.getHeight();
        // Third page,
        PdfRendererPreV.Page thirdPage = renderer.openPage(2);

        // First query, First page only contains single "simple"
        List<PageMatchBounds> firstPageRects = firstPage.searchText("simple");

        assertThat(firstPageRects.size()).isEqualTo(1);
        assertThat(firstPageRects.get(0).getBounds().size()).isEqualTo(1);
        // the rects area should be less than the page area
        assertThat(calculateArea(firstPageRects)).isLessThan(firstPageHeight * firstPageWidth);
        firstPage.close();

        // second query
        List<PageMatchBounds> secondPageRects = secondPage.searchText("more");

        assertThat(secondPageRects.size()).isEqualTo(28);
        // assert that size of all rects are 1 as more does not extend to other lines
        for (PageMatchBounds rect : secondPageRects) {
            assertThat(rect.getBounds().size()).isEqualTo(1);
        }
        // the rects area should be less than the page area
        assertThat(calculateArea(secondPageRects)).isLessThan(secondPageHeight * secondPageWidth);

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
    public void searchPageText_querryTextInMultipleLines() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page openPage = renderer.openPage(1);
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
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.searchText("z").isEmpty()).isTrue();

        firstPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText_withNullQuerry_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page openPage = renderer.openPage(1);

        assertThrows(NullPointerException.class, () -> openPage.searchText(null));
    }

    @Test
    public void write_withNullDest_throwsException() throws Exception {
        PdfRendererPreV expectedRenderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        assertThrows(NullPointerException.class, () -> expectedRenderer.write(null, true));
    }

    //TODO: Update the test to assert pdfs.
    @Test
    public void write_protectedPdf_withSecurity() throws Exception {
        PdfRendererPreV expectedRenderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
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
        PdfRendererPreV renderer = new PdfRendererPreV(saveAsDescp, LOAD_PARAMS);
        assertThat(renderer).isNotNull();

        renderer.close();
    }

    @Test
    public void write_withUnprotected() throws Exception {
        PdfRendererPreV expectedRenderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        File filePath = getFile(mContext, "unprotected.pdf");
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
        PdfRendererPreV renderer = new PdfRendererPreV(saveAsDescp);
        assertSamplePdf(renderer, expectedRenderer);
    }

    @Test
    public void selectPageText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

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
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

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
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

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
    public void selectPageText_emptySpace() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point leftPoint = new Point(157, 330);
        Point rightPoint = new Point(157, 330);
        PageSelection pageSelection = firstPage.selectContent(new SelectionBoundary(leftPoint),
                new SelectionBoundary(rightPoint), true);

        assertThat(pageSelection).isNull();

        firstPage.close();
        renderer.close();
    }

    @Test
    public void selectPageText_withoutLeftBoundary_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point rightPoint = new Point(157, 330);
        assertThrows(NullPointerException.class,
                () -> firstPage.selectContent(null, new SelectionBoundary(rightPoint), true));

    }

    @Test
    public void selectPageText_withoutRightBoundary_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point point = new Point(157, 330);
        assertThrows(NullPointerException.class,
                () -> firstPage.selectContent(new SelectionBoundary(point), null, true));
    }

    @Test
    public void selectPageText_withNegativeIndex_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        assertThrows(IllegalArgumentException.class,
                () -> firstPage.selectContent(new SelectionBoundary(-1), null, true));
    }

    @Test
    public void renderPage_withNullParams_throwsException() throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(A4_PORTRAIT, mContext, null);
             PdfRendererPreV.Page page = renderer.openPage(0)) {
            assertThrows(NullPointerException.class, () -> page.render(
                    Bitmap.createBitmap(A4_WIDTH_PTS, A4_HEIGHT_PTS, Bitmap.Config.ARGB_8888), null,
                    null, null));
        }
    }

    @Test
    public void getPageGotoLinks_pageWithoutGotoLink() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(0);

        assertThat(page.getGotoLinks()).isEmpty();

        page.close();
        renderer.close();
    }

    @Test
    public void getPageGotoLinks_pageWithGotoLink() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.sample_links, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(0);

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

    private void assertSamplePdf(PdfRendererPreV renderer, PdfRendererPreV expectedRenderer) {
        assertThat(renderer.getDocumentLinearizationType()).isEqualTo(
                expectedRenderer.getDocumentLinearizationType());
        assertThat(renderer.getPageCount()).isEqualTo(expectedRenderer.getPageCount());

        PdfRendererPreV.Page firstPage = renderer.openPage(0);
        PdfRendererPreV.Page expectedFirstPage = expectedRenderer.openPage(0);
        assertSamplePdfPage(firstPage, expectedFirstPage);
        assertThat(firstPage.searchText("A Simple PDF file").size()).isEqualTo(
                firstPage.searchText("A Simple PDF file").size());
        assertThat(firstPage.searchText("A Simple PDF file").size()).isEqualTo(1);

        firstPage.close();
        expectedFirstPage.close();

        PdfRendererPreV.Page secondPage = renderer.openPage(1);
        PdfRendererPreV.Page expectedSecondPage = expectedRenderer.openPage(1);
        assertSamplePdfPage(secondPage, expectedSecondPage);
        assertThat(secondPage.searchText("Simple PDF file 2").size()).isEqualTo(
                secondPage.searchText("Simple PDF file 2").size());
        assertThat(secondPage.searchText("more").size()).isEqualTo(28);

        secondPage.close();
        expectedSecondPage.close();

        PdfRendererPreV.Page thirdPage = renderer.openPage(2);
        PdfRendererPreV.Page expectedThirdPage = expectedRenderer.openPage(2);
        assertSamplePdfPage(thirdPage, expectedThirdPage);
        assertThat(thirdPage.searchText("Simple PDF file 2").size()).isEqualTo(
                thirdPage.searchText("Simple PDF file 2").size());

        thirdPage.close();
        expectedThirdPage.close();
    }

    private void assertSamplePdfPage(PdfRendererPreV.Page page, PdfRendererPreV.Page expectedPage) {
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

    private void assertPageException(PdfRendererPreV.Page page) {
        Point leftPoint = new Point(275, 163);
        Point rightPoint = new Point(65, 125);

        assertThrows(IllegalStateException.class, page::close);
        assertThrows(IllegalStateException.class, page::getHeight);
        assertThrows(IllegalStateException.class, page::getWidth);
        assertThrows(IllegalStateException.class, page::getGotoLinks);
        assertThrows(IllegalStateException.class, page::getLinkContents);
        assertThrows(IllegalStateException.class, page::getTextContents);
        assertThrows(IllegalStateException.class, page::getImageContents);
        assertThrows(IllegalStateException.class, () -> page.searchText("more"));
        assertThrows(IllegalStateException.class,
                () -> page.selectContent(new SelectionBoundary(leftPoint),
                        new SelectionBoundary(rightPoint), false));
        assertThrows(IllegalStateException.class, () -> page.render(
                Bitmap.createBitmap(A4_WIDTH_PTS, A4_HEIGHT_PTS, Bitmap.Config.ARGB_8888), null,
                null, new RenderParams.Builder(1).build()));
    }
}
