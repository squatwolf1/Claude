# 테일즈위버 거래소 시세 오버레이
import uuid  # winrt가 내부적으로 사용 (PyInstaller 누락 방지)
# 화면 캡처 + Windows 내장 OCR만 사용 (게임 메모리/입력/패킷 접근 없음)
import asyncio, ctypes, json, os, re, sys, threading, difflib
import tkinter as tk
from tkinter import ttk, messagebox, simpledialog
import mss
from PIL import Image, ImageOps

try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)  # 캡처 좌표 = 화면 좌표
except Exception:
    pass

BASE = os.path.dirname(sys.executable if getattr(sys, "frozen", False) else os.path.abspath(__file__))
CFG = os.path.join(BASE, "tw_market.json")
SLOTS = ["투구", "갑옷", "무기", "손목", "머리", "손", "다리", "윙"]
WEAPON_TIERS = ["아르카 본", "아르카 블러드", "아카드", "인퍼널", "아퀼루스", "어비스", "이클립스"]  # 무기 전용
TIERS = ["엔키라", "엔키라 칼라그", "인퍼널", "아퀼루스", "어비스", "이클립스"]  # 하위 -> 상위
TIER_COST = {"이클립스": (20232000, "3035"), "어비스": (7272000, "1091")}  # (시드, 엘소)
RF_COST = {"이클립스": (25838333, "3876", 7751500, "1163"), "어비스": (7238333, "1086", 2171500, "326")}  # 리폼 (1회 시드, 1회 엘소, 잠금당 시드, 잠금당 엘소)
STR_KEYS = {"r", "elso", "s", "felso", "lelso", "pm", "pa", "pb"}
EV_DEFAULT = {"ue": False, "uf": False, "fmode": "seed", "fseed": 25838333, "felso": "3876", "lseed": 7751500, "lelso": "1163",
              "pm": "3", "pa": "5", "pb": "2.5", "r": "2", "seed": 20232000, "elso": "3035", "s": "1", "n": None, "o": None, "mode": "seed", "use": "n"}
DEFAULT_RECIPES = {"이클립스 윙": [["어비스 윙", 1], ["가짜 달여왕 군단의 휘장 장식", 3], ["가공된 달의 광물", 1],
                                  ["룬의 원석", 20], ["달의 약초", 100], ["가짜 달여왕 군단의 인장", 6]]}


# ---------- OCR ----------
def ocr_lines(img):
    """이미지 -> [(y중심, [(x, text), ...])] (Windows.Media.Ocr, 한국어)"""
    from winrt.windows.media.ocr import OcrEngine
    from winrt.windows.globalization import Language
    from winrt.windows.graphics.imaging import SoftwareBitmap, BitmapPixelFormat
    from winrt.windows.storage.streams import DataWriter

    lang = Language("ko")
    if not OcrEngine.is_language_supported(lang):
        raise RuntimeError("Windows 한국어 OCR 미설치\n(설정 > 시간 및 언어 > 언어 > 한국어 > 언어 옵션 > 광학 문자 인식)")
    img = img.convert("RGBA")

    async def run():
        w = DataWriter()
        w.write_bytes(img.tobytes())
        bmp = SoftwareBitmap.create_copy_from_buffer(w.detach_buffer(), BitmapPixelFormat.RGBA8, img.width, img.height)
        return await OcrEngine.try_create_from_language(lang).recognize_async(bmp)

    res = asyncio.run(run())
    words = []
    for line in res.lines:
        for wd in line.words:
            r = wd.bounding_rect
            words.append((r.y + r.height / 2, r.x, r.height, wd.text))
    # y가 가까운 단어끼리 한 행으로 묶기 (OCR이 한 행을 여러 줄로 쪼개는 경우 대비)
    words.sort()
    rows = []
    for y, x, h, t in words:
        if rows and abs(rows[-1][0] - y) < h * 0.6:
            rows[-1][1].append((x, t))
        else:
            rows.append([y, [(x, t)]])
    return [(y, sorted(ws)) for y, ws in rows]


