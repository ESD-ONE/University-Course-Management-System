# UCMS V3 — Architecture, DevOps & Security Deliverables

Everything in this folder explains and presents the security hardening, load balancing and
rate limiting work (see [`../implementation-change-log.md`](../implementation-change-log.md)
Part 4), plus the wider system architecture it sits on top of: the microservices design, the
Jenkins CI/CD pipeline, inter-service communication, the transactional/relational database
design, and the test suites that verify all of it.

## Contents

| File | What it is |
|---|---|
| [`01-security-loadbalancing-ratelimit-guide.md`](01-security-loadbalancing-ratelimit-guide.md) | The written guide: how security, load balancing and rate limiting work, how to configure them, and the production rollout runbook |
| [`presentation/UCMS-Architecture-DevOps-Security.pptx`](presentation/UCMS-Architecture-DevOps-Security.pptx) | A 35-slide presentation deck covering the full system: why microservices, why schema-per-service, the DevOps pipeline, service communication, database transactions, entity relationships, deployment, load balancing, security, rate limiting and testing |
| [`presentation/narration-script.txt`](presentation/narration-script.txt) | The spoken narration for every slide, as plain text — usable as speaker notes, a script to read live, or input to a text-to-speech engine. (The same text is also embedded as speaker notes inside the `.pptx` itself.) |
| [`presentation/generate_deck.js`](presentation/generate_deck.js) | The pptxgenjs script that builds the deck — edit and re-run (`node generate_deck.js`) to regenerate it after a content change |
| [`diagrams/`](diagrams/) | 17 Mermaid source files (`.mmd`) and their rendered PNGs, used throughout the guide and the deck |

## Diagrams

| # | Diagram | Shows |
|---|---|---|
| 01 | `microservices-architecture` | All 7 services, the gateway, one shared Postgres with a schema per service, RabbitMQ, Redis, MinIO |
| 02 | `devops-jenkins-pipeline` | The full CI/CD flow: checkout → build & test → package → frontend build → security scan → (on develop/release/main) deploy via rolling redeploy |
| 03a | `communication-sync` | Every synchronous REST call between services, authenticated with Keycloak service tokens |
| 03b | `communication-async` | The RabbitMQ event flow: publishers, the topic exchange, consumer queues, dead-letter queues, and the live-push fan-out to the browser |
| 04 | `event-flow-sequence` | One concrete example end to end: a student enrolling, from browser to database to event consumers |
| 05–09 | `er-*` | Entity-relationship diagrams per schema — tables only, no columns, as requested |
| 10 | `er-cross-schema` | Every cross-schema reference in the system, and which service owns which table |
| 11–13 | `tx-*` | Three real database transactions, step by step: enrolling, dropping + waitlist promotion, and a grade posting that crosses a schema boundary via an event |
| 14 | `deployment-architecture` | The container/network topology: `ucms_edge`, `ucms_app`, `ucms_data` |
| 15 | `load-balancing` | The three load-balancing layers: nginx → gateway, gateway → service, service → service |
| 16 | `rolling-deploy` | How a zero-downtime deploy actually proceeds, replica by replica |
| 17 | `testing-strategy` | The five-layer test pyramid (kept as a reference diagram; the deck itself rebuilds this as native slide content) |

## Regenerating the diagrams

```bash
cd docs/V3/diagrams
npx -y @mermaid-js/mermaid-cli@11 -i <name>.mmd -o <name>.png -b white -s 2 -w 1400
```

## Regenerating the presentation

```bash
cd docs/V3/presentation
npm install --no-save pptxgenjs@3.12.0   # if not already resolvable
node generate_deck.js
```

This rebuilds both `UCMS-Architecture-DevOps-Security.pptx` and `narration-script.txt` from
the same source data, so the slide content and the narration never drift apart.
