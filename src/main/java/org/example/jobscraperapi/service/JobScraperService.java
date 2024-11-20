package org.example.jobscraperapi.service;

import org.example.jobscraperapi.scraper.PlaywrightConcurrent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class JobScraperService {
    private static final int THREAD_POOL_SIZE = 2;
    private final ExecutorService executorService;
    private final AtomicBoolean isScrapingActive = new AtomicBoolean(false);
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private long startScrapingTime;

    public JobScraperService() {
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }
     // "python junior"
    public void scrapeJobs(SseEmitter emitter, String keywords) {
        if (isScrapingActive.get()) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Scraping is already in progress"));
                return;
            } catch (IOException e) {
                log.error("Error sending error message", e);
                emitter.completeWithError(e);
                return;
            }
        }

        List<List<String>> URLs = buildURLS(keywords);

        startScrapingTime = System.currentTimeMillis();

        startScrapingProcess(emitter, URLs);
    }

    private List<List<String>> buildURLS(String keywords) {
//        String indeedURL = "https://mx.indeed.com/jobs?q=%s";
        String compuURL = "https://mx.computrabajo.com/trabajo-de-%s";
        String occURL = "https://www.occ.com.mx/empleos/de-%s";

        String[] keywordsList = keywords.split("\\s+");

//        String indeedURLFormat = String.join("+", keywordsList);
//        indeedURL = String.format(indeedURL, indeedURLFormat);

        String compuURLFormat = String.join("-", keywordsList);
        compuURL = String.format(compuURL, compuURLFormat);
        occURL = String.format(occURL, compuURLFormat);

        return List.of(
//                List.of("indeed", indeedURL),
                List.of("computrabajo", compuURL),
                List.of("occ", occURL)
        );
    }

    private void startScrapingProcess(SseEmitter emitter, List<List<String>> URLs) {
        isScrapingActive.set(true);
        completedTasks.set(0);

        // Create completion service for handling results as they come in
        CompletionService<Map<String, Object>> completionService =
                new ExecutorCompletionService<>(executorService);

        // Submit tasks
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (List<String> url : URLs) {
            futures.add(completionService.submit(new PlaywrightConcurrent.PlaywrightTask(
                    url.get(0),
                    url.get(1)
            )));
        }

        // Handle results in a separate thread
        executorService.submit(() -> processResults(completionService, futures, emitter));
    }
    private void processResults(
            CompletionService<Map<String, Object>> completionService,
            List<Future<Map<String, Object>>> futures,
            SseEmitter emitter) {
        try {
            for (int i = 0; i < futures.size(); i++) {
                try {
                    Future<Map<String, Object>> completed = completionService.take();
                    Map<String, Object> result = completed.get();

                    // Send result through SSE
                    emitter.send(SseEmitter.event()
                            .name("job_data")
                            .data(result)
                            .id(String.valueOf(System.currentTimeMillis())));

                    completedTasks.incrementAndGet();

                    // Send progress update
                    emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(Map.of(
                                    "completed", completedTasks.get(),
                                    "total", 2,
                                    "percentage", (completedTasks.get() * 100.0) / 2
                            )));

                } catch (ExecutionException | InterruptedException e) {
                    log.error("Error processing scraping result", e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "error");
                    errorResult.put("error", e.getMessage());
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(errorResult));
                }
            }

            // All tasks completed
            long endScrapingTime = System.currentTimeMillis();
            isScrapingActive.set(false);

            long totalProcessingTime = endScrapingTime - startScrapingTime;

            // Send completion event
            emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(Map.of(
                            "message", "Scraping completed",
                            "totalTime", totalProcessingTime,
                            "totalProcessed", completedTasks.get()
                    )));

            emitter.complete();

        } catch (IOException e) {
            log.error("Error sending SSE events", e);
            emitter.completeWithError(e);
        } finally {
            isScrapingActive.set(false);
        }
    }
}