package uk.gov.hmcts.payment.api.service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;
import java.util.Arrays;

@Service
@Profile("!liberataMock")
public class AccountServiceImpl implements AccountService<AccountDto, String> {

    private static final Logger LOG = LoggerFactory.getLogger(AccountServiceImpl.class);
    @Autowired
    private OAuth2RestOperations restTemplate;
    @Autowired
    private org.springframework.core.env.Environment environment;

    @Value("${liberata.api.account.url}")
    private String baseUrl;

    @Value("${liberata.api.mock}")
    private Boolean mockLiberata;

    @Value("${liberata.api.mock.account}")
    private String mockPbaAccounts;

    @Override
    @HystrixCommand(commandKey = "retrievePbaAccount", commandProperties = {
        @HystrixProperty(name = "execution.timeout.enabled", value = "false")
    })
    public AccountDto retrieve(String pbaCode) {
        if (Boolean.TRUE.equals(mockLiberata)) {
            LOG.info("Liberata mock is enabled");
            return AccountDto.accountDtoWith()
                .accountNumber(pbaCode)
                .accountName("CAERPHILLY COUNTY BOROUGH COUNCIL")
                .creditLimit(BigDecimal.valueOf(28879))
                .availableBalance(BigDecimal.valueOf(30000))
                .status(getAccountStatus(pbaCode))
                .build();
        }
        return restTemplate.getForObject(baseUrl + "/" + pbaCode, AccountDto.class);
    }

    private AccountStatus getAccountStatus(String pbaCode) {
        if (Arrays.stream(mockPbaAccounts.split(",")).anyMatch(value ->{
            return value.equals(pbaCode);

        })) {
            return AccountStatus.valueOf(pbaCode.replaceAll("^.+?_", ""));
        }
        throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "UnKnown test pba account number");
    }
}
