package org.example.jobscraperapi.model;

import lombok.Data;

@Data
public class ScrapingRequest {
    // The request contains a string containing all the keywords that
    // the user types in the form
    private String keywords;
}