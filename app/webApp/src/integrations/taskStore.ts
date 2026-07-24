import { useSyncExternalStore } from 'react';
import { WebTaskStore } from 'shared-logic';
import type { WebTaskSnapshot } from 'shared-logic';
import type { TaskActions } from '../taskActions.ts';

const configuredBaseUrl = import.meta.env.VITE_TASK_API_BASE_URL?.trim();
const databaseName =
  import.meta.env.VITE_TASK_DATABASE_NAME?.trim() || 'kmp-native-first-tasks';

const store = new WebTaskStore(
  configuredBaseUrl || window.location.origin,
  databaseName,
);

const subscribeToTasks = (listener: () => void) => {
  const subscription = store.subscribe(listener);
  return () => subscription.cancel();
};

const getTaskSnapshot = (): WebTaskSnapshot => store.getSnapshot();

const subscribeToConnectivity = (listener: () => void) => {
  window.addEventListener('online', listener);
  window.addEventListener('offline', listener);
  return () => {
    window.removeEventListener('online', listener);
    window.removeEventListener('offline', listener);
  };
};

const getOnlineSnapshot = () => navigator.onLine;

export function useTaskSnapshot(): WebTaskSnapshot {
  return useSyncExternalStore(subscribeToTasks, getTaskSnapshot);
}

export function useOnlineStatus(): boolean {
  return useSyncExternalStore(
    subscribeToConnectivity,
    getOnlineSnapshot,
    () => true,
  );
}

export const taskActions: TaskActions = {
  create: store.create.bind(store),
  update: store.update.bind(store),
  toggleCompleted: store.toggleCompleted.bind(store),
  delete: store.delete.bind(store),
  clearCompleted: store.clearCompleted.bind(store),
  keepLocal: store.keepLocal.bind(store),
  useRemote: store.useRemote.bind(store),
  mergeConflict: store.mergeConflict.bind(store),
  plannedTasks: store.plannedTasks.bind(store),
  sync: store.sync.bind(store),
  clearActionError: store.clearActionError.bind(store),
};

if (import.meta.hot) {
  import.meta.hot.dispose(() => store.close());
}
