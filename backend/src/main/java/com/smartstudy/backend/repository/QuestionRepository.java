package com.smartstudy.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartstudy.backend.model.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("select q from Question q where q.topic.subject.id = :subjectId")
    List<Question> findByTopicSubjectId(@Param("subjectId") Long subjectId);

    List<Question> findByTopicId(Long topicId);

    List<Question> findByIdIn(List<Long> ids);
}
