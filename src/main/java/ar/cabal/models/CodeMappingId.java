/*package ar.cabal.models;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class CodeMappingId implements Serializable {
    @Column(name = "ORIGEN")
    private String origen;

    @Column(name = "DESTINO")
    private String destino;

    @Column(name = "SSERROR")
    private String ssCode;



    public CodeMappingId(String origen, String destino, String ssCode) {
        this.origen = origen;
        this.destino = destino;
        this.ssCode = ssCode;
    }

    public CodeMappingId() {

    }


    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    // Getter y Setter para destino
    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    // Getter y Setter para ssCode
    public String getSsCode() {
        return ssCode;
    }

    public void setSsCode(String ssCode) {
        this.ssCode = ssCode;
    }
}
*/