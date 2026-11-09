// Renders docs/hero(-tr).png and docs/social-preview(-tr).png from the HTML templates with headless Chromium.
// Usage (from the repo root): npm install playwright@1 && npx playwright install --with-deps chromium && node docs/assets/render.mjs
import { chromium } from "playwright";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const jobs = [
  { html: "hero.html", out: "../hero.png", width: 1600, height: 1000 },
  { html: "hero.html?lang=tr", out: "../hero-tr.png", width: 1600, height: 1000 },
  { html: "social-preview.html", out: "../social-preview.png", width: 1280, height: 640 },
  { html: "social-preview.html?lang=tr", out: "../social-preview-tr.png", width: 1280, height: 640 },
];
const browser = await chromium.launch();
for (const j of jobs) {
  const page = await browser.newPage({ viewport: { width: j.width, height: j.height }, deviceScaleFactor: 1 });
  const [file, query] = j.html.split("?");
  await page.goto("file://" + path.join(here, file) + (query ? "?" + query : ""));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForLoadState("networkidle");
  const broken = await page.evaluate(() => [...document.images].filter((i) => !i.complete || i.naturalWidth === 0).map((i) => i.src));
  if (broken.length) throw new Error(j.html + ": missing images " + broken.join(", "));
  await page.screenshot({ path: path.join(here, j.out), clip: { x: 0, y: 0, width: j.width, height: j.height } });
  console.log("rendered", j.out);
  await page.close();
}
await browser.close();
