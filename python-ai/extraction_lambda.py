# Minimal copy of extraction logic (trimmed) - see original project for full version.
import json, os, logging, time
from typing import List, Dict, Optional
import pdfplumber, pytesseract
from pdf2image import convert_from_path
from groq import Groq
from PIL import Image
import numpy as np, cv2
from env_loader import load_env

logging.basicConfig(level=logging.INFO)
load_env()
client = Groq(api_key=os.environ.get("GROQ_API_KEY") or os.environ.get("OPENAI_API_KEY"))

LEGEND_TRIGGER_KEYWORDS=["legends","legend:","end of statement"]

def preprocess_image(pil_image):
    gray = np.array(pil_image.convert("L")); _,th=cv2.threshold(gray,150,255,cv2.THRESH_BINARY); return Image.fromarray(th)

def strip_footer(text:str)->str:
    if not text: return text
    out=[]; stop=False
    for line in text.splitlines():
        l=line.lower()
        if any(k in l for k in LEGEND_TRIGGER_KEYWORDS): stop=True
        if stop: continue
        out.append(line)
    return "\n".join(out)

def detect_bank_name(text:str)->Optional[str]:
    if not text: return None
    snippet=text[:3000]
    prompt=f"Return ONLY the short bank name token (e.g. HDFC, ICICI, SBI, AXIS) for this statement snippet:\n---\n{snippet}\n---\nBank:";
    try:
        r=client.chat.completions.create(model="moonshotai/kimi-k2-instruct",messages=[{"role":"user","content":prompt}],temperature=0,max_completion_tokens=8)
        return (r.choices[0].message.content or '').split()[0].strip(':,').upper()
    except Exception:
        return None

def call_llm(chunk, bank_name=None)->List[Dict]:
    schema={"type":"array","items":{"type":"object","properties":{"date":{"type":"string"},"description":{"type":"string"},"amount":{"type":"number"},"balance":{"type":["number","null"]},"category":{"type":"string"},"bankName":{"type":"string"}},"required":["date","description","amount","category","bankName"],"additionalProperties":False}}
    prompt=f"Extract bank transactions as JSON array (no wrapper). Use ISO dates YYYY-MM-DD. Amount negative for debits. Use bankName={(bank_name or 'INFER')}. If none return [].\n---\n{chunk}\n---";
    for attempt in range(3):
        try:
            c=client.chat.completions.create(model="moonshotai/kimi-k2-instruct",messages=[{"role":"user","content":prompt}],temperature=0,max_completion_tokens=1024,response_format={"type":"json_schema","json_schema":{"name":"txns","schema":schema}})
            raw=c.choices[0].message.content.strip()
            return json.loads(raw if not raw.startswith('```') else raw.split('```')[1])
        except Exception as e:
            time.sleep(2*(attempt+1))
    return []

def extract(pdf_path:str,password:Optional[str]=None)->List[Dict]:
    all_text=""; first_pages=""; header_found=False
    with pdfplumber.open(pdf_path, password=password) as pdf:
        for i,p in enumerate(pdf.pages):
            t=p.extract_text()
            if not t or not t.strip():
                img=convert_from_path(pdf_path, first_page=p.page_number,last_page=p.page_number)[0]
                t=pytesseract.image_to_string(preprocess_image(img))
            if i<2: first_pages+="\n"+t
            all_text+="\n"+t
    bank=detect_bank_name(first_pages)
    cleaned=strip_footer(all_text)
    chunks=[cleaned[i:i+6000] for i in range(0,len(cleaned),6000)]
    txns=[]
    for ch in chunks:
        txns.extend(call_llm(ch, bank))
    return txns

if __name__=="__main__":
    import sys
    if len(sys.argv)<2:
        print("Usage: python extraction_lambda.py <pdf> [password]"); exit(1)
    res=extract(sys.argv[1], sys.argv[2] if len(sys.argv)>2 else None)
    print(json.dumps(res, indent=2))
