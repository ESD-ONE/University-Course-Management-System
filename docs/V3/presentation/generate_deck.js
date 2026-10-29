// UCMS V3 presentation generator — pptxgenjs structured deck.
// Run: node generate_deck.js   (from docs/V3/presentation/)
const pptxgen = require("pptxgenjs");
const fs = require("fs");
const path = require("path");
const { applyTheme } = require(
  "/Users/sachinthadilshan/.claude/skills/synced/99056b36-2cc9-4f1d-80d3-85a389925921_26bc7d5e-120e-4f97-8191-0bf08e7eef89/pptx/scripts/apply_theme.js"
);

const DIAG = path.join(__dirname, "..", "diagrams");
const OUT = path.join(__dirname, "UCMS-Architecture-DevOps-Security.pptx");
const NARRATION_PATH = path.join(__dirname, "narration-script.txt");

// ---------------------------------------------------------------- theme
const THEME = {
  name: "UCMS Teal",
  headFontFace: "Cambria",
  bodyFontFace: "Calibri",
  colors: {
    dk1: "16252A",
    lt1: "FFFFFF",
    dk2: "1F5F5B",
    lt2: "EDF4F3",
    accent1: "2F7D77",
    accent2: "C98A3B",
    accent3: "3B4F56",
    accent4: "8AA6A3",
    accent5: "0E3E3B",
    accent6: "F6EBDD",
    hlink: "2F7D77",
    folHlink: "0E3E3B",
  },
};

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.333 x 7.5
const C = pres.SchemeColor;
const W = 13.333, H = 7.5;

const narration = [];
function note(slide, slideNo, title, text) {
  slide.addNotes(text);
  narration.push(`SLIDE ${slideNo} — ${title}\n${text}\n`);
}

// ---------------------------------------------------------------- layouts
pres.defineSlideMaster({
  title: "TITLE",
  background: { color: THEME.colors.dk2 },
  objects: [
    { rect: { x: 0, y: 0, w: W, h: H, fill: { color: THEME.colors.dk2 } } },
    { rect: { x: 0, y: 4.55, w: W, h: 0.03, fill: { color: THEME.colors.accent2 }, line: { type: "none" } } },
    {
      placeholder: {
        options: { name: "title", type: "title", x: 0.9, y: 2.55, w: 11.5, h: 1.5, fontFace: THEME.headFontFace,
          fontSize: 40, bold: true, color: "FFFFFF", align: "left", valign: "bottom" },
        text: "",
      },
    },
    {
      placeholder: {
        options: { name: "body", type: "body", x: 0.9, y: 4.75, w: 10.8, h: 1.2, fontFace: THEME.bodyFontFace,
          fontSize: 17, color: THEME.colors.accent6, align: "left", valign: "top" },
        text: "",
      },
    },
    { text: { text: "University Course Management System", options: { x: 0.9, y: 0.55, w: 8, h: 0.4, fontFace: THEME.bodyFontFace, fontSize: 12, color: THEME.colors.accent4, charSpacing: 1, isTextBox: true } } },
  ],
});

pres.defineSlideMaster({
  title: "SECTION",
  background: { color: THEME.colors.dk2 },
  objects: [
    { rect: { x: 0, y: 0, w: W, h: H, fill: { color: THEME.colors.dk2 } } },
    { text: { text: "", options: { name: "secnum", x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72, bold: true, color: THEME.colors.accent2, isTextBox: true } } },
    {
      placeholder: {
        options: { name: "title", type: "title", x: 0.9, y: 3.55, w: 11.2, h: 1.3, fontFace: THEME.headFontFace,
          fontSize: 34, bold: true, color: "FFFFFF", align: "left", valign: "top" },
        text: "",
      },
    },
    {
      placeholder: {
        options: { name: "body", type: "body", x: 0.9, y: 4.75, w: 10.6, h: 1.1, fontFace: THEME.bodyFontFace,
          fontSize: 16, color: THEME.colors.accent6, align: "left", valign: "top" },
        text: "",
      },
    },
  ],
  slideNumber: { x: 12.25, y: 6.98, color: THEME.colors.accent4, fontFace: THEME.bodyFontFace, fontSize: 10 },
});

pres.defineSlideMaster({
  title: "CONTENT",
  background: { color: "FFFFFF" },
  objects: [
    { rect: { x: 0, y: 0, w: W, h: 1.05, fill: { color: THEME.colors.lt2 } } },
    {
      placeholder: {
        options: { name: "title", type: "title", x: 0.6, y: 0.18, w: 11.6, h: 0.7, fontFace: THEME.headFontFace,
          fontSize: 26, bold: true, color: THEME.colors.dk2, align: "left", valign: "middle" },
        text: "",
      },
    },
    { text: { text: "UCMS", options: { x: 12.1, y: 0.32, w: 1, h: 0.4, fontFace: THEME.bodyFontFace, fontSize: 11, bold: true, color: THEME.colors.accent1, align: "right", isTextBox: true } } },
    {
      placeholder: {
        options: { name: "body", type: "body", x: 0.6, y: 1.3, w: 12.1, h: 5.7, fontFace: THEME.bodyFontFace,
          fontSize: 15, color: THEME.colors.dk1, align: "left", valign: "top" },
        text: "",
      },
    },
  ],
  slideNumber: { x: 12.25, y: 6.98, color: THEME.colors.accent4, fontFace: THEME.bodyFontFace, fontSize: 10 },
});

pres.defineSlideMaster({
  title: "BLANK_CONTENT",
  background: { color: "FFFFFF" },
  objects: [
    { rect: { x: 0, y: 0, w: W, h: 1.05, fill: { color: THEME.colors.lt2 } } },
    {
      placeholder: {
        options: { name: "title", type: "title", x: 0.6, y: 0.18, w: 11.6, h: 0.7, fontFace: THEME.headFontFace,
          fontSize: 26, bold: true, color: THEME.colors.dk2, align: "left", valign: "middle" },
        text: "",
      },
    },
    { text: { text: "UCMS", options: { x: 12.1, y: 0.32, w: 1, h: 0.4, fontFace: THEME.bodyFontFace, fontSize: 11, bold: true, color: THEME.colors.accent1, align: "right", isTextBox: true } } },
  ],
  slideNumber: { x: 12.25, y: 6.98, color: THEME.colors.accent4, fontFace: THEME.bodyFontFace, fontSize: 10 },
});

// ---------------------------------------------------------------- helpers
function addBullets(slide, items, opts) {
  // items: array of {text, level, bold, color}
  const paras = items.map((it, i) => ({
    text: it.text,
    options: {
      bullet: it.bullet === false ? false : { code: it.level ? "2013" : "25CF" },
      indentLevel: it.level || 0,
      bold: !!it.bold,
      color: it.color || THEME.colors.dk1,
      fontSize: it.fontSize || opts.fontSize || 15,
      breakLine: i !== items.length - 1,
      paraSpaceAfter: it.level ? 6 : 10,
    },
  }));
  slide.addText(paras, Object.assign({ x: opts.x, y: opts.y, w: opts.w, h: opts.h, valign: "top", fontFace: THEME.bodyFontFace }, opts.box || {}));
}

