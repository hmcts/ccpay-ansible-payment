package uk.gov.hmcts.payment.functional.idam;

import feign.Body;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import uk.gov.hmcts.reform.idam.client.models.TokenExchangeResponse;
import uk.gov.hmcts.reform.idam.client.models.test.CreateUserRequest;

public interface IdamApi {

    @RequestMapping(method = RequestMethod.POST, value = "/testing-support/accounts")
    @RequestLine("POST /testing-support/accounts")
    @Headers("Content-Type: application/json")
    void createUser(CreateUserRequest createUserRequest);

    @RequestLine("POST /o/token")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    @Body("username={username}&password={password}&scope={scope}&grant_type={grant_type}&client_id={client_id}&client_secret={client_secret}&redirect_uri={redirect_uri}")
    TokenExchangeResponse exchangeCode(@Param("username") String username,
                                       @Param("password") String password,
                                       @Param("scope") String scope,
                                       @Param("grant_type") String grantType,
                                       @Param("client_id") String clientId,
                                       @Param("client_secret") String clientSecret,
                                       @Param("redirect_uri") String redirectUri);

}
