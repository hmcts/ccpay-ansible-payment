package uk.gov.hmcts.payment.referencedata.controllers;

import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.referencedata.dto.SiteDTO;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;

import java.util.List;

@RestController
@Api(tags = {"Reference Data"})
@SwaggerDefinition(tags = {@Tag(name = "ReferenceDataController", description = "Reference Data REST API")})
public class ReferenceDataController {

    @Autowired
    private SiteService<Site, String> siteService;

    @ApiOperation(value = "Get allowed sites")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Sites retrieved successfully")
    })
    @GetMapping(value = "/reference-data/sites")
    @ResponseBody
    public ResponseEntity<List<SiteDTO>> getSites() {
        return new ResponseEntity<>(SiteDTO.fromSiteList(siteService.getAllSites()), HttpStatus.OK);
    }

}
