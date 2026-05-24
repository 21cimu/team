package com.fitmind.module.ai.dto;

import lombok.Data;

@Data
public class WeatherContextSnapshot {
    private String province;
    private String city;
    private String weather;
    private String temperature;
    private String windDirection;
    private String windPower;
    private String humidity;
    private String reportTime;
}
