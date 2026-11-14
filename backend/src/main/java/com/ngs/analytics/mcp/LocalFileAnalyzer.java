package com.ngs.analytics.mcp;

import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.FastqReadEnd;
import com.ngs.analytics.domain.FileType;
import com.ngs.analytics.domain.Role;
import com.ngs.analytics.domain.Project;
import com.ngs.analytics.domain.Sample;
import com.ngs.analytics.domain.UserAccount;
import com.ngs.analytics.fastq.FastqMateDetector;
import com.ngs.analytics.projects.ProjectDtos;
import com.ngs.analytics.projects.ProjectService;
import com.ngs.analytics.upload.FileTypeDetector;
import com.ngs.analytics.upload.UploadService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Backs the {@code analyze_file} MCP tool: reads FASTQ/VCF files from the local disk and sends them through the normal
 * upload flow. Disabled unless {@code ngs.mcp.analyze-roots} lists absolute directories; only regular files whose real
 * path (symlinks and {@code ..} resolved) is inside one of them are read. Paths outside the roots all get the same
 * message, so the tool cannot be used to probe the rest of the filesystem. With auth on, only ADMIN accounts may use it.
 */
@Component
public class LocalFileAnalyzer {

    static final String DEFAULT_PROJECT = "Agent analyses";
    private static final int MAX_SAMPLE_NAME = 200;

    private final NgsProperties properties;
    private final ProjectService projectService;
    private final UploadService uploadService;
    private final FastqMateDetector mateDetector;
    private final FileTypeDetector fileTypeDetector;

    public LocalFileAnalyzer(NgsProperties properties, ProjectService projectService, UploadService uploadService,
                             FastqMateDetector mateDetector, FileTypeDetector fileTypeDetector) {
        this.properties = properties;
        this.projectService = projectService;
        this.uploadService = uploadService;
        this.mateDetector = mateDetector;
        this.fileTypeDetector = fileTypeDetector;
    }

    /** True when at least one configured root is a usable absolute directory (blank, relative and "/" are ignored). */
    public boolean enabled() {
        return !realRoots().isEmpty();
    }

