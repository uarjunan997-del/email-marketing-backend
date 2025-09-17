from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Optional
import os
import json

from groq import Groq
from env_loader import load_env

app = FastAPI(title="Email Marketing AI Service")

load_env()
groq_api_key = os.getenv("GROQ_API_KEY") or os.getenv("OPENAI_API_KEY")
model_default = os.getenv("GROQ_MODEL", "moonshotai/kimi-k2-instruct-0905")

client: Optional[Groq] = None
if groq_api_key:
    client = Groq(api_key=groq_api_key)


class DraftRequest(BaseModel):
    prompt: str = ""
    tone: str = "friendly"
    audience: str = "customers"
    callToAction: str = "Click here"

class DraftResponse(BaseModel):
    html: str
    mjml: Optional[str] = None
    variables: List[str]
    model: str


MJML_INSTRUCTIONS = """
You MUST output JSON ONLY (no prose before or after) with EXACT top-level keys:
{"mjml": "<mjml>...</mjml>", "html": "(optional rendered html or blank)", "variables": ["var1","var2"], "model": "<model_id>"}

ALLOWED PLACEHOLDER VARIABLES (use ONLY these; do not invent new ones):
{{first_name}}, {{headline}}, {{subheading}}, {{preheader}}, {{logo_url}}, {{hero_image}}, {{primary_link}}, {{cta_text}}, {{content_image_1}}, {{content_image_2}}, {{content_image_3}}, {{company_name}}, {{unsubscribe_url}}, {{unsubscribe}}

MJML REQUIREMENTS (follow all):
1. Root structure: <mjml><mj-body> ... </mj-body></mjml>.
2. Max width: ensure outer content constrained to 600px (implicit or via container sections). Avoid styles wider than 600px.
3. Sections & Columns: Use <mj-section> wrapping one or more <mj-column>. Each logical horizontal band is its own <mj-section>.
4. Text: Use <mj-text>. Provide exactly one main heading using {{headline}} and a supporting subheading using {{subheading}} early in the email.
5. Preheader: Include a hidden preheader block at the very top using {{preheader}} (technique: a small <mj-text> with inline styles display:none;font-size:1px;line-height:1px;). Keep preheader length 35–90 chars.
6. CTA: A single primary call-to-action button: <mj-button href=\"{{primary_link}}\" background-color=\"#1363DF\" color=\"#FFFFFF\">{{cta_text}}</mj-button>. Keep CTA text <= 28 characters, action-oriented (e.g., "Get Started").
7. Logo: If using {{logo_url}}, place it in its own small top section (can be left or centered). Width <= 200px. Always include alt="Logo".
8. Hero (conditional): Only include a hero section if {{hero_image}} placeholder should appear. Use <mj-section background-url=\"{{hero_image}}\" background-size=\"cover\" background-repeat=\"no-repeat\"> with an overlay text column containing heading/subheading/CTA (avoid unreadable contrast; consider a semi-transparent background color on text container e.g. rgba(0,0,0,0.55) with white text).
9. Feature / Content Blocks: Use up to 3 columns with placeholders {{content_image_1}}, {{content_image_2}}, {{content_image_3}} as needed. Each feature column: image (if used) + short bold title + supporting paragraph (max ~90 chars) inside <mj-text> blocks.
10. Variables: Always use double curly braces; never leak braces inside HTML attributes incorrectly. Do not wrap variables in additional quotes unless required by attribute syntax.
11. Accessibility: Provide meaningful alt text for each image referencing the variable context (e.g., alt="Hero image" for {{hero_image}}). Maintain sufficient color contrast (WCAG AA) for text over backgrounds.
12. Mobile Friendliness: Avoid fixed pixel font sizes below 14px. Headline <= 48 characters, subheading <= 110 characters ideally. Use padding for touch targets (button padding >= 12px vertical, 20px horizontal).
13. Footer: Include a clear unsubscribe line using {{unsubscribe_url}} if available; fallback to {{unsubscribe}} otherwise. Include {{company_name}} in the footer. Provide a physical address placeholder ONLY if explicitly mentioned (otherwise omit to avoid hallucination).
14. Styling: No external fonts, scripts, or tracking code. Use safe web fonts (system defaults). Limit <mj-style> usage; inline style attributes preferred only when necessary.
15. No Real Assets: Do NOT substitute real URLs—keep placeholders exactly as listed.
16. Clean Output: Do not include instructional comments, explanations, or markdown code fences. Never include backticks. Output MUST be valid JSON and mjml string must be inside the "mjml" key.
17. HTML Key: The "html" field may be blank or contain a basic rendered HTML approximation; if uncertain, set it to an empty string.
18. Variables Array: MUST list each unique placeholder actually used in the MJML or html in appearance order. Do not list unused placeholders.
19. Consistency: Every variable used must appear in the variables array; every variable in the array must appear at least once in mjml or html.
20. Content Tone: Match the described tone while remaining concise, value-focused, and spam-filter safe (avoid ALL CAPS, overuse of exclamation points, and misleading urgency).

Return ONLY the JSON object described above. Escape internal quotes properly inside the JSON string values.
""".strip()

