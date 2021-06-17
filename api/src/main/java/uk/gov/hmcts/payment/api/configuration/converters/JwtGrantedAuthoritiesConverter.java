package uk.gov.hmcts.payment.api.configuration.converters;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import uk.gov.hmcts.payment.api.configuration.IdamRepository;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.ACCESS_TOKEN;

/**
 * Class is designed to fetch authorities from access token
 */

@Component
public class JwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String TOKEN_NAME = "tokenName";

    private final IdamRepository idamRepository;

    @Autowired
    public JwtGrantedAuthoritiesConverter(IdamRepository idamRepository) {
        this.idamRepository = idamRepository;
    }

    /**
     * Method responsible to extract authorities from access token received
     * @param jwt
     * @return
     */

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (jwt.containsClaim(TOKEN_NAME) && jwt.getClaim(TOKEN_NAME).equals(ACCESS_TOKEN)) {
            UserInfo userInfo = idamRepository.getUserInfo(jwt.getTokenValue());
            authorities = extractAuthorityFromClaims(userInfo.getRoles());
        }
        return authorities;
    }

    /**
     * Method responsible to get stream of authorities based on claims
     * @param roles
     * @return
     */
    private List<GrantedAuthority> extractAuthorityFromClaims(List<String> roles) {

        if (!Optional.ofNullable(roles).isPresent()){
            throw new InsufficientAuthenticationException("No roles can be extracted from user " +
                "most probably due to insufficient scopes provided");
        }
        return roles.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

}
