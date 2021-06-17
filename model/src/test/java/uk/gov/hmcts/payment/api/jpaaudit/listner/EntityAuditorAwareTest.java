package uk.gov.hmcts.payment.api.jpaaudit.listner;

import org.junit.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityAuditorAwareTest {

    @Test
    public void testGetCurrentAuditor(){
        EntityAuditorAware entityAuditorAware = new EntityAuditorAware();
        Optional<String> currentAuditor =  entityAuditorAware.getCurrentAuditor();
        assertEquals("PAYMENT-USER",currentAuditor.get());
    }
}
