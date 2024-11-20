package org.example.jobscraperapi.controller;

import org.example.jobscraperapi.model.ScrapingRequest;
import org.example.jobscraperapi.service.JobScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.naming.ldap.ExtendedRequest;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class JobScraperController {

    private final JobScraperService scraperService;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // Client connects and gets an emitter ID
    @GetMapping(path = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String emitterId = UUID.randomUUID().toString();

        configureEmitter(emitter, emitterId);
        emitters.put(emitterId, emitter);

        // Send the emitter ID to the client
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data(Map.of("emitterId", emitterId))
                    .id(emitterId));
        } catch (IOException e) {
            log.error("Error sending initial SSE event", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    // Start scraping with keywords
    @PostMapping("/scrape/{emitterId}")
    public ResponseEntity<?> startScraping(
            @PathVariable String emitterId,
            @RequestBody ScrapingRequest request) {

        SseEmitter emitter = emitters.get(emitterId);
        if (emitter == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No active connection found for this ID"));
        }

        try {
            scraperService.scrapeJobs(emitter, request.getKeywords());
            return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "Scraping started with keywords: " + request.getKeywords()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/disconnect/{emitterId}")
    public ResponseEntity<?> disconnect(@PathVariable String emitterId) {
        SseEmitter emitter = emitters.remove(emitterId);
        if (emitter != null) {
            emitter.complete();
            return ResponseEntity.ok(Map.of("message", "Disconnected successfully"));
        }
        return ResponseEntity.notFound().build();
    }

    private void configureEmitter(SseEmitter emitter, String emitterId) {
        emitter.onCompletion(() -> {
            emitters.remove(emitterId);
            log.info("SSE completed for client: {}", emitterId);
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitterId);
            log.warn("SSE timeout for client: {}", emitterId);
        });

        emitter.onError(ex -> {
            emitters.remove(emitterId);
            log.error("SSE error for client: {}", emitterId, ex);
        });
    }
}