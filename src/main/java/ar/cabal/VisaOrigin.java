package ar.cabal;

import org.jpos.iso.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class VisaOrigin extends Origin{


    @Override
    public void setMtiAndCode(ISOMsg m, String mti) throws ISOException {
        System.out.println("MTI: " + mti);
        Map map = new HashMap();
        map.put("0200.00", "1100.00");
        map.put("0200.20", "1100.20");
        map.put("0220.02", "1120.02");
        map.put("0220.00", "1120.00");
        map.put("0420.00", "1420.00");
        map.put("0420.02", "1420.02");
        map.put("0420.20", "1420.20");
        String s = (String) map.get(mti);

        StringTokenizer st = new StringTokenizer(s, ".");
        m.setMTI(st.nextToken());

        String [] ss=s.split("\\.");
        if (st.hasMoreTokens()) {
            m.set(3, st.nextToken() + ss[1]);
        }
    }

    @Override
    public void setComercioAndTerminal(ISOMsg msg,Case c) throws ISOException {
        //Codigo actividad establecimiento
        msg.set(18,"7299");// --> Es relevante esto ?
        msg.set(42,c.getNumComercio());
        //No viene el nombre del establecimiento y demas. Podriamos poner por default?
        msg.set(43,"                                        ");

    }

    @Override
    public  void setModoIngreso(ISOMsg msg,Case c){
        //Suponemos que es banda
        if(c.getModalidadComercio().equals("Mostrador")){
            msg.set(22,"M00101254001");
        }else{
            //Ver estos datos con boris
            msg.set(22,"M00101M54001"); // <!-- CTLS M00101M54001 CT M00101554001 -->
        }
    };

    @Override
    public void setCardInfo(ISOMsg msg,Case c) throws ParseException {
        msg.set(2,c.getTarjeta());
        SimpleDateFormat formato = new SimpleDateFormat("MM/yyyy");
        Date fechaVencimiento = formato.parse(c.getFechaVencimiento());
        SimpleDateFormat formatoDestino = new SimpleDateFormat("yyMM");
        String formattedDate = formatoDestino.format(fechaVencimiento);
        msg.set(14,formattedDate);
        //Si es por banda
        char entryMode=msg.getString(22).charAt(6);
        if(msg.getString(22).charAt(6) == '2'){
            //Evaluar valor de service code (puse 121)
           String pan= c.getTarjeta()+"="+formattedDate+121+c.getCvv()+"0000000";
           msg.set(35,pan);
        }

    }

    @Override
    public void setAmountAndCurrency(ISOMsg msg, Case c) throws ISOException {
        /*
        No esta especificado el currency ni tampoco el monto
        032 --> Pesos Argentinosmsg.getString(22).charAt(6) == '2'
        840 --> Dolares
        999 --> Puntos?
         */
        //Suponemos monto y pesos
        msg.set(4,ISOUtil.zeropad(BigDecimal.valueOf(100L).unscaledValue().toString(), 12));
        msg.set(49,"032");
    }

    @Override
    public void setDate(ISOMsg msg) {
        msg.set(12,getFormatDate("yyMMddHHmmss"));
    }

    @Override
    public void setCustoms(ISOMsg msg) {
        //Por ejemplo el 47
        msg.set(47,"00525433");

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

    @Override
    public void setAppSequenceNumberAndCryptogram(ISOMsg msg) {
        //Solo setemaos si en CT o CTLS
        if(msg.getString(22).charAt(6) == '5' || msg.getString(22).charAt(6) == '7'){
            // Ninguno de estos campos esta en el excel de homologacion
            msg.set(23,"000");
            msg.set(55,"9F02060000000010005F2A0209789F3704000000009F360200149F100A0116209080000000B0009F2608A995E100A3A91ABF");
        }
    }


}
