# Testes e validação

## Testes automatizados Java

```powershell
.\mvnw.cmd clean test
```

O relatório consolidado de cobertura está em [coverage-tracker-full.md](coverage-tracker-full.md).

## Cenários do WireMock

O processor chama `POST /partner/pix`. A `pixKey` seleciona o comportamento:

| `pixKey` | Comportamento |
|---|---|
| `cliente@email.com` | `200 OK` após 2 segundos |
| `partner-error@pix.test` | `500 Internal Server Error` |
| `partner-request-timeout@pix.test` | `408 Request Timeout` |
| `partner-rate-limit@pix.test` | `429 Too Many Requests` |
| `partner-timeout@pix.test` | atraso de 10 segundos |
| `partner-invalid@pix.test` | `200 OK` com resposta inválida |

## Teste de sucesso

```powershell
curl.exe -X POST http://localhost:8080/pix `
  -H "Content-Type: application/json" `
  -H "X-Correlation-Id: teste-sucesso-001" `
  -d '{\"transactionId\":\"tx-sucesso-001\",\"amount\":150.75,\"pixKey\":\"cliente@email.com\",\"description\":\"Cenario de sucesso\"}'

curl.exe http://localhost:8080/pix/tx-sucesso-001
```

Resultado esperado:

- criação com `202 Accepted`;
- status final `COMPLETED`;
- tentativa `COMPLETED`;
- outbox `PUBLISHED`;
- nenhuma mensagem na DLQ.

## Idempotência

Repita o POST com o mesmo `transactionId` e consulte:

```powershell
docker compose exec postgres psql -U pix -d pixdb -c "select count(*) from pix.pix_transaction where transaction_id = 'tx-sucesso-001';"
docker compose exec postgres psql -U pix -d pixdb -c "select count(*) from pix.outbox_event where aggregate_id = 'tx-sucesso-001';"
```

Ambas as consultas devem retornar `1`.

## Validação e consulta inexistente

```powershell
curl.exe -i -X POST http://localhost:8080/pix `
  -H "Content-Type: application/json" `
  -d '{\"transactionId\":\"\",\"amount\":0,\"pixKey\":\"\"}'

curl.exe -i http://localhost:8080/pix/transacao-inexistente
```

Resultados esperados: `400 INVALID_REQUEST` e `404 PIX_NOT_FOUND`.

## Falha, retentativas e DLQ

```powershell
curl.exe -X POST http://localhost:8080/pix `
  -H "Content-Type: application/json" `
  -H "X-Correlation-Id: teste-erro-500" `
  -d '{\"transactionId\":\"tx-erro-500\",\"amount\":50.00,\"pixKey\":\"partner-error@pix.test\",\"description\":\"Erro 500\"}'
```

Resultado esperado:

- tentativas intermediárias `RETRYING`;
- última tentativa `FAILED`;
- transação `FAILED`;
- mensagem em `pix.dlq`.

## Redis indisponível

```powershell
docker compose stop redis
# Execute novamente um fluxo de sucesso.
docker compose start redis
```

A API deve continuar funcionando com PostgreSQL.

## k6

Perfis:

| Perfil | Objetivo |
|---|---|
| `full` | todos os cenários funcionais e de resiliência |
| `smoke` | saúde e fluxos essenciais |
| `resilience` | erros, timeout e respostas inválidas |
| `load` | carga gradual no fluxo de sucesso |

Exemplos:

```powershell
docker compose restart pix-processor
k6 run -e PROFILE=full .\tests\k6\pix-load-test.js

k6 run -e PROFILE=smoke .\tests\k6\pix-load-test.js

k6 run `
  -e PROFILE=load `
  -e VUS=25 `
  -e DURATION=2m `
  -e API_P95_THRESHOLD_MS=50 `
  -e END_TO_END_P95_THRESHOLD_MS=4000 `
  .\tests\k6\pix-load-test.js
```

Sem k6 instalado:

```powershell
docker run --rm -i `
  -v "${PWD}:/workspace" `
  -w /workspace `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e PROFILE=full `
  grafana/k6 run tests/k6/pix-load-test.js
```

Cada execução gera `tests/k6/summary-<perfil>.json`. Reinicie o processor antes dos perfis funcionais para limpar o estado em memória do circuit breaker.

## Consultas úteis

```powershell
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.pix_transaction;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.pix_processing_attempt order by created_at;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.outbox_event;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from flyway_schema_history;"
```

