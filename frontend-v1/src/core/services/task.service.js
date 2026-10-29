import ApiService from './api.service';

/** FR-AI-7.3: tasks — mine ({}), or for { customerId } | { leadId } | { opportunityId }; optional status. */
export const listTasks = (params) => ApiService.getTasks(params);

/** FR-AI-7.3: { title, notes?, dueDate?, assigneeId?, customerId | leadId | opportunityId }. */
export const createTask = (data) => ApiService.createTask(data);

/** FR-AI-7.3: { status?: open|done|cancelled, title?, notes?, dueDate?, assigneeId? }. */
export const updateTask = (id, data) => ApiService.updateTask(id, data);

/** FR-AI-7.3: colleagues a task can be given to ({ id, name, role }). */
export const getTaskAssignees = () => ApiService.getTaskAssignees();

/** FR-AI-7.3: the AI's proposed tasks from a meeting's conversation (nothing created). */
export const findMeetingActionItems = (meetingId) => ApiService.findMeetingActionItems(meetingId);

/** FR-AI-7.3: create the confirmed items ({ title, notes?, dueDate?, assigneeId }) as tasks. */
export const createMeetingTasks = (meetingId, items) => ApiService.createMeetingTasks(meetingId, items);
