package ar.cabal.origins.link;

import ar.cabal.IsoBulkSender;
import ar.cabal.OriginRunner;
import ar.cabal.dtos.Case;
import ar.cabal.origins.OriginHandler;
import ar.cabal.origins.TypeOperations;
import ar.cabal.origins.posnet.TypeOperationsPosnet;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import org.apache.poi.ss.usermodel.Row;
import org.jpos.ee.DB;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class LinkOriginHandler extends OriginHandler {


    private static final String filePath="cfg/link/link_";


    public LinkOriginHandler(String fileServer) {
        this.SERVER=fileServer;
    }


    @Override
    public void buildSpecificCase(Map<String, String> ctx, TypeOperations operations, ISOMsg previousRequest) throws ISOException {
        TypeOperationsLink operation= TypeOperationsLink.fromKey(operations.getOriginalMti());
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

        ctx.put("RRN",
                String.format("%012d",
                        ThreadLocalRandom.current().nextLong(0, 1_000_000_000_000L)));

        if(operation.equals(TypeOperationsLink.REVERSO_EXTRACCION)){
            ctx.put("ORIGINALDATA",previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
            ctx.put("LOCAL_TIME_PREV_RESPONSE",previousRequest.getString(12));
        }
    }

    @Override
    public SmbFile getInputFile(CIFSContext context) throws Exception {
        return new SmbFile(buildPath(IN_SHARE, "homologacion_test_suite_atm.xlsx"), context);
    }

    @Override
    public void setIrcAndSdi(DB db, ISOMsg isoMsgResponse, Row row, String mtiOrigen) throws ISOException {
      String sql = """
            SELECT 
                tl.CODRESPUESTAINTERNO AS irc,
                ac1.description AS irc_desc
            FROM tranlog tl
            LEFT JOIN action_codes ac1 ON ac1.code = TO_NUMBER(tl.CODRESPUESTAINTERNO)
            WHERE tl.codigoMoneda = :currencyCode
              AND tl.ss_stan = :stan
             AND tl.codtransaccioninterno =:mti
            ORDER BY tl.id DESC
            FETCH FIRST 1 ROW ONLY
        """;

            Object[] result = (Object[]) db.session().createNativeQuery(sql)
                    .setParameter("currencyCode", isoMsgResponse.getString(49))
                    .setParameter("stan", ISOUtil.zeropad(isoMsgResponse.getString(11), 12))
                    .setParameter("mti",mtiOrigen.substring(1))
                    .uniqueResult();

            if (result != null) {
                // IRC y descripción
                row.createCell(5).setCellValue(result[0] != null ? result[0].toString() : "");
                row.createCell(6).setCellValue(result[1] != null ? result[1].toString() : "");

            }
    }

    @Override
    public String getName() {
        return "LINK";
    }

    @Override
    public SmbFile getOutputFile(CIFSContext context) throws Exception {
        return new SmbFile(buildPath(OUT_SHARE, "link_test_suite_resultados.xlsx"), context);

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

        ctx.put("TID",
                String.format("%016d", Math.abs(new java.util.Random().nextLong()) % 10000000000000000L));

        // PINBLOCK
        ctx.put("PINBLOCK",c.getPinblock());

        // FECHA FORMATEADA (yyMM)
        try {
            SimpleDateFormat formato = new SimpleDateFormat("MM/yyyy");
            Date fecha = formato.parse(c.getFechaVencimiento());
            SimpleDateFormat destino = new SimpleDateFormat("yyMM");
            ctx.put("EXP", destino.format(fecha));
        } catch (Exception e) {
            throw new RuntimeException("No se puede setear fecha vencimiento tarjeta en case: "+ c.getCaseName());
        }

            String serviceCode = "000";
            // Track2 con toda la lógica actual
            String track2 = c.getTarjeta()
                    + "="
                    + ctx.get("EXP")
                    + serviceCode
                    + c.getCvv()
                    + "0000000000";

            ctx.put("TRACK2", track2);

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



        return ctx;
    }

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public Case getCaseFromRow(Row row) {
        final String tipoStr = getString(row, 1);
        final String mtiStr = getString(row, 2);
        final String resultadoStr = getString(row, 8);

        final String[] tipos = tipoStr.contains("+") ? tipoStr.split("\\+") : new String[]{tipoStr};
        final String[] mtis = mtiStr.contains("-") ? mtiStr.split("-") : new String[]{mtiStr};
        final String[] resultadosEsperados = resultadoStr.contains("/") ? resultadoStr.split("/") : new String[]{resultadoStr};

        final int total = Math.max(mtis.length, Math.max(tipos.length, resultadosEsperados.length));
        final List<Case.SpecificCase> specificCases = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            final Case.SpecificCase specificCase = new Case.SpecificCase();
            specificCase.setMti(mtis[i].trim());
            specificCase.setTipo(i < tipos.length ? tipos[i] : tipos[0]);
            specificCase.setResultadoEsperado(i < resultadosEsperados.length ? resultadosEsperados[i] : resultadosEsperados[0]);
            specificCases.add(specificCase);
        }

        return Case.builder()
                .caseName(getString(row, 0))
                .cuotas(getString(row, 3))
                .condicionTarjeta(getString(row, 4))
                .condicionDisponibleDeLaTarjetaCuenta(getString(row, 5))
                .modalidadComercio(getString(row, 6))
                .amount(getDouble(row,7))
                .tarjeta(getString(row, 9))
                .cvv(getString(row, 10))
                .pinblock(getString(row,11))
                .fechaVencimiento(getString(row, 12))
                .numComercio(getString(row, 13))
                .specificCases(specificCases)
                .build();
    }


    @Override
    public ISOMsg processSpecificCase(
            Map<String,String> caseContext,
            Case.SpecificCase specificCase,
            Case c,
            ISOMsg previousRequest,
            IsoBulkSender sender,
            ConcurrentLinkedQueue<OriginRunner.ResultRecord> results,
            int groupIndex) throws Exception {

        // Crear request según origen
        ISOMsg req = createISOMsgByFile(
                caseContext,
                specificCase.getMti(),
                c.getModalidadComercio(),
                previousRequest
        );


        // Enviar
        ISOMsg resp = sender.send(req);


        // Registrar resultado
        results.add(new OriginRunner.ResultRecord(
                groupIndex,
                resp,
                c.getCondicionTarjeta(),
                c.getCaseName(),
                specificCase
        ));
        return req;
    }
}
