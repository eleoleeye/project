package com.verygoodbank.tes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileCopyUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeEnrichmentServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testEnrichTrades() throws Exception {
        // Read the sample trade.csv file from test resources
        ClassPathResource tradeResource = new ClassPathResource("trade.csv");
        String tradeCsv = FileCopyUtils.copyToString(new InputStreamReader(tradeResource.getInputStream(), StandardCharsets.UTF_8));

        // Expected enriched CSV output
        String expectedResponse = String.join("\n",
                "date,product_name,currency,price",
                "20160101,Treasury Bills Domestic,EUR,10.0",
                "20160101,Corporate Bonds Domestic,EUR,20.1",
                "20160101,REPO Domestic,EUR,30.34",
                "20160101,Missing Product Name,EUR,35.34"
        );

        // Perform the POST request to /api/v1/enrich
        mockMvc.perform(post("/api/v1/enrich")
                        .contentType("text/csv")
                        .content(tradeCsv))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"enriched_trades.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(expectedResponse));
    }
}
