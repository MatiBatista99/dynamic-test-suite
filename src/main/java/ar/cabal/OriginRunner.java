package ar.cabal;

import ar.cabal.dtos.Case;
import ar.cabal.dtos.CaseGroup;
import ar.cabal.dtos.CodeMappingDto;
import ar.cabal.origins.OriginHandler;
import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import lombok.Data;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Session;
import org.jpos.ee.DB;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.MUX;
import org.jpos.q2.Q2;
import org.jpos.util.Log;
import org.jpos.util.NameRegistrar;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class OriginRunner implements Runnable{

    private final OriginHandler origin;
    private final MUX mux;
    private final DB db;
    private final Log log;
    private static final int MAX_THREADS_PER_ORIGIN = 5;

    public OriginRunner (OriginHandler originHandler, MUX mux, DB db, Log log) {

        this.origin = originHandler;
        this.mux = mux;
        this.db = db;
        this.log=log;
    }

    @Override
    public void run() {
        log.info("Processing started origin "+origin.getName() +": " + Timestamp.valueOf(LocalDateTime.now()));
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS_PER_ORIGIN );

        try {
            CIFSContext context = getContextFileServer();

            SmbFile file = origin.getInputFile(context);

            try (SmbFileInputStream is = new SmbFileInputStream(file);
                 Workbook workbookInput = new XSSFWorkbook(is);
                 Workbook workbookOutput = new XSSFWorkbook()) {

                if (!mux.isConnected())
                    throw new IllegalStateException("MUX is not connected.");

                IsoBulkSender sender = new IsoBulkSender(mux);

                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (int i = 0; i < workbookInput.getNumberOfSheets(); i++) {
                    Sheet sheet = workbookInput.getSheetAt(i);

                    futures.add(CompletableFuture.runAsync(() -> {
                        try (XSSFWorkbook localWb = new XSSFWorkbook()) {
                            // Procesa la hoja en su workbook temporal
                            processSheet(sheet, localWb, sender,executor);

                            // Una vez procesada, copia las hojas al workbook principal
                            synchronized (workbookOutput) {
                                mergeSheets(localWb, workbookOutput);
                            }

                        } catch (Exception e) {
                            log.error("Error processing sheet " + sheet.getSheetName() + ": " + e.getMessage(), e);
                        }
                    }, executor));
                }

                // Esperar que terminen todas las tareas
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();


                origin.saveOutputFile(workbookOutput,context);

                executor.shutdown();

                log.info("Processing finished: " + Timestamp.valueOf(LocalDateTime.now()));

            } catch (Exception e) {
                log.error("Error during processing: " + e.getMessage(), e);
            }
        }catch (Exception e){
            log.error("Error during obtain excel from file server: " + e.getMessage(), e);
        }

    }

    private void mergeSheets(XSSFWorkbook sourceWb, Workbook targetWb) {
        for (int i = 0; i < sourceWb.getNumberOfSheets(); i++) {
            Sheet sourceSheet = sourceWb.getSheetAt(i);
            String sheetName = sourceSheet.getSheetName();

            // Evita nombres duplicados en el workbook final
            String finalName = sheetName;
            int counter = 1;
            while (targetWb.getSheet(finalName) != null) {
                finalName = sheetName + "_" + counter++;
            }

            Sheet targetSheet = targetWb.createSheet(finalName);

            // Copiar filas y celdas
            for (int r = 0; r <= sourceSheet.getLastRowNum(); r++) {
                Row sourceRow = sourceSheet.getRow(r);
                if (sourceRow == null) continue;

                Row targetRow = targetSheet.createRow(r);
                for (int c = 0; c < sourceRow.getLastCellNum(); c++) {
                    Cell sourceCell = sourceRow.getCell(c);
                    if (sourceCell == null) continue;

                    Cell targetCell = targetRow.createCell(c);
                    copyCellValue(sourceCell, targetCell);
                }
            }
        }
    }


    private void copyCellValue(Cell sourceCell, Cell targetCell) {
        switch (sourceCell.getCellType()) {
            case STRING:
                targetCell.setCellValue(sourceCell.getStringCellValue());
                break;
            case NUMERIC:
                targetCell.setCellValue(sourceCell.getNumericCellValue());
                break;
            case BOOLEAN:
                targetCell.setCellValue(sourceCell.getBooleanCellValue());
                break;
            case FORMULA:
                targetCell.setCellFormula(sourceCell.getCellFormula());
                break;
            case BLANK:
                targetCell.setBlank();
                break;
            default:
                // Otros tipos como ERROR, etc.
                break;
        }
    }

    private void processSheet(
            Sheet sheet,
            Workbook workbookResponse,
            IsoBulkSender sender,
            ExecutorService executor
    ) throws Exception {

        List<Case> cases = readCases(sheet);
        if (cases.isEmpty()) return;

        Sheet sheetResponse = workbookResponse.createSheet(sheet.getSheetName());
        writeHeader(sheetResponse);

        ConcurrentLinkedQueue<ResultRecord> results = new ConcurrentLinkedQueue<>();
        List<CompletableFuture<Void>> caseFutures = new ArrayList<>();

        for (int i = 0; i < cases.size(); i++) {
            final int groupIndex = i;
            Case c = cases.get(i);

            caseFutures.add(
                    CompletableFuture.runAsync(() -> {
                        try {
                            ISOMsg previousRequest = null;

                            for (Case.SpecificCase specificCase : c.getSpecificCases()) {
                                previousRequest = origin.processSpecificCase(
                                        origin.buildContextCase(c),
                                        specificCase,
                                        c,
                                        previousRequest,
                                        sender,
                                        results,
                                        groupIndex
                                );
                            }
                        } catch (Exception e) {
                            log.warn("Error in case " + c.getCaseName(), e);
                        }
                    }, executor)
            );
        }

        CompletableFuture
                .allOf(caseFutures.toArray(new CompletableFuture[0]))
                .join();

        List<ResultRecord> sortedResults = results.stream()
                .sorted(Comparator.comparingInt(ResultRecord::getIndex))
                .toList();

        int rowIndex = 1;
        for (ResultRecord record : sortedResults) {
            writeResultRow(sheetResponse, rowIndex++, record);
        }
    }


    // --- Métodos auxiliares (sin cambios lógicos, solo limpieza) ---

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {"Casos", "Tipo", "Condicion tarjeta", "Resultado esperado",
                "RC", "IRC", "Descripcion error","RRNN"};
        for (int i = 0; i < headers.length; i++)
            header.createCell(i).setCellValue(headers[i]);
    }




    private void writeResultRow(Sheet sheet, int index, ResultRecord rs) throws ISOException {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(rs.getCaseName());
        row.createCell(1).setCellValue(rs.getTipo());
        row.createCell(2).setCellValue(rs.getCondicionTarjeta());
        String resultadoEsperado=rs.getResultadoEsperado();
        row.createCell(3).setCellValue(resultadoEsperado);
        ISOMsg isoMsgResp=rs.getResponse();
        String rc = isoMsgResp.getString(39);
        row.createCell(4).setCellValue(rc);


        //En cso de procesmaiento online
/*
        if (isoMsgResp.getMTI().contains("1110")) {
            CodeMappingDto cm = codeMappings.getOrDefault(rc, codeMappings.get("default"));
            row.createCell(5).setCellValue(cm.getCode());
            row.createCell(6).setCellValue(cm.getDescription());
        } else if(rs.getTipo().toLowerCase().contains("reverso")){
          //Vamos a buscar el tranlog y grabamos el irc correspondiente
             setIrcAndDescription(isoMsgResp,row);
        } else {
            row.createCell(5).setCellValue("-");
            row.createCell(6).setCellValue("-");
        }

 */
        origin.setIrcAndSdi(db,isoMsgResp,row,rs.getMtiOrigen());

        row.createCell(7).setCellValue(isoMsgResp.getString(37));
    }


    private List<Case> readCases(Sheet sheet) {
        List<Case> cases= new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            Case c=origin.getCaseFromRow(row);
            if (c.getCaseName() == null || c.getCaseName().trim().isEmpty()) continue; // Rever esto
            cases.add(c);
        }
        return cases;
    }

    @Data
    public static class ResultRecord {
        private final int index;
        private final String resultadoEsperado;
        private final ISOMsg response;
        private final String condicionTarjeta;
        private final String caseName;
        private final String tipo;
        private final String mtiOrigen;

        public ResultRecord(int index, ISOMsg response, String condicionTarjeta, String caseName, Case.SpecificCase specificCase) {
            this.index = index;
            this.caseName=caseName;
            this.condicionTarjeta=condicionTarjeta;
            this.response = response;
            this.resultadoEsperado=specificCase.getResultadoEsperado();
            this.tipo=specificCase.getTipo();
            this.mtiOrigen=specificCase.getMti();
        }
    }

