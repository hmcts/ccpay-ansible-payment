package uk.gov.hmcts.payment.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.dto.MigratingDataDto;
import uk.gov.hmcts.payment.api.exceptions.OrderReferenceNotFoundException;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentGroupNotFoundException;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class MigrationServiceImpl implements MigrationService{

    private final Logger LOG = LoggerFactory.getLogger(MigrationServiceImpl.class);

    @Autowired
    private Payment2Repository payment2Repository;

    @Autowired
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Override
    public List<MigratingDataDto> findMigrationDataByPaymentLinkIdAndDateCreatedForMultiRecords() {
        return payment2Repository.findMigrationDataByPaymentLinkIdAndDateCreatedForMultiRecords();
    }


    @Override
    @Transactional
    public String updatePaymentFeeLinkWithMigratingData(MigratingDataDto migratingDataDto) {
        String status = "INCOMPLETE";
        LOG.info("Data Migration in Progress for: Payment Group Ref: "+migratingDataDto.getPaymentGroupReference()+" CcdCaseNumber: "+migratingDataDto.getCcdCaseNumber()+" " +
            "CaseReference: "+ migratingDataDto.getCaseReference()+ "serviceType: "+migratingDataDto.getServiceType()+"" +
            "siteId: "+migratingDataDto.getSiteId());
        try{
            PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.findByPaymentReference(migratingDataDto.getPaymentGroupReference()).orElseThrow(PaymentGroupNotFoundException::new);
            paymentFeeLink.setCcdCaseNumber(migratingDataDto.getCcdCaseNumber()!=null?migratingDataDto.getCcdCaseNumber():paymentFeeLink.getCcdCaseNumber());
            paymentFeeLink.setCaseReference(migratingDataDto.getCaseReference()!=null?migratingDataDto.getCaseReference():paymentFeeLink.getCaseReference());
            paymentFeeLink.setOrgId(migratingDataDto.getSiteId()!=null?migratingDataDto.getSiteId():paymentFeeLink.getOrgId());
            paymentFeeLink.setEnterpriseServiceName(migratingDataDto.getServiceType()!=null?migratingDataDto.getServiceType():paymentFeeLink.getEnterpriseServiceName());
            paymentFeeLinkRepository.save(paymentFeeLink);
            status = "COMPLETE";
            LOG.info("Migration Success for "+migratingDataDto.getPaymentGroupReference() );

        }catch (PaymentGroupNotFoundException exception){
            LOG.info("Migration Failed for "+migratingDataDto.getPaymentGroupReference()+"due to "+ exception.toString());
        }catch (Exception exception){
            LOG.info("Migration Failed for "+migratingDataDto.getPaymentGroupReference()+"due to "+ exception.toString());
        }
        return status;
    }
}
