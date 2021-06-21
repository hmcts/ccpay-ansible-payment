package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomUtils;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.TelephonyPaymentRequest;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.servicebus.CallbackServiceImpl;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest", "mockcallbackservice"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class TelephonyControllerTest extends PaymentsDataUtil {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @MockBean
    protected CallbackServiceImpl callbackServiceImplMock;

    @Autowired
    protected PaymentDbBackdoor db;
    @Autowired
    private TelephonyRepository telephonyRepository;

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");

    RestActions restActions;

    @MockBean
    private ReferenceDataService referenceDataService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    protected CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, null, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");
    }

    @Test
    public void updateTelephonyPaymentStatusWithSuccess() throws Exception {
        String rawFormData = "orderCurrency=&orderAmount=488.50&orderReference=RC-1519-9028-1909-1435&ppAccountID=1210&" +
            "transactionResult=SUCCESS&transactionAuthCode=test123&transactionID=3045021106&transactionResponseMsg=&" +
            "avsAddress=&avsPostcode=&avsCVN=&cardExpiry=1220&cardLast4=9999&cardType=MASTERCARD&ppCallID=820782890&" +
            "customData1=MOJTest120190124123432&customData2=MASTERCARD&customData3=CreditCard&customData4=";

        String paymentReference = "RC-1519-9028-1909-1435";
        Payment dbPayment = populateTelephonyPaymentToDb(paymentReference, false);

        restActions
            .postWithFormData("/telephony/callback", rawFormData)
            .andExpect(status().isNoContent());

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult result = restActions
            .get("/payments?ccd_case_number=" + dbPayment.getCcdCaseNumber()+"&start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertThat(payments.size()).isEqualTo(1);
        assertEquals(payments.get(0).getPaymentReference(), paymentReference);
        assertThat("success".equalsIgnoreCase(payments.get(0).getStatus()));

    }

    @Test
    public void updateTelephonyPaymentStatus_recordinDBAndRaiseStatusCallbackEvent() throws Exception {
        String rawFormData = "orderCurrency=&orderAmount=488.50&orderReference=RC-1519-9028-1909-1435&ppAccountID=1210&" +
            "transactionResult=DECLINE&transactionAuthCode=test123&transactionID=3045021106&transactionResponseMsg=&" +
            "avsAddress=&avsPostcode=&avsCVN=&cardExpiry=1220&cardLast4=9999&cardType=MASTERCARD&ppCallID=820782890&" +
            "customData1=MOJTest120190124123432&customData2=MASTERCARD&customData3=CreditCard&customData4=";

        String paymentReference = "RC-1519-9028-1909-1435";
        Payment dbPayment = populateTelephonyPaymentToDb(paymentReference, true);

        restActions
            .postWithFormData("/telephony/callback", rawFormData)
            .andExpect(status().isNoContent());

        Optional<TelephonyCallback> optionalCallback = telephonyRepository.findById(paymentReference);
        assertThat(optionalCallback).isNotNull();

        verify(callbackServiceImplMock).callback(dbPayment.getPaymentLink(), dbPayment);
    }

    @Test
    public void updateTelephonyPaymentStatus_throw404IfPaymentNotFound() throws Exception {
        String rawFormData = "orderCurrency=&orderAmount=488.50&orderReference=RC-invalid-reference&ppAccountID=1210&" +
            "transactionResult=SUCCESS&transactionAuthCode=test123&transactionID=3045021106&transactionResponseMsg=&" +
            "avsAddress=&avsPostcode=&avsCVN=&cardExpiry=1220&cardLast4=9999&cardType=MASTERCARD&ppCallID=820782890&" +
            "customData1=MOJTest120190124123432&customData2=MASTERCARD&customData3=CreditCard&customData4=";

        String paymentReference = "RC-1519-9028-1909-1435";
        Payment dbPayment = populateTelephonyPaymentToDb(paymentReference, false);

        restActions
            .postWithFormData("/telephony/callback", rawFormData)
            .andExpect(status().isNotFound());

        verify(callbackServiceImplMock, times(0)).callback(dbPayment.getPaymentLink(), dbPayment);
    }

    @Test
    public void retrieveTelephonyPaymentWithDeclineStatusShouldShowDeclinedInResponse() throws Exception {
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("550.00"))
            .caseReference("Reference")
            .ccdCaseNumber("ccdCaseNumber")
            .description("Description")
            .serviceType("DIVORCE")
            .currency("GBP")
            .siteId("AA00")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("telephony").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("pci pal").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("decline").build())
            .reference("RC-1518-9429-1432-7825")
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("550.00")).version("1").code("FEE0123").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2019-15186162099").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result = restActions
            .get("/card-payments/" + savedPayment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
        assertEquals(paymentDto.getReference(), payment.getReference());
        assertEquals(paymentDto.getStatus(), "Declined");

    }

    @Test
    public void updateTelephonyPaymentStatus_throw400IfBadRequest() throws Exception {
        String rawFormData = "orderReference=RC-invalid-reference&ppAccountID=1210&" +
            "transactionResult=SUCCESS&transactionAuthCode=test123&transactionID=3045021106&transactionResponseMsg=&" +
            "avsAddress=&avsPostcode=&avsCVN=&cardExpiry=1220&cardLast4=9999&cardType=MASTERCARD&ppCallID=820782890&" +
            "customData1=MOJTest120190124123432&customData2=MASTERCARD&customData3=CreditCard&customData4=";

        String paymentReference = "RC-1519-9028-1909-1435";
        Payment dbPayment = populateTelephonyPaymentToDb(paymentReference, false);

        restActions
            .postWithFormData("/telephony/callback", rawFormData)
            .andExpect(status().isBadRequest());

        verify(callbackServiceImplMock, times(0)).callback(dbPayment.getPaymentLink(), dbPayment);
    }

    @Test
    public void updateTelephonyPaymentStatus_ShouldNotBeUpdatedWithDuplicateReq() throws Exception {
        String rawFormData = "orderCurrency=&orderAmount=488.50&orderReference=RC-1519-9028-1909-1435&ppAccountID=1210&" +
            "transactionResult=SUCCESS&transactionAuthCode=test123&transactionID=3045021106&transactionResponseMsg=&" +
            "avsAddress=&avsPostcode=&avsCVN=&cardExpiry=1220&cardLast4=9999&cardType=MASTERCARD&ppCallID=820782890&" +
            "customData1=MOJTest120190124123432&customData2=MASTERCARD&customData3=CreditCard&customData4=";

        String paymentReference = "RC-1519-9028-1909-1435";
        //Create Telephony Payment
        Payment dbPayment = populateTelephonyPaymentToDb(paymentReference, false);

        //Update Telephony Payment Status from PCI PAL - 1st time
        restActions
            .postWithFormData("/telephony/callback", rawFormData)
            .andExpect(status().isNoContent());

        //Validate & capture Update_timestamp - After 1st PCI PAL Callback Request
        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult result = restActions
            .get("/payments?ccd_case_number=" + dbPayment.getCcdCaseNumber()+"&start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertThat(payments.size()).isEqualTo(1);
        assertEquals(payments.get(0).getPaymentReference(), paymentReference);
        Date updatedTsForFirstReq = payments.get(0).getDateUpdated();

        //Update Telephony Payment Status from PCI PAL - 2nd time(Duplicate)
        restActions
            .postWithFormData("/telephony/callback", rawFormData)
            .andExpect(status().isNoContent());

        //Validate & capture Update_timestamp - After 2nd PCI PAL Callback Request(Duplicate)
        result = restActions
            .get("/payments?ccd_case_number=" + dbPayment.getCcdCaseNumber()+"&start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        payments = response.getPayments();
        assertThat(payments.size()).isEqualTo(1);
        assertEquals(payments.get(0).getPaymentReference(), paymentReference);
        Date updatedTsForSecondReq = payments.get(0).getDateUpdated();

        //UpdateTimeStamp should not be changed after 2nd Request(Duplicate)
        assertEquals(updatedTsForFirstReq, updatedTsForSecondReq);
    }

    @Test
    public void updateTelephonyPaymentStatusWithSuccess_Apportionment() throws Exception {
        String ccdCaseNumber = "1111CC12" + RandomUtils.nextInt();

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee(ccdCaseNumber)))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BigDecimal amount = new BigDecimal("101.99");

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber(ccdCaseNumber)
            .provider("pci pal")
            .channel("telephony")
            .build();

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        MvcResult result2 = restActions
            .withReturnUrl("https://www.moneyclaims.service.gov.uk")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", telephonyPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentDto.class);

        String paymentReference = paymentDtoResult.getReference();

        String rawFormData = "orderCurrency=&orderAmount=100&orderReference=" +
            paymentReference +
            "&ppAccountID=1210&" +
            "transactionResult=SUCCESS&transactionAuthCode=test123&transactionID=3045021106&transactionResponseMsg=&" +
            "avsAddress=&avsPostcode=&avsCVN=&cardExpiry=1220&cardLast4=9999&cardType=MASTERCARD&ppCallID=820782890&" +
            "customData1=MOJTest120190124123432&customData2=MASTERCARD&customData3=CreditCard&customData4=";

        restActions
            .postWithFormData("/telephony/callback", rawFormData)
            .andExpect(status().isNoContent());

        PaymentFeeLink savedPaymentGroup = db.findByReference(paymentGroupDto.getPaymentGroupReference());
        List<Payment> payments = savedPaymentGroup.getPayments();

        assertThat(payments.size()).isEqualTo(1);
        assertEquals(payments.get(0).getReference(), paymentReference);
        assertThat("success".equalsIgnoreCase(payments.get(0).getStatus()));

        List<PaymentFee> fees = savedPaymentGroup.getFees();

        assertThat(BigDecimal.valueOf(0.00).equals(fees.get(0).getAmountDue()));
    }

    @Test
    public void updateTelephonyPaymentStatusWithFailed_Apportionment() throws Exception {
        String ccdCaseNumber = "1111CC12" + RandomUtils.nextInt();

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee(ccdCaseNumber)))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BigDecimal amount = new BigDecimal("101.99");

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber(ccdCaseNumber)
            .provider("pci pal")
            .channel("telephony")
            .build();

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        MvcResult result2 = restActions
            .withReturnUrl("https://www.moneyclaims.service.gov.uk")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", telephonyPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentDto.class);

        String paymentReference = paymentDtoResult.getReference();

        String rawFormData = "orderCurrency=&orderAmount=100&orderReference=" +
            paymentReference +
            "&ppAccountID=1210&" +
            "transactionResult=FAILED&transactionAuthCode=test123&transactionID=3045021106&transactionResponseMsg=&" +
            "avsAddress=&avsPostcode=&avsCVN=&cardExpiry=1220&cardLast4=9999&cardType=MASTERCARD&ppCallID=820782890&" +
            "customData1=MOJTest120190124123432&customData2=MASTERCARD&customData3=CreditCard&customData4=";

        restActions
            .postWithFormData("/telephony/callback", rawFormData)
            .andExpect(status().isNoContent());

        PaymentFeeLink savedPaymentGroup = db.findByReference(paymentGroupDto.getPaymentGroupReference());
        List<Payment> payments = savedPaymentGroup.getPayments();
        assertThat(payments.size()).isEqualTo(1);
        assertEquals(payments.get(0).getReference(), paymentReference);
        assertThat("failed".equalsIgnoreCase(payments.get(0).getStatus()));

        List<PaymentFee> fees = savedPaymentGroup.getFees();

        assertThat(BigDecimal.valueOf(101.99).equals(fees.get(0).getAmountDue()));

    }

    private FeeDto getNewFee(String ccdCaseNumber){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("101.99"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .reference("BXsd1123")
            .ccdCaseNumber(ccdCaseNumber)
            .build();

    }


}
