package ar.cabal.origins.visa;

import ar.cabal.dtos.Case;
import ar.cabal.origins.Origin;
import org.jpos.iso.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

public class VisaOrigin extends Origin {


    @Override
    public void setMtiAndCode(ISOMsg m, String mti) throws ISOException {
        // Buscar el código de proceso asociado
        MtiAndProcessingCodeEnum mtiAndPcEnum = MtiAndProcessingCodeEnum.fromKey(mti);
        if (mtiAndPcEnum == null) {
            throw new ISOException("Código de proceso no definido para: " + mti);
        }

        // Setear en el mensaje ISO
        m.setMTI(mtiAndPcEnum.getMti());
        m.set(3, mtiAndPcEnum.getCode()); // 6 dígitos exactos
    }



    @Override
    public void setComercioAndTerminal(ISOMsg msg, Case c) throws ISOException {
        //Codigo actividad establecimiento
        msg.set(18,"7299");// --> Es relevante esto ?
        msg.set(42,c.getNumComercio());
        //No viene el nombre del establecimiento y demas. Podriamos poner por default?
        msg.set(43,"                                        ");

    }


    @Override
    public void setTerminalId(ISOMsg isoMsg) {
        isoMsg.set(41,String.valueOf((int)(Math.random() * 90000000) + 10000000));
    }

    @Override
    public void setAdquirente(ISOMsg msg) {
        msg.set(32,"06544003");// Esto es para adquirencia prisma
        // Vamos a tener cajeros banelco, link, prisma, visa desde el exterior?
    }

    @Override
    public void setPresentador(ISOMsg msg) {
        msg.set(33,"06028000");// Valor fijo para visa segun documentacion de prisma
    }


    @Override
    public void setEntryModeAndCardInfo(ISOMsg msg,Case c) throws ParseException {
        msg.set(2,c.getTarjeta());
        SimpleDateFormat formato = new SimpleDateFormat("MM/yyyy");
        Date fechaVencimiento = formato.parse(c.getFechaVencimiento());
        SimpleDateFormat formatoDestino = new SimpleDateFormat("yyMM");
        String formattedDate = formatoDestino.format(fechaVencimiento);
        //Banda
        if(c.getModalidadComercio().equals("Mostrador")){
            msg.set(22,"M00101254001");
            String serviceCode="000";
            if(c.getTarjeta().equals("6502720014541281")){
                serviceCode="999";
            }
            String pan= c.getTarjeta()+"D"+formattedDate+serviceCode+c.getCvv()+"0000000";
            msg.set(35,pan);
            //Manual
        }else if(c.getModalidadComercio().equals("Internet")){
            msg.set(22,"200181684101");
            msg.set(14,formattedDate);
        }
    }


    @Override
    public void setAmountAndCurrency(ISOMsg msg, Case c) throws ISOException {
        /*
        032 --> Pesos Argentinosmsg.getString(22).charAt(6) == '2'
        840 --> Dolares
        999 --> Puntos?
         */
        //Suponemos monto y moneda
        msg.set(4,ISOUtil.zeropad((String.valueOf(c.getAmount().longValue())+"00"), 12));
        msg.set(49,"032"); // Operatoria nacional, en principio en pesos
    }

    @Override
    public void setDate(ISOMsg msg) {
        msg.set(12,getFormatDate("yyMMddHHmmss"));
    }


    @Override
    public void setRrn(ISOMsg msg) {
        long num = ThreadLocalRandom.current().nextLong(0, 1_000_000_000_000L); // [0, 10^12)
        msg.set(37, String.format("%012d", num)); // rellena con ceros a la izquierda
    }


    @Override
    public void setCustoms(ISOMsg msg,Case c) {
        msg.set(32,"540001");
        //Por ejemplo el 47
        msg.set(47,"00525"+c.getCvv());
    }

    @Override
    public void setDatosPrivados(ISOMsg msg, Case c) {
        //Estandarizar plan y cuotas en excel homologacion
        String cuotas=c.getCuotas();
        String plan="1";
        if(cuotas.length()<2){
            cuotas="0"+cuotas;
        }
        String CAMPO48="053003"+plan+cuotas+"006      019                   08        003"+c.getCvv();

        msg.set(48,CAMPO48);
    }




}
