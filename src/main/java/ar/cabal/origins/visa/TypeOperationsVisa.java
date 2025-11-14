package ar.cabal.origins.visa;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter

public enum TypeOperationsVisa {

    // Autorizacion
    AUTORIZACION_COMPRA("0200.00","1100", "000000", "Compra o Compra con Extracash"),
    //En el excel aparece 0220.02 pero debe ser una autorizacion
    AUTORIZACION_ANULACION("0200.02","1100", "220000", "Anulación de Compra"),
    AUTORIZACION_DEVOLUCION("0200.20","1100", "200030", "Devolución de Compra"),

    // Reverso
    REVERSO_COMPRA("0420.00","1420","000000", "Reverso de compra"),
    REVERSO_ANULACION("0420.02","1420","220000", "Reverso de anulacion"),
    REVERSO_DEVOLUCION("0420.20","1420","200030", "Reverso de devolucion");

    private final String originalMti;
    private final String mti;
    private final String code;
    private final String description;

    private static final Map<String, TypeOperationsVisa> LOOKUP_BY_KEY_AND_MTI = new HashMap<>();

    static {
        for (TypeOperationsVisa pc : values()) {
            LOOKUP_BY_KEY_AND_MTI.put(pc.getOriginalMti(), pc);
        }
    }

    TypeOperationsVisa(String originalMti, String mti, String code, String description) {
        this.mti=mti;
        this.originalMti = originalMti;
        this.code = code;
        this.description = description;
    }


    /**
     * Busca un código de proceso a partir del mti
     */
    public static TypeOperationsVisa fromKey(String originalMti) {

        return LOOKUP_BY_KEY_AND_MTI.get(originalMti);
    }

}

