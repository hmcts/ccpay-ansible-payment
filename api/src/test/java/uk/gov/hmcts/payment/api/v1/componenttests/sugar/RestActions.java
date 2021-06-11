package uk.gov.hmcts.payment.api.v1.componenttests.sugar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.reform.auth.checker.core.service.ServiceRequestAuthorizer;
import uk.gov.hmcts.reform.auth.checker.core.user.UserRequestAuthorizer;

import java.util.UUID;

import static org.springframework.http.MediaType.*;

public class RestActions {
    private final HttpHeaders httpHeaders = new HttpHeaders();
    private final MockMvc mvc;
    private final ServiceResolverBackdoor serviceRequestAuthorizer;
    private final UserResolverBackdoor userRequestAuthorizer;
    private final ObjectMapper objectMapper;

    public RestActions(MockMvc mvc, ServiceResolverBackdoor serviceRequestAuthorizer, UserResolverBackdoor userRequestAuthorizer, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.serviceRequestAuthorizer = serviceRequestAuthorizer;
        this.userRequestAuthorizer = userRequestAuthorizer;
        this.objectMapper = objectMapper;
    }

    public RestActions withAuthorizedService(String serviceId) {
        String token = UUID.randomUUID().toString();
        serviceRequestAuthorizer.registerToken(token, serviceId);
        httpHeaders.add(ServiceRequestAuthorizer.AUTHORISATION, token);
        return this;
    }

    public RestActions withUserId(String userId) {
        httpHeaders.add("user-id", userId);
        return this;
    }

    public RestActions withReturnUrl(String returnUrl) {
        httpHeaders.add("return-url", returnUrl);
        return this;
    }

    public RestActions withHeader(String header, String value) {
        httpHeaders.add(header, value);
        return this;
    }

    public RestActions withAuthorizedUser(String userId) {
        String token = UUID.randomUUID().toString();
        userRequestAuthorizer.registerToken(token, userId);
        httpHeaders.add(UserRequestAuthorizer.AUTHORISATION, token);
        return this;
    }

    public ResultActions get(String urlTemplate) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.get(urlTemplate)
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)
            .headers(httpHeaders))
        );
    }

    public ResultActions post(String urlTemplate) {
        return post(urlTemplate, null);
    }

    public ResultActions post(String urlTemplate, Object requestBody) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.post(urlTemplate)
            .headers(httpHeaders)
            .content(toJson(requestBody))
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)));
    }

    public ResultActions postWithFormData(String urlTemplate, String requestBody) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.post(urlTemplate)
            .headers(httpHeaders)
            .content(requestBody)
            .contentType(APPLICATION_FORM_URLENCODED_VALUE)
            .accept(APPLICATION_JSON)));
    }

    public ResultActions patch(String urlTemplate) {
        return patch(urlTemplate, null);
    }

    public ResultActions patch(String urlTemplate, Object requestBody) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.patch(urlTemplate)
            .headers(httpHeaders)
            .content(toJson(requestBody))
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)));
    }


    public ResultActions delete(String urlTemplate) {
        return delete(urlTemplate, null);
    }


    public ResultActions delete(String urlTemplate, Object requestBody) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.delete(urlTemplate)
            .headers(httpHeaders)
            .content(toJson(requestBody))
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)));
    }

    public ResultActions put(String urlTemplate) {
        return put(urlTemplate, null);
    }

    public ResultActions put(String urlTemplate, Object requestBody) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.put(urlTemplate)
            .headers(httpHeaders)
            .content(toJson(requestBody))
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)));
    }

    private String toJson(Object o) {
        return translateException(() -> objectMapper.writeValueAsString(o));
    }

    private <T> T translateException(CallableWithException<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    interface CallableWithException<T> {
        T call() throws Exception;
    }
}
