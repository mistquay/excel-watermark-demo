package com.example.watermark;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class WatermarkController {
    private final ExcelWatermarkService service;
    private final WatermarkRenderer renderer;

    public WatermarkController(ExcelWatermarkService service, WatermarkRenderer renderer) {
        this.service = service;
        this.renderer = renderer;
    }

    @GetMapping("/sample")
    public ResponseEntity<byte[]> sample() throws Exception {
        return download(service.sample(), "水印演示数据.xlsx");
    }

    @PostMapping("/inspect")
    public Map<String, Object> inspect(@RequestParam MultipartFile file) throws Exception {
        validateFile(file);
        try (var workbook = service.open(file.getBytes())) {
            List<Map<String, Object>> sheets = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            formatter.setUseCachedValuesForFormulaCells(true);
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                var sheet = workbook.getSheetAt(index);
                List<List<String>> rows = new ArrayList<>();
                for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 11); rowIndex++) {
                    var row = sheet.getRow(rowIndex);
                    List<String> cells = new ArrayList<>();
                    for (int col = 0; col < 8; col++) cells.add(row == null ? "" : formatter.formatCellValue(row.getCell(col)));
                    rows.add(cells);
                }
                sheets.add(Map.of("index", index, "name", sheet.getSheetName(), "rows", rows,
                        "rowCount", sheet.getPhysicalNumberOfRows()));
            }
            return Map.of("sheets", sheets, "fileName", Objects.requireNonNullElse(file.getOriginalFilename(), "工作簿.xlsx"));
        }
    }

    @PostMapping(value = "/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] preview(@RequestParam Map<String, String> fields,
                          @RequestParam(required = false) MultipartFile logo) throws Exception {
        return renderer.render(options(fields, null), renderer.readLogo(bytes(logo)), 1200, 850);
    }

    @PostMapping("/watermark")
    public ResponseEntity<byte[]> watermark(@RequestParam MultipartFile file,
                                            @RequestParam(required = false) MultipartFile logo,
                                            @RequestParam Map<String, String> fields,
                                            @RequestParam(required = false) List<Integer> sheets) throws Exception {
        validateFile(file);
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "工作簿.xlsx");
        String safeName = original.replaceAll("[\\\\/\\r\\n]", "_");
        return download(service.apply(file.getBytes(), bytes(logo), options(fields, sheets)),
                safeName.substring(0, safeName.length() - 5) + "_水印.xlsx");
    }

    private WatermarkOptions options(Map<String, String> fields, List<Integer> sheets) {
        try {
            return new WatermarkOptions(
                    WatermarkOptions.Type.valueOf(fields.getOrDefault("type", "TEXT")),
                    WatermarkOptions.Visibility.valueOf(fields.getOrDefault("visibility", "BOTH")),
                    fields.getOrDefault("text", "内部资料 · 仅供演示"), fields.getOrDefault("color", "#426b5b"),
                    Double.parseDouble(fields.getOrDefault("opacity", "0.18")),
                    Integer.parseInt(fields.getOrDefault("fontSize", "32")),
                    Integer.parseInt(fields.getOrDefault("angle", "-30")),
                    fields.getOrDefault("layout", "tile"), fields.getOrDefault("density", "normal"), sheets);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("字号、角度或透明度格式不正确", exception);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("请选择 .xlsx 文件；旧版 .xls 请先另存为 .xlsx");
        }
    }

    private byte[] bytes(MultipartFile file) throws Exception {
        return file == null || file.isEmpty() ? null : file.getBytes();
    }

    private ResponseEntity<byte[]> download(byte[] bytes, String name) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    @RestControllerAdvice
    public static class Errors {
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<Map<String, String>> tooLarge() {
            return ResponseEntity.status(413).body(Map.of("message", "Excel 不能超过 20MB，请缩小文件后重试"));
        }
    }
}
