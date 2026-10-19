package com.example.supplychain.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public metadata uses a configured origin, never a client-supplied Host header. */
@RestController
public class B09PublicPageController {
    private final String publicBase;
    private final String home;

    public B09PublicPageController(
            @Value("${app.public-base-url:http://localhost:8081}") String configuredBase) throws IOException {
        URI origin = URI.create(configuredBase.trim());
        String path = origin.getRawPath();
        if (!("https".equalsIgnoreCase(origin.getScheme()) || "http".equalsIgnoreCase(origin.getScheme()))
                || origin.getHost() == null || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null || origin.getRawFragment() != null
                || (path != null && !path.isEmpty() && !path.equals("/"))) {
            throw new IllegalArgumentException("app.public-base-url must be an http(s) origin without credentials, path, query or fragment");
        }
        publicBase = origin.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + origin.getRawAuthority();
        try (var input = new ClassPathResource("static/index.html").getInputStream()) {
            home = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__B09_PUBLIC_BASE__", publicBase);
        }
    }

    @GetMapping(value={"/", "/index.html"}, produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> home() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePublic()).body(home);
    }

    @GetMapping(value="/sitemap.xml", produces=MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n"
                + "  <url><loc>" + publicBase + "/</loc></url>\n</urlset>\n";
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(xml);
    }

    @GetMapping(value="/robots.txt", produces=MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String body = "User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /h2-console\n"
                + "Disallow: /reports.html\nDisallow: /audit-logs.html\n"
                + "Sitemap: " + publicBase + "/sitemap.xml\n";
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(body);
    }
}
