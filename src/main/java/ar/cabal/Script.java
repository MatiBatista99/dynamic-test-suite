package ar.cabal;

import ar.cabal.dtos.Case;
import ar.cabal.dtos.CaseGroup;
import ar.cabal.dtos.CodeMappingDto;
import ar.cabal.origins.Origin;
import ar.cabal.origins.OriginFactory;
import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.*;
import lombok.Data;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.impl.xb.ltgfmt.Code;
import org.hibernate.Session;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.ee.DB;
import org.jpos.iso.*;
import org.jpos.q2.QBeanSupport;
import org.jpos.util.NameRegistrar;

import java.io.*;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Script extends QBeanSupport implements Runnable {

    private static final String MUX_NAME = "mux.dynamic-channel-mux";
    private static final String INPUT_FILE = "/visa_test_suite_1.xlsx";
    private static final String OUTPUT_DIR = "test-cases";
    private static final String OUTPUT_FILE = "resultados.xlsx";
    private static final String serverAddress="vdicet005";
    private static final String sharenameIn="file-server/in";
    private static final String sharenameOut="file-server/out";
    private  DB db;
    private String origin;

    private Map<String, CodeMappingDto> codeMappings;

    private static final int THREAD_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    @Override
    public void setConfiguration(Configuration var1) throws ConfigurationException{
        this.origin=var1.get("origin");
    }

    @Override
    protected void startService() {
        this.db = new DB();
        this.codeMappings = getCodeMappings(db.open(), origin);
        log.info("Loaded " + codeMappings.size() + " action codes.");
        new Thread(this, "VisaScriptRunner").start();
    }

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
                .setParameter("stan",ISOUtil.zeropad(isoMsgResponse.getString(11),12))
                .setParameter("rrn", isoMsgResponse.getString(37))
                .setParameter("tid", isoMsgResponse.getString(41))
                .setParameter("pan", isoMsgResponse.getString(2))
                .uniqueResult();

        if (result != null ) {
            row.createCell(5).setCellValue( result[0] != null ? result[0].toString() : "");
            row.createCell(6).setCellValue(result[1] != null ? result[1].toString() : "");
        }

    }




    private CIFSContext getContextFileServer() throws CIFSException {
            Properties props = new Properties();
            props.put("jcifs.smb.client.disableSpnegoIntegrity", "true");
            props.put("jcifs.smb.client.minVersion", "SMB202");
            props.put("jcifs.smb.client.maxVersion", "SMB311");

            return new BaseContext(new PropertyConfiguration(props))
                    .withCredentials(new jcifs.smb.NtlmPasswordAuthenticator("", "peld-mbatista", "Riverelmacapo920..."));
    }

    private SmbFile getFileByOrigin(CIFSContext context) throws MalformedURLException {
        String url= "smb://" + serverAddress+ "/" + sharenameIn + "/";
        switch (origin){
            case "VISA":
                url=url+"visa_test_suite.xlsx";
                break;
            default:
                break;
        }

        return new SmbFile(url,context);
    }

    private SmbFile createFileByOrigin(CIFSContext context) throws MalformedURLException {
        String url= "smb://" + serverAddress+ "/" + sharenameOut + "/";
        switch (origin){
            case "VISA":
                url=url+"visa_test_suite_resultados.xlsx";
                break;
            default:
                break;
        }

        return new SmbFile(url,context);
    }


    private void saveFileByOrigin(Workbook wb, CIFSContext context)
            throws MalformedURLException, SmbException {
        SmbFile smbFileOut = createFileByOrigin(context);

        try (SmbFileOutputStream smbfos = new SmbFileOutputStream(smbFileOut)) {
            // Escribir el Workbook directamente al stream remoto
            wb.write(smbfos);
            smbfos.flush();
            System.out.println("Archivo Excel guardado correctamente en el file server: " + smbFileOut.getPath());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }



    @Override
    public void run() {
        log.info("Processing started: " + Timestamp.valueOf(LocalDateTime.now()));

        try {
            CIFSContext context = getContextFileServer();

            SmbFile file = getFileByOrigin(context);

            try (SmbFileInputStream is = new SmbFileInputStream(file);
                 Workbook workbookInput = new XSSFWorkbook(is);
                 Workbook workbookOutput = new XSSFWorkbook()) {

                File outputDir = new File(OUTPUT_DIR);
                if (!outputDir.exists() && !outputDir.mkdirs())
                    throw new IOException("Unable to create output dir: " + OUTPUT_DIR);

                MUX mux = NameRegistrar.get(MUX_NAME);
                if (!mux.isConnected())
                    throw new IllegalStateException("MUX is not connected.");

                ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
                Origin origin = OriginFactory.getOrigin("VISA");
                IsoBulkSender sender = new IsoBulkSender(THREAD_POOL_SIZE, mux);

                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (int i = 0; i < workbookInput.getNumberOfSheets(); i++) {
                    Sheet sheet = workbookInput.getSheetAt(i);

                    futures.add(CompletableFuture.runAsync(() -> {
                        try (XSSFWorkbook localWb = new XSSFWorkbook()) {
                            // Procesa la hoja en su workbook temporal
                            processSheet(sheet, localWb, origin, sender);

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


               saveFileByOrigin(workbookOutput,context);

                sender.shutdown();
                executor.shutdown();

                log.info("Processing finished: " + Timestamp.valueOf(LocalDateTime.now()));

            } catch (Exception e) {
                log.error("Error during processing: " + e.getMessage(), e);
            }
        }catch (Exception e){
            log.error("Error during obtain excel from file server: " + e.getMessage(), e);
        }

        getServer().shutdown();
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


/*
    private void processSheet(Sheet sheet, Workbook workbookResponse,
                              Origin origin, ISOPackager packager,
                              IsoBulkSender sender) throws ISOException {

        List<CaseGroup> groups = readCaseGroups(sheet);
        if (groups.isEmpty()) return;

        Sheet sheetResponse = workbookResponse.createSheet(sheet.getSheetName());
        writeHeader(sheetResponse);

        ConcurrentLinkedQueue<ResultRecord> results = new ConcurrentLinkedQueue<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < groups.size(); i++) {
            final int groupIndex = i;
            CaseGroup group = groups.get(i);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ISOMsg previousResponse = null;

                    for (Case c : group.getCases()) {
                        ISOMsg req = origin.createISOMsg(c);
                        if (!"Compra".equalsIgnoreCase(c.getTipo()) && previousResponse != null) {
                            req.set(37, previousResponse.getString(37));
                            req.set(11, previousResponse.getString(11));
                            req.set(41, previousResponse.getString(41));
                        }
                        ISOMsg resp = sender.send(req);
                        //Seteamos original_rrn
                        resp.set(37,req.getString(37));
                        resp.set(41,req.getString(41));

                        results.add(new ResultRecord(groupIndex, c, resp));
                        previousResponse = resp;
                    }
                } catch (Exception e) {
                    log.warn("Error in group " + group.getGroupName() + ": " + e.getMessage(), e);
                }
            });

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<ResultRecord> sortedResults = results.stream()
                .sorted(Comparator.comparingInt(ResultRecord::getIndex))
                .collect(Collectors.toList());

        int rowIndex = 1;
        for (ResultRecord record : sortedResults) {
            writeResultRow(sheetResponse, rowIndex++, record.getCaseData(), record.getResponse());
        }
    } */

    private void processSheet(Sheet sheet, Workbook workbookResponse,
                              Origin origin, IsoBulkSender sender) throws ISOException {

        List<Case> cases = readCases(sheet);
        if (cases.isEmpty()) return;

        Sheet sheetResponse = workbookResponse.createSheet(sheet.getSheetName());
        writeHeader(sheetResponse);

        ConcurrentLinkedQueue<ResultRecord> results = new ConcurrentLinkedQueue<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < cases.size(); i++) {
            final int groupIndex = i;
            Case c = cases.get(i);
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ISOMsg previousResponse = null;
                    for(Case.SpecificCase specificCase: c.getSpecificCases()) {
                        ISOMsg req = origin.createISOMsg(c,specificCase.getMti());
                        if (!"0200.00".equalsIgnoreCase(specificCase.getMti()) && previousResponse != null) {
                            req.set(37, previousResponse.getString(37));
                            req.set(11, previousResponse.getString(11));
                            req.set(41, previousResponse.getString(41));
                        }
                        if(specificCase.getTipo().toLowerCase().contains("reverso")){
                            Date date=getDate(previousResponse.getString(12));
                            String dateHoy=getDateDay(date);
                            req.set(56,"1100"+previousResponse.getString(11)+dateHoy+getDateTime(date)+"00"+dateHoy+"0000");
                        }
                        ISOMsg resp = sender.send(req);
                        //Seteamos original_rrn
                        resp.set(37, req.getString(37));
                        resp.set(41, req.getString(41));
                        results.add(new ResultRecord(groupIndex,resp,specificCase.getResultadoEsperado(),c.getCondicionTarjeta(),c.getCaseName(),specificCase.getTipo()));
                        previousResponse = resp;
                    }
                } catch (Exception e) {
                    log.warn("Error in case " + c.getCaseName() + ": " + e.getMessage(), e);
                }
            });

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

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
                "RC", "IRC", "Descripcion error", "RRNN"};
        for (int i = 0; i < headers.length; i++)
            header.createCell(i).setCellValue(headers[i]);
    }


    private Date getDate(String date) throws ParseException {
        SimpleDateFormat simpleDateFormat= new SimpleDateFormat("yyMMddHHmmss");
        return  simpleDateFormat.parse(date);
    }

    private String getDateDay(Date date)  {
        SimpleDateFormat simpleDateFormat= new SimpleDateFormat("MMdd");
        return  simpleDateFormat.format(date);
    }

    private String getDateTime(Date date)  {
        SimpleDateFormat simpleDateFormat= new SimpleDateFormat("HHmmss");
        return  simpleDateFormat.format(date);
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

        row.createCell(7).setCellValue(isoMsgResp.getString(37));
    }

    private List<CaseGroup> readCaseGroups(Sheet sheet) {
        List<CaseGroup> groups = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            Case c = getCaseFromRow(row);
            CaseGroup group = new CaseGroup("Grupo_" + row.getRowNum());
            group.addCase(c);
            groups.add(group);
        }
        return groups;
    }

    private List<Case> readCases(Sheet sheet) {
        List<Case> cases= new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            cases.add(getCaseFromRow(row));
        }
        return cases;
    }

    @Data
    private static class ResultRecord {
        private final int index;
        private final String resultadoEsperado;
        private final ISOMsg response;
        private final String condicionTarjeta;
        private final String caseName;
        private final String tipo;

        public ResultRecord(int index, ISOMsg response, String resultadoEsperado, String condicionTarjeta, String caseName,String tipo) {
            this.index = index;
            this.caseName=caseName;
            this.condicionTarjeta=condicionTarjeta;
            this.response = response;
            this.resultadoEsperado=resultadoEsperado;
            this.tipo=tipo;
        }
    }


    private static Case getCaseFromRow(Row row) {
        final String tipoStr = getString(row, 1);
        final String mtiStr = getString(row, 2);
        final String resultadoStr = getString(row, 7);

        final String[] tipos = tipoStr.contains("+") ? tipoStr.split("\\+") : new String[]{tipoStr};
        final String[] mtis = mtiStr.contains("-") ? mtiStr.split("-") : new String[]{mtiStr};
        final String[] resultadosEsperados = resultadoStr.contains("/") ? resultadoStr.split("/") : new String[]{resultadoStr};

        final int total = Math.max(mtis.length, Math.max(tipos.length, resultadosEsperados.length));
        final List<Case.SpecificCase> specificCases = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            final Case.SpecificCase specificCase = new Case.SpecificCase();
            specificCase.setMti(i < mtis.length ? mtis[i] : mtis[0]);
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
                .numComercio(getString(row, 11))
                .tarjeta(getString(row, 8))
                .cvv(getString(row, 9))
                .fechaVencimiento(getString(row, 10))
                .specificCases(specificCases)
                .build();
    }




    private static String getString(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return cell.getCellType() == CellType.NUMERIC ?
                String.valueOf((long) cell.getNumericCellValue()) :
                cell.getStringCellValue();
    }



    public static boolean isReverseResponse(ISOMsg message) throws ISOException {
        return (message.getMTI().substring(1).equals("410") || message.getMTI().substring(1).equals("430") || message.getMTI().substring(1).equals("431"));
    }

    public static boolean isAuthorizationReverse(ISOMsg message) throws ISOException {
        return (message.getMTI().equals("1400") || message.getMTI().equals("1420") || message.getMTI().equals("1421"));
    }


}
