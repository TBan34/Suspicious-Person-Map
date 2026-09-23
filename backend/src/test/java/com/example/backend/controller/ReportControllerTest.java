package com.example.backend.controller;

import com.example.backend.entity.ReportEntity;
import com.example.backend.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportControllerTest {

    /**
     * 不審者情報の取得結果に、保存されているLINEユーザーIDが含まれないことを確認する。
     */
    @Test
    void getReportsDoesNotExposeLineUserId() throws Exception {
        ReportEntity report = new ReportEntity();
        report.setId(1L);
        report.setUserId("line-user-id");
        report.setSummary("不審者情報");
        ReportRepository reportRepository = (ReportRepository) Proxy.newProxyInstance(
                ReportRepository.class.getClassLoader(),
                new Class<?>[]{ReportRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAll")) {
                        return List.of(report);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );

        ReportController controller = new ReportController(reportRepository);
        ObjectMapper objectMapper = new ObjectMapper();
        String responseBody = objectMapper.writeValueAsString(controller.getReports());
        JsonNode response = objectMapper.readTree(responseBody);

        assertThat(response.get(0).has("userId")).isFalse();
        assertThat(response.get(0).get("summary").asText()).isEqualTo("不審者情報");
    }
}
