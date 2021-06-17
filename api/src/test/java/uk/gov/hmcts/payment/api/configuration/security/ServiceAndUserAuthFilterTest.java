package uk.gov.hmcts.payment.api.configuration.security;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.hmcts.payment.api.configuration.SecurityUtils;
import uk.gov.hmcts.payment.api.configuration.security.authcheckerconfiguration.AuthCheckerConfiguration;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServiceAndUserAuthFilterTest {
    @Mock
    private SecurityUtils securityUtils;
    private ServiceAndUserAuthFilter filter;
    private MockHttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @Mock
    SecurityContext securityContext;

    @Mock
    Authentication authentication;

    @Before
    public void setUp() {
        AuthCheckerConfiguration config = new AuthCheckerConfiguration();
        filter = new ServiceAndUserAuthFilter(config.userIdExtractor(),
            config.authorizedRolesExtractor(), securityUtils);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();

        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturn200ResponseWhenRoleMatches() throws Exception {
        request.setRequestURI("/bulk-scan-payments/");
        when(SecurityContextHolder.getContext().getAuthentication()).thenReturn(getJWTAuthenticationTokenBasedOnRoles("payments"));
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUID_Roles("user123","payments"));

        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturn403ResponseWhenRolesdontMatche() throws Exception {
        request.setRequestURI("/bulk-scan-payments/");
        when(SecurityContextHolder.getContext().getAuthentication()).thenReturn(getJWTAuthenticationTokenBasedOnRoles("payments"));
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUID_Roles("user123","role"));

        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    public void shouldReturn403ResponseWhenRoleIsInvalid() throws Exception {
        request.setRequestURI("/cases/");
        when(SecurityContextHolder.getContext().getAuthentication()).thenReturn(getJWTAuthenticationTokenBasedOnRoles("payments"));
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUID_Roles("user123","payments-invalid-role"));

        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        Assert.assertTrue((StringUtils.containsIgnoreCase(((MockHttpServletResponse)response).getErrorMessage(),
            "Access Denied")));
    }

    @Test
    public void shouldReturn403RWhenNoRolesPresentForUserInfo() throws Exception {
        request.setRequestURI("/cases/");
        when(SecurityContextHolder.getContext().getAuthentication()).thenReturn(getJWTAuthenticationTokenBasedOnRoles("payments"));
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUID_Roles("user123",null));

        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        Assert.assertTrue((StringUtils.containsIgnoreCase(((MockHttpServletResponse)response).getErrorMessage(),
            "Access Denied")));
    }

    public static UserInfo getUserInfoBasedOnUID_Roles(String UID, String roles){
        return UserInfo.builder()
            .uid(UID)
            .roles(Arrays.asList(roles))
            .build();
    }

    @SuppressWarnings("unchecked")
    private JwtAuthenticationToken getJWTAuthenticationTokenBasedOnRoles(String authority) {
        List<String> stringGrantedAuthority = new ArrayList();
        stringGrantedAuthority.add(authority);

        Map<String,Object> claimsMap = new HashMap<>();
        claimsMap.put("roles", stringGrantedAuthority);

        Map<String,Object> headersMap = new HashMap<>();
        headersMap.put("authorisation","test-token");

        Jwt jwt = new Jwt("test_token",null,null,headersMap,claimsMap);
        return new JwtAuthenticationToken(jwt);
    }





}
