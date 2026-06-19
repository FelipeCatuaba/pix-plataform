import http from "k6/http";
import { check, fail, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const profile = (__ENV.PROFILE || "full").toLowerCase();
const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const pollIntervalSeconds = Number(__ENV.POLL_INTERVAL_SECONDS || "0.25");
const processingTimeoutSeconds = Number(
  __ENV.PROCESSING_TIMEOUT_SECONDS || (profile === "load" ? "45" : "180"),
);
const apiP95ThresholdMs = Number(__ENV.API_P95_THRESHOLD_MS || "50");
const endToEndP95ThresholdMs = Number(__ENV.END_TO_END_P95_THRESHOLD_MS || "4000");

const createDuration = new Trend("pix_create_duration", true);
const endToEndDuration = new Trend("pix_end_to_end_duration", true);
const processingFailures = new Rate("pix_processing_failures");
const processingTimeouts = new Counter("pix_processing_timeouts");
const functionalFailures = new Counter("pix_functional_failures");
const scenariosExecuted = new Counter("pix_scenarios_executed");

const partnerFailureCases = [
  {
    name: "partner_http_500",
    pixKey: "partner-error@pix.test",
    expectedStatus: "FAILED",
  },
  {
    name: "partner_http_408",
    pixKey: "partner-request-timeout@pix.test",
    expectedStatus: "FAILED",
  },
  {
    name: "partner_http_429",
    pixKey: "partner-rate-limit@pix.test",
    expectedStatus: "FAILED",
  },
  {
    name: "partner_read_timeout",
    pixKey: "partner-timeout@pix.test",
    expectedStatus: "FAILED",
  },
  {
    name: "partner_invalid_json",
    pixKey: "partner-invalid@pix.test",
    expectedStatus: "FAILED",
  },
];

export const options = buildOptions(profile);

export function runFullSuite() {
  testHealth();
  testSuccess();
  testIdempotency();
  testInvalidRequest();
  testNotFound();

  for (const testCase of partnerFailureCases) {
    testPartnerFailure(testCase);
  }
}

export function runSmokeSuite() {
  testHealth();
  testSuccess();
  testIdempotency();
  testInvalidRequest();
  testNotFound();
}

export function runResilienceSuite() {
  for (const testCase of partnerFailureCases) {
    testPartnerFailure(testCase);
  }
}

export function runLoadTest() {
  const transactionId = uniqueId("load");
  const result = createAndWait({
    scenario: "load_success",
    transactionId,
    pixKey: "cliente@email.com",
    expectedStatus: "COMPLETED",
    trackPerformance: true,
  });

  processingFailures.add(!result.success, { scenario: "load_success" });
}

function buildOptions(selectedProfile) {
  const functionalThresholds = {
    checks: ["rate==1"],
    pix_functional_failures: ["count==0"],
    pix_processing_timeouts: ["count==0"],
  };

  if (selectedProfile === "load") {
    return {
      scenarios: {
        load: {
          executor: "ramping-vus",
          exec: "runLoadTest",
          startVUs: 0,
          stages: [
            { duration: __ENV.RAMP_UP || "10s", target: Number(__ENV.VUS || "10") },
            { duration: __ENV.DURATION || "30s", target: Number(__ENV.VUS || "10") },
            { duration: __ENV.RAMP_DOWN || "10s", target: 0 },
          ],
          gracefulRampDown: "15s",
        },
      },
      thresholds: {
        checks: ["rate>0.99"],
        http_req_failed: ["rate<0.01"],
        pix_create_duration: [`p(95)<${apiP95ThresholdMs}`],
        pix_end_to_end_duration: [`p(95)<${endToEndP95ThresholdMs}`],
        pix_processing_failures: ["rate<0.01"],
        pix_processing_timeouts: ["count==0"],
      },
    };
  }

  const execByProfile = {
    full: "runFullSuite",
    smoke: "runSmokeSuite",
    resilience: "runResilienceSuite",
  };
  const exec = execByProfile[selectedProfile];

  if (!exec) {
    throw new Error(`PROFILE invalido: ${selectedProfile}. Use full, smoke, resilience ou load.`);
  }

  return {
    scenarios: {
      functional: {
        executor: "shared-iterations",
        exec,
        vus: 1,
        iterations: 1,
        maxDuration: "10m",
      },
    },
    thresholds: functionalThresholds,
  };
}

function testHealth() {
  const scenario = "health";
  scenariosExecuted.add(1, { scenario });

  const response = http.get(`${baseUrl}/actuator/health`, requestParams(scenario, "GET /actuator/health"));
  const body = parseJson(response);
  assertScenario(scenario, check(response, {
    "health retorna HTTP 200": (result) => result.status === 200,
    "health retorna UP": () => body?.status === "UP",
  }));
}

function testSuccess() {
  const scenario = "success";
  scenariosExecuted.add(1, { scenario });

  const result = createAndWait({
    scenario,
    transactionId: uniqueId("success"),
    pixKey: "cliente@email.com",
    expectedStatus: "COMPLETED",
    trackPerformance: false,
  });
  assertScenario(scenario, result.success);
}

function testIdempotency() {
  const scenario = "idempotency";
  scenariosExecuted.add(1, { scenario });

  const transactionId = uniqueId("idempotency");
  const firstResult = createAndWait({
    scenario,
    transactionId,
    pixKey: "cliente@email.com",
    expectedStatus: "COMPLETED",
    trackPerformance: false,
  });
  assertScenario(scenario, firstResult.success);

  const duplicateResponse = createPix(transactionId, "cliente@email.com", scenario);
  const duplicateBody = parseJson(duplicateResponse);
  assertScenario(scenario, check(duplicateResponse, {
    "idempotencia retorna HTTP 202": (result) => result.status === 202,
    "idempotencia preserva transactionId": () => duplicateBody?.transactionId === transactionId,
    "idempotencia preserva status final": () => duplicateBody?.status === "COMPLETED",
    "idempotencia preserva createdAt": () => duplicateBody?.createdAt === firstResult.body?.createdAt,
  }));
}

function testInvalidRequest() {
  const scenario = "invalid_request";
  scenariosExecuted.add(1, { scenario });

  const response = http.post(
    `${baseUrl}/pix`,
    JSON.stringify({
      transactionId: "",
      amount: 0,
      pixKey: "",
      description: "Requisicao invalida k6",
    }),
    {
      ...requestParams(scenario, "POST /pix invalid", [400]),
      headers: {
        "Content-Type": "application/json",
      },
    },
  );
  const body = parseJson(response);
  assertScenario(scenario, check(response, {
    "requisicao invalida retorna HTTP 400": (result) => result.status === 400,
    "requisicao invalida retorna INVALID_REQUEST": () => body?.code === "INVALID_REQUEST",
  }));
}

function testNotFound() {
  const scenario = "not_found";
  scenariosExecuted.add(1, { scenario });

  const response = http.get(
    `${baseUrl}/pix/${uniqueId("missing")}`,
    requestParams(scenario, "GET /pix/:transactionId missing", [404]),
  );
  const body = parseJson(response);
  assertScenario(scenario, check(response, {
    "consulta inexistente retorna HTTP 404": (result) => result.status === 404,
    "consulta inexistente retorna PIX_NOT_FOUND": () => body?.code === "PIX_NOT_FOUND",
  }));
}

function testPartnerFailure(testCase) {
  scenariosExecuted.add(1, { scenario: testCase.name });

  const result = createAndWait({
    scenario: testCase.name,
    transactionId: uniqueId(testCase.name),
    pixKey: testCase.pixKey,
    expectedStatus: testCase.expectedStatus,
    trackPerformance: false,
  });
  assertScenario(testCase.name, result.success);
}

function createAndWait({
  scenario,
  transactionId,
  pixKey,
  expectedStatus,
  trackPerformance,
}) {
  const startedAt = Date.now();
  const createResponse = createPix(transactionId, pixKey, scenario);
  const createBody = parseJson(createResponse);

  if (trackPerformance) {
    createDuration.add(createResponse.timings.duration, { scenario });
  }

  const created = check(createResponse, {
    [`${scenario}: POST retorna HTTP 202`]: (result) => result.status === 202,
    [`${scenario}: POST retorna transactionId`]: () => createBody?.transactionId === transactionId,
    [`${scenario}: POST retorna status valido`]: () =>
      createBody?.status === "PROCESSING" || createBody?.status === expectedStatus,
    [`${scenario}: POST retorna createdAt`]: () => Boolean(createBody?.createdAt),
  });

  if (!created) {
    return { success: false, body: createBody };
  }

  const finalResult = waitForFinalStatus(transactionId, scenario);
  if (!finalResult) {
    return { success: false, body: null };
  }

  if (trackPerformance) {
    endToEndDuration.add(Date.now() - startedAt, {
      scenario,
      outcome: finalResult.status.toLowerCase(),
    });
  }

  const correctFinalStatus = check(finalResult, {
    [`${scenario}: status final e ${expectedStatus}`]: (result) =>
      result.status === expectedStatus,
  });

  return {
    success: correctFinalStatus,
    body: finalResult,
  };
}

function createPix(transactionId, pixKey, scenario) {
  return http.post(
    `${baseUrl}/pix`,
    JSON.stringify({
      transactionId,
      amount: 150.75,
      pixKey,
      description: `Cenario k6 ${scenario}`,
    }),
    {
      ...requestParams(scenario, "POST /pix"),
      headers: {
        "Content-Type": "application/json",
        "X-Correlation-Id": `k6-${transactionId}`,
      },
    },
  );
}

function waitForFinalStatus(transactionId, scenario) {
  const deadline = Date.now() + processingTimeoutSeconds * 1000;

  while (Date.now() < deadline) {
    sleep(pollIntervalSeconds);

    const response = http.get(
      `${baseUrl}/pix/${transactionId}`,
      requestParams(scenario, "GET /pix/:transactionId"),
    );
    const body = parseJson(response);
    const validResponse = check(response, {
      [`${scenario}: consulta retorna HTTP 200`]: (result) => result.status === 200,
      [`${scenario}: consulta preserva transactionId`]: () => body?.transactionId === transactionId,
    });

    if (!validResponse) {
      return null;
    }

    if (body.status === "COMPLETED" || body.status === "FAILED") {
      return body;
    }
  }

  processingTimeouts.add(1, { scenario });
  check(null, {
    [`${scenario}: processamento termina em ${processingTimeoutSeconds}s`]: () => false,
  });
  return null;
}

function requestParams(scenario, name, expectedHttpStatuses = [200, 202]) {
  return {
    tags: {
      scenario,
      name,
    },
    responseCallback: http.expectedStatuses(...expectedHttpStatuses),
  };
}

function assertScenario(scenario, succeeded) {
  if (!succeeded) {
    functionalFailures.add(1, { scenario });
  }
}

function uniqueId(prefix) {
  return `k6-${prefix}-${Date.now()}-${__VU}-${__ITER}`;
}

function parseJson(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

export function handleSummary(data) {
  const outputPath = `tests/k6/summary-${profile}.json`;
  return {
    stdout: summaryText(data, outputPath),
    [outputPath]: JSON.stringify(data, null, 2),
  };
}

function summaryText(data, outputPath) {
  const metrics = data.metrics;
  const lines = [
    "",
    `Resumo PIX k6 - perfil ${profile}`,
    `Cenarios executados: ${metricValue(metrics.pix_scenarios_executed, "count") ?? 0}`,
    `Checks aprovados: ${formatPercent(metricValue(metrics.checks, "rate"))}`,
    `Falhas funcionais: ${metricValue(metrics.pix_functional_failures, "count") ?? 0}`,
    `Timeouts: ${metricValue(metrics.pix_processing_timeouts, "count") ?? 0}`,
  ];

  if (profile === "load") {
    lines.push(
      `P95 criacao: ${formatMilliseconds(metricValue(metrics.pix_create_duration, "p(95)"))}`,
      `P95 fim a fim: ${formatMilliseconds(metricValue(metrics.pix_end_to_end_duration, "p(95)"))}`,
    );
  }

  lines.push(`Relatorio completo: ${outputPath}`, "");
  return lines.join("\n");
}

function metricValue(metric, key) {
  return metric?.values?.[key];
}

function formatPercent(value) {
  return value === undefined ? "sem dados" : `${(value * 100).toFixed(2)}%`;
}

function formatMilliseconds(value) {
  return value === undefined ? "sem dados" : `${value.toFixed(2)} ms`;
}