function fitImage(file) {
  const dims = IMG_DIMS[file];
  return dims.w / dims.h;
}

const IMG_DIMS = {
  "01-microservices-architecture.png": { w: 2768, h: 1746 },
  "02-devops-jenkins-pipeline.png": { w: 2768, h: 1522 },
  "03a-communication-sync.png": { w: 2768, h: 746 },
  "03b-communication-async.png": { w: 2768, h: 838 },
  "04-event-flow-sequence.png": { w: 2768, h: 1066 },
  "05-er-identity-access.png": { w: 2060, h: 1392 },
  "06-er-base.png": { w: 1424, h: 658 },
  "07-er-academic.png": { w: 2266, h: 1026 },
  "08-er-enrollment.png": { w: 1162, h: 742 },
  "09-er-grading-notification-analytics.png": { w: 2134, h: 1068 },
  "10-er-cross-schema.png": { w: 2768, h: 1818 },
  "11-tx-enroll.png": { w: 2206, h: 1664 },
  "12-tx-drop-promote.png": { w: 2018, h: 1468 },
  "13-tx-grading-notification.png": { w: 2768, h: 1188 },
  "14-deployment-architecture.png": { w: 2768, h: 666 },
  "15-load-balancing.png": { w: 2768, h: 754 },
  "16-rolling-deploy.png": { w: 2446, h: 1602 },
};

function addFittedImage(slide, file, box) {
  const ratio = fitImage(file);
  let w = box.w, h = w / ratio;
  if (h > box.h) {
    h = box.h;
    w = h * ratio;
  }
  const x = box.x + (box.w - w) / 2;
  const y = box.y + (box.h - h) / 2;
  slide.addImage({ path: path.join(DIAG, file), x, y, w, h });
}

function captionBox(slide, text, x, y, w) {
  slide.addText(text, {
    x, y, w, h: 0.5, fontFace: THEME.bodyFontFace, fontSize: 11.5, italic: true, color: THEME.colors.accent3,
    align: "center", valign: "top", isTextBox: true, margin: 0,
  });
}

function statCard(slide, x, y, w, h, num, label, color) {
  slide.addShape("roundRect", { x, y, w, h, rectRadius: 0.08, fill: { color: THEME.colors.lt2 }, line: { type: "none" },
    shadow: { type: "outer", color: "888888", opacity: 0.25, blur: 4, offset: 2, angle: 90 } });
  slide.addText(num, { x, y: y + 0.12, w, h: h * 0.55, align: "center", valign: "bottom", fontFace: THEME.headFontFace,
    fontSize: 32, bold: true, color: color || THEME.colors.dk2, isTextBox: true, margin: 0 });
  slide.addText(label, { x: x + 0.1, y: y + h * 0.6, w: w - 0.2, h: h * 0.38, align: "center", valign: "top",
    fontFace: THEME.bodyFontFace, fontSize: 11, color: THEME.colors.accent3, isTextBox: true, margin: 0 });
}

function numberedStep(slide, x, y, w, h, n, title, desc, color) {
  slide.addShape("ellipse", { x, y, w: 0.42, h: 0.42, fill: { color: color || THEME.colors.dk2 }, line: { type: "none" } });
  slide.addText(String(n), { x, y, w: 0.42, h: 0.42, align: "center", valign: "middle", fontFace: THEME.headFontFace,
    fontSize: 16, bold: true, color: "FFFFFF", isTextBox: true, margin: 0 });
  slide.addText(title, { x: x + 0.55, y: y - 0.04, w: w - 0.55, h: 0.3, fontFace: THEME.bodyFontFace, fontSize: 13.5,
    bold: true, color: THEME.colors.dk1, isTextBox: true, margin: 0 });
  slide.addText(desc, { x: x + 0.55, y: y + 0.26, w: w - 0.55, h: h - 0.26, fontFace: THEME.bodyFontFace, fontSize: 11.5,
    color: THEME.colors.accent3, isTextBox: true, margin: 0, valign: "top" });
}

let slideNo = 0;
function sNo() { slideNo += 1; return slideNo; }

// =====================================================================
// 1. TITLE
// =====================================================================
{
  const s = pres.addSlide({ masterName: "TITLE" });
  s.addText("Architecture, DevOps, Data & Security", { placeholder: "title" });
  s.addText(
    "How UCMS is built: microservices and why, the Jenkins CI/CD pipeline, how services talk to each other, " +
    "the transactional and relational design inside each schema, entity relationships, load balancing, " +
    "deployment architecture, rate limiting, security hardening, and the test suites that verify all of it.",
    { placeholder: "body" }
  );
  s.addText("Final Project Presentation  ·  2026", { x: 0.9, y: 6.55, w: 8, h: 0.4, fontFace: THEME.bodyFontFace,
    fontSize: 13, color: THEME.colors.accent4, isTextBox: true, margin: 0 });
  note(s, sNo(), "Title",
    "Good morning. Today I'll walk through the architecture of our University Course Management System — " +
    "not just what we built, but why we built it this way. We'll cover the microservices design and the " +
    "reasoning behind it, the DevOps pipeline that builds and deploys it, how the seven services actually " +
    "talk to each other, the transactional design inside the database, the entity relationships across " +
    "schemas, and finally how we load-balance, secure, rate-limit and test the whole system.");
}

// =====================================================================
// 2. AGENDA
// =====================================================================
{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("Agenda", { placeholder: "title" });
  const items = [
    ["1", "System & Microservices Architecture", "Why microservices, why one schema per service"],
    ["2", "DevOps & CI/CD", "The Jenkins pipeline, and why each stage exists"],
    ["3", "Service Communication", "Synchronous REST calls vs. asynchronous events"],
    ["4", "Data, Transactions & Schema Design", "What happens inside one database transaction"],
    ["5", "Entity Relationships", "The tables in every schema and how they connect"],
    ["6", "Deployment, Scaling & Reliability", "Containers, load balancing, zero-downtime deploys"],
    ["7", "Security & Rate Limiting", "What protects the system and its users"],
    ["8", "Testing & Quality", "239 unit tests through to a live browser check"],
  ];
  const colW = 5.9, gap = 0.3, rowH = 1.28;
  items.forEach((it, i) => {
    const col = i < 4 ? 0 : 1;
    const row = i % 4;
    const x = 0.6 + col * (colW + gap);
    const y = 1.35 + row * rowH;
    s.addText(it[0], { x, y, w: 0.6, h: 0.55, fontFace: THEME.headFontFace, fontSize: 24, bold: true,
      color: THEME.colors.accent2, isTextBox: true, margin: 0 });
    s.addText(it[1], { x: x + 0.65, y: y - 0.03, w: colW - 0.65, h: 0.35, fontFace: THEME.bodyFontFace,
      fontSize: 15.5, bold: true, color: THEME.colors.dk2, isTextBox: true, margin: 0 });
    s.addText(it[2], { x: x + 0.65, y: y + 0.3, w: colW - 0.65, h: 0.55, fontFace: THEME.bodyFontFace,
      fontSize: 12, color: THEME.colors.accent3, isTextBox: true, margin: 0, valign: "top" });
  });
  note(s, sNo(), "Agenda",
    "Here's the roadmap. We'll move from the big picture down to the details: architecture first, then how " +
    "we build and ship it, then how the pieces talk to each other, then the database design underneath, " +
    "then how it's deployed and kept reliable, how it's secured, and finally how we know all of this " +
    "actually works — through testing.");
}

