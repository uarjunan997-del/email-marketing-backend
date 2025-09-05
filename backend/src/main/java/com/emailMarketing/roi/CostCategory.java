package com.emailMarketing.roi;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;

@Entity @Table(name="COST_CATEGORIES") @Getter @Setter
public class CostCategory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, unique=true,length=50)
    private String code;
    @Column(nullable=false,length=100)
    private String name;
    @Column(name="PARENT_CODE", length=50)
    private String parentCode;
    @Column(name="CATEGORY_TYPE", length=30, nullable=false)
    private String categoryType;
}