def capture(region):
    x, y, w, h = region
    with mss.mss() as s:
        g = s.grab({"left": x, "top": y, "width": w, "height": h})
    img = Image.frombytes("RGB", g.size, g.rgb)
    img = img.resize((w * 3, h * 3), Image.LANCZOS)  # 작은 게임 폰트 확대
    return ImageOps.autocontrast(ImageOps.grayscale(img)).convert("RGB")


# ---------- 파싱 ----------
PRICE_RE = re.compile(r"(?:(\d+)\s*억)?\s*(?:(\d+)\s*만)?\s*(\d{1,4})?\s*$")
FIX = str.maketrans({"O": "0", "o": "0", "D": "0", "l": "1", "I": "1", "|": "1", "!": "1", "S": "5", "B": "8", "〕": "0",
                     "민": "만", "반": "만", "안": "만", "맏": "만", "말": "만", "역": "억", "얶": "억", "먹": "억", "멱": "억", "엌": "억", "덕": "억"})


# 한글 자모가 영문/기호로 깨져 읽히는 경우 (긴 것부터 치환)
WORD_FIX = [("CH))H", "어깨"), ("CH", "어"), ("A|", "시"), ("Al", "시"), ("AI", "시"), ("Bl", "비"), ("BI", "비")]


def fix_words(s):
    for a, b in WORD_FIX:
        s = s.replace(a, b)
    return s


NUMISH = re.compile(r"[\d〕lI|!OoDSB기日\[\]「」\-?만억먹멱엌덕역얶민반안맏말]+")


def parse_row(text):
    """'아이템명 840 20만 5000 2일' -> (이름, 개수, 가격)"""
    text = fix_words(re.sub(r"[,.:]", "", text.strip()))
    s = re.sub(r"\s*[\d?]*\s*(일|시간|분|밀|잎)\s*$", "", text)  # 남은 시간 제거
    # 끝에서부터 숫자처럼 생긴 토큰 = 개수/가격, 나머지 = 이름(원문 그대로)
    tok = s.split()
    i = len(tok)
    while i and NUMISH.fullmatch(tok[i - 1]):
        i -= 1
    name, tail = " ".join(tok[:i]), " ".join(tok[i:])
    tail = re.sub(r"기(?=\d*\s*[억먹멱엌덕역얶])", "21", tail)  # '21억' -> '기억'
    tail = re.sub(r"[1lI|]?〕", "0", tail).translate(FIX)       # '40만' -> '41〕만'
    m = re.fullmatch(r"(?:(\d+)\s+)?((?:\d+\s*억\s*)?\d+\s*만(?:\s*\d{1,4})?|\d+\s*억(?:\s*\d{1,4})?)", tail) \
        or re.fullmatch(r"(\d+)\s+(\d{1,4})", tail)  # 1만 미만 가격은 개수가 있어야 인정
    if not m:
        return None
    p = PRICE_RE.search(m.group(2))
    price = int(p.group(1) or 0) * 10**8 + int(p.group(2) or 0) * 10**4 + int(p.group(3) or 0)
    return name.strip(), int(m.group(1) or 1), price


def parse_price(s):
    """'20만 5000' / '205000' / '1억 2000만' -> 정수"""
    s = s.replace(",", "").strip()
    p = re.fullmatch(r"(?:(\d+)\s*억)?\s*(?:(\d+)\s*만)?\s*(\d+)?", s)
    if not s or not p:
        return None
    return int(p.group(1) or 0) * 10**8 + int(p.group(2) or 0) * 10**4 + int(p.group(3) or 0)


# 게임 폰트에서 ㅁ/ㅇ 이 비슷해 생기는 오인식 (양쪽에 똑같이 적용해 비교)
CONFUSE = str.maketrans({"며": "여", "머": "어", "먹": "억", "뮝": "윙", "윔": "윙", "마": "아", "미": "이", "므": "으", "무": "우", "모": "오", "들": "틀", "화": "하", "혈": "결"})


def norm(s):
    return re.sub(r"[\s\[\]()+]", "", s).lower().translate(CONFUSE)


def match(target, name):
    t, n = norm(target), norm(name)
    return bool(t) and (t in n or (len(t) >= 4 and t[1:] in n) or difflib.SequenceMatcher(None, t, n).ratio() >= 0.9)


