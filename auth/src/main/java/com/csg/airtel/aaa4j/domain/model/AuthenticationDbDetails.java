package com.csg.airtel.aaa4j.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationDbDetails {
    private String userName;
    private String password;
    private String status;
    private String nasIpAddress;
    private Integer encryptionMethod;
    private Map<String, String> attributes;
    private List<BucketDetails> bucketDetails;

}
