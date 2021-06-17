package uk.gov.hmcts.payment.api.configuration.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
/**
 * Custom filter responsible for getting the serviceName
 */
public class ServicePaymentFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(ServicePaymentFilter.class);
    private final AuthTokenValidator authTokenValidator;
    private String serviceName;

    public ServicePaymentFilter(AuthTokenValidator authTokenValidator) {
        super();
        this.authTokenValidator = authTokenValidator;
    }
    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String token = request.getHeader("ServiceAuthorization");
        if (token != null) {
            String bearerToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
            this.serviceName = authTokenValidator.getServiceName(bearerToken);
        }
        filterChain.doFilter(request, response);
    }

    public String getServiceName() {
        LOG.info("Service Name", this.serviceName);
        return this.serviceName;
    }
}
