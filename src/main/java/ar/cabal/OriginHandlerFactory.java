package ar.cabal;


public class OriginHandlerFactory {

    public static OriginHandler getHandler(String origin, String fileServer) {
        if (origin == null) return null;
        switch (origin.toUpperCase()) {
            case "VISA":
                return new VisaOriginHandler(fileServer);
            //case "POSNET": ;
            default:
                throw new IllegalArgumentException("Origen no soportado: " + origin);
        }
    }
}
