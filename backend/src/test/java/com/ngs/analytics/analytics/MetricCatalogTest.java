package com.ngs.analytics.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricCatalogTest {

    private final MetricCatalog catalog = new MetricCatalog();

    @Test
    void exposesVersionedDefinitionsWithUnits() {
        MetricCatalog.Definition definition = catalog.require("mean_quality");

        assertThat(definition.domain()).isEqualTo("FASTQ");
        assertThat(definition.unit()).isEqualTo("Phred");
        assertThat(definition.version()).isEqualTo(1);
    }

    @Test
    void rejectsMetricsOutsideTheSemanticCatalog() {
        assertThatThrownBy(() -> catalog.require("invented_metric"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown metric");
    }
}