def build_prompt(req: DraftRequest) -> tuple[str, str]:
    system = (
        "You are an expert B2B/B2C email marketing strategist and MJML architect. "
        "Your task: produce production-ready MJML that adheres strictly to brand-safe, accessible, mobile-first, deliverability-conscious best practices. "
        "Follow ALL enumerated contract rules verbatim. Do NOT add commentary or markdown. Output must be pure JSON with required keys. "
        "Preserve only approved placeholders and never hallucinate new ones. Maintain concise, conversion-oriented copy that avoids spam triggers."
    )
    user_parts: list[str] = []
    user_parts.append(f"Brief: {req.prompt}\n")
    if req.tone:
        user_parts.append(f"Tone: {req.tone}\n")
    if req.audience:
        user_parts.append(f"Audience: {req.audience}\n")
    if req.callToAction:
        user_parts.append(f"Call To Action: {req.callToAction}\n")
    user_parts.append("\nFinal Instructions:\n")
    user_parts.append(MJML_INSTRUCTIONS)
    return system, "".join(user_parts)


def extract_variables(html: str) -> List[str]:
    import re
    return list(dict.fromkeys(re.findall(r"\{\{([a-zA-Z0-9_]+)\}\}", html)))


def sanitize_model_html(raw: str) -> str:
    """Remove common LLM wrappers like markdown code fences or stray quoting around HTML."""
    if not raw:
        return raw
    txt = raw.strip().replace('\r\n', '\n')
    # If wrapped in JSON-like quotes accidentally, strip leading/trailing quotes when both ends are quotes and content contains <!DOCTYPE or <html
    if (txt.startswith('"') and txt.endswith('"')) or (txt.startswith("'") and txt.endswith("'")):
        inner = txt[1:-1].strip()
        if '<!DOCTYPE' in inner or '<html' in inner.lower():
            txt = inner
    # Remove leading fences like ```html / ``` or odd combinations
    if txt.startswith('```'):
        # drop first line
        parts = txt.split('\n')
        # skip first fence line
        parts = parts[1:]
        txt = '\n'.join(parts)
    # Sometimes models emit leading backticks variants like ``"`html
    if txt.lower().startswith('`html') or txt.lower().startswith('```html'):
        # remove first line up to first newline
        first_nl = txt.find('\n')
        if first_nl != -1:
            txt = txt[first_nl+1:]
    # Strip trailing fence
    if '\n```' in txt:
        # remove last fence and anything after
        fence_index = txt.rfind('\n```')
        txt = txt[:fence_index].rstrip()
    # Final safety: remove any remaining solitary triple backticks
    while '```' in txt:
        txt = txt.replace('```', '')
    return txt.strip()


def try_parse_json(raw: str):
    """Attempt to parse a JSON object from a raw string. Returns dict or None.
    Performs a few heuristic cleanups (strip code fences, trim surrounding backticks)."""
    if not raw:
        return None
    candidate = raw.strip()
    # Remove common markdown fences
    if candidate.startswith('```'):
        lines = [l for l in candidate.split('\n') if not l.strip().startswith('```')]
        candidate = '\n'.join(lines).strip()
    # If still wrapped fully in quotes and contains braces inside, strip quotes
    if (candidate.startswith('"') and candidate.endswith('"')) or (candidate.startswith("'") and candidate.endswith("'")):
        inner = candidate[1:-1].strip()
        if inner.startswith('{') and inner.endswith('}'):
            candidate = inner
    if not (candidate.startswith('{') and candidate.endswith('}')):
        return None
    try:
        return json.loads(candidate)
    except Exception:
        # Heuristic: sometimes model prepends prose then JSON. Try to locate first '{' and last '}' pair.
        try:
            first = candidate.find('{')
            last = candidate.rfind('}')
            if first != -1 and last != -1 and last > first:
                return json.loads(candidate[first:last+1])
        except Exception:
            return None
    return None


