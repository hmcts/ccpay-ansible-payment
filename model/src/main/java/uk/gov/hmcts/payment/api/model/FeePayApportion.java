package uk.gov.hmcts.payment.api.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import uk.gov.hmcts.payment.api.jpaaudit.listner.Auditable;
import uk.gov.hmcts.payment.api.jpaaudit.listner.FeePayApportionEntityListener;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@EntityListeners(FeePayApportionEntityListener.class)
@Getter
@Setter
@Builder(builderMethodName = "feePayApportionWith")
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table(name = "fee_pay_apportion")
public class FeePayApportion extends Auditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "fee_id")
    private Integer feeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_link_id", insertable = false, updatable = false)
    private PaymentFeeLink paymentLink;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @Column(name = "payment_amount")
    private BigDecimal paymentAmount;

    @Column(name = "apportion_amount")
    private BigDecimal apportionAmount;

    @Column(name = "apportion_type")
    private String apportionType;

    @Column(name = "call_surplus_amount")
    private BigDecimal callSurplusAmount;

    @ToString.Exclude
    @Column(name = "ccd_case_number")
    private String ccdCaseNumber;

    @ToString.Exclude
    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false)
    private Date dateCreated;

    @CreationTimestamp
    @Column(name = "date_updated", nullable = false)
    private Date dateUpdated;

    @Override
    public int hashCode(){
        return super.hashCode();
    }
}