def won(n):
    if n is None:
        return "-"
    eok, r = divmod(int(n), 10**8)
    man, one = divmod(r, 10**4)
    parts = [f"{eok}억" if eok else "", f"{man}만" if man else "", f"{one}" if one else ""]
    return " ".join(p for p in parts if p) or "0"


# ---------- 영역 선택 ----------
def select_region(root, done):
    top = tk.Toplevel(root)
    top.attributes("-fullscreen", True, "-alpha", 0.3, "-topmost", True)
    top.configure(bg="black", cursor="cross")
    c = tk.Canvas(top, bg="black", highlightthickness=0)
    c.pack(fill="both", expand=True)
    st = {}

    def down(e):
        st["x"], st["y"] = e.x_root, e.y_root
        st["r"] = c.create_rectangle(e.x, e.y, e.x, e.y, outline="red", width=2)

    def move(e):
        c.coords(st["r"], st["x"] - top.winfo_rootx(), st["y"] - top.winfo_rooty(), e.x, e.y)

    def up(e):
        x1, y1, x2, y2 = min(st["x"], e.x_root), min(st["y"], e.y_root), max(st["x"], e.x_root), max(st["y"], e.y_root)
        top.destroy()
        if x2 - x1 > 20 and y2 - y1 > 10:
            done([x1, y1, x2 - x1, y2 - y1])

    c.bind("<ButtonPress-1>", down)
    c.bind("<B1-Motion>", move)
    c.bind("<ButtonRelease-1>", up)
    top.bind("<Escape>", lambda e: top.destroy())


