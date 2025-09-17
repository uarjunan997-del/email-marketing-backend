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

## Run the Email Draft Microservice (Groq)

1. Create a virtual environment and install requirements:
	- Windows PowerShell:
	  - python -m venv .venv
	  - .venv\\Scripts\\Activate.ps1
	  - pip install -r requirements.txt
2. Configure environment variables (any of the following):
	 - Create a `.env` file (see `.env.example`) in `python-ai/` or repo root.
	 - Or set PowerShell vars:
		 - $env:GROQ_API_KEY = "<your_groq_key>"
		 - Optional: $env:GROQ_MODEL = "llama3-70b-8192"
3. Start the service:
	- uvicorn app:app --host 0.0.0.0 --port 8000 --reload

The Java backend calls http://localhost:8000/ai/draft. Configure `ai.python.base-url` in Spring if running elsewhere.

Env precedence (last wins):
1. repo `/.env`
2. `backend/.env`
3. `python-ai/.env`
4. `*.env.development` / `*.env.dev`
5. `*.env.local`

## Existing PDF Extraction Usage
```
python extraction_lambda.py path/to/statement.pdf [password]
```
Prints JSON array of extracted transactions.

## Next Steps
- Wrap as AWS Lambda / Azure Function.
- Add caching / rate limit for LLM calls.
- Extend schema with currency, accountNumber, or classification confidence.
