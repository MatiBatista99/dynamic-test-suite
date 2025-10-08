package ar.cabal;

import java.util.*;
import java.util.concurrent.*;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.MUX;
import org.jpos.util.NameRegistrar;

public class IsoBulkSender {

    private final ExecutorService executor;
    private MUX mux;


    public IsoBulkSender(int poolSize,MUX mux) {
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.mux=mux;
    }

    public List<CompletableFuture<ISOMsg>> sendAllAsync(List<ISOMsg> transactions) {
        List<CompletableFuture<ISOMsg>> futures = new ArrayList<>();
        for (ISOMsg tx : transactions) {
            CompletableFuture<ISOMsg> future = CompletableFuture.supplyAsync(() -> sendTransaction(tx), executor);
            futures.add(future);
        }
        return futures;
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

