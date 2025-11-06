package ar.cabal;

import ar.cabal.origins.Origin;
import ar.cabal.origins.visa.VisaOrigin;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;


public class VisaOriginHandler extends OriginHandler {

    public VisaOriginHandler(String fileServer) {
        this.SERVER=fileServer;
    }

    @Override
    public Origin getOriginTemplate() {
        return new VisaOrigin();
    }

    @Override
    public String getOriginName() {
        return "VISA";
    }

    @Override
    public SmbFile getInputFile(CIFSContext context) throws Exception {
        return new SmbFile(buildPath(IN_SHARE, "visa_test_suite.xlsx"), context);
    }

    @Override
    public SmbFile getOutputFile(CIFSContext context) throws Exception {
        return new SmbFile(buildPath(OUT_SHARE, "visa_test_suite_resultados.xlsx"), context);
    }

}
