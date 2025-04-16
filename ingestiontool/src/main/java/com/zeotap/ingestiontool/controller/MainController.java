package com.zeotap.ingestiontool.controller;

import com.opencsv.CSVWriter;
import com.opencsv.CSVReader;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileWriter;
import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;



@Controller
public class MainController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/connect")
    public String connect(@RequestParam Map<String, String> params, Model model) {
        String sourceType = params.get("sourceType");

        if ("clickhouse".equals(sourceType)) {
            String host = params.get("host");
            String port = params.get("port");
            String database = params.get("database");
            String user = params.get("user");
            String jwtToken = params.get("jwtToken");

            String jdbcUrl = "jdbc:clickhouse://" + host + ":" + port + "/" + database + "?compress=0";
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", jwtToken);

            try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
                model.addAttribute("message", " Connected successfully to ClickHouse!");

                // Fetch table names
                List<String> tableNames = new ArrayList<>();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SHOW TABLES");

                while (rs.next()) {
                    tableNames.add(rs.getString(1));
                }

                model.addAttribute("tables", tableNames);
                model.addAttribute("database", database); // optional, for display
            } catch (SQLException e) {
                model.addAttribute("message", " Failed to connect: " + e.getMessage());
            }
        } else {
            model.addAttribute("message", " Flat File selected (connection logic coming soon)");
        }

        return "index";
    }

    @PostMapping("/load-columns")
    public String loadColumns(@RequestParam("selectedTable") String selectedTable, Model model) {
        String jdbcUrl = "jdbc:clickhouse://localhost:8123/default?compress=0";
        Properties props = new Properties();
        props.setProperty("user", "default");
        props.setProperty("password", "admin123");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
            List<String> columns = new ArrayList<>();

            String query = "DESCRIBE TABLE " + selectedTable;
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                String columnName = rs.getString("name");
                columns.add(columnName);
            }

            model.addAttribute("selectedTable", selectedTable);
            model.addAttribute("columns", columns);
        } catch (SQLException e) {
            model.addAttribute("message", " Failed to load columns: " + e.getMessage());
        }

        return "index";
    }


    @PostMapping("/start-ingestion")
    public String startIngestion(
            @RequestParam("selectedTable") String selectedTable,
            @RequestParam("selectedColumns") List<String> selectedColumns,
            Model model
    ) {
        String jdbcUrl = "jdbc:clickhouse://localhost:8123/default?compress=0";
        Properties props = new Properties();
        props.setProperty("user", "default");
        props.setProperty("password", "admin123");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
            String columnsCsv = String.join(", ", selectedColumns);
            String query = "SELECT " + columnsCsv + " FROM " + selectedTable;

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);

            String outputFile = "output.csv";
            try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile))) {
                writer.writeNext(selectedColumns.toArray(new String[0])); // Header

                int rowCount = 0;
                while (rs.next()) {
                    String[] row = new String[selectedColumns.size()];
                    for (int i = 0; i < selectedColumns.size(); i++) {
                        row[i] = rs.getString(selectedColumns.get(i));
                    }
                    writer.writeNext(row);
                    rowCount++;
                }

                model.addAttribute("message", " Ingestion complete! " + rowCount + " records written to " + outputFile);
            }

        } catch (Exception e) {
            model.addAttribute("message", " Ingestion failed: " + e.getMessage());
        }

        return "index";
    }

    @PostMapping("/upload-csv")
    public String uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetTable") String targetTable,
            Model model
    ) {
        String projectPath = System.getProperty("user.dir");
        File uploadDir = new File(projectPath, "uploads");
        if (!uploadDir.exists()) uploadDir.mkdirs();

        File savedFile = new File(uploadDir, file.getOriginalFilename());

        try {
            file.transferTo(savedFile);

            // Read first few lines of CSV for preview
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(savedFile));
                 CSVReader csvReader = new CSVReader(reader)) {

                List<String[]> previewRows = new ArrayList<>();
                String[] row;
                int rowCount = 0;

                while ((row = csvReader.readNext()) != null && rowCount < 6) { // 1 header + 5 rows
                    previewRows.add(row);
                    rowCount++;
                }

                model.addAttribute("previewRows", previewRows);
                model.addAttribute("uploadedFilename", file.getOriginalFilename());
                model.addAttribute("targetTable", targetTable);
                model.addAttribute("message", " Preview loaded. Click confirm to insert.");
            }

        } catch (Exception e) {
            model.addAttribute("message", " Upload failed: " + e.getMessage());
        }

        return "index";
    }


    @PostMapping("/confirm-insert")
    public String confirmInsert(
            @RequestParam("filename") String filename,
            @RequestParam("targetTable") String targetTable,
            Model model
    ) {
        String jdbcUrl = "jdbc:clickhouse://localhost:8123/default?compress=0";
        Properties props = new Properties();
        props.setProperty("user", "default");
        props.setProperty("password", "admin123");

        try (
                Connection conn = DriverManager.getConnection(jdbcUrl, props);
                InputStreamReader reader = new InputStreamReader(new FileInputStream("uploads/" + filename));
                CSVReader csvReader = new CSVReader(reader)
        ) {
            List<String[]> allRows = csvReader.readAll();
            if (allRows.isEmpty()) {
                model.addAttribute("message", " CSV is empty.");
                return "index";
            }

            String[] headers = allRows.get(0);
            String columnNames = String.join(", ", headers);

            String insertSql = "INSERT INTO " + targetTable + " (" + columnNames + ") VALUES ";
            List<String> valueRows = new ArrayList<>();

            for (int i = 1; i < allRows.size(); i++) {
                String[] row = allRows.get(i);
                StringBuilder sb = new StringBuilder("(");
                for (int j = 0; j < row.length; j++) {
                    sb.append("'").append(row[j].replace("'", "''")).append("'");
                    if (j < row.length - 1) sb.append(", ");
                }
                sb.append(")");
                valueRows.add(sb.toString());
            }

            Statement stmt = conn.createStatement();
            stmt.execute(insertSql + String.join(", ", valueRows));

            model.addAttribute("message", " Insert complete! " + (allRows.size() - 1) + " records added to " + targetTable);

        } catch (Exception e) {
            model.addAttribute("message", " Insert failed: " + e.getMessage());
        }

        return "index";
    }



    @GetMapping("/download")
    public void downloadFile(@RequestParam("filename") String filename, HttpServletResponse response) {
        try {
            String filePath = System.getProperty("user.dir") + "/uploads/" + filename;
            File file = new File(filePath);

            if (file.exists()) {
                response.setContentType("text/csv");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                Files.copy(file.toPath(), response.getOutputStream());
                response.getOutputStream().flush();
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
