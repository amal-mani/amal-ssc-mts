"""Check the app's data files before publishing. Usage: python3 tools/check_data.py <folder with the json files>
Prints OK or a list of problems and exits 1."""
import json, os, sys
D = sys.argv[1] if len(sys.argv) > 1 else 'data'
E = []
def load(n):
    p = os.path.join(D, n)
    if not os.path.exists(p): return None
    try: return json.load(open(p, encoding='utf-8'))
    except Exception as x: E.append(f'{n}: not valid JSON ({x})'); return None
def chk(q, where, need_s=False, need_tml=False):
    o = q.get('o')
    if not (isinstance(o, list) and len(o) == 4 and len({str(x).strip().lower() for x in o}) == 4): E.append(f'{where}: needs 4 different options: {str(q.get("q"))[:60]}')
    if not (isinstance(q.get('a'), int) and 0 <= q['a'] < 4): E.append(f'{where}: answer index must be 0-3: {str(q.get("q"))[:60]}')
    if not str(q.get('q', '')).strip() or not str(q.get('e', '')).strip(): E.append(f'{where}: empty question or explanation')
    if need_s and q.get('s') not in ('Quant', 'Reasoning', 'English', 'GA'): E.append(f'{where}: bad subject {q.get("s")}')
    if need_tml and (not q.get('t') or not q.get('ml')): E.append(f'{where}: missing t or ml: {str(q.get("q"))[:60]}')

b = load('bank.json')
if b:
    seen = set()
    for i, q in enumerate(b.get('q', [])):
        chk(q, f'bank.q[{i}]', True)
        k = str(q.get('q')).strip().lower()
        if k in seen: E.append(f'bank.q[{i}]: duplicate question: {k[:60]}')
        seen.add(k)
    for c in b.get('cloze', []):
        if not c.get('p') or len(c.get('b', [])) < 1: E.append(f'cloze {c.get("id")}: needs p and b')
        for j, x in enumerate(c.get('b', [])):
            chk({'q': c.get('p'), **x}, f'cloze {c.get("id")} blank {j+1}')
            if f'({j+1})' not in c.get('p', ''): E.append(f'cloze {c.get("id")}: passage has no blank ({j+1})')
    for c in b.get('rc', []):
        if not c.get('p') or len(c.get('qs', [])) < 3: E.append(f'rc {c.get("id")}: needs p and 3+ qs')
        for j, x in enumerate(c.get('qs', [])): chk(x, f'rc {c.get("id")} q{j+1}')
    for v in b.get('vocab', []):
        if not all(v.get(k) for k in ('w', 'm', 'syn', 'ant', 'ex')) or v['w'].lower() not in v['ex'].lower(): E.append(f'vocab {v.get("w")}: bad item')

p = load('pattern.json')
if p:
    for k, n in (('Quant', 20), ('Reasoning', 20), ('GA', 25), ('English', 25)):
        a = p.get(k)
        if not (isinstance(a, list) and all(isinstance(x, list) and len(x) == 2 and isinstance(x[0], str) and isinstance(x[1], int) and x[1] > 0 for x in a)): E.append(f'pattern.{k}: must be [["Topic", count], ...]'); continue
        if sum(x[1] for x in a) != n: E.append(f'pattern.{k}: counts add to {sum(x[1] for x in a)}, must be {n}')
    if not isinstance(p.get('log', []), list) or not isinstance(p.get('notes', []), list): E.append('pattern: log and notes must be lists')

c = load('ca.json')
if c:
    for d in c.get('days', []):
        for i, q in enumerate(d.get('quiz', [])): chk(q, f'ca {d.get("date")} quiz {i}')
m = load('monthly.json')
if m:
    for mo in m.get('months', []):
        for i, q in enumerate(mo.get('quiz', [])): chk(q, f'monthly {mo.get("month")} quiz {i}')
        for v in mo.get('videos', []):
            if not str(v.get('url', '')).startswith('https://www.youtube.com/results?search_query='): E.append(f'monthly {mo.get("month")}: video links must be YouTube search links')
pr = load('practice.json')
if pr:
    for d in pr.get('days', []):
        for i, q in enumerate(d.get('q', [])): chk(q, f'practice {d.get("date")} q{i}', True)
lq = load('lessonquiz.json')
if lq:
    for k, v in lq.get('quizzes', {}).items():
        for i, q in enumerate(v): chk(q, f'lessonquiz {k} q{i}')
for n in ('topics.json', 'updates.json', 'schedule.json', 'mocks.json'): load(n)

import datetime, re
ex = load('exams.json')
if ex:
    ids = set()
    for x in ex.get('items', []):
        w = f"exams {x.get('id')}"
        for k in ('id', 'body', 'name', 'status', 'text', 'apply'):
            if not x.get(k): E.append(f'{w}: missing {k}')
        if x.get('id') in ids: E.append(f'{w}: duplicate id')
        ids.add(x.get('id'))
        if x.get('status') not in ('open', 'soon', 'closed'): E.append(f'{w}: bad status')
        for k in ('start', 'end'):
            if x.get(k):
                try: datetime.date.fromisoformat(x[k])
                except Exception: E.append(f'{w}: bad date {k}')
        if x.get('status') == 'open' and not x.get('end'): E.append(f'{w}: open but no last date')
        for k in ('apply', 'notice', 'src'):
            if x.get(k) and not re.match(r'https://', x[k]): E.append(f'{w}: {k} not https')
        for pp in x.get('posts', []):
            if len(pp) != 4: E.append(f'{w}: post needs [cat,name,dept,tag]')

if E:
    print('PROBLEMS:', len(E)); [print(' -', e) for e in E[:60]]; sys.exit(1)
print('OK')
