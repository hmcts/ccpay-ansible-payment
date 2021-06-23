package uk.gov.hmcts.payment.api.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.payment.api.domain.model.ApproverAction;
import uk.gov.hmcts.payment.api.dto.RefundDto;

import java.util.Map;

@Service
public class RefundDomainServiceImpl implements RefundDomainService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundDomainServiceImpl.class);

    @Override
    public Map create(RefundDto refundDto, String paymentReference, MultiValueMap<String, String> headers) {

        LOG.info("Refund Created !!!!!");
        return null;
    }

    @Override
    public Boolean action(String refundReference, ApproverAction approverAction, MultiValueMap<String, String> headers, RefundDto refundDto) {

        LOG.info("Refund Reason : " + refundDto.getReason());
        switch (approverAction){
            case REJECT:
                LOG.info(refundReference + " Refund Rejected !!!!!");
                break;
            case SENDBACK:
                LOG.info(refundReference + " Refund Send Back !!!!!");
                break;
            case APPROVE:
                LOG.info(refundReference + " Refund Approved !!!!!");
                break;
        }
        return null;
    }

    @Override
    public Boolean update(RefundDto refundDto, String refundReference, MultiValueMap<String, String> headers) {

        LOG.info("Refund Updated !!!!!");
        return null;
    }
}
