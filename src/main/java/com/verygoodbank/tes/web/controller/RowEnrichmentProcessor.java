package com.verygoodbank.tes.web.controller;


import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RowEnrichmentProcessor {

    private static final String MISSING_PRODUCT_NAME = "Missing Product Name";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductRegistry productRegistry;

    public String processTradeLine(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length != 4) {
            log.error("Invalid trade data: {}", line);
            return null;
        }

        String date = parts[0];
        if (!isValidDate(date)) {
            log.error("Invalid date format: {}, for line: {}", date, line);
            return null;
        }

        String productName = productRegistry.getProductNames().getOrDefault(parts[1], MISSING_PRODUCT_NAME);
        if (MISSING_PRODUCT_NAME.equals(productName)) {
            log.warn("Missing product mapping for product_id: {}", parts[1]);
        }

        return String.join(",", date, productName, parts[2], parts[3]);
    }

    private boolean isValidDate(final String dateStr) {
        try {
            DATE_FORMATTER.parse(dateStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
