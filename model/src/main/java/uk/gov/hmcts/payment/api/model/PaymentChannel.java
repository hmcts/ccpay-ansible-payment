package uk.gov.hmcts.payment.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@Builder(builderMethodName = "paymentChannelWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_channel")
public class PaymentChannel {

    public final static PaymentChannel TELEPHONY = new PaymentChannel("telephony","Through the IVR");

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;
}
