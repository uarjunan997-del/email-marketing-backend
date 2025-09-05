package com.emailMarketing.roi;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.io.Serializable;

@Entity @Table(name="COST_ALLOCATION_LINKS") @Getter @Setter
public class CostAllocationLink {
    @EmbeddedId private LinkId id;
    @Column(name="WEIGHT_VALUE", precision=14) private Double weightValue;

    @Embeddable @Getter @Setter
    public static class LinkId implements Serializable { private Long allocationId; private Long campaignId; @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof LinkId l)) return false; return java.util.Objects.equals(allocationId,l.allocationId) && java.util.Objects.equals(campaignId,l.campaignId);} @Override public int hashCode(){ return java.util.Objects.hash(allocationId,campaignId);} }
}
