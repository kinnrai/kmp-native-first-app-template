import { TaskApp } from './components/TaskApp/TaskApp.tsx';
import {
  taskActions,
  useOnlineStatus,
  useTaskSnapshot,
} from './integrations/taskStore.ts';

export function App() {
  const snapshot = useTaskSnapshot();
  const isOnline = useOnlineStatus();

  return (
    <TaskApp
      actions={taskActions}
      isOnline={isOnline}
      snapshot={snapshot}
    />
  );
}
