package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import java.time.LocalDate;

@Value
@Builder
@Jacksonized
public class ForecastRequest {

    @NotNull(message = "date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate date;

    @NotBlank(message = "category is required")
    @Pattern(regexp = "Electronics|Clothing|Furniture|Groceries|Toys",
             message = "category must be one of: Electronics, Clothing, Furniture, Groceries, Toys")
    String category;

    @NotBlank(message = "region is required")
    @Pattern(regexp = "North|South|East|West",
             message = "region must be one of: North, South, East, West")
    String region;

    @Min(value = 0, message = "inventoryLevel must be >= 0")
    int inventoryLevel;

    @Min(value = 0, message = "unitsSold must be >= 0")
    int unitsSold;

    @Min(value = 0, message = "unitsOrdered must be >= 0")
    int unitsOrdered;

    @DecimalMin(value = "0.0", message = "price must be >= 0")
    double price;

    @DecimalMin(value = "0.0", message = "discount must be between 0 and 100")
    @DecimalMax(value = "100.0", message = "discount must be between 0 and 100")
    double discount;

    @NotBlank(message = "weatherCondition is required")
    @Pattern(regexp = "Sunny|Rainy|Snowy|Cloudy|Windy",
             message = "weatherCondition must be one of: Sunny, Rainy, Snowy, Cloudy, Windy")
    String weatherCondition;

    boolean promotion;

    @DecimalMin(value = "0.0", message = "competitorPricing must be >= 0")
    double competitorPricing;

    @NotBlank(message = "seasonality is required")
    @Pattern(regexp = "Spring|Summer|Autumn|Winter",
             message = "seasonality must be one of: Spring, Summer, Autumn, Winter")
    String seasonality;

    boolean epidemic;
}
