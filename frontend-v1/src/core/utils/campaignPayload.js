/**
 * Every editable campaign field (the API replaces them all on update), so saving the details never
 * clears the FR-7.1c audience/message, and saving the message never clears the details.
 */
export const campaignPayload = (c, overrides = {}) => ({
  name: c.name,
  description: c.description || '',
  channel: c.channel,
  startDate: c.startDate || null,
  endDate: c.endDate || null,
  budget: c.budget,
  actualCost: c.actualCost,
  ownerId: c.ownerId,
  segmentId: c.segmentId,
  messageSubject: c.messageSubject,
  messageBody: c.messageBody,
  ...overrides,
});
