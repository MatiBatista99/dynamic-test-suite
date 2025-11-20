package ar.cabal.origins.posnet;

import ar.cabal.dtos.Case;
import ar.cabal.origins.OriginHandler;
import ar.cabal.origins.TypeOperations;
import ar.cabal.qmux.QMux;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import org.apache.poi.ss.usermodel.Row;
import org.jpos.ee.DB;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.MUX;


import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class PosnetOriginHandler extends OriginHandler {

    private static final String filePath="cfg/posnet/pos_";


    public PosnetOriginHandler(String fileServer) {
        this.SERVER=fileServer;
    }

    @Override
    public void buildSpecificCase(Map<String, String> ctx, TypeOperations operations, ISOMsg previousRequest) throws ISOException {
        TypeOperationsPosnet operation= TypeOperationsPosnet.fromKey(operations.getOriginalMti());
        // =======================
        // MTI y PROCESS CODE
        // =======================
        ctx.put("MTI", operation.getMti());

        ctx.put("PCODE", operation.getCode());

        // =======================
        // Fechas ISO (local, hora, transmisión)
        // =======================
        ctx.put("LOCAL_DATE", getNowFormatDate("MMdd"));
        ctx.put("LOCAL_TIME", getNowFormatDate("HHmmss"));
        ctx.put("CAPTURE_DATE",  getNowFormatDate("MMdd"));
        ctx.put("TRANSMISSION_DATE", getNowFormatDate("MMddHHmmss"));


        switch (operation)  {
            case AUTORIZACION_COMPRA:
                ctx.put("RRN",
                        String.format("%012d",
                                ThreadLocalRandom.current().nextLong(0, 1_000_000_000_000L)));
                ctx.put("TID",
                        String.format("%016d", Math.abs(new java.util.Random().nextLong()) % 10000000000000000L));
                break;
            case AUTORIZACION_ANULACION:
                /*
                ctx.put("ORIGINF", previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
                break; */
            case AUTORIZACION_DEVOLUCION:
                ctx.put("ORIGINF", previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
                break;
            case REVERSO_COMPRA:
               /* ctx.put("COD_REVER","R9");
                ctx.put("ORIGINALDATA",previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
                ctx.put("NUMAUTH",previousRequest.getString(38));
                break; */
            case REVERSO_ANULACION:
                ctx.put("COD_REVER","R9");
                ctx.put("ORIGINALDATA",previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
                ctx.put("NUMAUTH",previousRequest.getString(38));
                break;
        }
    }

    @Override
    public void setIrcAndSdi(DB db,ISOMsg isoMsgResponse, Row row, String mtiOrigen) throws ISOException {
        String sql = """
        SELECT 
            tl.CODRESPUESTAINTERNO AS irc,
            ac1.description AS irc_desc
        FROM tranlog tl
        LEFT JOIN action_codes ac1 ON ac1.code = TO_NUMBER(tl.CODRESPUESTAINTERNO)
        WHERE tl.codigoMoneda = :currencyCode
          AND tl.ss_stan = :stan
          AND tl.ss_rrn = :rrn
          AND tl.idTerminal = :tid
         AND tl.codtransaccioninterno =:mti
        ORDER BY tl.id DESC
        FETCH FIRST 1 ROW ONLY
    """;

        Object[] result = (Object[]) db.session().createNativeQuery(sql)
                .setParameter("currencyCode", isoMsgResponse.getString(49))
                .setParameter("stan", ISOUtil.zeropad(isoMsgResponse.getString(11), 12))
                .setParameter("rrn", isoMsgResponse.getString(37))
                .setParameter("tid", isoMsgResponse.getString(41))
                .setParameter("mti", mtiOrigen.substring(1))
                .uniqueResult();

        if (result != null) {
            // IRC y descripción
            row.createCell(5).setCellValue(result[0] != null ? result[0].toString() : "");
            row.createCell(6).setCellValue(result[1] != null ? result[1].toString() : "");

        }
    }

    @Override
    public SmbFile getOutputFile(CIFSContext context) throws Exception {
        return new SmbFile(buildPath(OUT_SHARE, "posnet_test_suite_resultados.xlsx"), context);

    }

    @Override
    public String getName() {
        return "POSNET";
    }


    @Override
    public Map<String, String> buildContextCase(Case c) throws ISOException {
        Map<String, String> ctx = new HashMap<>();

        // =======================
        // PAN + VTO
        // =======================
        ctx.put("PAN", c.getTarjeta());
        ctx.put("FECHA_VENC_ORIGINAL", c.getFechaVencimiento());

        ctx.put("STAN",getStan());

        // FECHA FORMATEADA (yyMM)
        try {
            SimpleDateFormat formato = new SimpleDateFormat("MM/yyyy");
            Date fecha = formato.parse(c.getFechaVencimiento());
            SimpleDateFormat destino = new SimpleDateFormat("yyMM");
            ctx.put("EXP", destino.format(fecha));
        } catch (Exception e) {
            throw new RuntimeException("No se puede setear fecha vencimiento tarjeta en case: "+ c.getCaseName());
        }

        // =======================
        // ENTRY MODE + TRACK2
        // =======================
        if ("MOSTRADOR".equalsIgnoreCase(c.getModalidadComercio())) {

            // Entry mode banda
            ctx.put("ENTRYMODE", "021");

            String serviceCode = "000";

            // Track2 con toda la lógica actual
            String track2 = c.getTarjeta()
                    + "D"
                    + ctx.get("EXP")
                    + serviceCode
                    + c.getCvv()
                    + "0000000";

            ctx.put("TRACK2", track2);


        } else if ("INTERNET".equalsIgnoreCase(c.getModalidadComercio())) {
            ctx.put("ENTRYMODE", "011");

            ctx.put("TRACK2",c.getTarjeta()+"="+ctx.get("EXP"));

        }



        // =======================
        // MONTO (campo 4)
        // =======================
        /*String amount12 = ISOUtil.zeropad(
                (c.getAmount().longValue() + "00"),
                12
        ); */
        ctx.put("AMOUNTTRN", ISOUtil.zeropad(
                (c.getAmount().longValue() + "00"),
                12
        ));
        //ctx.put("AMOUNT", String.valueOf(c.getAmount().longValue() * 100));
        //ctx.put("CURRENCY", "032");

        // =======================
        // Comercio
        // =======================
        ctx.put("MID", c.getNumComercio());
        //  ctx.put("COD_ACTIVIDAD", "7299");

        // =======================
        // PRESENTADOR + ADQUIRENTE
        // =======================
        // ctx.put("PRESENTADOR", "06028000");
        //ctx.put("ADQUIRENTE", "06544003");


        // =======================
        // Cuotas + Plan
        // =======================
        String cuotas = c.getCuotas();
        if (cuotas.length() < 2) cuotas = "0" + cuotas;

        ctx.put("PLAN", "1");
        ctx.put("CUOTAS", cuotas);

        // =======================
        // CUSTOM FIELD 47
        // =======================
        ctx.put("CAMPO47", "00525" + c.getCvv());

        // =======================
        // CAMPO 48 (Datos privados)
        // =======================
        String campo48 =
                "053003" +
                        "1" + cuotas +              // plan + cuotas
                        "006      " +
                        "019                   " +
                        "08        " +
                        "003" + c.getCvv();

        ctx.put("CAMPO48", campo48);

        ctx.put("CAMPO63", "&amp; 0000500156! 0400020  00000000032      Y ! C000026 " + c.getCvv() + "     1434      00 1 00 ! C400012 000000000082! R200046 " + ctx.get("CUOTAS") + "    " + ctx.get("PLAN") + "                             0         ");
        return ctx;
    }


    @Override
    public ISOMsg createISOMsgByFile(Map<String, String> caseContext, String mti, String modadlidadComercio,ISOMsg previousRequest) throws ISOException, IOException {

        TypeOperations operation = TypeOperations.fromKey(mti);

        if (operation == null) {
            throw new ISOException("No se encontró definición MTI+PCODE para: " + mti);
        }

        String filename = getFilename(operation,filePath,modadlidadComercio);

        ISOMsg msg = getMessage(filename);

        // Log opcional
        System.out.println("[DEBUG] Archivo XML seleccionado: " + filename);

        //Agregamos MTI, PCode y fechas segun operacion
        buildSpecificCase(caseContext,operation,previousRequest);

        return applyRequestProps(msg,caseContext);
    }



}
