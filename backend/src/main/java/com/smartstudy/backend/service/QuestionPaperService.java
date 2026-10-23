package com.smartstudy.backend.service;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartstudy.backend.model.QuestionPaper;
import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.QuestionPaperRepository;

@Service
public class QuestionPaperService {

    private final QuestionPaperRepository paperRepository;
    private final SubjectService subjectService;
    private final FileStorageService storageService;
    private final PdfTextExtractor textExtractor;

    public QuestionPaperService(QuestionPaperRepository paperRepository,
                                SubjectService subjectService,
                                FileStorageService storageService,
                                PdfTextExtractor textExtractor) {
        this.paperRepository = paperRepository;
        this.subjectService = subjectService;
        this.storageService = storageService;
        this.textExtractor = textExtractor;
    }

    public QuestionPaper upload(Long subjectId, MultipartFile file, Integer examYear, User owner) {
        Subject subject = subjectService.getSubjectByIdForUser(subjectId, owner);

        String storedName = null;
        try {
            storedName = storageService.store(file);
            String text = textExtractor.extractText(storageService.resolve(storedName));
            QuestionPaper paper = new QuestionPaper(
                    file.getOriginalFilename(), storedName, examYear, text, subject);
            return paperRepository.save(paper);
        } catch (IOException e) {
            if (storedName != null) {
                storageService.deleteQuietly(storedName);
            }
            throw new IllegalArgumentException("Could not read this file as a PDF");
        }
    }

    public List<QuestionPaper> listForSubject(Long subjectId, User owner) {
        subjectService.getSubjectByIdForUser(subjectId, owner);
        return paperRepository.findBySubjectId(subjectId);
    }

    public QuestionPaper getForUser(Long paperId, User owner) {
        QuestionPaper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found"));
        if (!paper.getSubject().getOwner().getId().equals(owner.getId())) {
            throw new SecurityException("Not your paper");
        }
        return paper;
    }

    public void delete(Long paperId, User owner) {
        QuestionPaper paper = getForUser(paperId, owner);
        storageService.deleteQuietly(paper.getStoredFilename());
        paperRepository.delete(paper);
    }
}