# ---------- 앱 ----------
class App:
    def __init__(self, root):
        self.root = root
        self.cfg = {"region": None, "auto": 0, "cur": "이클립스 윙", "recipes": DEFAULT_RECIPES, "prices": {}}
        if os.path.exists(CFG):
            try:
                self.cfg.update(json.load(open(CFG, encoding="utf-8")))
            except Exception:
                pass
        self.cfg.pop("items", None)
        self.cfg.setdefault("db", {})
        self.cfg.setdefault("memo", {})  # 재료별 수급 메모
        self.cfg["ev"] = {k: v for k, v in self.cfg.get("ev", {}).items() if "mode" in v}  # 구버전 값 정리
        self.last = "아직 스캔 안 함"
        root.title("TW 제작 시세표")
        root.attributes("-topmost", True, "-alpha", 0.92)
        root.geometry("1000x640")

        bar = ttk.Frame(root)
        bar.pack(fill="x", padx=4, pady=4)
        ttk.Button(bar, text="영역 지정", command=lambda: select_region(root, self.set_region)).pack(side="left")
        ttk.Button(bar, text="스캔", command=self.scan).pack(side="left", padx=4)
        self.auto = tk.BooleanVar(value=bool(self.cfg["auto"]))
        ttk.Checkbutton(bar, text="자동(2초)", variable=self.auto, command=self.save).pack(side="left")
        ttk.Button(bar, text="인식 확인", command=self.show_last).pack(side="left", padx=4)
        self.status = ttk.Label(bar, text="")
        self.status.pack(side="right")

        eq = ttk.Frame(root)
        eq.pack(fill="x", padx=4)
        cur = self.cfg["cur"].rsplit(" ", 1) if " " in self.cfg["cur"] else ["이클립스", "윙"]
        self.cb_slot = ttk.Combobox(eq, state="readonly", values=SLOTS, width=6)
        self.cb_tier = ttk.Combobox(eq, state="readonly", values=TIERS, width=12)
        self.cb_slot.set(cur[1] if cur[1] in SLOTS else SLOTS[0])
        self.tier_values()
        self.cb_tier.set(cur[0] if cur[0] in self.cb_tier["values"] else self.cb_tier["values"][0])
        for t, cb in (("부위", self.cb_slot), ("단계", self.cb_tier)):
            ttk.Label(eq, text=t).pack(side="left")
            cb.pack(side="left", padx=2)
            cb.bind("<<ComboboxSelected>>", self.pick_equip)
        ttk.Button(eq, text="제작비 DB 저장 →", command=self.db_save).pack(side="right")
        ttk.Label(eq, text="   재료 순서").pack(side="left")
        ttk.Button(eq, text="▲ 위로", command=lambda: self.move(-1)).pack(side="left", padx=2)
        ttk.Button(eq, text="▼ 아래로", command=lambda: self.move(1)).pack(side="left")

        db = ttk.Frame(root)
        db.pack(side="right", fill="y", padx=4, pady=4)
        self.db_title = ttk.Label(db, text="")
        self.db_title.pack()
        self.dbv = ttk.Treeview(db, columns=("n", "p"), show="headings")
        for c, t, w in (("n", "장비", 120), ("p", "제작비", 110)):
            self.dbv.heading(c, text=t)
            self.dbv.column(c, width=w, anchor="w" if c == "n" else "e")
        self.dbv.pack(fill="y", expand=True)
        self.dbv.bind("<Double-1>", self.db_use)
        ttk.Button(db, text="DB 삭제", command=self.db_del).pack(fill="x")

        add = ttk.Frame(root)
        add.pack(fill="x", padx=4, pady=4)
        self.e_name, self.e_qty, self.e_price = ttk.Entry(add, width=18), ttk.Entry(add, width=5), ttk.Entry(add, width=12)
        self.e_memo = ttk.Entry(add)
        for e, t in ((self.e_name, "재료"), (self.e_qty, "개수"), (self.e_price, "가격(비우면 스캔)"), (self.e_memo, "메모")):
            ttk.Label(add, text=t).pack(side="left")
            e.pack(side="left", padx=2, fill="x", expand=e is self.e_memo)
            e.bind("<Return>", lambda _: self.add())
        self.e_qty.insert(0, "1")
        ttk.Button(add, text="추가/수정", command=self.add).pack(side="left")

        cols = ("name", "qty", "price", "total", "memo")
        self.tv = ttk.Treeview(root, columns=cols, show="headings", height=10)
        for c, t, w in zip(cols, ("재료", "개수", "최저 개당가", "합계", "메모(수급처)"), (170, 45, 115, 115, 200)):
            self.tv.heading(c, text=t)
            self.tv.column(c, width=w, anchor="w" if c in ("name", "memo") else "e")
        self.tv.pack(fill="both", expand=True, padx=4)
        self.tv.bind("<<TreeviewSelect>>", self.on_select)
        self.tv.bind("<Delete>", lambda e: self.remove())
        # 인챈트 기댓값 (장비별 저장). 일반 주문서: 성공 시 1장 소모 / 일회용: 시도마다 1장 소모
        self.ev_vars, self.ev_ent = {}, {}
        self.cost_mode, self.use = tk.StringVar(), tk.StringVar()

        def field(fr, k, label, w=11, scan=False):
            ttk.Label(fr, text=label).pack(side="left")
            v = self.ev_vars[k] = tk.StringVar()
            e = self.ev_ent[k] = ttk.Entry(fr, textvariable=v, width=w)
            e.pack(side="left", padx=1)
            e.bind("<FocusOut>", self.ev_save)
            e.bind("<Return>", self.ev_save)
            if scan:
                ttk.Button(fr, text="스캔", width=4, command=lambda: self.scan(k)).pack(side="left")

        f1 = ttk.Frame(root); f1.pack(fill="x", padx=4)
        self.inc = {"ue": tk.BooleanVar(), "uf": tk.BooleanVar()}  # 인챈트/리폼 총 제작비 포함 여부
        ttk.Checkbutton(f1, text="인챈트 포함", variable=self.inc["ue"], command=self.ev_save).pack(side="left")
        field(f1, "r", "확률%", 4)
        lf = ttk.LabelFrame(f1, text="1회 비용 지불")
        lf.pack(side="left", padx=6)
        for m, t in (("seed", "시드"), ("elso", "엘소")):
            ttk.Radiobutton(lf, text=t, value=m, variable=self.cost_mode, command=self.ev_save).pack(side="left")
        ttk.Separator(f1, orient="vertical").pack(side="left", fill="y", padx=4)
        field(f1, "seed", "1회 시드", 10)
        field(f1, "elso", "  1회 엘소", 6)
        ttk.Separator(f1, orient="vertical").pack(side="left", fill="y", padx=4)
        field(f1, "s", "  필요 성공 횟수", 4)
        f2 = ttk.Frame(root); f2.pack(fill="x", padx=4)
        self.ev_lbl = {}
        for k, t in (("n", "일반 주문서"), ("o", "일회용 주문서")):
            ttk.Radiobutton(f2, value=k, variable=self.use, command=self.ev_save).pack(side="left")
            field(f2, k, t, 16)
        # 리폼: 주스탯 + 부스탯 2개. 성공한 줄은 잠금(잠금 1개당 1회 비용 추가)
        f3 = ttk.Frame(root); f3.pack(fill="x", padx=4, pady=(6, 0))
        ttk.Checkbutton(f3, text="리폼 포함", variable=self.inc["uf"], command=self.ev_save).pack(side="left")
        lf = ttk.LabelFrame(f3, text="1회 비용 지불")
        lf.pack(side="left", padx=6)
        self.fmode = tk.StringVar()
        for m, t in (("seed", "시드"), ("elso", "엘소")):
            ttk.Radiobutton(lf, text=t, value=m, variable=self.fmode, command=self.ev_save).pack(side="left")
        ttk.Separator(f3, orient="vertical").pack(side="left", fill="y", padx=4)
        field(f3, "fseed", "1회 시드", 10)
        field(f3, "felso", "  1회 엘소", 6)
        ttk.Separator(f3, orient="vertical").pack(side="left", fill="y", padx=4)
        field(f3, "lseed", "잠금 1개당 시드", 9)
        field(f3, "lelso", "  엘소", 5)
        f4 = ttk.Frame(root); f4.pack(fill="x", padx=4)
        for k, t in (("pm", "주스탯 확률%"), ("pa", "  부스탯1(92) 확률%"), ("pb", "  부스탯2(180) 확률%")):
            field(f4, k, t, 5)
        self.sum = tk.Label(root, text="", font=("맑은 고딕", 16, "bold"), fg="#fff", bg="#2b5797", anchor="e", padx=10, pady=6)
        self.sum.pack(fill="x", padx=4, pady=(4, 0))
        self.ev_lbl = tk.Label(root, text="", font=("맑은 고딕", 11, "bold"), fg="#fff", bg="#3c6db0", anchor="e", justify="right", padx=10, pady=4)
        self.ev_lbl.pack(fill="x", padx=4)
        self.rf_lbl = tk.Label(root, text="", font=("맑은 고딕", 11, "bold"), fg="#fff", bg="#4a7cc0", anchor="e", padx=10, pady=4)
        self.rf_lbl.pack(fill="x", padx=4, pady=(0, 4))
        self.busy = False
        self.refresh()
        self.tick()

    # 데이터: recipes{장비: [[재료, 개수]]}, prices{재료: {"p": 가격, "m": 수동여부}} (장비끼리 시세 공유)
    def items(self):
        return self.cfg["recipes"].get(self.cfg["cur"], [])

    def price(self, name):  # 우선순위: 직접입력 > 제작비 DB > 스캔
        pr = self.cfg["prices"].get(name, {})
        return pr.get("p") if pr.get("m") or name not in self.cfg["db"] else self.cfg["db"][name]

    def mark(self, name):
        return " ✎" if self.cfg["prices"].get(name, {}).get("m") else " ⚙" if name in self.cfg["db"] else ""

    def refresh(self):
        self.dbv.delete(*self.dbv.get_children())
        slot = self.cfg["cur"].rsplit(" ", 1)[-1]
        self.db_title.config(text=f"[{slot}] 제작비 DB (더블클릭 = 재료로 추가)")
        for n, p in self.cfg["db"].items():
            if n.rsplit(" ", 1)[-1] == slot:  # 현재 부위만 표시
                self.dbv.insert("", "end", iid=n, values=(n, won(p)))
        self.tv.delete(*self.tv.get_children())
        total, miss = 0, 0
        for i, (n, q) in enumerate(self.items()):
            p = self.price(n)
            miss += p is None
            total += q * (p or 0)
            self.tv.insert("", "end", iid=str(i), values=(n, q, won(p) + self.mark(n), won(None if p is None else q * p), self.cfg["memo"].get(n, "")))
        if self.cfg["cur"] not in self.cfg["ev"]:
            self.tier_cost()
        ev = self.cfg["ev"].setdefault(self.cfg["cur"], {})
        for k, d in EV_DEFAULT.items():
            ev.setdefault(k, d)
        self.cost_mode.set(ev["mode"]); self.use.set(ev["use"]); self.fmode.set(ev["fmode"])
        for k, v in self.inc.items():
            v.set(ev[k])
        for k, v in self.ev_vars.items():
            if self.root.focus_get() is not self.ev_ent[k]:  # 입력 중인 칸은 덮어쓰지 않음
                v.set(ev[k] if k in STR_KEYS else won(ev[k]) if ev[k] is not None else "")
        res = {k: self.expect(ev, k) for k in "no"}
        fmt = lambda se, el: " + ".join([f"{won(se)} 시드"] * bool(se or not el) + [f"{el:,} 엘소"] * bool(el))
        self.ev_lbl.config(text="  |  ".join(("▶ " if k == ev["use"] else "") + f"{t} 인챈트 기댓값: {fmt(*res[k])}"
                                            for k, t in (("n", "일반"), ("o", "일회용"))) + ("" if ev["ue"] else "  [미포함]"))
        seed, elso = res[ev["use"]] if ev["ue"] else (0, 0)
        rs, re_, tries = self.reform(ev)
        self.rf_lbl.config(text=f"리폼 기댓값 (평균 {tries:.0f}회): {fmt(rs, re_)}" + ("" if ev["uf"] else "  [미포함]"))
        if ev["uf"]:
            seed, elso = seed + rs, elso + re_
        total += seed
        self.total = total
        self.sum.config(text=f"{self.cfg['cur']} 총 제작비: {won(total)} 시드" + (f" + {elso:,} 엘소" if elso else "") + (f"  (미확인 {miss})" if miss else ""))

    def save(self):
        self.cfg["auto"] = int(self.auto.get())
        json.dump(self.cfg, open(CFG, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    def set_region(self, r):
        self.cfg["region"] = r
        self.save()
        self.status.config(text=f"영역 {r[2]}x{r[3]}")

    def tier_values(self):  # 부위에 맞는 단계 목록 (무기는 별도)
        vals = WEAPON_TIERS if self.cb_slot.get() == "무기" else TIERS
        self.cb_tier["values"] = vals
        if self.cb_tier.get() not in vals:
            self.cb_tier.set(vals[0])

    def pick_equip(self, _=None):
        self.tier_values()
        self.cfg["cur"] = f"{self.cb_tier.get()} {self.cb_slot.get()}"
        self.cfg["recipes"].setdefault(self.cfg["cur"], [])
        self.tier_cost()
        self.save(); self.refresh()

    def tier_cost(self):  # 단계별 인챈트 1회 비용 자동 적용
        tier = self.cfg["cur"].rsplit(" ", 1)[0]
        if tier in TIER_COST:
            ev = self.cfg["ev"].setdefault(self.cfg["cur"], {})
            ev["seed"], ev["elso"] = TIER_COST[tier]
        if tier in RF_COST:
            ev = self.cfg["ev"].setdefault(self.cfg["cur"], {})
            ev["fseed"], ev["felso"], ev["lseed"], ev["lelso"] = RF_COST[tier]

    def reform(self, ev):
        """성공한 스탯은 바로 잠그는 전략의 기댓값. 상태 = 잠긴 스탯 집합,
        E[S] = (1회비용 + 잠금수x잠금비용 + Σ P(새로 성공 T)·E[S∪T]) / (1 - P(아무것도 성공 안 함))
        반환 (시드, 엘소, 평균 시도 횟수)"""
        try:
            p = [float(ev[k]) / 100 for k in ("pm", "pa", "pb")]
        except (ValueError, TypeError):
            return 0, 0, 0
        if not all(0 < x <= 1 for x in p):
            return 0, 0, 0
        seed = ev["fmode"] == "seed"
        base = (ev["fseed"] or 0) if seed else int(ev["felso"] or 0)
        lock = (ev["lseed"] or 0) if seed else int(ev["lelso"] or 0)

        memo = {}

        def solve(mask):  # -> (기대 비용, 기대 시도)
            if mask == 7:
                return 0.0, 0.0
            if mask in memo:
                return memo[mask]
            free = [i for i in range(3) if not mask >> i & 1]
            c, t, stay = base + bin(mask).count("1") * lock, 1.0, 0.0
            for sub in range(1 << len(free)):
                pr, nm = 1.0, mask
                for j, i in enumerate(free):
                    if sub >> j & 1:
                        pr *= p[i]; nm |= 1 << i
                    else:
                        pr *= 1 - p[i]
                if nm == mask:
                    stay = pr
                else:
                    ec, et = solve(nm)
                    c += pr * ec; t += pr * et
            memo[mask] = (c / (1 - stay), t / (1 - stay))
            return memo[mask]

        cost, tries = solve(0)
        return (round(cost), 0, tries) if seed else (0, round(cost), tries)

    def db_save(self):
        self.cfg["db"][self.cfg["cur"]] = self.total
        self.save(); self.refresh()

    def db_del(self):
        for n in self.dbv.selection():
            self.cfg["db"].pop(n, None)
        self.save(); self.refresh()

    def db_use(self, _):
        for n in self.dbv.selection():
            if n != self.cfg["cur"] and all(norm(it[0]) != norm(n) for it in self.items()):
                self.items().append([n, 1])
        self.save(); self.refresh()

    def on_select(self, _):
        s = self.tv.selection()
        if s:
            n, q = self.items()[int(s[0])]
            pr = self.cfg["prices"].get(n, {})
            for e, v in ((self.e_name, n), (self.e_qty, q), (self.e_price, won(pr.get("p")) if pr.get("m") else ""),
                         (self.e_memo, self.cfg["memo"].get(n, ""))):
                e.delete(0, "end"); e.insert(0, v)

    def add(self):
        name = self.e_name.get().strip()
        try:
            qty = int(self.e_qty.get().replace(",", ""))
        except ValueError:
            return messagebox.showwarning("", "개수는 숫자로 입력")
        ptxt = self.e_price.get().strip()
        price = parse_price(ptxt)
        if not name or (ptxt and price is None):
            return messagebox.showwarning("", "이름/가격 확인 (예: 20만 5000)") if name else None
        items = self.items()
        for it in items:
            if norm(it[0]) == norm(name):
                it[:] = [name, qty]
                break
        else:
            items.append([name, qty])
        pr = self.cfg["prices"].setdefault(name, {"p": None})
        pr["m"] = price is not None
        self.cfg["memo"][name] = self.e_memo.get().strip()
        if price is not None:
            pr["p"] = price
        self.save(); self.refresh()

    def move(self, d):  # 선택한 재료 순서 이동
        s = self.tv.selection()
        if not s:
            return
        i, items = int(s[0]), self.items()
        j = i + d
        if 0 <= j < len(items):
            items[i], items[j] = items[j], items[i]
            self.save(); self.refresh()
            self.tv.selection_set(str(j)); self.tv.see(str(j))

    def remove(self):
        for s in sorted(self.tv.selection(), key=int, reverse=True):
            del self.items()[int(s)]
        self.save(); self.refresh()

    def show_last(self):
        w = tk.Toplevel(self.root)
        w.title("마지막 인식 결과")
        w.attributes("-topmost", True)
        t = tk.Text(w, width=70, height=20)
        t.pack(fill="both", expand=True)
        t.insert("1.0", self.last)

    def expect(self, ev, k):
        """성공 1회당 기대 시도 = 100/확률. 일반: 주문서 1장 + 시도x1회비용 / 일회용: 시도x(주문서+1회비용)
        x 필요 성공 횟수. 시드와 엘소는 따로 반환 (시드, 엘소)"""
        try:
            n, succ = 100 / float(ev["r"]), int(ev["s"] or 1)
        except (ValueError, ZeroDivisionError, TypeError):
            return 0, 0
        scroll = ev[k] or 0
        seed = scroll if k == "n" else scroll * n
        elso = 0
        if ev["mode"] == "seed":
            seed += (ev["seed"] or 0) * n
        else:
            elso = int(ev["elso"] or 0) * n
        return round(seed * succ), round(elso * succ)

    def ev_save(self, _=None):
        ev = self.cfg["ev"].setdefault(self.cfg["cur"], {})
        for k, v in self.ev_vars.items():
            ev[k] = v.get().strip() if k in STR_KEYS else parse_price(v.get())
        for k, v in self.inc.items():
            ev[k] = v.get()
        ev["mode"], ev["use"], ev["fmode"] = self.cost_mode.get() or "seed", self.use.get() or "n", self.fmode.get() or "seed"
        self.save(); self.refresh()

    def scan(self, target=None):
        if self.busy:
            return
        self.target = target
        if not self.cfg["region"]:
            return messagebox.showinfo("", "먼저 '영역 지정'으로 거래소 목록(아이템 이름~개당 가격)을 드래그하세요.")
        self.busy = True
        self.status.config(text="스캔 중…")
        threading.Thread(target=self._scan, daemon=True).start()

    def _scan(self):
        try:
            img = capture(self.cfg["region"])
            img.save(os.path.join(BASE, "scan_debug.png"))
            texts = [" ".join(t for _, t in ws) for _, ws in ocr_lines(img)]
            rows, last = [], ""
            for r in filter(None, map(parse_row, texts)):
                # 이름이 안 읽힌 행은 같은 검색 결과이므로 위(없으면 아래 첫) 이름을 이어받음
                last = r[0] if re.search("[가-힣]{2}", r[0]) else last
                rows.append([last, r[1], r[2]])
            allrows = rows  # 선택 스캔용 (이름 없어도 맨 위 행)
            rows = [r for r in rows if r[0]]  # 이름 비교용: 이름은 위 행에서 아래로만 이어받음
            names = {n for rec in self.cfg["recipes"].values() for n, _ in rec}
            hit = []
            sel = self.tv.selection()
            if self.target and allrows:  # 인챈트/리폼 스캔 버튼: 맨 위 행 가격
                self.cfg["ev"].setdefault(self.cfg["cur"], {})[self.target] = allrows[0][2]
                hit.append(self.target)
                names = set()
            elif sel and allrows:  # 재료를 선택해 두면 이름 비교 없이 맨 위 행(최저가) 적용
                n = self.items()[int(sel[0])][0]
                self.cfg["prices"].setdefault(n, {})["p"] = allrows[0][2]
                self.cfg["prices"][n]["m"] = False
                hit.append(n)
                names = set()
            for n in names:
                pr = self.cfg["prices"].setdefault(n, {"p": None})
                if pr.get("m"):
                    continue
                for rn, _cnt, p in rows:  # 위에서부터 첫 일치 행 = 최저가
                    if match(n, rn):
                        pr["p"] = p
                        hit.append(n)
                        break
            self.last = "[OCR 원문]\n" + "\n".join(texts) + "\n\n[해석된 행] (이름 | 개수 | 가격)\n" + \
                "\n".join(f"{a} | {b} | {won(c)}" for a, b, c in allrows) + "\n\n[일치] " + ", ".join(hit) + \
                "\n\n캡처 이미지: scan_debug.png"
            msg = f"행 {len(rows)} / 일치 {len(hit)}"
            self.root.after(0, lambda: (self.save(), self.refresh()))
        except Exception as e:
            msg = "오류"
            self.last = f"오류: {e}"
            self.root.after(0, lambda e=e: messagebox.showerror("OCR", str(e)))
        self.root.after(0, lambda: (self.status.config(text=msg), setattr(self, "busy", False)))

    def tick(self):
        if self.auto.get() and self.cfg["region"] and not self.busy:
            self.scan()
        self.root.after(2000, self.tick)


if __name__ == "__main__":
    root = tk.Tk()
    App(root)
    root.mainloop()
