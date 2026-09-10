package storage;

import model.Database;
import model.Record;
import model.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * StorageManager implementation holding all state essentially in memory via the Database catalog.
 */
public class MemoryStorageManager implements StorageManager {
    private Database database;

    public MemoryStorageManager(Database database) {
        this.database = database;
    }

    @Override
    public void createTable(Table table) {}

    @Override
    public void dropTable(String tableName) {}

    @Override
    public boolean tableExists(String tableName) {
        return database.containsTable(tableName);
    }

    @Override
    public int insertRecord(String tableName, Record record) {
        Table table = database.getTable(tableName);
        if (table != null) {
            table.addRecord(record);
            return table.getRecords().size() - 1;
        }
        return -1;
    }

    @Override
    public List<Record> getRecords(String tableName) {
        Table table = database.getTable(tableName);
        if (table != null) return new ArrayList<>(table.getRecords());
        return new ArrayList<>();
    }
    
    @Override
    public Record getRecord(String tableName, int recordPosition) {
        Table table = database.getTable(tableName);
        if (table != null && recordPosition >= 0 && recordPosition < table.getRecords().size()) {
            return table.getRecords().get(recordPosition);
        }
        return null;
    }

    @Override
    public void updateRecords(String tableName, List<Record> records) {}

    @Override
    public void deleteRecords(String tableName, List<Record> recordsToDelete) {
        Table table = database.getTable(tableName);
        if (table != null) {
            for (Record record : recordsToDelete) {
                record.setDeleted(true);
            }
        }
    }
}
