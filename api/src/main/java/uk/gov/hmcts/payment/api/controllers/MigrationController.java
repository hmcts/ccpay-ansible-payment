package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.payment.api.dto.MigratingDataDto;
import uk.gov.hmcts.payment.api.dto.MigrationSingleDataDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.MigrationService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.InvalidFeeRequestException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentGroupNotFoundException;

import javax.transaction.Transactional;
import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Api(tags = {"Migration Job"})
@SwaggerDefinition(tags = {@Tag(name = "PaymentGroupController", description = "Payment group REST API")})
public class MigrationController {

    @Autowired
    private MigrationService migrationService;

    private final Logger LOG = LoggerFactory.getLogger(MigrationController.class);

    @ApiOperation(value = "Migrating ccd_case_num, case_ref, site_id, service_type data from payment to order table",
        notes = "Migrating ccd_case_num, case_ref, site_id, service_type data from payment to order table")
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "Data updated successfully"),
    })
    @PatchMapping(value = "/migrate-data/{paymentGroupReference}")
    public ResponseEntity<String> updateWithGivenData(@PathVariable String paymentGroupReference, @Valid @RequestBody MigrationSingleDataDto migratingRequestData) {
        MigratingDataDto migratingDataDto = MigratingDataDto.ccdLinkWith()
            .paymentGroupReference(paymentGroupReference)
            .ccdCaseNumber(migratingRequestData.getCcdCaseNumber())
            .caseReference(migratingRequestData.getCaseReference())
            .serviceType(migratingRequestData.getServiceType())
            .siteId(migratingRequestData.getSiteId())
            .build();
        String status = migrationService.updatePaymentFeeLinkWithMigratingData(migratingDataDto);
        return new ResponseEntity<>(status, HttpStatus.ACCEPTED);
    }

    @ApiOperation(value = "Migrating ccd_case_num, case_ref, site_id, service_type data from payment to order table",
        notes = "Migrating ccd_case_num, case_ref, site_id, service_type data from payment to order table")
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "Data updated successfully"),
    })
    @PutMapping(value = "/apply-migration-multiple-records/{paymentType}")
    public ResponseEntity<String> migrateData(@PathVariable String paymentType) {
        if(paymentType.equals("multiple")){
            List<MigratingDataDto> migratingDataDtos = migrationService.findMigrationDataByPaymentLinkIdAndDateCreatedForMultiRecords();
            LOG.info("No of records received: "+migratingDataDtos.size());
            int successCount=0;
            int failCount=0;

            for(MigratingDataDto migratingData: migratingDataDtos){
                String status = migrationService.updatePaymentFeeLinkWithMigratingData(migratingData);
                if(status.equals("COMPLETE")){
                    successCount+=1;
                }else{
                    failCount+=1;
                }
            }
            if(migratingDataDtos.size()==successCount){
                LOG.info("Migration complete for multiple records");
                return new ResponseEntity<>("Status: COMPLETE; No of records updated: "+successCount, HttpStatus.ACCEPTED);
            }
            LOG.info("Migration Incomplete for multiple records");
            return new ResponseEntity<>("Status: INCOMPLETE; No of records updated: "+successCount +" No of records failed: "+failCount, HttpStatus.ACCEPTED);
        }
        return new ResponseEntity<>("Bad Request", HttpStatus.BAD_REQUEST);
    }

}
