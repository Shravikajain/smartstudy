import re

# Matches the start of a question: "1.", "2)", "Q3.", "Q.4)" at the start of a line.
QUESTION_START = re.compile(r"(?m)^[ \t]*(?:Q\.?[ \t]*)?\d{1,2}[ \t]*[.)][ \t]+(?=\S)")
PAGE_NOISE = re.compile(r"(?mi)^[ \t]*(?:page[ \t]*)?\d+(?:[ \t]*of[ \t]*\d+)?[ \t]*$")
MIN_QUESTION_CHARS = 20


def split_questions(text: str) -> list[str]:
    text = PAGE_NOISE.sub("", text)
    matches = list(QUESTION_START.finditer(text))

    if len(matches) >= 2:
        chunks = []
        for i, m in enumerate(matches):
            end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
            chunks.append(text[m.end():end])
    else:
        # Fallback: no numbering detected, so treat blank-line-separated blocks as questions.
        chunks = re.split(r"\n\s*\n", text)

    questions = []
    for chunk in chunks:
        cleaned = re.sub(r"\s+", " ", chunk).strip()
        if len(cleaned) >= MIN_QUESTION_CHARS:
            questions.append(cleaned)
    return questions