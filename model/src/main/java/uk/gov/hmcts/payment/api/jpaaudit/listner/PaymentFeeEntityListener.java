package uk.gov.hmcts.payment.api.jpaaudit.listner;

import uk.gov.hmcts.payment.api.jpaaudit.model.AuditEventsType;
import uk.gov.hmcts.payment.api.jpaaudit.model.PaymentAuditHistory;
import uk.gov.hmcts.payment.api.model.PaymentFee;

import javax.persistence.EntityManager;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.transaction.Transactional;

import static javax.transaction.Transactional.TxType.MANDATORY;
import static uk.gov.hmcts.payment.api.jpaaudit.listner.Action.*;
import static uk.gov.hmcts.payment.api.jpaaudit.model.AuditEventsType.*;

public class PaymentFeeEntityListener {

    @PostPersist
    public void postPersist(PaymentFee target) { perform(target, INSERTED, FEE_CREATED); }

    @PostUpdate
    public void postUpdate(PaymentFee target) {
        perform(target, UPDATED, FEE_UPDATED);
    }

    @PostRemove
    public void postRemove(PaymentFee target) {
        perform(target, DELETED, FEE_REMOVED);
    }

    @Transactional(MANDATORY)
    private void perform(PaymentFee target, Action action, AuditEventsType auditEventsType) {
        EntityManager entityManager = BeanUtil.getBean(EntityManager.class);
        entityManager.persist(PaymentAuditHistory.paymentAuditHistoryWith()
            .ccdCaseNo(target.getCcdCaseNumber())
            .auditType(auditEventsType)
            .auditPayload(target.toString())
            .auditDescription(auditEventsType.getDescription())
            .build());
    }
}
