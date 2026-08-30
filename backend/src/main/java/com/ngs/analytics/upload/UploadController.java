package com.ngs.analytics.upload;

import com.ngs.analytics.auth.SecurityUtils;
import com.ngs.analytics.domain.Analysis;
import com.ngs.analytics.domain.FileAsset;
import com.ngs.analytics.projects.ProjectDtos;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/samples")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/{sampleId}/files")
    public Map<String, Object> upload(@PathVariable UUID sampleId, @RequestParam("file") MultipartFile file) {
        UploadService.UploadResult result = uploadService.upload(sampleId, SecurityUtils.currentUser(), file);
        return Map.of(
                "file", toFile(result.fileAsset()),
                "analysis", toAnalysis(result.analysis())
        );
    }

    @GetMapping("/{sampleId}/files")
    public List<ProjectDtos.FileAssetResponse> files(@PathVariable UUID sampleId) {
        return uploadService.listFiles(sampleId, SecurityUtils.currentUser()).stream().map(this::toFile).toList();
    }

    @GetMapping("/{sampleId}/analyses")
    public List<ProjectDtos.AnalysisResponse> analyses(@PathVariable UUID sampleId) {
        return uploadService.listAnalyses(sampleId, SecurityUtils.currentUser()).stream().map(this::toAnalysis).toList();
    }

    private ProjectDtos.FileAssetResponse toFile(FileAsset f) {
        return new ProjectDtos.FileAssetResponse(
                f.getId().toString(),
                f.getOriginalFilename(),
                f.getFileType().name(),
                f.getSizeBytes(),
                f.getUploadedAt()
        );
    }

    private ProjectDtos.AnalysisResponse toAnalysis(Analysis a) {
        return new ProjectDtos.AnalysisResponse(
                a.getId().toString(),
                a.getSample().getId().toString(),
                a.getFileAsset().getId().toString(),
                a.getMateFileAsset() != null ? a.getMateFileAsset().getId().toString() : null,
                a.getStatus().name(),
                a.getEngine(),
                a.getErrorMessage(),
                a.getCreatedAt(),
                a.getStartedAt(),
                a.getFinishedAt()
        );
    }
}
