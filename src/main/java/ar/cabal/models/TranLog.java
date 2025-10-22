/*package ar.cabal.models;



import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

@Entity
@Table(name = "TRANLOG")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "SUBCLASS", length = 3, discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("TL")
@Data
public class TranLog extends org.jpos.ee.Cloneable
        implements Serializable {
    @Id
    @Column(name = "ID", unique = true, nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



}

 */