    public Map<String, Object> analyze(List<String> paths, String sampleName, UUID projectId, UserAccount user) {
        if (!enabled()) {
            throw new IllegalArgumentException(
                    "analyze_file is disabled. Set NGS_MCP_ANALYZE_ROOTS to the directories it may read.");
        }
        if (!properties.getAuth().isDisabled() && user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("analyze_file needs an ADMIN account when auth is enabled.");
        }
        if (paths == null || paths.isEmpty() || paths.size() > 2) {
            throw new IllegalArgumentException("Pass one file, or two FASTQ files (R1 and R2) for paired-end.");
        }
        if (sampleName != null && sampleName.trim().length() > MAX_SAMPLE_NAME) {
            throw new IllegalArgumentException("sampleName must be at most " + MAX_SAMPLE_NAME + " characters.");
        }
        // Validate everything before creating any project, sample or analysis.
        List<Path> files = new ArrayList<>();
        for (String raw : paths) {
            Path file = checked(raw);
            validateContent(file, raw);
            files.add(file);
        }
        if (files.size() == 2) {
            requireMates(files.get(0), files.get(1));
        }
        // R2 goes last so the upload flow finds its R1 mate and enqueues the paired analysis.
        files.sort(Comparator.comparing(f -> mateDetector.detectReadEnd(f.getFileName().toString()) == FastqReadEnd.R2));

        Project project = projectId != null ? projectService.getOwned(projectId, user) : defaultProject(user);
        String name = sampleName == null || sampleName.isBlank() ? files.get(0).getFileName().toString() : sampleName.trim();
        Sample sample = projectService.createSample(project.getId(), user, new ProjectDtos.CreateSampleRequest(name, null, null));

        List<Map<String, Object>> analyses = new ArrayList<>();
        for (Path file : files) {
            UploadService.UploadResult result;
            try {
                result = uploadService.upload(sample.getId(), user, new LocalFile(file, size(file)));
            } catch (RuntimeException ex) {
                if (analyses.isEmpty()) {
                    throw ex;
                }
                throw new IllegalStateException("Analyzed " + analyses + " in sample " + sample.getId()
                        + ", but " + file.getFileName() + " failed: " + ex.getMessage(), ex);
            }
            Map<String, Object> analysis = new LinkedHashMap<>();
            analysis.put("analysisId", result.analysis().getId().toString());
            analysis.put("file", file.getFileName().toString());
            analysis.put("status", result.analysis().getStatus().name());
            analysis.put("paired", result.analysis().getMateFileAsset() != null);
            analyses.add(analysis);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", project.getId().toString());
        out.put("sampleId", sample.getId().toString());
        out.put("analyses", analyses);
        return out;
    }

    /**
     * Real path of an absolute, regular, readable file inside an allowed root. The root check runs on the path as
     * given before the filesystem is touched, so paths outside the roots cannot tell "missing" from "exists".
     */
    Path checked(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty path");
        }
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Path must be absolute: " + raw);
        }
        String notAllowed = "File not found or outside the allowed directories (NGS_MCP_ANALYZE_ROOTS): " + raw;
        Path normalized = path.normalize();
        boolean underRoot = properties.getMcp().getAnalyzeRoots().stream()
                .map(LocalFileAnalyzer::absoluteRoot)
                .filter(Objects::nonNull)
                .anyMatch(normalized::startsWith)
                || realRoots().stream().anyMatch(normalized::startsWith);
        if (!underRoot) {
            throw new IllegalArgumentException(notAllowed);
        }
        Path real;
        try {
            real = path.toRealPath();
        } catch (NoSuchFileException ex) {
            throw new IllegalArgumentException("File not found: " + raw);
        } catch (IOException ex) {
            throw new IllegalArgumentException(notAllowed);
        }
        if (realRoots().stream().noneMatch(real::startsWith)) {
            throw new IllegalArgumentException(notAllowed); // a symlink inside a root pointing outside
        }
        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(real)) {
            throw new IllegalArgumentException("Not a readable regular file: " + raw);
        }
        return real;
    }

    private void validateContent(Path file, String raw) {
        if (fileTypeDetector.detect(file.getFileName().toString()) == FileType.UNKNOWN) {
            throw new IllegalArgumentException("Unsupported file type. Use FASTQ/VCF/FASTA (+ .gz): " + raw);
        }
        long size = size(file);
        if (size == 0) {
            throw new IllegalArgumentException("File is empty: " + raw);
        }
        if (size > properties.getStorage().getMaxUploadBytes()) {
            throw new IllegalArgumentException("File exceeds the upload size limit: " + raw);
        }
    }

    private void requireMates(Path first, Path second) {
        String a = first.getFileName().toString();
        String b = second.getFileName().toString();
        boolean fastq = fileTypeDetector.isFastq(fileTypeDetector.detect(a)) && fileTypeDetector.isFastq(fileTypeDetector.detect(b));
        boolean oneOfEach = (mateDetector.detectReadEnd(a) == FastqReadEnd.R1 && mateDetector.detectReadEnd(b) == FastqReadEnd.R2)
                || (mateDetector.detectReadEnd(a) == FastqReadEnd.R2 && mateDetector.detectReadEnd(b) == FastqReadEnd.R1);
        if (!fastq || !oneOfEach || !mateDetector.normalizeBaseName(a).equals(mateDetector.normalizeBaseName(b))) {
            throw new IllegalArgumentException("Two files must be the R1 and R2 FASTQ of one sample (same name apart from R1/R2).");
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            throw new IllegalArgumentException("File not readable: " + file);
        }
    }

    private List<Path> realRoots() {
        return properties.getMcp().getAnalyzeRoots().stream()
                .map(LocalFileAnalyzer::absoluteRoot)
                .filter(Objects::nonNull)
                .map(root -> {
                    try {
                        Path real = root.toRealPath();
                        return real.getParent() == null || !Files.isDirectory(real) ? null : real;
                    } catch (IOException | RuntimeException ex) {
                        return null; // a missing root allows nothing
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** Configured root as an absolute normalized path; blank, relative and filesystem-root entries are ignored. */
    private static Path absoluteRoot(String root) {
        if (root == null || root.isBlank()) {
            return null;
        }
        Path path = Path.of(root.trim());
        return !path.isAbsolute() || path.normalize().getParent() == null ? null : path.normalize();
    }

    private Project defaultProject(UserAccount user) {
        return projectService.list(user).stream()
                .filter(p -> DEFAULT_PROJECT.equals(p.getName()))
                .findFirst()
                .orElseGet(() -> projectService.create(user, new ProjectDtos.CreateProjectRequest(DEFAULT_PROJECT, null)));
    }

    /**
     * Hands a checked local file to the upload flow, which expects a multipart file. The stream re-checks that the
     * path still resolves to itself and fails if the file grows past the size that was checked.
     */
    private record LocalFile(Path path, long size) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return path.getFileName().toString(); }
        @Override public String getContentType() { return null; }
        @Override public boolean isEmpty() { return size == 0; }
        @Override public long getSize() { return size; }
        @Override public InputStream getInputStream() throws IOException {
            if (!path.toRealPath().equals(path)) {
                throw new IOException("File changed after it was checked: " + path.getFileName());
            }
            return new BoundedInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), size);
        }
        @Override public byte[] getBytes() { throw new UnsupportedOperationException("Use getInputStream"); }
        @Override public void transferTo(File dest) { throw new UnsupportedOperationException("Use getInputStream"); }
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0 && --remaining < 0) {
                throw new IOException("File grew while it was being copied");
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0 && (remaining -= n) < 0) {
                throw new IOException("File grew while it was being copied");
            }
            return n;
        }
    }
}
