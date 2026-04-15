package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.finance.config.AiPipelineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsumptionPredictionPipelineService {

    private final AiPipelineProperties aiPipelineProperties;

    public void runPredictionPipeline() {
        String pythonCommand = resolvePythonCommand();
        runCommand(List.of(
                pythonCommand,
                "model/export_source_tables.py"
        ));
        runCommand(List.of(
                pythonCommand,
                "model/persist_consumer_prediction.py"
        ));
    }

    private String resolvePythonCommand() {
        Path workingDir = Path.of(aiPipelineProperties.workingDir()).toAbsolutePath().normalize();
        Path configuredPath = Path.of(aiPipelineProperties.pythonCommand());
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize().toString();
        }
        return workingDir.resolve(configuredPath).normalize().toString();
    }

    private void runCommand(List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(aiPipelineProperties.workingDir()));
        processBuilder.redirectErrorStream(true);

        ExecutorService outputReaderExecutor = Executors.newSingleThreadExecutor();
        try {
            Process process = processBuilder.start();
            Future<String> outputFuture = outputReaderExecutor.submit(() -> readOutput(process));

            boolean finished = process.waitFor(aiPipelineProperties.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }

                throw new IllegalStateException(
                        "AI pipeline command timed out after %d seconds. command=%s"
                                .formatted(aiPipelineProperties.timeoutSeconds(), String.join(" ", command))
                );
            }

            int exitCode = process.exitValue();
            String output = getOutput(outputFuture, command);

            if (exitCode != 0) {
                throw new IllegalStateException(
                        "AI pipeline command failed. command=" + String.join(" ", command) + ", output=" + output
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to start AI pipeline command: " + String.join(" ", command),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "AI pipeline command interrupted: " + String.join(" ", command),
                    e
            );
        } finally {
            outputReaderExecutor.shutdownNow();
        }
    }

    private String getOutput(Future<String> outputFuture, List<String> command) throws InterruptedException {
        try {
            return outputFuture.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Failed to read AI pipeline output: " + String.join(" ", command),
                    e.getCause()
            );
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Timed out while reading AI pipeline output: " + String.join(" ", command),
                    e
            );
        }
    }

    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!output.isEmpty()) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
            }
        }
        return output.toString();
    }
}