/*
    private static Case getCaseFromRow(Row row) {
        final String tipoStr = getString(row, 1);
        final String mtiStr = getString(row, 2);
        final String resultadoStr = getString(row, 8);

        final String[] tipos = tipoStr.contains("+") ? tipoStr.split("\\+") : new String[]{tipoStr};
        final String[] mtis = mtiStr.contains("-") ? mtiStr.split("-") : new String[]{mtiStr};
        final String[] resultadosEsperados = resultadoStr.contains("/") ? resultadoStr.split("/") : new String[]{resultadoStr};

        final int total = Math.max(mtis.length, Math.max(tipos.length, resultadosEsperados.length));
        final List<Case.SpecificCase> specificCases = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            final Case.SpecificCase specificCase = new Case.SpecificCase();
            specificCase.setMti(mtis[i].trim());
            specificCase.setTipo(i < tipos.length ? tipos[i] : tipos[0]);
            specificCase.setResultadoEsperado(i < resultadosEsperados.length ? resultadosEsperados[i] : resultadosEsperados[0]);
            specificCases.add(specificCase);
        }

        return Case.builder()
                .caseName(getString(row, 0))
                .cuotas(getString(row, 3))
                .condicionTarjeta(getString(row, 4))
                .condicionDisponibleDeLaTarjetaCuenta(getString(row, 5))
                .modalidadComercio(getString(row, 6))
                .amount(getDouble(row,7))
                .tarjeta(getString(row, 9))
                .cvv(getString(row, 10))
                .pinblock(getString(row,11))
                .fechaVencimiento(getString(row, 12))
                .numComercio(getString(row, 13))
                .specificCases(specificCases)
                .build();
    }




    private static String getString(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return cell.getCellType() == CellType.NUMERIC ?
                String.valueOf((long)cell.getNumericCellValue()) :
                cell.getStringCellValue().toUpperCase();
    }

    private static Double getDouble(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 100.0;
        return cell.getCellType() == CellType.NUMERIC ?
                cell.getNumericCellValue():
                100.00;
    } */

    public Map<String, CodeMappingDto> getCodeMappings(Session session, String origen) {
        String sql = """
            SELECT cm.sserror AS ssCode,
                   TO_CHAR(ac.code, 'FM0000') AS code,
                   ac.description AS description
            FROM mapeocodigos cm
            JOIN action_codes ac ON ac.code = TO_NUMBER(cm.dserror)
            WHERE cm.origen = :origen
              AND cm.destino = 'JPOS'
        """;
        List<Object[]> results = session.createNativeQuery(sql)
                .setParameter("origen", origen)
                .getResultList();

        return results.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> {
                    return CodeMappingDto.builder()
                            .code((String) row[1])
                            .description((String) row[2])
                            .build();
                }
        ));
    }



    //Reversos en caso de visa

    public void setIrcAndDescription(ISOMsg isoMsgResponse, Row row) throws ISOException {
        String sql = """
        SELECT tl.CODRESPUESTAINTERNO AS irc,
               ac.description AS dp
        FROM tranlog tl
        JOIN action_codes ac ON ac.code = TO_NUMBER(tl.CODRESPUESTAINTERNO)
        WHERE tl.codigoMoneda = :currencyCode
          AND tl.ss_stan = :stan
          AND tl.ss_rrn = :rrn
          AND tl.idTerminal = :tid
          AND tl.pan = :pan
        AND TL.codtransaccioninterno in ('420.00','420.02','420.20')
        ORDER BY tl.id DESC
        FETCH FIRST 1 ROW ONLY
        """;

        Object[] result = (Object[]) db.session().createNativeQuery(sql)
                .setParameter("currencyCode", isoMsgResponse.getString(49))
                .setParameter("stan", ISOUtil.zeropad(isoMsgResponse.getString(11),12))
                .setParameter("rrn", isoMsgResponse.getString(37))
                .setParameter("tid", isoMsgResponse.getString(41))
                .setParameter("pan", isoMsgResponse.getString(2))
                .uniqueResult();

        if (result != null ) {
            row.createCell(5).setCellValue( result[0] != null ? result[0].toString() : "");
            row.createCell(6).setCellValue(result[1] != null ? result[1].toString() : "");
        }

    }


    public void setIrcAndSdi(ISOMsg isoMsgResponse, Row row, String mtiOrigen) throws ISOException {
        String sql = """
        SELECT 
            tl.CODRESPUESTAINTERNO AS irc,
            ac1.description AS irc_desc
        FROM tranlog tl
        LEFT JOIN action_codes ac1 ON ac1.code = TO_NUMBER(tl.CODRESPUESTAINTERNO)
        WHERE tl.codigoMoneda = :currencyCode
          AND tl.ss_stan = :stan
          AND tl.ss_rrn = :rrn
          AND tl.idTerminal = :tid
         AND tl.codtransaccioninterno =:mti
        ORDER BY tl.id DESC
        FETCH FIRST 1 ROW ONLY
    """;

        Object[] result = (Object[]) db.session().createNativeQuery(sql)
                .setParameter("currencyCode", isoMsgResponse.getString(49))
                .setParameter("stan", ISOUtil.zeropad(isoMsgResponse.getString(11), 12))
                .setParameter("rrn", isoMsgResponse.getString(37))
                .setParameter("tid", isoMsgResponse.getString(41))
                .setParameter("mti",mtiOrigen.substring(1))
                .uniqueResult();

        if (result != null) {
            // IRC y descripción
            row.createCell(5).setCellValue(result[0] != null ? result[0].toString() : "");
            row.createCell(6).setCellValue(result[1] != null ? result[1].toString() : "");

        }
    }


    private CIFSContext getContextFileServer() throws CIFSException {
        Properties props = new Properties();
        props.put("jcifs.smb.client.disableSpnegoIntegrity", "true");
        props.put("jcifs.smb.client.minVersion", "SMB202");
        props.put("jcifs.smb.client.maxVersion", "SMB311");

        return new BaseContext(new PropertyConfiguration(props))
                .withCredentials(new jcifs.smb.NtlmPasswordAuthenticator("", "peld-mbatista", "Riverelmacpao920."));
    }

}
