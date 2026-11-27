package com.company.crm.ai.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * FR-AI-5.2: AI content is reviewed, edited and approved by a person before it is sent. Each AI
 * draft is registered (only a hash of its text is kept); a message sent from a draft must name it
 * and confirm the review, or it is refused. The approval — who, whether they edited it, which
 * message it became — is recorded and audited. A draft can be sent once.
 */
@Service
@RequiredArgsConstructor
public class AiDraftService {

    /** A checked approval, to record once the message is saved. */
    public record Approval(long draftId, boolean edited) {}

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    @Transactional
    public long register(User user, String channel, String subject, String body) {
        return jdbc.queryForObject("INSERT INTO ai_drafts (tenant_id, user_id, channel, content_hash) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, user.getTenant().getId(), user.getId(), channel, hash(subject, body));
    }

    /**
     * Before sending: null when the message isn't from an AI draft. Refuses (400 AI_REVIEW_REQUIRED)
     * a draft that wasn't reviewed, and (409) one that isn't this user's, is for another channel, or
     * was already sent.
     */
    @Transactional(readOnly = true)
    public Approval check(User user, Long draftId, boolean reviewed, String channel, String subject, String body) {
        if (draftId == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT channel, content_hash, approved_at FROM ai_drafts "
                + "WHERE id = ? AND tenant_id = ? AND user_id = ?", draftId, user.getTenant().getId(), user.getId());
        if (rows.isEmpty()) {
            throw ApiException.notFound("AI draft not found");
        }
        Map<String, Object> draft = rows.get(0);
        if (draft.get("approved_at") != null) {
            throw ApiException.conflict("This AI draft has already been sent — draft a new one");
        }
        if (!channel.equals(draft.get("channel"))) {
            throw ApiException.conflict("This AI draft was written for " + draft.get("channel") + ", not " + channel);
        }
        if (!reviewed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Review the AI draft and confirm it before sending", "AI_REVIEW_REQUIRED");
        }
        return new Approval(draftId, !hash(subject, body).equals(draft.get("content_hash")));
    }

    /** After the message is saved: the draft is approved, used up and linked to it. */
    @Transactional
    public void approved(User user, Approval approval, Long communicationId, String channel) {
        if (approval == null) {
            return;
        }
        int updated = jdbc.update("UPDATE ai_drafts SET approved_at = now(), edited = ?, communication_id = ? WHERE id = ? AND approved_at IS NULL",
                approval.edited(), communicationId, approval.draftId());
        if (updated == 0) {
            throw ApiException.conflict("This AI draft has already been sent — draft a new one");
        }
        auditService.record(user, AuditAction.AI_CONTENT_APPROVED, "communication", communicationId,
                channel + " from AI draft #" + approval.draftId() + (approval.edited() ? ", edited before sending" : ", sent as drafted"));
    }

    static String hash(String subject, String body) {
        return DataEncryption.sha256((subject == null ? "" : subject.trim()) + "\n" + (body == null ? "" : body.trim()));
    }
}
