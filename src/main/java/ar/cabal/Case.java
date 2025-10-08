package ar.cabal;

import lombok.Data;

@Data
public class Case {
    private String caseName;
    private String tipo;
    private String mti;
    private String cuotas;
    private String condicionTarjeta;
    private String condicionDisponibleDeLaTarjetaCuenta;
    private String modalidadComercio;
    private String resultadoEsperado;
    private String resultado;
    private String tarjeta;
    private String cvv;
    private String fechaVencimiento;
    private String numComercio;

}
