package ar.cabal.origins.posnet;

import ar.cabal.dtos.Case;
import ar.cabal.origins.OriginHandler;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.q2.iso.TaskAdaptor;
import org.jpos.util.Log;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class PosnetOriginHandler extends OriginHandler {


    public PosnetOriginHandler(String fileServer) {
        this.SERVER=fileServer;
    }

    @Override
    public SmbFile getInputFile(CIFSContext context) throws Exception {
        return new SmbFile(buildPath(IN_SHARE, "posnet_test_suite.xlsx"), context);
    }

    @Override
    public SmbFile getOutputFile(CIFSContext context) throws Exception {
        return new SmbFile(buildPath(OUT_SHARE, "posnet_test_suite_resultados.xlsx"), context);

    }

    /*
    @Override
    public void prepareRequest(ISOMsg req, ISOMsg previousResponse, Case.SpecificCase specificCase) throws ISOException, ParseException {

        if (!"0200.00".equalsIgnoreCase(specificCase.getMti()) && previousResponse != null) {
            String date=previousResponse.getString(17);
            req.set(90, "0200"+previousResponse.getString(37)+date+previousResponse.getString(12)+date+"000000000000");
;
        }
        if(specificCase.getMti().toLowerCase().contains("0420")  && previousResponse != null){
            Date date=getDate(previousResponse.getString(12));
            String dateHoy=getDateDay(date);
            req.set(56,"1100"+previousResponse.getString(11)+dateHoy+getDateTime(date)+"00"+dateHoy+"0000");
        }
    }

     */

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
    public Map<String, String> buildSpecificCase(Map<String, String> ctx, TypeOperationsPosnet operation, ISOMsg previousRequest) throws ISOException {
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
                ctx.put("ORIGINF", previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
                break;
            case AUTORIZACION_DEVOLUCION:
                ctx.put("ORIGINF", previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
                break;
            case REVERSO_COMPRA:
                TaskAdaptor
                ctx.put("COD_REVER","R9");
                ctx.put("ORIGINALDATA",previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
                ctx.put("NUMAUTH",previousRequest.getString(38));
                break;
            case REVERSO_ANULACION:
                ctx.put("COD_REVER","R9");
                ctx.put("ORIGINALDATA",previousRequest.getMTI()+previousRequest.getString(37)+previousRequest.getString(13)+previousRequest.getString(12)+previousRequest.getString(13)+"000000000000");
                ctx.put("NUMAUTH",previousRequest.getString(38));
                break;
        }
        return ctx;
    }


    @Override
    public ISOMsg createISOMsgByFile(Map<String, String> caseContext, String mti, String modadlidadComercio,ISOMsg previousRequest) throws ISOException, IOException {

        TypeOperationsPosnet operation = TypeOperationsPosnet.fromKey(mti);

        if (operation == null) {
            throw new ISOException("No se encontró definición MTI+PCODE para: " + mti);
        }


        String filename = getFilename(operation,modadlidadComercio);

        ISOMsg msg = getMessage(filename);

        // Log opcional
        System.out.println("[DEBUG] Archivo XML seleccionado: " + filename);

        //Agregamos MTI, PCode y fechas segun operacion
        buildSpecificCase(caseContext,operation,previousRequest);

        return applyRequestProps(msg,caseContext);
    }



    /**
     * Construye el nombre de archivo VISA basado en la operación y modalidad.
     */
    private String getFilename(TypeOperationsPosnet operation, String modalidad) throws ISOException {

        StringBuilder filename = new StringBuilder("cfg/posnet/pos_");

        switch (operation) {
            // === AUTORIZACIONES ===
            case AUTORIZACION_COMPRA:
                filename.append("auth_");
                filename.append(resolveModalidad(modalidad, operation));
                break;

            case AUTORIZACION_ANULACION:
                filename.append("anul");
                break;

            case AUTORIZACION_DEVOLUCION:
                filename.append("dev");
                break;

            // === REVERSOS ===
            case REVERSO_COMPRA:
            case REVERSO_ANULACION:
            case REVERSO_DEVOLUCION:
                filename.append("rever");
                break;

            default:
                throw new ISOException("Tipo de operación no soportado: " + operation.name());
        }

        filename.append(".xml");
        return filename.toString();
    }


    protected String resolveModalidad(String modalidad, TypeOperationsPosnet op)
            throws ISOException {

        String result = modalidadMap.get(modalidad);
        if (result == null) {
            throw new ISOException("Modalidad de comercio desconocida: " + modalidad + " para operación " + op.name());
        }
        return result;
    }





}
