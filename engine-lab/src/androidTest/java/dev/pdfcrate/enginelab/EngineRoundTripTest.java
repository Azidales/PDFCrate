package dev.pdfcrate.enginelab;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Candidate evaluation only. Passing does not approve the engine for production. */
@RunWith(AndroidJUnit4.class)
public class EngineRoundTripTest {
    @Rule public final TemporaryFolder temp = new TemporaryFolder();

    @Before public void initialize() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PDFBoxResourceLoader.init(context);
    }

    @Test public void textAnnotationAndFormSurviveSaveAndReopen() throws Exception {
        File source = temp.newFile("source.pdf");
        File edited = temp.newFile("edited.pdf");
        createFixture(source, 1);
        byte[] before = readBytes(source);

        try (PDDocument document = PDDocument.load(source)) {
            assertTrue(new PDFTextStripper().getText(document).contains("PDFCrate page 1"));
            PDTextField field = (PDTextField) document.getDocumentCatalog()
                    .getAcroForm().getField("reader");
            field.setValue("PDFCrate reader");
            PDAnnotationTextMarkup highlight = new PDAnnotationTextMarkup(
                    PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT);
            highlight.setRectangle(new PDRectangle(50, 695, 200, 25));
            highlight.setQuadPoints(new float[] {50, 720, 250, 720, 50, 695, 250, 695});
            highlight.setContents("Round-trip annotation");
            document.getPage(0).getAnnotations().add(highlight);
            document.save(edited);
        }

        try (PDDocument reopened = PDDocument.load(edited)) {
            assertEquals(1, reopened.getNumberOfPages());
            assertEquals("PDFCrate reader", reopened.getDocumentCatalog()
                    .getAcroForm().getField("reader").getValueAsString());
            assertTrue(new PDFTextStripper().getText(reopened).contains("PDFCrate page 1"));
            assertTrue(reopened.getPage(0).getAnnotations().stream().anyMatch(
                    annotation -> "Round-trip annotation".equals(annotation.getContents())));
        }
        assertArrayEquals("Save-copy must preserve the source", before, readBytes(source));
        assertRendersWithAndroid(edited, 1, 0);
    }

    @Test public void encryptedDocumentRejectsWrongPasswordAndAcceptsCorrectOne() throws Exception {
        File file = temp.newFile("protected.pdf");
        createFixture(file, 1);
        File protectedFile = temp.newFile("protected-output.pdf");
        try (PDDocument document = PDDocument.load(file)) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "fixture-owner", "fixture-reader", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            policy.setPreferAES(true);
            document.protect(policy);
            document.save(protectedFile);
        }
        try (PDDocument ignored = PDDocument.load(protectedFile, "wrong")) {
            fail("Wrong password was accepted");
        } catch (InvalidPasswordException expected) {
            // The future UI must map this to a retryable password prompt.
        }
        try (PDDocument document = PDDocument.load(protectedFile, "fixture-reader")) {
            assertTrue(document.isEncrypted());
            assertTrue(new PDFTextStripper().getText(document).contains("PDFCrate page 1"));
        }
    }

    @Test public void invalidInputFailsWithIOException() throws Exception {
        File file = temp.newFile("invalid.pdf");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write("Not a PDF".getBytes(StandardCharsets.UTF_8));
        }
        try (PDDocument ignored = PDDocument.load(file)) {
            fail("Invalid file was accepted");
        } catch (IOException expected) {
            // The future UI must report an invalid document without crashing.
        }
    }

    @Test public void thousandPageFixtureCanOpenAndRenderItsLastPage() throws Exception {
        File file = temp.newFile("thousand-pages.pdf");
        createFixture(file, 1000);
        try (PDDocument document = PDDocument.load(file)) {
            assertEquals(1000, document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1000);
            stripper.setEndPage(1000);
            assertTrue(stripper.getText(document).contains("PDFCrate page 1000"));
        }
        assertRendersWithAndroid(file, 1000, 999);
        // This is a functional smoke test, not a performance budget or benchmark.
    }

    private void createFixture(File target, int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int index = 1; index <= pages; index++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA, 18);
                    content.newLineAtOffset(50, 700);
                    content.showText("PDFCrate page " + index);
                    content.endText();
                }
            }
            PDAcroForm form = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(form);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA);
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/Helv 12 Tf 0 g");
            PDTextField field = new PDTextField(form);
            field.setPartialName("reader");
            form.getFields().add(field);
            field.getWidgets().get(0).setRectangle(new PDRectangle(50, 600, 250, 30));
            field.getWidgets().get(0).setPage(document.getPage(0));
            document.getPage(0).getAnnotations().add(field.getWidgets().get(0));
            field.setValue("Initial value");
            document.save(target);
        }
    }

    private void assertRendersWithAndroid(File file, int expectedPages, int index) throws Exception {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            assertEquals(expectedPages, renderer.getPageCount());
            try (PdfRenderer.Page page = renderer.openPage(index)) {
                Bitmap bitmap = Bitmap.createBitmap(600, 850, Bitmap.Config.ARGB_8888);
                try {
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                    bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0,
                            bitmap.getWidth(), bitmap.getHeight());
                    int darkPixels = 0;
                    for (int pixel : pixels) {
                        if (Color.alpha(pixel) > 0 && Color.red(pixel) < 128
                                && Color.green(pixel) < 128 && Color.blue(pixel) < 128) {
                            darkPixels++;
                        }
                    }
                    assertTrue("Independent renderer returned a blank page", darkPixels > 50);
                } finally {
                    bitmap.recycle();
                }
            }
        }
    }

    private byte[] readBytes(File file) throws IOException {
        return java.nio.file.Files.readAllBytes(file.toPath());
    }
}
