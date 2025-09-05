package com.emailMarketing.analytics;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="currency_rates")
public class CurrencyRate {
    @Id
    @Column(name="rate_date") private java.sql.Date rateDatePk; // composite simplified; store base+counter in columns with unique constraint not expressed via JPA here
    private String baseCurrency; private String counterCurrency; private Double rate; // For MVP we treat (date,base,counter) unique externally
    public java.sql.Date getRateDatePk(){return rateDatePk;} public void setRateDatePk(java.sql.Date d){this.rateDatePk=d;}
    public String getBaseCurrency(){return baseCurrency;} public void setBaseCurrency(String baseCurrency){this.baseCurrency=baseCurrency;}
    public String getCounterCurrency(){return counterCurrency;} public void setCounterCurrency(String counterCurrency){this.counterCurrency=counterCurrency;}
    public Double getRate(){return rate;} public void setRate(Double rate){this.rate=rate;}
    public LocalDate getRateDate(){ return rateDatePk==null?null: rateDatePk.toLocalDate(); }
}
