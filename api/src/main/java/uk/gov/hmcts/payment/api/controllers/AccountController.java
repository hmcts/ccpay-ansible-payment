package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.exception.AccountNotFoundException;
import uk.gov.hmcts.payment.api.exception.LiberataServiceInaccessibleException;
import uk.gov.hmcts.payment.api.service.AccountService;

@RestController
@Api(tags = {"Account"})
@SwaggerDefinition(tags = {@Tag(name = "AccountController", description = "Account REST API")})
public class AccountController {

    private static final Logger LOG = LoggerFactory.getLogger(AccountController.class);

    @Autowired
    private AccountService<AccountDto, String> accountService;

    @ApiOperation(value = "Get the account status and available balance for a PBA account number", notes = "Get the account status and available balance for a PBA account number")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Account details retrieved"),
        @ApiResponse(code = 404, message = "Account not found"),
        @ApiResponse(code = 503, message = "Failed to connect with Liberata")
    })
    @GetMapping(value = "/accounts/{accountNumber}")
    public AccountDto getAccounts(@PathVariable("accountNumber") String accountNumber) {
        try {
            return accountService.retrieve(accountNumber);
        } catch (HttpClientErrorException ex) {
            LOG.error("Error while calling account", ex);
            throw new AccountNotFoundException("Account not found");
        } catch (Exception ex) {
            throw new LiberataServiceInaccessibleException("Failed to connect with Liberata. " + ex.getMessage());
        }

        
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(AccountNotFoundException.class)
    public String return404(AccountNotFoundException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(LiberataServiceInaccessibleException.class)
    public String return503(LiberataServiceInaccessibleException ex) {
        return ex.getMessage();
    }
}
