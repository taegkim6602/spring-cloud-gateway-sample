package com.example.demogateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserTrafficInfo {
    private long id;
    private String expDate;
    private long dayTraffic;
    private long usedTraffic;
}
