package storage;

import exception.StorageException;
import model.Record;
import model.Table;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists records into binary files on disk while adhering to the
 * StorageManager contract.
 */
public class FileStorageManager implements StorageManager {

    private static final String DB_DIR = "database";
    private static final String TABLES_DIR = DB_DIR + "/tables";
    private static final String METADATA_DIR = DB_DIR + "/metadata";
    private static final String INDEXES_DIR = DB_DIR + "/indexes";

    private RecordSerializer serializer;
    private RecordDeserializer deserializer;
    private MetadataManager metadataManager;
    private Map<String, List<Record>> sessionCache;

    public FileStorageManager() {
        this.serializer = new RecordSerializer();
        this.deserializer = new RecordDeserializer();
        this.metadataManager = new MetadataManager();
        this.sessionCache = new HashMap<>();

        createDirectories();
    }

    private void createDirectories() {
        new File(TABLES_DIR).mkdirs();
        new File(METADATA_DIR).mkdirs();
        new File(INDEXES_DIR).mkdirs();
    }

    private File getTableFile(String tableName) {
        return new File(TABLES_DIR, tableName + ".db");
    }

    @Override
    public void createTable(Table table) {
        File file = getTableFile(table.getTableName());
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            metadataManager.saveSchema(table);
        } catch (IOException e) {
            throw new StorageException("Failed to create table file: " + e.getMessage());
        }
    }

    @Override
    public void dropTable(String tableName) {
        File file = getTableFile(tableName);
        if (file.exists()) {
            file.delete();
        }
        metadataManager.deleteSchema(tableName);
        sessionCache.remove(tableName);
    }

    @Override
    public boolean tableExists(String tableName) {
        return getTableFile(tableName).exists();
    }

    @Override
    public int insertRecord(String tableName, Record record) {
        File file = getTableFile(tableName);
        if (!file.exists()) {
            throw new StorageException("Table file does not exist: " + tableName);
        }

        try (FileOutputStream fos = new FileOutputStream(file, true);
                DataOutputStream dos = new DataOutputStream(fos)) {

            byte[] data = serializer.serialize(record);
            dos.writeInt(data.length);
            dos.write(data);

        } catch (IOException e) {
            throw new StorageException("Failed to insert record: " + e.getMessage());
        }
        
        List<Record> cached = sessionCache.get(tableName);
        if (cached != null) {
            cached.add(record);
            return cached.size() - 1;
        } else {
            return getRecords(tableName).size() - 1;
        }
    }

    @Override
    public List<Record> getRecords(String tableName) {
        if (sessionCache.containsKey(tableName)) {
            return sessionCache.get(tableName);
        }

        File file = getTableFile(tableName);
        List<Record> records = new ArrayList<>();

        if (!file.exists()) {
            return records;
        }

        try (FileInputStream fis = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(fis)) {

            while (true) {
                try {
                    int length = dis.readInt();
                    byte[] data = new byte[length];
                    dis.readFully(data);

                    Record record = deserializer.deserialize(data);
                    records.add(record);
                } catch (EOFException eofe) {
                    break;
                }
            }

        } catch (IOException e) {
            throw new StorageException("Failed to read records: " + e.getMessage());
        }

        sessionCache.put(tableName, records);
        return records;
    }

    @Override
    public Record getRecord(String tableName, int recordPosition) {
        System.out.println(
                "Reading record at position=" + recordPosition);
        List<Record> records = sessionCache.get(tableName);
        if (records == null) {
            records = getRecords(tableName);
        }
        if (recordPosition >= 0 && recordPosition < records.size()) {
            return records.get(recordPosition);
        }
        return null;
    }

    @Override
    public void updateRecords(String tableName, List<Record> records) {
        List<Record> allRecords = sessionCache.get(tableName);
        if (allRecords != null) {
            rewriteFile(tableName, allRecords);
        }
    }

    @Override
    public void deleteRecords(String tableName, List<Record> recordsToDelete) {
        List<Record> allRecords = sessionCache.get(tableName);
        if (allRecords != null) {
            for (Record record : recordsToDelete) {
                record.setDeleted(true);
            }
            rewriteFile(tableName, allRecords);
        }
    }

    private void rewriteFile(String tableName, List<Record> records) {
        File file = getTableFile(tableName);

        try (FileOutputStream fos = new FileOutputStream(file, false);
                DataOutputStream dos = new DataOutputStream(fos)) {

            for (Record record : records) {
                if (record.isDeleted()) continue;
                byte[] data = serializer.serialize(record);
                dos.writeInt(data.length);
                dos.write(data);
            }

        } catch (IOException e) {
            throw new StorageException("Failed to rewrite table file: " + e.getMessage());
        }
    }
}
