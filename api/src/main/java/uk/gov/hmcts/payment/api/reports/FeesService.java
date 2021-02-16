package uk.gov.hmcts.payment.api.reports;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.fees2.register.api.contract.Fee2Dto;
import uk.gov.hmcts.fees2.register.api.contract.FeeVersionDto;

import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;

@Service
public class FeesService {
    private static final Logger LOG = getLogger(FeesService.class);

    private final FeesRegisterAdapter feesRegisterAdapter;

    @Autowired
    public FeesService(FeesRegisterAdapter feesRegisterAdapter) {
        this.feesRegisterAdapter = feesRegisterAdapter;

    }


    public Optional<FeeVersionDto> getFeeVersion(String feeCode, String version) {
        try {
            Optional<Map<String, FeeVersionDto>> feeVersionsDtoMapForAFeeCode = Optional.ofNullable(getFeesVersionsData().get(feeCode));
            FeeVersionDto matchingFeeDtoVersion = null;
            if (feeVersionsDtoMapForAFeeCode.isPresent()) {
                matchingFeeDtoVersion = feeVersionsDtoMapForAFeeCode.get().get(version);
            }
            return Optional.ofNullable(matchingFeeDtoVersion);
        } catch (Exception ex) {
            LOG.error("Error fetching FeeVersion by code:{} and version:{}", feeCode,  version, ex);
        }
        return Optional.empty();
    }

    public Map<String, Map<String, FeeVersionDto>> getFeesVersionsData() {

        Map<String, Map<String, FeeVersionDto>> mapOfFeeVersionsDtoMap = new HashMap<>();
        if(getFeesDtoMap() != null){
            Iterator<Map.Entry<String, Fee2Dto>> iterator = getFeesDtoMap().entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, Fee2Dto> entry = iterator.next();
                Map<String, FeeVersionDto> feeVersionsDtoMap = new HashMap<>();
                if (entry.getValue().getCurrentVersion() != null) {
                    feeVersionsDtoMap.put(entry.getValue().getCurrentVersion().getVersion().toString(),
                        entry.getValue().getCurrentVersion());
                }
                for (FeeVersionDto feeVersion : entry.getValue().getFeeVersionDtos()) {
                    feeVersionsDtoMap.put(feeVersion.getVersion().toString(), feeVersion);
                }

                mapOfFeeVersionsDtoMap.put(entry.getKey(), feeVersionsDtoMap);

            }
        }
        return mapOfFeeVersionsDtoMap;
    }

    public Map<String, Fee2Dto> getFeesDtoMap() {
        LOG.info("Inside getFeesDtoMap in FeesService!!!");
        return feesRegisterAdapter.getFeesDtoMap();
    }

}
