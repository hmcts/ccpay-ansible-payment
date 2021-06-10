package uk.gov.hmcts.payment.api.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.util.Date;

@Entity
@Data
@Builder(builderMethodName = "idempotencyKeysWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeysPK.class)
public class IdempotencyKeys {

    @Generated(GenerationTime.INSERT)
    @Column(name = "id", columnDefinition = "serial", updatable = false)
    private Integer id;

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "request_body")
    private String requestBody;

    @Column(name = "response_body")
    private String responseBody;

    @Id
    @Column(name = "request_hashcode")
    private Integer request_hashcode;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false)
    private Date dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private Date dateUpdated;
}
