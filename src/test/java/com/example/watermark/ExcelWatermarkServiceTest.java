package com.example.watermark;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;

class ExcelWatermarkServiceTest {
    private final WatermarkRenderer renderer = new WatermarkRenderer();
    private final ExcelWatermarkService service = new ExcelWatermarkService(renderer);

    private WatermarkOptions options(WatermarkOptions.Type type, WatermarkOptions.Visibility visibility, List<Integer> sheets) {
        return new WatermarkOptions(type, visibility, "内部验证\nCONFIDENTIAL", "#426b5b", .2, 32, -30, "tile", "normal", sheets);
    }

    @ParameterizedTest
    @EnumSource(WatermarkOptions.Visibility.class)
    void configuresIndependentScreenAndPrintLayersAndPreservesCells(WatermarkOptions.Visibility visibility) throws Exception {
        byte[] result = service.apply(service.sample(), null, options(WatermarkOptions.Type.TEXT, visibility, List.of()));
        try (XSSFWorkbook workbook = service.open(result)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            for (var sheet : workbook) {
                var xssf = (org.apache.poi.xssf.usermodel.XSSFSheet) sheet;
                assertThat(xssf.getCTWorksheet().isSetPicture()).isEqualTo(visibility != WatermarkOptions.Visibility.PRINT);
                assertThat(xssf.getCTWorksheet().isSetLegacyDrawingHF()).isEqualTo(visibility != WatermarkOptions.Visibility.SCREEN);
                assertThat(xssf.getOddHeader().getCenter().contains("&G")).isEqualTo(visibility != WatermarkOptions.Visibility.SCREEN);
                assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("DEMO-001");
            }
            assertThat(workbook.getSheetAt(1).getRow(86).getCell(3).getCellFormula()).isEqualTo("SUM(D2:D86)");
            assertThat(workbook.getSheetAt(1).getRow(2).getCell(3).getNumericCellValue()).isEqualTo(241);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getFillForegroundColor()).isEqualTo((short) 31);
        }
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(result))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().endsWith(".vml")) {
                    var xml = factory.newDocumentBuilder().parse(new ByteArrayInputStream(zip.readAllBytes()));
                    assertThat(xml.getElementsByTagNameNS("urn:schemas-microsoft-com:vml", "imagedata").getLength()).isEqualTo(1);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(WatermarkOptions.Type.class)
    void supportsTextImageAndCombinedWatermarks(WatermarkOptions.Type type) throws Exception {
        byte[] output = service.apply(service.sample(), logo(), options(type, WatermarkOptions.Visibility.BOTH, List.of(0)));
        try (XSSFWorkbook workbook = service.open(output)) {
            assertThat(workbook.getSheetAt(0).getCTWorksheet().isSetPicture()).isTrue();
            assertThat(workbook.getSheetAt(1).getCTWorksheet().isSetPicture()).isFalse();
            assertThat(workbook.getSheetAt(1).getCTWorksheet().isSetLegacyDrawingHF()).isFalse();
            for (var picture : workbook.getAllPictures()) {
                var image = ImageIO.read(new ByteArrayInputStream(picture.getData()));
                assertThat(image.getColorModel().hasAlpha()).isTrue();
                boolean ink = false;
                for (int y = 0; y < image.getHeight() && !ink; y += 3) {
                    for (int x = 0; x < image.getWidth(); x += 3) {
                        int alpha = image.getRGB(x, y) >>> 24;
                        if (alpha > 0) { assertThat(alpha).isLessThanOrEqualTo(52); ink = true; break; }
                    }
                }
                assertThat(ink).isTrue();
            }
        }
    }

    @Test
    void canSwitchPreviouslyWatermarkedWorkbookToScreenOnly() throws Exception {
        byte[] first = service.apply(service.sample(), null, options(WatermarkOptions.Type.TEXT, WatermarkOptions.Visibility.BOTH, List.of()));
        byte[] next = service.apply(first, null, options(WatermarkOptions.Type.TEXT, WatermarkOptions.Visibility.SCREEN, List.of()));
        try (var workbook = service.open(next)) {
            assertThat(workbook.getSheetAt(0).getCTWorksheet().isSetPicture()).isTrue();
            assertThat(workbook.getSheetAt(0).getCTWorksheet().isSetLegacyDrawingHF()).isFalse();
            assertThat(workbook.getSheetAt(0).getOddHeader().getCenter()).doesNotContain("&G");
        }
    }

    @Test
    void rejectsMissingLogoCorruptedWorkbooksAndUnknownSheets() throws Exception {
        assertThatThrownBy(() -> service.apply(service.sample(), null, options(WatermarkOptions.Type.IMAGE, WatermarkOptions.Visibility.BOTH, List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("需要上传");
        assertThatThrownBy(() -> service.open("invalid".getBytes())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(service.sample(), null, options(WatermarkOptions.Type.TEXT, WatermarkOptions.Visibility.BOTH, List.of(99))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("工作表不存在");
        assertThatThrownBy(() -> renderer.readLogo("invalid".getBytes())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsCenteredLayoutAndLandscapeWithoutChangingPageOrientation() throws Exception {
        byte[] source;
        try (var workbook = service.open(service.sample()); var output = new ByteArrayOutputStream()) {
            workbook.getSheetAt(0).getPrintSetup().setLandscape(true);
            workbook.getSheetAt(0).getOddHeader().setCenter("已有页眉");
            workbook.write(output);
            source = output.toByteArray();
        }
        var settings = new WatermarkOptions(WatermarkOptions.Type.BOTH, WatermarkOptions.Visibility.PRINT, "单个标记",
                "#b44c4c", .1, 50, 45, "single", "sparse", List.of(0));
        try (var workbook = service.open(service.apply(source, logo(), settings))) {
            assertThat(workbook.getSheetAt(0).getPrintSetup().getLandscape()).isTrue();
            assertThat(workbook.getSheetAt(0).getOddHeader().getCenter()).isEqualTo("已有页眉&G");
        }
    }

    private byte[] logo() throws IOException {
        BufferedImage image = new BufferedImage(100, 40, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.BLUE);
        graphics.fillRect(5, 5, 90, 30);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
