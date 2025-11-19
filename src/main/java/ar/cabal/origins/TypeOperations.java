package ar.cabal.origins;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter

public enum TypeOperations {

    // Autorizacion
    AUTORIZACION_COMPRA("0200.00", "Compra o Compra con Extracash"),
    AUTORIZACION_ANULACION("0200.02", "Anulación de Compra"),
    AUTORIZACION_DEVOLUCION("0200.20", "Devolución de Compra"),

    // Reverso
    REVERSO_COMPRA("0420.00", "Reverso de compra"),
    REVERSO_ANULACION("0420.02", "Reverso de anulacion"),
    REVERSO_DEVOLUCION("0420.20", "Reverso de devolucion");

    private final String originalMti;
    private final String description;

    private static final Map<String, TypeOperations> LOOKUP_BY_KEY_AND_MTI = new HashMap<>();

    static {
        for (TypeOperations pc : values()) {
            LOOKUP_BY_KEY_AND_MTI.put(pc.getOriginalMti(), pc);
        }
    }

    TypeOperations(String originalMti, String description) {
        this.originalMti = originalMti;
        this.description = description;
    }


    /**
     * Busca un código de proceso a partir del mti
     */
    public static TypeOperations fromKey(String originalMti) {

        return LOOKUP_BY_KEY_AND_MTI.get(originalMti);
    }

}

