import type { WebTaskItem } from 'shared-logic';

export interface TaskActions {
  create(
    title: string,
    notes: string | null | undefined,
    priority: string,
    dueDate: string | null | undefined,
    dueAt: string | null | undefined,
    projectId: string | null | undefined,
  ): void;
  update(
    taskId: string,
    title: string,
    notes: string | null | undefined,
    priority: string,
    dueDate: string | null | undefined,
    dueAt: string | null | undefined,
    isCompleted: boolean,
    projectId: string | null | undefined,
  ): void;
  toggleCompleted(taskId: string): void;
  delete(taskId: string): void;
  clearCompleted(): void;
  keepLocal(taskId: string): void;
  useRemote(taskId: string): void;
  mergeConflict(
    taskId: string,
    title: string,
    notes: string | null | undefined,
    priority: string,
    dueDate: string | null | undefined,
    dueAt: string | null | undefined,
    isCompleted: boolean,
    projectId: string | null | undefined,
  ): void;
  plannedTasks(
    view: string,
    today: string,
    timeZoneId: string,
  ): WebTaskItem[];
  sync(): void;
  clearActionError(): void;
}
