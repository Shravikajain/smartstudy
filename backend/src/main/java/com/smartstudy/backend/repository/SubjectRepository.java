package com.smartstudy.backend.repository;

import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByOwner(User owner);
}