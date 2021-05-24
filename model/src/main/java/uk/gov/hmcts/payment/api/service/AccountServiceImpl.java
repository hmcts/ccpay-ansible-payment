package uk.gov.hmcts.payment.api.service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;

@Service
@Profile("!liberataMock")
public class AccountServiceImpl implements AccountService<AccountDto, String> {

    @Autowired
    private OAuth2RestOperations restTemplate;

    private static final Logger LOG = LoggerFactory.getLogger(AccountServiceImpl.class);

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Value("${liberata.api.account.url}")
    private String baseUrl;

    @Value("${liberata.api.mock}")
    private Boolean mockLiberata;

    @Value("${liberata.api.mock.account.status}")
    private String responseStatus;

    @Override
    @HystrixCommand(commandKey = "retrievePbaAccount", commandProperties = {
        @HystrixProperty(name = "execution.timeout.enabled", value = "false")
    })
    public AccountDto retrieve(String pbaCode) {
        if (mockLiberata) {
            LOG.info("Liberata mock is enabled");
            return AccountDto.accountDtoWith()
                .accountNumber(pbaCode)
                .accountName("CAERPHILLY COUNTY BOROUGH COUNCIL")
                .creditLimit(BigDecimal.valueOf(28879))
                .availableBalance(BigDecimal.valueOf(30000))
                .status(getAccountStatus())
                .build();
        }
        return restTemplate.getForObject(baseUrl + "/" + pbaCode, AccountDto.class);
    }

    private AccountStatus getAccountStatus() {
        if (responseStatus == "On-Hold") {
            return AccountStatus.ON_HOLD;
        } else if (responseStatus == "deleted") {
            return AccountStatus.DELETED;
        } else {
            return AccountStatus.ACTIVE;
        }
    }
}
