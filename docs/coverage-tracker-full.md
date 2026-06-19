PROJECT COVERAGE: lines=99.81% | branches=97.67%

MODULE        | CLASS                                    | LINES% | BRANCHES% | STATUS
-----------------------------------------------------------------------------------------------
pix-api       | PixRequest                               |   100% |       N/A | GATE_MET
pix-api       | PixTransactionStatus                     |   100% |       N/A | GATE_MET
pix-api       | PixTransaction                           |   100% |       N/A | GATE_MET
pix-api       | PixApplication                           |   100% |       N/A | GATE_MET
pix-api       | OutboxPublisher                          |   100% |      100% | GATE_MET
pix-api       | PixStatusResponse                        |   100% |       N/A | GATE_MET
pix-api       | PixResponse                              |   100% |       N/A | GATE_MET
pix-api       | MicrometerTelemetryAdapter               |   100% |       N/A | GATE_MET
pix-api       | CorrelationIdFilter                      |   100% |      100% | GATE_MET
pix-api       | RedisPixCacheAdapter                     |   100% |      100% | GATE_MET
pix-api       | CachedPixStatus                          |   100% |       N/A | GATE_MET
pix-api       | ResourceNotFoundException                |   100% |       N/A | GATE_MET
pix-api       | ApiExceptionHandler                      |   100% |       N/A | GATE_MET
pix-api       | PixController                            |   100% |      100% | GATE_MET
pix-api       | PixUseCase                               |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-api       | CreatePixCommand                         |   100% |       N/A | GATE_MET
pix-api       | PixTransactionCrudRepository             |     0% |       N/A | BLOCKED - boundary: repository interface has no executable bytecode
pix-api       | OutboxEventCrudRepository                |     0% |       N/A | BLOCKED - boundary: repository interface has no executable bytecode
pix-api       | OutboxEvent                              |   100% |       N/A | GATE_MET
pix-api       | PixTransactionRepositoryAdapter          |   100% |       N/A | GATE_MET
pix-api       | OutboxEventPersistenceModel              |   100% |       N/A | GATE_MET
pix-api       | OutboxRepositoryAdapter                  |   100% |       N/A | GATE_MET
pix-api       | PixTransactionPersistenceModel           |   100% |       N/A | GATE_MET
pix-api       | PixCreationTransactionService            |   100% |       N/A | GATE_MET
pix-api       | PixRequestedEvent                        |   100% |       N/A | GATE_MET
pix-api       | PixApplicationService                    |   100% |      100% | GATE_MET
pix-api       | StatusCachePort                          |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-api       | OutboxEventPort                          |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-api       | TelemetryPort                            |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-api       | PixTransactionPort                       |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-api       | IdempotencyCachePort                     |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-processor | TelemetryPort                            |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-processor | PartnerPixPort                           |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-processor | ProcessingAttemptPort                    |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-processor | DlqPublisherPort                         |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-processor | PixTransactionPort                       |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-processor | StatusCacheInvalidationPort              |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode
pix-processor | DlqMessage                               |   100% |       N/A | GATE_MET
pix-processor | PixProcessingService                     |    99% |       93% | GATE_MET
pix-processor | PixRequestedKafkaListener                |   100% |       N/A | GATE_MET
pix-processor | PixTransaction                           |   100% |      100% | GATE_MET
pix-processor | PartnerPixResponse                       |   100% |       N/A | GATE_MET
pix-processor | PixTransactionStatus                     |   100% |       N/A | GATE_MET
pix-processor | PixRequestedEvent                        |   100% |       N/A | GATE_MET
pix-processor | PartnerPixException                      |   100% |       N/A | GATE_MET
pix-processor | HttpConfig                               |   100% |       N/A | GATE_MET
pix-processor | RedisStatusCacheInvalidationAdapter      |   100% |       N/A | GATE_MET
pix-processor | ProcessingAttemptRepositoryAdapter       |   100% |       N/A | GATE_MET
pix-processor | PixTransactionCrudRepository             |     0% |       N/A | BLOCKED - boundary: repository interface has no executable bytecode
pix-processor | ProcessingAttemptCrudRepository          |     0% |       N/A | BLOCKED - boundary: repository interface has no executable bytecode
pix-processor | PixTransactionRepositoryAdapter          |   100% |       N/A | GATE_MET
pix-processor | ProcessingAttemptPersistenceModel        |   100% |       N/A | GATE_MET
pix-processor | PixTransactionPersistenceModel           |   100% |       N/A | GATE_MET
pix-processor | KafkaDlqPublisherAdapter                 |   100% |       N/A | GATE_MET
pix-processor | PartnerPixRequest                        |   100% |       N/A | GATE_MET
pix-processor | PartnerPixClient                         |   100% |      100% | GATE_MET
pix-processor | MicrometerTelemetryAdapter               |   100% |      100% | GATE_MET
pix-processor | PixProcessorApplication                  |   100% |       N/A | GATE_MET
pix-processor | ProcessPixResult                         |   100% |       N/A | GATE_MET
pix-processor | ProcessPixUseCase                        |     0% |       N/A | BLOCKED - boundary: interface has no executable bytecode

SUMMARY: 43 GATE_MET / 17 BLOCKED - boundary (interfaces without executable bytecode) / 0 BOUNDARY_VIOLATION
