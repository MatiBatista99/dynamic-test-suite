package ar.cabal.origins;

import ar.cabal.origins.visa.VisaOrigin;

public class OriginFactory {
    public static Origin getOrigin(String origen) {
        switch (origen.toUpperCase()) {
            case "VISA": return new VisaOrigin();
            default: throw new IllegalArgumentException("Origen no soportado: " + origen);
        }
    }
}

