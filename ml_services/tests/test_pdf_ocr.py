from app import pdf_ocr


class FakeImage:
    def __init__(self):
        self.closed = False

    def close(self):
        self.closed = True


def test_extract_pdf_text_processes_pages_in_small_batches(monkeypatch):
    batches = []
    images = []

    def convert(pdf_bytes, **kwargs):
        batches.append((kwargs["first_page"], kwargs["last_page"]))
        assert kwargs["dpi"] == pdf_ocr.OCR_DPI
        batch_images = [FakeImage() for _ in range(kwargs["last_page"] - kwargs["first_page"] + 1)]
        images.extend(batch_images)
        return batch_images

    monkeypatch.setattr(pdf_ocr, "pdfinfo_from_bytes", lambda *_args, **_kwargs: {"Pages": 5})
    monkeypatch.setattr(pdf_ocr, "convert_from_bytes", convert)
    monkeypatch.setattr(pdf_ocr.pytesseract, "image_to_string", lambda *_args, **_kwargs: "Question text. " * 10)

    text, page_count = pdf_ocr.extract_pdf_text(b"%PDF-valid")

    assert page_count == 5
    assert len(text) >= 100
    assert batches == [(1, 4), (5, 5)]
    assert all(image.closed for image in images)


def test_extract_pdf_text_rejects_documents_over_page_limit(monkeypatch):
    monkeypatch.setattr(
        pdf_ocr,
        "pdfinfo_from_bytes",
        lambda *_args, **_kwargs: {"Pages": pdf_ocr.MAX_OCR_PAGES + 1},
    )

    try:
        pdf_ocr.extract_pdf_text(b"%PDF-valid")
    except pdf_ocr.PdfOcrError as error:
        assert "supports up to" in str(error)
    else:
        raise AssertionError("Expected oversized PDF rejection")


def test_extract_pdf_text_rejects_non_pdf_data():
    try:
        pdf_ocr.extract_pdf_text(b"not a PDF")
    except pdf_ocr.PdfOcrError as error:
        assert "valid PDF" in str(error)
    else:
        raise AssertionError("Expected invalid PDF rejection")
