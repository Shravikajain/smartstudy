import json
from types import SimpleNamespace

import pytest

from app import llm_client
from app.schemas import GenerateRequest, GradeAnswerRequest, InterviewTurnRequest


def test_topic_batch_prompt_uses_the_supplied_topic_list():
    request = GenerateRequest(
        subject_name="Science",
        topic_name="Electricity, Light",
        topic_names=["Electricity", "Light"],
        count=2,
    )

    prompt = llm_client._build_prompt(request)

    assert "- Electricity\n- Light" in prompt
    assert "distribute questions across the topics" in prompt


def test_topic_names_containing_commas_stay_unambiguous():
    topics = ["graph, shortest path, dijkstra", "sorting, merge sort"]
    request = GenerateRequest(
        subject_name="Algorithms", topic_name=", ".join(topics), topic_names=topics, count=2,
    )

    prompt = llm_client._build_prompt(request)
    schema = llm_client._question_schema(request)

    assert "- graph, shortest path, dijkstra\n- sorting, merge sort" in prompt
    item = schema["properties"]["questions"]["items"]["properties"]
    assert item["topic_name"]["enum"] == topics
    assert item["options"]["minItems"] == item["options"]["maxItems"] == 4
    assert llm_client.QUESTION_SCHEMA["properties"]["questions"]["items"]["properties"][
        "topic_name"] == {"type": "string"}          # the shared template is not mutated


def _fake_client(*contents):
    replies = iter(contents)

    class FakeClient:
        calls = 0

        def chat(self, **_kwargs):
            FakeClient.calls += 1
            return SimpleNamespace(message=SimpleNamespace(content=next(replies)))

    return FakeClient()


def _mcq(prompt, options=("A", "B", "C", "D"), correct_index=0, topic="Electricity"):
    return {
        "prompt": prompt, "topic_name": topic, "question_type": "multiple_choice",
        "options": list(options), "correct_index": correct_index, "reference_answer": None,
        "explanation": "Because.", "difficulty": 3,
    }


def test_generation_shuffles_options_and_keeps_the_correct_answer(monkeypatch):
    options = ["Ohm", "Volt", "Ampere", "Watt"]
    reply = json.dumps({"questions": [_mcq(f"Unit question {i}?", options, 2) for i in range(20)]})
    monkeypatch.setattr(llm_client, "_client", lambda: _fake_client(reply))

    result = llm_client.generate_questions(
        GenerateRequest(subject_name="Science", topic_name="Electricity", count=20))

    assert len(result) == 20
    assert all(q.options[q.correct_index] == "Ampere" for q in result)
    assert len({q.correct_index for q in result}) > 1


def test_generation_drops_duplicate_options_and_repeated_prompts(monkeypatch):
    reply = json.dumps({"questions": [
        _mcq("What is current?"),
        _mcq("what is  CURRENT?"),
        _mcq("Which option repeats?", ("A", "a", "C", "D")),
        _mcq("What is resistance?", topic="electricity"),
    ]})
    monkeypatch.setattr(llm_client, "_client", lambda: _fake_client(reply))

    result = llm_client.generate_questions(
        GenerateRequest(subject_name="Science", topic_name="Electricity", count=4))

    assert [q.prompt for q in result] == ["What is current?", "What is resistance?"]
    assert all(q.topic_name == "Electricity" for q in result)


def test_malformed_model_output_is_retried_once(monkeypatch):
    client = _fake_client("not json", json.dumps({"score": 0.8, "feedback": "Good."}))
    monkeypatch.setattr(llm_client, "_client", lambda: client)

    result = llm_client.grade_answer(GradeAnswerRequest(
        prompt="Q", reference_answer="R", rubric="Rubric", student_answer="x" * 60))

    assert result.score == 0.8
    assert client.calls == 2


def test_repeated_malformed_output_raises_llm_error(monkeypatch):
    client = _fake_client("not json", "still not json")
    monkeypatch.setattr(llm_client, "_client", lambda: client)

    with pytest.raises(llm_client.LlmError):
        llm_client.grade_answer(GradeAnswerRequest(
            prompt="Q", reference_answer="R", rubric="Rubric", student_answer="x" * 60))
    assert client.calls == 2


def _interview(question_number, question_count=3):
    return InterviewTurnRequest(
        target_type="engineering_role", target="Backend Engineer",
        question_number=question_number, question_count=question_count,
        answer=None if question_number == 0 else "A detailed answer about caching.",
    )


