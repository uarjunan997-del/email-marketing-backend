package com.emailMarketing.template;

import com.emailMarketing.template.model.TemplateVariable;
import com.emailMarketing.template.model.TemplateVariableBinding;
import com.emailMarketing.template.repo.TemplateVariableBindingRepository;
import com.emailMarketing.template.repo.TemplateVariableRepository;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class TemplateRenderingService {
  private final TemplateVariableRepository variableRepo;
  private final TemplateVariableBindingRepository bindingRepo;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  // Extended variable pattern: {{path|default:foo|upper|lower|trim|date:yyyy-MM-dd}}
  // Capture groups:
  // 1 = variable path; remainder of the expression parsed manually for pipes
  private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}|]+)([^}]*)}}" );
  private static final Pattern CONDITIONAL_BLOCK = Pattern.compile("<!-- IF (.+?) -->([\\s\\S]*?)<!-- ENDIF -->");
  private static final Pattern LIST_BLOCK = Pattern.compile("<!-- FOR (.+?) IN (.+?) -->([\\s\\S]*?)<!-- ENDFOR -->");
  public TemplateRenderingService(TemplateVariableRepository variableRepo, TemplateVariableBindingRepository bindingRepo){
    this.variableRepo=variableRepo; this.bindingRepo=bindingRepo;
  }

  public String sanitize(String html){ return Jsoup.clean(html, Safelist.relaxed().addAttributes(":all","style","class","id")); }

  /**
   * Backward compatible simple render returning only HTML.
   */
  public String render(EmailTemplate template, Map<String,Object> data){
    return renderDetailed(template, data).getHtml();
  }

  public static class RenderResult {
    private final String html;
    private final Set<String> missingRequired;
    private final Map<String,Object> resolvedValues;
    public RenderResult(String html, Set<String> missingRequired, Map<String,Object> resolvedValues){
      this.html = html; this.missingRequired = missingRequired; this.resolvedValues = resolvedValues;
    }
    public String getHtml(){ return html; }
    public Set<String> getMissingRequired(){ return missingRequired; }
    public Map<String,Object> getResolvedValues(){ return resolvedValues; }
  }

  /**
   * Rich render returning HTML + diagnostics (missing required variables & resolved map).
   */
  public RenderResult renderDetailed(EmailTemplate template, Map<String,Object> data){
    String working = template.getHtml();
    working = processConditionalBlocks(working, data);
    working = processListBlocks(working, data);
    Map<String,Object> resolved = new HashMap<>();
    Set<String> missingRequired = new HashSet<>();
    working = substituteVariables(working, data, template.getId(), resolved, missingRequired);
    working = appendUnsubscribe(working, (String) data.getOrDefault("unsubscribeUrl", "#"));
    return new RenderResult(working, missingRequired, resolved);
  }

  private String processConditionalBlocks(String html, Map<String,Object> data){
    Matcher m = CONDITIONAL_BLOCK.matcher(html); StringBuffer sb = new StringBuffer();
    while(m.find()){
      String expr = m.group(1).trim(); String body = m.group(2);
      Object val = lookup(expr, data);
      boolean truthy = val != null && !(val instanceof Boolean b && !b) && !"".equals(val);
      m.appendReplacement(sb, Matcher.quoteReplacement(truthy?body:""));
    }
    m.appendTail(sb); return sb.toString();
  }
  private String processListBlocks(String html, Map<String,Object> data){
    Matcher m = LIST_BLOCK.matcher(html); StringBuffer sb = new StringBuffer();
    while(m.find()){
      String var = m.group(1).trim(); String listExpr = m.group(2).trim(); String body = m.group(3);
      Object val = lookup(listExpr, data); StringBuilder rendered = new StringBuilder();
      if(val instanceof Iterable<?> it){
        int idx=0; for(Object item: it){ Map<String,Object> local = new HashMap<>(data); local.put(var, item); local.put(var+"Index", idx++); rendered.append(substituteVariables(body, local, null)); }
      }
      m.appendReplacement(sb, Matcher.quoteReplacement(rendered.toString()));
    }
    m.appendTail(sb); return sb.toString();
  }

  private String substituteVariables(String html, Map<String,Object> data, Long templateId){
    return substituteVariables(html, data, templateId, null, null);
  }

  private String substituteVariables(String html, Map<String,Object> data, Long templateId, Map<String,Object> resolvedOut, Set<String> missingRequiredOut){
    Map<String,String> defaults = new HashMap<>();
    Map<String, TemplateVariableBinding> bindings = new HashMap<>();
    Map<String,Boolean> requiredFlags = new HashMap<>();
    if(templateId!=null){
      for(TemplateVariable v: variableRepo.findByTemplateId(templateId)){
        defaults.put(v.getVarName(), v.getDefaultValue());
        // If required notion is added later, update here. Currently treat all as optional.
        requiredFlags.put(v.getVarName(), false);
      }
      for(TemplateVariableBinding b: bindingRepo.findByTemplateId(templateId)){ bindings.put(b.getVarName(), b); }
    }
    Matcher m = VAR_PATTERN.matcher(html); StringBuffer sb = new StringBuffer();
    while(m.find()){
      String key = m.group(1).trim();
      String modifiersRaw = m.group(2) != null ? m.group(2) : ""; // includes pipes
      List<String> tokens = parseModifierTokens(modifiersRaw);
      String inlineDefault = extractAndRemoveDefault(tokens);
      List<String> transforms = tokens; // remaining tokens after default removed
      if ("unsubscribe".equals(key)) {
        // Preserve token for later anchor replacement in appendUnsubscribe
        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
        continue;
      }
      Object value = lookup(key, data);
      if(value == null && templateId != null){
        TemplateVariableBinding b = bindings.get(key);
        if(b != null){ value = resolveFromBinding(b, data); }
      }
      Object fallback = value != null? value : (inlineDefault!=null? inlineDefault : defaults.getOrDefault(key, ""));
      if(fallback == null || Objects.toString(fallback, "").isEmpty()){
        if(requiredFlags.getOrDefault(key, false) && missingRequiredOut != null){ missingRequiredOut.add(key); }
      }
      String replacement = applyTransforms(Objects.toString(fallback==null?"":fallback, ""), transforms);
      if(resolvedOut != null){ resolvedOut.put(key, replacement); }
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb); return sb.toString();
  }

  private List<String> parseModifierTokens(String raw){
    if(raw == null || raw.isBlank()) return Collections.emptyList();
    // raw looks like |default:Foo|upper|date:yyyy-MM-dd
    String[] parts = raw.split("\\|");
    List<String> tokens = new ArrayList<>();
    for(String p: parts){
      String t = p.trim();
      if(t.isEmpty()) continue;
      tokens.add(t); // keep as-is (e.g., default:Foo, upper, date:pattern)
    }
    return tokens;
  }

  private String extractAndRemoveDefault(List<String> tokens){
    Iterator<String> it = tokens.iterator();
    while(it.hasNext()){
      String t = it.next();
      if(t.startsWith("default:")){
        it.remove();
        return t.substring("default:".length());
      }
    }
    return null;
  }

  private String applyTransforms(String value, List<String> transforms){
    String out = value;
    for(String t: transforms){
      if(t.equalsIgnoreCase("upper")) out = out.toUpperCase(Locale.ROOT);
      else if(t.equalsIgnoreCase("lower")) out = out.toLowerCase(Locale.ROOT);
      else if(t.equalsIgnoreCase("trim")) out = out.trim();
      else if(t.startsWith("date:")){
        String pattern = t.substring("date:".length());
        out = formatDate(out, pattern);
      }
    }
    return out;
  }

  private String formatDate(String input, String pattern){
    if(input == null || input.isBlank()) return input;
    // Try parse as ISO_INSTANT, ISO_LOCAL_DATE, or epoch millis
    try {
      Instant inst;
      if(input.matches("^-?\\d{10,13}$")){
        long epoch = Long.parseLong(input);
        if(input.length()==10) epoch *= 1000L;
        inst = Instant.ofEpochMilli(epoch);
      } else {
        // attempt instant parse
        try { inst = Instant.parse(input); } catch(Exception e){
          // fallback: treat as local date
          LocalDate ld = LocalDate.parse(input);
          return ld.format(DateTimeFormatter.ofPattern(pattern));
        }
      }
      ZonedDateTime zdt = inst.atZone(ZoneId.systemDefault());
      return zdt.format(DateTimeFormatter.ofPattern(pattern));
    } catch(Exception e){
      return input; // silently ignore invalid format
    }
  }
  private Object lookup(String path, Map<String,Object> data){
    String[] parts = path.split("\\."); Object cur = data.get(parts[0]);
    for(int i=1;i<parts.length && cur!=null;i++){ try { var f = cur.getClass().getDeclaredField(parts[i]); f.setAccessible(true); cur = f.get(cur); } catch(Exception e){ return null; } }
    return cur;
  }
  private Object resolveFromBinding(TemplateVariableBinding b, Map<String,Object> data){
    TemplateVariableBinding.SourceType type = b.getSourceType();
    String key = b.getSourceKey();
    switch(type){
      case CONTACT_COLUMN: {
        Object contact = data.get("contact");
        Object val = getFromMapOrObject(contact, key);
        if(val == null) { val = data.get(key); }
        return val != null? val : b.getDefaultValue();
      }
      case CUSTOM_FIELD: {
        Object contact = data.get("contact");
        Object custom = getFromMapOrObject(contact, "custom_fields");
        if(custom instanceof Map<?,?> map){
          Object v = map.get(key);
          return v != null? v : b.getDefaultValue();
        } else if(custom instanceof String s){
          try{
            Map<String,Object> m = MAPPER.readValue(s, new TypeReference<Map<String,Object>>(){});
            Object v = m.get(key);
            return v != null? v : b.getDefaultValue();
          }catch(Exception ignored){ return b.getDefaultValue(); }
        }
        return b.getDefaultValue();
      }
      case STATIC:
        return b.getDefaultValue() != null ? b.getDefaultValue() : key;
      case SYSTEM: {
        // Known system keys; accept fallbacks from data map
        String sk = key != null ? key : b.getVarName();
        if("unsubscribe_url".equals(sk) || "unsubscribe".equals(sk)){
          Object url = data.get("unsubscribeUrl");
          return url != null? url : b.getDefaultValue();
        }
        return b.getDefaultValue();
      }
      default:
        return b.getDefaultValue();
    }
  }
  private Object getFromMapOrObject(Object obj, String field){
    if(obj == null || field == null) return null;
    if(obj instanceof Map<?,?> map){ return map.get(field); }
    try { var f = obj.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(obj); } catch(Exception e){ return null; }
  }
  private String appendUnsubscribe(String html, String url){
    if(!html.contains("{{unsubscribe}}")) return html;
    return html.replace("{{unsubscribe}}", "<a href='"+url+"'>Unsubscribe</a>");
  }
}
