package org.example.jobscraperapi.service;

import org.example.jobscraperapi.scraper.PlaywrightSequential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.print.attribute.standard.JobKOctets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class JobScraperSequentialService {
    private List<Map<String, Object>> results = new ArrayList<>();

    public JobScraperSequentialService() {
    }

    public List<Map<String, Object>> scrapeJobs(String keywords) {
        List<List<String>> URLs = buildURLS(keywords);

        startScrapingProcess(URLs);

        return results;
    }

    private List<List<String>> buildURLS(String keywords) {
        String compuURL = "https://mx.computrabajo.com/trabajo-de-%s";
        String occURL = "https://www.occ.com.mx/empleos/de-%s";

        String[] keywordsList = keywords.split("\\s+");

        String compuURLFormat = String.join("-", keywordsList);
        compuURL = String.format(compuURL, compuURLFormat);
        occURL = String.format(occURL, compuURLFormat);

        return List.of(
                List.of("computrabajo", compuURL),
                List.of("occ", occURL)
        );
    }

    private void startScrapingProcess(List<List<String>> URLs) {
        for (List<String> URL : URLs) {
            // Exec scraping for each URL
            // get the result and append it to the result array
            Map<String, Object> resultJob = new PlaywrightSequential.Task(
                    URL.get(0),
                    URL.get(1)
            ).call();

            results.add(resultJob);
        }
    }
}