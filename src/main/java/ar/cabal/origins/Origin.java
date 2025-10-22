package ar.cabal.origins;

import ar.cabal.dtos.Case;
import org.jpos.iso.*;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.space.SpaceUtil;

import java.text.ParseException;
import java.util.Date;

public abstract class Origin {


    public ISOMsg createISOMsg(Case c,String mti) throws ISOException, ParseException {
        ISOMsg msg = new ISOMsg();
        setPackager(msg);
        setStan(msg);
        setTerminalId(msg);
        setRrn(msg);
        setMtiAndCode(msg, mti);
        setEntryModeAndCardInfo(msg,c);
        setDate(msg);
        setAmountAndCurrency(msg,c);
        setComercioAndTerminal(msg,c);
        setDatosPrivados(msg,c);
        setCustoms(msg,c);
        return msg;
    }


    public void setStan(ISOMsg isoMsg) throws ISOException {
        Space psp = SpaceFactory.getSpace();
        long s = SpaceUtil.nextLong (psp, "STAN") % 1000000000000L;
        String stan= ISOUtil.zeropad (Long.toString (s), 6);
        isoMsg.set(11,stan);
    }

    public abstract void setTerminalId( ISOMsg isoMsg);

    public void setPackager(ISOMsg msg) throws ISOException {
        msg.setPackager(new GenericPackager());
    }

    public  abstract void setAdquirente(ISOMsg msg);

    public  abstract void setPresentador(ISOMsg msg);

    public abstract void setMtiAndCode(ISOMsg msg,String mti) throws ISOException;

    public abstract void setComercioAndTerminal(ISOMsg msg, Case c) throws ISOException;

    public abstract void setEntryModeAndCardInfo(ISOMsg msg,Case c) throws ParseException;

    public abstract void setAmountAndCurrency(ISOMsg msg, Case c) throws ISOException;

    public abstract void setDate(ISOMsg msg);

    public abstract void setCustoms(ISOMsg msg,Case c);

    public abstract void setDatosPrivados(ISOMsg msg, Case c);

    public abstract void setRrn(ISOMsg msg);

    protected String getFormatDate(String format){
        Date d=new Date();
        return ISODate.formatDate (d, format);
    }

}
