package com.smartstudy.backend.dto;

import java.util.List;

public class StartQuizRequest {

    private Integer maxQuestions;
    private List<Long> questionIds;
    private String questionType;

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public Integer getMaxQuestions() { return maxQuestions; }
    public void setMaxQuestions(Integer maxQuestions) { this.maxQuestions = maxQuestions; }
    public List<Long> getQuestionIds() { return questionIds; }
    public void setQuestionIds(List<Long> questionIds) { this.questionIds = questionIds; }
}
