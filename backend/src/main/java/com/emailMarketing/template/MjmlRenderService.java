package com.emailMarketing.template;

import org.springframework.stereotype.Service;

/** Simple MJML renderer placeholder. In real integration, call a Node microservice or Java wrapper. */
@Service
public class MjmlRenderService {
    public String renderMjml(String mjml){
        if(mjml==null || mjml.isBlank()) return "";
        // Placeholder conversion: wrap content; real impl would invoke external process.
        return "<!-- mjml placeholder render -->\n" + mjml
                .replaceAll("<mj-section>", "<table width='100%' role='presentation'><tr><td>")
                .replaceAll("</mj-section>", "</td></tr></table>")
                .replaceAll("<mj-text>", "<p style='font-family:Arial,sans-serif;font-size:14px;line-height:1.5;margin:0 0 12px'>")
                .replaceAll("</mj-text>", "</p>");
    }
}
