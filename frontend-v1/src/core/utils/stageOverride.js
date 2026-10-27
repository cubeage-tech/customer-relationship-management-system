/**
 * BR-1/VAL-7: records move through the pipeline in order. When the server refuses a move that skips
 * stages (errorCode STAGE_SKIP), the user can confirm an override; the reason goes to the audit log.
 */
export const isStageSkip = (error) => error?.response?.data?.errorCode === 'STAGE_SKIP';

/** The override reason (may be empty), or null when the user cancels. */
export const askOverrideReason = () => {
  const reason = window.prompt('This skips pipeline stages. To move it anyway, say why (kept in the audit log):', '');
  return reason === null ? null : reason.trim();
};
