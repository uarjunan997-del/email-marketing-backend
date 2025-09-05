package com.emailMarketing.attribution;

import jakarta.persistence.*;

@Entity
@Table(name="attribution_models")
public class AttributionModel {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String modelCode; private String description; private Double decayHalfLifeDays; private String activeFlag;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public String getModelCode(){return modelCode;} public void setModelCode(String modelCode){this.modelCode=modelCode;}
    public String getDescription(){return description;} public void setDescription(String description){this.description=description;}
    public Double getDecayHalfLifeDays(){return decayHalfLifeDays;} public void setDecayHalfLifeDays(Double decayHalfLifeDays){this.decayHalfLifeDays=decayHalfLifeDays;}
    public String getActiveFlag(){return activeFlag;} public void setActiveFlag(String activeFlag){this.activeFlag=activeFlag;}
}
