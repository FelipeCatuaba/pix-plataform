# Observabilidade e operação

As aplicações usam o OpenTelemetry Java Agent para enviar métricas e traces por OTLP ao Datadog Agent. Os logs dos containers `pix-api` e `pix-processor` também são coletados.

## Comandos operacionais

```powershell
docker compose ps
docker compose logs -f pix-api pix-processor
docker compose exec datadog-agent agent status
```

## Logs correlacionados

Em **Logs > Explorer**:

```text
env:local (service:pix-api OR service:pix-processor) @transactionId:tx-sucesso-001
```

Se o campo ainda não estiver configurado como facet:

```text
env:local (service:pix-api OR service:pix-processor) "tx-sucesso-001"
```

Campos principais:

- `transactionId`;
- `correlationId`;
- `attempt`;
- `status`;
- `errorCode`.

## Métricas

| Métrica | Significado |
|---|---|
| `pix.transaction.end_to_end.duration.seconds` | entrada HTTP até conclusão e acknowledgment Kafka |
| `pix.outbox.duration.seconds` | entrada HTTP até publicação no Kafka |
| `pix.processor.duration.seconds` | consumo Kafka até conclusão e acknowledgment |
| `pix.partner.latency` | duração da chamada ao parceiro |
| `pix.api.requests` | requisições recebidas |
| `pix.api.errors` | erros da API |
| `pix.transactions.created` | transações criadas |
| `pix.transactions.completed` | transações concluídas |
| `pix.transactions.failed` | falhas finais |
| `pix.processor.consumed` | eventos consumidos |
| `pix.processor.retries` | retentativas |
| `pix.processor.dlq` | mensagens enviadas à DLQ |
| `pix.partner.errors` | erros do parceiro |
| `pix.outbox.pending` | eventos pendentes selecionados |
| `pix.outbox.published` | eventos publicados |

## Tempo total do fluxo

No **Metrics Explorer**, consulte:

```text
p95:pix.transaction.end_to_end.duration.seconds{env:local} by {outcome}
p95:pix.outbox.duration.seconds{env:local}
p95:pix.processor.duration.seconds{env:local} by {outcome}
p95:pix.partner.latency{env:local}
```

`pix.transaction.end_to_end.duration.seconds` inclui persistência, outbox, espera no Kafka, processamento, chamadas ao parceiro e acknowledgment. Não inclui o tempo até o próximo polling do cliente.

## Dashboard sugerido

| Widget | Consulta |
|---|---|
| P95 total | `p95:pix.transaction.end_to_end.duration.seconds{env:local} by {outcome}` |
| P95 Outbox | `p95:pix.outbox.duration.seconds{env:local}` |
| P95 processor | `p95:pix.processor.duration.seconds{env:local} by {outcome}` |
| P95 parceiro | `p95:pix.partner.latency{env:local}` |
| Concluídas | `sum:pix.transactions.completed{env:local}.as_count()` |
| Falhas | `sum:pix.transactions.failed{env:local}.as_count()` |
| Retentativas | `sum:pix.processor.retries{env:local}.as_count()` |
| DLQ | `sum:pix.processor.dlq{env:local}.as_count()` |

As durações são publicadas em segundos. Não use `transactionId` como tag de métrica, pois isso cria alta cardinalidade; use logs para investigar uma transação específica.

## Traces

Em **APM > Trace Explorer**:

```text
env:local (service:pix-api OR service:pix-processor)
```

Os traces detalham as etapas técnicas. Para acompanhar todo o fluxo assíncrono, use a métrica end-to-end.

