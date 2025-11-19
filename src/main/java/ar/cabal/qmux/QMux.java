package ar.cabal.qmux;

import org.jdom2.Element;
import org.jpos.core.Environment;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOResponseListener;
import org.jpos.core.ConfigurationException;
import org.jpos.q2.QFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

public class QMux extends org.jpos.q2.iso.QMUX {

    long timeout;

    public QMux() {
        super();
    }

    public void setKeys(String[] keys) {
        this.key = keys;
    }



    public void initService(String in, String out) throws ConfigurationException
    {
        super.initService();
        this.in = in;
        this.out = out;

    }


        @Override
    public ISOMsg request(ISOMsg m, long to) throws ISOException {
        if (to > 0) {
            return super.request(m, to);
        }
        return super.request(m, timeout);
    }

    @Override
    public void request(ISOMsg arg0, long arg1, ISOResponseListener arg2, Object arg3) throws ISOException {
        if (arg1 > 0) {
            super.request(arg0, arg1, arg2, arg3);
        } else {
            super.request(arg0, timeout, arg2, arg3);
        }
    }


    @Override
    public void startService() {
        super.startService();
        synchronized (sp) {
            while (sp.rdp(in) != null) { // Vaciamos la cola de entrada para evitar el delay.
                notify(in, null);
            }
        }
    }
}
