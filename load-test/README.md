# Seckill load-test package

This directory contains the repeatable load-test plan, scoped reset helpers, and evidence templates for an isolated test environment.

## Package map

- `jmeter/`: non-GUI JMeter plan, sample parameter file, scenario notes, and artifact-retention rules.
- `reset/`: scoped MySQL and Redis reset helpers. Drain or intentionally purge the test RabbitMQ queue before restoring stock.
- `report/REPORT_TEMPLATE.md`: per-run evidence record. Replace placeholders with observed values; never turn an acceptance target into an observed result.
- `report/FINAL_ACCEPTANCE.md`: final checklist and decision record for the tail acceptance work.

## Minimal run sequence

1. Build and start the application and dependencies.
2. Start the observability stack and verify Prometheus targets are up.
3. Prepare isolated test users and, when applicable, CAPTCHA-issued seckill paths.
4. Reset only the scoped goods and users, then verify DB stock equals Redis stock.
5. Run JMeter in non-GUI mode and retain the JTL, HTML report, logs, and time range.
6. Fill the report template and complete the final acceptance checklist.

The package does not solve or bypass CAPTCHA, does not contact a real payment provider, and does not provide a production-safe data reset. See the subdirectory READMEs for the exact contracts and limitations.
