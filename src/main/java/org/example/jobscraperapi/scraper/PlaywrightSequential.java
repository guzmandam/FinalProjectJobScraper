package org.example.jobscraperapi.scraper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;

import java.util.*;

public class PlaywrightSequential {
    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:122.0) Gecko/20100101 Firefox/122.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edg/121.0.0.0"
    );

    private static final List<Map<String, Double>> MEXICO_LOCATIONS = Arrays.asList(
            Map.of("latitude", 19.4326, "longitude", -99.1332),
            Map.of("latitude", 20.6597, "longitude", -103.3496),
            Map.of("latitude", 25.6866, "longitude", -100.3161),
            Map.of("latitude", 19.1808, "longitude", -96.1429),
            Map.of("latitude", 21.8853, "longitude", -102.2916)
    );

    public static class Task {
        private final String url;
        private final String site;
        private final Random random = new Random();

        public Task(String site, String url) {
            this.url = url;
            this.site = site;
        }

        public Map<String, Object> call() {
            try (Playwright playwright = Playwright.create()) {
                String userAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
                Map<String, Double> location = MEXICO_LOCATIONS.get(random.nextInt(MEXICO_LOCATIONS.size()));

                // Advanced launch options to avoid detection
                BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(Arrays.asList(
                                "--disable-blink-features=AutomationControlled",
                                "--disable-features=IsolateOrigins",
                                "--disable-site-isolation-trials",
                                "--disable-web-security",
                                "--disable-blink-features",
                                "--no-sandbox",
                                "--disable-gpu"
                        ));

                Browser browser = playwright.chromium().launch(launchOptions);

                // Context options with randomized parameters
                Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                        .setUserAgent(userAgent)
                        .setViewportSize(1920, 1080)
                        .setLocale("es-MX")
                        .setTimezoneId("America/Mexico_City")
                        .setGeolocation(location.get("latitude"), location.get("longitude"))
                        .setPermissions(Arrays.asList("geolocation"))
                        .setJavaScriptEnabled(true)
                        .setDeviceScaleFactor(1.0)
                        .setIsMobile(false)
                        .setHasTouch(false)
                        .setColorScheme(ColorScheme.LIGHT)
                        .setReducedMotion(ReducedMotion.NO_PREFERENCE)
                        .setForcedColors(ForcedColors.NONE)
                        .setIgnoreHTTPSErrors(true);

                BrowserContext context = browser.newContext(contextOptions);

                Map<String, String> headers = new HashMap<>();
                headers.put("Accept-Language", "es-MX,es;q=0.9,en;q=0.8");
                headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
                headers.put("sec-ch-ua-platform", "\"Windows\"");
                headers.put("sec-ch-ua", "\"Chromium\";v=\"121\", \"Not A(Brand\";v=\"99\"");
                headers.put("sec-ch-ua-mobile", "?0");
                headers.put("Upgrade-Insecure-Requests", "1");
                headers.put("Sec-Fetch-Site", "none");
                headers.put("Sec-Fetch-Mode", "navigate");
                headers.put("Sec-Fetch-User", "?1");
                headers.put("Sec-Fetch-Dest", "document");
                context.setExtraHTTPHeaders(headers);

                Page page = context.newPage();
                setupPageHandlers(page);

                try {
                    // Random delay before navigation (1-3 seconds)
                    Thread.sleep(1000 + random.nextInt(2000));

                    System.out.printf("Navigating to %s with UserAgent: %s%n",
                            url, userAgent);

                    // Navigate with custom timeout and wait options
                    page.setDefaultNavigationTimeout(10_000);
                    page.setDefaultTimeout(10_000);

                    Response response = page.navigate(url, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.LOAD));

                    // Check if we got blocked
                    if (response != null && (response.status() == 403 || response.status() == 429)) {
                        Map<String, Object> blockedResponse = new HashMap<>();
                        blockedResponse.put("status", "blocked");
                        blockedResponse.put("message", String.format("Blocked by %s (Status: %d)", url, response.status()));
                        blockedResponse.put("site", site);
                        blockedResponse.put("url", url);
                        blockedResponse.put("timestamp", System.currentTimeMillis());
                        blockedResponse.put("data", new HashMap<>());

                        return blockedResponse;
                    }

                    // Random delay after page load (2-4 seconds)
                    Thread.sleep(2000 + random.nextInt(2000));

                    Map<String, Object> result;

                    switch (site) {
                        case "computrabajo":
                            result = processCompuTrabajo(page, url, site);
                            break;

                        case "occ":
                            result = processOcc(page, url, site);
                            break;

                        default:
                            result = new HashMap<String, Object>(Map.of(
                                    "status", "failed",
                                    "timestamp", System.currentTimeMillis(),
                                    "message", "",
                                    "url", url,
                                    "site", site,
                                    "data", new HashMap<>()
                            ));

                    }

                    return result;
                } finally {
                    context.close();
                    browser.close();
                }
            } catch (Exception e) {
                return Map.of(
                    "site", site,
                        "status", "error",
                        "message", e.getMessage(),
                        "data", new ArrayList<>()
                );
            }
        }

        private static Map<String, Object> processCompuTrabajo(Page page, String url, String site) {
            Map<String, Object> result = new HashMap<>();
            List<Map<String, String>> resultsCompuTrabajo = new ArrayList<>();

            Locator listItemsCompu = page.locator("div#offersGridOfferContainer > article");
            listItemsCompu.first().waitFor();
            int countC = listItemsCompu.count();
            for (int i = 0; i < countC; i++) {
                Locator articleItem = listItemsCompu.nth(i);
                articleItem.waitFor();
                Map<String, String> content = new HashMap<>();

                content.put("title", articleItem.locator("h2.fs18.fwB").first().innerText());
                content.put("url", "https://mx.computrabajo.com" + articleItem.locator("a.js-o-link.fc_base").first().getAttribute("href"));
                content.put("company", articleItem.locator("p.dIB.fs16.fc_base.mt5").first().innerText());

                resultsCompuTrabajo.add(content);
            }

            result.put("data", resultsCompuTrabajo);
            result.put("status", "success");
            result.put("url", url);
            result.put("site", site);
            result.put("timestamp", System.currentTimeMillis());
            result.put("message", "");

            return result;
        }

        private static Map<String, Object> processOcc(Page page, String url, String site) {
            Map<String, Object> result = new HashMap<>();
            List<Map<String, String>> resultsOcc = new ArrayList<>();

            Locator listItemsOCC = page.locator("[id^='jobcard-']");
            listItemsOCC.first().waitFor();
            int countO = listItemsOCC.count();
            for (int i = 0; i < countO; i++) {
                Locator occItem = listItemsOCC.nth(i);
                occItem.waitFor();
                Map<String, String> content = new HashMap<>();

                content.put("title", occItem.locator("h2").first().innerText());
                content.put("url", "https://www.occ.com.mx" + occItem.locator("a").first().getAttribute("href"));
                content.put("company", occItem.locator("a").first().innerText());

                resultsOcc.add(content);
            }

            result.put("data", resultsOcc);
            result.put("status", "success");
            result.put("url", url);
            result.put("site", site);
            result.put("timestamp", System.currentTimeMillis());
            result.put("message", "");

            return result;
        }
        private void setupPageHandlers(Page page) {
            // Handle JavaScript dialogs automatically
            page.onDialog(dialog -> {
                System.out.printf("Dialog appeared on %s: %s%n", url, dialog.message());
                dialog.dismiss();
            });

            // Log console messages for debugging
//            page.onConsoleMessage(msg -> {
//                if (msg.type().equals("error") || msg.type().equals("warning")) {
//                    System.out.printf("Console [%s] on %s: %s%n", msg.type(), url, msg.text());
//                }
//            });

            // Monitor for potential blocking messages or elements
            page.onResponse(response -> {
                if (response.status() == 403 || response.status() == 429) {
                    System.out.printf("Received blocking status %d from %s%n", response.status(), url);
                }
            });
        }
    }
}
