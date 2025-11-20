package ar.cabal;

import ar.cabal.dtos.CodeMappingDto;
import ar.cabal.origins.OriginHandler;
import ar.cabal.origins.OriginHandlerFactory;
import org.jpos.core.Configuration;
import org.jpos.ee.DB;
import org.jpos.iso.*;
import org.jpos.q2.QBeanSupport;

import java.util.ArrayList;
import java.util.List;


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


            List<Thread> threads = new ArrayList<>();

            for (String origin : origins) {
                DB db= new DB();
                db.open();
                MUX mux = MuxFactory.getMuxByOrigin(origin);
                OriginHandler originHandler=OriginHandlerFactory.getHandler(origin, fileServer);
                Thread t = new Thread(
                        new OriginRunner(originHandler, mux, db,log),
                        origin + "-Runner"
                );
                threads.add(t);
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }
            getServer().shutdown();

        } catch (Exception e) {
            log.error(e);
            getServer().shutdown();
        }
    }

}
