# Tail work status

This is the implementation status after the repository-gap review. It is separate from runtime load-test evidence.

## Completed in code

- Java 21 CAPTCHA expression evaluation no longer depends on Nashorn.
- Sensitive credential/token debug logging was removed.
- Test-only `/login/token_test` is available only under the `loadtest` profile.
- User registration, logout, password change, order list, and unpaid-order cancellation are implemented.
- Delivery address CRUD, default-address selection, and immutable order address snapshots are implemented.
- CAPTCHA-gated seckill requests carry the selected address through RabbitMQ to order creation.
- Admin authentication uses a dedicated `admin_user` table and short-lived Redis tokens.
- Admin goods/seckill inventory management and order listing/shipping endpoints are implemented.
- Admin user listing is implemented and returns only redacted user fields.
- User receive/complete transitions are conditional on the previous order state.
- Order status 4 is closed, status 5 is completed, and status 6 is refunded; timeout closure is not presented as a refund.
- User-state HTML caching was removed from the rendered goods detail endpoint, and detail JSON uses a redacted user DTO.

## Verification

- `mvn test -q`: passed.
- Inline JavaScript syntax checks for goods detail, order detail, address, admin, and registration pages: passed.
- `git diff --check`: passed.
- JMeter remains paused. No 2000/5000-thread run was started.

## Environment follow-up

- [x] Applied V3/V4 schema changes to the new local `seckill` database through the configured MySQL connection. `delivery_address`, `admin_user`, and the three immutable order-address snapshot columns are present.
- Provision an administrator out of band; no default administrator password is committed.
- [x] Rebuilt and restarted the application from the current source. The deployable JAR contains the Thymeleaf templates.
- [x] Runtime smoke checks passed for health, login, registration, address, admin, and static goods-detail routes. Unauthenticated address/admin requests were rejected with business codes `500210`/`500220`.
- Existing JMeter reports remain ingress-only and do not establish seckill business throughput or oversell safety. High-concurrency runs remain paused.
