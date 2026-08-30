package com.ngs.analytics.fastq;

import com.ngs.analytics.domain.FastqReadEnd;
import com.ngs.analytics.domain.FileAsset;
import com.ngs.analytics.upload.FileTypeDetector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class FastqMateDetector {

    private static final Pattern R1 = Pattern.compile("(_R1|\\.1\\.|_1\\.)", Pattern.CASE_INSENSITIVE);
    private static final Pattern R2 = Pattern.compile("(_R2|\\.2\\.|_2\\.)", Pattern.CASE_INSENSITIVE);

    private final FileTypeDetector fileTypeDetector;

    public FastqMateDetector(FileTypeDetector fileTypeDetector) {
        this.fileTypeDetector = fileTypeDetector;
    }

    public FastqReadEnd detectReadEnd(String filename) {
        String name = filename == null ? "" : filename;
        if (R2.matcher(name).find()) {
            return FastqReadEnd.R2;
        }
        if (R1.matcher(name).find()) {
            return FastqReadEnd.R1;
        }
        return FastqReadEnd.SINGLE;
    }

    public String normalizeBaseName(String filename) {
        String base = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        base = base.replaceAll("\\.(fastq|fq)(\\.gz)?$", "");
        base = base.replaceAll("(_r1|_r2|\\.1|\\.2|_1|_2)$", "");
        return base;
    }

    public Optional<FileAsset> findMate(FileAsset uploaded, List<FileAsset> sampleFiles) {
        if (uploaded.getReadEnd() != FastqReadEnd.R2) {
            return Optional.empty();
        }
        String base = normalizeBaseName(uploaded.getOriginalFilename());
        return sampleFiles.stream()
                .filter(f -> f.getId() != null && !f.getId().equals(uploaded.getId()))
                .filter(f -> fileTypeDetector.isFastq(f.getFileType()))
                .filter(f -> f.getReadEnd() == FastqReadEnd.R1)
                .filter(f -> normalizeBaseName(f.getOriginalFilename()).equals(base))
                .findFirst();
    }
}
