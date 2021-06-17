package uk.gov.hmcts.payment.api.jpaaudit.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Data
@Builder(builderMethodName = "auditEventsTypeWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "audit_events_type")
public class AuditEventsType {

    public final static AuditEventsType PAYMENT_CREATED = new AuditEventsType("payment_created", "Valid payment received and recorded successfully");
    public final static AuditEventsType PAYMENT_UPDATED = new AuditEventsType("payment_updated", "Payment updated successfully");
    public final static AuditEventsType FEE_CREATED = new AuditEventsType("fee_created", "Valid fee received and recorded successfully");
    public final static AuditEventsType FEE_UPDATED = new AuditEventsType("fee_updated", "Fee updated successfully");
    public final static AuditEventsType FEE_REMOVED = new AuditEventsType("fee_removed", "Fee removed successfully");
    public final static AuditEventsType REMISSION_APPLIED = new AuditEventsType("remission_applied", "Valid remission received and applied to fee successfully");
    public final static AuditEventsType PAYMENT_APPORTIONED = new AuditEventsType("payment_apportioned", "Valid payment received and fee apportioned successfully");
    public final static AuditEventsType ORDER_CREATED = new AuditEventsType("order_created", "Valid Order request received and recorded successfully");

    public final static AuditEventsType PAYMENT_ALLOCATION_CREATED = new AuditEventsType("payment_allocation_created", "Valid offline payment allocated successfully");
    public final static AuditEventsType PAYMENT_ALLOCATION_UPDATED = new AuditEventsType("payment_allocation_updated", "Offline Allocated Payment updated successfully");

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;
}
