package com.fitmind.module.exercise.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FitnessVisionRealtimeProcessSession implements AutoCloseable {

    private static final int STDERR_LIMIT = 6000;

    private final ObjectMapper objectMapper;
    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final ExecutorService ioExecutor;
    private final StringBuilder stderrBuffer = new StringBuilder();
    private final long timeoutSeconds;
    private String readyMessage;

    private FitnessVisionRealtimeProcessSession(
            ObjectMapper objectMapper,
            Process process,
            BufferedWriter stdin,
            BufferedReader stdout,
            ExecutorService ioExecutor,
            long timeoutSeconds,
            String readyMessage
    ) {
        this.objectMapper = objectMapper;
        this.process = process;
        this.stdin = stdin;
        this.stdout = stdout;
        this.ioExecutor = ioExecutor;
        this.timeoutSeconds = timeoutSeconds;
        this.readyMessage = readyMessage;
    }

    public static FitnessVisionRealtimeProcessSession start(
            ObjectMapper objectMapper,
            Path pythonExecutable,
            Path scriptPath,
            Path modelPath,
            Path classMappingPath,
            Path workingDirectory,
            long timeoutSeconds
    ) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable.toString());
        command.add(scriptPath.toString());
        command.add("--model-path");
        command.add(modelPath.toString());
        command.add("--class-mapping");
        command.add(classMappingPath.toString());
        command.add("--top-k");
        command.add("3");
        command.add("--min-frames");
        command.add("20");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("TF_CPP_MIN_LOG_LEVEL", "2");

        Process process = processBuilder.start();
        BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        ExecutorService ioExecutor = Executors.newFixedThreadPool(2);

        FitnessVisionRealtimeProcessSession session = new FitnessVisionRealtimeProcessSession(
                objectMapper,
                process,
                stdin,
                stdout,
                ioExecutor,
                timeoutSeconds,
                ""
        );
        session.startStderrPump();

        String readyLine;
        try {
            readyLine = session.readLineWithTimeout();
        } catch (Exception e) {
            session.close();
            throw e;
        }

        if (readyLine == null || readyLine.isBlank()) {
            String stderr = session.getStderrTail();
            session.close();
            throw new IllegalStateException(stderr.isBlank()
                    ? "Realtime vision process started without a ready message"
                    : stderr);
        }
        session.readyMessage = readyLine;
        return session;
    }

    public String getReadyMessage() {
        return readyMessage;
    }

    public synchronized String analyzeFrame(String imageBase64) {
        ensureAlive();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "frame");
        payload.put("imageBase64", imageBase64);
        return exchange(payload);
    }

    public synchronized String sendPing() {
        ensureAlive();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "ping");
        return exchange(payload);
    }

    private String exchange(ObjectNode payload) {
        try {
            stdin.write(objectMapper.writeValueAsString(payload));
            stdin.write("\n");
            stdin.flush();
            String line = readLineWithTimeout();
            if (line == null || line.isBlank()) {
                throw new IllegalStateException(buildFailureMessage("Realtime vision process returned an empty response"));
            }
            return line;
        } catch (Exception e) {
            throw new IllegalStateException(buildFailureMessage("Realtime vision process failed: " + e.getMessage()), e);
        }
    }

    private void startStderrPump() {
        ioExecutor.submit(() -> {
            try (BufferedReader stderr = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stderr.readLine()) != null) {
                    synchronized (stderrBuffer) {
                        if (stderrBuffer.length() + line.length() + 1 > STDERR_LIMIT) {
                            int overflow = stderrBuffer.length() + line.length() + 1 - STDERR_LIMIT;
                            stderrBuffer.delete(0, Math.min(overflow, stderrBuffer.length()));
                        }
                        stderrBuffer.append(line).append('\n');
                    }
                    log.debug("fitness realtime stderr: {}", line);
                }
            } catch (IOException e) {
                log.debug("Failed to consume realtime vision stderr", e);
            }
        });
    }

    private String readLineWithTimeout() throws Exception {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                return stdout.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, ioExecutor);
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private void ensureAlive() {
        if (process.isAlive()) {
            return;
        }
        throw new IllegalStateException(buildFailureMessage("Realtime vision process is not running"));
    }

    private String buildFailureMessage(String fallback) {
        String stderr = getStderrTail();
        return stderr.isBlank() ? fallback : stderr;
    }

    private String getStderrTail() {
        synchronized (stderrBuffer) {
            return stderrBuffer.toString().trim();
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (process.isAlive()) {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("type", "close");
                stdin.write(objectMapper.writeValueAsString(payload));
                stdin.write("\n");
                stdin.flush();
            }
        } catch (Exception e) {
            log.debug("Failed to request realtime vision shutdown", e);
        }

        try {
            stdin.close();
        } catch (IOException e) {
            log.debug("Failed to close realtime vision stdin", e);
        }
        try {
            stdout.close();
        } catch (IOException e) {
            log.debug("Failed to close realtime vision stdout", e);
        }

        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        ioExecutor.shutdownNow();
    }
}