def extract_first_json_object(text: str) -> Optional[str]:
    """Scan the text and attempt to extract the first balanced top-level JSON object.
    Handles cases where the model prepends prose and then a JSON block or has trailing commentary.
    Returns the raw substring of the JSON object or None if not found."""
    if not text:
        return None
    started = False
    brace_stack = 0
    start_index = -1
    for i, ch in enumerate(text):
        if not started:
            if ch == '{':
                started = True
                brace_stack = 1
                start_index = i
        else:
            if ch == '{':
                brace_stack += 1
            elif ch == '}':
                brace_stack -= 1
                if brace_stack == 0:
                    return text[start_index:i+1]
    return None


def extract_mjml_fragment(text: str) -> Optional[str]:
    """Attempt to extract the first <mjml ...</mjml> fragment even if JSON contract was not followed."""
    if not text:
        return None
    lower = text.lower()
    start = lower.find('<mjml')
    if start == -1:
        return None
    end = lower.find('</mjml>', start)
    if end == -1:
        return None
    end += len('</mjml>')
    return text[start:end].strip()


def reconstruct_from_mjml_plus_trailing(text: str) -> Optional[dict]:
    """Handle malformed output like: <mjml>...</mjml>", "html": "", "variables": [ ... ], "model": "x" (missing opening brace).
    We attempt to isolate the mjml segment then parse the trailing pseudo-JSON parts heuristically.
    Returns a dict with keys mjml/html/variables/model or None if pattern not detected."""
    if not text:
        return None
    lower = text.lower()
    start = lower.find('<mjml')
    if start == -1:
        return None
    end = lower.find('</mjml>', start)
    if end == -1:
        return None
    end += len('</mjml>')
    mjml_part = text[start:end]
    remainder = text[end:]
    # Look for pattern like "variables": [ ... ] and "model": "..."
    import re
    vars_match = re.search(r'"variables"\s*:\s*\[(.*?)\]', remainder, re.DOTALL)
    model_match = re.search(r'"model"\s*:\s*"([^"]+)"', remainder)
    html_match = re.search(r'"html"\s*:\s*"(.*?)"', remainder, re.DOTALL)
    variables: List[str] = []
    if vars_match:
        raw_list = vars_match.group(1)
        # Split by quotes containing {{var}} tokens
        token_matches = re.findall(r'\{\{[^}]+\}\}', raw_list)
        # Normalize remove duplicate braces (keep as {{var}}) and dedupe preserving order
        seen = set()
        for t in token_matches:
            if t not in seen:
                seen.add(t)
                variables.append(t.replace('"','').strip())
    model = model_match.group(1) if model_match else 'unknown-model'
    html = html_match.group(1) if html_match else ''
    if mjml_part:
        return {"mjml": mjml_part.strip(), "html": html, "variables": variables, "model": model}
    return None


