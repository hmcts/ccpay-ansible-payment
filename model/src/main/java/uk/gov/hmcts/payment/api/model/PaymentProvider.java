package uk.gov.hmcts.payment.api.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@Builder(builderMethodName = "paymentProviderWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_provider")
public class PaymentProvider {

    public final static PaymentProvider GOV_PAY = new PaymentProvider("gov pay","Gov pay");
    public final static PaymentProvider PCI_PAL = new PaymentProvider("pci pal","PCI PAL");

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;
}
