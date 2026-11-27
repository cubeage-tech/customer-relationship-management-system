package com.company.crm.common.mail;

import org.springframework.mail.MailSendException;

/**
 * NFR-7.4a: the mail server didn't take the email now, but it was saved and will be retried
 * automatically. Callers that record a delivery status can show "queued for retry" instead of a
 * final failure.
 */
public class MailQueuedException extends MailSendException {

    public MailQueuedException(String message, Throwable cause) {
        super(message, cause);
    }
}