def test_final_interview_turn_is_closed_even_if_model_asks_another_question(monkeypatch):
    reply = json.dumps({"question": "One more?", "feedback": "Well done.", "score": 0.7,
                        "complete": False})
    monkeypatch.setattr(llm_client, "_client", lambda: _fake_client(reply))

    result = llm_client.run_interview_turn(_interview(3))

    assert result.question is None and result.complete is True
    assert result.feedback == "Well done."


def test_middle_interview_turn_without_next_question_fails(monkeypatch):
    reply = json.dumps({"question": None, "feedback": "Ok.", "score": 0.5, "complete": True})
    monkeypatch.setattr(llm_client, "_client", lambda: _fake_client(reply))

    with pytest.raises(llm_client.LlmError):
        llm_client.run_interview_turn(_interview(1))


def test_topic_batch_generation_discards_unrecognized_topics(monkeypatch):
    questions = [
        {
            "prompt": "Question on electricity?",
            "topic_name": "Electricity",
            "question_type": "multiple_choice",
            "options": ["A", "B", "C", "D"],
            "correct_index": 0,
            "reference_answer": None,
            "explanation": "Because.",
            "difficulty": 3,
        },
        {
            "prompt": "Question on an unknown topic?",
            "topic_name": "Unknown",
            "question_type": "multiple_choice",
            "options": ["A", "B", "C", "D"],
            "correct_index": 0,
            "reference_answer": None,
            "explanation": "Because.",
            "difficulty": 3,
        },
    ]

    class FakeClient:
        def chat(self, **_kwargs):
            return SimpleNamespace(
                message=SimpleNamespace(content=json.dumps({"questions": questions}))
            )

    monkeypatch.setattr(llm_client, "_client", lambda: FakeClient())
    request = GenerateRequest(
        subject_name="Science",
        topic_name="Electricity, Light",
        topic_names=["Electricity", "Light"],
        count=2,
    )

    result = llm_client.generate_questions(request)

    assert len(result) == 1
    assert result[0].topic_name == "Electricity"


def test_generation_accepts_the_shape_gpt_oss_returns_when_it_ignores_the_schema(monkeypatch):
    # Observed from gpt-oss:120b on Ollama Cloud: a bare list, "question" instead of "prompt",
    # and the correct answer given as option text or a letter instead of correct_index.
    reply = "```json\n" + json.dumps([
        {"question": "Which unit measures current?", "topic_name": "Electricity",
         "question_type": "multiple_choice", "options": ["A) Volt", "B) Ampere", "C) Ohm", "D) Watt"],
         "correct_answer": "Ampere", "explanation": "Current is measured in amperes.", "difficulty": 1},
        {"question": "Which unit measures resistance?", "topic_name": "Electricity",
         "question_type": "multiple_choice", "options": ["Volt", "Ampere", "Ohm", "Watt"],
         "correct_answer": "C", "explanation": "Resistance is measured in ohms.", "difficulty": 1},
    ]) + "\n```"
    monkeypatch.setattr(llm_client, "_client", lambda: _fake_client(reply))

    result = llm_client.generate_questions(
        GenerateRequest(subject_name="Science", topic_name="Electricity", count=2))

    assert [q.prompt for q in result] == ["Which unit measures current?",
                                          "Which unit measures resistance?"]
    assert result[0].options[result[0].correct_index] == "Ampere"
    assert result[1].options[result[1].correct_index] == "Ohm"


def test_subjective_answer_field_is_used_as_reference_answer(monkeypatch):
    reply = json.dumps({"questions": [{
        "question": "Explain memoization.", "topic_name": "Electricity",
        "question_type": "subjective", "options": [], "answer": "Caching subproblem results.",
        "explanation": "Rubric: mention caching.", "difficulty": 2,
    }]})
    monkeypatch.setattr(llm_client, "_client", lambda: _fake_client(reply))

    result = llm_client.generate_questions(GenerateRequest(
        subject_name="Science", topic_name="Electricity", count=1, question_type="subjective"))

    assert result[0].reference_answer == "Caching subproblem results."


def test_json_is_extracted_from_text_around_it():
    assert llm_client._parse_json('Here you go:\n{"score": 0.5, "feedback": "Ok."}\nThanks') == {
        "score": 0.5, "feedback": "Ok."}


def test_ambiguous_answer_text_is_not_guessed():
    assert llm_client._answer_index("Ohm", ["Ohm", "ohm", "Volt", "Watt"]) is None
    assert llm_client._answer_index(True, ["A", "B", "C", "D"]) is None
