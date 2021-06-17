package uk.gov.hmcts.payment.api.model;


import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.util.Date;

@Entity
@Data
@Builder(builderMethodName = "paymentAllocationWith")
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "payment_allocation")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "payment_group_reference")
    private String paymentGroupReference;

    @ManyToOne
    @JoinColumn(name = "allocation_status")
    private PaymentAllocationStatus paymentAllocationStatus;

    @Column(name = "unidentified_reason")
    private String unidentifiedReason;

    @Column(name = "receiving_office")
    private String receivingOffice;

    @Column(name = "reason")
    private String reason;

    @Column(name = "explanation")
    private String explanation;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @CreationTimestamp
    @Column(name = "date_created")
    private Date dateCreated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", insertable = false, updatable = false)
    @ToString.Exclude
    private Payment payment;

    @Override
    public int hashCode(){
        return super.hashCode();
    }
}
