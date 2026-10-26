package com.smartstudy.backend.service;

import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.SubjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    public Subject createSubject(String name, User owner) {
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("Enter a subject name (up to 255 characters)");
        }
        Subject subject = new Subject(name.trim(), owner);
        return subjectRepository.save(subject);
    }

    public List<Subject> getSubjectsForUser(User owner) {
        return subjectRepository.findByOwner(owner);
    }

    public Subject getSubjectByIdForUser(Long id, User owner) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        if (!subject.getOwner().getId().equals(owner.getId())) {
            throw new SecurityException("Not your subject");
        }
        return subject;
    }
}