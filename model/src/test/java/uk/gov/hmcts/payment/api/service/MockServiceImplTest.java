package uk.gov.hmcts.payment.api.service;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class MockServiceImplTest {

    public MockAccountServiceImpl target = new MockAccountServiceImpl();

    @Test
    public void test_retrieve_with_mocked_account_number() {
        AccountDto accountDto = target.retrieve("PBAFUNC12345");
        assertEquals("CAERPHILLY COUNTY BOROUGH COUNCIL" , accountDto.getAccountName());
        assertEquals("PBAFUNC12345" , accountDto.getAccountNumber());
        assertEquals(BigDecimal.valueOf(30000) , accountDto.getAvailableBalance());
        assertEquals(BigDecimal.valueOf(28879) , accountDto.getCreditLimit());
        assertEquals(AccountStatus.ACTIVE , accountDto.getStatus());
    }

    @Test
    public void test_retrieve_with_pba_account_not_found() {
        try {
            target.retrieve(null);
        } catch(HttpClientErrorException httpClientErrorException) {
            assertEquals(HttpStatus.NOT_FOUND,httpClientErrorException.getStatusCode());
            assertEquals("Unknown test pba account number", httpClientErrorException.getStatusText());
        }

        try {
            target.retrieve("");
        } catch(HttpClientErrorException httpClientErrorException) {
            assertEquals(HttpStatus.NOT_FOUND,httpClientErrorException.getStatusCode());
            assertEquals("Unknown test pba account number", httpClientErrorException.getStatusText());
        }

        try {
            target.retrieve(" ");
        } catch(HttpClientErrorException httpClientErrorException) {
            assertEquals(HttpStatus.NOT_FOUND,httpClientErrorException.getStatusCode());
            assertEquals("Unknown test pba account number", httpClientErrorException.getStatusText());
        }

        try {
            target.retrieve("  ");
        } catch(HttpClientErrorException httpClientErrorException) {
            assertEquals(HttpStatus.NOT_FOUND,httpClientErrorException.getStatusCode());
            assertEquals("Unknown test pba account number", httpClientErrorException.getStatusText());
        }

        try {
            target.retrieve("PBAFUNC123456");
        } catch(HttpClientErrorException httpClientErrorException) {
            assertEquals(HttpStatus.NOT_FOUND,httpClientErrorException.getStatusCode());
            assertEquals("Unknown test pba account number", httpClientErrorException.getStatusText());
        }
    }
}
