package ar.cabal.origins;

import ar.cabal.dtos.Case;
import ar.cabal.origins.posnet.TypeOperationsPosnet;
import ar.cabal.origins.visa.TypeOperationsVisa;
import ar.cabal.qmux.QMux;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.jpos.ee.DB;
import org.jpos.iso.*;
import org.jpos.iso.packager.XMLPackager;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.space.SpaceUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public abstract class OriginHandler {

    protected String SERVER;
    protected static final String IN_SHARE = "file-server/in";
    protected static final String OUT_SHARE = "file-server/out";
    protected static final Map<String, String> modalidadMap = Map.of(
            "MOSTRADOR", "magstripe",
            "INTERNET", "man"
    );

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


    public SmbFile getInputFile(CIFSContext context) throws Exception{
        return new SmbFile(buildPath(IN_SHARE, "homologacion_test_suite.xlsx"), context);
    };

    public abstract void setIrcAndSdi(DB db, ISOMsg isoMsgResponse, Row row, String mtiOrigen) throws ISOException;

    public abstract String getName();

    public abstract SmbFile getOutputFile(CIFSContext context) throws Exception;


    public abstract Map<String, String> buildContextCase(Case c) throws ISOException;


    public abstract ISOMsg createISOMsgByFile(Map<String,String> caseContext, String mti,String modalidadComercio,ISOMsg previousRequest) throws ISOException, IOException;



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
                } else if (comp instanceof ISOField) {
                    String currentValue = (String) comp.getValue();
                    if (currentValue != null && currentValue.startsWith("!")) {
                        String key = currentValue.substring(1);
                        if (ctx.containsKey(key)) {
                            m.set(i, ctx.get(key));
                        } else {
                            System.out.println("⚠️ Missing key in context: " + key);
                        }
                    }
                }
            }
        }
        return m;
    }

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
