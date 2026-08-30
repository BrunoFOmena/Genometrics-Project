package com.ngs.analytics.fastq;

import java.util.List;

public final class AdapterCatalog {

    public record Adapter(String name, String sequence) {
    }

    private static final List<Adapter> ADAPTERS = List.of(
            new Adapter("Illumina_Universal", "AGATCGGAAGAG"),
            new Adapter("Illumina_Index", "GATCGGAAGAGC"),
            new Adapter("TruSeq_Universal", "AATGATACGGCGACCACCGAGATCTACAC"),
            new Adapter("TruSeq_Index", "CAAGCAGAAGACGGCATACGAGAT"),
            new Adapter("Nextera_Transposase", "CTGTCTCTTATACACATCT"),
            new Adapter("Nextera_Read1", "TCGTCGGCAGCGTCAGATGTGTATAAGAGACAG"),
            new Adapter("Nextera_Read2", "GTCTCGTGGGCTCGGAGATGTGTATAAGAGACAG"),
            new Adapter("SmallRNA_3p", "TGGAATTCTCGGGTGCCAAGG"),
            new Adapter("SmallRNA_5p", "GTTCAGAGTTCTACAGTCCGACGATC"),
            new Adapter("PolyA", "AAAAAAAAAAAA")
    );

    private AdapterCatalog() {
    }

    public static List<Adapter> all() {
        return ADAPTERS;
    }
}
