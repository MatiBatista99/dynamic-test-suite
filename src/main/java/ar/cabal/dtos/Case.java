package ar.cabal.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Case {
    private String caseName;
    private String cuotas;
    private String condicionTarjeta;
    private String condicionDisponibleDeLaTarjetaCuenta;
    private String modalidadComercio;
    private Double amount;
    private String tarjeta;
    private String cvv;
    private String pinblock;
    private String fechaVencimiento;
    private String numComercio;
    private List<SpecificCase> specificCases;


    @Data
    public static class SpecificCase{
        private String tipo;
        private String mti;
        private String resultadoEsperado;
    }

}
