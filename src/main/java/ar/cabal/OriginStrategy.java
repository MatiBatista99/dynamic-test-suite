package ar.cabal;

import org.jpos.iso.ISOMsg;

public interface OriginStrategy {

    ISOMsg createPurchase(Case c);

    ISOMsg createNofitication();

    ISOMsg createReverse();

    ISOMsg createAnulacion();

}
