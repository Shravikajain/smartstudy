from app.question_splitter import split_questions


def test_splits_numbered_questions():
    text = (
        "1. Explain the N-Queens problem using backtracking.\n"
        "2. Write the Floyd-Warshall algorithm and analyse its complexity.\n"
    )
    questions = split_questions(text)
    assert len(questions) == 2
    assert questions[0].startswith("Explain the N-Queens")


def test_removes_page_number_noise():
    text = (
        "1. Explain the Master theorem with an example recurrence.\n"
        "Page 2 of 5\n"
        "2. Define NP-complete problems and the Cook-Levin theorem.\n"
    )
    questions = split_questions(text)
    assert len(questions) == 2
    assert all("Page" not in q for q in questions)


def test_falls_back_to_blank_line_blocks_when_unnumbered():
    text = "Explain the master theorem in detail.\n\nDiscuss the knapsack problem using dynamic programming."
    assert len(split_questions(text)) == 2


def test_drops_fragments_that_are_too_short():
    text = "1. Hi\n2. Explain the N-Queens problem using backtracking.\n"
    assert len(split_questions(text)) == 1