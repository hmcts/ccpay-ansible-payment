package uk.gov.hmcts.payment.api.controllers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.dto.MigratingDataDto;
import uk.gov.hmcts.payment.api.dto.MigrationSingleDataDto;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;
import uk.gov.hmcts.payment.api.service.MigrationService;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class MigrationControllerTest {

    @Mock
    private MigrationService migrationService;

    @InjectMocks
    private MigrationController migrationController;

    @Test
    public void testupdateWithGivenData(){
        MigrationSingleDataDto mockRequestDto = MigrationSingleDataDto.migrationSingleDataDto()
            .ccdCaseNumber("1234567890123456")
            .caseReference("case-reference")
            .siteId("AA07")
            .serviceType("Divorce")
            .build();
        when(migrationService.updatePaymentFeeLinkWithMigratingData(any(MigratingDataDto.class))).thenReturn("COMPLETE");
        ResponseEntity<String> response = migrationController.updateWithGivenData("1",mockRequestDto);
        assertEquals(response.getBody(),"COMPLETE");
    }

    @Test
    public void testDataMigration(){
        when(migrationService.findMigrationDataByPaymentLinkIdAndDateCreatedForMultiRecords()).thenReturn(Arrays.asList(MigratingDataDto.ccdLinkWith()
            .ccdCaseNumber("1234567890123456")
            .caseReference("case-reference")
            .siteId("AA07")
            .serviceType("Divorce")
            .paymentGroupReference("1")
            .build()
        ));
        when(migrationService.updatePaymentFeeLinkWithMigratingData(any(MigratingDataDto.class))).thenReturn("COMPLETE");
        ResponseEntity<String> response = migrationController.migrateData("multiple");
        assertEquals(response.getBody(),"Status: COMPLETE; No of records updated: 1");
    }


    @Test
    public void testDataMigrationWithFailure(){
        when(migrationService.findMigrationDataByPaymentLinkIdAndDateCreatedForMultiRecords()).thenReturn(Arrays.asList(MigratingDataDto.ccdLinkWith()
            .ccdCaseNumber("1234567890123456")
            .caseReference("case-reference")
            .siteId("AA07")
            .serviceType("Divorce")
            .paymentGroupReference("1")
            .build()
        ));
        when(migrationService.updatePaymentFeeLinkWithMigratingData(any(MigratingDataDto.class))).thenReturn("INCOMPLETE");
        ResponseEntity<String> response = migrationController.migrateData("multiple");
        assertEquals(response.getBody(),"Status: INCOMPLETE; No of records updated: 0 No of records failed: 1");
    }


}
