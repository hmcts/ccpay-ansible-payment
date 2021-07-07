package uk.gov.hmcts.payment.api.model;

import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import uk.gov.hmcts.payment.api.jpaaudit.listner.Auditable;
import uk.gov.hmcts.payment.api.jpaaudit.listner.PaymentFeeEntityListener;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Entity
@EntityListeners(PaymentFeeEntityListener.class)
@Getter
@Setter
@Builder(builderMethodName = "feeWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "fee")
public class PaymentFee extends Auditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code")
    private String code;

    @Column(name = "version")
    private String version;

    @Column(name = "volume")
    private Integer volume;

    @Column(name = "calculated_amount")
    private BigDecimal calculatedAmount;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_link_id", insertable = false, updatable = false)
    private PaymentFeeLink paymentLink;

    @ToString.Exclude
    @Column(name = "ccd_case_number")
    private String ccdCaseNumber;

    @ToString.Exclude
    @Column(name = "reference")
    private String reference;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    @ToString.Exclude
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "fee_id", referencedColumnName = "id")
    private List<Remission> remissions;

    @ToString.Exclude
    @Transient
    private BigDecimal apportionAmount;

    @ToString.Exclude
    @Transient
    private BigDecimal allocatedAmount;

    @Column(name = "amount_due")
    private BigDecimal amountDue;

    @ToString.Exclude
    @Transient
    private Date dateApportioned;

    @Column(name = "date_created")
    private Timestamp dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private Timestamp dateUpdated;

    @Override
    public int hashCode(){
        return super.hashCode();
    }
}
