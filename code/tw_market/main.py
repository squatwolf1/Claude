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
SLOTS = ["투구", "갑옷", "무기", "손목", "머리", "몸", "손", "다리", "윙"]
TIERS = ["엔키라", "엔키라 칼라그", "인퍼널", "아퀼루스", "어비스", "이클립스"]  # 하위 -> 상위
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
FIX = str.maketrans({"O": "0", "o": "0", "D": "0", "l": "1", "I": "1", "|": "1", "!": "1", "S": "5", "B": "8",
                     ",": "", ".": "", "민": "만", "반": "만", "안": "만", "맏": "만", "말": "만", "역": "억", "얶": "억", "먹": "억", "멱": "억", "엌": "억", "덕": "억"})


def parse_row(text):
    """'아이템명 840 20만 5000 2일' -> (이름, 개수, 가격)"""
    s = re.sub(r"\s*\d+\s*(일|시간|분|밀|잎)\s*$", "", text.strip())  # 남은 시간 제거
    m = re.search(r"(\d+)\s+((?:\d+\s*억\s*)?(?:\d+\s*만\s*)?\d{1,4})\s*$", s.translate(FIX)) \
        or re.search(r"(\d+)\s+((?:\d+\s*억\s*)?\d+\s*만)\s*$", s.translate(FIX))
    if not m:
        return None
    p = PRICE_RE.search(m.group(2))
    price = int(p.group(1) or 0) * 10**8 + int(p.group(2) or 0) * 10**4 + int(p.group(3) or 0)
    name = s.translate(FIX)[: m.start()]
    return name.strip(), int(m.group(1)), price


def parse_price(s):
    """'20만 5000' / '205000' / '1억 2000만' -> 정수"""
    s = s.replace(",", "").strip()
    p = re.fullmatch(r"(?:(\d+)\s*억)?\s*(?:(\d+)\s*만)?\s*(\d+)?", s)
    if not s or not p:
        return None
    return int(p.group(1) or 0) * 10**8 + int(p.group(2) or 0) * 10**4 + int(p.group(3) or 0)


