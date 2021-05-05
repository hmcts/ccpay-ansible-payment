package uk.gov.hmcts.payment.api.controllers;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.controllers.PaymentReferenceDataController;
import uk.gov.hmcts.payment.api.model.LegacySite;
import uk.gov.hmcts.payment.api.model.LegacySiteRepository;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentChannelRepository;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentMethodRepository;
import uk.gov.hmcts.payment.api.model.PaymentProvider;
import uk.gov.hmcts.payment.api.model.PaymentProviderRepository;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.PaymentStatusRepository;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;


@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class PaymentReferenceDataControllerTest {

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @InjectMocks
    private PaymentReferenceDataController paymentReferenceDataController;

    @Mock
    private LegacySiteRepository legacySiteRepository;

    @Mock
    private PaymentStatusRepository paymentStatusRepository;

    @Mock
    private PaymentProviderRepository paymentProviderRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private PaymentChannelRepository paymentChannelRepository;

    @Test
    public void testFindAllPaymentChannels() throws Exception {

        List<PaymentChannel> paymentChannelMock = Collections.singletonList(new PaymentChannel("card", "card payment"));
        when(paymentChannelRepository.findAll()).thenReturn(paymentChannelMock);
        List<PaymentChannel> paymentChannelMockResponse = paymentReferenceDataController.findAllPaymentChannels();
        assertEquals(paymentChannelMock, paymentChannelMockResponse);
    }

    @Test
    public void testFindAllPaymentMethods() throws Exception {

        List<PaymentMethod> paymentMethodMock = Collections.singletonList(new PaymentMethod("online", "card payment"));
        when(paymentMethodRepository.findAll()).thenReturn(paymentMethodMock);
        List<PaymentMethod> paymentMethodMockResponse = paymentReferenceDataController.findAllPaymentMethods();
        assertEquals(paymentMethodMock, paymentMethodMockResponse);
    }

    @Test
    public void testFindAllPaymentProviders() throws Exception {

        List<PaymentProvider> paymentProviderMock = Collections.singletonList(new PaymentProvider().GOV_PAY);
        when(paymentProviderRepository.findAll()).thenReturn(paymentProviderMock);
        List<PaymentProvider> paymentProviderMockResponse = paymentReferenceDataController.findAllPaymentProviders();
        assertEquals(paymentProviderMock, paymentProviderMockResponse);
    }

    @Test
    public void testFindAllPaymentStatuses() throws Exception {

        List<PaymentStatus> paymentStatusMock = Collections.singletonList(new PaymentStatus().CREATED);
        when(paymentStatusRepository.findAll()).thenReturn(paymentStatusMock);
        List<PaymentStatus> paymentStatusMockResponse = paymentReferenceDataController.findAllPaymentStatuses();
        assertEquals(paymentStatusMock, paymentStatusMockResponse);
    }


    @Test
    public void testFindAllLegacySites() throws Exception {

        List<LegacySite> legacySiteMock = Collections.singletonList(new LegacySite("site 2", "Site name 1"));
        when(legacySiteRepository.findAll()).thenReturn(legacySiteMock);
        List<LegacySite> legacySiteMockResponse = paymentReferenceDataController.findAllLegacySites();
        assertEquals(legacySiteMock, legacySiteMockResponse);
    }
}
