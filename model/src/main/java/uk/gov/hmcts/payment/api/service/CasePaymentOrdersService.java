package uk.gov.hmcts.payment.api.service;

import uk.gov.hmcts.payment.casepaymentorders.client.dto.CpoGetResponse;

public interface CasePaymentOrdersService {
    CpoGetResponse getCasePaymentOrders(String caseIds, String page, String size, String authorization);
}