# 게임 폰트에서 ㅁ/ㅇ 이 비슷해 생기는 오인식 (양쪽에 똑같이 적용해 비교)
CONFUSE = str.maketrans({"며": "여", "머": "어", "먹": "억", "뮝": "윙", "윔": "윙", "마": "아", "미": "이", "므": "으", "무": "우", "모": "오"})


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
        self.last = "아직 스캔 안 함"
        root.title("TW 제작 시세표")
        root.attributes("-topmost", True, "-alpha", 0.92)
        root.geometry("790x450")

        bar = ttk.Frame(root)
        bar.pack(fill="x", padx=4, pady=4)
        ttk.Button(bar, text="영역 지정", command=lambda: select_region(root, self.set_region)).pack(side="left")
        ttk.Button(bar, text="스캔", command=self.scan).pack(side="left", padx=4)
        self.auto = tk.BooleanVar(value=bool(self.cfg["auto"]))
        ttk.Checkbutton(bar, text="자동(3초)", variable=self.auto, command=self.save).pack(side="left")
        ttk.Button(bar, text="인식 확인", command=self.show_last).pack(side="left", padx=4)
        self.status = ttk.Label(bar, text="")
        self.status.pack(side="right")

        eq = ttk.Frame(root)
        eq.pack(fill="x", padx=4)
        cur = self.cfg["cur"].rsplit(" ", 1) if " " in self.cfg["cur"] else ["이클립스", "윙"]
        self.cb_slot = ttk.Combobox(eq, state="readonly", values=SLOTS, width=6)
        self.cb_tier = ttk.Combobox(eq, state="readonly", values=TIERS, width=12)
        self.cb_slot.set(cur[1] if cur[1] in SLOTS else SLOTS[0])
        self.cb_tier.set(cur[0] if cur[0] in TIERS else TIERS[0])
        for t, cb in (("부위", self.cb_slot), ("단계", self.cb_tier)):
            ttk.Label(eq, text=t).pack(side="left")
            cb.pack(side="left", padx=2)
            cb.bind("<<ComboboxSelected>>", self.pick_equip)
        ttk.Button(eq, text="제작비 DB 저장 →", command=self.db_save).pack(side="right")

        db = ttk.Frame(root)
        db.pack(side="right", fill="y", padx=4, pady=4)
        ttk.Label(db, text="제작비 DB (더블클릭 = 재료로 추가)").pack()
        self.dbv = ttk.Treeview(db, columns=("n", "p"), show="headings")
        for c, t, w in (("n", "장비", 120), ("p", "제작비", 110)):
            self.dbv.heading(c, text=t)
            self.dbv.column(c, width=w, anchor="w" if c == "n" else "e")
        self.dbv.pack(fill="y", expand=True)
        self.dbv.bind("<Double-1>", self.db_use)
        ttk.Button(db, text="DB 삭제", command=self.db_del).pack(fill="x")

        add = ttk.Frame(root)
        add.pack(fill="x", padx=4, pady=4)
        self.e_name, self.e_qty, self.e_price = ttk.Entry(add), ttk.Entry(add, width=5), ttk.Entry(add, width=12)
        for e, t in ((self.e_name, "재료"), (self.e_qty, "개수"), (self.e_price, "가격(비우면 스캔)")):
            ttk.Label(add, text=t).pack(side="left")
            e.pack(side="left", padx=2, fill="x", expand=e is self.e_name)
        self.e_qty.insert(0, "1")
        ttk.Button(add, text="추가/수정", command=self.add).pack(side="left")
        ttk.Button(add, text="삭제", command=self.remove).pack(side="left")

        cols = ("name", "qty", "price", "total")
        self.tv = ttk.Treeview(root, columns=cols, show="headings", height=10)
        for c, t, w in zip(cols, ("재료", "개수", "최저 개당가", "합계"), (170, 50, 120, 120)):
            self.tv.heading(c, text=t)
            self.tv.column(c, width=w, anchor="w" if c == "name" else "e")
        self.tv.pack(fill="both", expand=True, padx=4)
        self.tv.bind("<<TreeviewSelect>>", self.on_select)
        self.sum = tk.Label(root, text="", font=("맑은 고딕", 16, "bold"), fg="#fff", bg="#2b5797", anchor="e", padx=10, pady=6)
        self.sum.pack(fill="x", padx=4, pady=4)
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
        for n, p in self.cfg["db"].items():
            self.dbv.insert("", "end", iid=n, values=(n, won(p)))
        self.tv.delete(*self.tv.get_children())
        total, miss = 0, 0
        for i, (n, q) in enumerate(self.items()):
            p = self.price(n)
            miss += p is None
            total += q * (p or 0)
            self.tv.insert("", "end", iid=str(i), values=(n, q, won(p) + self.mark(n), won(None if p is None else q * p)))
        self.total = total
        self.sum.config(text=f"{self.cfg['cur']} 총 제작비: {won(total)}" + (f"  (미확인 {miss})" if miss else ""))

    def save(self):
        self.cfg["auto"] = int(self.auto.get())
        json.dump(self.cfg, open(CFG, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    def set_region(self, r):
        self.cfg["region"] = r
        self.save()
        self.status.config(text=f"영역 {r[2]}x{r[3]}")

    def pick_equip(self, _=None):
        self.cfg["cur"] = f"{self.cb_tier.get()} {self.cb_slot.get()}"
        self.cfg["recipes"].setdefault(self.cfg["cur"], [])
        self.save(); self.refresh()

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
            for e, v in ((self.e_name, n), (self.e_qty, q), (self.e_price, won(pr.get("p")) if pr.get("m") else "")):
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
        if price is not None:
            pr["p"] = price
        self.save(); self.refresh()

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

    def scan(self):
        if self.busy:
            return
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
            rows = [r for r in map(parse_row, texts) if r]
            names = {n for rec in self.cfg["recipes"].values() for n, _ in rec}
            hit = []
            sel = self.tv.selection()
            if sel and rows:  # 재료를 선택해 두면 이름 비교 없이 맨 위 행(최저가) 적용
                n = self.items()[int(sel[0])][0]
                self.cfg["prices"].setdefault(n, {})["p"] = rows[0][2]
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
                "\n".join(f"{a} | {b} | {won(c)}" for a, b, c in rows) + "\n\n[일치] " + ", ".join(hit) + \
                "\n\n캡처 이미지: scan_debug.png"
            msg = f"행 {len(rows)} / 일치 {len(hit)}"
            self.root.after(0, lambda: (self.save(), self.refresh()))
        except Exception as e:
            msg = "오류"
            self.last = f"오류: {e}"
            self.root.after(0, lambda e=e: messagebox.showerror("OCR", str(e)))
        self.root.after(0, lambda: (self.status.config(text=msg), setattr(self, "busy", False)))

    def tick(self):
        if self.auto.get() and self.cfg["region"]:
            self.scan()
        self.root.after(3000, self.tick)


if __name__ == "__main__":
    root = tk.Tk()
    App(root)
    root.mainloop()
