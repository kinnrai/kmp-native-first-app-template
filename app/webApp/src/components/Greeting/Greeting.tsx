import './Greeting.css';

import { useState } from 'react';
import { JSLogo } from '../JSLogo/JSLogo.tsx';
import { getGreeting } from '../../integrations/sharedLogic.ts';
import type { AnimationEvent } from 'react';

export function Greeting() {
  const [greeting] = useState(getGreeting);
  const [isVisible, setIsVisible] = useState<boolean>(false);
  const [isAnimating, setIsAnimating] = useState<boolean>(false);

  const handleClick = () => {
    if (isVisible) {
      setIsAnimating(true);
    } else {
      setIsVisible(true);
    }
  };

  const handleAnimationEnd = (event: AnimationEvent<HTMLDivElement>) => {
    if (event.animationName === 'fadeOut') {
      setIsVisible(false);
      setIsAnimating(false);
    }
  };

  return (
    <div className="greeting-container">
      <button onClick={handleClick} className="greeting-button">
        点击我！
      </button>

      {isVisible && (
        <div
          className={isAnimating ? 'greeting-content fade-out' : 'greeting-content'}
          onAnimationEnd={handleAnimationEnd}
        >
          <JSLogo />
          <div>React: {greeting}</div>
        </div>
      )}
    </div>
  );
}
