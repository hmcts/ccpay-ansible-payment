package uk.gov.hmcts.payment.api.v1.componenttests;

import org.junit.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SpringConfigurationComponentTest extends TestUtil {

    @Test
    public void invalidRequestShouldResultIn400() throws Exception {
        restActions
            .withAuthorizedUser()
            .withAuthorizedService("divorce")
            .post("/users/3/payments/", "{ invalid json }").andExpect(status().isBadRequest());

    }
}