// =====================================================================
// SECTION 1 — Architecture
// =====================================================================
pres.addSection({ title: "1. System & Microservices Architecture" });
{
  const s = pres.addSlide({ masterName: "SECTION", sectionTitle: "1. System & Microservices Architecture" });
  s.addText("01", { placeholder: undefined, x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72,
    bold: true, color: THEME.colors.accent2, isTextBox: true, margin: 0 });
  s.addText("System & Microservices Architecture", { placeholder: "title" });
  s.addText("Why we split UCMS into seven independent services, and why each gets its own database schema.",
    { placeholder: "body" });
  note(s, sNo(), "Section 1",
    "Let's start with the architecture. UCMS is built as seven independent microservices sitting behind an " +
    "API gateway, each with its own database schema. The question worth answering first isn't what the " +
    "services are — it's why we chose this shape at all.");
}

{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("Why Microservices?", { placeholder: "title" });
  addBullets(s, [
    { text: "Independent deployability — Enrollment, Grading, Notification and Analytics were added as four whole new services without touching Identity, Base or Academic Structure. Each is its own Maven module, its own Docker image, its own release.", bold: false },
    { text: "Failure isolation — if one service is down, the gateway's circuit breaker returns a clean 503 for just that feature instead of the whole app breaking. Proven in testing: we killed a service replica mid-traffic and 40 out of 40 requests still succeeded, served by the other replica.", bold: false },
    { text: "Independent scaling — Enrollment and Notification run 2 replicas (enrollment windows spike traffic; notifications hold long-lived live connections); Base and Analytics stay at 1 replica (low traffic). We size resources per capability, not per monolith.", bold: false },
    { text: "Bounded business capabilities — Identity & Access, Base (faculties/departments/scales), Academic Structure (programs/courses/offerings), Enrollment, Grading, Notification, Analytics: each one maps to a real, ownable piece of the university's domain.", bold: false },
    { text: "This is the module structure actually in the codebase today — services/identity-access-service, services/enrollment-service, and so on — not a diagram we drew after the fact.", bold: true },
  ], { x: 0.6, y: 1.3, w: 12.1, h: 5.7, fontSize: 14.5 });
  note(s, sNo(), "Why Microservices",
    "Why microservices instead of one large application? Five concrete reasons, all things we can point to " +
    "in the running system rather than theory. First, independent deployability — we added four entire new " +
    "services over the life of this project without redeploying or even recompiling the first three. " +
    "Second, failure isolation — we actually tested this: we killed a running service container in the " +
    "middle of live traffic, and every single request still succeeded because the load balancer and circuit " +
    "breaker routed around it. Third, independent scaling — the enrollment service and the notification " +
    "service run two copies each, because enrollment traffic spikes hard at the start of a term and " +
    "notification holds many long-lived connections, while a service like analytics barely needs more than " +
    "one copy. Fourth, each service maps to one bounded piece of how a university actually works. And " +
    "finally, this isn't a whiteboard diagram — these are the actual folders and Docker images in our " +
    "repository.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Microservices Architecture", { placeholder: "title" });
  addFittedImage(s, "01-microservices-architecture.png", { x: 0.4, y: 1.2, w: 12.5, h: 5.6 });
  captionBox(s, "7 services behind one gateway · one PostgreSQL instance, one schema per service · RabbitMQ for events · Redis for cache, limits & revocation · MinIO for files", 0.4, 6.8, 11.5);
  note(s, sNo(), "Microservices Architecture Diagram",
    "Here's the whole system at a glance. Every request from the browser goes through nginx, then the API " +
    "gateway, which routes to one of seven services. Each service owns its own schema inside one shared " +
    "PostgreSQL database — I'll explain why it's one database but separate schemas on the next slide. " +
    "Underneath, RabbitMQ carries events between services that don't need to talk synchronously, Redis " +
    "backs the cache, the rate limiter and logout revocation, and MinIO stores uploaded files like " +
    "assignment submissions.");
}

{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("Why One Schema Per Service?", { placeholder: "title" });
  addBullets(s, [
    { text: "One shared PostgreSQL instance (simple to back up, monitor and patch) + one schema per service (real business isolation) — the best of both, without running seven separate database servers.", bold: false },
    { text: "Enforced by database grants, not convention — each service connects with its own schema-scoped role (infra/init-schemas.sh). The enrollment service's database role literally cannot SELECT from the identity_access schema, even if the code tried to.", bold: false },
    { text: "No foreign keys across schemas — ever. A reference to another service's row is a plain uuid column with a comment, never a real FK. This is deliberate: a cross-schema FK would force every write to check a constraint in someone else's schema, which defeats the whole point of the boundary.", bold: false },
    { text: "“Who owns this data?” always has a one-line answer — if a table lives under academic.*, Academic Structure owns it, full stop.", bold: false },
    { text: "Every mutable table across all 7 schemas carries the same 7-column audit block: version, created_at/by, updated_at/by, deleted_at/by — one standard, enforced everywhere, not seven different conventions.", bold: true },
  ], { x: 0.6, y: 1.3, w: 12.1, h: 5.7, fontSize: 14.5 });
  note(s, sNo(), "Why One Schema Per Service",
    "Why not one shared schema, or seven separate database servers? We chose the middle path: one Postgres " +
    "instance — so there's only one thing to back up and monitor — but each service gets its own schema and " +
    "its own database login that can only see its own schema. This isn't just a naming convention, it's " +
    "enforced by Postgres grants. And we never create a real foreign key across two schemas — if enrollment " +
    "needs to reference a student, it stores a plain UUID with a comment, not a constraint, because a real " +
    "cross-schema foreign key would mean every write in one service has to coordinate with another " +
    "service's schema, which breaks the whole point of having separate services. And one more thing we " +
    "enforce everywhere: every table that can change carries the same seven audit columns, so 'who changed " +
    "this and when' is always answerable the same way, anywhere in the system.");
}

// =====================================================================
// SECTION 2 — DevOps
// =====================================================================
pres.addSection({ title: "2. DevOps & CI/CD" });
{
  const s = pres.addSlide({ masterName: "SECTION", sectionTitle: "2. DevOps & CI/CD" });
  s.addText("02", { x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72, bold: true,
    color: THEME.colors.accent2, isTextBox: true, margin: 0 });
  s.addText("DevOps & CI/CD", { placeholder: "title" });
  s.addText("How a git push turns into a running, zero-downtime deployment — and why each stage exists.",
    { placeholder: "body" });
  note(s, sNo(), "Section 2",
    "Next, the DevOps side — how code actually gets from a developer's laptop into production, and why we " +
    "built the pipeline the way we did.");
}

