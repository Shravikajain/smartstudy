package com.smartstudy.backend.dto;

import java.util.List;

public class StartQuizRequest {

    private Integer maxQuestions;
    private List<Long> questionIds;

    public Integer getMaxQuestions() { return maxQuestions; }
    public void setMaxQuestions(Integer maxQuestions) { this.maxQuestions = maxQuestions; }
    public List<Long> getQuestionIds() { return questionIds; }
    public void setQuestionIds(List<Long> questionIds) { this.questionIds = questionIds; }
}
