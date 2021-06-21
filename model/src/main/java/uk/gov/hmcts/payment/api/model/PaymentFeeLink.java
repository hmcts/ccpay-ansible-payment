package uk.gov.hmcts.payment.api.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@Builder(builderMethodName = "paymentFeeLinkWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_fee_link")
public class PaymentFeeLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payment_reference")
    private String paymentReference;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false)
    private Date dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated", nullable = false)
    private Date dateUpdated;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "payment_link_id", referencedColumnName = "id", nullable = false)
    @ToString.Exclude
    private List<Payment> payments;

    @OneToMany(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_link_id", referencedColumnName = "id", nullable = false)
    @ToString.Exclude
    private List<PaymentFee> fees;


    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "payment_link_id", referencedColumnName = "id", nullable = false)
    @ToString.Exclude
    private List<Remission> remissions;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "payment_link_id", referencedColumnName = "id", nullable = false)
    @ToString.Exclude
    private List<FeePayApportion> apportions;

    @Override
    public int hashCode(){
        return super.hashCode();
    }

}
