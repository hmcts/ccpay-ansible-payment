package uk.gov.hmcts.payment.functional.dsl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.TelephonyCardPaymentsRequest;
import uk.gov.hmcts.payment.api.contract.TelephonyCardPaymentsResponse;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.dto.BulkScanPaymentRequest;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupResponse;
import uk.gov.hmcts.payment.api.dto.PaymentRecordRequest;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.api.dto.TelephonyCallbackDto;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
@Scope("prototype")
public class PaymentsTestDsl {
    private final Map<String, String> headers = new HashMap<>();

    @Value("${test.url}")
    private String baseURL;

    @Autowired
    private S2sTokenService serviceTokenFactory;

    @Autowired
    private IdamService idamService;

    private Response response;

    public PaymentGivenDsl given() {
        return new PaymentGivenDsl();
    }

    public class PaymentGivenDsl {

        public PaymentGivenDsl userToken(String userToken) {
            headers.put("Authorization", userToken);
            return this;
        }

        public PaymentGivenDsl s2sToken(String serviceToken) {
            headers.put("ServiceAuthorization", serviceToken);
            return this;
        }

        public PaymentGivenDsl returnUrl(String url) {
            headers.put("return-url", url);
            return this;
        }

        public PaymentGivenDsl serviceCallBackUrl(String url) {
            headers.put("service-callback-url", url);
            return this;
        }

        public PaymentGivenDsl setFF4Jfeature(String featureName, boolean value) {
            String path = "/api/ff4j/store/features/" + featureName + (value ? "enable" : "disable");
            RestAssured.given().relaxedHTTPSValidation().baseUri(baseURL).contentType(ContentType.JSON).headers(headers)
                .post(path);
            return this;
        }

        public PaymentWhenDsl when() {
            return new PaymentWhenDsl();
        }
    }

    public class PaymentWhenDsl {
        private RequestSpecification newRequest() {
            return RestAssured.given().relaxedHTTPSValidation().baseUri(baseURL).contentType(ContentType.JSON).headers(headers);
        }

        public PaymentWhenDsl getPaymentGroupByReference(String reference) {
            response = newRequest().get("/payment-groups/{reference}", reference);
            return this;
        }

        public PaymentWhenDsl getPayment(String userId, String paymentId) {
            response = newRequest().get("/users/{userToken}/payments/{paymentId}", userId, paymentId);
            return this;
        }

        public PaymentWhenDsl getAccountInfomation(String accountNumber) {
            response = newRequest().get("/accounts/" + accountNumber);
            return this;
        }

        public PaymentWhenDsl getBuildInfo() {
            response = newRequest().get("/info");
            return this;
        }

        public PaymentWhenDsl createCardPayment(CardPaymentRequest cardPaymentRequest) {
            response = newRequest().contentType(ContentType.JSON).body(cardPaymentRequest).post("/card-payments");
            return this;
        }

        public PaymentWhenDsl createTelephonyCardPayment(CardPaymentRequest cardPaymentRequest, String paymentGroupReference) {
            response = newRequest().contentType(ContentType.JSON).body(cardPaymentRequest).post("/payment-groups/{payment-group-reference}/card-payments", paymentGroupReference);
            return this;
        }

        public PaymentWhenDsl createTelephonyPayment(TelephonyCardPaymentsRequest telephonyCardPaymentsRequest, String paymentGroupReference) {
            response = newRequest().contentType(ContentType.JSON).body(telephonyCardPaymentsRequest).post("/payment-groups/{payment-group-reference}/telephony-card-payments", paymentGroupReference);
            return this;
        }

        public PaymentWhenDsl createTelephonyPayment(PaymentRecordRequest paymentRecordRequest) {
            response = newRequest().contentType(ContentType.JSON).body(paymentRecordRequest).post("/payment-records");
            return this;
        }

        public PaymentWhenDsl createUpfrontRemission(RemissionRequest remissionRequest) {
            response = newRequest().contentType(ContentType.JSON).body(remissionRequest).post("/remissions");
            return this;
        }

        public PaymentWhenDsl createRetrospectiveRemission(RemissionRequest remissionRequest, String paymentGroup, Integer feeId) {
            response = newRequest().contentType(ContentType.JSON).body(remissionRequest)
                .post("/payment-groups/{payment-group-reference}/fees/{unique_fee_id}/remissions", paymentGroup, feeId);
            return this;
        }

        public PaymentWhenDsl addNewPaymentGroup(PaymentGroupDto paymentGroupFeeRequest) {
            response = newRequest().contentType(ContentType.JSON).body(paymentGroupFeeRequest)
                .post("/payment-groups");
            return this;
        }