{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("Why This Pipeline?", { placeholder: "title" });
  addBullets(s, [
    { text: "One pipeline, every branch, every push — Checkout, Build & Test (239 tests), Package, Frontend Build, Security Scan. A failing test or a found vulnerability turns the whole pipeline red before anything reaches a server.", bold: false },
    { text: "Deploy only runs on develop / release / main — a feature branch still gets the full test and security verification, with zero risk of accidentally going live.", bold: false },
    { text: "Jenkins runs on the same host it deploys to — no SSH hop, no separate deploy credential, removes a whole class of “the pipeline can't reach the server” failures.", bold: false },
    { text: "The frontend build runs inside a throwaway node:20 container, because the Jenkins host itself has no Node toolchain — the build environment is reproducible and never pollutes the host.", bold: false },
    { text: "Deploy changed from “docker compose up --build” (a brief outage every release) to a rolling deploy script — new replicas start and pass health checks before the old ones stop. Verified: 686 of 686 requests succeeded through a live redeploy.", bold: true },
  ], { x: 0.6, y: 1.3, w: 12.1, h: 5.7, fontSize: 14.3 });
  note(s, sNo(), "Why This Pipeline",
    "A few deliberate choices here. Every single push, on every branch, runs the full build and test suite " +
    "and a security scan — not just the branches we deploy. That way a feature branch is already known-good " +
    "before it's ever merged. Only three branches actually deploy: develop, release and main. Jenkins lives " +
    "on the exact same server it deploys to, which sounds simple but removes an entire category of bugs " +
    "where a deploy script can't reach the target machine or the right credentials aren't available. And the " +
    "biggest operational change we made: instead of just restarting containers — which causes a brief outage " +
    "every single release — we now start the new version alongside the old one, wait until it's actually " +
    "healthy, and only then retire the old version. We proved this works: during a live redeploy under " +
    "constant traffic, 686 out of 686 requests succeeded. Zero dropped.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Jenkins CI/CD Pipeline", { placeholder: "title" });
  addFittedImage(s, "02-devops-jenkins-pipeline.png", { x: 0.3, y: 1.15, w: 12.7, h: 5.9 });
  note(s, sNo(), "Jenkins Pipeline Diagram",
    "Here's the pipeline end to end. A push triggers Jenkins, which checks out the code, builds every " +
    "service and runs all 239 tests, packages eight Spring Boot jars, builds the Angular frontend, and runs " +
    "a security scan with Trivy and npm audit. If it's a feature branch, that's the end — verified, not " +
    "deployed. If it's develop, release or main, the pipeline continues: it copies the build artifacts, " +
    "scans the container images that are about to go live, then hands off to our rolling deploy script, " +
    "which only replaces old containers once new ones are confirmed healthy.");
}

// =====================================================================
// SECTION 3 — Communication
// =====================================================================
pres.addSection({ title: "3. Service Communication" });
{
  const s = pres.addSlide({ masterName: "SECTION", sectionTitle: "3. Service Communication" });
  s.addText("03", { x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72, bold: true,
    color: THEME.colors.accent2, isTextBox: true, margin: 0 });
  s.addText("Service Communication", { placeholder: "title" });
  s.addText("Two patterns, used deliberately for different jobs: synchronous REST calls, and asynchronous events.",
    { placeholder: "body" });
  note(s, sNo(), "Section 3",
    "Now, how do seven independent services actually talk to each other? We use two different patterns, " +
    "and which one applies depends on whether the caller needs an answer right now.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Synchronous Communication — REST", { placeholder: "title" });
  addFittedImage(s, "03a-communication-sync.png", { x: 0.3, y: 1.15, w: 12.7, h: 3.9 });
  addBullets(s, [
    { text: "Used when the caller needs the answer immediately to finish its own request — e.g. Enrollment must know if a course offering is still open before it can accept a student.", fontSize: 12.5 },
    { text: "Every service calls another using a Keycloak service token (client_credentials grant) — each service has its own client and only the minimum realm roles it needs. Least privilege, not one shared secret.", fontSize: 12.5 },
    { text: "The Gateway independently re-verifies every token at the edge as a backstop, even though each service checks again itself — defence in depth.", fontSize: 12.5 },
  ], { x: 0.5, y: 5.15, w: 11.6, h: 1.6 });
  note(s, sNo(), "Synchronous Communication",
    "This is plain REST over HTTP, used whenever a service needs an answer right now to complete its own " +
    "work — for example, before Enrollment can accept a student into a course, it has to ask Academic " +
    "Structure whether that course offering still has seats and is actually open. Every one of these calls " +
    "is authenticated with its own Keycloak service token — not one shared secret for everything, but a " +
    "separate client per service, each holding only the specific roles it actually needs. Notification, for " +
    "instance, is allowed to call Identity but not Academic Structure — and we have an automated test that " +
    "proves exactly that boundary is enforced.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Asynchronous Communication — Events", { placeholder: "title" });
  addFittedImage(s, "03b-communication-async.png", { x: 0.3, y: 1.15, w: 12.7, h: 4.2 });
  addBullets(s, [
    { text: "Used when the caller doesn't need to wait — e.g. Enrollment shouldn't block a student's enrollment on Notification or Analytics being available.", fontSize: 12.5 },
    { text: "RabbitMQ topic exchange (ucms.events); consumers bind their own queue with their own routing pattern — a new consumer can be added without touching the publisher.", fontSize: 12.5 },
    { text: "At-least-once delivery, made safe by idempotency (dedupe by event id) and dead-letter queues for anything that keeps failing.", fontSize: 12.5 },
  ], { x: 0.5, y: 5.45, w: 11.6, h: 1.3 });
  note(s, sNo(), "Asynchronous Communication",
    "The second pattern is asynchronous, over RabbitMQ. We use this whenever the caller doesn't need to wait " +
    "for the answer — enrolling a student shouldn't be blocked on whether the notification service happens " +
    "to be up at that exact moment. Events are published to one topic exchange, and each consumer — " +
    "Notification, Analytics — binds its own queue with its own routing pattern. That means we can add a " +
    "brand new consumer of these events without changing a single line in the service that publishes them. " +
    "Because RabbitMQ guarantees at-least-once delivery, not exactly-once, every consumer has to be " +
    "idempotent — Notification, for example, checks whether it's already handled this exact event id before " +
    "creating anything.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Example — A Student Enrolls, End to End", { placeholder: "title" });
  addFittedImage(s, "04-event-flow-sequence.png", { x: 0.9, y: 1.15, w: 11.5, h: 5.95 });
  note(s, sNo(), "Event Flow Example",
    "Let's make this concrete with one real example: a student enrolling in a course. The browser calls the " +
    "gateway, which verifies the token, checks the rate limit, and forwards to an enrollment service " +
    "replica. Enrollment synchronously asks Academic Structure whether the offering has room — that's the " +
    "synchronous path. It then runs one local database transaction to record the enrollment, and only after " +
    "that transaction commits does it publish an event onto RabbitMQ. From there, Notification and Analytics " +
    "each independently pick up that event — Notification to push a live update and an email, Analytics to " +
    "mark its dashboards as needing a recompute. Enrollment itself never waits for either of them.");
}

