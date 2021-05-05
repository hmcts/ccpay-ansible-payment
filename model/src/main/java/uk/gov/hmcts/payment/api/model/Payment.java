package uk.gov.hmcts.payment.api.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import uk.gov.hmcts.payment.api.jpaaudit.listner.Auditable;
import uk.gov.hmcts.payment.api.jpaaudit.listner.PaymentEntityListener;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Entity
@EntityListeners(PaymentEntityListener.class)
@Getter
@Setter
@Builder(builderMethodName = "paymentWith")
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "payment", indexes = {
    @Index(name = "ix_pay_ccd_case_number", columnList = "ccd_case_number"),
    @Index(name = "ix_pay_payment_status_provider", columnList = "payment_status, payment_provider")
})
public class Payment extends Auditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ToString.Exclude
    @Column(name = "user_id")
    private String userId;

    @ToString.Exclude
    @Column(name = "external_reference")
    private String externalReference;

    @CreationTimestamp
    @Column(name = "date_created")
    private Date dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private Date dateUpdated;

    @ToString.Exclude
    @Transient
    private String email;

    @Column(name = "amount")
    private BigDecimal amount;

    @ToString.Exclude
    @Column(name = "description")
    private String description;

    @ToString.Exclude
    @Column(name = "site_id")
    private String siteId;

    @ToString.Exclude
    @Column(name = "giro_slip_no")
    private String giroSlipNo;

    @ToString.Exclude
    @Transient
    private String status;

    @ToString.Exclude
    @Transient
    private Boolean finished;

    @ToString.Exclude
    @Transient
    private String returnUrl;

    @ToString.Exclude
    @Transient
    private String nextUrl;

    @ToString.Exclude
    @Transient
    private String cancelUrl;

    @ToString.Exclude
    @Transient
    private String refundsUrl;

    @ToString.Exclude
    @Column(name = "currency")
    private String currency;

    @ToString.Exclude
    @Column(name = "ccd_case_number")
    private String ccdCaseNumber;

    @ToString.Exclude
    @Column(name = "case_reference")
    private String caseReference;

    @ToString.Exclude
    @Column(name = "service_type")
    private String serviceType;

    @ToString.Exclude
    @Column(name = "s2s_service_name")
    private String s2sServiceName;

    @ManyToOne
    @JoinColumn(name = "payment_channel")
    private PaymentChannel paymentChannel;

    @ManyToOne
    @JoinColumn(name = "payment_method")
    private PaymentMethod paymentMethod;

    @ManyToOne
    @JoinColumn(name = "payment_provider")
    private PaymentProvider paymentProvider;

    @ManyToOne
    @JoinColumn(name = "payment_status")
    private PaymentStatus paymentStatus;

    @ToString.Exclude
    @Column(name = "organisation_name")
    private String organisationName;

    @ToString.Exclude
    @Column(name = "pba_number")
    private String pbaNumber;

    @ToString.Exclude
    @Column(name = "customer_reference")
    private String customerReference;

    @Column(name = "reference")
    private String reference;

    @ToString.Exclude
    @Column(name = "reported_date_offline")
    private Date reportedDateOffline;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_link_id", insertable = false, updatable = false)
    private PaymentFeeLink paymentLink;

    @ToString.Exclude
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "payment_id", referencedColumnName = "id", nullable = false)
    private List<StatusHistory> statusHistories;

    @ToString.Exclude
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "payment_id", referencedColumnName = "id", nullable = false)
    private List<PaymentAllocation> paymentAllocation;

    @ToString.Exclude
    @Column(name = "service_callback_url")
    private String serviceCallbackUrl;

    @Column(name = "document_control_number")
    private String documentControlNumber;

    @Column(name = "banked_date")
    private Date bankedDate;

    @ToString.Exclude
    @Column(name = "payer_name")
    private String payerName;

    @Override
    public int hashCode(){
        return super.hashCode();
    }
}
