package ar.cabal.origins.visa;

import ar.cabal.dtos.Case;
import ar.cabal.origins.OriginHandler;
import ar.cabal.origins.TypeOperations;
import ar.cabal.origins.posnet.TypeOperationsPosnet;
import ar.cabal.qmux.QMux;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.MUX;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


public class VisaOriginHandler extends OriginHandler {


    private static final String filePath="cfg/visa/visa_";

    private static final String[] keysMux= new String[] {"12", "37"};

    public VisaOriginHandler(String fileServer) {
        this.SERVER=fileServer;
    }


    @Override
    public SmbFile getOutputFile(CIFSContext context) throws Exception {
        return new SmbFile(buildPath(OUT_SHARE, "visa_test_suite_resultados.xlsx"), context);
    }


    /*
    public void prepareRequest(ISOMsg req, ISOMsg previousResponse, Case.SpecificCase specificCase) throws ParseException {
        //ISOMsg request=createISOMsgByFile(c,specificCase);

        if (!"0200.00".equalsIgnoreCase(specificCase.getMti()) && previousResponse != null) {
            req.set(37, previousResponse.getString(37));
            req.set(11, previousResponse.getString(11));
            req.set(41, previousResponse.getString(41));
        }
        if(specificCase.getMti().toLowerCase().contains("0420")  && previousResponse != null){
            Date date=getDate(previousResponse.getString(12));
            String dateHoy=getDateDay(date);
            req.set(56,"1100"+previousResponse.getString(11)+dateHoy+getDateTime(date)+"00"+dateHoy+"0000");
        }
    } */

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
            throw new RuntimeException("No se puede setear fecha vencimiento tarjeta");
        }

        // =======================
        // ENTRY MODE + TRACK2
        // =======================
        if ("MOSTRADOR".equalsIgnoreCase(c.getModalidadComercio())) {

            // Entry mode banda
            ctx.put("ENTRYMODE", "M00101254001");


        } else if ("INTERNET".equalsIgnoreCase(c.getModalidadComercio())) {
            ctx.put("ENTRYMODE", "200181684101");
        }

        String serviceCode = "000";

        // Track2 con toda la lógica actual
        String track2 = c.getTarjeta()
                + "D"
                + ctx.get("EXP")
                + serviceCode
                + c.getCvv()
                + "0000000";

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
        // RRN
        // =======================

        ctx.put("RRN",
                String.format("%012d",
                        ThreadLocalRandom.current().nextLong(0, 1_000_000_000_000L)));

        // =======================
        // TERMINAL ID
        // =======================
        ctx.put("TID",
                String.valueOf((int)(Math.random() * 90000000) + 10000000));

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

        return ctx;
    }




    @Override
    public void buildSpecificCase(Map<String, String> ctx, TypeOperations operations, ISOMsg previousRequest) throws ISOException {
        TypeOperationsVisa operation= TypeOperationsVisa.fromKey(operations.getOriginalMti());
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
        ctx.put("TRANSMISSION_DATE", getNowFormatDate("yyMMddHHmmss"));


        if(operation.equals(TypeOperationsVisa.REVERSO_COMPRA) || operation.equals(TypeOperationsVisa.REVERSO_ANULACION) || operation.equals(TypeOperationsVisa.REVERSO_DEVOLUCION)){
            ctx.put("ORIGINALDATA",previousRequest.getMTI()+ ctx.get("STAN") + ctx.get("LOCAL_DATE")+ctx.get("LOCAL_TIME") + "00"+ ctx.get("LOCAL_DATE") + "0000");
        }
    }

    @Override
    public void setKeysMux(QMux qMux) {
        qMux.setKeys(keysMux);
    }

    @Override
    public String getName() {
        return "VISA";
    }


    @Override
    public ISOMsg createISOMsgByFile(Map<String, String> caseContext, String mti, String modalidadComercio, ISOMsg previousRequest) throws ISOException, IOException {
        TypeOperations operation = TypeOperations.fromKey(mti);

        if (operation == null) {
            throw new ISOException("No se encontró definición MTI+PCODE para: " + mti);
        }

        String filename = getFilename(operation,filePath,modalidadComercio);

        ISOMsg msg = getMessage(filename);

        // Log opcional
        System.out.println("[DEBUG] Archivo XML seleccionado: " + filename);

        //Agregamos MTI, PCode y fechas segun operacion
        buildSpecificCase(caseContext,operation,previousRequest);

        return applyRequestProps(msg, caseContext);
    }



/*
    @Override
    public String getFilename(TypeOperations operation, String modalidad) throws ISOException {

        StringBuilder filename = new StringBuilder("cfg/visa/visa_");

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
                filename.append("devolucion");
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

 */





}
