package uk.gov.hmcts.payment.api.configuration;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.hmcts.payment.api.configuration.converters.JwtGrantedAuthoritiesConverter;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JwtGrantedAuthoritiesConverterTest {

    @Mock
    private IdamRepository idamRepository;

    @InjectMocks
    private JwtGrantedAuthoritiesConverter converter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        }

    @Test
    public void shouldReturnEmptyAuthorities() {
        Jwt jwt = Mockito.mock(Jwt.class);
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertNotNull(authorities);
        assertEquals(0, authorities.size());
    }

    @Test
    public void shouldReturnEmptyAuthoritiesWhenClaimNotAvailable() {
        Jwt jwt = Mockito.mock(Jwt.class);
        when(jwt.containsClaim(anyString())).thenReturn(false);
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertNotNull(authorities);
        assertEquals(0, authorities.size());
    }

    @Test
    public void shouldReturnEmptyAuthoritiesWhenClaimValueNotEquals() {
        Jwt jwt = Mockito.mock(Jwt.class);
        when(jwt.containsClaim(anyString())).thenReturn(true);
        when(jwt.getClaim(anyString())).thenReturn("Test");
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertNotNull(authorities);
        assertEquals(0, authorities.size());
    }

    @Test
    public void shouldReturnEmptyAuthoritiesWhenIdamReturnsNoUsers() {
        Jwt jwt = Mockito.mock(Jwt.class);
        when(jwt.containsClaim(anyString())).thenReturn(true);
        when(jwt.getClaim(anyString())).thenReturn("access_token");
        when(jwt.getTokenValue()).thenReturn("access_token");
        UserInfo userInfo = mock(UserInfo.class);
        List roles = new ArrayList();
        when(userInfo.getRoles()).thenReturn(roles);
        when(idamRepository.getUserInfo(anyString())).thenReturn(userInfo);
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertNotNull(authorities);
        assertEquals(0, authorities.size());
    }

    @Test
    public void shouldReturnAuthoritiesWhenIdamReturnsUserRoles() {
        Jwt jwt = Mockito.mock(Jwt.class);
        when(jwt.containsClaim(anyString())).thenReturn(true);
        when(jwt.getClaim(anyString())).thenReturn("access_token");
        when(jwt.getTokenValue()).thenReturn("access_token");
        UserInfo userInfo = mock(UserInfo.class);
        List roles = new ArrayList();
        roles.add("payments");
        when(userInfo.getRoles()).thenReturn(roles);
        when(idamRepository.getUserInfo(anyString())).thenReturn(userInfo);
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertNotNull(authorities);
        assertEquals(1, authorities.size());
    }
}
