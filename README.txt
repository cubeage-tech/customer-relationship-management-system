# SmartCRM AI

**AI-Powered Customer Relationship Management (CRM) Platform for Companies & Industries**

SmartCRM AI is a multi-industry CRM platform that helps companies manage customers, leads, sales pipelines, quotations, communication, service tickets, marketing campaigns, and sales team performance — augmented by AI for lead scoring, sales forecasting, recommendations, content generation, sentiment analysis, and more.

> 📄 Full requirements are documented in [`docs/SmartCRM_AI_SRS.docx`](./docs/SmartCRM_AI_SRS.docx) — the Software Requirements Specification (SRS). This README is a high-level orientation for developers; the SRS is the source of truth for detailed functional/non-functional requirements, business rules, and open questions.

---

## Table of Contents

- [Overview](#overview)
- [Core Modules](#core-modules)
- [AI Capabilities](#ai-capabilities)
- [Tech Stack](#tech-stack)
- [Repository Structure](#repository-structure)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [Available Scripts](#available-scripts)
- [Architecture Notes](#architecture-notes)
- [Business Model](#business-model)
- [Roadmap / Future Enhancements](#roadmap--future-enhancements)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Status & Open Questions](#status--open-questions)
- [License](#license)

---

## Overview

Target industries include manufacturing, engineering, real estate, construction, automobile, industrial equipment supply, software, trading, general services, and healthcare.

SmartCRM AI aims to:

- Eliminate lost enquiries through centralized lead capture.
- Replace manual, spreadsheet-based sales tracking with a structured digital pipeline.
- Provide a unified history of every customer interaction (Customer 360°).
- Reduce response times for enquiries and service requests.
- Improve visibility into distributed sales team performance.
- Enable AI-driven sales forecasting, recommendations, and automation.

---

## Core Modules

| # | Module | Description |
|---|--------|-------------|
| 1 | Customer Management (Customer 360°) | Centralized customer profiles, contacts, purchase & communication history, documents. |
| 2 | Lead Management | Lead capture, source tracking, assignment, and pipeline: `New Lead → Contacted → Meeting → Quotation → Negotiation → Converted`. |
| 3 | Sales Pipeline Management | Opportunity, stage, deal value, and closing-date tracking. |
| 4 | Quotation & Proposal Management | Quotation creation, pricing, discount approval, PDF proposals, AI-assisted drafting. |
| 5 | Communication Management | Unified Email, WhatsApp, SMS, call logs, and meeting scheduling. |
| 6 | Service & Support Management | Complaint/ticket logging, technician assignment, SLA tracking, feedback. |
| 7 | Marketing Management | Campaigns, segmentation, promotion tracking, AI targeting. |
| 8 | Sales Team Management | Performance, targets, activity, attendance, commission calculation. |
| 9 | Analytics Dashboard & Reporting | Sales, customer, revenue, and team performance reports. |
| — | Sales Team Mobile App | Field visit logging, lead updates, meeting reports, push notifications (Flutter). |

## AI Capabilities

1. **AI Lead Scoring** — prioritizes leads by purchase probability.
2. **AI Sales Prediction** — forecasts revenue, deal probability, and target attainment.
3. **AI Customer Recommendation** — suggests products, timing, and approach.
4. **AI Chat Assistant** — natural-language querying of CRM data.
5. **AI Email & Message Generator** — drafts sales/marketing content.
6. **AI Sentiment Analysis** — detects customer sentiment and churn risk.
7. **AI Meeting Assistant** — recording, summaries, and action items.
8. **AI Sales Coach** — personalized coaching suggestions for reps.
9. **AI Duplicate Detection** — flags duplicate/incorrect customer records.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend (Web) | Next.js, React, TypeScript, Tailwind CSS |
| Mobile App | Flutter (Android & iOS) |
| Backend | Node.js / NestJS, Python (FastAPI) |
| Database | PostgreSQL (relational), MongoDB (unstructured/logs) |
| AI Layer | OpenAI API, LangChain, custom Python ML/NLP models |
| Integrations | WhatsApp Business API, Email API, Payment Gateway, ERP, Cloud Storage |

---

## Repository Structure

Repo: [`cubeage-tech/customer-relationship-management-system`](https://github.com/cubeage-tech/customer-relationship-management-system)

```
customer-relationship-management-system/
├── frontend-v1/              # Next.js + React + TypeScript web client
│   ├── package.json
│   └── ...
├── backend-v1/               # Node.js / NestJS (+ Python FastAPI AI service) backend
│   ├── package.json
│   └── ...
├── docs/
│   └── SmartCRM_AI_SRS.docx  # Software Requirements Specification
└── README.md
```

> The mobile app (Flutter) and any dedicated AI microservice are not yet reflected as separate top-level folders. If/when they're added, update this section to match (e.g., a `mobile-v1/` folder, or an `ai-service` package inside `backend-v1/`).

---

## Getting Started

### Prerequisites

- Node.js (LTS)
- Python 3.11+
- PostgreSQL 15+
- MongoDB 6+
- Flutter SDK (for mobile app development)
- API keys: OpenAI, WhatsApp Business API, Email provider, Payment Gateway

### Setup

```bash
# Clone the repository
git clone https://github.com/cubeage-tech/customer-relationship-management-system.git
cd customer-relationship-management-system

# --- Backend ---
cd backend-v1
npm install
cp .env.example .env      # fill in DATABASE_URL, MONGODB_URI, API keys, etc.
npm run migrate           # run database migrations (if configured)
npm start               # start backend in dev mode
```

```bash
# --- Frontend (in a separate terminal) ---
cd frontend-v1
npm install
cp .env.example .env.local # fill in NEXT_PUBLIC_API_URL, etc.
npm run dev                # start Next.js dev server
```

> Both `frontend-v1` and `backend-v1` currently maintain their own `package.json` and are run independently rather than through a shared workspace/monorepo tool. Adjust these commands once each package's actual `scripts` are finalized.

---

## Environment Variables

| Variable | Description |
|----------|--------------|
| `DATABASE_URL` | PostgreSQL connection string |
| `MONGODB_URI` | MongoDB connection string |
| `OPENAI_API_KEY` | OpenAI API key for AI features |
| `WHATSAPP_API_TOKEN` | WhatsApp Business API token |
| `EMAIL_API_KEY` | Transactional/marketing email provider key |
| `PAYMENT_GATEWAY_KEY` | Payment gateway secret key |
| `JWT_SECRET` | Secret for signing auth tokens |
| `CLOUD_STORAGE_BUCKET` | Cloud storage bucket for documents/attachments |

> See the SRS ([Section 19, Integrations](./docs/SmartCRM_AI_SRS.docx) and Section 20, Security Requirements) for details on each integration and data-handling expectations.

---

## Available Scripts

Each package (`frontend-v1`, `backend-v1`) maintains its own `package.json` and scripts. Run them from within the respective folder:

| Command | Where | Description |
|---------|-------|--------------|
| `npm run dev` | `frontend-v1`, `backend-v1` | Run the app/service locally in development mode |
| `npm run build` | `frontend-v1`, `backend-v1` | Build for production |
| `npm run start` | `frontend-v1`, `backend-v1` | Run the production build |
| `npm run test` | `frontend-v1`, `backend-v1` | Run unit/integration tests |
| `npm run lint` | `frontend-v1`, `backend-v1` | Lint the codebase |
| `npm run migrate` | `backend-v1` | Run database migrations |

> Update this table to match the exact scripts defined in each `package.json` as the codebase is built out.

---

## Architecture Notes

- **Multi-tenant SaaS**: each client organization operates in an isolated tenant/workspace (see SRS Section 1.2 and 9.1).
- **Core vs. AI split**: NestJS handles transactional CRM business logic; FastAPI hosts AI/ML-oriented endpoints (scoring, prediction, NLP) — see SRS Section 8 for rationale.
- **Offline-first mobile**: the Flutter app queues field-captured data locally and syncs on reconnect.
- **Human-in-the-loop AI**: AI-generated content (quotations, emails, campaign copy) always requires human review/approval before being sent to a customer.

---

## Business Model

- **SaaS Subscription** — Starter / Business / Enterprise tiers.
- **Industry-Specific CRM** — Manufacturing, Real Estate, Healthcare, Industrial Sales editions.
- **AI Premium Add-ons** — advanced analytics, sales prediction, AI assistant, automation.

## Roadmap / Future Enhancements

- AI Autonomous Sales Agent (automated outreach and meeting scheduling)
- Voice CRM (voice-command record updates)
- Predictive Customer Retention (proactive churn prevention)
- Industry Marketplace Integration (Manufacturer → Dealer → Customer)

---

## Documentation

- **[Software Requirements Specification (SRS)](./docs/SmartCRM_AI_SRS.docx)** — complete functional, non-functional, security, and architecture requirements.
- Open questions requiring stakeholder confirmation are tracked in **SRS Section 25.4** (roles/permissions, SLA thresholds, ERP/payment integration scope, applicable data protection regulations, etc.) — check there before making assumptions in code.

## Contributing

1. Create a feature branch from `dev`: `git checkout -b feature/<short-description>`.
2. Place UI work in `frontend-v1/` and API/business-logic work in `backend-v1/`, following the module boundaries defined in the SRS (Section 6).
3. Add/update tests for any new functional requirement (`FR-x.x`) you implement.
4. Open a pull request referencing the relevant SRS requirement ID(s).

## Status & Open Questions

This repository is in **early/greenfield development**, built against the v1.0 SRS. Several implementation details are pending client/stakeholder confirmation — see SRS Section 25.4 for the full list, including:

- Discount approval thresholds and approver hierarchy
- Service ticket SLA durations per priority
- Definitive roles & permissions matrix
- ERP and payment gateway providers to integrate
- Applicable data protection regulations by region

## License

Proprietary — © Devniks (Pentasoftware Consultancy). All rights reserved. Not for external distribution without authorization.

To start backend use .\mvnw.cmd spring-boot:run