package com.interview.agent.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.interview.agent.agent.EvaluationAgent;
import com.interview.agent.agent.InterviewerAgent;
import com.interview.agent.agent.JdAnalysisAgent;
import com.interview.agent.agent.QuestionPlannerAgent;
import com.interview.agent.agent.ResumeMatchAgent;
import com.interview.agent.agent.ReviewPlannerAgent;
import com.interview.agent.config.AppConfig;
import com.interview.agent.memory.LongTermMemoryService;
import com.interview.agent.model.Difficulty;
import com.interview.agent.model.EvaluationReport;
import com.interview.agent.model.InterviewTurn;
import com.interview.agent.model.JdRequirement;
import com.interview.agent.model.MatchReport;
import com.interview.agent.model.Question;
import com.interview.agent.model.ReviewPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewOrchestrator {

    private final JdAnalysisAgent jdAnalysisAgent;
    private final ResumeMatchAgent resumeMatchAgent;
    private final QuestionPlannerAgent questionPlannerAgent;
    private final InterviewerAgent interviewerAgent;
    private final EvaluationAgent evaluationAgent;
    private final ReviewPlannerAgent reviewPlannerAgent;
    private final AnswerBridge answerBridge;
    private final DifficultyController difficultyController;
    private final LongTermMemoryService longTermMemoryService;
    private final AppConfig appConfig;

    private final Map<String, InterviewEventSink> sinks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> quitFlags = new ConcurrentHashMap<>();

    public void registerSink(String sessionId, InterviewEventSink sink) {
        sinks.put(sessionId, sink);
    }

    public void unregister(String sessionId) {
        sinks.remove(sessionId);
        quitFlags.remove(sessionId);
        answerBridge.cancel(sessionId);
    }

    public void requestQuit(String sessionId) {
        quitFlags.put(sessionId, true);
        answerBridge.cancel(sessionId);
    }

    public void submitAnswer(String sessionId, String answer) {
        answerBridge.submit(sessionId, answer);
    }

    public OverAllState run(String sessionId, Long userId, String jdText, String resumeText) throws Exception {
        CompiledGraph graph = buildGraph();
        Map<String, Object> input = new HashMap<>();
        input.put(InterviewStateKeys.SESSION_ID, sessionId);
        input.put(InterviewStateKeys.USER_ID, userId);
        input.put(InterviewStateKeys.JD_TEXT, jdText);
        input.put(InterviewStateKeys.RESUME_TEXT, resumeText);
        input.put(InterviewStateKeys.QUESTION_INDEX, 0);
        input.put(InterviewStateKeys.TURNS, new ArrayList<InterviewTurn>());
        input.put(InterviewStateKeys.DIFFICULTY, Difficulty.MEDIUM.name());
        input.put(InterviewStateKeys.STREAK, 0);
        input.put(InterviewStateKeys.QUIT, false);

        return graph.invoke(input).orElseThrow(() -> new IllegalStateException("Interview graph returned empty state"));
    }

    private CompiledGraph buildGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(InterviewStateKeys.SESSION_ID, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.USER_ID, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.JD_TEXT, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.RESUME_TEXT, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.JD_REQUIREMENT, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.MATCH_REPORT, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.QUESTIONS, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.QUESTION_INDEX, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.TURNS, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.CURRENT_ANSWER, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.FOLLOWUP_PENDING, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.FOLLOWUP_QUESTION, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.LAST_GRADE, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.DIFFICULTY, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.STREAK, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.EVALUATION, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.REVIEW_PLAN, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.PHASE_MESSAGE, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.QUIT, KeyStrategy.REPLACE);
            strategies.put(InterviewStateKeys.NEXT_ROUTE, KeyStrategy.REPLACE);
            return strategies;
        };

        StateGraph graph = new StateGraph("interview-pipeline", keyStrategyFactory);

        graph.addNode("analyze_jd", node_async(this::analyzeJd));
        graph.addNode("match_resume", node_async(this::matchResume));
        graph.addNode("plan_questions", node_async(this::planQuestions));
        graph.addNode("ask_question", node_async(this::askQuestion));
        graph.addNode("grade_answer", node_async(this::gradeAnswer));
        graph.addNode("ask_followup", node_async(this::askFollowup));
        graph.addNode("evaluate", node_async(this::evaluate));
        graph.addNode("review", node_async(this::review));

        graph.addEdge(START, "analyze_jd");
        graph.addEdge("analyze_jd", "match_resume");
        graph.addEdge("match_resume", "plan_questions");
        graph.addEdge("plan_questions", "ask_question");
        graph.addEdge("ask_question", "grade_answer");
        graph.addConditionalEdges("grade_answer", AsyncEdgeAction.edge_async(this::routeAfterGrade), Map.of(
                "followup", "ask_followup",
                "next", "ask_question",
                "evaluate", "evaluate",
                "end", END
        ));
        graph.addConditionalEdges("ask_followup", AsyncEdgeAction.edge_async(this::routeAfterFollowup), Map.of(
                "next", "ask_question",
                "evaluate", "evaluate",
                "end", END
        ));
        graph.addEdge("evaluate", "review");
        graph.addEdge("review", END);

        return graph.compile(CompileConfig.builder().recursionLimit(120).build());
    }

    private Map<String, Object> analyzeJd(OverAllState state) {
        if (isQuit(state)) {
            return Map.of(InterviewStateKeys.QUIT, true);
        }
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        String jdText = state.value(InterviewStateKeys.JD_TEXT, "");
        emit(sessionId, "phase", "正在分析岗位 JD…", null);
        JdRequirement jd = jdAnalysisAgent.analyze(jdText);
        emit(sessionId, "jd_analysis", "JD 分析完成：" + jd.getTitle(), jd);
        return Map.of(
                InterviewStateKeys.JD_REQUIREMENT, jd,
                InterviewStateKeys.PHASE_MESSAGE, "jd_done"
        );
    }

    private Map<String, Object> matchResume(OverAllState state) {
        if (isQuit(state)) {
            return Map.of(InterviewStateKeys.QUIT, true);
        }
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        JdRequirement jd = state.value(InterviewStateKeys.JD_REQUIREMENT, new JdRequirement());
        String resume = state.value(InterviewStateKeys.RESUME_TEXT, "");
        emit(sessionId, "phase", "正在评估简历匹配度…", null);
        MatchReport report = resumeMatchAgent.match(jd, resume);
        emit(sessionId, "match_report", "匹配度 " + report.getScore() + " 分", report);
        return Map.of(InterviewStateKeys.MATCH_REPORT, report);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> planQuestions(OverAllState state) {
        if (isQuit(state)) {
            return Map.of(InterviewStateKeys.QUIT, true);
        }
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        Long userId = state.value(InterviewStateKeys.USER_ID, Long.class).orElse(null);
        JdRequirement jd = state.value(InterviewStateKeys.JD_REQUIREMENT, new JdRequirement());
        MatchReport match = state.value(InterviewStateKeys.MATCH_REPORT, new MatchReport());
        String resume = state.value(InterviewStateKeys.RESUME_TEXT, "");
        Difficulty difficulty = Difficulty.fromString(state.value(InterviewStateKeys.DIFFICULTY, "MEDIUM"));
        emit(sessionId, "phase", "正在规划面试题…", null);
        List<String> weak = longTermMemoryService.topWeakTopics(userId, 5);
        List<Question> questions = questionPlannerAgent.plan(
                jd, match, resume, appConfig.getInterview().getMaxQuestions(), weak, difficulty);
        emit(sessionId, "question_plan", "已准备 " + questions.size() + " 道题，开始面试", questions.stream()
                .map(q -> Map.of("topic", q.getTopic(), "type", q.getType(), "source", q.getSource().name()))
                .toList());
        return Map.of(
                InterviewStateKeys.QUESTIONS, questions,
                InterviewStateKeys.QUESTION_INDEX, 0
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> askQuestion(OverAllState state) throws Exception {
        if (isQuit(state)) {
            return Map.of(InterviewStateKeys.QUIT, true, InterviewStateKeys.NEXT_ROUTE, "end");
        }
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        List<Question> questions = state.value(InterviewStateKeys.QUESTIONS, List.of());
        int index = state.value(InterviewStateKeys.QUESTION_INDEX, 0);
        if (index >= questions.size()) {
            return Map.of(InterviewStateKeys.NEXT_ROUTE, "evaluate");
        }
        Question q = questions.get(index);
        String presented = interviewerAgent.presentQuestion(q, index, questions.size());
        emit(sessionId, "question", presented, q);
        answerBridge.prepare(sessionId);
        String answer = answerBridge.await(sessionId, appConfig.getInterview().getAnswerTimeoutSeconds());
        if (quitFlags.getOrDefault(sessionId, false)) {
            return Map.of(InterviewStateKeys.QUIT, true, InterviewStateKeys.NEXT_ROUTE, "end");
        }
        return Map.of(
                InterviewStateKeys.CURRENT_ANSWER, answer,
                InterviewStateKeys.FOLLOWUP_PENDING, false
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> gradeAnswer(OverAllState state) {
        if (isQuit(state)) {
            return Map.of(InterviewStateKeys.QUIT, true, InterviewStateKeys.NEXT_ROUTE, "end");
        }
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        List<Question> questions = state.value(InterviewStateKeys.QUESTIONS, List.of());
        int index = state.value(InterviewStateKeys.QUESTION_INDEX, 0);
        String answer = state.value(InterviewStateKeys.CURRENT_ANSWER, "");
        Question q = questions.get(index);
        InterviewerAgent.GradeResult grade = interviewerAgent.grade(q, answer);
        emit(sessionId, "grade", grade.comment(), Map.of(
                "verdict", grade.verdict(),
                "score", grade.score()
        ));

        Difficulty difficulty = Difficulty.fromString(state.value(InterviewStateKeys.DIFFICULTY, "MEDIUM"));
        int streak = state.value(InterviewStateKeys.STREAK, 0);
        DifficultyController.State ds = difficultyController.onVerdict(difficulty, streak, grade.verdict());

        boolean needFollowup = "partial".equalsIgnoreCase(grade.verdict())
                && grade.followUp() != null && !grade.followUp().isBlank();

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewStateKeys.LAST_GRADE, grade);
        updates.put(InterviewStateKeys.DIFFICULTY, ds.difficulty().name());
        updates.put(InterviewStateKeys.STREAK, ds.streak());
        updates.put(InterviewStateKeys.FOLLOWUP_PENDING, needFollowup);
        updates.put(InterviewStateKeys.FOLLOWUP_QUESTION, needFollowup ? grade.followUp() : "");

        if (!needFollowup) {
            List<InterviewTurn> turns = new ArrayList<>(state.value(InterviewStateKeys.TURNS, List.of()));
            turns.add(interviewerAgent.buildTurn(q, answer, grade, null));
            updates.put(InterviewStateKeys.TURNS, turns);
            updates.put(InterviewStateKeys.QUESTION_INDEX, index + 1);
            if (index + 1 >= questions.size()) {
                updates.put(InterviewStateKeys.NEXT_ROUTE, "evaluate");
            } else {
                updates.put(InterviewStateKeys.NEXT_ROUTE, "next");
            }
        } else {
            updates.put(InterviewStateKeys.NEXT_ROUTE, "followup");
        }
        return updates;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> askFollowup(OverAllState state) throws Exception {
        if (isQuit(state)) {
            return Map.of(InterviewStateKeys.QUIT, true, InterviewStateKeys.NEXT_ROUTE, "end");
        }
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        String followup = state.value(InterviewStateKeys.FOLLOWUP_QUESTION, "");
        emit(sessionId, "followup", "追问：" + followup, null);
        answerBridge.prepare(sessionId);
        String followAnswer = answerBridge.await(sessionId, appConfig.getInterview().getAnswerTimeoutSeconds());
        if (quitFlags.getOrDefault(sessionId, false)) {
            return Map.of(InterviewStateKeys.QUIT, true, InterviewStateKeys.NEXT_ROUTE, "end");
        }

        List<Question> questions = state.value(InterviewStateKeys.QUESTIONS, List.of());
        int index = state.value(InterviewStateKeys.QUESTION_INDEX, 0);
        Question q = questions.get(index);
        String answer = state.value(InterviewStateKeys.CURRENT_ANSWER, "");
        InterviewerAgent.GradeResult grade = state.value(InterviewStateKeys.LAST_GRADE, InterviewerAgent.GradeResult.class)
                .orElse(null);
        List<InterviewTurn> turns = new ArrayList<>(state.value(InterviewStateKeys.TURNS, List.of()));
        turns.add(interviewerAgent.buildTurn(q, answer, grade, followAnswer));

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewStateKeys.TURNS, turns);
        updates.put(InterviewStateKeys.QUESTION_INDEX, index + 1);
        updates.put(InterviewStateKeys.FOLLOWUP_PENDING, false);
        if (index + 1 >= questions.size()) {
            updates.put(InterviewStateKeys.NEXT_ROUTE, "evaluate");
        } else {
            updates.put(InterviewStateKeys.NEXT_ROUTE, "next");
        }
        return updates;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluate(OverAllState state) {
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        Long userId = state.value(InterviewStateKeys.USER_ID, Long.class).orElse(null);
        List<InterviewTurn> turns = state.value(InterviewStateKeys.TURNS, List.of());
        emit(sessionId, "phase", "正在生成评估报告…", null);
        EvaluationReport report = evaluationAgent.evaluate(turns);
        longTermMemoryService.recordWeaknesses(userId, report.getWeakTopics());

        // Low-score reference answers
        List<Map<String, Object>> lowScoreRefs = turns.stream()
                .filter(t -> t.getScore() < 60)
                .map(t -> Map.<String, Object>of(
                        "topic", t.getQuestion().getTopic(),
                        "question", t.getQuestion().getContent(),
                        "referenceAnswer", t.getQuestion().getReferenceAnswer() == null
                                ? "" : t.getQuestion().getReferenceAnswer(),
                        "source", t.getQuestion().getSource().name()
                ))
                .toList();
        emit(sessionId, "evaluation", report.getSummary(), Map.of(
                "report", report,
                "lowScoreReferences", lowScoreRefs
        ));
        return Map.of(InterviewStateKeys.EVALUATION, report);
    }

    private Map<String, Object> review(OverAllState state) {
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        Long userId = state.value(InterviewStateKeys.USER_ID, Long.class).orElse(null);
        EvaluationReport report = state.value(InterviewStateKeys.EVALUATION, new EvaluationReport());
        emit(sessionId, "phase", "正在生成复习计划…", null);
        ReviewPlan plan = reviewPlannerAgent.plan(report, longTermMemoryService.topWeakTopics(userId, 8));
        emit(sessionId, "review_plan", plan.getSummary(), plan);
        emit(sessionId, "done", "面试流程结束，加油！", null);
        return Map.of(InterviewStateKeys.REVIEW_PLAN, plan);
    }

    private String routeAfterGrade(OverAllState state) {
        if (Boolean.TRUE.equals(state.value(InterviewStateKeys.QUIT, false))
                || quitFlags.getOrDefault(state.value(InterviewStateKeys.SESSION_ID, ""), false)) {
            return "end";
        }
        return state.value(InterviewStateKeys.NEXT_ROUTE, "evaluate");
    }

    private String routeAfterFollowup(OverAllState state) {
        if (Boolean.TRUE.equals(state.value(InterviewStateKeys.QUIT, false))
                || quitFlags.getOrDefault(state.value(InterviewStateKeys.SESSION_ID, ""), false)) {
            return "end";
        }
        return state.value(InterviewStateKeys.NEXT_ROUTE, "evaluate");
    }

    private boolean isQuit(OverAllState state) {
        String sessionId = state.value(InterviewStateKeys.SESSION_ID, "");
        return Boolean.TRUE.equals(state.value(InterviewStateKeys.QUIT, false))
                || quitFlags.getOrDefault(sessionId, false);
    }

    private void emit(String sessionId, String type, String content, Object data) {
        InterviewEventSink sink = sinks.get(sessionId);
        if (sink != null) {
            sink.emit(type, content, data);
        }
    }
}
