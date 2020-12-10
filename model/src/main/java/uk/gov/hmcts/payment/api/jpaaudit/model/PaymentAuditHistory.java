package uk.gov.hmcts.payment.api.jpaaudit.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.Date;

@Entity
@Data
@Builder(builderMethodName = "paymentAuditHistoryWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_audit_history")
@EntityListeners(AuditingEntityListener.class)
public class PaymentAuditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ccd_case_no")
    private String ccdCaseNo;

    //@Column(name = "audit_type")
    @ManyToOne
    @JoinColumn(name = "audit_type")
    private AuditEventsType auditType;

    @Column(name = "audit_payload")
    private String auditPayload;

    @Column(name = "audit_description")
    private String auditDescription;

    @CreationTimestamp
    @Column(name = "date_created")
    private Date dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private Date dateUpdated;
}
