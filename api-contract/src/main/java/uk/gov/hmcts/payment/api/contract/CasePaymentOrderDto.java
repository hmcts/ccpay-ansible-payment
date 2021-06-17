package uk.gov.hmcts.payment.api.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CasePaymentOrderDto {
    private UUID id;
    private LocalDateTime createdTimestamp;
    private Long caseId;
    private String action;
    private String responsibleParty;
    private String orderReference;
}
