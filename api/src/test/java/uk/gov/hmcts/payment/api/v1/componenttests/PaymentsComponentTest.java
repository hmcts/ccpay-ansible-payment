package uk.gov.hmcts.payment.api.v1.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.api.v1.contract.CreatePaymentRequestDto;
import uk.gov.hmcts.payment.api.v1.contract.PaymentOldDto;
import uk.gov.hmcts.payment.api.v1.contract.PaymentOldDto.LinksDto;
import uk.gov.hmcts.payment.api.v1.contract.PaymentOldDto.StateDto;
import uk.gov.hmcts.payment.api.v1.contract.RefundPaymentRequestDto;
import uk.gov.hmcts.payment.api.v1.model.PaymentOld;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.String.format;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.v1.contract.CreatePaymentRequestDto.createPaymentRequestDtoWith;
import static uk.gov.hmcts.payment.api.v1.contract.PaymentOldDto.paymentDtoWith;
import static uk.gov.hmcts.payment.api.v1.contract.RefundPaymentRequestDto.refundPaymentRequestDtoWith;
import static uk.gov.hmcts.payment.api.v1.model.PaymentOld.paymentWith;

public class PaymentsComponentTest extends TestUtil {

    private static final String USER_ID = "3";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    protected CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }


    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, objectMapper);

    }

    @Test
    public void createPaymentValidationRules() throws Exception {
        CreatePaymentRequestDto validRequest = createPaymentRequestDtoWith()
            .amount(100)
            .description("description")
            .reference("reference")
            .returnUrl("https://returnUrl.hmcts.net")
            .build();

        tryCreateAndExpect(validRequest.withAmount(null), "amount: must not be null");
        tryCreateAndExpect(validRequest.withAmount(0), "amount: must be greater than or equal to 1");
        tryCreateAndExpect(validRequest.withReference(null), "reference: must not be empty");
        tryCreateAndExpect(validRequest.withReference(""), "reference: must not be empty");
        tryCreateAndExpect(validRequest.withDescription(null), "description: must not be empty");
        tryCreateAndExpect(validRequest.withDescription(""), "description: must not be empty");
        tryCreateAndExpect(validRequest.withReturnUrl(null), "returnUrl: must not be empty");
        tryCreateAndExpect(validRequest.withReturnUrl(""), "returnUrl: must not be empty");
        tryCreateAndExpect(validRequest.withReturnUrl("invalid"), "returnUrl: must be a valid URL");
    }

    private void tryCreateAndExpect(CreatePaymentRequestDto requestBody, String expectedContent) throws Exception {
        restActions
            .post(format("/users/%s/payments", USER_ID), requestBody)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().string(expectedContent));
    }

    //@Test
    public void getExistingPaymentShouldReturn200AndBody() throws Exception {
        stubFor(get(urlPathMatching("/v1/payments/GOV_PAY_ID"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(contentsOf("gov-pay-responses/status-created.json"))
                .withHeader("Content-Type", "application/json")
            ));

        PaymentOld paymentOld = db.create(validPaymentWith().govPayId("GOV_PAY_ID"));

        restActions
            .get(format("/users/%s/payments/%s", USER_ID, paymentOld.getId()))
            .andExpect(status().isOk())
            .andExpect(body().isEqualTo(
                paymentDtoWith()
                    .id(paymentOld.getId().toString())
                    // as defined in .json file
                    .state(new StateDto("created", false))
                    .amount(12000)
                    .description("New passport application")
                    .reference("PaymentOld reference")
                    .dateCreated(paymentOld.getDateCreated())
                    .links(new LinksDto(
                        new PaymentOldDto.LinkDto("https://www.payments.service.gov.uk/secure/7f4adfaa-d834-4657-9c16-946863655bb2", "GET"),
                        new PaymentOldDto.LinkDto(String.format("http://localhost/users/%s/payments/%s/cancel", USER_ID, paymentOld.getId()), "POST")
                    ))
                    .build()
            ));
    }

    //@Test
    public void getUnknownPaymentShouldReturn404() throws Exception {
        restActions.get(format("/users/%s/payments/99999", USER_ID))
            .andExpect(status().isNotFound());
    }

    //@Test
    public void cancelExistingPaymentShouldReturn204() throws Exception {
        stubFor(get(urlPathMatching("/v1/payments/GOV_PAY_ID"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(contentsOf("gov-pay-responses/status-created.json"))
                .withHeader("Content-Type", "application/json")
            ));

        stubFor(post(urlPathMatching("/v1/payments/GOV_PAY_ID/cancel"))
            .willReturn(aResponse()
                .withStatus(204)
                .withHeader("Content-Type", "application/json")
            ));

        PaymentOld paymentOld = db.create(validPaymentWith().govPayId("GOV_PAY_ID").amount(100));

        restActions.post(format("/users/%s/payments/%s/cancel", USER_ID, paymentOld.getId()))
            .andExpect(status().is(204));
    }

    //@Test
    public void cancelPaymentFailureShouldReturn400() throws Exception {
        stubFor(post(urlPathMatching("/v1/payments/GOV_PAY_ID/cancel"))
            .willReturn(aResponse()
                .withStatus(400)
                .withBody("{ \"code\": \"P0501\", \"description\": \"Cancellation of paymentOld failed\" }")
                .withHeader("Content-Type", "application/json")
            ));

        PaymentOld paymentOld = db.create(validPaymentWith().govPayId("GOV_PAY_ID"));

        restActions.post(format("/users/%s/payments/%s/cancel", USER_ID, paymentOld.getId()))
            .andExpect(status().is(400));
    }

    //@Test
    public void refundPaymentValidationRules() throws Exception {
        RefundPaymentRequestDto validRequest = refundPaymentRequestDtoWith()
            .amount(100)
            .refundAmountAvailable(200)
            .build();

        tryRefundAndExpect(validRequest.withAmount(null), "amount: may not be null");
        tryRefundAndExpect(validRequest.withAmount(0), "amount: must be greater than or equal to 1");
        tryRefundAndExpect(validRequest.withRefundAmountAvailable(null), "refundAmountAvailable: may not be null");
        tryRefundAndExpect(validRequest.withRefundAmountAvailable(0), "refundAmountAvailable: must be greater than or equal to 1");
    }

    private void tryRefundAndExpect(RefundPaymentRequestDto requestBody, String expectedContent) throws Exception {
        restActions.post(format("/users/%s/payments/1/refunds", USER_ID), requestBody)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().string(expectedContent));
    }

    //@Test
    public void refundPaymentShouldReturn201OnSuccess() throws Exception {
        stubFor(get(urlPathMatching("/v1/payments/GOV_PAY_ID"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(contentsOf("gov-pay-responses/status-created.json"))
                .withHeader("Content-Type", "application/json")
            ));

        stubFor(post(urlPathMatching("/v1/payments/GOV_PAY_ID/refunds"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
            ));

        PaymentOld paymentOld = db.create(validPaymentWith().govPayId("GOV_PAY_ID"));

        restActions
            .post(format("/users/%s/payments/%s/refunds", USER_ID, paymentOld.getId()), refundPaymentRequestDtoWith().amount(100).refundAmountAvailable(100).build())
            .andExpect(status().is(201));
    }

    //@Test
    public void refundPaymentShouldReturn412IfAmountMismatch() throws Exception {
        stubFor(post(urlPathMatching("/v1/payments/GOV_PAY_ID/refunds"))
            .willReturn(aResponse()
                .withStatus(412)
                .withBody("{ \"code\": \"P0604\", \"description\": \"Refund amount available mismatch\" }")
                .withHeader("Content-Type", "application/json")
            ));

        PaymentOld paymentOld = db.create(validPaymentWith().govPayId("GOV_PAY_ID"));

        restActions
            .post(format("/users/%s/payments/%s/refunds", USER_ID, paymentOld.getId()), refundPaymentRequestDtoWith().amount(100).refundAmountAvailable(100).build())
            .andExpect(status().is(412));
    }

    private PaymentOld.PaymentOldBuilder validPaymentWith() {
        return paymentWith()
            .userId(USER_ID)
            .amount(100)
            .status("status")
            .govPayId("paymentId")
            .description("description")
            .email("email@email.com")
            .reference("reference")
            .returnUrl("returnUrl")
            .cancelUrl(resolvePlaceholders("${gov.pay.url}/GOV_PAY_ID/cancel"))
            .refundsUrl(resolvePlaceholders("${gov.pay.url}/GOV_PAY_ID/refunds"))
            .finished(true);
    }

}
