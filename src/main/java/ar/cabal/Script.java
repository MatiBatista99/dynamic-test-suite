package ar.cabal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;
import org.jpos.iso.*;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.q2.QFactory;
import org.jpos.q2.iso.ChannelAdaptor;
import org.jpos.util.NameRegistrar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Script extends org.jpos.q2.QBeanSupport implements Runnable{


    static final String muxName="mux.dynamic-channel-mux";

    @Override
    protected void startService() {
        new Thread(this).start();
    }


    public void run () {
        log.info( "Inicio procesamiento:"+ Timestamp.valueOf(LocalDateTime.now()));
        //Consumir excel del file server
        try {
            InputStream is=getClass().getResourceAsStream("/visa_test_suite.xlsx"); // La idea es que esto este parametrizado a futuro tambien.
            Workbook workbook = new XSSFWorkbook(is);
            Map<String, Case> casesMap=new HashMap<>();
            for (int i=0;i<workbook.getNumberOfSheets();i++){
                Sheet sheet=workbook.getSheetAt(i);
                String sheetName=sheet.getSheetName();
                System.out.println("Hoja: "+sheetName);
                for(Row row : sheet){
                    if(row.getRowNum()==0){
                        continue;
                    }
                    Case cases=getACase(row);
                    if(cases.getCaseName()!=null){
                        casesMap.put(cases.getCaseName(),cases);
                    }
                }

            }
            VisaOrigin visaOrigin= new VisaOrigin();
            GenericPackager genericPackager= new GenericPackager();
            List<ISOMsg> transactions = new ArrayList<>();

            for(String cs: casesMap.keySet()){
                Case c= casesMap.get(cs);
                transactions.add(visaOrigin.createISOMsg(c,genericPackager));
            }

            MUX mux = NameRegistrar.get(muxName);
            if (mux.isConnected()) {
                IsoBulkSender sender = new IsoBulkSender(30); // Pool de 20 hilos concurrentes

                List<CompletableFuture<ISOMsg>> futures = sender.sendAllAsync(transactions);

// Esperar todas las respuestas
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                //create a workbook
                Workbook workbookk = new XSSFWorkbook();
                //create a sheet in the workbook(you can give it a name)
                Sheet sheet = workbookk.createSheet("excel-sheet");
                int i=0;
                for (CompletableFuture<ISOMsg> f : futures) {
                    try {
                        ISOMsg resp = f.get();
//create a row in the sheet
                        Row row = sheet.createRow(i);

//add cells in the sheet
                        Cell cell = row.createCell(0);

                        cell.setCellValue("Caso" + (i+1));

                        Cell cell1 = row.createCell(1);

                        cell1.setCellValue(casesMap.get("Caso " + (i+1) ).getResultadoEsperado());

                        Cell cell2 = row.createCell(2);

                        if(!Objects.equals(resp.getString(39), "000")){
                            cell2.setCellValue("Denegada");
                        }else{
                            cell2.setCellValue("Aprobada");
                        }
                        i++;
                        System.out.println("Respuesta recibida: " + resp.getMTI() + " RC=" + resp.getString(39));
                    } catch (Exception e) {
                        System.err.println("Fallo en transacción: " + e.getMessage());
                    }
                }
                File outputDir = new File("/test-cases");
                if (!outputDir.exists()) {
                    outputDir.mkdirs(); // crea la carpeta si no existe
                }

                File outputFile = new File(outputDir, "excel.xlsx");
                FileOutputStream out = new FileOutputStream(outputFile);

                workbookk.write(out);
                out.close();
                workbookk.close();

                System.out.println("Archivo generado en: " + outputFile.getAbsolutePath());
                sender.shutdown();
            }

            log.info("Fin procesamiento:"+ Timestamp.valueOf(LocalDateTime.now()));
            //Terminamos con el proceso
            getServer().shutdown();

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ISOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (NameRegistrar.NotFoundException e) {
            throw new RuntimeException(e);
        }


    }


    @NotNull
    private static Case getACase(Row row) {
        Case cases=new Case();
        for(Cell cell : row){
            switch (cell.getAddress().getColumn()){
                case 0:
                    cases.setCaseName(cell.getStringCellValue());
                    break;
                case 1:
                    cases.setTipo(cell.getStringCellValue());
                    break;
                case 2:
                    cases.setMti(cell.getStringCellValue());
                    break;
                case 3:
                    if(cell.getCellType().equals(CellType.NUMERIC)){
                        Double cellValue=cell.getNumericCellValue();
                        cases.setCuotas(String.valueOf(cellValue.shortValue()));
                    }else {
                        cases.setCuotas(cell.getStringCellValue());
                    }
                    break;
                case 4:
                    cases.setCondicionTarjeta(cell.getStringCellValue());
                    break;
                case 5:
                    cases.setCondicionDisponibleDeLaTarjetaCuenta(cell.getStringCellValue());
                    break;
                case 6:
                    cases.setModalidadComercio(cell.getStringCellValue());
                    break;
                case 7:
                    cases.setResultadoEsperado(cell.getStringCellValue());
                    break;
                case 8:
                    cases.setResultado(cell.getStringCellValue());
                    break;
                case 11:
                    cases.setTarjeta(cell.getStringCellValue());
                    break;
                case 12:
                    cases.setCvv(cell.getStringCellValue());
                    break;
                case 13:
                    cases.setFechaVencimiento(cell.getStringCellValue());
                    break;
                case 14:
                    cases.setNumComercio(cell.getStringCellValue());
                    break;
                default:
                    break;
            }
        }
        return cases;
    }
}

