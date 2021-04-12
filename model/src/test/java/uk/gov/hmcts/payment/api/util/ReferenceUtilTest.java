package uk.gov.hmcts.payment.api.util;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReferenceUtilTest {

    @Test
    public void testGetNext() throws CheckDigitException {
        ReferenceUtil referenceUtil = new ReferenceUtil();
        String result = referenceUtil.getNext("test");
        assertEquals(24,result.length());
    }
}
