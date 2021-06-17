package uk.gov.hmcts.payment.api.v1.componenttests.sugar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.MultiValueMap;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;
;

import static org.springframework.http.MediaType.*;

public class RestActions {
    private final HttpHeaders httpHeaders = new HttpHeaders();
    private final MockMvc mvc;
    private final ObjectMapper objectMapper;

    public static final String AUTHORISATION = "Authorization";
    public static final String SERVICE_AUTHORISATION = "ServiceAuthorization";

    public RestActions(MockMvc mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    public RestActions withAuthorizedService(String serviceId) {
        String token = "Bearer "+serviceId+ UUID.randomUUID().toString();
        httpHeaders.add(SERVICE_AUTHORISATION, token);
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

    public RestActions withAuthorizedUser() {
        String token = UUID.randomUUID().toString();
        httpHeaders.add(AUTHORISATION, token);
        return this;
    }

    public ResultActions get(String urlTemplate) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.get(urlTemplate)
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)
            .headers(httpHeaders))
        );
    }

    public ResultActions get(String urlTemplate, MultiValueMap<String, String> params) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.get(urlTemplate)
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)
            .headers(httpHeaders)
            .params(params))
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

    public ResultActions post(String urlTemplate, String requestBody) {
        return translateException(() -> mvc.perform(MockMvcRequestBuilders.post(urlTemplate)
            .headers(httpHeaders)
            .content(requestBody)
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

    public ResultActions postWithMultiPartFileData(String urlTemplate, MockMultipartFile mockMultipartFile
        , String paramName, String paramValue) {

        return translateException(() -> mvc.perform(MockMvcRequestBuilders
            .multipart(urlTemplate)
            .file(mockMultipartFile)
            .headers(httpHeaders)
            .param(paramName, paramValue)
            .contentType(MULTIPART_FORM_DATA_VALUE)
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

    private String toJson(Object obj) {
        return translateException(() -> objectMapper.writeValueAsString(obj));
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

