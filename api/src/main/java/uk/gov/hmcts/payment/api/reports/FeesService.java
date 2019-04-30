package uk.gov.hmcts.payment.api.reports;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.fees2.register.api.contract.Fee2Dto;
import uk.gov.hmcts.fees2.register.api.contract.FeeVersionDto;

import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;

@Service
@Transactional
public class FeesService {
    private static final Logger LOG = getLogger(FeesService.class);

    private final FeeRepository feeRepository;

    @Autowired
    public FeesService(FeeRepository feeRepository) {
        this.feeRepository = feeRepository;

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

        Iterator<Map.Entry<String, Fee2Dto>> iterator = getFeesDtoMap().entrySet().iterator();
        Map<String, Map<String, FeeVersionDto>> mapOfFeeVersionsDtoMap = new HashMap<>();

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
        return mapOfFeeVersionsDtoMap;
    }

    public Map<String, Fee2Dto> getFeesDtoMap() {
        return feeRepository.getFeesDtoMap();
    }

}
