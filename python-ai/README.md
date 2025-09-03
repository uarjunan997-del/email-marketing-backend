# Python AI Integration Starter

Includes PDF bank statement extraction pipeline leveraging:
- `pdfplumber` for text extraction
- OCR fallback via `pytesseract` + `pdf2image` + OpenCV preprocessing
- LLM (Groq) for structured transaction extraction with JSON schema enforcement
- Footer/legend stripping heuristics, row chunking, post-processing de-dup & filtering

## Environment Variables
```
OPENAI_API_KEY=<your_groq_key>
TESSDATA_PREFIX=optional/path/for/tesseract
```

## Dependencies (example `requirements.txt`)
```
pdfplumber
pytesseract
pdf2image
groq
opencv-python
numpy
Pillow
```
Add any missing packages when you copy the script.

## Usage
```
python extraction_lambda.py path/to/statement.pdf [password]
```
Prints JSON array of extracted transactions.

## Next Steps
- Wrap as AWS Lambda / Azure Function.
- Add caching / rate limit for LLM calls.
- Extend schema with currency, accountNumber, or classification confidence.
