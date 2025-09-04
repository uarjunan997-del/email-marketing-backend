package com.emailMarketing.template;

import com.emailMarketing.template.model.TemplateVariable;
import com.emailMarketing.template.repo.TemplateVariableRepository;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateRenderingService {
  private final TemplateVariableRepository variableRepo;
  private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_.]+)(?:\\|default:(.+?))?}}" );
  private static final Pattern CONDITIONAL_BLOCK = Pattern.compile("<!-- IF (.+?) -->([\\s\\S]*?)<!-- ENDIF -->");
  private static final Pattern LIST_BLOCK = Pattern.compile("<!-- FOR (.+?) IN (.+?) -->([\\s\\S]*?)<!-- ENDFOR -->");
  public TemplateRenderingService(TemplateVariableRepository variableRepo){this.variableRepo=variableRepo;}

  public String sanitize(String html){ return Jsoup.clean(html, Safelist.relaxed().addAttributes(":all","style","class","id")); }

  public String render(EmailTemplate template, Map<String,Object> data){
    String working = template.getHtml();
    working = processConditionalBlocks(working, data);
    working = processListBlocks(working, data);
    working = substituteVariables(working, data, template.getId());
    working = appendUnsubscribe(working, (String) data.getOrDefault("unsubscribeUrl", "#"));
    // CSS inline placeholder: could integrate external library; keep simple now
    return working;
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
    Map<String,String> defaults = new HashMap<>();
    if(templateId!=null){ for(TemplateVariable v: variableRepo.findByTemplateId(templateId)){ defaults.put(v.getVarName(), v.getDefaultValue()); } }
    Matcher m = VAR_PATTERN.matcher(html); StringBuffer sb = new StringBuffer();
    while(m.find()){
      String key = m.group(1).trim(); String inlineDefault = m.group(2)!=null?m.group(2).trim():null;
      Object value = lookup(key, data);
      String replacement = Objects.toString(value != null? value : (inlineDefault!=null? inlineDefault : defaults.getOrDefault(key, "")), "");
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb); return sb.toString();
  }
  private Object lookup(String path, Map<String,Object> data){
    String[] parts = path.split("\\."); Object cur = data.get(parts[0]);
    for(int i=1;i<parts.length && cur!=null;i++){ try { var f = cur.getClass().getDeclaredField(parts[i]); f.setAccessible(true); cur = f.get(cur); } catch(Exception e){ return null; } }
    return cur;
  }
  private String appendUnsubscribe(String html, String url){
    if(!html.contains("{{unsubscribe}}")) return html;
    return html.replace("{{unsubscribe}}", "<a href='"+url+"'>Unsubscribe</a>");
  }
}
