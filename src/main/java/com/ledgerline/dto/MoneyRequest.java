
package com.ledgerline.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record MoneyRequest(

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @Digits(integer = 17, fraction = 2,
            message = "Amount can have at most 2 decimal places")
    BigDecimal amount

) {}
