package com.example.watermark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WatermarkControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ExcelWatermarkService service;

    @Test
    void servesPageAndSampleWorkbook() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/sample")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void inspectsActualWorkbookAndDownloadsConfiguredResult() throws Exception {
        var file = new MockMultipartFile("file", "示例.xlsx", "application/octet-stream", service.sample());
        mvc.perform(multipart("/api/inspect").file(file)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sheets.length()").value(2))
                .andExpect(jsonPath("$.sheets[0].rows[1][1]").value("DEMO-001"));
        mvc.perform(multipart("/api/watermark").file(file).param("visibility", "PRINT").param("sheets", "0"))
                .andExpect(status().isOk()).andExpect(header().exists("Content-Disposition"));
        mvc.perform(multipart("/api/preview").param("text", "验证水印"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
    }

    @Test
    void returnsUsefulErrorsForUnsupportedFilesAndOptions() throws Exception {
        mvc.perform(multipart("/api/inspect").file(new MockMultipartFile("file", "旧版.xls", null, new byte[]{1})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(".xlsx")));
        mvc.perform(multipart("/api/preview").param("opacity", "NaN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("透明度")));
        mvc.perform(multipart("/api/preview").param("type", "IMAGE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("需要上传")));
    }
}
