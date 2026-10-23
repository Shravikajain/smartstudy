package com.smartstudy.backend.dto;

public class TopicResponse {
    private Long id;
    private String name;
    private String description;
    private Long subjectId;

    public TopicResponse(Long id, String name, String description, Long subjectId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.subjectId = subjectId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getSubjectId() { return subjectId; }
}