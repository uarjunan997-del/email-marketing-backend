package com.emailMarketing.template;

import org.springframework.stereotype.Service;
import ch.digitalfondue.mjml4j.Mjml4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MJML rendering service using mjml4j (pure Java implementation of mjml).
 * If rendering fails for any reason we return a commented fallback containing the
 * original MJML so the caller can still persist and potentially re-render later.
 */
@Service
public class MjmlRenderService {
    private static final Logger log = LoggerFactory.getLogger(MjmlRenderService.class);

    public String renderMjml(String mjml) {
        if (mjml == null || mjml.isBlank()) return "";
        try {
            // Basic configuration: language en, default direction LTR, no include resolver.
            Mjml4j.Configuration configuration = new Mjml4j.Configuration("en");
            String rendered = Mjml4j.render(mjml, configuration);
            // mjml4j does not pretty-print; keep as-is so we preserve inline styles.
            return rendered;
        } catch (Exception ex) {
            log.warn("MJML render failed: {}", ex.toString());
            return "<!-- mjml render failed: " + escapeForComment(ex.getMessage()) + " -->\n" + mjml;
        }
    }

    private String escapeForComment(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("--", "-");
    }
}
