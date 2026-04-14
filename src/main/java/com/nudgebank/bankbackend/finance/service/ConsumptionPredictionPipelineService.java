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

        try {
            Process process = processBuilder.start();
            String output = readOutput(process);
            int exitCode = process.waitFor();

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
