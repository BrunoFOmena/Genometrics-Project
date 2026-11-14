package com.ngs.analytics.mcp;

import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.FastqReadEnd;
import com.ngs.analytics.domain.Project;
import com.ngs.analytics.domain.Sample;
import com.ngs.analytics.domain.UserAccount;
import com.ngs.analytics.fastq.FastqMateDetector;
import com.ngs.analytics.projects.ProjectDtos;
import com.ngs.analytics.projects.ProjectService;
import com.ngs.analytics.upload.UploadService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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
 * upload flow. Disabled unless {@code ngs.mcp.analyze-roots} lists directories; only regular files whose real path
 * (symlinks and {@code ..} resolved) is inside one of them are read.
 */
@Component
public class LocalFileAnalyzer {

    static final String DEFAULT_PROJECT = "Agent analyses";

    private final NgsProperties properties;
    private final ProjectService projectService;
    private final UploadService uploadService;
    private final FastqMateDetector mateDetector;

    public LocalFileAnalyzer(NgsProperties properties, ProjectService projectService, UploadService uploadService,
                             FastqMateDetector mateDetector) {
        this.properties = properties;
        this.projectService = projectService;
        this.uploadService = uploadService;
        this.mateDetector = mateDetector;
    }

    public boolean enabled() {
        return !properties.getMcp().getAnalyzeRoots().isEmpty();
    }

    public Map<String, Object> analyze(List<String> paths, String sampleName, UUID projectId, UserAccount user) {
        if (!enabled()) {
            throw new IllegalArgumentException(
                    "analyze_file is disabled. Set NGS_MCP_ANALYZE_ROOTS to the directories it may read.");
        }
        if (paths == null || paths.isEmpty() || paths.size() > 2) {
            throw new IllegalArgumentException("Pass one file, or two FASTQ files (R1 and R2) for paired-end.");
        }
        List<Path> files = new ArrayList<>();
        for (String raw : paths) {
            files.add(checked(raw));
        }
        // R2 goes last so the upload flow finds its R1 mate and enqueues the paired analysis.
        files.sort(Comparator.comparing(f -> mateDetector.detectReadEnd(f.getFileName().toString()) == FastqReadEnd.R2));

        Project project = projectId != null ? projectService.getOwned(projectId, user) : defaultProject(user);
        String name = sampleName == null || sampleName.isBlank() ? files.get(0).getFileName().toString() : sampleName.trim();
        Sample sample = projectService.createSample(project.getId(), user, new ProjectDtos.CreateSampleRequest(name, null, null));

        List<Map<String, Object>> analyses = new ArrayList<>();
        for (Path file : files) {
            var result = uploadService.upload(sample.getId(), user, new LocalFile(file));
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

    /** Real path of an absolute, regular, readable file inside an allowed root. */
    Path checked(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty path");
        }
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Path must be absolute: " + raw);
        }
        Path real;
        try {
            real = path.toRealPath();
        } catch (NoSuchFileException ex) {
            throw new IllegalArgumentException("File not found: " + raw);
        } catch (IOException ex) {
            throw new IllegalArgumentException("File not readable: " + raw);
        }
        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(real)) {
            throw new IllegalArgumentException("Not a readable regular file: " + raw);
        }
        boolean allowed = properties.getMcp().getAnalyzeRoots().stream()
                .map(LocalFileAnalyzer::realRoot)
                .filter(Objects::nonNull)
                .anyMatch(real::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("Path is outside the allowed directories (NGS_MCP_ANALYZE_ROOTS): " + raw);
        }
        return real;
    }

    private static Path realRoot(String root) {
        try {
            return Path.of(root.trim()).toAbsolutePath().toRealPath();
        } catch (IOException | RuntimeException ex) {
            return null; // a missing root allows nothing
        }
    }

    private Project defaultProject(UserAccount user) {
        return projectService.list(user).stream()
                .filter(p -> DEFAULT_PROJECT.equals(p.getName()))
                .findFirst()
                .orElseGet(() -> projectService.create(user, new ProjectDtos.CreateProjectRequest(DEFAULT_PROJECT, null)));
    }

    /** Hands a checked local file to the upload flow, which expects a multipart file. */
    private record LocalFile(Path path) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return path.getFileName().toString(); }
        @Override public String getContentType() { return null; }
        @Override public boolean isEmpty() { return getSize() == 0; }
        @Override public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException ex) {
                throw new IllegalArgumentException("File not readable: " + path);
            }
        }
        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
        @Override public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
        }
        @Override public void transferTo(File dest) throws IOException { Files.copy(path, dest.toPath()); }
    }
}
