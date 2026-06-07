package com.csg.airtel.aaa4j.domain.model.session;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConsumptionRecord {
    private LocalDate date;
    private Long bytesConsumed;
    private Integer requestCount;
}
