package com.ngs.analytics.mcp;

import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.Analysis;
import com.ngs.analytics.domain.AnalysisStatus;
import com.ngs.analytics.domain.Project;
import com.ngs.analytics.domain.Sample;
import com.ngs.analytics.domain.UserAccount;
import com.ngs.analytics.fastq.FastqMateDetector;
import com.ngs.analytics.projects.ProjectService;
import com.ngs.analytics.upload.FileTypeDetector;
import com.ngs.analytics.upload.UploadService;
import com.ngs.analytics.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private final LocalFileAnalyzer analyzer = new LocalFileAnalyzer(properties, projects, uploads,
            new FastqMateDetector(new FileTypeDetector()), new FileTypeDetector());

    @BeforeEach
    void setUp() throws Exception {
        root = Files.createDirectories(tmp.resolve("allowed"));
        outside = Files.createDirectories(tmp.resolve("outside"));
        properties.getMcp().setAnalyzeRoots(List.of(root.toString()));
        properties.getAuth().setDisabled(true); // local default: single user, auth off
    }

    @Test
    void acceptsARegularFileInsideTheRoot() throws Exception {
        Path file = Files.writeString(root.resolve("a.fastq"), "@r\nACGT\n+\nIIII\n");

        assertThat(analyzer.checked(file.toString())).isEqualTo(file.toRealPath());
    }

    @Test
    void rejectsFilesOutsideTheRoot() throws Exception {
        Path file = Files.writeString(outside.resolve("secret.vcf"), "x");

        assertThatThrownBy(() -> analyzer.checked(file.toString())).hasMessageContaining("File not found or outside the allowed directories");
    }

    @Test
    void rejectsDotDotTraversal() throws Exception {
        Files.writeString(outside.resolve("secret.vcf"), "x");

        assertThatThrownBy(() -> analyzer.checked(root.resolve("../outside/secret.vcf").toString()))
                .hasMessageContaining("File not found or outside the allowed directories");
    }

    @Test
    void rejectsSymlinksThatEscapeTheRoot() throws Exception {
        Path secret = Files.writeString(outside.resolve("secret.vcf"), "x");
        Path link = Files.createSymbolicLink(root.resolve("link.vcf"), secret);

        assertThatThrownBy(() -> analyzer.checked(link.toString())).hasMessageContaining("File not found or outside the allowed directories");
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

        assertThatThrownBy(() -> analyzer.checked(file.toString())).hasMessageContaining("File not found or outside the allowed directories");
    }

    @Test
    void pathsOutsideTheRootsGetTheSameMessageWhetherOrNotTheyExist() throws Exception {
        Files.writeString(outside.resolve("exists.vcf"), "x");
        String existing = messageOf(outside.resolve("exists.vcf").toString()).replace("exists.vcf", "X");
        String missing = messageOf(outside.resolve("missing.vcf").toString()).replace("missing.vcf", "X");
        String directory = messageOf(outside + "/X");

        assertThat(existing).isEqualTo(missing).isEqualTo(directory);
    }

    private String messageOf(String path) {
        try {
            analyzer.checked(path);
            return "accepted";
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    @Test
    void ignoresBlankRelativeAndFilesystemRoots() {
        properties.getMcp().setAnalyzeRoots(List.of("", "  ", "data/runs", "/"));

        assertThat(analyzer.enabled()).isFalse();
    }

    @Test
    void rejectsUnsupportedEmptyOrUnpairedFilesBeforeCreatingAnything() throws Exception {
        Path text = Files.writeString(root.resolve("notes.txt"), "x");
        Path empty = Files.writeString(root.resolve("empty.vcf"), "");
        Path r1 = Files.writeString(root.resolve("a_R1.fastq"), "@r\nA\n+\nI\n");
        Path otherR1 = Files.writeString(root.resolve("b_R1.fastq"), "@r\nA\n+\nI\n");
        Path otherR2 = Files.writeString(root.resolve("b_R2.fastq"), "@r\nA\n+\nI\n");
        Path vcf = Files.writeString(root.resolve("v.vcf"), "x");
        UserAccount user = new UserAccount();

        assertThatThrownBy(() -> analyzer.analyze(List.of(text.toString()), null, null, user)).hasMessageContaining("Unsupported file type");
        assertThatThrownBy(() -> analyzer.analyze(List.of(empty.toString()), null, null, user)).hasMessageContaining("empty");
        assertThatThrownBy(() -> analyzer.analyze(List.of(r1.toString(), otherR1.toString()), null, null, user)).hasMessageContaining("R1 and R2");
        assertThatThrownBy(() -> analyzer.analyze(List.of(r1.toString(), otherR2.toString()), null, null, user)).hasMessageContaining("R1 and R2");
        assertThatThrownBy(() -> analyzer.analyze(List.of(r1.toString(), vcf.toString()), null, null, user)).hasMessageContaining("R1 and R2");
        verify(projects, never()).createSample(any(), any(), any());
        verify(projects, never()).create(any(), any());
    }

    @Test
    void rejectsTooLongSampleNames() throws Exception {
        Path vcf = Files.writeString(root.resolve("v.vcf"), "x");

        assertThatThrownBy(() -> analyzer.analyze(List.of(vcf.toString()), "x".repeat(201), null, new UserAccount()))
                .hasMessageContaining("at most 200");
    }

    @Test
    void needsAnAdminWhenAuthIsOn() throws Exception {
        Path vcf = Files.writeString(root.resolve("v.vcf"), "x");
        properties.getAuth().setDisabled(false);
        UserAccount researcher = new UserAccount();
        researcher.setRole(Role.RESEARCHER);

        assertThatThrownBy(() -> analyzer.analyze(List.of(vcf.toString()), null, null, researcher)).hasMessageContaining("ADMIN");
    }

    @Test
    void failsWhenTheFileGrowsDuringTheCopy() throws Exception {
        Path vcf = Files.writeString(root.resolve("v.vcf"), "#header\n");
        UserAccount user = devUserWithSample();
        when(uploads.upload(any(), eq(user), any())).thenAnswer(inv -> {
            MultipartFile file = inv.getArgument(2);
            Files.writeString(vcf, "more data appended by a writer\n", StandardOpenOption.APPEND);
            try (var in = file.getInputStream()) {
                assertThatThrownBy(in::readAllBytes).isInstanceOf(IOException.class).hasMessageContaining("grew");
            }
            Analysis analysis = mock(Analysis.class);
            when(analysis.getId()).thenReturn(UUID.randomUUID());
            when(analysis.getStatus()).thenReturn(AnalysisStatus.QUEUED);
            return new UploadService.UploadResult(null, analysis);
        });

        analyzer.analyze(List.of(vcf.toString()), null, null, user);

        verify(uploads).upload(any(), eq(user), any());
    }

    private UserAccount devUserWithSample() {
        UserAccount user = new UserAccount();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(LocalFileAnalyzer.DEFAULT_PROJECT);
        Sample sample = new Sample();
        sample.setId(UUID.randomUUID());
        when(projects.list(user)).thenReturn(List.of(project));
        when(projects.createSample(eq(project.getId()), eq(user), any())).thenReturn(sample);
        return user;
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
