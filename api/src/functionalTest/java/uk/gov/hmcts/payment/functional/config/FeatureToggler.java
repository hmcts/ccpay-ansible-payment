package uk.gov.hmcts.payment.functional.config;

public interface FeatureToggler {

    boolean getBooleanValue(String key, Boolean defaultValue);

}
