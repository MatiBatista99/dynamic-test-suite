package ar.cabal.jpos;


import java.util.BitSet;
import org.jpos.iso.ISOComponent;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOUtil;


public class IFA_BITMAP extends org.jpos.iso.IFA_BITMAP {

    public IFA_BITMAP() {
        super();
    }

    /**
     * @param c - a component
     * @return packed component
     * @exception ISOException
     */
    @Override
    public byte[] pack(ISOComponent c) throws ISOException {

        BitSet bs = (BitSet) c.getValue();
        byte[] b = super.pack(c);//Guardo el bitmap de 16 para los 0800
        if (b.length == 16 && bs.get(3)){//Los 0800 no tiene el campo 3
            bs.set(1, true);//Posnet necesita que siempre este presente el bitmap secundario si el mensaje original lo tenia
            b = ISOUtil.bitSet2byte (bs);
            //b = ISOUtil.bitSet2byte(bs,32);//Cada bitmap es de 16, como no es un 0800 lo fijo en 32
            b = (ISOUtil.hexString (b) + "0000000000000000").getBytes();
        }
        return b;
    }
}

