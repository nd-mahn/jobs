package nd.mahn.jobproducerconsumerimporter.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nd.mahn.jobproducerconsumerimporter.dto.MnpDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MnpRepository {
    private final JdbcTemplate jdbcTemplate;

    @Value("${sql.merge}")
    private String merge;
    @Value("${sql.truncate}")
    private String truncate;
    @Value("${sql.create-mnp}")
    private String create;
    @Value("${sql.create-temp}")
    private String createTemp;
    @Value("${sql.insert-temp}")
    private String insert;

    @PostConstruct
    private void initCreateTable() {
        try {
            create();
            log.info("Table MNP checked/created.");
        } catch (Exception e) {
            log.warn("Could not create table MNP: {}", e.getMessage());
        }
        
        try {
            createTemp();
            log.info("Table MNP_TEMP checked/created.");
        } catch (Exception e) {
            log.warn("Could not create table MNP_TEMP: {}", e.getMessage());
        }
    }

    public void merge() {
        log.info("Starting SQL Merge process...");
        long start = System.currentTimeMillis();
        int rows = jdbcTemplate.update(merge);
        log.info("Merge completed. Rows affected: {}. Duration: {} ms", rows, (System.currentTimeMillis() - start));
    }

    public void truncate() {
        log.info("Truncating table MNP_TEMP...");
        jdbcTemplate.update(truncate);
        log.info("Truncate completed.");
    }

    public void create() {
        jdbcTemplate.execute(create);
    }

    public void createTemp() {
        jdbcTemplate.execute(createTemp);
    }

    public void insert(List<MnpDto> data) {
        if (data == null || data.isEmpty()) return;
        log.info("Inserting {} records into MNP_TEMP...", data.size());
        long start = System.currentTimeMillis();
        jdbcTemplate.batchUpdate(insert, new MnpBatchSetter(data));
        log.info("Batch insert completed in {} ms", (System.currentTimeMillis() - start));
    }
}
