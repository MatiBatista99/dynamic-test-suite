package ar.cabal;

import java.util.*;
import java.util.concurrent.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.MUX;
import org.jpos.util.Log;
import org.jpos.util.NameRegistrar;

@EqualsAndHashCode(callSuper = true)
@Data
public class IsoBulkSender extends Log {

    private final ExecutorService executor;
    private MUX mux;


    public IsoBulkSender(int poolSize,MUX mux) {
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.mux=mux;
    }

    public ISOMsg send(ISOMsg tx) {
        return sendTransaction(tx);
    }

    private ISOMsg sendTransaction(ISOMsg tx) {
        try {
            // Simulación de envío por socket al host
            System.out.println("Enviando transacción: " + tx.getMTI() + " Trace: " + tx.getString(11));
            return mux.request(tx, 150000);
        } catch (Exception e) {
            throw new RuntimeException("Error enviando transacción", e);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}

