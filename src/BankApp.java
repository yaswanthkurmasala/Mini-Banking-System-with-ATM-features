package src;
import java.sql.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Scanner;
import java.math.BigDecimal;

public class BankApp {
    // 🔧 Update these for your machine
    static final String URL  = "jdbc:mysql://localhost:3306/bankdb";
    static final String USER = "root";
    static final String PASS = "Yaswanth@77"; // <-- change me

    static final int MAX_PIN_ATTEMPTS = 3;

    // ===== Utilities =====
    static Connection getConn() throws Exception {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    static String generateAccountNo() {
        // simple 10-digit account no
        long base = 1_000_000_000L + (long)(Math.random() * 8_999_999_999L);
        return String.valueOf(base);
    }

    static String genSalt() {
        byte[] salt = new byte[12];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static String hashPin(String pin, String salt) throws Exception {
        return sha256(pin + ":" + salt);
    }

    // ===== Core features =====
    static String register(Scanner sc) {
        try (Connection c = getConn()) {
            System.out.print("Enter your Name: ");
            String name = sc.nextLine().trim();

            String pin;
            while (true) {
                System.out.print("Set a 4-digit PIN: ");
                pin = sc.nextLine().trim();
                if (pin.matches("\\d{4}")) break;
                System.out.println("PIN must be exactly 4 digits.");
            }

            String accountNo = generateAccountNo();
            String salt = genSalt();
            String pinHash = hashPin(pin, salt);

            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO accounts (account_no, name, pin_hash, salt, balance) VALUES (?,?,?,?,0.00)"
            )) {
                ps.setString(1, accountNo);
                ps.setString(2, name);
                ps.setString(3, pinHash);
                ps.setString(4, salt);
                ps.executeUpdate();
            }

            System.out.println("\n✅ Account created successfully!");
            System.out.println("Your Account No: " + accountNo);
            System.out.println("(Please save it safely)\n");
            return accountNo;
        } catch (Exception e) {
            System.out.println("Error (register): " + e.getMessage());
            return null;
        }
    }

    static String login(Scanner sc) {
        System.out.print("Enter Account No: ");
        String accountNo = sc.nextLine().trim();

        System.out.print("Enter 4-digit PIN: ");
        String pin = sc.nextLine().trim();

        try (Connection c = getConn()) {
            c.setAutoCommit(false);

            try (PreparedStatement ps = c.prepareStatement(
                "SELECT pin_hash, salt, failed_attempts, is_locked FROM accounts WHERE account_no = ? FOR UPDATE"
            )) {
                ps.setString(1, accountNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("❌ Account not found.");
                        c.rollback();
                        return null;
                    }

                    String dbHash = rs.getString("pin_hash");
                    String salt = rs.getString("salt");
                    int attempts = rs.getInt("failed_attempts");
                    boolean locked = rs.getBoolean("is_locked");

                    if (locked) {
                        System.out.println("🔒 Account is locked due to multiple wrong PIN attempts. Contact support.");
                        c.rollback();
                        return null;
                    }

                    String inputHash = hashPin(pin, salt);
                    if (!inputHash.equals(dbHash)) {
                        attempts++;
                        boolean lockNow = attempts >= MAX_PIN_ATTEMPTS;

                        try (PreparedStatement u = c.prepareStatement(
                                "UPDATE accounts SET failed_attempts = ?, is_locked = ? WHERE account_no = ?")) {
                            u.setInt(1, attempts);
                            u.setBoolean(2, lockNow);
                            u.setString(3, accountNo);
                            u.executeUpdate();
                        }

                        c.commit();
                        if (lockNow) {
                            System.out.println("❌ Wrong PIN. Account has been LOCKED.");
                        } else {
                            System.out.println("❌ Wrong PIN. Attempts: " + attempts + "/" + MAX_PIN_ATTEMPTS);
                        }
                        return null;
                    }

                    // success → reset attempts
                    try (PreparedStatement r = c.prepareStatement(
                            "UPDATE accounts SET failed_attempts = 0 WHERE account_no = ?")) {
                        r.setString(1, accountNo);
                        r.executeUpdate();
                    }

                    c.commit();
                    System.out.println("✅ Login success. Welcome!");
                    return accountNo;
                }
            } catch (Exception inner) {
                c.rollback();
                throw inner;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.out.println("Error (login): " + e.getMessage());
            return null;
        }
    }