        public PaymentWhenDsl addNewFeeToPaymentGroup(PaymentGroupDto paymentGroupFeeRequest) {
            response = newRequest().contentType(ContentType.JSON).body(paymentGroupFeeRequest)
                .put("/payment-groups");
            return this;
        }

        public PaymentWhenDsl addNewFeeAndPaymentGroup(PaymentGroupDto paymentGroupFeeRequest) {
            response = newRequest().contentType(ContentType.JSON).body(paymentGroupFeeRequest)
                .post("/payment-groups/");
            return this;
        }

        public PaymentWhenDsl createBulkScanPayment(BulkScanPaymentRequest bulkScanPaymentRequest, String paymentGroupReference) {
            response = newRequest().contentType(ContentType.JSON).body(bulkScanPaymentRequest)
                .post("/payment-groups/{payment-group-reference}/bulk-scan-payments", paymentGroupReference );
            return this;
        }

        public PaymentWhenDsl createBulkScanPaymentStrategic(BulkScanPaymentRequest bulkScanPaymentRequest, String paymentGroupReference) {
            response = newRequest().contentType(ContentType.JSON).body(bulkScanPaymentRequest)
                .post("/payment-groups/{payment-group-reference}/bulk-scan-payments-strategic", paymentGroupReference );
            return this;
        }

        public PaymentWhenDsl createBulkScanPaymentWithPaymentGroup(BulkScanPaymentRequest bulkScanPaymentRequest) {
            response = newRequest().contentType(ContentType.JSON).body(bulkScanPaymentRequest)
                .post("/payment-groups/bulk-scan-payments" );
            return this;
        }

        public PaymentWhenDsl addNewFeetoExistingPaymentGroup(PaymentGroupDto paymentGroupFeeRequest, String paymentGroupReference) {
            response = newRequest().contentType(ContentType.JSON).body(paymentGroupFeeRequest)
                .put("/payment-groups/{payment-group-reference}", paymentGroupReference);
            return this;
        }

        public PaymentWhenDsl getRemissions(String paymentGroupReference) {
            response = newRequest().get("/payment-groups/{payment-group-reference}", paymentGroupReference);
            return this;
        }

        public PaymentWhenDsl getPaymentGroups(String ccdCaseNumber) {
            response = newRequest().get("/cases/{ccdcasenumber}/paymentgroups", ccdCaseNumber);
            return this;
        }

        public PaymentWhenDsl updatePaymentStatus(String paymentReference, String status) {
            StringBuilder sb = new StringBuilder("/payments/");
            sb.append(paymentReference);
            sb.append("/status/");
            sb.append(status);

            response = newRequest().contentType(ContentType.JSON).patch(sb.toString());
            return this;
        }

        public PaymentWhenDsl telephonyCallback(TelephonyCallbackDto callbackDto) {
            Map formData = new ObjectMapper().convertValue(callbackDto, Map.class);
            response = newRequest().contentType(ContentType.URLENC.withCharset("UTF-8")).formParams(formData).post("/telephony/callback");
            return this;
        }

        public PaymentWhenDsl enableSearch() {
            response = newRequest().contentType(ContentType.JSON).post("/api/ff4j/store/features/payment-search/enable");
            return this;
        }

        public PaymentWhenDsl getCardPayment(String reference) {
            response = newRequest().get("/card-payments/" + reference);
            return this;
        }

        public PaymentWhenDsl cardPaymentsStatusUpdateJob() {
            response = newRequest().patch("/jobs/card-payments-status-update");
            return this;
        }

        public PaymentWhenDsl searchPaymentsBetweenDates(String startDate, String endDate) {
            if (startDate != null && endDate != null) {
                response = newRequest().get("/payments?start_date=" + startDate + "&end_date=" + endDate);
            } else if (startDate != null) {
                response = newRequest().get("/payments?start_date=" + startDate);
            } else if (endDate != null) {
                response = newRequest().get("/payments?end_date=" + endDate);
            }

            return this;
        }

        public PaymentWhenDsl searchPaymentsBetweenDatesPaymentMethodServiceName(String startDate, String endDate, String paymentMethod) {
            if (startDate != null && endDate != null) {
                response = newRequest().get("/payments?start_date=" + startDate + "&end_date=" + endDate + "&service_name=DIGITAL_BAR" + "&payment_method=" + paymentMethod);
            } else if (startDate != null) {
                response = newRequest().get("/payments?start_date=" + startDate + "&service_name=DIGITAL_BAR" + "&payment_method=" + paymentMethod);
            } else if (endDate != null) {
                response = newRequest().get("/payments?end_date=" + endDate + "&service_name=DIGITAL_BAR" + "&payment_method=" + paymentMethod);
            }

            return this;
        }

