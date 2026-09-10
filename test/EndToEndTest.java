import engine.DatabaseEngine;
import engine.QueryResult;
import java.io.File;

public class EndToEndTest {
    public static void main(String[] args) {
        // Clear previous db if any
        deleteDirectory(new File("database"));
        
        DatabaseEngine db = new DatabaseEngine("TestDB");
        
        execute(db, "CREATE TABLE users (id INT PRIMARY KEY, name STRING)");
        execute(db, "CREATE INDEX users_id_idx ON users (id)");
        
        System.out.println("\n--- Inserting 2000 records ---");
        long start = System.currentTimeMillis();
        for (int i = 1; i <= 2000; i++) {
            // Suppress output for bulk inserts
            db.execute("INSERT INTO users VALUES (" + i + ", 'User_" + i + "')");
        }
        long end = System.currentTimeMillis();
        System.out.println("Inserted 2000 records in " + (end - start) + "ms.");
        
        System.out.println("\n--- Select User 1000 (Index Scan) ---");
        execute(db, "SELECT * FROM users WHERE id = 1000");
        
        System.out.println("\n--- Updating User 1000 ---");
        execute(db, "UPDATE users SET name = 'Updated_User_1000' WHERE id = 1000");
        
        System.out.println("\n--- Select User 1000 After Update (Index Scan) ---");
        execute(db, "SELECT * FROM users WHERE id = 1000");
        
        System.out.println("\n--- Deleting User 500 ---");
        execute(db, "DELETE FROM users WHERE id = 500");
        
        System.out.println("\n--- Select User 500 After Delete (Index Scan) ---");
        execute(db, "SELECT * FROM users WHERE id = 500");
        
        System.out.println("\n--- Deleting User 1500 ---");
        execute(db, "DELETE FROM users WHERE id = 1500");
        
        System.out.println("\n--- Select User 1500 After Delete (Index Scan) ---");
        execute(db, "SELECT * FROM users WHERE id = 1500");
        
        System.out.println("\n--- Select All Records Count ---");
        QueryResult res = db.execute("SELECT * FROM users");
        if (res.getRecords() != null) {
            System.out.println("Total records found: " + res.getRecords().size() + " (Expected: 1998)");
            if (res.getRecords().size() == 1998) {
                System.out.println("Test PASSED: Correct number of records remain.");
            } else {
                System.out.println("Test FAILED: Incorrect record count.");
            }
        }
        
    }
    
    private static void execute(DatabaseEngine db, String query) {
        QueryResult res = db.execute(query);
        System.out.println("Query: " + query);
        if (res.getRecords() != null) {
            System.out.println(res.toString());
        } else {
            System.out.println(res.getMessage());
        }
    }
    
    private static void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        deleteDirectory(f);
                    } else {
                        f.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
