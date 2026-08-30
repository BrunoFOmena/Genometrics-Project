package com.ngs.analytics.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * H2 file DBs created before Phase 1 may miss new columns when ddl-auto update does not alter them.
 * Applies idempotent ALTERs on local dev startup only.
 */
@Component
@Profile("dev-h2")
public class DevH2SchemaPatch {

    private static final Logger log = LoggerFactory.getLogger(DevH2SchemaPatch.class);

    private final JdbcTemplate jdbcTemplate;

    public DevH2SchemaPatch(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyPhase1Columns() {
        patch("ALTER TABLE file_assets ADD COLUMN IF NOT EXISTS read_end VARCHAR(32) DEFAULT 'SINGLE'");
        patch("UPDATE file_assets SET read_end = 'SINGLE' WHERE read_end IS NULL");
        patch("ALTER TABLE analyses ADD COLUMN IF NOT EXISTS mate_file_asset_id UUID");
        patch("ALTER TABLE fastq_metrics ADD COLUMN IF NOT EXISTS paired_end BOOLEAN DEFAULT FALSE");
        patch("ALTER TABLE fastq_metrics ADD COLUMN IF NOT EXISTS read_countr1 BIGINT DEFAULT 0");
        patch("ALTER TABLE fastq_metrics ADD COLUMN IF NOT EXISTS read_countr2 BIGINT DEFAULT 0");
        patch("ALTER TABLE fastq_metrics ADD COLUMN IF NOT EXISTS adapter_hits_json TEXT");
        patch("ALTER TABLE fastq_metrics ADD COLUMN IF NOT EXISTS quality_heatmap_json TEXT");
    }

    private void patch(String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.debug("Dev H2 schema patch OK: {}", sql);
        } catch (Exception ex) {
            log.warn("Dev H2 schema patch failed ({}): {}", sql, ex.getMessage());
        }
    }
}
