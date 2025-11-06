package ar.cabal;

import ar.cabal.origins.Origin;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import org.apache.poi.ss.usermodel.Workbook;

public abstract class OriginHandler {

    protected String SERVER;
    protected static final String IN_SHARE = "file-server/in";
    protected static final String OUT_SHARE = "file-server/out";


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

    public abstract Origin getOriginTemplate();

    public abstract String getOriginName();

    public abstract SmbFile getInputFile(CIFSContext context) throws Exception;

    public abstract SmbFile getOutputFile(CIFSContext context) throws Exception;

    /*
    String getOriginName();

    SmbFile getInputFile(CIFSContext context) throws Exception;

    SmbFile getOutputFile(CIFSContext context) throws Exception;

    void saveOutputFile(Workbook workbook, CIFSContext context) throws Exception; */

}
