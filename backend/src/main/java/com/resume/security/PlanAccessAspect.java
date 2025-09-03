package com.resume.security;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Minimal aspect enforcing plan level based on injected current user provider.
 * Replace CurrentUserProvider with your own implementation.
 */
@Aspect
@Component
public class PlanAccessAspect {

    private final CurrentUserProvider currentUserProvider;

    public PlanAccessAspect(CurrentUserProvider currentUserProvider) { this.currentUserProvider = currentUserProvider; }

    @Before("@within(com.resume.security.RequiresPaidPlan) || @annotation(com.resume.security.RequiresPaidPlan) || @within(com.resume.security.RequiresPlan) || @annotation(com.resume.security.RequiresPlan)")
    public void ensurePlan(JoinPoint jp) {
        UserLike u = currentUserProvider.currentUser();
        PlanTier userTier = u == null ? PlanTier.FREE : u.planTier();
        PlanTier required = null;
        MethodSignature ms = (MethodSignature) jp.getSignature();
        if (ms.getMethod().isAnnotationPresent(RequiresPlan.class)) {
            required = ms.getMethod().getAnnotation(RequiresPlan.class).level();
        } else if (jp.getTarget().getClass().isAnnotationPresent(RequiresPlan.class)) {
            required = jp.getTarget().getClass().getAnnotation(RequiresPlan.class).level();
        } else if (ms.getMethod().isAnnotationPresent(RequiresPaidPlan.class) || jp.getTarget().getClass().isAnnotationPresent(RequiresPaidPlan.class)) {
            required = PlanTier.PRO; // paid baseline
        }
        if (required != null && userTier.ordinal() < required.ordinal()) {
            throw new FeatureLockedException("Upgrade required: " + required.name());
        }
    }

    public interface CurrentUserProvider { UserLike currentUser(); }
    public interface UserLike { PlanTier planTier(); }
    public static class FeatureLockedException extends RuntimeException { public FeatureLockedException(String m){ super(m);} }
}
