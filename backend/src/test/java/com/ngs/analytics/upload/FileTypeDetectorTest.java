package com.ngs.analytics.upload;

import com.ngs.analytics.domain.FileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    @Test
    void detectsCompressedAndPlainFormats() {
        assertEquals(FileType.FASTQ_GZ, detector.detect("reads.fastq.gz"));
        assertEquals(FileType.VCF, detector.detect("variants.vcf"));
        assertEquals(FileType.FASTA, detector.detect("ref.fa"));
        assertTrue(detector.isGzipped(FileType.VCF_GZ));
        assertTrue(detector.isFastq(FileType.FASTQ));
    }

    @ParameterizedTest
    @CsvSource({
            "READS.FQ,FASTQ",
            "reads.fq.gz,FASTQ_GZ",
            "VARIANTS.VCF.GZ,VCF_GZ",
            "ref.FNA,FASTA",
            "ref.fasta.gz,FASTA_GZ",
            "ref.fa.gz,FASTA_GZ",
            "ref.fna.gz,FASTA_GZ"
    })
    void detectsAliasesAndUppercase(String filename, FileType expected) {
        assertEquals(expected, detector.detect(filename));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"reads.txt", "sample.exe", "noext"})
    void unknownFilenames(String filename) {
        assertEquals(FileType.UNKNOWN, detector.detect(filename));
    }

    @Test
    void typeHelpers() {
        assertTrue(detector.isVcf(FileType.VCF));
        assertTrue(detector.isVcf(FileType.VCF_GZ));
        assertFalse(detector.isVcf(FileType.FASTQ));
        assertTrue(detector.isFasta(FileType.FASTA));
        assertTrue(detector.isFasta(FileType.FASTA_GZ));
        assertFalse(detector.isFasta(FileType.VCF));
        assertFalse(detector.isGzipped(FileType.FASTQ));
        assertFalse(detector.isFastq(FileType.VCF));
    }
}
