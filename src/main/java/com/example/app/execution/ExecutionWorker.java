package com.example.app.execution;

import java.time.LocalDateTime;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExecutionWorker {

    private final ExecutionRepository executionRepository;
    private final PistonExecutionService pistonExecutionService;

    @RabbitListener(queues = "${rabbitmq.queue.execution}")
    public void processExecution(ExecutionMessage message) {
        Long executionId = message.getExecutionId();

        log.info("Received execution request: executionId={}, language={}", 
                 executionId, message.getLanguage());

        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new RuntimeException("Execution not found"));

        if (execution.getStatus() == ExecutionStatus.SUCCESS || 
            execution.getStatus() == ExecutionStatus.FAILED ||
            execution.getStatus() == ExecutionStatus.TIMEOUT) {
            log.info("Execution {} already completed, skipping", executionId);
            return;
        }

        execution.setStatus(ExecutionStatus.RUNNING);
        executionRepository.save(execution);

        log.info("Starting execution for executionId={}", executionId);

        ExecutionResult result = pistonExecutionService.execute(
                message.getLanguage(), message.getCode(), message.getInput(), executionId);

        log.info("Execution {} completed with status={} in {}ms", 
                 executionId, result.getStatus(), result.getExecutionTime());

        if (result.getStatus() == ExecutionStatus.FAILED || 
            result.getStatus() == ExecutionStatus.TIMEOUT) {
            log.warn("Execution {} did not succeed: {}", executionId, result.getError());
        }

        execution.setOutput(result.getOutput());
        execution.setError(result.getError());
        execution.setExecutionTime(result.getExecutionTime());
        execution.setStatus(result.getStatus());
        execution.setCompletedAt(LocalDateTime.now());
        executionRepository.save(execution);

        log.info("Execution {} saved to database successfully", executionId);
    }
}