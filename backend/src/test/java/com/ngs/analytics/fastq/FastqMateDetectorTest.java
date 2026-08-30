package com.ngs.analytics.fastq;

import com.ngs.analytics.domain.FastqReadEnd;
import com.ngs.analytics.domain.FileAsset;
import com.ngs.analytics.domain.FileType;
import com.ngs.analytics.upload.FileTypeDetector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FastqMateDetectorTest {

    private final FastqMateDetector detector = new FastqMateDetector(new FileTypeDetector());

    @Test
    void detectsR1R2AndSingle() {
        assertEquals(FastqReadEnd.R1, detector.detectReadEnd("sample_R1.fastq"));
        assertEquals(FastqReadEnd.R2, detector.detectReadEnd("sample_R2.fastq.gz"));
        assertEquals(FastqReadEnd.R1, detector.detectReadEnd("sample.1.fastq"));
        assertEquals(FastqReadEnd.R2, detector.detectReadEnd("sample.2.fq"));
        assertEquals(FastqReadEnd.SINGLE, detector.detectReadEnd("sample.fastq"));
    }

    @Test
    void normalizesBaseName() {
        assertEquals("sample", detector.normalizeBaseName("sample_R1.fastq"));
        assertEquals("sample", detector.normalizeBaseName("sample_R2.fq.gz"));
        assertEquals("sample", detector.normalizeBaseName("sample.1.fastq"));
    }

    @Test
    void findsMateForR2() {
        FileAsset r1 = asset("sample_R1.fastq", FastqReadEnd.R1);
        FileAsset r2 = asset("sample_R2.fastq", FastqReadEnd.R2);
        FileAsset other = asset("other_R1.fastq", FastqReadEnd.R1);
        assertTrue(detector.findMate(r2, List.of(r1, r2, other)).map(FileAsset::getId).filter(id -> id.equals(r1.getId())).isPresent());
        assertTrue(detector.findMate(r1, List.of(r1, r2)).isEmpty());
    }

    private static FileAsset asset(String name, FastqReadEnd end) {
        FileAsset f = new FileAsset();
        f.setId(UUID.randomUUID());
        f.setOriginalFilename(name);
        f.setFileType(FileType.FASTQ);
        f.setReadEnd(end);
        return f;
    }
}
