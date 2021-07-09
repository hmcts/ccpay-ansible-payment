package uk.gov.hmcts.payment.api.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.payment.casepaymentorders.client.CpoServiceClient;
import uk.gov.hmcts.payment.casepaymentorders.client.dto.CpoGetResponse;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


public class CasePaymentOrdersServiceImplTest {

    public static final String S2S_TOKEN = "s2sToken";
    public static final String AUTH_TOKEN = "authToken";
    public static final String CASE_IDS = "caseId1, caseId2";
    public static final String PAGE_NUMBER = "2";
    public static final String PAGE_SIZE = "5";

    @Mock
    private AuthTokenGenerator authTokenGenerator;
    @Mock
    private CpoServiceClient cpoServiceClient;

    @InjectMocks
    private CasePaymentOrdersServiceImpl casePaymentOrdersService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getCasePaymentOrdersShouldDelegateToCpoServiceClient() {
        CpoGetResponse clientResponse = new CpoGetResponse();
        given(authTokenGenerator.generate()).willReturn(S2S_TOKEN);
        given(cpoServiceClient.getCasePaymentOrders(null, CASE_IDS, PAGE_NUMBER, PAGE_SIZE, AUTH_TOKEN, S2S_TOKEN))
            .willReturn(clientResponse);

        CpoGetResponse response = casePaymentOrdersService
            .getCasePaymentOrders(CASE_IDS, PAGE_NUMBER, PAGE_SIZE, AUTH_TOKEN);

        verify(cpoServiceClient).getCasePaymentOrders(null, CASE_IDS, PAGE_NUMBER, PAGE_SIZE, AUTH_TOKEN, S2S_TOKEN);
        assertEquals(response, clientResponse);
    }
}
