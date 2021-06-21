package uk.gov.hmcts.payment.api.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import uk.gov.hmcts.payment.api.jpaaudit.listner.Auditable;
import uk.gov.hmcts.payment.api.jpaaudit.listner.RemissionEntityListener;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@EntityListeners(RemissionEntityListener.class)
@Getter
@Setter
@Builder(builderMethodName = "remissionWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "remission", indexes = {
    @Index(name = "ix_remission_hwf_reference", columnList = "hwf_reference"),
    @Index(name = "ix_remission_ccd_case_number", columnList = "ccd_case_number"),
    @Index(name = "ix_remission_remission_reference", columnList = "remission_reference")
})
public class Remission extends Auditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "remission_reference")
    private String remissionReference;

    @Column(name = "hwf_reference")
    private String hwfReference;

    @Column(name = "hwf_amount")
    private BigDecimal hwfAmount;

    @ToString.Exclude
    @Column(name = "beneficiary_name")
    private String beneficiaryName;

    @ToString.Exclude
    @Column(name = "ccd_case_number")
    private String ccdCaseNumber;

    @ToString.Exclude
    @Column(name = "case_reference")
    private String caseReference;

    @ToString.Exclude
    @Column(name = "site_id")
    private String siteId;

    @CreationTimestamp
    @Column(name = "date_created")
    private Date dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private Date dateUpdated;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_link_id", insertable = false, updatable = false)
    private PaymentFeeLink paymentFeeLink;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_id", insertable = false, updatable = false)
    private PaymentFee fee;

    @Override
    public int hashCode(){
        return super.hashCode();
    }
}
