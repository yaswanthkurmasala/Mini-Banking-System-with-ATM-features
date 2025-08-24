# 🏦 Mini Banking System (Java + MySQL)

A **console-based banking application** built with **Java** and **MySQL**.  
This project simulates a real-world banking system with secure **PIN authentication**,  
**account management**, and **transaction features**.

---

## ✨ Features

- 🔐 **Secure Account Creation**
  - Generates a unique 10-digit Account Number.
  - PIN is stored as **SHA-256 hash** with random **salt** for security.
- ✅ **Login with Account Locking**
  - 3 wrong PIN attempts → account locked.
- 💰 **Banking Operations**
  - Deposit money.
  - Withdraw money (with insufficient balance check).
  - Transfer between accounts.
  - Check balance anytime.
- 📜 **Transaction History**
  - View last 10 transactions with timestamp.
- 🛡️ **Safe Transactions**
  - Uses MySQL transactions (`COMMIT` / `ROLLBACK`) to prevent inconsistent states.

---

## 🗂️ Project Structure

📂 src/

└── BankApp.java # Main Java Application

📂 lib/

└── mysql-connector-j-9.4.0.jar # MySQL JDBC Driver

.vscode/

├── settings.json # Library reference config

└── launch.json # VS Code run config


---

## 🛠️ Tech Stack

- **Java 17+**
- **MySQL 8+**
- **JDBC (MySQL Connector/J)**
- **VS Code** (with Java Extension Pack)

---

## 🗄️ Database Setup

Run the following SQL in MySQL:

```sql
   CREATE DATABASE bankdb;
   USE bankdb;
   
   CREATE TABLE accounts (
       account_no VARCHAR(20) PRIMARY KEY,
       name VARCHAR(100) NOT NULL,
       pin_hash VARCHAR(256) NOT NULL,
       salt VARCHAR(100) NOT NULL,
       balance DECIMAL(15,2) DEFAULT 0.00,
       failed_attempts INT DEFAULT 0,
       is_locked BOOLEAN DEFAULT FALSE
   );
   
   CREATE TABLE transactions (
       id INT AUTO_INCREMENT PRIMARY KEY,
       account_no VARCHAR(20),
       type VARCHAR(20),
       amount DECIMAL(15,2),
       balance_after DECIMAL(15,2),
       related_account VARCHAR(20),
       note VARCHAR(200),
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );
```

---

## ▶️ How to Run

1. Clone the project:

         git clone https://github.com/<your-username>/BankApp.git
         cd BankApp


2. Place the MySQL Connector JAR in src/lib/
   Example: src/lib/mysql-connector-j-9.4.0.jar

3. Update DB credentials in BankApp.java:

         static final String URL  = "jdbc:mysql://localhost:3306/bankdb";
         static final String USER = "root";
         static final String PASS = "your-password"; 


4. Compile and run:

         javac -cp "src/lib/mysql-connector-j-9.4.0.jar;." src/BankApp.java
         java -cp "src/lib/mysql-connector-j-9.4.0.jar;." src.BankApp

---

## 📖 Sample Flow

      ==== Mini Banking System ====
      1) Register
      2) Login
      0) Exit
      Choose: 1
      
      Enter your Name: Yaswanth
      Set a 4-digit PIN: 1234
      ✅ Account created successfully!
      Your Account No: 1002345678
      
      --- After Login ---
      1) Balance
      2) Deposit
      3) Withdraw
      4) Transfer
      5) Last 10 Transactions
      9) Logout

---

## 📌 Notes

   - Change DB credentials (USER, PASS) before running.
   
   - Account locks after 3 failed login attempts.
   
   - Use transaction-safe operations to avoid data corruption.

## 🎯 Future Enhancements

   - Add Interest Calculation.

   - Implement Admin Panel for managing accounts.
   
   - Build a GUI (Swing/JavaFX) for better UI.
   
   - Deploy as a Spring Boot REST API.
