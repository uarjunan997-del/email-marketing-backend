package com.emailMarketing.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class CurrentUserProviderConfig {
    @Bean
    public PlanAccessAspect.CurrentUserProvider currentUserProvider(){
        return () -> new PlanAccessAspect.UserLike(){
            @Override public PlanTier planTier(){
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                return auth!=null ? PlanTier.PRO : PlanTier.FREE; // MVP: authenticated users get PRO tier
            }
        };
    }
}
