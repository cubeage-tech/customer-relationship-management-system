package com.company.crm.common.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * NFR-7.2b: on startup, encrypts personal data written before encryption existed and fills in its
 * blind indexes. Only rows holding a plain (unprefixed) value are touched, in batches, so after
 * the first run it finds nothing and costs one quick query per column.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PiiEncryptionBackfill implements ApplicationRunner {

    private static final int BATCH = 500;

    /** A column to encrypt and, optionally, the blind-index column computed from it. */
    private record Col(String name, String hashColumn, BiFunction<DataEncryption, String, String> index) {}

    private record Table(String name, List<Col> columns) {}

    private static final List<Table> TABLES = List.of(
            new Table("customers", List.of(
                    new Col("email", "email_hash", DataEncryption::emailIndex),
                    new Col("phone", "phone_hash", DataEncryption::phoneIndex),
                    new Col("address", null, null))),
            new Table("customer_contacts", List.of(
                    new Col("email", "email_hash", DataEncryption::emailIndex),
                    new Col("phone", "phone_hash", DataEncryption::phoneIndex))),
            new Table("leads", List.of(
                    new Col("contact_email", "email_hash", DataEncryption::emailIndex),
                    new Col("contact_phone", "phone_hash", DataEncryption::phoneIndex))),
            new Table("users", List.of(new Col("phone", null, null))),
            new Table("tenants", List.of(new Col("bank_account_number", null, null))),
            new Table("campaign_recipients", List.of(new Col("address", null, null))),
            new Table("communication_logs", List.of(new Col("counterpart", "counterpart_hash", DataEncryption::counterpartIndex))));

    private final JdbcTemplate jdbc;
    private final DataEncryption encryption;
    private final TransactionTemplate transactions;

    @Override
    public void run(ApplicationArguments args) {
        for (Table table : TABLES) {
            for (Col col : table.columns()) {
                int done = encryptColumn(table.name(), col);
                if (done > 0) {
                    log.info("NFR-7.2b: encrypted {} existing {}.{} value(s)", done, table.name(), col.name());
                }
            }
        }
    }

    private int encryptColumn(String table, Col col) {
        String select = "SELECT id, " + col.name() + " FROM " + table + " WHERE " + col.name() + " IS NOT NULL AND "
                + col.name() + " NOT LIKE '" + DataEncryption.PREFIX + "%' ORDER BY id LIMIT " + BATCH;
        String update = "UPDATE " + table + " SET " + col.name() + " = ?"
                + (col.hashColumn() != null ? ", " + col.hashColumn() + " = ?" : "") + " WHERE id = ?";
        int total = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(select);
            if (rows.isEmpty()) {
                return total;
            }
            List<Object[]> batch = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String plain = (String) row.get(col.name());
                Object id = row.get("id");
                batch.add(col.hashColumn() != null
                        ? new Object[]{encryption.encrypt(plain), col.index().apply(encryption, plain), id}
                        : new Object[]{encryption.encrypt(plain), id});
            }
            transactions.executeWithoutResult(s -> jdbc.batchUpdate(update, batch));
            total += rows.size();
        }
    }
}
