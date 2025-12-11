package ar.cabal.origins;


import ar.cabal.origins.link.LinkOriginHandler;
import ar.cabal.origins.posnet.PosnetOriginHandler;
import ar.cabal.origins.visa.VisaOriginHandler;

public class OriginHandlerFactory {

    public static OriginHandler getHandler(String origin, String fileServer) {
        if (origin == null) return null;
        switch (origin.toUpperCase()) {
            case "VISA":
                return new VisaOriginHandler(fileServer);
            case "POSNET": ;
                return new PosnetOriginHandler(fileServer);
            case "LINK": ;
                return new LinkOriginHandler(fileServer);
            default:
                throw new IllegalArgumentException("Origen no soportado: " + origin);
        }
    }
}