// =====================================================================
// SECTION 4 — Transactions
// =====================================================================
pres.addSection({ title: "4. Data, Transactions & Schema Design" });
{
  const s = pres.addSlide({ masterName: "SECTION", sectionTitle: "4. Data, Transactions & Schema Design" });
  s.addText("04", { x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72, bold: true,
    color: THEME.colors.accent2, isTextBox: true, margin: 0 });
  s.addText("Data, Transactions & Schema Design", { placeholder: "title" });
  s.addText("What actually happens inside one database transaction — and what happens when a change needs to cross a schema boundary.",
    { placeholder: "body" });
  note(s, sNo(), "Section 4",
    "This section was specifically requested: let's go inside the database and look at what a transaction " +
    "actually does, step by step, and how we keep data consistent both within one schema and across " +
    "schemas.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Transaction 1 — Enrolling a Student", { placeholder: "title" });
  addFittedImage(s, "11-tx-enroll.png", { x: 1.6, y: 1.1, w: 10.1, h: 6.0 });
  note(s, sNo(), "Transaction — Enroll",
    "Here's a real transaction, exactly as it runs in the enrollment schema. It opens, takes a Postgres " +
    "advisory lock keyed on the course offering id — this is important: we don't have a seat-counter row to " +
    "lock, because the capacity actually lives in a different service's schema, so an advisory lock is how " +
    "we safely serialize two students trying to grab the last seat in the same course at the same moment. It " +
    "checks for an existing enrollment, counts how many seats are taken, and either enrolls or waitlists the " +
    "student, writes an audit log row, and commits. Only after that commit succeeds does it publish the " +
    "event — if anything fails before the commit, the whole thing rolls back as if it never happened, and no " +
    "event is ever sent for something that didn't actually happen.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Transaction 2 — Drop & Automatic Waitlist Promotion", { placeholder: "title" });
  addFittedImage(s, "12-tx-drop-promote.png", { x: 2.0, y: 1.1, w: 9.3, h: 6.0 });
  note(s, sNo(), "Transaction — Drop and Promote",
    "This one shows why doing this as a single transaction matters. When a student drops a course, and that " +
    "drop frees a seat, the very next waitlisted student is automatically promoted to enrolled — all inside " +
    "the same transaction as the drop itself. Because it's one transaction, there's no window where the " +
    "system could crash between the drop and the promotion and leave a seat open with nobody properly " +
    "promoted into it. It either both happened, or neither did.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Transaction 3 — Grading to Notification, Across Schemas", { placeholder: "title" });
  addFittedImage(s, "13-tx-grading-notification.png", { x: 0.5, y: 1.15, w: 12.3, h: 5.9 });
  note(s, sNo(), "Transaction — Grading to Notification",
    "And here's what happens when a change needs to cross a schema boundary. A lecturer posts a grade — " +
    "that's one local transaction entirely inside the assignment_grading schema. Once it commits, an event " +
    "goes out. The notification service picks it up and runs its OWN separate local transaction inside its " +
    "own schema — it checks whether it's already processed this exact event for this user, reads the " +
    "student's notification preferences, and inserts the notification. Two independent, fully-local " +
    "transactions, linked only by an event. This is eventual consistency by design — never a distributed " +
    "transaction spanning two schemas — and the idempotency check is exactly what makes 'deliver at least " +
    "once' safe to use here.");
}

// =====================================================================
// SECTION 5 — ER
// =====================================================================
pres.addSection({ title: "5. Entity Relationships" });
{
  const s = pres.addSlide({ masterName: "SECTION", sectionTitle: "5. Entity Relationships" });
  s.addText("05", { x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72, bold: true,
    color: THEME.colors.accent2, isTextBox: true, margin: 0 });
  s.addText("Entity Relationships", { placeholder: "title" });
  s.addText("The tables in every schema, named only — and how they connect, inside and across schema boundaries.",
    { placeholder: "body" });
  note(s, sNo(), "Section 5",
    "Now let's look at the tables themselves — just the tables and how they relate, without getting into " +
    "individual columns.");
}

