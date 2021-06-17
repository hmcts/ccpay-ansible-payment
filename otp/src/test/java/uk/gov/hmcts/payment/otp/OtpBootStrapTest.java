package uk.gov.hmcts.payment.otp;

import org.apache.commons.codec.binary.Base32;
import org.junit.Before;
import org.junit.Test;


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class OtpBootStrapTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @Before
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @Test
    public void testMain() throws Exception {
        Base32 base32 = new Base32();
        String arg = base32.encodeAsString("test".getBytes());
        String[] input = {arg};
        OtpBootstrap.main(input);
        assertEquals(7, outContent.toString().length());

    }

}
