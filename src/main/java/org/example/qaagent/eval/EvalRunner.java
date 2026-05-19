package org.example.qaagent.eval;

import org.example.qaagent.model.ChatRequest;
import org.example.qaagent.model.ChatResponse;
import org.example.qaagent.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Component
public class EvalRunner {
    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final ChatService chatService;
    private final FaithfulnessEvaluator faithfulnessEval;
    private final ContextPrecisionEvaluator contextPrecisionEval;
    private final ComplianceEvaluator complianceEval;
    private final StyleConsistencyEvaluator styleEval;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvalRunner(ChatService chatService,
                       FaithfulnessEvaluator faithfulnessEval,
                       ContextPrecisionEvaluator contextPrecisionEval,
                       ComplianceEvaluator complianceEval,
                       StyleConsistencyEvaluator styleEval) {
        this.chatService = chatService;
        this.faithfulnessEval = faithfulnessEval;
        this.contextPrecisionEval = contextPrecisionEval;
        this.complianceEval = complianceEval;
        this.styleEval = styleEval;
    }

    public void runEvaluation(String datasetPath, String outputDir) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(datasetPath));
        Files.createDirectories(Path.of(outputDir));

        List<Map<String, Object>> results = new ArrayList<>();
        double sumFaithfulness = 0, sumPrecision = 0, sumCompliance = 0, sumStyle = 0;
        int refusalTP = 0, refusalTN = 0, refusalFP = 0, refusalFN = 0;
        int total = 0;

        for (String line : lines) {
            if (line.isBlank()) continue;
            JsonNode evalItem = mapper.readTree(line);
            String query = evalItem.get("query").asText();
            boolean shouldRefuse = evalItem.get("should_refuse").asBoolean();
            String reference = evalItem.has("reference") && !evalItem.get("reference").isNull()
                    ? evalItem.get("reference").asText() : null;

            ChatResponse response;
            try {
                response = chatService.chat(new ChatRequest(query, null, "eval-runner"));
            } catch (Exception e) {
                log.error("Eval failed for query '{}': {}", query, e.getMessage());
                continue;
            }

            if (shouldRefuse && response.refused()) refusalTP++;
            else if (shouldRefuse && !response.refused()) refusalFN++;
            else if (!shouldRefuse && response.refused()) refusalFP++;
            else refusalTN++;

            double faithfulness = 0, precision = 0, compliance = 0, style = 0;

            if (!response.refused() && !shouldRefuse && reference != null) {
                try {
                    String context = response.sources().stream()
                            .map(s -> s.content())
                            .reduce("", (a, b) -> a + "\n\n" + b);

                    faithfulness = faithfulnessEval.evaluate(context, response.answer());
                    precision = contextPrecisionEval.evaluate(query, response.sources());
                    compliance = complianceEval.evaluate(query, reference, response.answer());
                    style = styleEval.evaluate(query, response.answer());

                    sumFaithfulness += faithfulness;
                    sumPrecision += precision;
                    sumCompliance += compliance;
                    sumStyle += style;
                    total++;
                } catch (Exception e) {
                    log.error("Eval scoring failed for query '{}': {}", query, e.getMessage());
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("refused", response.refused());
            result.put("shouldRefuse", shouldRefuse);
            result.put("faithfulness", faithfulness);
            result.put("contextPrecision", precision);
            result.put("compliance", compliance);
            result.put("styleConsistency", style);
            result.put("latencyMs", response.latencyMs());
            results.add(result);

            log.info("Eval: query='{}' faith={} prec={} comp={} style={}",
                      query, String.format("%.2f", faithfulness), String.format("%.2f", precision),
                      String.format("%.2f", compliance), String.format("%.2f", style));
        }

        double avgFaith = total > 0 ? sumFaithfulness / total : 0;
        double avgPrec  = total > 0 ? sumPrecision / total : 0;
        double avgComp  = total > 0 ? sumCompliance / total : 0;
        double avgStyle = total > 0 ? sumStyle / total : 0;
        int refusalTotal = refusalTP + refusalTN + refusalFP + refusalFN;
        double refusalAccuracy = refusalTotal > 0 ? (double)(refusalTP + refusalTN) / refusalTotal : 0;

        writeCSV(results, Path.of(outputDir, "evaluation_report.csv"));

        String summary = String.format("""
            # Evaluation Report

            ## Aggregate Metrics
            | Metric | Score | Target |
            |--------|-------|--------|
            | Faithfulness | %.3f | >= 0.85 |
            | Context Precision | %.3f | >= 0.70 |
            | Answer Compliance | %.1f%% | >= 80%% |
            | Style Consistency | %.1f%% | >= 80%% |
            | Refusal Appropriateness | %.1f%% | >= 80%% |

            ## Refusal Matrix
            | | Predicted Refuse | Predicted Answer |
            |---|---|---|
            | Should Refuse | %d (TP) | %d (FN) |
            | Should Answer | %d (FP) | %d (TN) |

            Total evaluated: %d (answered: %d, refused: %d)
            """,
            avgFaith, avgPrec, avgComp * 100, avgStyle * 100, refusalAccuracy * 100,
            refusalTP, refusalFN, refusalFP, refusalTN,
            results.size(), total, results.size() - total
        );
        Files.writeString(Path.of(outputDir, "evaluation_report.md"), summary);
        log.info("Evaluation complete. Report saved to {}", outputDir);
    }

    private void writeCSV(List<Map<String, Object>> results, Path path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println("query,refused,shouldRefuse,faithfulness,contextPrecision,compliance,styleConsistency,latencyMs");
            for (Map<String, Object> r : results) {
                pw.printf("\"%s\",%s,%s,%.3f,%.3f,%.3f,%.3f,%d%n",
                    ((String)r.get("query")).replace("\"", "\"\""),
                    r.get("refused"), r.get("shouldRefuse"),
                    r.get("faithfulness"), r.get("contextPrecision"),
                    r.get("compliance"), r.get("styleConsistency"),
                    r.get("latencyMs"));
            }
        }
    }
}
