from concurrent.futures import ThreadPoolExecutor
from io import BytesIO

import pytesseract
from pdf2image import convert_from_bytes, pdfinfo_from_bytes
from pdf2image.exceptions import (
    PDFInfoNotInstalledError,
    PDFPageCountError,
    PDFPopplerTimeoutError,
    PDFSyntaxError,
)
from pytesseract import TesseractError, TesseractNotFoundError

MAX_PDF_BYTES = 20 * 1024 * 1024
MAX_OCR_PAGES = 120
MAX_OCR_TEXT_LENGTH = 500_000
OCR_BATCH_SIZE = 4
OCR_DPI = 120


class PdfOcrError(ValueError):
    pass


def _recognize_image(image) -> str:
    return pytesseract.image_to_string(
        image,
        lang="eng",
        config="--psm 6",
        timeout=45,
    )


def extract_pdf_text(pdf_bytes: bytes) -> tuple[str, int]:
    if not pdf_bytes.startswith(b"%PDF-"):
        raise PdfOcrError("The uploaded file is not a valid PDF.")
    if len(pdf_bytes) > MAX_PDF_BYTES:
        raise PdfOcrError("The PDF is too large for scanned-page OCR (20 MB maximum).")

    try:
        page_count = int(pdfinfo_from_bytes(pdf_bytes, timeout=30)["Pages"])
    except (PDFInfoNotInstalledError, PDFPageCountError, PDFPopplerTimeoutError,
            PDFSyntaxError, KeyError, ValueError) as error:
        raise PdfOcrError("Could not inspect this PDF. Check that it is not damaged or password-protected.") from error

    if page_count < 1:
        raise PdfOcrError("The PDF contains no pages.")
    if page_count > MAX_OCR_PAGES:
        raise PdfOcrError(
            f"This PDF has {page_count} pages; scanned-PDF OCR supports up to {MAX_OCR_PAGES} pages."
        )

    extracted_pages = []
    total_text_length = 0
    try:
        with ThreadPoolExecutor(max_workers=OCR_BATCH_SIZE) as executor:
            for first_page in range(1, page_count + 1, OCR_BATCH_SIZE):
                last_page = min(first_page + OCR_BATCH_SIZE - 1, page_count)
                images = convert_from_bytes(
                    pdf_bytes,
                    dpi=OCR_DPI,
                    first_page=first_page,
                    last_page=last_page,
                    fmt="png",
                    grayscale=True,
                    thread_count=1,
                    timeout=60,
                )
                try:
                    page_texts = list(executor.map(_recognize_image, images))
                    extracted_pages.extend(page_texts)
                    total_text_length += sum(map(len, page_texts))
                    if total_text_length > MAX_OCR_TEXT_LENGTH:
                        raise PdfOcrError(
                            "The scanned PDF contains too much text to process at once. "
                            "Upload a shorter section of the paper."
                        )
                finally:
                    for image in images:
                        image.close()
    except PdfOcrError:
        raise
    except (PDFInfoNotInstalledError, PDFPageCountError, PDFPopplerTimeoutError,
            PDFSyntaxError, TesseractError, TesseractNotFoundError, OSError) as error:
        raise PdfOcrError(
            "OCR could not read the scanned PDF. Check its image quality and try again."
        ) from error

    text = "\n".join(extracted_pages).strip()
    if len(text) < 100:
        raise PdfOcrError(
            "No readable text could be recognized. The scan may be too faint, rotated, or image-only."
        )
    return text, page_count
