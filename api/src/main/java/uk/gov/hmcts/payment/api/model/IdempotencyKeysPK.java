package uk.gov.hmcts.payment.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder(builderMethodName = "idempotencyKeysPKWith")
@AllArgsConstructor
@NoArgsConstructor
public class IdempotencyKeysPK implements Serializable {
    private String idempotencyKey;

    private Integer request_hashcode;
}