@app.post("/ai/draft", response_model=DraftResponse)
async def ai_draft(req: DraftRequest):
    if client is None:
        # Fallback basic HTML when not configured
        html = (
            "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='max-width:600px;margin:auto;font-family:Arial,sans-serif'>"
            f"<tr><td style='padding:24px;background:#0d47a1;color:#fff;text-align:center'><h1 style='margin:0;font-size:24px'>{req.prompt or 'New marketing email'}</h1></td></tr>"
            "<tr><td style='padding:24px;background:#ffffff;color:#222;font-size:14px;line-height:1.5'>"
            "<p>Hi {{first_name}},</p>"
            f"<p>We're reaching out to our {req.audience} with a {req.tone} update.</p>"
            f"<p>{req.prompt or 'New email'} – crafted just for you.</p>"
            f"<p style='text-align:center;margin:32px 0'><a href='{{primary_link}}' style='display:inline-block;background:#0d47a1;color:#fff;padding:12px 20px;border-radius:4px;text-decoration:none'>{req.callToAction}</a></p>"
            "<p>If you have any questions, just reply to this email.</p>"
            "<p style='font-size:12px;color:#666'>If you no longer wish to receive these emails you can {{unsubscribe}}.</p>"
            "</td></tr></table>"
        )
        return DraftResponse(html=html, mjml=None, variables=extract_variables(html), model="placeholder-local")

    # Normal path when Groq client is available
    system, user = build_prompt(req)
    print(system)
    print(user)

    completion = client.chat.completions.create(
        model=os.getenv("GROQ_MODEL", model_default),
        temperature=0.4,
        max_tokens=2200,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    )

    raw = (completion.choices[0].message.content or '').strip()
    raw_trunc = (raw[:300] + '...') if len(raw) > 300 else raw
    print(f"[AI-DRAFT] Raw model output (truncated): {raw_trunc}")

    mjml: Optional[str] = None
    html: Optional[str] = None
    variables: List[str] = []
    model_id = os.getenv("GROQ_MODEL", model_default)

    parsed = None
    recovery_path = 'none'
    try:
        # Primary parse attempt: direct parse
        parsed = try_parse_json(raw)
        if not parsed:
            # Secondary: attempt balanced object extraction then parse
            candidate = extract_first_json_object(raw)
            if candidate:
                parsed = try_parse_json(candidate)
                if parsed:
                    recovery_path = 'balanced-json'
        else:
            recovery_path = 'direct-json'
        if parsed:
            mjml = parsed.get('mjml')
            html = parsed.get('html') or ''
            candidate_for_vars = (mjml or '') + '\n' + (html or '')
            variables = extract_variables(candidate_for_vars)
        else:
            # Attempt malformed MJML + trailing fields reconstruction
            recon = reconstruct_from_mjml_plus_trailing(raw)
            if recon:
                parsed = recon
                recovery_path = 'reconstruct-mjml-trailing'
                mjml = recon.get('mjml')
                html = recon.get('html') or ''
                variables = [v.strip() for v in recon.get('variables', []) if v.strip()]
            else:
                raise ValueError('No JSON object detected')
    except Exception:
        # Fallback path – treat entire output as (possibly fenced) HTML or embedded JSON
        sanitized = sanitize_model_html(raw)
        # Second-chance: if sanitized still looks like JSON, parse again
        second = try_parse_json(sanitized)
        if second:
            mjml = second.get('mjml')
            html = second.get('html') or ''
            candidate_for_vars = (mjml or '') + '\n' + (html or '')
            variables = extract_variables(candidate_for_vars)
            recovery_path = 'sanitized-json'
        else:
            # Not JSON – treat as raw HTML (could still be MJML but model mis-followed spec)
            html = sanitized
            # If it appears to be MJML root, put into mjml field and clear html so placeholder logic engages
            if '<mjml' in html.lower() and mjml is None:
                mjml = html
                html = ''  # force placeholder injection below
            variables = extract_variables((mjml or '') + '\n' + (html or ''))
            recovery_path = 'raw-html-or-mjml'

    # If html itself is a JSON blob (model nested JSON inside html), attempt recovery
    if html and html.strip().startswith('{') and html.strip().endswith('}'):
        recovered = try_parse_json(html)
        if recovered:
            inner_html = recovered.get('html') or ''
            inner_mjml = recovered.get('mjml') or None
            # Only override if we actually extracted something meaningful
            if inner_html or inner_mjml:
                if inner_mjml and not mjml:
                    mjml = inner_mjml
                html = inner_html
                # Recompute variables
                variables = extract_variables((mjml or '') + '\n' + (html or ''))

    if not html:
        # Provide a minimal visible placeholder so the iframe shows something user-friendly
        html = (
            "<div style=\"font-family:Arial,sans-serif;padding:24px;text-align:center;color:#555;\">"
            "<em>Rendering MJML… (final HTML will appear after save)</em>"
            "</div>"
        )

    # STRICT mode optionally enforces JSON compliance; otherwise we already built fallback
    strict = os.getenv("AI_STRICT_JSON", "false").lower() == "true"
    if parsed is None and mjml is None:
        # Last-chance MJML fragment scraping to reduce hard failures
        frag = extract_mjml_fragment(raw)
        if frag:
            mjml = frag
            variables = extract_variables(frag)
            html = html or (
                "<div style=\"font-family:Arial,sans-serif;padding:24px;text-align:center;color:#555;\">"
                "<em>Rendering MJML… (final HTML will appear after save)</em>"
                "</div>"
            )
            parsed = {"mjml": mjml, "html": html, "variables": variables, "model": model_id}  # sentinel so strict mode passes
            recovery_path = 'fragment-mjml'

    if strict and parsed is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=502, detail="AI model failed to return valid JSON or recoverable MJML fragment.")
    print(f"Drafted email path={recovery_path} vars={len(variables)} model={model_id} mjml_present={mjml is not None} html_len={len(html or '')}")
    return DraftResponse(html=html, mjml=mjml, variables=variables, model=model_id)



# For local dev: uvicorn app:app --reload --host 0.0.0.0 --port 8000