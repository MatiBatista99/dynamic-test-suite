/*package ar.cabal.models;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;



@Entity
@Table(name = "MAPEOCODIGOS")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class CodeMapping implements Serializable {

    @EmbeddedId
    private CodeMappingId id;

    @Column(name = "DSERROR")
    private String dsCode;


    public String getOrigen() {
        return id.getOrigen();
    }

    public void setOrigen(String origen) {
        id.setOrigen(origen);
    }

    public String getDestino() {
        return id.getDestino();
    }

    public void setDestino(String destino) {
        id.setDestino(destino);
    }

    public String getSsCode() {
        return id.getSsCode();
    }

    public String getDsCode(){return this.dsCode;}

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CodeMapping other = (CodeMapping) obj;
        return !((id == null && other.id != null) || (id != null && !id.equals(other.id)));
    }



}
*/
