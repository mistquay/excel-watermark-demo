package com.example.watermark;

import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ExcelWatermarkService {
    private static final String IMAGE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
    private static final String VML_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/vmlDrawing";
    private final WatermarkRenderer renderer;

    public ExcelWatermarkService(WatermarkRenderer renderer) {
        this.renderer = renderer;
    }

    public byte[] apply(byte[] source, byte[] logoBytes, WatermarkOptions options) throws Exception {
        BufferedImage logo = renderer.readLogo(logoBytes);
        try (XSSFWorkbook workbook = open(source); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index : options.sheets()) {
                if (index < 0 || index >= workbook.getNumberOfSheets()) throw new IllegalArgumentException("选择的工作表不存在");
            }
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                if (!options.sheets().isEmpty() && !options.sheets().contains(index)) continue;
                XSSFSheet sheet = workbook.getSheetAt(index);
                if (sheet.getCTWorksheet().isSetPicture()) sheet.getCTWorksheet().unsetPicture();
                removeDemoPrintWatermark(sheet);
                if (options.screen()) addScreenWatermark(workbook, sheet, logo, options);
                if (options.print()) addPrintWatermark(workbook, sheet, logo, options);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void addScreenWatermark(XSSFWorkbook workbook, XSSFSheet sheet, BufferedImage logo,
                                    WatermarkOptions options) throws IOException {
        byte[] png = renderer.render(options, logo, 1200, 850);
        XSSFPictureData picture = workbook.getAllPictures().get(workbook.addPicture(png, Workbook.PICTURE_TYPE_PNG));
        var relation = sheet.addRelation(null, XSSFRelation.IMAGES, picture);
        sheet.getCTWorksheet().addNewPicture().setId(relation.getRelationship().getId());
    }

    private void addPrintWatermark(XSSFWorkbook workbook, XSSFSheet sheet, BufferedImage logo,
                                   WatermarkOptions options) throws Exception {
        double[] page = pageSize(sheet);
        double width = Math.max(144, page[0] - (sheet.getMargin(Sheet.LeftMargin) + sheet.getMargin(Sheet.RightMargin)) * 72);
        double height = Math.max(144, page[1] - (sheet.getMargin(Sheet.TopMargin) + sheet.getMargin(Sheet.BottomMargin)) * 72);
        byte[] png = renderer.render(options, logo,
                (int) Math.round(width * WatermarkRenderer.PIXELS_PER_POINT),
                (int) Math.round(height * WatermarkRenderer.PIXELS_PER_POINT));
        XSSFPictureData picture = workbook.getAllPictures().get(workbook.addPicture(png, Workbook.PICTURE_TYPE_PNG));

        PackagePartName vmlName = PackagingURIHelper.createPartName("/xl/drawings/watermark-header-" + UUID.randomUUID() + ".vml");
        PackagePart vml = workbook.getPackage().createPart(vmlName, "application/vnd.openxmlformats-officedocument.vmlDrawing");
        var imageRelation = vml.addRelationship(picture.getPackagePart().getPartName(), TargetMode.INTERNAL, IMAGE_REL);
        double offset = Math.max(0, (sheet.getMargin(Sheet.TopMargin) - sheet.getMargin(Sheet.HeaderMargin)) * 72);
        try (OutputStream output = vml.getOutputStream()) {
            output.write(vml(width, height, offset, imageRelation.getId()).getBytes(StandardCharsets.UTF_8));
        }

        // 页眉图片是 Excel 的打印水印机制；统一普通页眉，保证每页均生效。
        if (sheet.getCTWorksheet().isSetLegacyDrawingHF()) sheet.getCTWorksheet().unsetLegacyDrawingHF();
        var relation = sheet.getPackagePart().addRelationship(vmlName, TargetMode.INTERNAL, VML_REL);
        sheet.getCTWorksheet().addNewLegacyDrawingHF().setId(relation.getId());
        sheet.getOddHeader().setCenter(sheet.getOddHeader().getCenter().replace("&G", "") + "&G");
        var headerFooter = sheet.getCTWorksheet().getHeaderFooter();
        headerFooter.setDifferentFirst(false);
        headerFooter.setDifferentOddEven(false);
        headerFooter.setScaleWithDoc(false);
        headerFooter.setAlignWithMargins(true);
    }

    private void removeDemoPrintWatermark(XSSFSheet sheet) throws Exception {
        if (!sheet.getCTWorksheet().isSetLegacyDrawingHF()) return;
        String id = sheet.getCTWorksheet().getLegacyDrawingHF().getId();
        var relation = sheet.getPackagePart().getRelationship(id);
        if (relation != null && relation.getTargetURI().toString().contains("watermark-header-")) {
            sheet.getCTWorksheet().unsetLegacyDrawingHF();
            sheet.getPackagePart().removeRelationship(id);
            sheet.getOddHeader().setCenter(sheet.getOddHeader().getCenter().replace("&G", ""));
        }
    }

    private double[] pageSize(XSSFSheet sheet) {
        double[] size = switch (sheet.getPrintSetup().getPaperSize()) {
            case PrintSetup.A3_PAPERSIZE -> new double[]{841.89, 1190.55};
            case PrintSetup.LETTER_PAPERSIZE -> new double[]{612, 792};
            case PrintSetup.LEGAL_PAPERSIZE -> new double[]{612, 1008};
            default -> new double[]{595.28, 841.89};
        };
        return sheet.getPrintSetup().getLandscape() ? new double[]{size[1], size[0]} : size;
    }

    private String vml(double width, double height, double top, String relationId) {
        return """
                <xml xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office"
                     xmlns:x="urn:schemas-microsoft-com:office:excel">
                  <o:shapelayout v:ext="edit"><o:idmap v:ext="edit" data="1"/></o:shapelayout>
                  <v:shapetype id="_x0000_t75" coordsize="21600,21600" o:spt="75" o:preferrelative="t"
                              path="m@4@5l@4@11@9@11@9@5xe" filled="f" stroked="f">
                    <v:stroke joinstyle="miter"/>
                    <v:formulas>
                      <v:f eqn="if lineDrawn pixelLineWidth 0"/><v:f eqn="sum @0 1 0"/>
                      <v:f eqn="sum 0 0 @1"/><v:f eqn="prod @2 1 2"/><v:f eqn="prod @3 21600 pixelWidth"/>
                      <v:f eqn="prod @3 21600 pixelHeight"/><v:f eqn="sum @0 0 1"/><v:f eqn="prod @6 1 2"/>
                      <v:f eqn="prod @7 21600 pixelWidth"/><v:f eqn="sum @8 21600 0"/>
                      <v:f eqn="prod @7 21600 pixelHeight"/><v:f eqn="sum @10 21600 0"/>
                    </v:formulas>
                    <v:path o:extrusionok="f" gradientshapeok="t" o:connecttype="rect"/>
                    <o:lock v:ext="edit" aspectratio="t"/>
                  </v:shapetype>
                  <v:shape id="CH" o:spid="_x0000_s1025" type="#_x0000_t75"
                           style="position:absolute;margin-left:0;margin-top:%spt;width:%spt;height:%spt;z-index:1">
                    <v:imagedata o:relid="%s" o:title="Excel watermark"/>
                    <o:lock v:ext="edit" rotation="t"/>
                  </v:shape>
                </xml>
                """.formatted(number(top), number(width), number(height), relationId);
    }

    private String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    public XSSFWorkbook open(byte[] source) throws IOException {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(source));
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法读取文件，请上传未加密、未损坏的 .xlsx 工作簿", exception);
        }
    }

    public byte[] sample() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            header.setFont(font);
            for (int sheetIndex = 0; sheetIndex < 2; sheetIndex++) {
                XSSFSheet sheet = workbook.createSheet(sheetIndex == 0 ? "设备巡检记录" : "月度费用明细");
                String[] titles = sheetIndex == 0 ? new String[]{"序号", "设备编码", "站点名称", "巡检结果", "负责人", "备注"}
                        : new String[]{"序号", "项目编码", "项目名称", "金额（元）", "负责人", "备注"};
                Row title = sheet.createRow(0);
                title.setHeightInPoints(30);
                for (int col = 0; col < titles.length; col++) {
                    Cell cell = title.createCell(col);
                    cell.setCellValue(titles[col]);
                    cell.setCellStyle(header);
                    sheet.setColumnWidth(col, (col == 0 ? 7 : col == 2 ? 22 : 16) * 256);
                }
                for (int rowIndex = 1; rowIndex <= 85; rowIndex++) {
                    Row row = sheet.createRow(rowIndex);
                    row.setHeightInPoints(25);
                    row.createCell(0).setCellValue(rowIndex);
                    row.createCell(1).setCellValue("DEMO-%03d".formatted(rowIndex));
                    row.createCell(2).setCellValue((sheetIndex == 0 ? "演示站点 " : "演示项目 ") + rowIndex);
                    if (sheetIndex == 0) row.createCell(3).setCellValue(rowIndex % 7 == 0 ? "待复查" : "正常");
                    else row.createCell(3).setCellValue(rowIndex * 120.5);
                    row.createCell(4).setCellValue("演示人员");
                    row.createCell(5).setCellValue("合成示例数据");
                }
                if (sheetIndex == 1) sheet.createRow(86).createCell(3).setCellFormula("SUM(D2:D86)");
                sheet.createFreezePane(0, 1);
                sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
                sheet.getPrintSetup().setFitWidth((short) 1);
                sheet.getPrintSetup().setFitHeight((short) 0);
                sheet.setFitToPage(true);
                sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(0, 0, -1, -1));
                workbook.setPrintArea(sheetIndex, 0, 5, 0, sheetIndex == 0 ? 85 : 86);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
