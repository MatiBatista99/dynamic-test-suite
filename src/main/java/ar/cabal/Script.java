package ar.cabal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;
import org.jpos.iso.*;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.q2.QBeanSupport;
import org.jpos.util.NameRegistrar;

import java.io.*;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Script extends QBeanSupport implements Runnable {

    private static final String MUX_NAME = "mux.dynamic-channel-mux";
    private static final String INPUT_FILE = "/visa_test_suite.xlsx";
    private static final String OUTPUT_DIR = "test-cases";
    private static final String OUTPUT_FILE = "resultados.xlsx";

    @Override
    protected void startService() {
        new Thread(this).start();
    }

    @Override
    public void run() {
        log.info("Inicio procesamiento: " + Timestamp.valueOf(LocalDateTime.now()));

        try (InputStream is = getClass().getResourceAsStream(INPUT_FILE);
             Workbook workbookInput = new XSSFWorkbook(is);
             Workbook workbookOutput = new XSSFWorkbook()) {

            File outputDir = new File(OUTPUT_DIR);
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("No se pudo crear el directorio de salida: " + OUTPUT_DIR);
            }

            MUX mux = NameRegistrar.get(MUX_NAME);
            if (!mux.isConnected()) {
                throw new IllegalStateException("El MUX no está conectado.");
            }

            for (int i = 0; i < workbookInput.getNumberOfSheets(); i++) {
                processSheet(workbookInput.getSheetAt(i), workbookOutput, mux);
            }

            File outputFile = new File(outputDir, OUTPUT_FILE);
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                workbookOutput.write(out);
            }

            log.info("Fin procesamiento: " + Timestamp.valueOf(LocalDateTime.now()));
            getServer().shutdown();

        } catch (Exception e) {
            log.error("Error durante el procesamiento: " + e.getMessage(), e);
        }
    }

    private void processSheet(Sheet sheet, Workbook workbookResponse, MUX mux)
            throws ISOException, ParseException {

        Map<String, Case> casesMap = readCases(sheet);
        if (casesMap.isEmpty()) return;

        Sheet sheetResponse = workbookResponse.createSheet(sheet.getSheetName());
        writeHeader(sheetResponse);

        GenericPackager packager = new GenericPackager();
        //Patron template
        VisaOrigin visaOrigin = new VisaOrigin(); // Esto deberia ser dinamico
        List<ISOMsg> transactions = new ArrayList<>();

        for (Case c : casesMap.values()) {
            transactions.add(visaOrigin.createISOMsg(c, packager));
        }

        IsoBulkSender sender = new IsoBulkSender(30,mux);
        List<CompletableFuture<ISOMsg>> futures = sender.sendAllAsync(transactions);
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int rowIndex = 1;
        for (Map.Entry<String, Case> entry : casesMap.entrySet()) {
            try {
                ISOMsg resp = futures.get(rowIndex - 1).get();
                writeResultRow(sheetResponse, rowIndex++, entry.getValue(), resp);
            } catch (Exception e) {
                log.warn("Error en caso " + entry.getKey() + ": " + e.getMessage());
            }
        }

        sender.shutdown();
    }

    private Map<String, Case> readCases(Sheet sheet) {
        Map<String, Case> casesMap = new LinkedHashMap<>();

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // omitimos encabezado
            Case c = getACase(row);
            if (c.getCaseName() != null && !c.getCaseName().isEmpty()) {
                casesMap.put(c.getCaseName(), c);
            }
        }

        return casesMap;
    }

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Casos");
        header.createCell(1).setCellValue("Tipo");
        header.createCell(2).setCellValue("Condicion tarjeta");
        header.createCell(3).setCellValue("Resultado esperado");
        header.createCell(4).setCellValue("Resultado");
    }

    private void writeResultRow(Sheet sheet, int index, Case c, ISOMsg response) throws ISOException {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(c.getCaseName());
        row.createCell(1).setCellValue(c.getTipo());
        row.createCell(2).setCellValue(c.getCondicionTarjeta());
        row.createCell(3).setCellValue(c.getResultadoEsperado());

        String result = "000".equals(response.getString(39)) ? "Aprobada" : "Denegada";
        row.createCell(4).setCellValue(result);

        System.out.println("Respuesta " + c.getCaseName() + " -> MTI=" + response.getMTI() +
                " RC=" + response.getString(39));
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
