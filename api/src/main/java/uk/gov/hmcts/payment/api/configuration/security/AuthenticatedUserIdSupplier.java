package uk.gov.hmcts.payment.api.configuration.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.configuration.SecurityUtils;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

@Component
public class AuthenticatedUserIdSupplier implements UserIdSupplier {

    @Autowired
    SecurityUtils securityUtils;

    @Override
    public String get() {

        UserInfo userInfo = securityUtils.getUserInfo();

        return userInfo != null ? userInfo.getUid() : null;
    }
}
