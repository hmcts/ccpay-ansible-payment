package uk.gov.hmcts.payment.api.configuration.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;


@Component
public class AuthenticatedServiceIdSupplier implements ServiceIdSupplier {

    @Autowired
    ServicePaymentFilter servicePaymentFilter;

    @Override
    public String get() {
        return servicePaymentFilter.getServiceName();
    }

}
