package uk.gov.hmcts.payment.api.util;

public enum ApportionType {
    AUTO("AUTO");


    private String name;

    ApportionType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}
