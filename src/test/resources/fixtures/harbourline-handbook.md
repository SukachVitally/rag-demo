# Harbourline Payments — Engineering Handbook

Harbourline Payments is a fictional payment-processing company used here as test data. None of the facts below are real, which makes them useful for testing retrieval: the LLM cannot know them from training, so a correct answer proves the context was retrieved.

## Company overview

Harbourline was founded in 2019 in Belfast by Maeve Donnelly and Tomas Kerrigan. The engineering team has 42 people split into four squads: Ledger, Gateway, Risk, and Platform. The main office is on Ormeau Avenue, and there is a smaller satellite office in Kraków with nine engineers who mostly work on the Risk squad.

The company processes card payments for around 1,800 small and medium merchants, mostly in Ireland and the UK. Peak traffic happens on the last Friday of November, when the system handled 3,400 transactions per second in 2025.

## Architecture

The platform consists of eleven services written in Java 21 with Spring Boot. Services communicate over Kafka for asynchronous events and gRPC for synchronous calls. The public merchant API is REST and sits behind an API gateway called Lighthouse, which is an internal fork of Spring Cloud Gateway.

The core services are:

- **ledger-service** — double-entry bookkeeping. Every money movement creates exactly two journal lines that must sum to zero. It uses PostgreSQL 16 with serializable isolation for posting transactions.
- **auth-service** — talks to card schemes and returns authorisation decisions. Its hard latency budget is 180 ms at p99.
- **risk-engine** — scores each transaction from 0 to 1000. Scores above 850 are declined automatically; scores between 700 and 850 are sent to manual review.
- **settlement-service** — runs the nightly settlement batch at 02:30 UK time and produces payout files for partner banks.
- **merchant-portal** — the web dashboard for merchants, built with React and a Spring Boot backend-for-frontend.

All money amounts are stored as `long` values in minor units (pence or cents). Using `double` or `float` for money is forbidden and is checked by an ArchUnit rule in every build.

## Deployment process

Services are deployed to Kubernetes on AWS (region eu-west-2, London). The deployment pipeline runs in GitHub Actions and has five stages: build, unit tests, contract tests, staging deploy, and production deploy.

Production deployments are only allowed Monday to Thursday between 09:00 and 16:00 UK time. Friday deployments are banned except for hotfixes approved by the on-call lead. During the "November freeze", which runs from 15 November to 5 December, only hotfixes may be deployed.

Every production deploy uses a canary release: 5% of traffic for 15 minutes, then 25% for 15 minutes, then 100%. The canary is rolled back automatically if the error rate rises above 0.5% or p99 latency rises by more than 20% compared with the stable version.

Database migrations use Flyway. Migrations must be backwards compatible with the previous release, so a column can only be dropped two releases after the code stops using it. This is called the "expand and contract" rule internally.

## On-call

Each squad runs its own on-call rotation with one primary and one secondary engineer. A rotation lasts one week and changes over on Wednesday at 10:00, not Monday, so that handovers never fall on a weekend or a Monday morning.

On-call engineers are paid a flat £350 per week plus £60 for each night they are paged after midnight.

Incident severity levels:

- **SEV1** — payments failing for more than 5% of merchants, or any data loss in the ledger. Response within 5 minutes; the CTO is informed immediately.
- **SEV2** — a single large merchant affected, or degraded latency across the platform. Response within 15 minutes.
- **SEV3** — non-customer-facing issues such as a failed internal report. Handled in working hours.

After every SEV1 and SEV2 there is a blameless postmortem, which must be published within five working days. Postmortems are stored in the "Lessons" space of the internal wiki.

## Incident history

The most serious incident so far, internally called "the Double Tap", happened on 14 March 2024. A retry bug in the Kafka consumer of ledger-service caused about 2,100 transactions to be posted twice over 38 minutes. No merchant lost money because settlement-service detected the imbalance before the nightly payout, but the cleanup took two days. The fix introduced idempotency keys on every journal posting, stored in a unique-indexed column called `posting_key`.

A second notable incident in August 2025 was caused by an expired TLS certificate on the connection to one card scheme. Authorisations for that scheme failed for 22 minutes. Since then, certificate expiry is monitored and alerts fire 30 days before expiry.

## Coding standards

- Code review requires two approvals, at least one from the owning squad.
- Test coverage must stay above 80% for ledger-service and risk-engine, and above 65% for other services.
- Logging uses structured JSON. Card numbers (PANs) must never appear in logs; a log filter masks anything that looks like a 16-digit number to show only the last four digits.
- Feature flags are managed in an internal tool called Switchboard. Flags older than 90 days generate a weekly reminder to remove them.

## Glossary

- **PAN** — primary account number, the long number on a payment card.
- **Settlement** — the process of actually moving money to merchants after transactions are authorised.
- **Chargeback** — a payment reversed at the cardholder's request through their bank. Harbourline charges merchants a £15 fee per chargeback.
- **Lighthouse** — Harbourline's API gateway.
- **Switchboard** — Harbourline's feature-flag tool.
- **The Double Tap** — the March 2024 duplicate-posting incident.