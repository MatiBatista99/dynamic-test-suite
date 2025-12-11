package ar.cabal;

import ar.cabal.origins.OriginHandler;
import ar.cabal.origins.posnet.PosnetOriginHandler;
import ar.cabal.origins.visa.VisaOriginHandler;
import org.jpos.iso.MUX;
import org.jpos.util.NameRegistrar;

public class MuxFactory {


    public static MUX getMuxByOrigin(String origin) throws NameRegistrar.NotFoundException {
        if (origin == null) return null;
        switch (origin.toUpperCase()) {
            case "VISA":
                return NameRegistrar.get("mux.selftest-visa-mux");
            case "POSNET": ;
                return NameRegistrar.get("mux.selftest-posnet-mux");
            case "LINK": ;
                return NameRegistrar.get("mux.selftest-link-mux");
            default:
                throw new IllegalArgumentException("Origen no soportado: " + origin);
        }
    }
}
