package ar.cabal.origins;

import ar.cabal.IsoBulkSender;
import ar.cabal.OriginRunner;
import ar.cabal.dtos.Case;
import ar.cabal.origins.posnet.TypeOperationsPosnet;
import ar.cabal.origins.visa.TypeOperationsVisa;
import ar.cabal.qmux.QMux;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.bouncycastle.util.encoders.UTF8;
import org.jpos.ee.DB;
import org.jpos.iso.*;
import org.jpos.iso.packager.XMLPackager;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.space.SpaceUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class OriginHandler {

    protected String SERVER;
    protected static final String IN_SHARE = "file-server/in";
    protected static final String OUT_SHARE = "file-server/out";
    protected static final Map<String, String> modalidadMap = Map.of(
            "MOSTRADOR", "magstripe",
            "INTERNET", "man"
    );

    public ISOMsg createISOMsgByFile(Map<String, String> caseContext, String mti, String modadlidadComercio,ISOMsg previousRequest) throws ISOException, IOException {

        TypeOperations operation = TypeOperations.fromKey(mti);

        if (operation == null) {
            throw new ISOException("No se encontró definición MTI+PCODE para: " + mti);
        }

        String filename = getFilename(operation,getFilePath(),modadlidadComercio);

        ISOMsg msg = getMessage(filename);

        // Log opcional
        System.out.println("[DEBUG] Archivo XML seleccionado: " + filename);

        //Agregamos MTI, PCode y fechas segun operacion
        buildSpecificCase(caseContext,operation,previousRequest);

        return applyRequestProps(msg,caseContext);
    }


    public void saveOutputFile(Workbook workbook, CIFSContext context) throws Exception {
        try (var out = new jcifs.smb.SmbFileOutputStream(getOutputFile(context))) {
            workbook.write(out);
            out.flush();
        }
        workbook.close();
    }

    protected String buildPath(String share, String filename) {
        return "smb://" + SERVER + "/" + share + "/" + filename;
    }

    public abstract void buildSpecificCase(Map<String, String> ctx, TypeOperations operation, ISOMsg previousRequest) throws ISOException;


    public  abstract SmbFile getInputFile(CIFSContext context) throws Exception;

    public abstract void setIrcAndSdi(DB db, ISOMsg isoMsgResponse, Row row, String mtiOrigen) throws ISOException;

    public abstract String getName();

    public abstract SmbFile getOutputFile(CIFSContext context) throws Exception;


    public abstract Map<String, String> buildContextCase(Case c) throws ISOException;

    public abstract String getFilePath();

    public abstract Case  getCaseFromRow(Row row);

    protected static String getString(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return cell.getCellType() == CellType.NUMERIC ?
                String.valueOf((long)cell.getNumericCellValue()) :
                cell.getStringCellValue().toUpperCase();
    }

    protected Double getDouble(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 100.0;
        return cell.getCellType() == CellType.NUMERIC ?
                cell.getNumericCellValue():
                100.00;
    }
    public ISOMsg getMessage (String filename)
            throws IOException, ISOException
    {
        File f = new File(filename);
        ISOMsg m = null;
        if (f.canRead()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] b  = new byte[fis.available()];
                fis.read (b);
                m = new ISOMsg ();
                m.setPackager (new XMLPackager());
                try {
                    m.unpack(b);
                } catch (ISOException e) {
                    throw new ISOException ("Error parsing '" + filename + "'", e);
                }
            }
        }
        return m;
    }


    public ISOMsg applyRequestProps(ISOMsg m, Map<String, String> ctx)
            throws ISOException
    {
        int maxField = m.getMaxField();
        for (int i = 0; i <= maxField; i++) {
            if (m.hasField(i)) {
                ISOComponent comp = m.getComponent(i);
                if (comp instanceof ISOMsg) {
                    applyRequestProps((ISOMsg) comp, ctx);
                }
                else if (comp instanceof ISOField) {
                    String currentValue = (String) comp.getValue();
                    if (currentValue != null && currentValue.startsWith("!")) {
                        String key = currentValue.substring(1);
                        if (ctx.containsKey(key)) {
                            if(getName().equals("LINK") && i==52) {
                                    byte[] pinblockBytes = ISOUtil.hex2byte(ctx.get(key));
                                    m.set(i, pinblockBytes);
                            }else {
                                m.set(i, ctx.get(key));
                            }
                        } else {
                            System.out.println("⚠️ Missing key in context: " + key);
                        }
                    }
                }
            }
        }
        return m;
    }

    public abstract ISOMsg processSpecificCase(
            Map<String,String> caseContext,
            Case.SpecificCase specificCase,
            Case c,
            ISOMsg previousRequest,
            IsoBulkSender sender,
            ConcurrentLinkedQueue<OriginRunner.ResultRecord> results,
            int groupIndex) throws Exception;

    protected Date getDate(String date) throws ParseException {
        SimpleDateFormat simpleDateFormat= new SimpleDateFormat("yyMMddHHmmss");
        return  simpleDateFormat.parse(date);
    }

    protected String getDateDay(Date date)  {
        SimpleDateFormat simpleDateFormat= new SimpleDateFormat("MMdd");
        return  simpleDateFormat.format(date);
    }

    protected String getDateTime(Date date)  {
        SimpleDateFormat simpleDateFormat= new SimpleDateFormat("HHmmss");
        return  simpleDateFormat.format(date);
    }


    protected String resolveModalidad(String modalidad, Map<String, String> modalidadMap, TypeOperationsVisa op)
            throws ISOException {

        String result = modalidadMap.get(modalidad);
        if (result == null) {
            throw new ISOException("Modalidad de comercio desconocida: " + modalidad + " para operación " + op.name());
        }
        return result;
    }

    protected String getFilename(TypeOperations operation,String filePath, String modalidad) throws ISOException {

        StringBuilder filename = new StringBuilder(filePath);

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

            // === EXTRACCIONES ===
            case EXTRACCION:
                filename.append("extraccion");
                break;

            // === CONSULTAS ===
            case CONSULTA_SALDO:
                filename.append("consulta_saldo");
                break;

            // === REVERSOS ===
            case REVERSO_COMPRA:
            case REVERSO_ANULACION:
            case REVERSO_DEVOLUCION:
                filename.append("rever");
                break;
            case REVERSO_EXTRACCION:
                filename.append("rever_extraccion");
                break;

            default:
                throw new ISOException("Tipo de operación no soportado: " + operation.name());
        }

        filename.append(".xml");
        return filename.toString();
    }


    private String resolveModalidad(String modalidad, TypeOperations op)
            throws ISOException {
        String result = modalidadMap.get(modalidad);
        if (result == null) {
            throw new ISOException("Modalidad de comercio desconocida: " + modalidad + " para operación " + op.name());
        }
        return result;
    }


    protected String getStan() throws ISOException {
        Space psp = SpaceFactory.getSpace();
        long s = SpaceUtil.nextLong (psp, "STAN") % 1000000000000L;
        return  ISOUtil.zeropad (Long.toString (s), 6);
    }

    protected String getNowFormatDate(String format){
        Date d=new Date();
        return ISODate.formatDate (d, format);
    }

}
