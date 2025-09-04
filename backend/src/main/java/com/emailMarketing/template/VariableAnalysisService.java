package com.emailMarketing.template;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

@Service
public class VariableAnalysisService {
    private static final Pattern VAR = Pattern.compile("\\{\\{([a-zA-Z0-9_\\.]+)(?:\\|default:[^}]+)?}}" );
    private static final Set<String> REQUIRED = Set.of("unsubscribe");
    private static final Set<String> RESERVED_PREFIXES = Set.of("javascript:");

    public Result analyze(String html){
        Set<String> found = new LinkedHashSet<>();
        Matcher m = VAR.matcher(html==null?"":html);
        while(m.find()){
            String raw = m.group(1);
            if(!isSafe(raw)) continue;
            found.add(raw.split("\\|")[0]);
        }
        List<String> missingRequired = REQUIRED.stream().filter(r->found.stream().noneMatch(f->f.equals(r) || f.endsWith("."+r))).toList();
        return new Result(List.copyOf(found), missingRequired, List.of());
    }

    private boolean isSafe(String var){
        String lower = var.toLowerCase();
        for(String p: RESERVED_PREFIXES){ if(lower.startsWith(p)) return false; }
        return true;
    }

    public record Result(List<String> variables, List<String> missingRequired, List<String> warnings){}
}
