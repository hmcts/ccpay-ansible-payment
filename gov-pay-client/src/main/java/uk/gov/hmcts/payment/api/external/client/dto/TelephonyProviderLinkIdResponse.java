package uk.gov.hmcts.payment.api.external.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class TelephonyProviderLinkIdResponse {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("LinkId")
    private String linkId;
}
