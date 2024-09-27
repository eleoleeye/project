package com.verygoodbank.tes.web.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeEnrichmentService {

    private static final int CHUNK_SIZE = 10_000;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final String END_OF_FILE = "EOF";
    private static final String RESPONSE_HEADER = "date,product_name,currency,price\n";

    private final ExecutorService writersExecutors = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private final ExecutorService chunksExecutors = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private final RowEnrichmentProcessor rowEnrichmentProcessor;

    public void enrichTrades(InputStream inputStream, OutputStream outputStream) throws InterruptedException {
        BlockingQueue<String> resultQueue = new LinkedBlockingQueue<>();
        Future<?> writer = writersExecutors.submit(() -> runWriter(outputStream, resultQueue));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            reader.readLine();// Skip header

            List<CompletableFuture<Void>> chunkTasks = processByChunks(reader, resultQueue);

            // Wait for all chunk processing tasks to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(chunkTasks.toArray(new CompletableFuture[0]));
            allFutures.join();

            // Now put END_OF_FILE into resultQueue to signal the end of processing for the writer
            resultQueue.put(END_OF_FILE);

        } catch (InterruptedException ie) {
            log.error("Thread was interrupted: {}", ie.getMessage(), ie);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error reading from input stream: {}", e.getMessage(), e);
        }  finally {
            try {
                writer.get(5, TimeUnit.MINUTES);
            } catch (ExecutionException | TimeoutException e) {
                log.error("Error in writer thread: {}", e.getMessage(), e);
            }
        }
    }

    private List<CompletableFuture<Void>> processByChunks(BufferedReader reader,
                                                          BlockingQueue<String> resultQueue) throws IOException {
        String line;
        List<CompletableFuture<Void>> chunkTasks = new ArrayList<>();
        List<String> chunk = new ArrayList<>(CHUNK_SIZE);

        while ((line = reader.readLine()) != null) {
            chunk.add(line);
            if (chunk.size() == CHUNK_SIZE) {
                CompletableFuture<Void> chunkTask =
                        CompletableFuture.runAsync(() -> processTradeChunk(
                                new ArrayList<>(chunk), resultQueue), chunksExecutors);
                chunkTasks.add(chunkTask);
                chunk.clear();
            }
        }
        if (!chunk.isEmpty()) {
            CompletableFuture<Void> chunkTask =
                    CompletableFuture.runAsync(() -> processTradeChunk(chunk, resultQueue), chunksExecutors);
            chunkTasks.add(chunkTask);
        }

        return chunkTasks;
    }

    private void processTradeChunk(List<String> chunk, BlockingQueue<String> resultQueue) {
        String enrichedChunk = chunk.stream()
                .map(rowEnrichmentProcessor::processTradeLine)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        if (!enrichedChunk.isEmpty()) {
            try {
                resultQueue.put(enrichedChunk);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while adding result to queue: {}", e.getMessage());
            }
        }
    }

    private void runWriter(final OutputStream outputStream, final BlockingQueue<String> resultQueue) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writer.write(RESPONSE_HEADER);
            String result;
            while (!(result = resultQueue.take()).equals(END_OF_FILE)) {
                writer.write(result);
            }
            writer.flush();
        } catch (Exception e) {
            log.error("Error writing to output stream: {} ", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
