package uk.gov.hmcts.payment.api.contract.util;

public enum Service {
    CMC("Civil Money Claims"),
    DIVORCE("Divorce"),
    PROBATE("Probate"),
    FINREM("Finrem"),
    DIGITAL_BAR("Digital Bar"),
    FPL("Family Public Law"),
    IAC("Immigration and Asylum Appeals"),
    UNSPEC("Unspecified Claim"),
    CIVIL("Civil");

    private String name;

    Service(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}
