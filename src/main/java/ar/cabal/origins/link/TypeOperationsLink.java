package ar.cabal.origins.link;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter

public enum TypeOperationsLink {

    // Extraccion
    EXTRACCION("0200.01","0200", "013000", "Extraccion cajero"),

    // Consultas
    CONSULTA_SALDO("0200.31","0200", "311000", "Consulta saldo cajero"),

    // Reversos
    REVERSO_EXTRACCION("0420.01","0420","013000", "Reverso de extraccion cajero"),;

    private final String originalMti;
    private final String mti;
    private final String code;
    private final String description;

    private static final Map<String, TypeOperationsLink> LOOKUP_BY_KEY_AND_MTI = new HashMap<>();

    static {
        for (TypeOperationsLink pc : values()) {
            LOOKUP_BY_KEY_AND_MTI.put(pc.getOriginalMti(), pc);
        }
    }

    TypeOperationsLink(String originalMti, String mti, String code, String description) {
        this.mti=mti;
        this.originalMti = originalMti;
        this.code = code;
        this.description = description;
    }


    /**
     * Busca un código de proceso a partir del mti
     */
    public static TypeOperationsLink fromKey(String originalMti) {

        return LOOKUP_BY_KEY_AND_MTI.get(originalMti);
    }

}