    static void showBalance(String accountNo) {
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT name, balance FROM accounts WHERE account_no = ?")) {
            ps.setString(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Name: " + rs.getString("name"));
                    System.out.println("Balance: ₹" + rs.getBigDecimal("balance"));
                } else {
                    System.out.println("Account not found.");
                }
            }
        } catch (Exception e) {
            System.out.println("Error (balance): " + e.getMessage());
        }
    }

    static void recordTxn(Connection c, String accountNo, String type, BigDecimal amount,
                          BigDecimal balanceAfter, String related, String note) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO transactions (account_no, type, amount, balance_after, related_account, note) " +
            "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, accountNo);
            ps.setString(2, type);
            ps.setBigDecimal(3, amount);
            ps.setBigDecimal(4, balanceAfter);
            ps.setString(5, related);
            ps.setString(6, note);
            ps.executeUpdate();
        }
    }

    static void deposit(String accountNo, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Amount must be positive.");
            return;
        }

        try (Connection c = getConn()) {
            c.setAutoCommit(false);

            BigDecimal current;
            try (PreparedStatement g = c.prepareStatement(
                    "SELECT balance FROM accounts WHERE account_no = ? FOR UPDATE")) {
                g.setString(1, accountNo);
                try (ResultSet rs = g.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Account not found.");
                        c.rollback();
                        return;
                    }
                    current = rs.getBigDecimal("balance");
                }
            }

            BigDecimal newBal = current.add(amount);
            try (PreparedStatement u = c.prepareStatement(
                    "UPDATE accounts SET balance = ? WHERE account_no = ?")) {
                u.setBigDecimal(1, newBal);
                u.setString(2, accountNo);
                u.executeUpdate();
            }

            recordTxn(c, accountNo, "DEPOSIT", amount, newBal, null, "Cash deposit");
            c.commit();

            System.out.println("✅ Deposited ₹" + amount + ". New Balance: ₹" + newBal);
        } catch (Exception e) {
            System.out.println("Error (deposit): " + e.getMessage());
        }
    }

    static void withdraw(String accountNo, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Amount must be positive.");
            return;
        }

        try (Connection c = getConn()) {
            c.setAutoCommit(false);

            BigDecimal current;
            try (PreparedStatement g = c.prepareStatement(
                    "SELECT balance FROM accounts WHERE account_no = ? FOR UPDATE")) {
                g.setString(1, accountNo);
                try (ResultSet rs = g.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Account not found.");
                        c.rollback();
                        return;
                    }
                    current = rs.getBigDecimal("balance");
                }
            }

            if (current.compareTo(amount) < 0) {
                System.out.println("❌ Insufficient funds.");
                c.rollback();
                return;
            }

            BigDecimal newBal = current.subtract(amount);
            try (PreparedStatement u = c.prepareStatement(
                    "UPDATE accounts SET balance = ? WHERE account_no = ?")) {
                u.setBigDecimal(1, newBal);
                u.setString(2, accountNo);
                u.executeUpdate();
            }

            recordTxn(c, accountNo, "WITHDRAW", amount, newBal, null, "Cash withdrawal");
            c.commit();

            System.out.println("✅ Withdrawn ₹" + amount + ". New Balance: ₹" + newBal);
        } catch (Exception e) {
            System.out.println("Error (withdraw): " + e.getMessage());
        }
    }

    static void transfer(String fromAcc, String toAcc, BigDecimal amount) {
        if (fromAcc.equals(toAcc)) {
            System.out.println("Cannot transfer to same account.");
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Amount must be positive.");
            return;
        }

        try (Connection c = getConn()) {
            c.setAutoCommit(false);

            BigDecimal fromBal, toBal;

            // lock both rows (order by account_no to avoid deadlocks)
            String a1 = fromAcc.compareTo(toAcc) < 0 ? fromAcc : toAcc;
            String a2 = fromAcc.compareTo(toAcc) < 0 ? toAcc : fromAcc;

            try (PreparedStatement g1 = c.prepareStatement(
                    "SELECT account_no, balance FROM accounts WHERE account_no IN (?,?) FOR UPDATE")) {
                g1.setString(1, a1);
                g1.setString(2, a2);
                try (ResultSet rs = g1.executeQuery()) {
                    boolean foundFrom = false, foundTo = false;
                    BigDecimal tmpFrom = null, tmpTo = null;

                    while (rs.next()) {
                        String acc = rs.getString("account_no");
                        BigDecimal bal = rs.getBigDecimal("balance");
                        if (acc.equals(fromAcc)) { tmpFrom = bal; foundFrom = true; }
                        if (acc.equals(toAcc))   { tmpTo   = bal; foundTo   = true; }
                    }

                    if (!foundFrom || !foundTo) {
                        System.out.println("❌ One or both accounts not found.");
                        c.rollback();
                        return;
                    }
                    fromBal = tmpFrom;
                    toBal   = tmpTo;
                }
            }

            if (fromBal.compareTo(amount) < 0) {
                System.out.println("❌ Insufficient funds.");
                c.rollback();
                return;
            }

            BigDecimal newFrom = fromBal.subtract(amount);
            BigDecimal newTo   = toBal.add(amount);

            try (PreparedStatement u1 = c.prepareStatement(
                        "UPDATE accounts SET balance = ? WHERE account_no = ?");
                 PreparedStatement u2 = c.prepareStatement(
                        "UPDATE accounts SET balance = ? WHERE account_no = ?")) {
                u1.setBigDecimal(1, newFrom); u1.setString(2, fromAcc); u1.executeUpdate();
                u2.setBigDecimal(1, newTo);   u2.setString(2, toAcc);   u2.executeUpdate();
            }

            recordTxn(c, fromAcc, "TRANSFER_OUT", amount, newFrom, toAcc, "Transfer to " + toAcc);
            recordTxn(c, toAcc,   "TRANSFER_IN",  amount, newTo,   fromAcc, "Transfer from " + fromAcc);

            c.commit();
            System.out.println("✅ Transfer successful. New Balance: ₹" + newFrom);
        } catch (Exception e) {
            System.out.println("Error (transfer): " + e.getMessage());
        }
    }

    static void history(String accountNo, int limit) {
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT type, amount, balance_after, related_account, created_at " +
                "FROM transactions WHERE account_no = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, accountNo);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n--- Last " + limit + " Transactions ---");
                while (rs.next()) {
                    String type = rs.getString("type");
                    BigDecimal amt = rs.getBigDecimal("amount");
                    BigDecimal bal = rs.getBigDecimal("balance_after");
                    String rel = rs.getString("related_account");
                    Timestamp t = rs.getTimestamp("created_at");
                    System.out.printf("%-14s ₹%-10s Bal:₹%-10s Rel:%-12s %s%n",
                            type, amt, bal, (rel==null?"-":rel), t.toString());
                }
                System.out.println("---------------------------------------\n");
            }
        } catch (Exception e) {
            System.out.println("Error (history): " + e.getMessage());
        }
    }

    // ===== CLI =====
    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.println("==== Mini Banking System ====");
                System.out.println("1) Register");
                System.out.println("2) Login");
                System.out.println("0) Exit");
                System.out.print("Choose: ");
                String ch = sc.nextLine().trim();

                if ("1".equals(ch)) {
                    register(sc);
                } else if ("2".equals(ch)) {
                    String acc = login(sc);
                    if (acc != null) loggedInMenu(sc, acc);
                } else if ("0".equals(ch)) {
                    System.out.println("Bye!");
                    break;
                } else {
                    System.out.println("Invalid choice.\n");
                }
            }
        }
    }

    static void loggedInMenu(Scanner sc, String accountNo) {
        while (true) {
            System.out.println("\n-- Account: " + accountNo + " --");
            System.out.println("1) Balance");
            System.out.println("2) Deposit");
            System.out.println("3) Withdraw");
            System.out.println("4) Transfer");
            System.out.println("5) Last 10 Transactions");
            System.out.println("9) Logout");
            System.out.print("Choose: ");
            String ch = sc.nextLine().trim();

            try {
                switch (ch) {
                    case "1":
                        showBalance(accountNo);
                        break;
                    case "2":
                        System.out.print("Amount to deposit: ");
                        BigDecimal d = new BigDecimal(sc.nextLine().trim());
                        deposit(accountNo, d);
                        break;
                    case "3":
                        System.out.print("Amount to withdraw: ");
                        BigDecimal w = new BigDecimal(sc.nextLine().trim());
                        withdraw(accountNo, w);
                        break;
                    case "4":
                        System.out.print("To Account No: ");
                        String to = sc.nextLine().trim();
                        System.out.print("Amount: ");
                        BigDecimal amt = new BigDecimal(sc.nextLine().trim());
                        transfer(accountNo, to, amt);
                        break;
                    case "5":
                        history(accountNo, 10);
                        break;
                    case "9":
                        return;
                    default:
                        System.out.println("Invalid option.");
                }
            } catch (NumberFormatException nfe) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}