        public PaymentWhenDsl searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(String startDate, String endDate, String paymentMethod) {
            if (startDate != null && endDate != null) {
                response = newRequest().get("/reconciliation-payments?start_date=" + startDate + "&end_date=" + endDate + "&service_name=DIGITAL_BAR" + "&payment_method=" + paymentMethod);
            } else if (startDate != null) {
                response = newRequest().get("/reconciliation-payments?start_date=" + startDate + "&service_name=DIGITAL_BAR" + "&payment_method=" + paymentMethod);
            } else if (endDate != null) {
                response = newRequest().get("/reconciliation-payments?end_date=" + endDate + "&service_name=DIGITAL_BAR" + "&payment_method=" + paymentMethod);
            }

            return this;
        }

        public PaymentWhenDsl searchPaymentsByServiceBetweenDates(Service serviceName, String startDate, String endDate) {
            StringBuilder sb = new StringBuilder("/payments?");
            sb.append("start_date=").append(startDate);
            sb.append("&end_date=").append(endDate);
            sb.append("&service_name=").append(serviceName);
            response = newRequest().get(sb.toString());

            return this;
        }

        public PaymentThenDsl then() {
            return new PaymentThenDsl();
        }
    }

    public class PaymentThenDsl {
        public PaymentThenDsl forbidden() {
            response.then().statusCode(403);
            return this;
        }

        public PaymentThenDsl notFound() {
            response.then().statusCode(404);
            return this;
        }

        public PaymentThenDsl noContent() {
            response.then().statusCode(204);
            return this;
        }

        public PaymentThenDsl ok() {
            response.then().statusCode(200);
            return this;
        }

        public PaymentThenDsl created() {
            response.then().statusCode(201);
            return this;
        }

        public PaymentGroupDto createdWithContent(int statusCode) {
            return response.then().statusCode(statusCode).extract().as(PaymentGroupDto.class);
        }

        public PaymentThenDsl created(Consumer<PaymentDto> payment) {
            PaymentDto paymentDto = response.then().statusCode(201).extract().as(PaymentDto.class);
            payment.accept(paymentDto);
            return this;
        }

        public <T> PaymentThenDsl gotCreated(Class<T> type, Consumer<T> assertions) {
            T dto = response.then().statusCode(201).extract().as(type);
            assertions.accept(dto);
            return this;
        }

        public <T> PaymentThenDsl got(Class<T> type, Consumer<T> assertions) {
            T dto = response.then().statusCode(200).extract().as(type);
            assertions.accept(dto);
            return this;
        }

        public PaymentThenDsl cancelled() {
            response.then().statusCode(204);
            return this;
        }

        public PaymentThenDsl get(Consumer<PaymentDto> paymentAssertions) {
            PaymentDto paymentDto = response.then().statusCode(200).extract().as(PaymentDto.class);
            paymentAssertions.accept(paymentDto);
            return this;
        }

        public PaymentDto get() {
            return response.then().statusCode(200).extract().as(PaymentDto.class);
        }

        public PaymentDto getByStatusCode(int statusCode) {
            return response.then().statusCode(statusCode).extract().as(PaymentDto.class);
        }

        public PaymentGroupDto getPaymentGroupDtoByStatusCode(int statusCode) {
            return response.then().statusCode(statusCode).extract().as(PaymentGroupDto.class);
        }

        public AccountDto getAccount() {
            return response.then().statusCode(200).extract().as(AccountDto.class);
        }

        public PaymentThenDsl getPayments(Consumer<PaymentsResponse> paymentsResponseAssertions) {
            PaymentsResponse paymentsResponse = response.then().statusCode(200).extract().as(PaymentsResponse.class);
            paymentsResponseAssertions.accept(paymentsResponse);
            return this;
        }

        public TelephonyCardPaymentsResponse createdTelephoneCardPaymentsResponse() {
            TelephonyCardPaymentsResponse telephonyCardPaymentsResponse = response.then().statusCode(201).extract().as(TelephonyCardPaymentsResponse.class);
            return telephonyCardPaymentsResponse;
        }
        public PaymentsResponse getPayments() {
            PaymentsResponse paymentsResponse = response.then().statusCode(200).extract().as(PaymentsResponse.class);
            return paymentsResponse;
        }

        public PaymentThenDsl getPaymentGroups(Consumer<PaymentGroupResponse> paymentGroupsResponseAssertions) {
            PaymentGroupResponse paymentGroupsResponse = response.then().statusCode(200).extract().as(PaymentGroupResponse.class);
            paymentGroupsResponseAssertions.accept(paymentGroupsResponse);
            return this;
        }

        public PaymentThenDsl validationError(String message) {
            String validationError = response.then().statusCode(422).extract().body().asString();
            Assertions.assertThat(validationError).isEqualTo(message);
            return this;
        }

        public Response validationErrorFor400() {
            return response.then().statusCode(400).extract().response();
        }

    }

}
