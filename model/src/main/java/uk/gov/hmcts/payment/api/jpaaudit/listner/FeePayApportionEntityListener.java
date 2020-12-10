package uk.gov.hmcts.payment.api.jpaaudit.listner;

import uk.gov.hmcts.payment.api.jpaaudit.model.AuditEventsType;
import uk.gov.hmcts.payment.api.jpaaudit.model.PaymentAuditHistory;
import uk.gov.hmcts.payment.api.model.FeePayApportion;

import javax.persistence.EntityManager;
import javax.persistence.PostPersist;
import javax.transaction.Transactional;

import static javax.transaction.Transactional.TxType.MANDATORY;
import static uk.gov.hmcts.payment.api.jpaaudit.listner.Action.INSERTED;
import static uk.gov.hmcts.payment.api.jpaaudit.model.AuditEventsType.PAYMENT_APPORTIONED;

public class FeePayApportionEntityListener {

    @PostPersist
    public void postPersist(FeePayApportion target) { perform(target, INSERTED, PAYMENT_APPORTIONED); }

    @Transactional(MANDATORY)
    private void perform(FeePayApportion target, Action action, AuditEventsType auditEventsType) {
        EntityManager entityManager = BeanUtil.getBean(EntityManager.class);
        entityManager.persist(PaymentAuditHistory.paymentAuditHistoryWith()
            .ccdCaseNo(target.getCcdCaseNumber())
            .auditType(auditEventsType)
            .auditPayload(target.toString())
            .auditDescription(auditEventsType.getDescription())
            .build());
    }
}
