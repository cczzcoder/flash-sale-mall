# JMeter seckill verification

The checked-in plan is suitable for two separate checks:

1. `runSeckill=false` (default) verifies authenticated login and goods-detail ingress without changing stock.
2. `runSeckill=true` verifies the submit/result path only when each CSV row already contains a valid, one-time `seckillPath`.

Do not claim oversell or order-creation results from the ingress-only run. Reset only an isolated test dataset before a business run, and record DB stock, Redis stock, queue depth, created orders, sold-out responses, duplicate responses, and dead-letter messages.

The business-run CSV must include a valid `deliveryAddrId` for every user. The seckill endpoint rejects requests without an address with business code `500219`; the checked-in JMX sends this field together with `goodsId`.

Example non-GUI command from the repository root:

```bash
jmeter -n -t load-test/jmeter/seckill-flow.jmx \
  -JbaseUrl=http://localhost:8080 \
  -JusersFile=load-test/jmeter/users.csv \
  -Jthreads=500 -JrampSeconds=60 -Jloops=1 \
  -JrunSeckill=false -JrunMockPayment=false \
  -l target/jmeter/run.jtl -e -o target/jmeter/html
```

For a real seckill run, provision paths through the loadtest-only endpoint while the `loadtest` profile is active, write the returned user/path pairs to an isolated CSV, set `runSeckill=true`, and use a short path-to-request window because paths are one-time-use.
