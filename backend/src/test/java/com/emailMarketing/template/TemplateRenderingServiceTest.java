package com.emailMarketing.template;

import com.emailMarketing.template.model.TemplateVariableBinding;
import com.emailMarketing.template.repo.TemplateVariableBindingRepository;
import com.emailMarketing.template.repo.TemplateVariableRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TemplateRenderingServiceTest {
    @Test
    public void testBindingResolutionContactAndSystem(){
        TemplateVariableRepository varRepo = Mockito.mock(TemplateVariableRepository.class);
        TemplateVariableBindingRepository bindRepo = Mockito.mock(TemplateVariableBindingRepository.class);
        TemplateRenderingService svc = new TemplateRenderingService(varRepo, bindRepo);

        // Bindings for first_name and unsubscribe_url
        TemplateVariableBinding b1 = new TemplateVariableBinding();
        b1.setTemplateId(1L); b1.setVarName("first_name");
        b1.setSourceType(TemplateVariableBinding.SourceType.CONTACT_COLUMN); b1.setSourceKey("first_name");
        TemplateVariableBinding b2 = new TemplateVariableBinding();
        b2.setTemplateId(1L); b2.setVarName("unsubscribe_url");
        b2.setSourceType(TemplateVariableBinding.SourceType.SYSTEM); b2.setSourceKey("unsubscribe_url");
        Mockito.when(bindRepo.findByTemplateId(1L)).thenReturn(List.of(b1,b2));

        EmailTemplate t = new EmailTemplate();
        t.setId(1L); t.setHtml("Hello {{first_name}}. {{unsubscribe}} and {{unsubscribe_url}}.");

        Map<String,Object> contact = Map.of(
                "first_name","Jane",
                "custom_fields", Map.of("vip","yes")
        );
    String html = svc.render(t, Map.of(
                "contact", contact,
                "unsubscribeUrl", "https://u.example.com/abc"
        ));
    System.out.println("Rendered HTML: " + html);
        assertTrue(html.contains("Hello Jane"));
        assertTrue(html.contains("<a href='https://u.example.com/abc'>Unsubscribe</a>"));
        assertTrue(html.contains("https://u.example.com/abc"));
    }

    @Test
    public void testCustomFieldBinding(){
        TemplateVariableRepository varRepo = Mockito.mock(TemplateVariableRepository.class);
        TemplateVariableBindingRepository bindRepo = Mockito.mock(TemplateVariableBindingRepository.class);
        TemplateRenderingService svc = new TemplateRenderingService(varRepo, bindRepo);
        TemplateVariableBinding b = new TemplateVariableBinding();
        b.setTemplateId(2L); b.setVarName("loyalty_tier");
        b.setSourceType(TemplateVariableBinding.SourceType.CUSTOM_FIELD); b.setSourceKey("tier");
        Mockito.when(bindRepo.findByTemplateId(2L)).thenReturn(List.of(b));

        EmailTemplate t = new EmailTemplate();
        t.setId(2L); t.setHtml("Tier: {{loyalty_tier}}");
        Map<String,Object> contact = Map.of(
                "first_name","Jane",
                "custom_fields", Map.of("tier","gold")
        );
        String html = svc.render(t, Map.of("contact", contact));
        assertEquals("Tier: gold", html);
    }
}
