package uk.gov.hmcts.payment.functional.idam;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.idam.models.User;
import uk.gov.hmcts.reform.idam.client.models.TokenExchangeResponse;
import uk.gov.hmcts.reform.idam.client.models.test.CreateUserRequest;
import uk.gov.hmcts.reform.idam.client.models.test.UserGroup;
import uk.gov.hmcts.reform.idam.client.models.test.UserRole;

import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Service
public class IdamService {
    public static final String CMC_CITIZEN_GROUP = "cmc-private-beta";
    public static final String CMC_CASE_WORKER_GROUP = "caseworker";

    public static final String BEARER = "Bearer ";
    public static final String GRANT_TYPE = "password";
    public static final String SCOPES = "openid profile roles";

    private final IdamApi idamApi;
    private final TestConfigProperties testConfig;

    private static final Logger LOG = LoggerFactory.getLogger(IdamService.class);

    @Autowired
    public IdamService(TestConfigProperties testConfig) {
        this.testConfig = testConfig;
        idamApi = Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(IdamApi.class, testConfig.getIdamApiUrl());
    }


    public User createUserWith(String userGroup, String... roles) {
        String email = nextUserEmail();
        CreateUserRequest userRequest = userRequest(email, userGroup, roles);
        LOG.info("idamApi : " + idamApi.toString());
        LOG.info("userRequest : " + userRequest);
        try {
            idamApi.createUser(userRequest);
        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }

        String accessToken = authenticateUser(email, testConfig.getTestUserPassword());

        return User.userWith()
            .authorisationToken(accessToken)
            .email(email)
            .build();
    }

    public String authenticateUser(String username, String password) {
        String authorisation = username + ":" + password;
        String base64Authorisation = Base64.getEncoder().encodeToString(authorisation.getBytes());

        LOG.info("username : " + username);
        LOG.info("password : " + password);
        LOG.info("base64Authorisation : " + base64Authorisation);
        LOG.info("testConfig.getOauth2().getClientId() : " + testConfig.getOauth2().getClientId());
        LOG.info("testConfig.getOauth2().getRedirectUrl() : " + testConfig.getOauth2().getRedirectUrl());

        try {
            TokenExchangeResponse tokenExchangeResponse = idamApi.exchangeCode(
                username,
                password,
                SCOPES,
                GRANT_TYPE,
                testConfig.getOauth2().getClientId(),
                testConfig.getOauth2().getClientSecret(),
                testConfig.getOauth2().getRedirectUrl()
            );

            return BEARER + tokenExchangeResponse.getAccessToken();
        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }
        return null;
    }


    private CreateUserRequest userRequest(String email, String userGroup, String[] roles) {
        return CreateUserRequest.builder()
            .email(email)
            .password(testConfig.getTestUserPassword())
            .roles(Stream.of(roles)
                .map(UserRole::new)
                .collect(toList()))
            .userGroup(new UserGroup(userGroup))
            .build();
    }

    private String nextUserEmail() {
        return String.format(testConfig.getGeneratedUserEmailPattern(), UUID.randomUUID().toString());
    }
}
