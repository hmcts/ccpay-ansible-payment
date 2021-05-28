package uk.gov.hmcts.payment.api.service.govpay;

import com.google.common.collect.ImmutableMap;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.PaymentServiceRequest;
import uk.gov.hmcts.payment.api.external.client.GovPayClient;
import uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment;
import uk.gov.hmcts.payment.api.external.client.dto.Link;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.service.DelegatingPaymentService;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayAuthUtil;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayKeyRepository;

import java.util.List;


@Service
public class GovPayDelegatingPaymentService implements DelegatingPaymentService<GovPayPayment, String> {
    private static final Logger LOG = LoggerFactory.getLogger(GovPayDelegatingPaymentService.class);
    private final GovPayKeyRepository govPayKeyRepository;
    private final GovPayClient govPayClient;
    private final ServiceIdSupplier serviceIdSupplier;
    private final GovPayAuthUtil govPayAuthUtil;

    @Autowired
    public GovPayDelegatingPaymentService(GovPayKeyRepository govPayKeyRepository, GovPayClient govPayClient, ServiceIdSupplier serviceIdSupplier, GovPayAuthUtil govPayAuthUtil) {
        this.govPayKeyRepository = govPayKeyRepository;
        this.govPayClient = govPayClient;
        this.serviceIdSupplier = serviceIdSupplier;
        this.govPayAuthUtil = govPayAuthUtil;
    }

    @Override
    public GovPayPayment create(PaymentServiceRequest paymentServiceRequest) {
//        String key = keyForService();
        return GovPayPayment.govPaymentWith()
            .amount(10000)
            .state(new uk.gov.hmcts.payment.api.external.client.dto.State("created", false, null, null))
            .description("description")
            .reference("reference")
            .paymentId("paymentId")
            .paymentProvider("sandbox")
            .returnUrl("https://www.google.com")
            .links(GovPayPayment.Links.linksWith().nextUrl(new Link("any", ImmutableMap.of(), "cancelHref", "any")).build())
            .build();
//        LOG.info("Language value in GovPayDelegatingPaymentService: {}", paymentServiceRequest.getLanguage());
//        return govPayClient.createPayment(key, new CreatePaymentRequest(paymentServiceRequest.getAmount().movePointRight(2).intValue(),
//            paymentServiceRequest.getPaymentReference(), paymentServiceRequest.getDescription(),
//            paymentServiceRequest.getReturnUrl(),paymentServiceRequest.getLanguage()));
    }

    @Override
    public GovPayPayment update(PaymentServiceRequest paymentServiceRequest) {
        return null;
    }

    @Override
    public GovPayPayment retrieve(@NonNull String id) {
        return govPayClient.retrievePayment(keyForService(), id);
    }

    @Override
    public GovPayPayment retrieve(@NonNull String id, @NonNull String service) {
        return govPayClient.retrievePayment(keyForService(service), id);
    }

    @Override
    public List<GovPayPayment> search(PaymentSearchCriteria searchCriteria) {
        return null;
    }

    @Override
    public void cancel(String cancelUrl) {
        govPayClient.cancelPayment(keyForService(), cancelUrl);
    }

    @Override
    public List<Payment> searchByCriteria(PaymentSearchCriteria searchCriteria) {
        return null;
    }

    private String hrefFor(Link link) {
        if (link == null) {
            throw new UnsupportedOperationException("Requested action is not available for the payment");
        }

        return link.getHref();
    }

    private String keyForService(String service) {
        return govPayAuthUtil.getServiceToken(service);
    }

    private String keyForService() {
        return govPayKeyRepository.getKey(serviceIdSupplier.get());
    }
}
