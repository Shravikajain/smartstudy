package com.smartstudy.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.Topic;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.TopicRepository;

@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final SubjectService subjectService;

    public TopicService(TopicRepository topicRepository, SubjectService subjectService) {
        this.topicRepository = topicRepository;
        this.subjectService = subjectService;
    }

    public Topic createTopic(Long subjectId, String name, String description, User owner) {
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("Enter a topic name (up to 255 characters)");
        }
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("Topic description must be at most 2,000 characters");
        }
        Subject subject = subjectService.getSubjectByIdForUser(subjectId, owner);
        Topic topic = new Topic(name.trim(), description, subject);
        return topicRepository.save(topic);
    }

    public List<Topic> getTopicsForSubject(Long subjectId, User owner) {
        subjectService.getSubjectByIdForUser(subjectId, owner);
        return topicRepository.findBySubjectId(subjectId);
    }

    public Topic updateTopic(Long topicId, String name, String description, User owner) {
        Topic topic = getTopicForUser(topicId, owner);
        topic.setName(name);
        topic.setDescription(description);
        return topicRepository.save(topic);
    }

    public void deleteTopic(Long topicId, User owner) {
        Topic topic = getTopicForUser(topicId, owner);
        topicRepository.delete(topic);
    }

    private Topic getTopicForUser(Long topicId, User owner) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found"));
        if (!topic.getSubject().getOwner().getId().equals(owner.getId())) {
            throw new SecurityException("Not your topic");
        }
        return topic;
    }
}