package uk.gov.hmcts.payment.api.model;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.gov.hmcts.payment.api.dto.MigratingDataDto;
import uk.gov.hmcts.payment.api.dto.Reference;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface Payment2Repository extends CrudRepository<Payment, Integer>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByReferenceAndPaymentMethod(String reference, PaymentMethod paymentMethod);

    Optional<Payment> findByReferenceAndPaymentProvider(String reference, PaymentProvider paymentProvider);

    Optional<Payment> findByReference(String reference);

    List<Reference> findReferencesByPaymentProviderAndPaymentStatusNotInAndDateCreatedLessThan(
        PaymentProvider paymentProvider, List<PaymentStatus> paymentStatuses, Date targetTime);

    Optional<List<Payment>> findAllByDateCreatedBetween(Date fromDate, Date toDate);

    Optional<List<Payment>> findByDocumentControlNumber(String documentControlNumber);

    Optional<List<Payment>> findByCcdCaseNumber(String ccdCaseNumber);

    Optional<List<Payment>> findByPaymentLinkId(Integer id);

    @Query("SELECT new uk.gov.hmcts.payment.api.dto.MigratingDataDto(p.paymentLink.paymentReference, p.ccdCaseNumber, p.caseReference, p.siteId, p.serviceType) from Payment p " +
        "WHERE (p.paymentLink.id,p.dateCreated ) in (" +
        "\tSELECT p.paymentLink.id, MAX(p.dateCreated) from Payment p GROUP BY p.paymentLink.id HAVING count(*)>1)")
    List<MigratingDataDto> findMigrationDataByPaymentLinkIdAndDateCreatedForMultiRecords();
}
