package com.ngs.analytics.mcp;

import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.Analysis;
import com.ngs.analytics.domain.AnalysisStatus;
import com.ngs.analytics.domain.Project;
import com.ngs.analytics.domain.Sample;
import com.ngs.analytics.domain.UserAccount;
import com.ngs.analytics.fastq.FastqMateDetector;
import com.ngs.analytics.projects.ProjectService;
import com.ngs.analytics.upload.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalFileAnalyzerTest {

    @TempDir Path tmp;
    private Path root;
    private Path outside;

    private final NgsProperties properties = new NgsProperties();
    private final ProjectService projects = mock(ProjectService.class);
    private final UploadService uploads = mock(UploadService.class);
    private final LocalFileAnalyzer analyzer = new LocalFileAnalyzer(properties, projects, uploads, new FastqMateDetector(new com.ngs.analytics.upload.FileTypeDetector()));

    @BeforeEach
    void setUp() throws Exception {
        root = Files.createDirectories(tmp.resolve("allowed"));
        outside = Files.createDirectories(tmp.resolve("outside"));
        properties.getMcp().setAnalyzeRoots(List.of(root.toString()));
    }

    @Test
    void acceptsARegularFileInsideTheRoot() throws Exception {
        Path file = Files.writeString(root.resolve("a.fastq"), "@r\nACGT\n+\nIIII\n");

        assertThat(analyzer.checked(file.toString())).isEqualTo(file.toRealPath());
    }

    @Test
    void rejectsFilesOutsideTheRoot() throws Exception {
        Path file = Files.writeString(outside.resolve("secret.vcf"), "x");

        assertThatThrownBy(() -> analyzer.checked(file.toString())).hasMessageContaining("outside the allowed directories");
    }

    @Test
    void rejectsDotDotTraversal() throws Exception {
        Files.writeString(outside.resolve("secret.vcf"), "x");

        assertThatThrownBy(() -> analyzer.checked(root.resolve("../outside/secret.vcf").toString()))
                .hasMessageContaining("outside the allowed directories");
    }

    @Test
    void rejectsSymlinksThatEscapeTheRoot() throws Exception {
        Path secret = Files.writeString(outside.resolve("secret.vcf"), "x");
        Path link = Files.createSymbolicLink(root.resolve("link.vcf"), secret);

        assertThatThrownBy(() -> analyzer.checked(link.toString())).hasMessageContaining("outside the allowed directories");
    }

    @Test
    void rejectsRelativePathsDirectoriesAndMissingFiles() {
        assertThatThrownBy(() -> analyzer.checked("datasets/sample.fastq")).hasMessageContaining("absolute");
        assertThatThrownBy(() -> analyzer.checked(root.toString())).hasMessageContaining("regular file");
        assertThatThrownBy(() -> analyzer.checked(root.resolve("none.fastq").toString())).hasMessageContaining("not found");
        assertThatThrownBy(() -> analyzer.checked(" ")).hasMessageContaining("Empty path");
    }

    @Test
    void aMissingRootAllowsNothing() throws Exception {
        Path file = Files.writeString(root.resolve("a.vcf"), "x");
        properties.getMcp().setAnalyzeRoots(List.of(tmp.resolve("does-not-exist").toString()));

        assertThatThrownBy(() -> analyzer.checked(file.toString())).hasMessageContaining("outside the allowed directories");
    }

    @Test
    void isDisabledWithoutRoots() {
        properties.getMcp().setAnalyzeRoots(List.of());

        assertThat(analyzer.enabled()).isFalse();
        assertThatThrownBy(() -> analyzer.analyze(List.of("/x.vcf"), null, null, new UserAccount()))
                .hasMessageContaining("NGS_MCP_ANALYZE_ROOTS");
    }

    @Test
    void rejectsMoreThanTwoFiles() {
        assertThatThrownBy(() -> analyzer.analyze(List.of("/a", "/b", "/c"), null, null, new UserAccount()))
                .hasMessageContaining("two FASTQ files");
    }

    @Test
    void uploadsR1BeforeR2AndReportsEachAnalysis() throws Exception {
        Path r2 = Files.writeString(root.resolve("s_R2.fastq"), "@r\nACGT\n+\nIIII\n");
        Path r1 = Files.writeString(root.resolve("s_R1.fastq"), "@r\nACGT\n+\nIIII\n");
        UserAccount user = new UserAccount();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(LocalFileAnalyzer.DEFAULT_PROJECT);
        Sample sample = new Sample();
        sample.setId(UUID.randomUUID());
        when(projects.list(user)).thenReturn(List.of(project));
        when(projects.createSample(eq(project.getId()), eq(user), any())).thenReturn(sample);
        when(uploads.upload(eq(sample.getId()), eq(user), any())).thenAnswer(inv -> {
            Analysis analysis = mock(Analysis.class);
            when(analysis.getId()).thenReturn(UUID.randomUUID());
            when(analysis.getStatus()).thenReturn(AnalysisStatus.QUEUED);
            return new UploadService.UploadResult(null, analysis);
        });

        Map<String, Object> result = analyzer.analyze(List.of(r2.toString(), r1.toString()), "S1", null, user);

        ArgumentCaptor<MultipartFile> files = ArgumentCaptor.forClass(MultipartFile.class);
        verify(uploads, times(2)).upload(eq(sample.getId()), eq(user), files.capture());
        assertThat(files.getAllValues()).extracting(MultipartFile::getOriginalFilename)
                .containsExactly("s_R1.fastq", "s_R2.fastq");
        assertThat(files.getAllValues().get(0).getSize()).isPositive();
        assertThat(result).containsEntry("projectId", project.getId().toString())
                .containsEntry("sampleId", sample.getId().toString());
        assertThat((List<?>) result.get("analyses")).hasSize(2);
    }
}
