package ar.cabal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CaseContext {
    private String caseName;
    private String mtiCombined;
    private String condicionTarjeta;
    private String resultadoEsperado;
    private String modalidad;
    private String tarjeta;
    private String cvv;
    private String vencimiento;
    private String numComercio;
    private String condicionDisponible;
    private String cuotas;
}

