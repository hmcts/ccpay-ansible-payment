package uk.gov.hmcts.payment.api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.casepaymentorders.client.CpoServiceClient;
import uk.gov.hmcts.payment.casepaymentorders.client.dto.CpoGetResponse;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

@Service
public class CasePaymentOrdersServiceImpl implements CasePaymentOrdersService {
    private final CpoServiceClient cpoServiceClient;
    private final AuthTokenGenerator authTokenGenerator;

    @Autowired
    public CasePaymentOrdersServiceImpl(CpoServiceClient cpoServiceClient,
                                        AuthTokenGenerator authTokenGenerator) {
        this.cpoServiceClient = cpoServiceClient;
        this.authTokenGenerator = authTokenGenerator;
    }

    @Override
    public CpoGetResponse getCasePaymentOrders(String caseIds, String pageNumber, String pageSize,
                                               String authorization) {
        return cpoServiceClient.getCasePaymentOrders(null, caseIds, pageNumber, pageSize, authorization,
                                                     authTokenGenerator.generate());
    }
}