{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("How to Read These Diagrams", { placeholder: "title" });
  const legendItems = [
    ["Box", "A table, named only — no columns shown, by design. Column-level detail lives in the migration scripts."],
    ["Solid line + crow's foot", "A real foreign key, inside the same schema — enforced by the database itself."],
    ["Dashed line, labelled", "A cross-schema reference — a plain id column (e.g. lecturer_id, student_id), never a real foreign key, per the “no cross-schema FK” rule."],
    ["Grouped box", "One service's schema boundary — every table inside it is owned by that one service."],
  ];
  let y = 1.5;
  legendItems.forEach((it) => {
    s.addShape("roundRect", { x: 0.6, y, w: 11.9, h: 1.15, rectRadius: 0.06, fill: { color: THEME.colors.lt2 }, line: { type: "none" } });
    s.addText(it[0], { x: 0.85, y: y + 0.1, w: 3.2, h: 0.95, fontFace: THEME.bodyFontFace, fontSize: 14, bold: true,
      color: THEME.colors.dk2, valign: "middle", isTextBox: true, margin: 0 });
    s.addText(it[1], { x: 4.3, y: y + 0.1, w: 7.9, h: 0.95, fontFace: THEME.bodyFontFace, fontSize: 12.5,
      color: THEME.colors.dk1, valign: "middle", isTextBox: true, margin: 0 });
    y += 1.3;
  });
  note(s, sNo(), "ER Legend",
    "Quick key before we look at the diagrams. A box is a table — just its name, no columns, because the " +
    "interesting thing to understand at this level is which tables exist and how they relate, not every " +
    "field in them. A solid line with a crow's foot is a real, database-enforced foreign key within the same " +
    "schema. A dashed line, labelled with the actual column name, is a cross-schema reference — a plain id " +
    "column, deliberately not a real foreign key, for the reasons I covered earlier. And each grouped box is " +
    "one service's schema boundary.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("identity_access — Who You Are, What You May Do", { placeholder: "title" });
  addFittedImage(s, "05-er-identity-access.png", { x: 2.6, y: 1.1, w: 8.1, h: 5.95 });
  note(s, sNo(), "ER — identity_access",
    "The identity_access schema: users, roles, permissions, role_permissions, role_scope_assignments, " +
    "assignment_permission_overrides, and user_sessions. A user has one role; a role is granted a set of " +
    "permissions; and a role_scope_assignment lets a user be granted a role within a specific scope — for " +
    "example, as a department head over just one department — with individual permission overrides layered " +
    "on top of that assignment.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("academic — Programs, Courses & Yearly Offerings", { placeholder: "title" });
  addFittedImage(s, "07-er-academic.png", { x: 0.6, y: 1.15, w: 12.1, h: 5.85 });
  note(s, sNo(), "ER — academic",
    "The academic schema: course_catalog is the master list of courses that exist; degree_programs builds " +
    "its curriculum from that catalog through program_courses; and a course template actually gets offered " +
    "in a given year as a course_academic_years row — what we call an offering — inside an academic_years " +
    "and semesters calendar. That offering is where capacity, the assigned lecturer, and the enrollment " +
    "window actually live.");
}

{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("base & enrollment schemas", { placeholder: "title" });
  addFittedImage(s, "06-er-base.png", { x: 0.5, y: 1.4, w: 5.9, h: 2.9 });
  addFittedImage(s, "08-er-enrollment.png", { x: 6.9, y: 1.4, w: 5.9, h: 2.9 });
  s.addText("base — faculties, departments, grading_scales, course_categories", { x: 0.5, y: 4.4, w: 5.9, h: 0.55,
    fontFace: THEME.bodyFontFace, fontSize: 11.5, bold: true, color: THEME.colors.dk2, isTextBox: true, margin: 0, valign: "top" });
  s.addText("A faculty contains many departments. Grading scales and course categories are shared reference lists other schemas point to by id.", { x: 0.5, y: 5.0, w: 5.9, h: 1.0,
    fontFace: THEME.bodyFontFace, fontSize: 11, color: THEME.colors.accent3, isTextBox: true, margin: 0, valign: "top" });
  s.addText("enrollment — student_program_enrollments, course_enrollments, registration_log", { x: 6.9, y: 4.4, w: 5.9, h: 0.55,
    fontFace: THEME.bodyFontFace, fontSize: 11.5, bold: true, color: THEME.colors.dk2, isTextBox: true, margin: 0, valign: "top" });
  s.addText("Every program or course registration action (enroll, waitlist, drop, promote) is appended to one shared audit trail.", { x: 6.9, y: 5.0, w: 5.9, h: 1.0,
    fontFace: THEME.bodyFontFace, fontSize: 11, color: THEME.colors.accent3, isTextBox: true, margin: 0, valign: "top" });
  note(s, sNo(), "ER — base and enrollment",
    "Two smaller schemas side by side. Base holds faculties, departments, grading scales and course " +
    "categories — the institution's shared reference data. Enrollment holds the actual registration records " +
    "— program enrollments and course enrollments — and a single registration_log table that every one of " +
    "those tables writes to, so there's one place to see the full audit trail of who registered, dropped, " +
    "waitlisted or got promoted, and when.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("assignment_grading, notification & analytics", { placeholder: "title" });
  addFittedImage(s, "09-er-grading-notification-analytics.png", { x: 0.5, y: 1.15, w: 12.3, h: 5.9 });
  note(s, sNo(), "ER — grading, notification, analytics",
    "Three smaller schemas together. In assignment_grading, a submission is graded once, producing a grade " +
    "row. In notification, a user's notification_preferences decide which categories of notification are " +
    "actually delivered to them. And in analytics, scheduled_reports run on a timer and feed into the same " +
    "reports table used for on-demand generation, while dashboard_snapshots cache the computed numbers so " +
    "dashboards load instantly instead of recomputing on every page view.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("The Full Picture — Cross-Schema Relationships", { placeholder: "title" });
  addFittedImage(s, "10-er-cross-schema.png", { x: 0.3, y: 1.1, w: 12.7, h: 6.0 });
  note(s, sNo(), "Cross-Schema Relationships",
    "And here's everything together — every dashed line on this diagram is a cross-schema reference. An " +
    "offering stores the lecturer's id, pointing into identity_access, but there's no foreign key enforcing " +
    "it — the owning schema, identity_access, could evolve its own users table without needing to coordinate " +
    "a migration across six other codebases. A department head's scope assignment points at a department id " +
    "in the base schema. A course enrollment points at both a student in identity_access and an offering in " +
    "academic. Ownership of each table always stays with exactly one service; consistency across these " +
    "dashed lines is handled by the application layer — validated through synchronous calls when it matters " +
    "immediately, and through events when it doesn't — never by a database constraint spanning two schemas.");
}

// =====================================================================
// SECTION 6 — Deployment
// =====================================================================
pres.addSection({ title: "6. Deployment, Scaling & Reliability" });
{
  const s = pres.addSlide({ masterName: "SECTION", sectionTitle: "6. Deployment, Scaling & Reliability" });
  s.addText("06", { x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72, bold: true,
    color: THEME.colors.accent2, isTextBox: true, margin: 0 });
  s.addText("Deployment, Scaling & Reliability", { placeholder: "title" });
  s.addText("Containers, networks, load balancing, and deploys that don't drop a single request.", { placeholder: "body" });
  note(s, sNo(), "Section 6",
    "Now let's look at how this actually runs in production — the containers, the networks, and how we " +
    "balance load and deploy without downtime.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Deployment Architecture", { placeholder: "title" });
  addFittedImage(s, "14-deployment-architecture.png", { x: 0.3, y: 1.2, w: 12.7, h: 4.3 });
  addBullets(s, [
    { text: "One Docker host, three isolated networks: ucms_edge (internet-facing), ucms_app (application), ucms_data (databases) — the public-facing web container has no route at all to the database.", fontSize: 12 },
    { text: "Busier services run 2 replicas; quieter ones run 1 — right-sized, not uniform.", fontSize: 12 },
  ], { x: 0.5, y: 5.7, w: 12.3, h: 1.3 });
  note(s, sNo(), "Deployment Architecture",
    "This is the actual deployment topology. One Docker host runs everything, but it's split into three " +
    "networks. The edge network is the only one reachable from the internet. The app network holds the " +
    "gateway and the services. The data network holds Postgres, RabbitMQ and MinIO. The web container — the " +
    "one thing exposed to the public internet — sits only on the edge network, so even if it were " +
    "compromised, it has no network route at all to the database. We verified this directly: from inside the " +
    "web container, the database hostname simply doesn't resolve.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Load Balancing — Three Layers", { placeholder: "title" });
  addFittedImage(s, "15-load-balancing.png", { x: 0.3, y: 1.15, w: 12.7, h: 4.6 });
  addBullets(s, [
    { text: "nginx → gateway replicas (least_conn, DNS re-resolved every 10s)   ·   gateway → service (health-checked every 5s, circuit breaker, GET retried on another replica)   ·   service → service (same pattern, 2s/10s timeouts)", fontSize: 12.5 },
  ], { x: 0.5, y: 5.9, w: 12.3, h: 1.0 });
  note(s, sNo(), "Load Balancing",
    "Load balancing happens at three layers, each doing the same basic job — spread traffic across healthy " +
    "replicas and route around unhealthy ones — but at different points in the request path. nginx spreads " +
    "incoming traffic across gateway replicas. The gateway spreads traffic across service replicas, using " +
    "DNS to discover every running replica of a service, health-checking each one every five seconds, and " +
    "opening a circuit breaker — returning an immediate, clean error — when a service is failing, instead of " +
    "making callers wait on it. And every service-to-service call uses the exact same pattern when it talks " +
    "to another service.");
}

{
  const s = pres.addSlide({ masterName: "BLANK_CONTENT" });
  s.addText("Zero-Downtime Rolling Deploys", { placeholder: "title" });
  addFittedImage(s, "16-rolling-deploy.png", { x: 1.0, y: 1.1, w: 8.7, h: 5.55 });
  statCard(s, 10.3, 2.3, 2.3, 1.6, "686/686", "requests OK during a live redeploy", THEME.colors.dk2);
  note(s, sNo(), "Rolling Deploy",
    "And here's how a deploy actually happens without anyone noticing. New replicas of the new version start " +
    "up right next to the old ones. The deploy script waits until the new ones report healthy — if one never " +
    "does, it aborts and the old version just keeps serving traffic, nothing is lost. Only once the new " +
    "version is confirmed healthy and the load balancers have had time to discover it do the old replicas " +
    "get stopped, and even then gracefully, finishing any request already in flight. We measured this for " +
    "real: while continuously sending requests during an actual redeploy, 686 out of 686 succeeded.");
}

// =====================================================================
// SECTION 7 — Security & Rate Limiting
// =====================================================================
pres.addSection({ title: "7. Security & Rate Limiting" });
{
  const s = pres.addSlide({ masterName: "SECTION", sectionTitle: "7. Security & Rate Limiting" });
  s.addText("07", { x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72, bold: true,
    color: THEME.colors.accent2, isTextBox: true, margin: 0 });
  s.addText("Security & Rate Limiting", { placeholder: "title" });
  s.addText("What protects the system, its data and its users — and what stops any one user or script from overwhelming it.",
    { placeholder: "body" });
  note(s, sNo(), "Section 7",
    "Security and rate limiting. These work together — rate limiting is itself a security control against " +
    "abuse, so I'll cover them as one connected picture.");
}

{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("Security Hardening", { placeholder: "title" });
  const cols = [
    ["Edge", ["Only reachable over HTTPS at the real domain — no public plain-HTTP port", "Strict Content-Security-Policy, HSTS, nosniff, no framing", "Keycloak's admin console returns 404 from the public site"]],
    ["Identity", ["12–128 character passwords, not the username/email, not reused", "Brute-force lockout; every login and admin action logged", "Admin two-factor login built and verified, ready to switch on"]],
    ["Containers & network", ["Every service runs as a non-root user on a read-only filesystem, zero Linux capabilities", "Three isolated Docker networks — the public-facing container cannot reach the database"]],
  ];
  let x = 0.6;
  const colW = 3.95, gap = 0.2;
  cols.forEach((c) => {
    s.addShape("roundRect", { x, y: 1.35, w: colW, h: 5.5, rectRadius: 0.08, fill: { color: THEME.colors.lt2 }, line: { type: "none" } });
    s.addText(c[0], { x: x + 0.2, y: 1.55, w: colW - 0.4, h: 0.4, fontFace: THEME.headFontFace, fontSize: 16,
      bold: true, color: THEME.colors.dk2, isTextBox: true, margin: 0 });
    addBullets(s, c[1].map((t) => ({ text: t })), { x: x + 0.2, y: 2.05, w: colW - 0.4, h: 4.6, fontSize: 11.5 });
    x += colW + gap;
  });
  note(s, sNo(), "Security Hardening",
    "Security is layered in three groups. At the edge: the application is only reachable over HTTPS at our " +
    "real domain now, with a strict content security policy, HSTS, and the usual browser-protection headers " +
    "— and Keycloak's own admin console, which used to be reachable from the public internet, now returns a " +
    "plain 404 from outside. On identity: passwords must be 12 to 128 characters and can't reuse the last " +
    "few, Keycloak locks out repeated failed logins, and every login and admin action is logged. We also " +
    "built two-factor login for administrators — tested working both on and off, just not switched on yet " +
    "until every admin has an authenticator app. And on containers: every service runs as a non-root user on " +
    "a read-only filesystem with every Linux capability dropped, and the network is split so the one " +
    "internet-facing container literally cannot reach the database.");
}

{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("Rate Limiting — Two Layers", { placeholder: "title" });
  s.addText("nginx — per client IP (a flood guard, before any service does work)", { x: 0.6, y: 1.3, w: 12.1, h: 0.4,
    fontFace: THEME.bodyFontFace, fontSize: 14.5, bold: true, color: THEME.colors.dk2, isTextBox: true, margin: 0 });
  const nginxRows = [["Login form", "30 / min"], ["Token endpoint", "120 / min"], ["General API", "50 / sec"], ["Live notification streams (per IP)", "10 open"]];
  let x = 0.6;
  nginxRows.forEach((r) => {
    statCard(s, x, 1.8, 2.85, 1.15, r[1], r[0], THEME.colors.accent1);
    x += 3.0;
  });
  s.addText("API Gateway — per signed-in user, shared across replicas via Redis", { x: 0.6, y: 3.3, w: 12.1, h: 0.4,
    fontFace: THEME.bodyFontFace, fontSize: 14.5, bold: true, color: THEME.colors.dk2, isTextBox: true, margin: 0 });
  const gwRows = [["Uploads", "10 / min"], ["Analytics writes", "6 / min"], ["Enrollment writes", "2 / sec"], ["Account writes", "2 / sec"], ["Everything else", "20 / sec"]];
  x = 0.6;
  const w2 = 2.33;
  gwRows.forEach((r) => {
    statCard(s, x, 3.8, w2, 1.15, r[1], r[0], THEME.colors.accent2);
    x += w2 + 0.15;
  });
  addBullets(s, [
    { text: "Over the limit: HTTP 429 with a Retry-After header and a plain-language message — the app shows it as-is.", fontSize: 12.5 },
    { text: "If Redis is briefly unreachable, the per-user check is skipped rather than blocking the whole site — nginx's per-IP limit still applies underneath.", fontSize: 12.5 },
  ], { x: 0.6, y: 5.3, w: 12.1, h: 1.6 });
  note(s, sNo(), "Rate Limiting",
    "Rate limiting works at two layers. nginx limits by client IP address before a request even reaches a " +
    "service — generous limits, because a whole computer lab can sit behind one shared IP address, so this " +
    "is really just a flood guard. The real per-account protection against password guessing is Keycloak's " +
    "own lockout. The second layer is the gateway, which limits per signed-in user, with the count shared " +
    "across every gateway replica through Redis, so a user can't dodge the limit by being routed to a " +
    "different replica. Different actions get different limits — enrollment writes are tight, because the " +
    "real risk is a script hammering the enrollment window, while ordinary reads are generous. Go over the " +
    "limit and you get a 429 with a Retry-After header telling you exactly how long to wait, and the app " +
    "surfaces that message directly.");
}

// =====================================================================
// SECTION 8 — Testing
// =====================================================================
pres.addSection({ title: "8. Testing & Quality" });
{
  const s = pres.addSlide({ masterName: "SECTION", sectionTitle: "8. Testing & Quality" });
  s.addText("08", { x: 0.9, y: 2.15, w: 4, h: 1.4, fontFace: THEME.headFontFace, fontSize: 72, bold: true,
    color: THEME.colors.accent2, isTextBox: true, margin: 0 });
  s.addText("Testing & Quality", { placeholder: "title" });
  s.addText("How we know all of this actually works — from unit tests up to a real browser against the real deployment.",
    { placeholder: "body" });
  note(s, sNo(), "Section 8",
    "Finally — how do we actually know everything I've just described works? Through five layers of testing, " +
    "each one checking something the layer below it can't.");
}

{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("Testing Pyramid — Five Layers, One Pipeline", { placeholder: "title" });
  const steps = [
    ["239", "Unit & Integration Tests", "JUnit 5, MockMvc, H2, Testcontainers Redis — every service, every single Jenkins build"],
    ["66", "System / End-to-End Steps", "system_test.py drives the real stack through the gateway: Keycloak login → enroll → grade → notify → report"],
    ["13", "Edge & Container Checks", "Headers, Keycloak lockdown, load balancing, failover, rate limits, network isolation, container hardening"],
    ["5", "Real Browser Checks", "Headless Chromium: sign-in, silent SSO, zero Content-Security-Policy violations"],
    ["686/686", "Operational Proof", "Requests that succeeded during a live rolling redeploy — zero dropped"],
  ];
  let y = 1.35;
  const h = 1.02, gap = 0.08;
  steps.forEach((st, i) => {
    const barColor = i < 2 ? THEME.colors.dk2 : (i < 4 ? THEME.colors.accent1 : THEME.colors.accent2);
    s.addShape("roundRect", { x: 0.6, y, w: 11.9, h, rectRadius: 0.07, fill: { color: THEME.colors.lt2 }, line: { type: "none" } });
    s.addShape("roundRect", { x: 0.6, y, w: 0.12, h, rectRadius: 0, fill: { color: barColor }, line: { type: "none" } });
    s.addText(st[0], { x: 0.95, y, w: 1.75, h, align: "center", valign: "middle", fontFace: THEME.headFontFace,
      fontSize: 22, bold: true, color: barColor, isTextBox: true, margin: 0 });
    s.addText(st[1], { x: 2.85, y: y + 0.07, w: 3.9, h: h - 0.14, valign: "middle", fontFace: THEME.bodyFontFace,
      fontSize: 13, bold: true, color: THEME.colors.dk1, isTextBox: true, margin: 0 });
    s.addText(st[2], { x: 6.85, y: y + 0.07, w: 5.5, h: h - 0.14, valign: "middle", fontFace: THEME.bodyFontFace,
      fontSize: 10.5, color: THEME.colors.accent3, isTextBox: true, margin: 0 });
    y += h + gap;
  });
  s.addText("A dependency & image vulnerability scan (Trivy + npm audit) runs on every single build, independent of this pyramid.",
    { x: 0.6, y: 6.9, w: 11.9, h: 0.35, fontFace: THEME.bodyFontFace, fontSize: 11, italic: true,
      color: THEME.colors.accent3, isTextBox: true, margin: 0 });
  note(s, sNo(), "Testing Pyramid",
    "Five layers, from fastest and most numerous at the top, to slowest and most end-to-end at the bottom. " +
    "239 unit and integration tests run in every single build and check each service in isolation. 66 " +
    "end-to-end steps drive the real running stack through the real gateway, starting with an actual " +
    "Keycloak login and walking all the way through enrolling, grading, notifying and reporting. 13 checks " +
    "specifically target the edge and the containers — things like confirming the Keycloak admin console is " +
    "actually blocked, and that killing a replica doesn't lose a request. 5 checks run in a real headless " +
    "browser to confirm the strict security policy doesn't break anything a real user would do. And finally, " +
    "the operational proof — 686 out of 686 real requests succeeding during an actual live redeploy. " +
    "Running alongside all of this, independent of the pyramid, is a dependency and image vulnerability scan " +
    "on every build.");
}

// =====================================================================
// CLOSING
// =====================================================================
{
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText("Where This Leaves Us", { placeholder: "title" });
  const stats = [
    ["7", "microservices"], ["8", "database schemas"], ["239", "unit/integration tests"],
    ["66", "end-to-end steps"], ["3", "load-balancing layers¹"], ["0", "CSP violations"],
  ];
  const cw = 1.95, gh = 1.5;
  let x = 0.6;
  stats.forEach((st) => {
    statCard(s, x, 1.4, cw, gh, st[0], st[1], THEME.colors.dk2);
    x += cw + 0.08;
  });
  addBullets(s, [
    { text: "Every design choice in this deck traces back to a test that proves it — we didn't just design for failure isolation, zero downtime and least-privilege access, we measured them.", bold: true },
    { text: "Open item, highest priority: dependency versions carry known CVEs (Spring Boot 3.3.x / Angular 19 line) — upgrade planned, security scan currently runs in report-only mode until it's done.", },
    { text: "Admin two-factor login is built and tested both ways, ready to switch on once every administrator has an authenticator app.", },
  ], { x: 0.6, y: 3.3, w: 12.1, h: 2.6, fontSize: 14 });
  s.addText("¹ nginx → gateway, gateway → service, service → service",
    { x: 0.6, y: 6.9, w: 11, h: 0.35, fontFace: THEME.bodyFontFace, fontSize: 9.5, italic: true,
      color: THEME.colors.accent3, isTextBox: true, margin: 0 });
  note(s, sNo(), "Summary",
    "So, to summarize where this leaves us. Seven microservices, eight database schemas, 239 automated unit " +
    "and integration tests, 66 end-to-end steps, load balancing operating at every layer of the stack, and " +
    "zero content security policy violations in real browser testing. The important point isn't the list of " +
    "features — it's that every single thing I've described in this presentation, we actually measured. We " +
    "didn't just design for failure isolation; we killed a container and counted the successful requests. We " +
    "didn't just design for zero-downtime deploys; we redeployed live and counted 686 out of 686. We're " +
    "honest about what's still open too — our current dependency versions carry known vulnerabilities, which " +
    "is why the security scan currently just reports rather than blocking a build, and we have a planned " +
    "upgrade to close that.");
}

{
  const s = pres.addSlide({ masterName: "TITLE" });
  s.addText("Thank You", { placeholder: "title" });
  s.addText("Questions?", { placeholder: "body" });
  note(s, sNo(), "Thank You",
    "Thank you. I'm happy to take any questions — about the architecture, the testing, the deployment " +
    "pipeline, or anything else.");
}

// =====================================================================
(async () => {
  await pres.writeFile({ fileName: OUT });
  await applyTheme(OUT, THEME);
  fs.writeFileSync(NARRATION_PATH, narration.join("\n" + "=".repeat(70) + "\n\n"));
  console.log("Wrote", OUT);
  console.log("Wrote", NARRATION_PATH);
  console.log("Total slides:", slideNo);
})();
