package ar.cabal;

import org.jpos.iso.*;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.space.SpaceUtil;

import java.text.ParseException;
import java.util.Date;

public abstract class Origin {


    public ISOMsg createISOMsg(Case c, ISOPackager isoPackager) throws ISOException, ParseException {
        ISOMsg msg = new ISOMsg();
        setStan(msg);
        setTerminalId(msg);
        setPackager(msg,isoPackager);
        setMtiAndCode(msg,c.getMti());
        setModoIngreso(msg,c);
        setCardInfo(msg,c);
        setDate(msg);
        setAmountAndCurrency(msg,c);
        setComercioAndTerminal(msg,c);
        setDatosPrivados(msg,c);
        setCustoms(msg);
        setAppSequenceNumberAndCryptogram(msg); // Esta depende de si es EMV
        return msg;
    }


    public void setStan(ISOMsg isoMsg) throws ISOException {
        Space psp = SpaceFactory.getSpace();
        long s = SpaceUtil.nextLong (psp, "STAN") % 1000000000000L;
        String stan= ISOUtil.zeropad (Long.toString (s), 6);
        isoMsg.set(11,stan);
    }

    public void setTerminalId( ISOMsg isoMsg){
        //TID lo mandamos random?
        isoMsg.set(41,String.valueOf((int)(Math.random() * 90000000) + 10000000));
    }

    public void setPackager(ISOMsg msg, ISOPackager packager){
        msg.setPackager(packager);
    }


    public  abstract void setModoIngreso(ISOMsg msg,Case c);

    public abstract void setMtiAndCode(ISOMsg msg,String mti) throws ISOException;

    public abstract void setComercioAndTerminal(ISOMsg msg, Case c) throws ISOException;

    public abstract void setCardInfo(ISOMsg msg,Case c) throws ISOException, ParseException;

    public abstract void setAmountAndCurrency(ISOMsg msg, Case c) throws ISOException;

    public abstract void setDate(ISOMsg msg);

    public abstract void setCustoms(ISOMsg msg);

    public abstract void setDatosPrivados(ISOMsg msg, Case c);


    public abstract void setAppSequenceNumberAndCryptogram(ISOMsg msg); // SOlo para CTL o CTLS.


    protected String getFormatDate(String format){
        Date d=new Date();
        return ISODate.formatDate (d, format);
    }

}
