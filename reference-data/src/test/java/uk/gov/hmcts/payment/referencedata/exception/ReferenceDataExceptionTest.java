package uk.gov.hmcts.payment.referencedata.exception;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class ReferenceDataExceptionTest {

    @Test
    public void testException(){
        String msg = "Test Message";
        Assert.assertEquals(msg,new ReferenceDataNotFoundException(msg).getMessage());
    }
}
