package ar.cabal;

import ar.cabal.dtos.CodeMappingDto;
import ar.cabal.origins.OriginHandler;
import ar.cabal.origins.OriginHandlerFactory;
import org.jpos.core.Configuration;
import org.jpos.ee.DB;
import org.jpos.iso.*;
import org.jpos.q2.QBeanSupport;



public class Script extends QBeanSupport  {

    private String fileServer;

    @Override
    public void setConfiguration(Configuration cfg){
        this.fileServer=cfg.get("fileServer");
    }

    @Override
    protected void startService() {
        try {
            String[] origins = {"VISA", "POSNET"};

            DB db= new DB();
            db.open();

            for (String origin : origins) {
                MUX mux = MuxFactory.getMuxByOrigin(origin);
                OriginHandler originHandler=OriginHandlerFactory.getHandler(origin, fileServer);
                Thread t = new Thread(
                        new OriginRunner(getServer(),originHandler, mux, db,log),
                        origin + "-Runner"
                );

                t.start();
            }

        } catch (Exception e) {
            log.error(e);
            getServer().shutdown();
        }
    }

}
