package uk.gov.hmcts.payment.api.componenttests;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.service.AccountServiceImpl;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class AccountServiceTest {
    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);
    @Mock
    private OAuth2RestOperations restTemplateMock;
    @InjectMocks
    private AccountServiceImpl accountServiceImpl;
    @Value("${liberata.api.account.url}")
    private String baseUrl;
    @Value("${liberata.api.mock}")
    private Boolean mockLiberata;
    @Value("${liberata.api.mock.account}")
    private String mockPbaAccounts;

    @Before
    public void setup() throws Exception {
        FieldSetter.setField(accountServiceImpl, accountServiceImpl.getClass().getDeclaredField("baseUrl"), baseUrl);
        FieldSetter.setField(accountServiceImpl, accountServiceImpl.getClass().getDeclaredField("mockLiberata"), mockLiberata);
        FieldSetter.setField(accountServiceImpl, accountServiceImpl.getClass().getDeclaredField("mockPbaAccounts"), mockPbaAccounts);
    }

    @Test
    public void retrieveExistingAccountReturnsAccountDto() throws Exception {
        String pbaCode = "PBA1234";
        FieldSetter.setField(accountServiceImpl, accountServiceImpl.getClass().getDeclaredField("mockLiberata"), false);
        AccountDto expectedDto = new AccountDto(pbaCode, "accountName", new BigDecimal(100),
            new BigDecimal(100), AccountStatus.ACTIVE, new Date());
        when(restTemplateMock.getForObject(baseUrl + "/" + pbaCode, AccountDto.class)).thenReturn(expectedDto);
        assertEquals(expectedDto, accountServiceImpl.retrieve(pbaCode));
    }

    @Test
    public void retrieveMockActiveDto() throws Exception {
        String pbaCode = "PBA0001_ACTIVE";
        AccountDto expectedDto = new AccountDto(pbaCode, "CAERPHILLY COUNTY BOROUGH COUNCIL", new BigDecimal(28879),
            new BigDecimal(30000), AccountStatus.ACTIVE, null);
        assertEquals(expectedDto, accountServiceImpl.retrieve(pbaCode));
    }

    @Test
    public void retrieveMockDeletedDto() throws Exception {
        String pbaCode = "PBA0002_DELETED";
        AccountDto expectedDto = new AccountDto(pbaCode, "CAERPHILLY COUNTY BOROUGH COUNCIL", new BigDecimal(28879),
            new BigDecimal(30000), AccountStatus.DELETED, null);
        assertEquals(expectedDto, accountServiceImpl.retrieve(pbaCode));
    }

    @Test
    public void retrieveMockOnHoldDto() throws Exception {
        String pbaCode = "PBA0003_ON_HOLD";
        AccountDto expectedDto = new AccountDto(pbaCode, "CAERPHILLY COUNTY BOROUGH COUNCIL", new BigDecimal(28879),
            new BigDecimal(30000), AccountStatus.ON_HOLD, null);
        assertEquals(expectedDto, accountServiceImpl.retrieve(pbaCode));
    }
}
