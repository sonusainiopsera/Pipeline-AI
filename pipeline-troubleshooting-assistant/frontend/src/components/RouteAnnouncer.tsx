import React, { useEffect, useState } from 'react';
import { Page } from './Layout';

const PAGE_TITLES: Record<Page, string> = {
  dashboard: 'Dashboard',
  analyze: 'Analyze Pipeline Log',
  'knowledge-base': 'Knowledge Base',
  history: 'Analysis History',
};

// Visually-hidden style — element is in the DOM for screen readers but invisible.
const VISUALLY_HIDDEN: React.CSSProperties = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  padding: 0,
  margin: '-1px',
  overflow: 'hidden',
  clip: 'rect(0, 0, 0, 0)',
  whiteSpace: 'nowrap',
  border: 0,
};

interface RouteAnnouncerProps {
  page: Page;
}

// Announces page transitions to screen readers via an aria-live region and
// updates document.title so the browser tab also reflects the current page.
export default function RouteAnnouncer({ page }: RouteAnnouncerProps) {
  const [announcement, setAnnouncement] = useState('');

  useEffect(() => {
    const title = PAGE_TITLES[page];
    document.title = `${title} — Pipeline AI`;
    // Small delay so the incoming page content is rendered before the
    // announcement fires, giving screen readers more context.
    const timer = setTimeout(() => setAnnouncement(title), 100);
    return () => {
      clearTimeout(timer);
    };
  }, [page]);

  return (
    <div
      role="status"
      aria-live="assertive"
      aria-atomic="true"
      data-testid="route-announcer"
      style={VISUALLY_HIDDEN}
    >
      {announcement}
    </div>
  );
}
