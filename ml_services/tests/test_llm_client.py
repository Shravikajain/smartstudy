import json
from types import SimpleNamespace

from app import llm_client
from app.schemas import GenerateRequest


def test_topic_batch_prompt_uses_the_supplied_topic_list():
    request = GenerateRequest(
        subject_name="Science",
        topic_name="Electricity, Light",
        topic_names=["Electricity", "Light"],
        count=2,
    )

    prompt = llm_client._build_prompt(request)

    assert "Electricity, Light" in prompt
    assert "distribute questions across the topics" in prompt


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
