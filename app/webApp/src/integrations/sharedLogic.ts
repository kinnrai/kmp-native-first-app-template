import { greet as greetFromSharedLogic } from 'shared-logic';

export function getGreeting(): string {
  return greetFromSharedLogic();
}
