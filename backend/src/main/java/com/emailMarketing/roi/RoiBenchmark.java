package com.emailMarketing.roi;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.io.Serializable;

@Entity @Table(name="ROI_BENCHMARKS") @Getter @Setter
public class RoiBenchmark {
    @EmbeddedId private Pk id; @Column(name="BENCH_VALUE", nullable=false) private Double value;
    @Embeddable @Getter @Setter public static class Pk implements Serializable { private String industryCode; private String metric; @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof Pk p)) return false; return java.util.Objects.equals(industryCode,p.industryCode)&& java.util.Objects.equals(metric,p.metric);} @Override public int hashCode(){ return java.util.Objects.hash(industryCode,metric);} }
}
