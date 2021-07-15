package uk.gov.hmcts.payment.api.reports;

import uk.gov.hmcts.payment.api.util.PaymentMethodType;

public enum PaymentReportType {

    CARD,
    DIGITAL_BAR,
    PBA_CMC,
    PBA_DIVORCE,
    PBA_PROBATE,
    PBA_FINREM,
    PBA_FPL;

    public static PaymentReportType from(PaymentMethodType paymentMethodType, String serviceType) {
        String value = "";

        String serviceTypeEnum = getServiceTypeEnum(serviceType);

        if (paymentMethodType != null && serviceTypeEnum != null) {
            value = String.join("_", paymentMethodType.name(), serviceTypeEnum);
        } else if (serviceTypeEnum != null) {
            value = serviceTypeEnum;
        } else if (paymentMethodType != null) {
            value = paymentMethodType.name();
        }

        try {
            return PaymentReportType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedOperationException(String.format("No config defined as the report type is not supported for " +
                "paymentMethod %s and service %s ", paymentMethodType, serviceTypeEnum));
        }
    }

    private static String getServiceTypeEnum(String serviceType) {
        if (serviceType != null && (serviceType.equalsIgnoreCase("Specified Money Claims") || serviceType.equalsIgnoreCase("Civil Money Claims"))) {
            return "CMC";
        }
        if (serviceType != null && (serviceType.equalsIgnoreCase("Financial Remedy") || serviceType.equalsIgnoreCase("Finrem"))) {
            return "FINREM";
        }
        if (serviceType != null && (serviceType.equalsIgnoreCase("Family Public Law"))) {
            return "FPL";
        }
        if (serviceType != null && (serviceType.equalsIgnoreCase("Digital Bar"))) {
            return "DIGITAL_BAR";
        }
        if (serviceType != null && (serviceType.equalsIgnoreCase("Divorce"))) {
            return "DIVORCE";
        }
        if (serviceType != null && (serviceType.equalsIgnoreCase("Probate"))) {
            return "PROBATE";
        }
        return serviceType;
    }
}
