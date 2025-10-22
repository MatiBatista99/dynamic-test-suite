package ar.cabal.origins.visa;

import java.util.LinkedHashMap;
import java.util.Map;

public enum IsoTemplate {

    AUTORIZACION(new String[][]{
            {"0", "1100"},
            {"2", "!PAN"},
            {"3", "!PCODE"},
            {"4", "!AMOUNT"},
            {"11", "!STAN"},
            {"12", "!DATE"},
            {"18", "5813"},
            {"22", "!ENTRY_MODE"},
            {"26", "5813"},
            {"32", "06544003"},
            {"33", "06028000"},
            {"35", "!TRACK2"},
            {"37", "230220805121"},
            {"41", "29110001"},
            {"42", "47075440003"},
            {"43", "COOP OBRERA           BAHIA BLANCA 01 AR"},
            {"48", "!CAMPO48"},
            {"49", "032"}
    }),

    REVERSO(new String[][]{
            {"0", "1420"},
            {"2", "!PAN"},
            {"3", "!PCODE"},
            {"4", "!AMOUNT"},
            {"11", "!STAN"},
            {"12", "!DATE"},
            {"25", "4006"},
            {"26", "5411"},
            {"32", "540001"},
            {"33", "028000"},
            {"41", "38010101"},
            {"42", "45994640009"},
            {"48", "!CAMPO48 "},
            {"49", "!CURRENCY"},
            {"53", "2000000001000000"},
            {"56", "!ORIGINALDATA"}
    });

    private final Map<Integer, String> fields;

    IsoTemplate(String[][] flds) {
        fields = new LinkedHashMap<>();
        for (String[] f : flds)
            fields.put(Integer.parseInt(f[0]), f[1]);
    }

    public Map<Integer, String> getFields() {
        return new LinkedHashMap<>(fields);
    }
}

