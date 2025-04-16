# 📊 Data Ingestion Tool

A web-based Spring Boot application to move data between **ClickHouse** and **Flat Files (CSV)** with support for uploading, exporting, previewing, and schema discovery.

---

## 🚀 Features

- ✅ Connect to **ClickHouse** using host, port, DB, user & JWT token
- ✅ Select table and columns to extract → **export to `output.csv`**
- ✅ Upload `.csv` flat files to ingest into ClickHouse table
- ✅ **Preview** top 5 rows of uploaded CSV before insert
- ✅ **Download uploaded file** (saved in `/uploads` folder)
- ✅ Dynamic schema detection and column selection via checkboxes
- ✅ User-friendly success and error messages
- ❌ (Bonus) No deduplication logic implemented yet

---

## ⚙️ Technologies Used

- Java 17
- Spring Boot
- Maven
- Thymeleaf
- OpenCSV
- Docker (for ClickHouse setup)

---

## 🛠️ Getting Started

### Prerequisites

- Java 17
- Maven
- Docker (for ClickHouse)
- Git (optional for cloning)

---


### Build & Run Spring Boot App

# Open project root (where pom.xml is)
cd ingestiontool

# Build project
mvn clean install

# Run the app
mvn spring-boot:run

### Access App
 http://localhost:8080

🔄 How to Use
➤ ClickHouse ➝ Flat File (Export)
Select ClickHouse option

Enter host, port, database, user, JWT

Click Connect

Select a table → click Load Columns

Choose columns → click Start Ingestion

File output.csv is created in project root

➤ Flat File ➝ ClickHouse (Upload & Insert)
Select Flat File option

Upload a .csv file with headers

Enter target ClickHouse table name (e.g., test_users)

See Preview of top 5 rows

Click  Confirm Insert

Data inserted into the table

Download link shown for uploaded file

### Known Limitations / Future Improvements

❌ No deduplication or truncate option before insert

❌ Table creation not handled if table doesn't exist

⚠️ Assumes uploaded CSV headers match target table schema

🧩 (Bonus not implemented): Multi-table joins, progress bar, data type mismatch resolution


### Run ClickHouse via Docker

```bash
docker run -d --name clickhouse-server -p 8123:8123 -p 9000:9000 \
-e CLICKHOUSE_DEFAULT_PASSWORD=admin123 \
clickhouse/clickhouse-server
---



