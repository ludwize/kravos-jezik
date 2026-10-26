"""Open the site in a real browser, walk through it and save screenshots + stats.

usage: shots.py <url> <out-dir>
Runs locally against a dev server or on a GitHub runner against the live site.
"""
import asyncio
import json
import sys
import urllib.request

from playwright.async_api import async_playwright

URL, OUT = sys.argv[1].rstrip("/") + "/", sys.argv[2]
LOG = []


def log(*a):
    LOG.append(" ".join(str(x) for x in a))
    print(*a, flush=True)


async def click_main_until(pg, name, stop_texts, limit=60, every=4):
    """Press the big footer button until its text is one of stop_texts."""
    for i in range(limit):
        btn = pg.locator("#main-btn")
        if not await btn.count():
            await pg.wait_for_timeout(300)
            continue
        txt = (await btn.inner_text()).strip().upper()
        if txt in stop_texts:
            return i
        if i % every == 0:
            await pg.screenshot(path=f"{OUT}/{name}-{i:02d}.png")
        await btn.click()
        await pg.wait_for_timeout(250)
    log(f"{name}: did not reach {stop_texts}")
    return limit


async def walk(b, tag, w, h, scheme):
    errs = []
    pg = await b.new_page(viewport={"width": w, "height": h}, color_scheme=scheme)
    pg.on("pageerror", lambda e: errs.append(f"pageerror: {e}"))
    pg.on("console", lambda m: errs.append(f"console {m.type}: {m.text}") if m.type == "error" else None)
    await pg.goto(URL + "?test=1#/", wait_until="networkidle")
    await pg.evaluate("localStorage.clear()")
    await pg.goto(URL + "?test=1#/", wait_until="networkidle")
    await pg.wait_for_timeout(800)
    await pg.screenshot(path=f"{OUT}/{tag}-01-path.png")

    # first lesson
    await pg.locator(".node[data-id]").first.click()
    await pg.wait_for_timeout(400)
    await pg.screenshot(path=f"{OUT}/{tag}-02-sheet.png")
    await pg.locator(".modal [data-act=start]").click()
    await pg.wait_for_timeout(500)
    n = await click_main_until(pg, f"{tag}-03-lesson", {"NASTAVI"})
    log(f"{tag}: lesson took {n} presses")
    await pg.screenshot(path=f"{OUT}/{tag}-04-done.png")
    await pg.locator("#main-btn").click()
    await pg.wait_for_timeout(500)
    await pg.screenshot(path=f"{OUT}/{tag}-05-path-after.png")

    # switch language
    await pg.locator("[data-act=langs]:visible").first.click()
    await pg.wait_for_timeout(400)
    await pg.screenshot(path=f"{OUT}/{tag}-06-langs.png")
    await pg.locator("[data-act=setlang][data-l=es]").first.click()
    await pg.wait_for_timeout(800)
    await pg.screenshot(path=f"{OUT}/{tag}-07-es-path.png")

    # a story (locked -> "Ipak počni")
    await pg.locator(".node[data-id^='p-']").first.click()
    await pg.wait_for_timeout(400)
    await pg.locator(".modal [data-act=start]").click()
    await pg.wait_for_timeout(500)
    n = await click_main_until(pg, f"{tag}-08-story", {"ZAVRŠI PRIČU"}, every=5)
    await pg.screenshot(path=f"{OUT}/{tag}-09-story-end.png")
    await pg.locator("#main-btn").click()
    await pg.wait_for_timeout(400)
    await pg.screenshot(path=f"{OUT}/{tag}-10-story-done.png")
    await pg.locator("#main-btn").click()
    await pg.wait_for_timeout(400)

    # guide, review, words, profile, settings
    await pg.goto(URL + "?test=1#/vodic/0"); await pg.wait_for_timeout(500)
    await pg.screenshot(path=f"{OUT}/{tag}-11-guide.png")
    await pg.goto(URL + "?test=1#/ponavljanje"); await pg.wait_for_timeout(500)
    await pg.screenshot(path=f"{OUT}/{tag}-12-review.png")
    await pg.locator(".main [data-act=review]").first.click()
    await pg.wait_for_timeout(500)
    await click_main_until(pg, f"{tag}-13-rev", {"NASTAVI", "NAZAD"}, every=6)
    await pg.locator("#main-btn").click(); await pg.wait_for_timeout(400)
    await pg.goto(URL + "?test=1#/recnik"); await pg.wait_for_timeout(500)
    await pg.fill("#wq", "hvala" if False else "gracias")
    await pg.wait_for_timeout(300)
    await pg.screenshot(path=f"{OUT}/{tag}-14-words.png")
    await pg.goto(URL + "?test=1#/profil"); await pg.wait_for_timeout(500)
    await pg.screenshot(path=f"{OUT}/{tag}-15-profile.png", full_page=True)
    await pg.goto(URL + "?test=1#/podesavanja"); await pg.wait_for_timeout(500)
    await pg.screenshot(path=f"{OUT}/{tag}-16-settings.png", full_page=True)

    # normal mode: answer the first choice question with the first option and look at the feedback
    await pg.goto(URL + "#/", wait_until="networkidle"); await pg.wait_for_timeout(500)
    await pg.locator(".node[data-id]").first.click(); await pg.wait_for_timeout(300)
    await pg.locator(".modal [data-act=start]").click(); await pg.wait_for_timeout(400)
    for _ in range(10):
        if await pg.locator(".opt[data-act=choose]").count():
            break
        await pg.locator("#main-btn").click(); await pg.wait_for_timeout(250)
    await pg.screenshot(path=f"{OUT}/{tag}-17-pick.png")
    await pg.locator(".opt[data-act=choose]").first.click()
    await pg.locator("#main-btn").click(); await pg.wait_for_timeout(400)
    await pg.screenshot(path=f"{OUT}/{tag}-18-feedback.png")
    log(f"{tag} errors: {errs}")
    await pg.close()


async def main():
    for lang in ("sl", "es"):
        try:
            c = json.load(urllib.request.urlopen(URL + f"courses/{lang}.json"))
            clips = c.get("audio", {})
            log(f"{lang}: {len(c['units'])} units, {len(clips)} clips")
            if clips:
                t, k = next(iter(clips.items()))
                r = urllib.request.urlopen(URL + f"audio/{lang}/{k}.ogg")
                log(f"  audio '{t}' -> HTTP {r.status}, {len(r.read())} bytes, {r.headers.get('Content-Type')}")
        except Exception as e:  # noqa: BLE001
            log(f"{lang}: fetch failed {e}")
    async with async_playwright() as p:
        b = await p.chromium.launch()
        await walk(b, "desk", 1366, 900, "dark")
        await walk(b, "mob", 390, 844, "light")
        await b.close()
    open(f"{OUT}/stats.txt", "w").write("\n".join(LOG) + "\n")


asyncio.run(main())
