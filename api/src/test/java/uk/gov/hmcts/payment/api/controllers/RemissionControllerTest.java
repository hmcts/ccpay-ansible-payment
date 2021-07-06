package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.PaymentFeeDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.RemissionDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.api.v1.model.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.NoServiceFoundException;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.model.PaymentFee.feeWith;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class RemissionControllerTest {

    private static final String USER_ID = UserResolverBackdoor.CITIZEN_ID;

    private final static String REMISSION_REFERENCE_REGEX = "^[RM-]{3}(\\w{4}-){3}(\\w{4})";

    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    @Autowired
    protected RemissionDbBackdoor remissionDbBackdoor;

    @Autowired
    protected PaymentDbBackdoor paymentDbBackdoor;

    @Autowired
    protected PaymentFeeDbBackdoor paymentFeeDbBackdoor;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @Autowired
    private SiteService<Site, String> siteServiceMock;

    @MockBean
    private ReferenceDataService referenceDataService;

    @Before
    public void setUp() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

    }

    @Test
    @Transactional
    public void correctAndValidRemissionDataShouldReturn201() throws Exception {
        RemissionRequest remission = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference("HWFref")
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        restActions
            .post("/remissions", remission)
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    @Transactional
    public void correctAndValidRemissionDataShouldSaveToDb() {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andReturn();

        Remission savedRemission = remissionDbBackdoor.findByHwfReference(hwfReference);
        assertEquals(remissionDto.getCcdCaseNumber(), savedRemission.getCcdCaseNumber());
        assertEquals(remissionDto.getCaseReference(), savedRemission.getCaseReference());
        assertEquals(remissionDto.getHwfReference(), savedRemission.getHwfReference());
    }

    @Test
    @Transactional
    public void correctAndValidRemissionWithoutCCDNumberInFeeDataShouldSaveToDb() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .fee(getFeeWithOutCCDCaseNumber())
            .build();

        MvcResult result =  restActions
            .post("/remissions", remissionRequest)
            .andReturn();

        RemissionDto remissionResultDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertEquals(remissionRequest.getCcdCaseNumber(),remissionResultDto.getFee().getCcdCaseNumber());
    }

    @Test
    @Transactional
    public void duplicatehwfReferenceRemissionShouldReturn201() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isCreated())
            .andReturn();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    @Transactional
    public void emptyHwfReferenceShouldReturn400() throws Exception {
        String hwfReference = "";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void nullHwfReferenceShouldReturn400() throws Exception {
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void nullHwfAmountShouldReturn400() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfReference(hwfReference)
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void negativeHwfAmountShouldReturn400() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("-10.00"))
            .hwfReference(hwfReference)
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void hwfAmountEqualToZeroShouldReturn400() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("0.00"))
            .hwfReference(hwfReference)
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void hwfAmountWithMoreThan2DecimalPlacesShouldReturn400() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.001"))
            .hwfReference(hwfReference)
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void emptyCcdCaseNumberShouldReturn201() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    @Transactional
    public void nullCcdCaseNumberShouldReturn201() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    @Transactional
    public void emptyCaseReferenceShouldReturn201() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    @Transactional
    public void nullCaseReferenceShouldReturn201() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    @Transactional
    public void emptyCaseReferenceAndEmptyCcdCaseNumberCCDShouldReturn400() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("")
            .ccdCaseNumber("")
            .hwfAmount(new BigDecimal("10.01"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void nullCaseReferenceAndNullCcdCaseNumberCCDShouldReturn400() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .hwfAmount(new BigDecimal("10.01"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void nullCaseReferenceAndEmptyCcdCaseNumberCCDShouldReturn400() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .ccdCaseNumber("")
            .hwfAmount(new BigDecimal("10.01"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void emptyCaseReferenceAndNullCcdCaseNumberCCDShouldReturn400() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("")
            .hwfAmount(new BigDecimal("10.01"))
            .hwfReference(hwfReference)
            .caseType("tax_exception")
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    public void getRemissionRequestUponSuccessfulCreation() throws Exception {
        RemissionRequest remission = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference("HWFref")
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        MvcResult result = restActions
            .post("/remissions", remission)
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto remissionDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertTrue(remissionDto.getRemissionReference().matches(REMISSION_REFERENCE_REGEX));
    }

    @Test
    @Transactional
    public void feeDtoFilledGetsFeeSaved() throws Exception {

        BigDecimal calculatedAmount = new BigDecimal("199.99");
        String feeReference = "feeReference";

        FeeDto feeDto = FeeDto.feeDtoWith()
            .calculatedAmount(calculatedAmount)
            .code("FEE0001")
            .version("1")
            .reference(feeReference)
            .build();

        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .caseType("tax_exception")
            .hwfReference("HWFref")
            .fee(feeDto)
            .build();

        MvcResult result = restActions.post("/remissions", remissionRequest)
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto remissionDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);

        Remission remission = remissionDbBackdoor.findByRemissionReference(remissionDto.getRemissionReference());
        PaymentFee paymentFee = paymentFeeDbBackdoor.findByPaymentLinkId(remission.getPaymentFeeLink().getId());
        assertNotNull(paymentFee);
        assertThat(paymentFee.getCode()).isEqualTo("FEE0001");
    }

    @Test
    @Transactional
    public void paymentFeeLinkAndFeeCreatedWhenNoPaymentGroupReferenceSent() throws Exception {
        BigDecimal calculatedAmount = new BigDecimal("199.99");
        String feeReference = "feeReference";

        String feeCode = "FEE0001";
        FeeDto feeDto = FeeDto.feeDtoWith()
            .calculatedAmount(calculatedAmount)
            .code(feeCode)
            .version("1")
            .reference(feeReference)
            .build();

        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .caseType("tax_exception")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference("HWFref")
            .fee(feeDto)
            .build();

        MvcResult result = restActions
            .post("/remissions", remissionRequest)
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto remissionDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);

        String returnedRemissionReference = remissionDto.getRemissionReference();
        Remission remission = remissionDbBackdoor.findByRemissionReference(returnedRemissionReference);
        assertNotNull(remission);
        String paymentGroupReference = remission.getPaymentFeeLink().getPaymentReference();
        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.findByReference(paymentGroupReference);
        assertNotNull(paymentFeeLink);
        PaymentFee paymentFee = paymentFeeDbBackdoor.findByPaymentLinkId(paymentFeeLink.getId());
        assertNotNull(paymentFee);
        assertEquals("Fee code is correct", feeCode, paymentFee.getCode());
    }

    @Test
    @Transactional
    public void noFeeAndNoPaymentGroupReferenceAndRemissionGetsCreated() throws Exception {
        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .caseType("tax_exception")
            .hwfReference("HWFref")
            .fee(getFee())
            .build();

        MvcResult result = restActions
            .post("/remissions", remissionRequest)
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto remissionDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);
        String returnedRemissionReference = remissionDto.getRemissionReference();
        Remission remission = remissionDbBackdoor.findByRemissionReference(returnedRemissionReference);
        assertNotNull(remission);
        PaymentFeeLink paymentFeeLink = remission.getPaymentFeeLink();
        assertNotNull(paymentFeeLink);
    }

    @Test
    @Transactional
    public void noFeeWithPaymentGroupReferenceAndRemissionGetsCreated() throws Exception {
        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .caseType("tax_exception")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference("HWFref")
            .fee(getFee())
            .build();

        MvcResult result = restActions
            .post("/remissions", remissionRequest)
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto remissionDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);
        Remission remission = remissionDbBackdoor.findByRemissionReference(remissionDto.getRemissionReference());
        assertNotNull(remission);
        String foundPaymentGroupReference = remission.getPaymentFeeLink().getPaymentReference();
        assertEquals("Group reference are equal", remissionDto.getPaymentGroupReference(), foundPaymentGroupReference);
        PaymentFeeLink paymentFeeLink = remission.getPaymentFeeLink();
        assertNotNull(paymentFeeLink);
    }

    @Test
    @Transactional
    public void remissionWithWrongSiteIDShouldNotSaveToDb() throws Exception {
        String hwfReference = "HWFref";
        RemissionRequest remissionDto = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference(hwfReference)
            .build();

        restActions
            .post("/remissions", remissionDto)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createRemissionWithoutFeeShouldFailTest() throws Exception {
        RemissionRequest remissionRequest = getRemissionRequest();
        remissionRequest.setFee(null);

        MvcResult result = restActions
            .post("/remissions", remissionRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("fee: must not be null");
    }

    @Test
    public void createRemissionWithIncorrectHwfAmountShouldFailTest() throws Exception {
        RemissionRequest remissionRequest = getRemissionRequest();
        remissionRequest.setHwfAmount(new BigDecimal("550"));

        MvcResult result = restActions
            .post("/remissions", remissionRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("hwfAmountInvalid: Hwf amount cannot be greater than calculated amount.");
    }

    @Test
    public void createRemissionWithNoCcdCaseNumberAndCaseReferenceShoudFailTest() throws Exception {
        RemissionRequest remissionRequest = getRemissionRequest();
        remissionRequest.setCcdCaseNumber(null);
        remissionRequest.setCaseReference(null);

        MvcResult result = restActions
            .post("/remissions", remissionRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("eitherOneRequired: Either ccdCaseNumber or caseReference is required.");
    }

    @Test
    public void createUpfrontRemissionTest() throws Exception {
        RemissionRequest remissionRequest = getRemissionRequest();

        MvcResult result = restActions
            .post("/remissions", remissionRequest)
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto remissionDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(remissionDto).isNotNull();
        assertThat(remissionDto.getRemissionReference()).startsWith("RM");
        assertThat(remissionRequest.getCcdCaseNumber()).isEqualTo(remissionDto.getFee().getCcdCaseNumber());
    }

    /*
    @Test
    @Transactional
    public void createRetrospectiveRemissionWithValidDataShouldBeSuccessfulTest() throws Exception {
        // create a telephony payment
        MvcResult result1 = restActions
            .withHeader("service-callback-url", "http://payments.com")
            .post("/card-payments", getCardPaymentRequest())
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto createPaymentResponseDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);
        Integer feeId = createPaymentResponseDto.getFees().get(0).getId();

        assertThat(createPaymentResponseDto).isNotNull();

        // Get fee id
        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.findByReference(createPaymentResponseDto.getPaymentGroupReference());

        // create a partial remission
        MvcResult result2 = restActions
            .post("/payment-groups/" + createPaymentResponseDto.getPaymentGroupReference() + "/fees/" + feeId + "/remissions", getRemissionRequest())
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto createRemissionResponseDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(createRemissionResponseDto).isNotNull();

        assertThat(createRemissionResponseDto.getFee().getCcdCaseNumber()).isEqualTo(createPaymentResponseDto.getFees().get(0).getCcdCaseNumber());
        assertThat(createRemissionResponseDto.getPaymentGroupReference()).isEqualTo(createPaymentResponseDto.getPaymentGroupReference());
        assertThat(createRemissionResponseDto.getPaymentReference()).isEqualTo(createPaymentResponseDto.getReference());
        assertThat(paymentFeeLink.getFees().size()).isEqualTo(1);
    }
    */

    @Test
    @Transactional
    public void createRemissionWithValidDataShouldBeSuccessfulTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        Integer feeId = paymentGroupDto.getFees().get(0).getId();

        // create a partial remission
        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/fees/" + feeId + "/remissions", getRemissionRequestForNetAmount())
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto createRemissionResponseDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(createRemissionResponseDto).isNotNull();

        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentGroupDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto1 = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto1.getFees().get(0).getNetAmount()).isEqualTo(paymentGroupDto.getFees().get(0).getCalculatedAmount().subtract(getRemissionRequestForNetAmount().getHwfAmount()));

    }

    @Test
    @Transactional
    public void createRemissionWithoutFeesShouldBeSuccessfulTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        Integer feeId = paymentGroupDto.getFees().get(0).getId();

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        // create a partial remission
        MvcResult result3 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/fees/" + feeId + "/remissions", getRemissionRequestForNetAmount())
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto createRemissionResponseDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(createRemissionResponseDto).isNotNull();

        MvcResult result4 = restActions
            .get("/payment-groups/" + paymentGroupDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto1 = objectMapper.readValue(result4.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto1.getFees().get(0).getNetAmount()).isEqualTo(paymentGroupDto.getFees().get(0).getCalculatedAmount().subtract(getRemissionRequestForNetAmount().getHwfAmount()));

        MvcResult result5 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/fees/" + feeId + "/remissions", getRemissionRequest())
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto createRemissionResponseDto1 = objectMapper.readValue(result5.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(createRemissionResponseDto1).isNotNull();

    }

    @Test
    @Transactional
    public void createRetroRemissionFullSuccessPaymentTest() throws Exception {

        PaymentFee fee = PaymentFee.feeWith().amountDue(new BigDecimal("10.00")).netAmount(new BigDecimal("10.00")).calculatedAmount(new BigDecimal("10.00")).version("1").code("FEE000123").build();
        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.create(paymentFeeLinkWith().paymentReference("2021-1625063858253").payments(Arrays.asList(populateCardPaymentToDb("555",true))).fees(Arrays.asList(fee)));
        MvcResult result = restActions
            .post("/payment-groups/" + paymentFeeLink.getPaymentReference() + "/fees/" + fee.getId() + "/remissions", getRetroRemissionRequest())
            .andExpect(status().isCreated())
            .andReturn();
        RemissionDto createRemissionResponseDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(createRemissionResponseDto.getRemissionReference()).isNotNull();
        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentFeeLink.getPaymentReference())
            .andExpect(status().isOk())
            .andReturn();
        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        assertEquals(paymentGroupDto.getFees().get(0).getAmountDue().longValue(),-10);
    }

    @Test
    @Transactional
    public void createRetroRemissionFullFailedPaymentTest() throws Exception {

        PaymentFee fee2 = PaymentFee.feeWith().amountDue(new BigDecimal("10.00")).netAmount(new BigDecimal("10.00")).calculatedAmount(new BigDecimal("10.00")).version("1").code("FEE000124").build();
        PaymentFeeLink paymentFeeLink2 = paymentDbBackdoor.create(paymentFeeLinkWith().paymentReference("2020-1625063858786").payments(Arrays.asList(populateCardPaymentToDb("345",false))).fees(Arrays.asList(fee2)));
        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentFeeLink2.getPaymentReference() + "/fees/" + fee2.getId() + "/remissions", getRetroRemissionRequest())
            .andExpect(status().isCreated())
            .andReturn();
        RemissionDto createRemissionResponseDto1 = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(createRemissionResponseDto1.getRemissionReference()).isNotNull();
        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentFeeLink2.getPaymentReference())
            .andExpect(status().isOk())
            .andReturn();
        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        assertEquals(paymentGroupDto.getFees().get(0).getAmountDue().longValue(),0);
    }

    @Test
    @Transactional
    public void createRetroRemissionPartialSuccessPaymentTest() throws Exception {

        PaymentFee fee2 = PaymentFee.feeWith().amountDue(new BigDecimal("20.00")).netAmount(new BigDecimal("20.00")).calculatedAmount(new BigDecimal("20.00")).version("1").code("FEE000456").build();
        PaymentFeeLink paymentFeeLink2 = paymentDbBackdoor.create(paymentFeeLinkWith().paymentReference("2020-1625063858456").payments(Arrays.asList(populateCardPaymentToDb("456",true))).fees(Arrays.asList(fee2)));
        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentFeeLink2.getPaymentReference() + "/fees/" + fee2.getId() + "/remissions", getRetroRemissionRequest())
            .andExpect(status().isCreated())
            .andReturn();
        RemissionDto createRemissionResponseDto1 = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(createRemissionResponseDto1.getRemissionReference()).isNotNull();
        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentFeeLink2.getPaymentReference())
            .andExpect(status().isOk())
            .andReturn();
        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        assertEquals(paymentGroupDto.getFees().get(0).getAmountDue().longValue(),0);
    }

    @Test
    @Transactional
    public void createRetroRemissionPartialFailedPaymentTest() throws Exception {

        PaymentFee fee2 = PaymentFee.feeWith().amountDue(new BigDecimal("20.00")).netAmount(new BigDecimal("20.00")).calculatedAmount(new BigDecimal("20.00")).version("1").code("FEE000567").build();
        PaymentFeeLink paymentFeeLink2 = paymentDbBackdoor.create(paymentFeeLinkWith().paymentReference("2020-1625063858567").payments(Arrays.asList(populateCardPaymentToDb("567",false))).fees(Arrays.asList(fee2)));
        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentFeeLink2.getPaymentReference() + "/fees/" + fee2.getId() + "/remissions", getRetroRemissionRequest())
            .andExpect(status().isCreated())
            .andReturn();
        RemissionDto createRemissionResponseDto1 = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), RemissionDto.class);
        assertThat(createRemissionResponseDto1.getRemissionReference()).isNotNull();
        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentFeeLink2.getPaymentReference())
            .andExpect(status().isOk())
            .andReturn();
        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        assertEquals(paymentGroupDto.getFees().get(0).getAmountDue().longValue(),10);
    }

    @Test
    public void createRetrospectiveRemissionWithInvalidPaymentGroupReferenceShouldFailTest() throws Exception {
        restActions
            .post("/payment-groups/2019-0000000001/fees/1/remissions" + getRemissionRequest())
            .andExpect(status().isNotFound());
    }


    @Test
    public void correctAndValidRemissionDataShouldReturn404NoServiceFound() throws Exception {
        RemissionRequest remission = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference("HWFref")
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenThrow(new NoServiceFoundException("Test Error"));
        restActions
            .post("/remissions", remission)
            .andExpect(status().isNotFound())
            .andExpect(content().string("Test Error"));
    }

    @Test
    public void correctAndValidRemissionDataShouldReturn504NoServiceFound() throws Exception {
        RemissionRequest remission = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference("HWFref")
            .caseType("tax_exception")
            .fee(getFee())
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenThrow(new GatewayTimeoutException("Test Error"));
        restActions
            .post("/remissions", remission)
            .andExpect(status().isGatewayTimeout())
            .andExpect(content().string("Test Error"));
    }


    private RemissionRequest getRemissionRequest() {
        return RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("A partial remission")
            .ccdCaseNumber("1111-2222-3333-4444")
            .hwfAmount(new BigDecimal("20"))
            .hwfReference("HR1111")
            .caseType("tax_exception")
            .fee(FeeDto.feeDtoWith()
                .calculatedAmount(new BigDecimal("300"))
                .code("FEE0111")
                .version("1")
                .volume(1)
                .build())
            .build();
    }

    private RemissionRequest getRetroRemissionRequest() {
        return RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("Full Remission")
            .ccdCaseNumber("1111-2222-3333-4444")
            .hwfAmount(new BigDecimal("10"))
            .hwfReference("HR1111")
            .caseType("tax_exception")
            .isRetroRemission(true)
            .fee(FeeDto.feeDtoWith()
                .calculatedAmount(new BigDecimal("10"))
                .amountDue(null)
                .code("FEE0123")
                .version("1")
                .volume(1)
                .build())
            .build();
    }
    private CardPaymentRequest getCardPaymentRequest() {
        return CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("250"))
            .ccdCaseNumber("CCD1234")
            .channel("telephony")
            .currency(CurrencyCode.GBP)
            .description("A test telephony payment")
            .provider("pci pal")
            .service("DIVORCE")
            .caseType("Divorce_Exception")
            .fees(Collections.singletonList(getFee()))
            .build();
    }

    private FeeDto getFee() {
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("10.00"))
            .ccdCaseNumber("CCD1234")
            .version("1")
            .code("FEE0123")
            .build();
    }

    private FeeDto getFeeWithOutCCDCaseNumber() {
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("10.00"))
            .version("1")
            .code("FEE0123")
            .build();
    }

    private FeeDto getNewFee(){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("250"))
            .code("FEE312")
            .version("1")
            .volume(1)
            .reference("BXsd1123")
            .ccdCaseNumber("1111-2222-2222-1111")
            .build();

    }

    private RemissionRequest getRemissionRequestForNetAmount() {
        return RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("A partial remission")
            .ccdCaseNumber("1111-2222-2222-1111")
            .hwfAmount(new BigDecimal("150"))
            .hwfReference("HR1111")
            .caseType("tax_exception")
            .fee(FeeDto.feeDtoWith()
                .calculatedAmount(new BigDecimal("250"))
                .code("FEE312")
                .version("1")
                .volume(1)
                .build())
            .build();
    }

    private FeeDto getConsecutiveFee() {
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("100.19"))
            .code("FEE313")
            .id(1)
            .version("1")
            .volume(2)
            .reference("BXsd11253")
            .ccdCaseNumber("1111-2222-2222-1111")
            .build();
    }

    public Payment populateCardPaymentToDb(String number, boolean isPaymentSuccess) throws Exception {
        //Create a payment in remissionDbBackdoor
        String paymentStatus="";
        if(isPaymentSuccess){
            paymentStatus="success";
        }else{
            paymentStatus="created";
        }
        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status(paymentStatus).externalStatus(paymentStatus).build();
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("10.00"))
            .caseReference("Reference" + number)
            .ccdCaseNumber("ccdCaseNumber" + number)
            .description("Test payments statuses for " + number)
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA07")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name(paymentStatus).build())
            .externalReference("e2kkddts5215h9qqoeuth5c0v" + number)
            .reference("RC-1519-9028-2432-000" + number)
            .statusHistories(Arrays.asList(statusHistory))
            .build();

        PaymentFee fee = feeWith().calculatedAmount(new BigDecimal("10.00")).version("1").code("FEE000" + number).volume(1).build();

        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.create(paymentFeeLinkWith()
            .paymentReference("2018-0000000000" + number)
            .caseReference("Reference" + number)
            .ccdCaseNumber("ccdCaseNumber" + number)
            .enterpriseServiceName("Probate")
            .orgId("AA0" + number)
            .payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);
        return payment;
    }
}
