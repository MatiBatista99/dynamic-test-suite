package ar.cabal.origins.posnet;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter

public enum TypeOperationsPosnet {

    // Autorizacion
    AUTORIZACION_COMPRA("0200.00","0200", "000030", "Compra o Compra con Extracash"),
    //En el excel aparece 0220.02 pero debe ser una autorizacion
    AUTORIZACION_ANULACION("0200.02","0200", "020030", "Anulación de Compra"),
    AUTORIZACION_DEVOLUCION("0200.20","0200", "200030", "Devolución de Compra"),

    // Reverso
    REVERSO_COMPRA("0420.00","0420","000030", "Reverso de compra"),
    REVERSO_ANULACION("0420.02","0420","220030", "Reverso de anulacion"),
    REVERSO_DEVOLUCION("0420.20","0420","200030", "Reverso de devolucion");

    private final String originalMti;
    private final String mti;
    private final String code;
    private final String description;

    private static final Map<String, TypeOperationsPosnet> LOOKUP_BY_KEY_AND_MTI = new HashMap<>();

    static {
        for (TypeOperationsPosnet pc : values()) {
            LOOKUP_BY_KEY_AND_MTI.put(pc.getOriginalMti(), pc);
        }
    }

    TypeOperationsPosnet(String originalMti, String mti, String code, String description) {
        this.mti=mti;
        this.originalMti = originalMti;
        this.code = code;
        this.description = description;
    }


    /**
     * Busca un código de proceso a partir del mti
     */
    public static TypeOperationsPosnet fromKey(String originalMti) {

        return LOOKUP_BY_KEY_AND_MTI.get(originalMti);
    }

}

