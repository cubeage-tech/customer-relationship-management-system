import { useEffect } from 'react';

const REVEAL_SELECTOR =
  '[data-motion="reveal"], section, article, form, table, [role="dialog"], .surface-card';

const useScrollReveal = (containerRef, locationKey) => {
  useEffect(() => {
    const container = containerRef.current;
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (!container || prefersReducedMotion || !('IntersectionObserver' in window)) return undefined;

    const observedElements = new Set();
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;

          entry.target.classList.remove('motion-pending');
          entry.target.classList.add('motion-visible');
          observer.unobserve(entry.target);
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -36px 0px' }
    );

    const observeElements = (root) => {
      if (root instanceof Element && root.matches(REVEAL_SELECTOR)) {
        observedElements.add(root);
        root.classList.add('motion-pending');
        observer.observe(root);
      }

      root.querySelectorAll?.(REVEAL_SELECTOR).forEach((element) => {
        if (observedElements.has(element)) return;

        observedElements.add(element);
        element.classList.add('motion-pending');
        observer.observe(element);
      });
    };

    observeElements(container);

    const mutationObserver = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node instanceof Element) observeElements(node);
        });
      });
    });
    mutationObserver.observe(container, { childList: true, subtree: true });

    return () => {
      mutationObserver.disconnect();
      observer.disconnect();
      observedElements.forEach((element) => {
        element.classList.remove('motion-pending', 'motion-visible');
      });
    };
  }, [containerRef, locationKey]);
};

export default useScrollReveal;
