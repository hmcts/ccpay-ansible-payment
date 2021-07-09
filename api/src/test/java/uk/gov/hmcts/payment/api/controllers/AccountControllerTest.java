package uk.gov.hmcts.payment.api.controllers;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.resource.OAuth2AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.payment.api.controllers.AccountController;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.exception.AccountNotFoundException;
import uk.gov.hmcts.payment.api.exception.LiberataServiceInaccessibleException;
import uk.gov.hmcts.payment.api.service.AccountService;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class AccountControllerTest {

    @Mock
    private AccountService<AccountDto, String> accountServiceMock;

    @InjectMocks
    private AccountController accountController;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @Test
    public void gettingExistingAccountNumberReturnsAccountDetails() {
        AccountDto expectedDto = new AccountDto("PBA1234", "accountName", new BigDecimal(100),
            new BigDecimal(100), AccountStatus.ACTIVE, new Date());

        //When Account Matches
        when(accountServiceMock.retrieve(eq("PBA1234"))).thenReturn(expectedDto);

        //When Not Account Matches
        when(accountServiceMock.retrieve(not(eq("PBA1234")))).
            thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "UnKnown test pba account number"));


        AccountDto actualDto = accountController.getAccounts("PBA1234");

        assertEquals(expectedDto, actualDto);
    }

    @Test(expected = AccountNotFoundException.class)
    public void gettingNonExistingAccountNumberThrowsAccountNotFoundException() {
        AccountDto expectedDto = new AccountDto("PBA4321", "accountName", new BigDecimal(100),
            new BigDecimal(100), AccountStatus.ACTIVE, new Date());

        //For Same account return account not found
        when(accountServiceMock.retrieve(eq("PBA4321"))).thenThrow(HttpClientErrorException.class);

        //For Any other account return account object
        when(accountServiceMock.retrieve(not(eq("PBA4321")))).thenReturn(expectedDto);
        accountController.getAccounts("PBA4321");
    }

    @Test(expected = LiberataServiceInaccessibleException.class)
    public void gettingNonExistingAccountNumberThrowsLiberataServiceInaccessibleException() {
        when(accountServiceMock.retrieve("PBA4321")).thenThrow(LiberataServiceInaccessibleException.class);
        accountController.getAccounts("PBA4321");
    }

    @Test
    public void gettingNonExistingAccountNumberReturns404() throws Exception {
        String errorMessage = "errorMessage";
        AccountNotFoundException ex = new AccountNotFoundException(errorMessage);
        assertEquals(errorMessage, accountController.return404(ex));
    }

    @Test(expected = LiberataServiceInaccessibleException.class)
    public void getLiberataServiceInaccessibleException() {
        AccountDto expectedDto = new AccountDto("PBA4324", "accountName", new BigDecimal(100),
            new BigDecimal(100), AccountStatus.ACTIVE, new Date());

        //For Same account return Auth exception
        when(accountServiceMock.retrieve(eq("PBA4324"))).thenThrow(OAuth2AccessDeniedException.class);

        //For Any other account return account object
        when(accountServiceMock.retrieve(not(eq("PBA4324")))).thenReturn(expectedDto);
        accountController.getAccounts("PBA4324");
    }

}
