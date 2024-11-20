package org.example.jobscraperapi.controller;

import org.example.jobscraperapi.model.ScrapingRequest;
import org.example.jobscraperapi.service.JobScraperSequentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.naming.ldap.ExtendedRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sequential/jobs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class JobScraperSeqController {
    private final JobScraperSequentialService scraperSequentialService;
    @PostMapping("/scrape")
    public ResponseEntity<?> startScraping(
            @RequestBody ScrapingRequest request
    ) {
        try {
            // Call sequential scraping
            List<Map<String, Object>> response;
            long startTime = System.currentTimeMillis();

            response = scraperSequentialService.scrapeJobs(request.getKeywords());

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "message", "Scraping completed with success!",
                    "data", response,
                    "totalTime", totalTime
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().
                    body(Map.of(
                            "error", e.getMessage()
                    ));
        }
    }
}