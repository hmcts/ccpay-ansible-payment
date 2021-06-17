package uk.gov.hmcts.payment.api.configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.gov.hmcts.payment.api.configuration.security.ServicePaymentFilter;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
@RunWith(MockitoJUnitRunner.class)
public class ServicePaymentFilterTest {


    @Mock
    SecurityUtils securityUtils;

    ServicePaymentFilter servicePaymentFilter;

    @Mock
    AuthTokenValidator authTokenValidator;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @Before
    public void setup() {

        servicePaymentFilter = new ServicePaymentFilter(authTokenValidator);
        request = mock(HttpServletRequest.class);

        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @Test
    public void shouldReturnServiceName() throws Exception {

        when(request.getHeader("ServiceAuthorization")).thenReturn("123");
        when(authTokenValidator.getServiceName(anyString())).thenReturn("divorce");
        servicePaymentFilter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    public void shouldReturnNothingWhenTokenIsNull() throws Exception {

        servicePaymentFilter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

}
