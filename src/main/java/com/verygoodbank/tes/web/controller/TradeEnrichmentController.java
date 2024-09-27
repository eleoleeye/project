package com.verygoodbank.tes.web.controller;

import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1")
public class TradeEnrichmentController {

    private final TradeEnrichmentService tradeEnrichmentService;

    @PostMapping(value = "/enrich")
    public ResponseEntity<StreamingResponseBody> enrichTrades(InputStream inputStream) {
        StreamingResponseBody responseBody = outputStream -> {
            try {
                tradeEnrichmentService.enrichTrades(inputStream, outputStream);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread was interrupted during trade enrichment", e);
                throw new RuntimeException("Thread was interrupted during trade enrichment", e);
            } catch (Exception e) {
                log.error("Error enriching trades", e);
                throw new RuntimeException("Error enriching trades", e);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"enriched_trades.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(responseBody);
    }
}


