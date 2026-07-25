export interface TaskActions {
  create(
    title: string,
    notes: string | null | undefined,
    priority: string,
    dueAt: string | null | undefined,
  ): void;
  update(
    taskId: string,
    title: string,
    notes: string | null | undefined,
    priority: string,
    dueAt: string | null | undefined,
    isCompleted: boolean,
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
    dueAt: string | null | undefined,
    isCompleted: boolean,
  ): void;
  sync(): void;
  clearActionError(): void;
}
