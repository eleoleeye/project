package com.verygoodbank.tes.web.controller;

import jakarta.annotation.PostConstruct;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProductRegistry {

    private final Map<String, String> productNamesByIds = new HashMap<>();

    @PostConstruct
    public void init() {
        URL path = getClass().getClassLoader().getResource("product.csv");
        try (Stream<String> lines = Files.lines(Paths.get(path.getPath()))) {
            lines.skip(1)// Skip header
                    .forEach(line -> {
                        String[] parts = line.split(",", 2);
                        if (parts.length == 2) {
                            try {
                                String productId = parts[0];
                                String productName = parts[1];
                                productNamesByIds.put(productId, productName);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid product ID: {}", parts[0]);
                            }
                        } else {
                            log.warn("Invalid line format: {}", line);
                        }
                    });
        } catch (Exception e) {
            log.error("Error loading product data: {}", e.getMessage());
        }
    }

    public Map<String, String> getProductNames() {
        return productNamesByIds;
    }
}
