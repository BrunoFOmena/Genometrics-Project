package com.ngs.analytics.upload;

import com.ngs.analytics.analytics.AnalysisService;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.*;
import com.ngs.analytics.fastq.FastqMateDetector;
import com.ngs.analytics.projects.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class UploadService {

    private final ProjectService projectService;
    private final FileAssetRepository fileAssetRepository;
    private final AnalysisRepository analysisRepository;
    private final StorageService storageService;
    private final FileTypeDetector fileTypeDetector;
    private final AnalysisService analysisService;
    private final FastqMateDetector fastqMateDetector;

    public UploadService(
            ProjectService projectService,
            FileAssetRepository fileAssetRepository,
            AnalysisRepository analysisRepository,
            StorageService storageService,
            FileTypeDetector fileTypeDetector,
            AnalysisService analysisService,
            FastqMateDetector fastqMateDetector
    ) {
        this.projectService = projectService;
        this.fileAssetRepository = fileAssetRepository;
        this.analysisRepository = analysisRepository;
        this.storageService = storageService;
        this.fileTypeDetector = fileTypeDetector;
        this.analysisService = analysisService;
        this.fastqMateDetector = fastqMateDetector;
    }

    @Transactional
    public UploadResult upload(UUID sampleId, UserAccount owner, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is required");
        }
        Sample sample = projectService.getSampleOwned(sampleId, owner);
        FileType type = fileTypeDetector.detect(file.getOriginalFilename());
        if (type == FileType.UNKNOWN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported file type. Use FASTQ/VCF/FASTA (+ .gz)");
        }

        StorageService.StoredFile stored = storageService.store(sampleId, file);
        FileAsset asset = new FileAsset();
        asset.setSample(sample);
        asset.setOriginalFilename(file.getOriginalFilename());
        asset.setFileType(type);
        asset.setStoragePath(stored.path());
        asset.setSizeBytes(stored.sizeBytes());
        if (fileTypeDetector.isFastq(type)) {
            asset.setReadEnd(fastqMateDetector.detectReadEnd(file.getOriginalFilename()));
        }
        fileAssetRepository.save(asset);

        if (fileTypeDetector.isFasta(type)) {
            projectService.updateFastaMetadata(sampleId, owner, file.getOriginalFilename(), stored.path());
            Analysis analysis = analysisService.enqueue(sample, asset);
            return new UploadResult(asset, analysis);
        }

        if (fileTypeDetector.isFastq(type) && asset.getReadEnd() == FastqReadEnd.R2) {
            var mates = fileAssetRepository.findBySampleIdOrderByUploadedAtDesc(sampleId);
            var mate = fastqMateDetector.findMate(asset, mates);
            if (mate.isPresent()) {
                Analysis analysis = analysisService.enqueuePaired(sample, mate.get(), asset);
                return new UploadResult(asset, analysis);
            }
        }

        Analysis analysis = analysisService.enqueue(sample, asset);
        return new UploadResult(asset, analysis);
    }

    @Transactional(readOnly = true)
    public List<FileAsset> listFiles(UUID sampleId, UserAccount owner) {
        projectService.getSampleOwned(sampleId, owner);
        return fileAssetRepository.findBySampleIdOrderByUploadedAtDesc(sampleId);
    }

    @Transactional(readOnly = true)
    public List<Analysis> listAnalyses(UUID sampleId, UserAccount owner) {
        projectService.getSampleOwned(sampleId, owner);
        return analysisRepository.findBySampleIdOrderByCreatedAtDesc(sampleId);
    }

    public record UploadResult(FileAsset fileAsset, Analysis analysis) {
    }
}
