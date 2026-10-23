package com.smartstudy.backend.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstudy.backend.dto.TopicRequest;
import com.smartstudy.backend.dto.TopicResponse;
import com.smartstudy.backend.model.Topic;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.TopicService;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api")
public class TopicController {

    private final TopicService topicService;
    private final UserService userService;

    public TopicController(TopicService topicService, UserService userService) {
        this.topicService = topicService;
        this.userService = userService;
    }

    @PostMapping("/subjects/{subjectId}/topics")
    public ResponseEntity<TopicResponse> create(@PathVariable Long subjectId,
                                                  @RequestBody TopicRequest request,
                                                  Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        Topic topic = topicService.createTopic(subjectId, request.getName(), request.getDescription(), user);
        return ResponseEntity.ok(toResponse(topic));
    }

    @GetMapping("/subjects/{subjectId}/topics")
    public ResponseEntity<List<TopicResponse>> list(@PathVariable Long subjectId,
                                                      Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        List<TopicResponse> topics = topicService.getTopicsForSubject(subjectId, user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(topics);
    }

    @PutMapping("/topics/{id}")
    public ResponseEntity<TopicResponse> update(@PathVariable Long id,
                                                  @RequestBody TopicRequest request,
                                                  Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        Topic topic = topicService.updateTopic(id, request.getName(), request.getDescription(), user);
        return ResponseEntity.ok(toResponse(topic));
    }

    @DeleteMapping("/topics/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        topicService.deleteTopic(id, user);
        return ResponseEntity.noContent().build();
    }

    private TopicResponse toResponse(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getName(),
                topic.getDescription(), topic.getSubject().getId());
    }
}