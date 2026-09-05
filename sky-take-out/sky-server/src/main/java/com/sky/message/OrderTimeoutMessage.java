package com.sky.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Wire-only compatibility shell for timeout messages serialized before the module migration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {
    private static final long serialVersionUID = 2457564595320478208L;

    private String orderNumber;
}
