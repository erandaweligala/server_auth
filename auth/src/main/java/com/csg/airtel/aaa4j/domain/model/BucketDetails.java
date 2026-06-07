package com.csg.airtel.aaa4j.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BucketDetails {
    private String rule;
    private Long priority;
    private String bucketId;
    private Long currentBalance;
    private String timeWindow;
    private LocalDateTime serviceStartDate;
    private LocalDateTime serviceExpiry;
    private String serviceStatus;
    private int isUnlimited;
}

