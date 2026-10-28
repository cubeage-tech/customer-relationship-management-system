package com.company.crm.ai;

import com.company.crm.ai.dto.AiDtos.ChatPlan;
import com.company.crm.ai.dto.AiDtos.ChatWording;
import com.company.crm.ai.dto.AiDtos.GeneratedText;
import com.company.crm.ai.dto.AiDtos.Objection;
import com.company.crm.ai.dto.AiDtos.SalesApproachPlan;
import com.company.crm.ai.dto.AiDtos.SentimentJudgement;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.communication.service.CommunicationService.InboundMessage;
import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.ai.service.AiContentService;
import com.company.crm.ai.service.SalesCoachingService.CoachingPlan;
import com.company.crm.ai.service.SalesCoachingService.FocusArea;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-AI-3.2 (best time to contact), FR-AI-3.3 (talking points), FR-AI-4.1–4.4 (chat: more questions,
 * follow-ups, structured rows with links, next action, scope) and FR-AI-5.1 (drafts with the CRM's
 * business facts and the best time to send).
 */
class SalesAssistantIntegrationTest extends PostgresIntegrationTest {

    @MockitoBean private LlmGateway llm;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CommunicationService communicationService;

    private TestTenant tenant;
    private String adminToken;
    private User exec;
    private long tenantId;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Assist Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        exec = tenant.user(SALES_EXECUTIVE);
        tenantId = tenant.tenant().getId();
        when(llm.isConfigured()).thenReturn(true);
    }

    // ==================== FR-AI-3.2 ====================

    @Test
    void theBestContactTimeComesFromWhenTheCustomerEngages() throws Exception {
        long customer = customer("Morning Buyer", "secret@buyer.test");
        LocalDate tuesday = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.TUESDAY));
        for (int week = 0; week < 4; week++) {
            message(customer, "inbound", tuesday.minusWeeks(week).atTime(10, 40), null);
        }
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        message(customer, "outbound", monday.atTime(17, 10), "no_answer");

        send(get("/api/ai/contact-time").param("customerId", String.valueOf(customer)), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windows[0].label").value("Tuesday 10:00–12:00"))
                .andExpect(jsonPath("$.data.interactions").value(4))
                .andExpect(jsonPath("$.data.basis").value("from 4 of their past interactions"))
                .andExpect(jsonPath("$.data.notes", hasItem(containsString("1 call(s) went unanswered"))));

        // No history: ordinary mid-week business hours.
        long quiet = customer("Quiet Ltd", null);
        send(get("/api/ai/contact-time").param("customerId", String.valueOf(quiet)), adminToken, null)
                .andExpect(jsonPath("$.data.basis").value("typical business hours (little history yet)"))
                .andExpect(jsonPath("$.data.windows[0].day").value(in(List.of("Tuesday", "Wednesday", "Thursday"))));
        long lead = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Ravi\",\"companyName\":\"Ravi Co\",\"industry\":\"trading\",\"source\":\"referral\"}"));
        send(get("/api/ai/contact-time").param("leadId", String.valueOf(lead)), adminToken, null)
                .andExpect(jsonPath("$.data.recordType").value("lead"));
        send(get("/api/ai/contact-time").param("customerId", String.valueOf(customer)),
                testTenants.tenantWithAllRoles("Other " + System.nanoTime()).token(ADMIN), null).andExpect(status().isNotFound());
        send(get("/api/ai/contact-time"), adminToken, null).andExpect(status().isBadRequest());
    }

    // ==================== FR-AI-3.3 ====================

    @Test
    void talkingPointsAreBuiltFromBusinessFactsOnly() throws Exception {
        long customer = customer("Pump House", "buyer@pumphouse.test");
        approved(customer, "Pump");
        jdbc.update("INSERT INTO opportunities (tenant_id, customer_id, stage, deal_value, owner_id, product_service, expected_closing_date) "
                + "VALUES (?, ?, 'proposal', 40000, ?, 'Pump upgrade', ?)", tenantId, customer, exec.getId(), Date.valueOf(LocalDate.now().plusDays(20)));
        when(llm.structured(anyString(), anyString(), eq(SalesApproachPlan.class))).thenReturn(Optional.of(new SalesApproachPlan(
                "Lead with the upgrade.", List.of("They already run our pump", "", "Ask about the timeline"), List.of("What is your deadline?"),
                List.of(new Objection("Too expensive", "Compare running costs")))));

        send(post("/api/ai/talking-points"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customer + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approach").value("Lead with the upgrade."))
                .andExpect(jsonPath("$.data.talkingPoints.length()").value(2)) // blanks dropped
                .andExpect(jsonPath("$.data.objections[0].response").value("Compare running costs"))
                .andExpect(jsonPath("$.data.basedOn", hasItem("Has bought: Pump")))
                .andExpect(jsonPath("$.data.basedOn", hasItem(startsWith("Open deal: proposal, value 40000"))));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(SalesApproachPlan.class));
        assertThat(prompt.getValue()).contains("Pump House", "Has bought: Pump", "chance to win").doesNotContain("buyer@pumphouse.test");

        // A deal gets its own chance and factors; the customer's AI exclusion (SEC-6) and roles are respected.
        long deal = jdbc.queryForObject("SELECT id FROM opportunities WHERE customer_id = ?", Long.class, customer);
        send(post("/api/ai/talking-points"), adminToken, "{\"opportunityId\":" + deal + "}")
                .andExpect(jsonPath("$.data.recordType").value("opportunity"))
                .andExpect(jsonPath("$.data.basedOn", hasItem(startsWith("Chance to win"))));
        send(put("/api/ai/exclusions/customer/" + customer), adminToken, "{\"excluded\":true}").andExpect(status().isOk());
        send(post("/api/ai/talking-points"), adminToken, "{\"customerId\":" + customer + "}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("AI_EXCLUDED"));
        send(post("/api/ai/talking-points"), tenant.token(SERVICE_AGENT), "{\"customerId\":" + customer + "}").andExpect(status().isForbidden());
        send(post("/api/ai/talking-points"), adminToken, "{}").andExpect(status().isBadRequest());
    }

    // ==================== FR-AI-4.1 – 4.4 ====================

    @Test
    void theChatAnswersMoreQuestionsWithLinksActionsAndOnlyVisibleRecords() throws Exception {
        User otherExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        long mine = idOf(send(post("/api/leads"), tenant.token(SALES_EXECUTIVE), "{\"leadName\":\"Hot Mine\",\"companyName\":\"Mine Co\",\"industry\":\"trading\",\"source\":\"referral\"}"));
        long theirs = idOf(send(post("/api/leads"), testTenants.tokenFor(otherExec), "{\"leadName\":\"Hot Theirs\",\"companyName\":\"Their Co\",\"industry\":\"trading\",\"source\":\"referral\"}"));
        jdbc.update("UPDATE leads SET ai_score = 88, ai_score_label = 'High' WHERE id IN (?, ?)", mine, theirs);
        plan(new ChatPlan("hot_leads", null, null, null, null, null, null, null, null, "High"));
        when(llm.structured(anyString(), anyString(), eq(ChatWording.class))).thenReturn(Optional.of(new ChatWording("One hot lead.", "Call them today")));

        chat(tenant.token(SALES_EXECUTIVE), "{\"question\":\"Which of my leads are hot?\",\"history\":[\"Show my leads\"]}")
                .andExpect(jsonPath("$.data.intent").value("hot_leads"))
                .andExpect(jsonPath("$.data.matchCount").value(1)) // FR-AI-4.4: not the other salesperson's lead
                .andExpect(jsonPath("$.data.rows[0].id").value(mine))
                .andExpect(jsonPath("$.data.rows[0].detail").value(startsWith("AI score 88 (High)")))
                .andExpect(jsonPath("$.data.recommendedAction").value("Call them today"))
                .andExpect(jsonPath("$.data.viewAllLink").value("/leads?scoreLabel=High"));
        ArgumentCaptor<String> planPrompt = ArgumentCaptor.forClass(String.class);
        verify(llm, atLeastOnce()).structured(anyString(), planPrompt.capture(), eq(ChatPlan.class));
        assertThat(planPrompt.getValue()).contains("Earlier questions in this conversation (context only): Show my leads");

        // Deals likely to close, and the forecast — a customer excluded from AI is shown to the user but not named to the model.
        long hidden = customer("Hidden Buyer", null);
        jdbc.update("INSERT INTO opportunities (tenant_id, customer_id, stage, deal_value, owner_id, expected_closing_date) VALUES (?, ?, 'negotiation', 9000, ?, ?)",
                tenantId, hidden, exec.getId(), Date.valueOf(LocalDate.now().withDayOfMonth(1).plusMonths(1).plusDays(5)));
        send(put("/api/ai/exclusions/customer/" + hidden), adminToken, "{\"excluded\":true}").andExpect(status().isOk());
        plan(new ChatPlan("likely_deals", null, null, null, null, null, null, null, null, null));
        chat(adminToken, "{\"question\":\"Which deals will close?\"}")
                .andExpect(jsonPath("$.data.rows[0].label").value("Hidden Buyer"))
                .andExpect(jsonPath("$.data.rows[0].detail").value(containsString("% chance")))
                .andExpect(jsonPath("$.data.viewAllLink").value("/opportunities"));
        plan(new ChatPlan("forecast", null, null, null, null, null, null, null, "next_month", null));
        chat(adminToken, "{\"question\":\"What's the forecast for next month?\"}").andExpect(jsonPath("$.data.matchCount").value(1));
        ArgumentCaptor<String> wording = ArgumentCaptor.forClass(String.class);
        verify(llm, atLeastOnce()).structured(anyString(), wording.capture(), eq(ChatWording.class));
        String forecastPrompt = wording.getAllValues().get(wording.getAllValues().size() - 1);
        assertThat(forecastPrompt).contains("forecast for next month", "expected").contains("(a record excluded from AI)").doesNotContain("Hidden Buyer");

        // Follow-ups due, and roles without lead access are told so.
        jdbc.update("UPDATE leads SET follow_up_date = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusDays(2)), mine);
        plan(new ChatPlan("follow_ups", null, null, null, null, null, null, null, null, null));
        chat(tenant.token(SALES_EXECUTIVE), "{\"question\":\"Who do I need to follow up?\"}")
                .andExpect(jsonPath("$.data.rows[0].detail").value(containsString("(overdue)")));
        plan(new ChatPlan("hot_leads", null, null, null, null, null, null, null, null, "High"));
        chat(tenant.token(SERVICE_AGENT), "{\"question\":\"Hot leads?\"}")
                .andExpect(jsonPath("$.data.answer").value("Your role doesn't have access to leads."))
                .andExpect(jsonPath("$.data.rows.length()").value(0));
        chat(adminToken, "{\"question\":\"x\",\"history\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}").andExpect(status().isBadRequest());
    }

    // ==================== FR-AI-5.1 ====================

    @Test
    void draftsUseTheCustomersFactsAndSuggestWhenToSend() throws Exception {
        long customer = customer("Draft Buyer", "draft@buyer.test");
        approved(customer, "Valve");
        when(llm.structured(anyString(), anyString(), eq(GeneratedText.class)))
                .thenReturn(Optional.of(new GeneratedText("Your valves", "Hello — time for new valves? — Assist Co")));
        send(post("/api/ai/generate-email"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customer + ",\"purpose\":\"Offer a re-order\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value(containsString("valves")))
                .andExpect(jsonPath("$.data.bestTimeToSend").value(containsString(":00–")));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(GeneratedText.class));
        assertThat(prompt.getValue()).contains("What we know:", "Has bought: Valve", "Offer a re-order").doesNotContain("draft@buyer.test");
    }

    // ==================== FR-AI-5.2 ====================

    @Test
    void aiDraftsAreSentOnlyAfterTheUserReviewsThem() throws Exception {
        long customer = customer("Review Co", "review@co.test");
        when(llm.structured(anyString(), anyString(), eq(GeneratedText.class)))
                .thenReturn(Optional.of(new GeneratedText("Quick check-in", "Hello, how is the pump running? — Assist Co")));
        String exec = tenant.token(SALES_EXECUTIVE);
        String draft = send(post("/api/ai/generate-email"), exec, "{\"customerId\":" + customer + ",\"purpose\":\"Check in\"}")
                .andExpect(jsonPath("$.data.draftId").isNumber()).andReturn().getResponse().getContentAsString();
        long draftId = ((Number) JsonPath.read(draft, "$.data.draftId")).longValue();
        String email = "{\"customerId\":" + customer + ",\"to\":\"review@co.test\",\"subject\":\"Quick check-in\","
                + "\"body\":\"Hello Meera, how is the pump running? — Assist Co\",\"aiDraftId\":" + draftId;

        // Not confirmed as reviewed: refused, nothing sent.
        send(post("/api/communications/email"), exec, email + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("AI_REVIEW_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_logs WHERE customer_id = ?", Long.class, customer)).isZero();
        // Another user can't use it; nor can it go out on another channel.
        send(post("/api/communications/email"), adminToken, email + ",\"aiReviewed\":true}").andExpect(status().isNotFound());

        // Reviewed and edited: sent, and the approval recorded.
        long sent = idOf(send(post("/api/communications/email"), exec, email + ",\"aiReviewed\":true}"));
        assertThat(jdbc.queryForMap("SELECT edited, communication_id, approved_at FROM ai_drafts WHERE id = ?", draftId))
                .containsEntry("edited", true).containsEntry("communication_id", sent).extractingByKey("approved_at").isNotNull();
        assertThat(jdbc.queryForObject("SELECT details FROM audit_events WHERE action = 'AI_CONTENT_APPROVED' AND entity_id = ?", String.class, sent))
                .isEqualTo("email from AI draft #" + draftId + ", edited before sending");
        // A draft is sent once.
        send(post("/api/communications/email"), exec, email + ",\"aiReviewed\":true}").andExpect(status().isConflict());

        String sms = send(post("/api/ai/generate-email"), exec, "{\"customerId\":" + customer + ",\"channel\":\"sms\",\"purpose\":\"Remind\"}")
                .andReturn().getResponse().getContentAsString();
        long smsDraft = ((Number) JsonPath.read(sms, "$.data.draftId")).longValue();
        send(post("/api/communications/email"), exec, "{\"customerId\":" + customer + ",\"to\":\"review@co.test\",\"subject\":\"x\","
                + "\"body\":\"y\",\"aiDraftId\":" + smsDraft + ",\"aiReviewed\":true}").andExpect(status().isConflict());
    }

    // ==================== FR-AI-5.3 ====================

    @Test
    void followUpsCampaignCopyAndProposalsAreDraftedFromBusinessFacts() throws Exception {
        long customer = customer("Proposal Co", "buyer@proposal.test");
        String execToken = tenant.token(SALES_EXECUTIVE);

        // Follow-up: no purpose needed; it picks up from the latest contact.
        when(llm.structured(anyString(), anyString(), eq(GeneratedText.class)))
                .thenReturn(Optional.of(new GeneratedText("Following up", "Following up on our call — Assist Co")));
        send(post("/api/ai/generate-email"), execToken, "{\"customerId\":" + customer + ",\"kind\":\"follow_up\"}").andExpect(status().isOk());
        ArgumentCaptor<String> followUp = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), followUp.capture(), eq(GeneratedText.class));
        assertThat(followUp.getValue()).contains("a follow-up", "Follow up on our most recent contact");
        send(post("/api/ai/generate-email"), execToken, "{\"customerId\":" + customer + "}").andExpect(status().isBadRequest());

        // Campaign copy: for the campaign and its audience, marketing only.
        String marketing = tenant.token(MARKETING_EXECUTIVE);
        long segment = idOf(send(post("/api/segments"), marketing, "{\"name\":\"Traders\",\"industries\":[\"trading\"]}"));
        long campaign = idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Spring offer\",\"description\":\"New pump range\",\"channel\":\"email\",\"startDate\":\""
                + LocalDate.now() + "\"}"));
        when(llm.structured(anyString(), anyString(), eq(AiContentService.CampaignText.class)))
                .thenReturn(Optional.of(new AiContentService.CampaignText("Meet the new pumps", "Our new range is here. — Assist Co")));
        String copy = "{\"campaignId\":" + campaign + ",\"segmentId\":" + segment + ",\"purpose\":\"Announce the new range\"}";
        send(post("/api/ai/campaign-copy"), marketing, copy)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Meet the new pumps"))
                .andExpect(jsonPath("$.data.basedOn[1]").value(containsString("Traders")));
        ArgumentCaptor<String> copyPrompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), copyPrompt.capture(), eq(AiContentService.CampaignText.class));
        assertThat(copyPrompt.getValue()).contains("Spring offer", "New pump range", "segment \"Traders\", 1 customers", "Announce the new range")
                .doesNotContain("buyer@proposal.test");
        send(post("/api/ai/campaign-copy"), execToken, copy).andExpect(status().isForbidden());
        long event = idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Expo\",\"channel\":\"event\",\"startDate\":\"" + LocalDate.now() + "\"}"));
        send(post("/api/ai/campaign-copy"), marketing, "{\"campaignId\":" + event + ",\"purpose\":\"Invite\"}").andExpect(status().isBadRequest());

        // Proposal: from the deal and its latest quotation.
        long deal = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customer + ",\"dealValue\":1000,\"productService\":\"Pumps\",\"ownerId\":"
                + exec.getId() + "}"));
        Long quotation = jdbc.queryForObject("INSERT INTO quotations (tenant_id, customer_id, opportunity_id, quotation_number, status, owner_id) "
                + "VALUES (?, ?, ?, 'Q-PROP-1', 'draft', ?) RETURNING id", Long.class, tenantId, customer, deal, exec.getId());
        jdbc.update("INSERT INTO quotation_line_items (quotation_id, product_name, quantity, unit_price) VALUES (?, 'Pump', 2, 500)", quotation);
        when(llm.structured(anyString(), anyString(), eq(AiContentService.ProposalPlan.class))).thenReturn(Optional.of(new AiContentService.ProposalPlan(
                "Pumps for Proposal Co", List.of(new AiContentService.ProposalSection("Investment", "- 2 × Pump at 500")))));
        send(post("/api/ai/proposal"), execToken, "{\"opportunityId\":" + deal + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotationNumber").value("Q-PROP-1"))
                .andExpect(jsonPath("$.data.text").value("Pumps for Proposal Co\n\nInvestment\n- 2 × Pump at 500"));
        ArgumentCaptor<String> proposal = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), proposal.capture(), eq(AiContentService.ProposalPlan.class));
        assertThat(proposal.getValue()).contains("Customer: Proposal Co (industry trading)", "Deal: Pumps", "Quotation Q-PROP-1: 2 × Pump at 500")
                .doesNotContain("buyer@proposal.test");
    }

    // ==================== FR-AI-6.1 ====================

    @Test
    void messagesAndFeedbackAreClassifiedAndTheReadingStored() throws Exception {
        long customer = customer("Mood Co", "mood@co.test");
        when(llm.structured(anyString(), anyString(), eq(SentimentJudgement.class)))
                .thenReturn(Optional.of(new SentimentJudgement("negative", -0.834, "high", "Second late delivery; considering other suppliers")));
        String text = "Second late delivery this month. We are looking at other suppliers.";

        // Off by default: nothing is sent to the AI as messages arrive.
        communicationService.recordInbound(new InboundMessage(CommunicationChannel.EMAIL, "mood@co.test", "Late again", text, "<m1" + System.nanoTime() + "@co>", List.of(), null));
        verify(llm, after(400).never()).structured(anyString(), anyString(), eq(SentimentJudgement.class));

        send(put("/api/ai/settings"), adminToken, "{\"aiEnabled\":true,\"messageContent\":true,\"autoSentiment\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.autoSentiment").value(true));
        long message = communicationService.recordInbound(new InboundMessage(CommunicationChannel.EMAIL, "mood@co.test", "Late again", text,
                "<m2" + System.nanoTime() + "@co>", List.of(), null)).orElseThrow().getId();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm, timeout(5000)).structured(anyString(), prompt.capture(), eq(SentimentJudgement.class));
        assertThat(prompt.getValue()).contains("Late again", "other suppliers");
        for (int i = 0; i < 50 && jdbc.queryForObject("SELECT sentiment FROM communication_logs WHERE id = ?", String.class, message) == null; i++) {
            Thread.sleep(100);
        }
        send(get("/api/communications").param("customerId", String.valueOf(customer)), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(jsonPath("$.data[?(@.id == " + message + ")].sentiment.sentiment").value(contains("negative")))
                .andExpect(jsonPath("$.data[?(@.id == " + message + ")].sentiment.score").value(contains(-0.83)))
                .andExpect(jsonPath("$.data[?(@.id == " + message + ")].sentiment.churnRisk").value(contains("high")));

        // A review: the customer's feedback on a resolved ticket.
        long ticket = jdbc.queryForObject("INSERT INTO service_tickets (tenant_id, customer_id, subject, priority, status, sla_due_at, resolved_at, created_at) "
                + "VALUES (?, ?, 'Pump noise', 'medium', 'resolved', now() + interval '1 day', now(), now()) RETURNING id", Long.class, tenantId, customer);
        when(llm.structured(anyString(), anyString(), eq(SentimentJudgement.class)))
                .thenReturn(Optional.of(new SentimentJudgement("positive", 0.9, "low", "Quick, friendly fix")));
        send(patch("/api/tickets/" + ticket + "/feedback"), adminToken, "{\"score\":5,\"comment\":\"Fixed in an hour, very friendly technician\"}")
                .andExpect(status().isOk());
        for (int i = 0; i < 50 && jdbc.queryForObject("SELECT feedback_sentiment FROM service_tickets WHERE id = ?", String.class, ticket) == null; i++) {
            Thread.sleep(100);
        }
        send(get("/api/tickets/" + ticket), adminToken, null)
                .andExpect(jsonPath("$.data.feedbackSentiment.sentiment").value("positive"))
                .andExpect(jsonPath("$.data.feedbackSentiment.reason").value("Quick, friendly fix"));
        verify(llm, timeout(5000).atLeast(2)).structured(anyString(), prompt.capture(), eq(SentimentJudgement.class));
        assertThat(prompt.getValue()).contains("Customer rating: 5 of 5", "Review: Fixed in an hour");

        // On request, for one that arrived while it was off: stored too.
        long earlier = jdbc.queryForObject("SELECT min(id) FROM communication_logs WHERE customer_id = ?", Long.class, customer);
        send(post("/api/ai/sentiment"), tenant.token(SALES_EXECUTIVE), "{\"communicationId\":" + earlier + "}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT sentiment FROM communication_logs WHERE id = ?", String.class, earlier)).isEqualTo("positive");
    }

    // ==================== FR-AI-6.2 ====================

    @Test
    void churnRiskCustomersAreFlaggedToTheOwnerAndTheirManagerOnce() throws Exception {
        User manager = tenant.user(SALES_MANAGER);
        long team = jdbc.queryForObject("INSERT INTO sales_teams (tenant_id, name, manager_id) VALUES (?, ?, ?) RETURNING id", Long.class,
                tenantId, "Churn team " + System.nanoTime(), manager.getId());
        jdbc.update("UPDATE users SET team_id = ? WHERE id = ?", team, exec.getId());
        long customer = customer("Churn Co", null);
        long calm = customer("Calm Co", null);
        // Bought every ~100 days, but not for 300; a ticket past its SLA; then a poor rating.
        for (int days : new int[]{400, 300}) {
            jdbc.update("INSERT INTO quotations (tenant_id, customer_id, quotation_number, status, owner_id, customer_approved_at) "
                    + "VALUES (?, ?, ?, 'approved', ?, now() - make_interval(days => ?))", tenantId, customer, "Q-" + System.nanoTime(), exec.getId(), days);
        }
        jdbc.update("INSERT INTO service_tickets (tenant_id, customer_id, subject, priority, status, sla_due_at, created_at) "
                + "VALUES (?, ?, 'Leak', 'high', 'open', now() - interval '1 day', now() - interval '3 days')", tenantId, customer);
        long resolved = jdbc.queryForObject("INSERT INTO service_tickets (tenant_id, customer_id, subject, priority, status, sla_due_at, resolved_at, created_at) "
                + "VALUES (?, ?, 'Noise', 'medium', 'resolved', now() + interval '1 day', now(), now()) RETURNING id", Long.class, tenantId, customer);
        send(patch("/api/tickets/" + resolved + "/feedback"), adminToken, "{\"score\":1,\"comment\":\"Took a week\"}").andExpect(status().isOk());

        String execToken = tenant.token(SALES_EXECUTIVE);
        send(get("/api/ai/churn-risk").param("customerId", String.valueOf(customer)), execToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value("high"))
                .andExpect(jsonPath("$.data.score").value(55))
                .andExpect(jsonPath("$.data.factors[*].name").value(contains("Poor review", "Buying less", "Open problems")))
                .andExpect(jsonPath("$.data.factors[1].detail").value("No purchase for 300 days (usually every 100)"));
        send(get("/api/ai/churn-risk").param("customerId", String.valueOf(calm)), execToken, null)
                .andExpect(jsonPath("$.data.level").value("low")).andExpect(jsonPath("$.data.factors").isEmpty());

        // Owner and their manager are told, once.
        for (String token : List.of(execToken, tenant.token(SALES_MANAGER))) {
            send(get("/api/notifications"), token, null)
                    .andExpect(jsonPath("$.data.items[?(@.title == 'Customer at risk: Churn Co')].entityId").value(contains((int) customer)))
                    .andExpect(jsonPath("$.data.items[?(@.title == 'Customer at risk: Churn Co')].body").value(contains(containsString("55/100"))));
        }
        assertThat(jdbc.queryForObject("SELECT churn_flagged_at FROM customers WHERE id = ?", Timestamp.class, customer)).isNotNull();

        // The list follows what each user can see.
        send(get("/api/ai/churn-risks").param("level", "high"), execToken, null)
                .andExpect(jsonPath("$.data[*].customerName").value(contains("Churn Co")));
        User otherExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        send(get("/api/ai/churn-risks"), testTenants.tokenFor(otherExec), null).andExpect(jsonPath("$.data").isEmpty());
        send(get("/api/ai/churn-risks"), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());
    }

    // ==================== FR-AI-6.3 ====================

    @Test
    void theSentimentTrendShowsEachMonthAndWhetherItIsDeclining() throws Exception {
        long customer = customer("Trend Co", null);
        // Positive four and five months ago; negative last month and this month (a message and a review); one message not yet read.
        reading(customer, "date_trunc('month', now()) - interval '5 months' + interval '2 days'", "positive", 0.6);
        reading(customer, "date_trunc('month', now()) - interval '4 months' + interval '2 days'", "positive", 0.8);
        reading(customer, "date_trunc('month', now()) - interval '1 month' + interval '2 days'", "negative", -0.4);
        reading(customer, "now()", "negative", -0.6);
        reading(customer, "now()", null, null);
        jdbc.update("INSERT INTO service_tickets (tenant_id, customer_id, subject, priority, status, sla_due_at, resolved_at, created_at, feedback_score, "
                + "feedback_comment, feedback_at, feedback_sentiment, feedback_sentiment_score) VALUES (?, ?, 'Slow', 'medium', 'resolved', now(), now(), now(), "
                + "2, 'Too slow', now(), 'negative', -0.5)", tenantId, customer);

        String execToken = tenant.token(SALES_EXECUTIVE);
        send(get("/api/ai/sentiment-trend").param("customerId", String.valueOf(customer)), execToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.series.length()").value(6))
                .andExpect(jsonPath("$.data.series[0].positive").value(1))
                .andExpect(jsonPath("$.data.series[5].month").value(java.time.YearMonth.now().toString()))
                .andExpect(jsonPath("$.data.series[5].negative").value(2))
                .andExpect(jsonPath("$.data.series[5].averageScore").value(-0.55))
                .andExpect(jsonPath("$.data.series[2].averageScore").doesNotExist())
                .andExpect(jsonPath("$.data.readings").value(5))
                .andExpect(jsonPath("$.data.fromReviews").value(1))
                .andExpect(jsonPath("$.data.recentAverage").value(-0.5))
                .andExpect(jsonPath("$.data.earlierAverage").value(0.7))
                .andExpect(jsonPath("$.data.direction").value("declining"))
                .andExpect(jsonPath("$.data.summary").value(containsString("declining — -0.50 in the last 3 months, down from +0.70")))
                .andExpect(jsonPath("$.data.unclassified").value(1));
        send(get("/api/ai/sentiment-trend").param("customerId", String.valueOf(customer)).param("months", "1"), execToken, null)
                .andExpect(status().isBadRequest());
        User otherExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        send(get("/api/ai/sentiment-trend").param("customerId", String.valueOf(customer)), testTenants.tokenFor(otherExec), null)
                .andExpect(status().is4xxClientError());
        send(get("/api/ai/sentiment-trend").param("customerId", String.valueOf(customer)), tenant.token(SERVICE_AGENT), null)
                .andExpect(status().isForbidden());
    }

    private void reading(long customerId, String at, String sentiment, Double score) {
        jdbc.update("INSERT INTO communication_logs (tenant_id, customer_id, channel, direction, status, body, sentiment, sentiment_score, occurred_at, created_at) "
                + "VALUES (?, ?, 'email', 'inbound', 'received', 'A message', ?, ?, " + at + ", now())", tenantId, customerId, sentiment, score);
    }

    // ==================== FR-AI-8.1 ====================

    @Test
    void eachSalespersonsActivityAndConversionIsAnalysedAgainstTheTeam() throws Exception {
        User other = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        long customer = customer("Perf Co", null);
        // exec: busy, but few calls connect and most deals are lost on price; well behind the revenue target.
        for (int i = 0; i < 10; i++) {
            call(exec, customer, i < 2 ? "connected" : "no_answer");
        }
        deal(exec, customer, "won", 500, 20, null);
        for (int i = 0; i < 3; i++) {
            deal(exec, customer, "lost", 800, 10, i < 2 ? "Price" : "Timing");
        }
        jdbc.update("INSERT INTO sales_targets (tenant_id, user_id, metric, target_value, period_start, period_end) "
                + "VALUES (?, ?, 'revenue', 10000, current_date - 10, current_date + 20)", tenantId, exec.getId());
        // the colleague: wins most of their deals
        for (int i = 0; i < 4; i++) {
            call(other, customer, "connected");
        }
        for (int i = 0; i < 3; i++) {
            deal(other, customer, "won", 1000, 30, null);
        }
        deal(other, customer, "lost", 1000, 5, "Price");

        String execToken = tenant.token(SALES_EXECUTIVE);
        send(get("/api/ai/sales-performance"), execToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.people.length()").value(1)) // only themselves
                .andExpect(jsonPath("$.data.people[0].metrics.calls").value(10))
                .andExpect(jsonPath("$.data.people[0].metrics.connectedCalls").value(2))
                .andExpect(jsonPath("$.data.people[0].metrics.winRate").value(0.25))
                .andExpect(jsonPath("$.data.people[0].metrics.revenueWon").value(500.0))
                .andExpect(jsonPath("$.data.people[0].metrics.topLossReason").value("price"))
                .andExpect(jsonPath("$.data.people[0].metrics.daysToClose").value(20.0))
                .andExpect(jsonPath("$.data.team.winRate").value(0.5))
                .andExpect(jsonPath("$.data.people[0].gaps[*].metric").value(hasItems("calls", "win_rate", "target")))
                .andExpect(jsonPath("$.data.people[0].gaps[?(@.metric == 'win_rate')].text").value(contains(containsString("\"price\""))))
                .andExpect(jsonPath("$.data.people[0].strengths[*].metric").value(hasItem("activity")));
        send(get("/api/ai/sales-performance").param("userId", String.valueOf(other.getId())), execToken, null).andExpect(status().isForbidden());

        // Admin sees everyone in sales; the colleague's win rate is a strength.
        send(get("/api/ai/sales-performance"), adminToken, null)
                .andExpect(jsonPath("$.data.people[?(@.userId == " + other.getId() + ")].strengths[*].metric").value(hasItem("win_rate")))
                .andExpect(jsonPath("$.data.people[?(@.userId == " + tenant.user(SALES_MANAGER).getId() + ")].gaps[*].metric").value(hasItem("activity")));
        send(get("/api/ai/sales-performance").param("from", "2026-01-01").param("to", "2025-01-01"), adminToken, null).andExpect(status().isBadRequest());
        send(get("/api/ai/sales-performance"), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());
    }

    // ==================== FR-AI-8.2 ====================

    @Test
    void salespeopleGetPersonalCoachingFromTheirFigures() throws Exception {
        User other = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        long customer = customer("Coach Co", null);
        for (int i = 0; i < 10; i++) {
            call(exec, customer, i < 2 ? "connected" : "no_answer");
        }
        deal(exec, customer, "won", 500, 20, null);
        for (int i = 0; i < 3; i++) {
            deal(exec, customer, "lost", 800, 10, "Price");
        }
        jdbc.update("INSERT INTO sales_targets (tenant_id, user_id, metric, target_value, period_start, period_end) "
                + "VALUES (?, ?, 'revenue', 10000, current_date - 10, current_date + 20)", tenantId, exec.getId());
        for (int i = 0; i < 4; i++) {
            call(other, customer, "connected");
        }
        for (int i = 0; i < 3; i++) {
            deal(other, customer, "won", 1000, 30, null);
        }
        deal(other, customer, "lost", 1000, 5, "Price");
        String execToken = tenant.token(SALES_EXECUTIVE);

        // No answer from the model: the built-in plan, from the same findings.
        send(post("/api/ai/sales-coaching"), execToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("rules"))
                .andExpect(jsonPath("$.data.userId").value(exec.getId().intValue()))
                .andExpect(jsonPath("$.data.focusAreas.length()").value(3))
                .andExpect(jsonPath("$.data.focusAreas[*].area").value(hasItems("Call connection", "Winning deals")))
                .andExpect(jsonPath("$.data.focusAreas[?(@.area == 'Winning deals')].actions[0]").value(contains(containsString("\"price\""))))
                .andExpect(jsonPath("$.data.strengthsToKeep[0]").value(containsString("More active than the team")))
                .andExpect(jsonPath("$.data.weeklyGoals").isNotEmpty());

        // With the model: its plan, written only from the figures.
        when(llm.structured(anyString(), anyString(), eq(CoachingPlan.class))).thenReturn(Optional.of(new CoachingPlan(
                "You are busy and active. Turning calls into conversations is the next step.",
                List.of(new FocusArea("Call connection", "2 of 10 calls connected", List.of("Call at the best contact times"), "5 of 10 calls connecting")),
                List.of("High activity"), List.of("15 customer touches"))));
        send(post("/api/ai/sales-coaching"), execToken, null)
                .andExpect(jsonPath("$.data.source").value("ai"))
                .andExpect(jsonPath("$.data.focusAreas[0].measure").value("5 of 10 calls connecting"));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        // asked twice: the first time it gave no plan (so the built-in one was used)
        verify(llm, org.mockito.Mockito.times(2)).structured(anyString(), prompt.capture(), eq(CoachingPlan.class));
        assertThat(prompt.getValue()).contains("Salesperson: " + exec.getFullName().split("\\s+")[0], "10 calls, 2 connected", "win rate 25%",
                "Most common loss reason: price", "CRM tools:").doesNotContain("Coach Co");

        // AI switched off: the built-in plan again, nothing sent.
        send(put("/api/ai/settings"), adminToken, "{\"aiEnabled\":false,\"messageContent\":true}").andExpect(status().isOk());
        send(post("/api/ai/sales-coaching"), execToken, null).andExpect(jsonPath("$.data.source").value("rules"));
        verify(llm, org.mockito.Mockito.times(2)).structured(anyString(), anyString(), eq(CoachingPlan.class)); // not asked again

        // A manager or admin coaches their people; a colleague can't; an admin isn't coached as a salesperson.
        send(post("/api/ai/sales-coaching").param("userId", String.valueOf(exec.getId())), adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value(exec.getFullName()));
        send(post("/api/ai/sales-coaching").param("userId", String.valueOf(exec.getId())), testTenants.tokenFor(other), null).andExpect(status().isForbidden());
        send(post("/api/ai/sales-coaching"), adminToken, null).andExpect(status().isBadRequest());
    }

    // ==================== FR-AI-8.3 ====================

    @Test
    void coachingIsSharedBetweenTheSalespersonAndTheirManager() throws Exception {
        User manager = tenant.user(SALES_MANAGER);
        long team = jdbc.queryForObject("INSERT INTO sales_teams (tenant_id, name, manager_id) VALUES (?, ?, ?) RETURNING id", Long.class,
                tenantId, "Coach team " + System.nanoTime(), manager.getId());
        jdbc.update("UPDATE users SET team_id = ? WHERE id = ?", team, exec.getId());
        String managerToken = tenant.token(SALES_MANAGER);
        String execToken = tenant.token(SALES_EXECUTIVE);
        String plan = ",\"from\":\"" + LocalDate.now().minusDays(89) + "\",\"to\":\"" + LocalDate.now() + "\",\"summary\":\"Busy and active; next, connect more calls.\","
                + "\"focusAreas\":[{\"area\":\"Call connection\",\"why\":\"2 of 10 calls connected\",\"actions\":[\"Call at the best contact times\"],"
                + "\"measure\":\"5 of 10 connecting\"}],\"strengthsToKeep\":[\"High activity\"],\"weeklyGoals\":[\"15 customer touches\"],\"source\":\"rules\"";

        // The manager shares it with the salesperson, with a note.
        long shared = idOf(send(post("/api/coaching"), managerToken, "{\"userId\":" + exec.getId() + plan + ",\"note\":\"Let's go through this on Monday\"}"));
        send(get("/api/notifications"), execToken, null)
                .andExpect(jsonPath("$.data.items[?(@.title == 'Coaching from " + manager.getFullName() + "')].body")
                        .value(contains(containsString("Let's go through this on Monday"))));
        send(get("/api/coaching"), execToken, null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sharedByName").value(manager.getFullName()))
                .andExpect(jsonPath("$.data[0].plan.focusAreas[0].measure").value("5 of 10 connecting"))
                .andExpect(jsonPath("$.data[0].acknowledgedAt").doesNotExist());

        // Nobody else in sales sees or answers it.
        User other = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        String otherToken = testTenants.tokenFor(other);
        send(get("/api/coaching").param("userId", String.valueOf(exec.getId())), otherToken, null).andExpect(status().isForbidden());
        send(post("/api/coaching/" + shared + "/acknowledge"), otherToken, "{}").andExpect(status().isNotFound());
        send(post("/api/coaching/" + shared + "/acknowledge"), managerToken, "{}").andExpect(status().isForbidden());
        send(post("/api/coaching"), otherToken, "{\"userId\":" + exec.getId() + plan + "}").andExpect(status().isForbidden());

        // The salesperson acknowledges with a reply; the manager is told.
        send(post("/api/coaching/" + shared + "/acknowledge"), execToken, "{\"reply\":\"Thanks — I'll try mornings\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reply").value("Thanks — I'll try mornings"))
                .andExpect(jsonPath("$.data.acknowledgedAt").isString());
        send(post("/api/coaching/" + shared + "/acknowledge"), execToken, "{}").andExpect(status().isConflict());
        send(get("/api/notifications"), managerToken, null)
                .andExpect(jsonPath("$.data.items[?(@.title == '" + exec.getFullName() + " acknowledged your coaching')].body")
                        .value(contains("Thanks — I'll try mornings")));

        // The salesperson shares their own plan with their manager.
        send(post("/api/coaching"), execToken, "{" + plan.substring(1) + "}").andExpect(status().isOk());
        send(get("/api/notifications"), managerToken, null)
                .andExpect(jsonPath("$.data.items[?(@.title == '" + exec.getFullName() + " shared their coaching plan')]").value(hasSize(1)));
        send(get("/api/coaching").param("userId", String.valueOf(exec.getId())), managerToken, null).andExpect(jsonPath("$.data.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'COACHING_SHARED' AND entity_id = ?", Long.class, exec.getId()))
                .isEqualTo(2);
        send(post("/api/coaching"), managerToken, "{\"userId\":" + exec.getId() + plan.replace("\"rules\"", "\"magic\"") + "}").andExpect(status().isBadRequest());
    }

    private void call(User by, long customerId, String outcome) {
        jdbc.update("INSERT INTO communication_logs (tenant_id, customer_id, channel, direction, status, call_outcome, occurred_at, created_at, created_by) "
                + "VALUES (?, ?, 'call', 'outbound', 'logged', ?, now() - interval '2 days', now(), ?)", tenantId, customerId, outcome, by.getId());
    }

    private void deal(User owner, long customerId, String stage, int value, int daysOpen, String lossReason) {
        jdbc.update("INSERT INTO opportunities (tenant_id, customer_id, owner_id, deal_value, stage, stage_changed_at, loss_reason, created_at) "
                + "VALUES (?, ?, ?, ?, ?, now() - interval '1 day', ?, now() - interval '1 day' - make_interval(days => ?))",
                tenantId, customerId, owner.getId(), value, stage, lossReason, daysOpen);
    }

    // ==================== helpers ====================

    private void plan(ChatPlan plan) {
        when(llm.structured(anyString(), anyString(), eq(ChatPlan.class))).thenReturn(Optional.of(plan));
    }

    private ResultActions chat(String token, String body) throws Exception {
        return send(post("/api/ai/chat"), token, body);
    }

    private long customer(String name, String email) throws Exception {
        return idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"" + name + "\",\"industry\":\"trading\",\"ownerId\":" + exec.getId()
                + (email != null ? ",\"email\":\"" + email + "\"" : "") + "}"));
    }

    private void message(long customerId, String direction, LocalDateTime at, String callOutcome) {
        jdbc.update("INSERT INTO communication_logs (tenant_id, customer_id, channel, direction, status, call_outcome, occurred_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, now())", tenantId, customerId, callOutcome != null ? "call" : "email", direction,
                callOutcome != null ? "logged" : "received", callOutcome, Timestamp.valueOf(at));
    }

    private void approved(long customerId, String product) {
        Long quotation = jdbc.queryForObject("INSERT INTO quotations (tenant_id, customer_id, quotation_number, status, owner_id, customer_approved_at) "
                + "VALUES (?, ?, ?, 'approved', ?, now()) RETURNING id", Long.class, tenantId, customerId, "Q-" + System.nanoTime(), exec.getId());
        jdbc.update("INSERT INTO quotation_line_items (quotation_id, product_name, quantity, unit_price) VALUES (?, ?, 1, 100)", quotation, product);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
