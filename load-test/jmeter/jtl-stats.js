// 汇总 JMeter JTL（CSV）为 QPS/延迟分位/错误率，用于压测报告。
// 用法: node load-test/jmeter/jtl-stats.js target/perftest/list-50.jtl
const fs = require('fs');
const file = process.argv[2];
const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/).filter(l => l.trim());
const header = lines[0].split(',');
const idx = name => header.indexOf(name);

const rows = lines.slice(1).map(l => l.split(','));
const n = rows.length;
const elapsed = rows.map(r => Number(r[idx('elapsed')])).sort((a, b) => a - b);
const ts = rows.map(r => Number(r[idx('timeStamp')]));
const errors = rows.filter(r => !/true/i.test(r[idx('success')]) || r[idx('responseCode')] !== '200');
const errCodes = {};
errors.forEach(r => { const c = r[idx('responseCode')]; errCodes[c] = (errCodes[c] || 0) + 1; });

let t0 = Infinity, t1 = -Infinity;
for (let i = 0; i < n; i++) {
  const t = ts[i];
  if (t < t0) t0 = t;
  const end = t + Number(rows[i][idx('elapsed')]);
  if (end > t1) t1 = end;
}
const wallSec = (t1 - t0) / 1000;
const pct = p => elapsed[Math.min(n - 1, Math.floor(n * p))];
const avg = elapsed.reduce((a, b) => a + b, 0) / n;

console.log(JSON.stringify({
  samples: n,
  wallSeconds: +wallSec.toFixed(1),
  qps: +(n / wallSec).toFixed(1),
  successQps: +(((n - errors.length) / wallSec)).toFixed(1),
  errors: errors.length,
  errorRate: +(errors.length * 100 / n).toFixed(2) + '%',
  errorCodes: errCodes,
  avgMs: +avg.toFixed(1),
  p50: pct(0.5), p90: pct(0.9), p95: pct(0.95), p99: pct(0.99),
  maxMs: elapsed[n - 1],
  throughputKBps: +(rows.reduce((a, r) => a + Number(r[idx('bytes')] || 0), 0) / 1024 / wallSec).toFixed(0)
}, null, 2));
