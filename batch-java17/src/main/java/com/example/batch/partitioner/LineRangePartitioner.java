package com.example.batch.partitioner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class LineRangePartitioner implements Partitioner {

    @Value("${batch.input-file}")
    private String location;

    @Value("${batch.grid-size:8}")
    private int gridSize;

    private volatile int totalLines = -1;

    @Override
    public Map<String, ExecutionContext> partition(int ignored) {
        int total = countLines();
        int dataLines = total - 1;
        if (dataLines <= 0) return new HashMap<>();

        int perPartition = (int) Math.ceil((double) dataLines / gridSize);
        Map<String, ExecutionContext> result = new HashMap<>();

        for (int i = 0; i < gridSize; i++) {
            int start = 1 + i * perPartition;
            int end = Math.min(1 + (i + 1) * perPartition, total);
            if (start >= end) break;

            ExecutionContext ec = new ExecutionContext();
            ec.putInt("startLine", start);
            ec.putInt("endLine", end);
            // Utilisé par PersonSkipListener pour tracer la partition d'origine.
            ec.putString("partitionId", "partition-" + i);
            result.put("partition-" + i, ec);
        }
        return result;
    }

    private int countLines() {
        if (totalLines >= 0) return totalLines;
        try {
            Resource resource = new DefaultResourceLoader().getResource(location);
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                int n = 0;
                while (r.readLine() != null) n++;
                totalLines = n;
                return n;
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Impossible de lire le fichier d'entrée '" + location
                            + "' (propriété batch.input-file / BATCH_INPUT_FILE) : "
                            + e.getMessage(), e);
        }
    }
}