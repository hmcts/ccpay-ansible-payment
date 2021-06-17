package uk.gov.hmcts.payment.api.service;

import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.bind.annotation.ExceptionHandler;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.payment.api.util.PaymentMethodType.CARD;

@RunWith(MockitoJUnitRunner.class)
public class PaymentServiceTest {

    @InjectMocks
    private PaymentServiceImpl service;

    @Mock
    private DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService;

    @Test
    public void shouldDelegateSearchToCardPaymentType() {
        // given
        List<PaymentFeeLink> paymentFeeLinks = Collections.emptyList();

        PaymentSearchCriteria searchCriteria =
            PaymentSearchCriteria.searchCriteriaWith()
                .startDate(LocalDateTime.now().minusDays(2).toDate())
                .endDate(LocalDateTime.now().toDate())
                .paymentMethod(CARD.getType())
                .build();

        given(delegatingPaymentService.search(eq(searchCriteria)))
            .willReturn(paymentFeeLinks);
        // when
        List<PaymentFeeLink> result = service.search(searchCriteria);
        // then
        assertThat(result).isSameAs(paymentFeeLinks);
    }

    @Test
    public void shouldPassStartDateWithMidnightTimeForSearch() {
        // given
        PaymentSearchCriteria searchCriteria =
            PaymentSearchCriteria.searchCriteriaWith()
                .startDate(LocalDateTime.now().toDate())
                .endDate(LocalDateTime.now().toDate())
                .paymentMethod(CARD.getType())
                .build();

        // when
        service.search(searchCriteria);
        // then
        verify(delegatingPaymentService).search(eq(searchCriteria));
    }

    @Test
    public void shouldPassEndDateWithMidnightForSearch() {
        // given
        PaymentSearchCriteria searchCriteria =
            PaymentSearchCriteria.searchCriteriaWith()
                .startDate(LocalDateTime.now().toDate())
                .endDate(LocalDateTime.now().plusDays(1).minusSeconds(1).toDate())
                .paymentMethod(CARD.getType())
                .build();

        // when
        service.search(searchCriteria);

        verify(delegatingPaymentService).search(eq(searchCriteria));
    }

    @Test
    public void shouldDelegateSearchToCMCService() {
        // given
        List<PaymentFeeLink> paymentFeeLinks = Collections.emptyList();

        PaymentSearchCriteria searchCriteria =
            PaymentSearchCriteria.searchCriteriaWith()
                .startDate(LocalDateTime.now().minusDays(2).toDate())
                .endDate(LocalDateTime.now().toDate())
                .paymentMethod(CARD.getType())
                .serviceType("cmc")
                .build();

        given(delegatingPaymentService.search(eq(searchCriteria)))
            .willReturn(paymentFeeLinks);
        // when

        List<PaymentFeeLink> result = service.search(searchCriteria);
        // then
        assertThat(result).isSameAs(paymentFeeLinks);
    }

    @Test(expected = PaymentException.class)
    public void shouldThrowExceptionOnInvalidServiceType() {
        String validServiceName = service.getServiceNameByCode("CMC");
        assertEquals("Civil Money Claims",validServiceName);
        service.getServiceNameByCode("Civil");
    }

}
