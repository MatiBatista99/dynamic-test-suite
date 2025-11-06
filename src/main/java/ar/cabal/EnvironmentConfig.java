package ar.cabal;



public class EnvironmentConfig {

    public static String getOrigin() {
        String origin = System.getProperty("origin");
        if (origin == null || origin.isEmpty())
            throw new IllegalStateException("Falta el parametro obligatorio: -Dorigin=<valor>");

        System.out.println("Configuracion cargada correctamente:");
        System.out.println("origin= " + origin);
        return origin;
    }

}

